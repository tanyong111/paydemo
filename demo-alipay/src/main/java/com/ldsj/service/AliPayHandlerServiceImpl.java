package com.ldsj.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayDataDataserviceBillDownloadurlQueryRequest;
import com.alipay.api.request.AlipayEbppInvoiceInfoSendRequest;
import com.alipay.api.request.AlipayTradeCancelRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayDataDataserviceBillDownloadurlQueryResponse;
import com.alipay.api.response.AlipayEbppInvoiceInfoSendResponse;
import com.alipay.api.response.AlipayTradeCancelResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldsj.entity.OrderInfo;
import com.ldsj.entity.OrderPayDetail;
import com.ldsj.utils.PayUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * 支付宝业务处理服务
 * 
 * @author tan
 *
 */
@Slf4j
@Service
public class AliPayHandlerServiceImpl {

	@Value("${ali.format}")
	String format; // 40 仅支持JSON
	@Value("${ali.charset}")
	String charset; // 10 请求使用的编码格式，如utf-8,gbk,gb2312等 utf-8
	@Value("${ali.sign-type}")
	String sign_type; // 是 10 商户生成签名字符串所使用的签名算法类型，目前支持RSA2和RSA，推荐使用RSA2 RSA2
	@Value("${ali.version}")
	String version; // 3 调用的接口版本，固定为：1.0 1.0
	@Value("${ali.alipay-public-key}")
	String alipay_public_key; // 用户私钥

	@Autowired
	private AlipayClient alipayClient;
	@Autowired
	private ObjectMapper objectMapper;

	/**
	 * 调用支付宝接口发起支付
	 * @throws AlipayApiException 
	 */
	public void launchPay(OrderInfo orderInfo, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
			throws IOException, AlipayApiException {
		Map<String,Object> paramsMap=new HashMap<>();
		paramsMap.put("out_trade_no", orderInfo.getOrderCode());
		paramsMap.put("total_amount", orderInfo.getPayAmount().setScale(2, BigDecimal.ROUND_DOWN));
		paramsMap.put("subject", "苹果6 16g");
		paramsMap.put("body", "苹果厂要倒闭了！");
		//paramsMap.put("timeout_express", "1d"); 测试环境好像设置不了1d，生成环境可以
		//paramsMap.put("trans_currency", "USD"); 貌似只支持人民币
		//paramsMap.put("settle_currency", "USD");
		System.out.println("paramsMap"+paramsMap);
		String form=null;
		if(!PayUtil.isMobileDevice(httpRequest)) {// 如果是google浏览器开启F2调试模式，会误判为手机端，这是一个坑
			paramsMap.put("product_code", "FAST_INSTANT_TRADE_PAY");// 电脑网站支付
			AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
			alipayRequest.setReturnUrl(PayUtil.getContextUrl(httpRequest)+"returnUrl");
			alipayRequest.setNotifyUrl(PayUtil.getContextUrl(httpRequest)+"notifyUrl");
			alipayRequest.setBizContent(objectMapper.writeValueAsString(paramsMap));
			form = alipayClient.pageExecute(alipayRequest).getBody();
		} else {
			paramsMap.put("product_code", "QUICK_WAP_WAY");// 手机网站支付
			AlipayTradeWapPayRequest alipayRequest = new AlipayTradeWapPayRequest();
			alipayRequest.setReturnUrl(PayUtil.getContextUrl(httpRequest)+"returnUrl");
			alipayRequest.setNotifyUrl(PayUtil.getContextUrl(httpRequest)+"notifyUrl");//在公共参数中设置回跳和通知地址
			alipayRequest.setBizContent(objectMapper.writeValueAsString(paramsMap));
			form = alipayClient.pageExecute(alipayRequest).getBody();
		}
		System.out.println("form"+form);
		httpResponse.setContentType("text/html;charset=" + charset);
		httpResponse.getWriter().write(form);
		httpResponse.getWriter().flush();
		httpResponse.getWriter().close();
	}

	/**
	 * 支付宝异步通知回调处理方法
	 * 
	 * @param inputParams
	 * @param httpRequest
	 * @param httpResponse
	 * @throws AlipayApiException
	 * @throws IOException
	 */
	public void notifyUrl(HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) throws AlipayApiException, IOException{
		Map<String, String> inputParams=getReceiveMap(httpRequest);
		// 支付宝自带的SDK验证参数是否正确
		log.debug("inputParams"+inputParams);
		System.out.println("inputParams"+inputParams);
		boolean signVerified = AlipaySignature.rsaCheckV1(inputParams, alipay_public_key, charset, sign_type);
		try(PrintWriter printWriter =httpResponse.getWriter();) {
			if (signVerified) {
				// 对支付结果中的业务内容进行二次校验
				if("TRADE_SUCCESS".equals(inputParams.get("trade_status")) || "TRADE_FINISHED".equals(inputParams.get("trade_status"))) {
					// 对支付结果中的业务内容进行二次校验
					String orderCode=inputParams.get("out_trade_no");
					BigDecimal payAmount=new BigDecimal(inputParams.get("total_amount"));
					LocalDateTime payTime=LocalDateTime.parse(inputParams.get("gmt_payment"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
					System.out.println("orderCode:"+orderCode+"payAmount:"+payAmount+"payTime:"+payTime);
					printWriter.write("success");
				} else {
					printWriter.write("failure");
				}
			} else {
				printWriter.write("failure");
			}
			printWriter.flush();
		}
	}
	
	// 从HttpServletRequest得到支付宝异步通知的参数
	private Map<String,String> getReceiveMap(HttpServletRequest request){
        Map<String,String> params = new HashMap<String,String>();
        Map requestParams = request.getParameterMap();
        for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
            String name = (String) iter.next();
            String[] values = (String[]) requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i]
                        : valueStr + values[i] + ",";
            }
            //乱码解决，这段代码在出现乱码时使用。
            //valueStr = new String(valueStr.getBytes("ISO-8859-1"), "utf-8");
            params.put(name, valueStr);
        }
        
        return params;
    }

