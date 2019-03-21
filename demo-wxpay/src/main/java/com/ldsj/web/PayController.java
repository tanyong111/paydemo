package com.ldsj.web;

import java.math.BigDecimal;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ldsj.service.wxpay.WXPayService;

@RestController
public class PayController {

	@Autowired
	private WXPayService wxPayService;

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
	 * 下单
	 * @param out_trade_no 
	 * @param amount
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/pay")
	public String pay(HttpServletRequest httpServletRequest,String out_trade_no,String amount) throws Exception {
		return wxPayService.unifiedorder(httpServletRequest,out_trade_no,new BigDecimal(amount));
	}

	/**
	 * 支付成功后微信回调接口
	 * 
	 * @param httpRequest
	 * @param httpResponse
	 * @param inputParams
	 * @return 回应给微信通知成功
	 * @throws Exception
	 */
	@RequestMapping("/payNotifyUrl")
	public String payNotifyUrl(HttpServletRequest httpRequest) throws Exception {
		return wxPayService.payNotifyUrl(httpRequest);
	}

	/**
	 * 支付结果查询
	 * 
	 * @param httpServletResponse
	 * @param out_trade_no
	 * @throws Exception
	 */
	@RequestMapping("/orderQuery")
	public String orderQuery(HttpServletResponse httpServletResponse, String out_trade_no) throws Exception {
		if (wxPayService.orderQuery(out_trade_no)) {
			return "success";
		} else {
			return "fail";
		}
	}

	/**
	 * 退款   退款接口的调用是需要cert证书的。
	 * 
	 * @param out_trade_no
	 * @param out_refund_no
	 * @param total_fee
	 * @param refund_fee
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/refund")
	public String refund(HttpServletRequest httpServletRequest,String out_trade_no, String out_refund_no, String total_fee, String refund_fee)
			throws Exception {
		if (wxPayService.refund(httpServletRequest,out_trade_no, out_refund_no,new BigDecimal(total_fee),new BigDecimal(refund_fee))) {
			return "退款成功！";
		} else {
			return "退款失败！";
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
	public String refundUotifyUrl(HttpServletRequest httpRequest) throws Exception {
		return wxPayService.refundNotifyUrl(httpRequest);
	}

	/**
	 * 退款查询
	 * 
	 * @param out_trade_no
	 * @param out_refund_no
	 * @return
	 * @throws Exception
	 */
	@RequestMapping("/refundQuery")
	public String refundQuery(String out_trade_no, String out_refund_no) throws Exception {
		if (wxPayService.refundQuery(out_trade_no, out_refund_no)) {
			return "退款成功！";
		} else {
			return "退款失败！";
		}
	}

}