package com.ldsj.web;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.github.binarywang.wxpay.bean.notify.WxPayNotifyResponse;
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult;
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyResult;
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyResult.ReqInfo;
import com.github.binarywang.wxpay.bean.order.WxPayMwebOrderResult;
import com.github.binarywang.wxpay.bean.order.WxPayNativeOrderResult;
import com.github.binarywang.wxpay.bean.request.WxPayOrderQueryRequest;
import com.github.binarywang.wxpay.bean.request.WxPayRefundQueryRequest;
import com.github.binarywang.wxpay.bean.request.WxPayRefundRequest;
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest;
import com.github.binarywang.wxpay.bean.result.WxPayOrderCloseResult;
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryResult;
import com.github.binarywang.wxpay.bean.result.WxPayRefundQueryResult;
import com.github.binarywang.wxpay.bean.result.WxPayRefundQueryResult.RefundRecord;
import com.github.binarywang.wxpay.bean.result.WxPayRefundResult;
import com.github.binarywang.wxpay.exception.WxPayException;
import com.github.binarywang.wxpay.service.WxPayService;
import com.ldsj.utils.PayUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PayController {

	@Autowired
	private WxPayService wxService;

	/**
	 * 支付首页
	 * 
	 * @param httpServletResponse
	 * @throws URISyntaxException
	 */
	@RequestMapping("/")
	public void index(HttpServletResponse httpServletResponse) throws URISyntaxException {
		URIBuilder urlBuilder = new URIBuilder("static/index.html");
		httpServletResponse.setHeader("Location", urlBuilder.toString());
		httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
	}

	/**
	 * 支付
	 * 
	 * @param httpRequest
	 * @param httpResponse
	 * @param orderCode
	 * @param payAmount
	 * @return native返回支付二维码url，手机端返回重定向
	 * @throws Exception
	 */
	@RequestMapping("/pay")
	public void pay(HttpServletRequest httpServletRequest,HttpServletResponse httpServletResponse, String orderCode, String payAmount) throws Exception {
		String tradeType="NATIVE";
		if(PayUtils.isMobileDevice(httpServletRequest)) {
			tradeType="MWEB"; // H5支付
		}
		WxPayUnifiedOrderRequest request = new WxPayUnifiedOrderRequest();
		request.setDeviceInfo("WEB");
		request.setBody("腾讯充值中心-QQ会员充值");
		request.setOutTradeNo(orderCode); // 商户订单号
		request.setTotalFee(WxPayUnifiedOrderRequest.yuanToFen(payAmount));// 金额必须是以分为单位的整数
		request.setNotifyUrl(PayUtils.getContextUrl(httpServletRequest)+"payNotifyUrl");// 买家支付成功后微信回调通知我们系统的地址，必须要外网能访问
		request.setTradeType(tradeType);// 交易类型
		request.setSpbillCreateIp(PayUtils.getRealIpAddress(httpServletRequest));
		request.setProductId("111");
		//request.setFeeType("USD");
		if("NATIVE".equals(tradeType)) {
			WxPayNativeOrderResult wxPayNativeOrderResult = wxService.createOrder(request);
			httpServletResponse.getWriter().write(wxPayNativeOrderResult.getCodeUrl());
			httpServletResponse.getWriter().close();
			log.debug(wxPayNativeOrderResult.getCodeUrl());
			return;
		} else { //需要开通H5支付，并且服务器的域名要跟H5支付功能里面填写的域名一致
			WxPayMwebOrderResult wxPayMwebOrderResult = wxService.createOrder(request);
			log.debug("MwebUrl:"+wxPayMwebOrderResult.getMwebUrl());
			URIBuilder urlBuilder = new URIBuilder(wxPayMwebOrderResult.getMwebUrl());
			httpServletResponse.setHeader("Location", urlBuilder.toString());
			httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
			return;
		}
	}
	
	/**
	 * 支付成功后微信回调接口
	 * 
	 * @param httpRequest
	 * @param httpResponse
	 * @param inputParams
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/payNotifyUrl")
	public String payNotifyUrl(@RequestBody String xmlData) throws Exception {
		final WxPayOrderNotifyResult notifyResult = wxService.parseOrderNotifyResult(xmlData);
		// TODO 根据自己业务场景需要构造返回对象
		if ("SUCCESS".equals(notifyResult.getResultCode())) {
			// 对支付结果中的业务内容进行二次校验
			String orderCode = notifyResult.getOutTradeNo();
			BigDecimal payAmount = new BigDecimal(notifyResult.getTotalFee()).divide(new BigDecimal(100));
			LocalDateTime payTime = LocalDateTime.parse(notifyResult.getTimeEnd(),
					DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
			log.debug("orderCode:"+orderCode+"payAmount:"+payAmount+"payTime:"+payTime);
			return WxPayNotifyResponse.success("OK");
		} else {
			return WxPayNotifyResponse.fail("fail");
		}
	}

	/**
	 * 支付结果查询
	 * 
	 * @param httpServletResponse
	 * @param out_trade_no
	 * @throws WxPayException 
	 * @throws Exception
	 */
	@RequestMapping("/orderQuery")
	public String orderQuery(HttpServletResponse httpServletResponse, String out_trade_no) throws WxPayException {
		WxPayOrderQueryRequest wxPayOrderQueryRequest = new WxPayOrderQueryRequest();
		wxPayOrderQueryRequest.setOutTradeNo(out_trade_no);
		WxPayOrderQueryResult queryOrder;
		try {
			queryOrder = wxService.queryOrder(wxPayOrderQueryRequest);
			if ("SUCCESS".equals(queryOrder.getTradeState())) {
				return "success";
			} else {
				return "fail";
			}
		} catch (WxPayException e) {
			log.debug("getCustomErrorMsg:"+e.getCustomErrorMsg()+"getReturnCode:"+e.getReturnCode()+"getReturnMsg:"+e.getReturnMsg()+"getResultCode:"+e.getResultCode());
			if("SUCCESS".equalsIgnoreCase(e.getReturnCode()) && "OK".equalsIgnoreCase(e.getReturnMsg())) {
				return "fail";
			} else {
				throw e;
			}
		}
	}

	/**
	 * 退款
	 * 
	 * @param orderCode
	 * @param rebackAmount
	 * @param refundNumber
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/refund")
	public String refund(HttpServletRequest httpServletRequest, String orderCode, String rebackAmount, String refundNumber, String payAmount)
			throws Exception {
		WxPayRefundRequest request = new WxPayRefundRequest();
		request.setOutTradeNo(orderCode);
		request.setOutRefundNo(refundNumber);
		request.setTotalFee(WxPayRefundRequest.yuanToFen(payAmount));
		request.setRefundFee(WxPayRefundRequest.yuanToFen(rebackAmount));
		//request.setNotifyUrl(tempContextUrl+"refundUotifyUrl"); // 退款结果异步通知
		try {
			WxPayRefundResult refund = wxService.refund(request);
			if ("SUCCESS".equalsIgnoreCase(refund.getResultCode())) {
				return "退款申请成功！";
			} else {
				log.info("微信退款申请失败！");
				return "退款申请失败！";
			}
		} catch (Exception e) {
			return "退款申请失败！";
		}
	}

	/**
	 * 微信异步退款回调通知接口
	 * 
	 * @param httpRequest
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/refundUotifyUrl")
	public String refundUotifyUrl(@RequestBody String xmlData) throws Exception {
		final WxPayRefundNotifyResult result = wxService.parseRefundNotifyResult(xmlData);
		if ("SUCCESS".equalsIgnoreCase(result.getResultCode())) {
			ReqInfo reqInfo = result.getReqInfo();
			if ("SUCCESS".equalsIgnoreCase(reqInfo.getRefundStatus().toUpperCase())) {
				return WxPayNotifyResponse.success("OK");
			}
		}
		return WxPayNotifyResponse.fail("OK");
	}

	/**
	 * 退款查询
	 * 
	 * @param orderCode
	 * @param refundNumber
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/refundQuery")
	public String refundQuery(String orderCode, String refundNumber) throws Exception {
		WxPayRefundQueryRequest request = new WxPayRefundQueryRequest();
		request.setOutRefundNo(refundNumber);
		WxPayRefundQueryResult refundQuery = wxService.refundQuery(request);
		List<RefundRecord> refundRecords = refundQuery.getRefundRecords();
		if (refundRecords != null) {
			for (RefundRecord refundRecord : refundRecords) {
				if ("SUCCESS".equalsIgnoreCase(refundRecord.getRefundStatus())) {
					return "退款成功！";
				}
			}
		}
		return "退款失败";
	}
	
	@RequestMapping("/closeOrder")
	public String closeOrder(String orderCode) throws Exception {
		WxPayOrderCloseResult wxPayOrderCloseResult = wxService.closeOrder(orderCode);
		log.info(wxPayOrderCloseResult.toString());
		if ("SUCCESS".equals(wxPayOrderCloseResult.getResultCode())) {
			return "关闭成功！";
		} else {
			return "关闭失败！";
		}
	}

}