	/**
	 * 支付宝退款调用
	 * 
	 * @param orderInfo
	 * @throws AlipayApiException
	 * @throws JsonProcessingException
	 */
	public boolean refund(OrderInfo orderInfo, OrderPayDetail orderPayDetail)
			throws AlipayApiException, JsonProcessingException {
		AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
		Map<String,Object> paramsMap=new HashMap<>();
		paramsMap.put("out_trade_no", orderInfo.getOrderCode());
		paramsMap.put("refund_amount",orderInfo.getRebackAmount().setScale(2, BigDecimal.ROUND_DOWN));
		paramsMap.put("out_request_no", orderPayDetail.getRefundNumber());
		request.setBizContent(objectMapper.writeValueAsString(paramsMap));
		AlipayTradeRefundResponse response = alipayClient.execute(request);
		if (response.isSuccess()) {
			log.debug("退款成功！");
			return true;
		} else {
			log.debug("退款失败！");
			return false;
		}
	}

	/**
	 * 退款查询
	 * 
	 * @param orderPayDetail
	 * @return
	 * @throws AlipayApiException
	 * @throws JsonProcessingException
	 */
	public boolean refundQuery(OrderPayDetail orderPayDetail) throws AlipayApiException, JsonProcessingException {
		AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
		Map<String, String> paramsMap = new HashMap<String, String>();
		paramsMap.put("out_trade_no", orderPayDetail.getOrderId());
		paramsMap.put("out_request_no", orderPayDetail.getRefundNumber());
		request.setBizContent(objectMapper.writeValueAsString(paramsMap));
		AlipayTradeFastpayRefundQueryResponse response = alipayClient.execute(request);
		if (response.isSuccess()) {
			log.debug("调用成功!" + response.getRefundAmount());
			return true;
		} else {
			log.debug("调用失败!");
			return false;
		}
	}

	/**
	 * 支付查询
	 * 
	 * @param out_trade_no
	 * @return true支付成功，false支付失败
	 * @throws AlipayApiException
	 */
	public boolean payQuery(String out_trade_no) throws AlipayApiException {
		AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
		request.setBizContent("{\"out_trade_no\":\"" + out_trade_no + "\"}");
		AlipayTradeQueryResponse response = alipayClient.execute(request);
		if ("TRADE_SUCCESS".equals(response.getTradeStatus()) || "TRADE_FINISHED".equals(response.getTradeStatus())) {
			System.out.println("支付成功" + response.getPayAmount() + response.getTotalAmount());
			return true;
		} else {
			System.out.println("支付失败");
			return false;
		}
	}

	/**
	 * 	支付关闭
	 * @param out_trade_no
	 * @return
	 * @throws AlipayApiException
	 * @throws JsonProcessingException 
	 */
	public boolean payClose(String out_trade_no,String trade_no) throws AlipayApiException, JsonProcessingException {
		Map<String, String> paramsMap = new HashMap<String, String>();
		paramsMap.put("out_trade_no", out_trade_no);
		paramsMap.put("trade_no", trade_no);
		AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
		request.setBizContent(objectMapper.writeValueAsString(paramsMap));
		AlipayTradeCloseResponse response = alipayClient.execute(request);
		if (response.isSuccess()) {
			System.out.println("调用成功");
			return true;
		} else {
			System.out.println("调用失败");
			return false;
		}
	}
	
