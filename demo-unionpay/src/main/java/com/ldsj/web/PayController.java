package com.ldsj.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ldsj.service.UnionPayService;
import com.ldsj.utils.AcpService;
import com.ldsj.utils.DemoBase;
import com.ldsj.utils.LogUtil;
import com.ldsj.utils.PayUtils;
import com.ldsj.utils.SDKConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class PayController {
	
	@Autowired
	private UnionPayService unionPayService;

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
	 * @throws IOException 
	 * 
	 */
	@RequestMapping("/pay")
	public void pay(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String orderCode,
			String payAmount) throws URISyntaxException, IOException {
		httpServletResponse.setContentType("text/html; charset="+ DemoBase.encoding);
		log.debug(orderCode + payAmount);
		// 转换为分
		String amount=String.valueOf((new BigDecimal(payAmount)).multiply(new BigDecimal(100)).intValue());
		unionPayService.launchPay(orderCode, amount, httpServletRequest, httpServletResponse);
	}

	/**
	 * 支付完成后跳转地址
	 * 
	 * @return
	 * @throws IOException 
	 * @throws ServletException 
	 * @throws URISyntaxException 
	 */
	@RequestMapping("returnurl")
	public void returnurl(HttpServletRequest req, HttpServletResponse resp) throws ServletException, URISyntaxException, IOException {
		LogUtil.writeLog("FrontRcvResponse前台接收报文返回开始");
		String encoding = req.getParameter(SDKConstants.param_encoding);
		LogUtil.writeLog("返回报文中encoding=[" + encoding + "]");
		Map<String, String> respParam = PayUtils.getAllRequestParam(req);
		// 打印请求报文
		LogUtil.printRequestLog(respParam);
		log.debug("打印前台支付成功通知原始消息："+respParam.toString());
		Map<String, String> valideData = null;
		if (null != respParam && !respParam.isEmpty()) {
			Iterator<Entry<String, String>> it = respParam.entrySet()
					.iterator();
			valideData = new HashMap<String, String>(respParam.size());
			while (it.hasNext()) {
				Entry<String, String> e = it.next();
				String key = (String) e.getKey();
				String value = (String) e.getValue();
				value = new String(value.getBytes(encoding), encoding);
				valideData.put(key, value);
			}
		}
		if (!AcpService.validate(valideData, encoding)) {
			LogUtil.writeLog("验证签名结果[失败].");
		} else {
			LogUtil.writeLog("验证签名结果[成功].");
			log.debug("打印前台支付成功通知验签成功后的消息："+valideData.toString());
			System.out.println(valideData.get("orderId")); //其他字段也可用类似方式获取
			String respCode = valideData.get("respCode");
			//判断respCode=00、A6后，对涉及资金类的交易，请再发起查询接口查询，确定交易成功后更新数据库。
			System.out.println(respCode);
		}
		LogUtil.writeLog("FrontRcvResponse前台接收报文返回结束");
		URIBuilder urlBuilder = new URIBuilder("static/returnUrl.html");
		resp.setHeader("Location", urlBuilder.toString());
		resp.setStatus(HttpStatus.SC_MOVED_TEMPORARILY);
	}

	/**
	 * 支付成功后银联异步回调地址
	 * 
	 * @param paymentId
	 * @param payerId
	 * @return
	 * @throws IOException 
	 */
	@RequestMapping("successPay")
	public void successPay(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		unionPayService.notifyUrl(req, resp);
	}

	/**
	 * 申请退款
	 * @param req
	 * @param resp
	 * @return
	 * @throws IOException
	 */
	@RequestMapping("/refund")
	public String refund(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String origQryId = req.getParameter("origQryId");//原消费交易返回的的queryId，可以从消费交易后台通知接口中或者交易状态查询接口中获取
		String orderId = req.getParameter("orderId"); 
		String txnAmt = req.getParameter("txnAmt");
		// 转换为分 //****退货金额，单位分，不要带小数点。退货金额小于等于原消费金额，当小于的时候可以多次退货至退货累计金额等于原消费金额	
		String amount=String.valueOf((new BigDecimal(txnAmt)).multiply(new BigDecimal(100)).intValue());
		if (unionPayService.refund(origQryId, orderId,amount)) {
			return "退款申请成功！";
		} else {
			return "退款申请失败！";
		}
	}
	
	/**
	 * 支付结果查询
	 * @param req
	 * @param resp
	 * @return
	 * @throws IOException
	 */
	@RequestMapping("/payQuery")
	public String payQuery(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String orderId = req.getParameter("orderId");
		if (unionPayService.payQuery(orderId)) {
			return "支付成功！";
		} else {
			return "支付失败！";
		}
	}
	
	/**
	 * 退款结果查询
	 * @param req
	 * @return
	 */
	@RequestMapping("/refundQuery")
	public String refundQuery(HttpServletRequest req) {
		String orderId = req.getParameter("orderId");
		if (unionPayService.refundQuery(orderId)) {
			return "退款成功！";
		} else {
			return "退款失败！";
		}
	}
}