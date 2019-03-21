package com.ldsj.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alipay.api.AlipayApiException;
import com.ldsj.entity.OrderInfo;
import com.ldsj.entity.OrderPayDetail;
import com.ldsj.service.AliPayHandlerServiceImpl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PayController {

	@Autowired
	private AliPayHandlerServiceImpl aliPayHandlerServiceImpl;

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
	 * @param httpRequest
	 * @param httpResponse
	 * @param orderCode
	 * @param payAmount
	 * @throws ServletException
	 * @throws IOException
	 * @throws AlipayApiException 
	 */
	@RequestMapping("/pay")
	public void pay(HttpServletRequest httpRequest, HttpServletResponse httpResponse, String orderCode,
			String payAmount) throws ServletException, IOException, AlipayApiException {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setOrderCode(orderCode);// 商户订单号，自定义
		orderInfo.setPayAmount(new BigDecimal(payAmount));// 支付金额
		log.debug(orderCode + payAmount);
		aliPayHandlerServiceImpl.launchPay(orderInfo, httpRequest, httpResponse);
	}
	
	/**
	 * 支付异步回调通知
	 * @param httpRequest
	 * @param httpResponse
	 * @param inputParams
	 * @throws AlipayApiException
	 * @throws IOException
	 */
	@RequestMapping("/notifyUrl")
	public void notifyUrl(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws AlipayApiException, IOException {
		aliPayHandlerServiceImpl.notifyUrl(httpRequest, httpResponse);
	}

	/**
	 * 支付成功后跳转地址
	 * @param httpRequest
	 * @param httpResponse
	 * @return
	 * @throws URISyntaxException 
	 * @throws ServletException
	 * @throws IOException
	 */
	@RequestMapping("/returnUrl")
	public void returnUrl(HttpServletResponse httpServletResponse) throws URISyntaxException {
		URIBuilder urlBuilder = new URIBuilder("static/returnUrl.html");
		httpServletResponse.setHeader("Location", urlBuilder.toString());
		httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
	}

	/**
	 * 退款
	 * @param orderCode
	 * @param rebackAmount
	 * @param refundNumber
	 * @return
	 * @throws ServletException
	 * @throws IOException
	 * @throws AlipayApiException
	 */
	@RequestMapping("/refund")
	public String refund(String orderCode, String rebackAmount, String refundNumber)
			throws ServletException, IOException, AlipayApiException {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setOrderCode(orderCode);// 商户订单号，支付时填写过的
		orderInfo.setRebackAmount(new BigDecimal(rebackAmount));
		System.out.println(orderCode + rebackAmount);
		OrderPayDetail orderPayDetail = new OrderPayDetail();
		orderPayDetail.setOrderId(orderInfo.getOrderCode());
		orderPayDetail.setRefundNumber(refundNumber); // 退款号，自定义
		if (aliPayHandlerServiceImpl.refund(orderInfo, orderPayDetail)) {
			return "退款申请成功！";
		} else {
			return "退款申请失败！";
		}
	}

	/**
	 * 退款查询
	 * @param orderCode
	 * @param refundNumber
	 * @return
	 * @throws ServletException
	 * @throws IOException
	 * @throws AlipayApiException
	 */
	@RequestMapping("/refundQuery")
	public String refundQuery(String orderCode, String refundNumber)
			throws ServletException, IOException, AlipayApiException {
		OrderPayDetail orderPayDetail = new OrderPayDetail();
		orderPayDetail.setOrderId(orderCode);
		orderPayDetail.setRefundNumber(refundNumber);
		if (aliPayHandlerServiceImpl.refundQuery(orderPayDetail)) {
			return "退款成功！";
		} else {
			return "退款失败！";
		}
	}

	/**
	 * 支付查询
	 * @param orderCode
	 * @return
	 * @throws ServletException
	 * @throws IOException
	 * @throws AlipayApiException
	 */
	@RequestMapping("/payQuery")
	public String payQuery(String orderCode) throws ServletException, IOException, AlipayApiException {
		if (aliPayHandlerServiceImpl.payQuery(orderCode)) {
			return "支付成功！";
		} else {
			return "支付失败！";
		}
	}
	
	/**
	 * 支付关闭
	 * 备注：此接口只有在用户扫码后才能调用关闭，不然会提示订单不存在！也就是说用户跳到支付宝网站支付的时候订单还未创建，只有用户扫码后订单才创建。
	 * @param orderCode
	 * @param trade_no
	 * @return
	 * @throws ServletException
	 * @throws IOException
	 * @throws AlipayApiException
	 */
	@RequestMapping("/payClose")
	public String payClose(String orderCode,String trade_no) throws ServletException, IOException, AlipayApiException {
		System.out.println(orderCode+trade_no);
		if (aliPayHandlerServiceImpl.payClose(orderCode,trade_no)) {
			return "关闭成功！";
		} else {
			return "关闭失败！";
		}
	}

	/**
	 * 支付取消
	 * 备注：目前还不清楚这个接口的作用，每次都没调成功！
	 * @param orderCode
	 * @return
	 * @throws ServletException
	 * @throws IOException
	 * @throws AlipayApiException
	 */
	@RequestMapping("/payCancel")
	public String payCancel(String orderCode) throws ServletException, IOException, AlipayApiException {
		System.out.println(orderCode);
		if (aliPayHandlerServiceImpl.payCancel(orderCode)) {
			return "取消成功！";
		} else {
			return "取消失败！";
		}
	}
}