package com.ldsj.service.wxpay;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ldsj.config.DefaultWXPayConfig;
import com.ldsj.utils.wxpay.WXPay;
import com.ldsj.utils.wxpay.WXPayUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WXPayService {

	@Autowired
	private DefaultWXPayConfig defaultWXPayConfig;
	@Value("${wx.key}")
	private String key;

	/**
	 * 统一下单
	 * @param httpServletRequest
	 * @param out_trade_no
	 * @param amount
	 * @return
	 * @throws Exception
	 */
	public String unifiedorder(HttpServletRequest httpServletRequest,String out_trade_no,BigDecimal amount) throws Exception {
		WXPay wxpay = new WXPay(defaultWXPayConfig);
		Map<String, String> data = new HashMap<String, String>();
		data.put("body", "腾讯充值中心-QQ会员充值"); // 商品描述
		data.put("out_trade_no", out_trade_no); // 商户订单号
		data.put("device_info", "WEB"); // 设备号 可以不传
		data.put("fee_type", "CNY"); // 标价币种  默认CNY人民币 非必传
		String total_fee = String.valueOf((amount.multiply(new BigDecimal(100))).intValue());
		data.put("total_fee", total_fee);// 金额必须是以分为单位的整数
		data.put("spbill_create_ip", PayUtils.getRealIpAddress(httpServletRequest));// 终端IP 发起支付的客户端的ip，H5支付时必传
		data.put("notify_url", PayUtils.getContextUrl(httpServletRequest)+"payNotifyUrl"); // 买家支付成功后微信回调通知我们系统的地址，必须要外网能访问
		data.put("trade_type", "NATIVE"); // 此处指定为扫码支付
		data.put("product_id", "111111");// 商品ID trade_type=NATIVE时，此参数必传。
		Map<String, String> resp = wxpay.unifiedOrder(data); // 调用微信的工具类发送请求并得到回应
		log.debug(resp.get("code_url"));
		return resp.get("code_url"); // 得到返回的二维码数据，将这个字符串编码成二维码显示给买家即可
	}

	/**
	 * 	微信异步回调处理
	 * 
	 * @param httpServletRequest
	 * @return
	 * @throws Exception
	 */
	public String payNotifyUrl(HttpServletRequest httpServletRequest) throws Exception {
		try (BufferedReader br = httpServletRequest.getReader()) {
			String xmlBack;
			String str;
			StringBuilder sb = new StringBuilder("");
			while ((str = br.readLine()) != null) {
				sb.append(str);
			}
			String notifyData = sb.toString(); // 支付结果通知的xml格式数据
			WXPay wxpay = new WXPay(defaultWXPayConfig);
			Map<String, String> notifyMap = WXPayUtil.xmlToMap(notifyData); // 转换成map
			log.debug(notifyMap.toString());
			if (wxpay.isPayResultNotifySignatureValid(notifyMap)) {
				// 签名正确
				// 进行处理。
				// 注意特殊情况：订单已经退款，但收到了支付结果成功的通知，不应把商户侧订单状态从退款改成支付成功
				String return_code = notifyMap.get("return_code");// 状态
				String out_trade_no = notifyMap.get("out_trade_no");// 订单号
				if (out_trade_no == null) {
					xmlBack = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
							+ "<return_msg><![CDATA[报文为空]]></return_msg>" + "</xml> ";
					return xmlBack;
				}
				// 业务逻辑处理 ****************************
				// logger.info("微信支付回调成功订单号: {}", notifyMap);
				xmlBack = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"
						+ "<return_msg><![CDATA[SUCCESS]]></return_msg>" + "</xml> ";
				log.debug(return_code+"-"+out_trade_no);
				return xmlBack;
			} else {
				// 签名错误，如果数据里没有sign字段，也认为是签名错误
				log.debug("验签失败！");
				xmlBack = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
						+ "<return_msg><![CDATA[报文为空]]></return_msg>" + "</xml> ";
				return xmlBack;
			}
		}
	}

	/**
	 * 支付查询
	 * 
	 * @param out_trade_no
	 * @throws Exception
	 */
	public boolean orderQuery(String out_trade_no) throws Exception {
		WXPay wxpay = new WXPay(defaultWXPayConfig);
		Map<String, String> data = new HashMap<String, String>();
		data.put("out_trade_no", out_trade_no);
		Map<String, String> resp = wxpay.orderQuery(data);
		log.debug(resp.toString());
		if ("SUCCESS".equals(resp.get("return_code")) && "SUCCESS".equals(resp.get("trade_state"))
				&& resp.get("out_trade_no") != null && resp.get("out_trade_no").equals(out_trade_no)) {// 支付成功
			return true;
		} else { // 支付失败
			return false;
		}
	}

	/**
	 * 退款
	 * @param httpServletRequest
	 * @param out_trade_no
	 * @param out_refund_no
	 * @param total_fee
	 * @param refund_fee
	 * @return
	 * @throws Exception
	 */
	public boolean refund(HttpServletRequest httpServletRequest,String out_trade_no,String out_refund_no,BigDecimal total_fee,BigDecimal refund_fee) throws Exception {
		WXPay wxpay = new WXPay(defaultWXPayConfig);
		Map<String, String> data = new HashMap<String, String>();
		data.put("out_trade_no", out_trade_no); // 商户订单号
		data.put("out_refund_no", out_refund_no);// 商户退款单号  必须的，不能跟订单号一致  查询退款的时候可能用到
		data.put("total_fee", String.valueOf((total_fee.multiply(new BigDecimal(100))).intValue()));// 订单金额
		data.put("refund_fee", String.valueOf((refund_fee.multiply(new BigDecimal(100))).intValue()));// 退款金额
		data.put("notify_url", PayUtils.getContextUrl(httpServletRequest)+"refundUotifyUrl"); // 退款结果通知url ,非必填
		Map<String, String> resp = wxpay.refund(data);
		log.debug(resp.toString());
		if ("SUCCESS".equals(resp.get("return_code")) && "SUCCESS".equals(resp.get("result_code"))
				&& resp.get("out_trade_no") != null && resp.get("out_trade_no").equals(out_trade_no)) {// 退款申请成功
			return true;
		} else { // 退款申请失败
			return false;
		}
	}

	/**
	 * 微信退款异步回调处理
	 * 
	 * @param httpServletRequest
	 * @return
	 * @throws Exception
	 */
	public String refundNotifyUrl(HttpServletRequest httpServletRequest) throws Exception {
		try (BufferedReader br = httpServletRequest.getReader()) {
			String xmlBack;
			String str;
			StringBuilder sb = new StringBuilder("");
			while ((str = br.readLine()) != null) {
				sb.append(str);
			}
			String notifyData = sb.toString(); // 支付结果通知的xml格式数据
			Map<String, String> notifyMap = WXPayUtil.xmlToMap(notifyData); // 转换成map
			String reqInfo = getRefundDecrypt(notifyMap.get("req_info"), key);
			Map<String, String> reqInfoMap = WXPayUtil.xmlToMap(reqInfo); // 转换成map
			log.debug(reqInfoMap.toString());
			if ("SUCCESS".equals(reqInfoMap.get("refund_status"))) {
				xmlBack = "<xml>" + "<return_code><![CDATA[SUCCESS]]></return_code>"
						+ "<return_msg><![CDATA[SUCCESS]]></return_msg>" + "</xml> ";
				return xmlBack;
			} else {
				xmlBack = "<xml>" + "<return_code><![CDATA[FAIL]]></return_code>"
						+ "<return_msg><![CDATA[报文为空]]></return_msg>" + "</xml> ";
				return xmlBack;
			}
		}
	}

	// 解密退款结果通知
	private String getRefundDecrypt(String reqInfoSecret, String key) throws Exception {
		String result = "";
		Security.addProvider(new BouncyCastleProvider());
		sun.misc.BASE64Decoder decoder = new sun.misc.BASE64Decoder();
		byte[] bt = decoder.decodeBuffer(reqInfoSecret);
		String md5key = WXPayUtil.MD5(key).toLowerCase();
		SecretKey secretKey = new SecretKeySpec(md5key.getBytes(), "AES");
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
		cipher.init(Cipher.DECRYPT_MODE, secretKey);
		byte[] resultbt = cipher.doFinal(bt);
		result = new String(resultbt, "UTF-8");
		return result;
	}

	// 关闭订单
	public boolean closeorder(String out_trade_no) throws Exception {
		Map<String, String> data = new HashMap<String, String>();
		data.put("out_trade_no", out_trade_no);
		WXPay wxpay = new WXPay(defaultWXPayConfig);
		Map<String, String> closeOrderMap = wxpay.closeOrder(data);
		if ("SUCCESS".equals(closeOrderMap.get("return_code")) && "SUCCESS".equals(closeOrderMap.get("result_code"))) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 订单退款查询
	 * 
	 * @param out_trade_no 订单号，可以不传
	 * @param out_refund_no 退款号
	 * @return
	 * @throws Exception
	 */
	public boolean refundQuery(String out_trade_no, String out_refund_no) throws Exception {
		Map<String, String> data = new HashMap<String, String>();
		//data.put("out_trade_no", out_trade_no); // out_trade_no 和 out_refund_no 任传一个
		data.put("out_refund_no", out_refund_no); 
		WXPay wxpay = new WXPay(defaultWXPayConfig);
		Map<String, String> refundQueryMap = wxpay.refundQuery(data);
		log.debug(refundQueryMap.toString());
		if ("SUCCESS".equals(refundQueryMap.get("return_code")) && "SUCCESS".equals(refundQueryMap.get("result_code"))
				&& "SUCCESS".equals(refundQueryMap.get("refund_status_0"))) {
			return true;
		} else {
			return false;
		}
	}
}
