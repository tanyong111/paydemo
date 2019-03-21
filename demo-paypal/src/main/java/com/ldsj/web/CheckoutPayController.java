package com.ldsj.web;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.braintreepayments.http.HttpResponse;
import com.ldsj.service.CheckoutService;
import com.ldsj.utils.PayUtils;
import com.paypal.orders.LinkDescription;
import com.paypal.orders.Order;
import com.paypal.payments.Refund;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/checkout")
public class CheckoutPayController {

	@Autowired
	private CheckoutService checkoutService;

	/**
	 * 支付首页
	 * 
	 * @param httpServletResponse
	 * @throws URISyntaxException
	 */
	@RequestMapping("/")
	public void index(HttpServletResponse httpServletResponse) throws URISyntaxException {
		URIBuilder urlBuilder = new URIBuilder("../static/checkout/index.html");
		httpServletResponse.setHeader("Location", urlBuilder.toString());
		httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
	}

	/**
	 * 支付
	 * 
	 * @throws URISyntaxException
	 * 
	 */
	@RequestMapping("/pay")
	public void pay(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String orderCode,
			String payAmount) throws URISyntaxException {
		log.debug(orderCode + payAmount);
		try {
			String brandName="我的测试网站支付";
			String description="测试网站支付";
			String amount=payAmount;
			HttpResponse<Order> response=checkoutService.createOrder(brandName, description, amount,PayUtils.getContextUrl(httpServletRequest) + "checkout/cancelPay", PayUtils.getContextUrl(httpServletRequest) + "checkout/successPay");
			if (response.statusCode() == 201){
	            String orderId = response.result().id();
	            log.debug("orderId:"+orderId);
	            log.debug("Links:");
	            for (LinkDescription link : response.result().links()) {
	            	if (link.rel().equals("approve")) {
						URIBuilder urlBuilder = new URIBuilder(link.href());
						httpServletResponse.setHeader("Location", urlBuilder.toString());
						httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
						return;
					}
	            }
	        }
		} catch (IOException e1) {
			log.error(e1.getMessage());
		}
		URIBuilder urlBuilder = new URIBuilder("static/error.html");
		httpServletResponse.setHeader("Location", urlBuilder.toString());
		httpServletResponse.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
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
	 * @throws IOException 
	 */
	@RequestMapping(method = RequestMethod.GET, value = "successPay")
	public String successPay(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
		Map<String, String> receiveMap = getReceiveMap(httpServletRequest);
		log.debug("receiveMap:"+receiveMap.toString());
		//{PayerID=QD863G8ZNQXML, token=3FH08899JN513853U} token就是订单id
		HttpResponse<Order> captureOrder = checkoutService.captureOrder(receiveMap.get("token"));
		if("APPROVED".equals(captureOrder.result().status()) || "COMPLETED".equals(captureOrder.result().status())) {
			return "success";
		}
		return "fail";
	}
	
	/**
	 * 支付结果查询
	 * @param orderId  发起订单时返回的订单号
	 * @return
	 * @throws IOException
	 */
	@RequestMapping("payQuery")
	public String payQuery(String orderId) throws IOException {
		if(checkoutService.orderQuery(orderId))
			return "success";
		return "fail";
	}

	/**
	 * 退款申请
	 * @param captureId 就是在买家支付成功后，我们执行captureOrder里面捕获的一个captureId
	 * @param refundAmount
	 * @return
	 * @throws IOException
	 */
	@RequestMapping("/refund")
	public String refund(String captureId,String refundAmount) throws IOException {
		HttpResponse<Refund> refundOrder = checkoutService.refundOrder(captureId, refundAmount);
		if("COMPLETED".equals(refundOrder.result().status()) || "PENDING".equals(refundOrder.result().status())) {
			return "success";
		}
		return "fail";
	}
	
	/**
	 * 退款结果查询
	 * @param refund_id 退款时返回的refund_id
	 * @return
	 * @throws IOException
	 */
	@RequestMapping("refundQuery")
	public String refundQuery(String refund_id) throws IOException {
		if(checkoutService.refundQuery(refund_id))
			return "success";
		return "fail";
	}
	
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
}