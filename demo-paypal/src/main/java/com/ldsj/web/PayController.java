package com.ldsj.web;

import java.math.BigDecimal;
import java.net.URISyntaxException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ldsj.service.PaypalService;
import com.paypal.base.rest.PayPalRESTException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PayController {

	@Autowired
	private PaypalService paypalService;

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
	 * @throws URISyntaxException
	 * @throws PayPalRESTException 
	 * 
	 */
	@RequestMapping("/pay")
	public void pay(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
			String payAmount) throws URISyntaxException, PayPalRESTException {
		log.debug(payAmount);
		String returnUrl=paypalService.launchPay(httpServletRequest, httpServletResponse, new BigDecimal(payAmount), "这是tt支付");
		if(returnUrl!=null) {
			URIBuilder urlBuilder = new URIBuilder(returnUrl);
			httpServletResponse.setHeader("Location", urlBuilder.toString());
			httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
		} else {
			URIBuilder urlBuilder = new URIBuilder("static/error.html");
			httpServletResponse.setHeader("Location", urlBuilder.toString());
			httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
		}
		return;
	}

	/**
	 * 客户取消支付
	 * 
	 * @return
	 */
	@RequestMapping(method = RequestMethod.GET, value = "cancelPay")
	public String cancelPay() {
		return "cancel";
	}

	/**
	 * 用户批准支付回调接口
	 * 
	 * @param paymentId
	 * @param payerId
	 * @return
	 * @throws Exception 
	 */
	@RequestMapping(method = RequestMethod.GET, value = "successPay")
	public String successPay(@RequestParam("paymentId") String paymentId, @RequestParam("PayerID") String payerId,HttpServletResponse httpServletResponse) throws Exception {
		log.debug("paymentId"+paymentId+"PayerID"+payerId);
		if(paypalService.paypalConfirmPay(paymentId, payerId)) {
			URIBuilder urlBuilder = new URIBuilder("static/returnUrl.html");
			httpServletResponse.setHeader("Location", urlBuilder.toString());
			httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
			return "success";
		} else {
			return "fail";
		}
	}

	/**
	 * 申请退款
	 * @param paymentId
	 * @param refundAmount
	 * @return
	 * @throws PayPalRESTException
	 */
	@RequestMapping("/refund")
	public String refund(String paymentId,String refundAmount) throws PayPalRESTException {
		log.debug("paymentId:"+paymentId+"refundAmount"+refundAmount);
		if(paypalService.refund(paymentId, new BigDecimal(refundAmount))) {
			return "退款申请成功！";
		} else {
			return "退款申请失败！";
		}
	}
	
	/**
	 * 支付查询
	 * @param paymentId
	 * @return
	 * @throws PayPalRESTException
	 */
	@RequestMapping("/paymentQuery")
	public String paymentQuery(String paymentId) throws PayPalRESTException {
		log.debug("paymentId"+paymentId);
		if(paypalService.orderQuery(paymentId)) {
			return "支付成功";
		} else {
			return "支付未成功";
		}
	}
	
	/**
	 * 退款查询
	 * @param paymentId
	 * @throws PayPalRESTException
	 */
	@RequestMapping("/refundQuery")
	public String refundQuery(String paymentId) throws PayPalRESTException {
		log.debug("paymentId"+paymentId);
		if(paypalService.refundQuery(paymentId)) {
			return "退款成功！";
		} else {
			return "退款失败！";
		}
	}
}