	public boolean payCancel(String out_trade_no) throws JsonProcessingException, AlipayApiException {
		Map<String, String> paramsMap = new HashMap<String, String>();
		paramsMap.put("out_trade_no", out_trade_no);
		AlipayTradeCancelRequest request = new AlipayTradeCancelRequest();
		request.setBizContent(objectMapper.writeValueAsString(paramsMap));
		AlipayTradeCancelResponse response = alipayClient.execute(request);
		if (response.isSuccess()) {
			System.out.println("调用成功");
			return true;
		} else {
			System.out.println("调用失败");
			return false;
		}
	}
	
	/**
	 * 	查询对账单下载地址
	 * @return
	 * @throws AlipayApiException
	 */
	public String payBillDownloadurl() throws AlipayApiException {
		AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
		request.setBizContent("{" +
		"\"bill_type\":\"trade\"," +
		"\"bill_date\":\"2016-04-05\"" +
		"  }");
		AlipayDataDataserviceBillDownloadurlQueryResponse response = alipayClient.execute(request);
		if(response.isSuccess()){
			System.out.println("调用成功");
			return response.getBillDownloadUrl();
		} else {
			System.out.println("调用失败");
			return null;
		}
	}
	
	/**
	 * 	发票下载链接获取
	 * 	参数较复杂，不理解
	 * @throws AlipayApiException 
	 */
	public void InvoiceInfoQuery() throws AlipayApiException {
		AlipayEbppInvoiceInfoSendRequest request = new AlipayEbppInvoiceInfoSendRequest();
		request.setBizContent("{" +
		"\"m_short_name\":\"XSD\"," +
		"\"sub_m_short_name\":\"XSD_HL\"," +
		"      \"invoice_info_list\":[{" +
		"        \"user_id\":\"2088399922382233\"," +
		"\"apply_id\":\"2016112800152005000000000239\"," +
		"\"invoice_code\":\"4112740003\"," +
		"\"invoice_no\":\"41791003\"," +
		"\"invoice_date\":\"2017-10-10\"," +
		"\"sum_amount\":\"101.00\"," +
		"\"ex_tax_amount\":\"100.00\"," +
		"\"tax_amount\":\"1.00\"," +
		"          \"invoice_content\":[{" +
		"            \"item_name\":\"餐饮费\"," +
		"\"item_no\":\"1010101990000000000\"," +
		"\"item_spec\":\"G39\"," +
		"\"item_unit\":\"台\"," +
		"\"item_quantity\":1," +
		"\"item_unit_price\":\"100.00\"," +
		"\"item_ex_tax_amount\":\"100.00\"," +
		"\"item_tax_rate\":\"0.01\"," +
		"\"item_tax_amount\":\"1.00\"," +
		"\"item_sum_amount\":\"101.00\"," +
		"\"row_type\":\"0\"" +
		"            }]," +
		"\"out_trade_no\":\"20171023293456785924325\"," +
		"\"invoice_type\":\"BLUE\"," +
		"\"invoice_kind\":\"PLAIN\"," +
		"\"invoice_title\":{" +
		"\"title_name\":\"支付宝（中国）网络技术有限公司\"," +
		"\"payer_register_no\":\"9133010060913454XP\"," +
		"\"payer_address_tel\":\"杭州市西湖区天目山路黄龙时代广场0571-11111111\"," +
		"\"payer_bank_name_account\":\"中国建设银行11111111\"" +
		"        }," +
		"\"payee_register_no\":\"310101000000090\"," +
		"\"payee_register_name\":\"支付宝（杭州）信息技术有限公司\"," +
		"\"payee_address_tel\":\"杭州市西湖区某某办公楼 0571-237405862\"," +
		"\"payee_bank_name_account\":\"西湖区建行11111111111\"," +
		"\"check_code\":\"15170246985745164986\"," +
		"\"out_invoice_id\":\"201710283459661232435535\"," +
		"\"ori_blue_inv_code\":\"4112740002\"," +
		"\"ori_blue_inv_no\":\"41791002\"," +
		"\"file_download_type\":\"PDF\"," +
		"\"file_download_url\":\"http://img.hadalo.com/aa/kq/ddhrtdefgxKVXXXXa6apXXXXXXXXXX.pdf\"," +
		"\"payee\":\"张三\"," +
		"\"checker\":\"李四\"," +
		"\"clerk\":\"赵吴\"," +
		"\"invoice_memo\":\"订单号：2017120800001\"," +
		"\"extend_fields\":\"m_invoice_detail_url=http://196.021.871.011:8080/invoice/detail.action?fpdm= 4112740003&fphm=41791003\"" +
		"        }]" +
		"  }");
		AlipayEbppInvoiceInfoSendResponse response = alipayClient.execute(request);
		if(response.isSuccess()){
		System.out.println("调用成功");
		} else {
		System.out.println("调用失败");
		}
	}
}
