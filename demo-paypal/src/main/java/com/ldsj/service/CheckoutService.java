package com.ldsj.service;

/**
 * 新版本的checkout支付方式
 */
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.braintreepayments.http.HttpResponse;
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.orders.AmountBreakdown;
import com.paypal.orders.AmountWithBreakdown;
import com.paypal.orders.ApplicationContext;
import com.paypal.orders.Capture;
import com.paypal.orders.Customer;
import com.paypal.orders.Item;
import com.paypal.orders.LinkDescription;
import com.paypal.orders.Money;
import com.paypal.orders.Order;
import com.paypal.orders.OrderRequest;
import com.paypal.orders.OrdersCaptureRequest;
import com.paypal.orders.OrdersCreateRequest;
import com.paypal.orders.OrdersGetRequest;
import com.paypal.orders.PurchaseUnit;
import com.paypal.orders.PurchaseUnitRequest;
import com.paypal.payments.CapturesRefundRequest;
import com.paypal.payments.Refund;
import com.paypal.payments.RefundRequest;
import com.paypal.payments.RefundsGetRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CheckoutService {

	@Value("${paypal.client.app}")
    private String clientId;
    @Value("${paypal.client.secret}")
    private String clientSecret;
    @Value("${paypal.mode}")
    private String mode;
    
	public PayPalHttpClient getPayPalHttpClient() {
		PayPalEnvironment environment;
		if("live".equals(mode)) {
			environment = new PayPalEnvironment.Live(clientId,clientSecret); // 正式环境
		} else {
			environment = new PayPalEnvironment.Sandbox(clientId,clientSecret); // 测试环境
		}
		PayPalHttpClient client = new PayPalHttpClient(environment);
		return client;
	}
	
	/**
	 *	 创建订单
	 * @param brandName
	 * @param description
	 * @param amount
	 * @param cancelUrl
	 * @param successUrl
	 * @return
	 * @throws IOException
	 */
	public HttpResponse<Order> createOrder(String brandName,String description,String amount,String cancelUrl, String successUrl) throws IOException {
		OrdersCreateRequest request = new OrdersCreateRequest();
		request.header("prefer","return=representation");
		OrderRequest orderRequest = new OrderRequest();
		orderRequest.intent("CAPTURE");

		ApplicationContext applicationContext = new ApplicationContext().brandName(brandName).landingPage("LOGIN")
				.cancelUrl(cancelUrl).returnUrl(successUrl).userAction("PAY_NOW")
				.shippingPreference("GET_FROM_FILE");
		orderRequest.applicationContext(applicationContext);

		List<PurchaseUnitRequest> purchaseUnitRequests = new ArrayList<PurchaseUnitRequest>();
		PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest()
				.description(description)
				.amount(new AmountWithBreakdown().currencyCode("USD").value(amount)
						.breakdown(new AmountBreakdown().itemTotal(new Money().currencyCode("USD").value(amount))))
				.items(new ArrayList<Item>() {
					{
						add(new Item().name(brandName).description(description)
								.unitAmount(new Money().currencyCode("USD").value(amount)).quantity("1"));
					}
				});
		purchaseUnitRequests.add(purchaseUnitRequest);
		orderRequest.purchaseUnits(purchaseUnitRequests);
		
		request.requestBody(orderRequest);
		HttpResponse<Order> response = getPayPalHttpClient().execute(request);
		if (response.statusCode() == 201) {
			log.debug("Status Code: " + response.statusCode()+"-Status: " 
					+ response.result().status()+"-Order ID: " + response.result().id()
					+"Intent: " + response.result().intent()
					+"Links: ");
			for (LinkDescription link : response.result().links()) {
				log.debug("\t" + link.rel() + ": " + link.href() + "\tCall Type: " + link.method());
			}
			log.debug("Total Amount: " + response.result().purchaseUnits().get(0).amount().currencyCode()
					+ " " + response.result().purchaseUnits().get(0).amount().value());
			log.debug("Full response body:");
		}
		log.debug("Creating Order...");
        if (response.statusCode() == 201){
        	log.debug("Links:");
            for (LinkDescription link : response.result().links()) {
            	log.debug("\t" + link.rel() + ": " + link.href());
            }
        }
        log.debug("Created Successfully\n");
        return response;
	}
	
	/**
	 * 捕获订单，买家支付成功后需要调用
	 * @param orderId
	 * @param debug
	 * @return
	 * @throws IOException
	 */
	public HttpResponse<Order> captureOrder(String orderId) throws IOException {
		OrdersCaptureRequest request = new OrdersCaptureRequest(orderId);
		request.requestBody(new OrderRequest());
		HttpResponse<Order> response = getPayPalHttpClient().execute(request);
		log.debug("Status Code: " + response.statusCode()+"-Status: " 
				+ response.result().status()+"-Order ID: " + response.result().id()
				+"Intent: " + response.result().intent()
				+"Links: ");
		for (LinkDescription link : response.result().links()) {
			log.debug("\t" + link.rel() + ": " + link.href());
		}
		log.debug("Capture ids:");
		for (PurchaseUnit purchaseUnit : response.result().purchaseUnits()) {
			for (Capture capture : purchaseUnit.payments().captures()) {
				log.debug("\t" + capture.id());
			}
		}
		log.debug("Buyer: "+response.result().payer());
		Customer buyer = response.result().payer();
		log.debug("\tEmail Address: " + buyer.emailAddress());
		log.debug("\tName: " + buyer.name().fullName());
		log.debug("\tPhone Number: " + buyer.phone().countryCode() + buyer.phone().nationalNumber());
		log.debug("Full response body:");
		return response;
	}
	
	public HttpResponse<Refund> refundOrder(String captureId, String refundAmount) throws IOException {
		CapturesRefundRequest request = new CapturesRefundRequest(captureId);
		request.prefer("return=representation");
		
		RefundRequest refundRequest = new RefundRequest();
		com.paypal.payments.Money money = new com.paypal.payments.Money();
		money.currencyCode("USD");
		money.value(refundAmount);
		refundRequest.amount(money);
		
		request.requestBody(refundRequest);
		HttpResponse<Refund> response = getPayPalHttpClient().execute(request);
		log.debug("Status Code: " + response.statusCode()+"-Status: " 
				+ response.result().status()+"-Order ID: " + response.result().id()
				+"Refund Id: " + response.result().id()
				+"Links: ");
		for (com.paypal.payments.LinkDescription link : response.result().links()) {
			log.debug("\t" + link.rel() + ": " + link.href() + "\tCall Type: " + link.method());
		}
		log.debug("Full response body:");
		return response;
	}
	
	public boolean orderQuery(String orderId) throws IOException {
		OrdersGetRequest request = new OrdersGetRequest(orderId);
		HttpResponse<Order> response = getPayPalHttpClient().execute(request);
		log.debug("Full response body:");
		log.debug("Full response body:"+response.result().status());
		if("COMPLETED".equals(response.result().status()))
			return true;
		return false;
	}
	
	public boolean refundQuery(String refund_id) throws IOException {
		RefundsGetRequest request = new RefundsGetRequest(refund_id);
		HttpResponse<Refund> response = getPayPalHttpClient().execute(request);
		log.debug("Full response body:");
		if("COMPLETED".equals(response.result().status()))
			return true;
		return false;
	}
}
