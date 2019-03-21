package com.ldsj.service;

/**
 * 老版本的普通支付方式
 */
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ldsj.utils.PayUtils;
import com.paypal.api.payments.Amount;
import com.paypal.api.payments.DetailedRefund;
import com.paypal.api.payments.Item;
import com.paypal.api.payments.ItemList;
import com.paypal.api.payments.Links;
import com.paypal.api.payments.Payer;
import com.paypal.api.payments.Payment;
import com.paypal.api.payments.PaymentExecution;
import com.paypal.api.payments.RedirectUrls;
import com.paypal.api.payments.Refund;
import com.paypal.api.payments.RefundRequest;
import com.paypal.api.payments.RelatedResources;
import com.paypal.api.payments.Sale;
import com.paypal.api.payments.Transaction;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PaypalService {

	@Autowired
	private APIContext aPIContext;

    /**
     * 发起支付
     * @param httpRequest
     * @param httpResponse
     * @param payAmount
     * @param description
     * @return
     * @throws PayPalRESTException
     */
	public String launchPay(HttpServletRequest httpRequest,
			HttpServletResponse httpResponse,BigDecimal payAmount,String description) throws PayPalRESTException {
		Payment payment = createPayment(payAmount, "USD", description,
				PayUtils.getContextUrl(httpRequest)+"cancelPay", PayUtils.getContextUrl(httpRequest)+"successPay");
		//交易id
		log.debug("payment.getId():"+payment.getId());
		log.debug(payment.toJSON());
		for (Links links : payment.getLinks()) {
			if ("approval_url".equalsIgnoreCase(links.getRel())) {
				return links.getHref();
			}
		}
		return null;
	}
	
	/**
	 * 	用户确认支付
	 * @param paymentId
	 * @param payerId
	 * @return
	 * @throws Exception 
	 */
	public boolean paypalConfirmPay(String paymentId,String payerId) throws Exception {
		Payment payment = executePayment(paymentId, payerId);
		log.info(payment.toJSON());
		// 支付验证
		boolean isSuccess = false;
		if ("approved".equalsIgnoreCase(payment.getState())) {
			List<Transaction> transactions = payment.getTransactions();
			for(Transaction transaction:transactions) {
				List<RelatedResources> relatedResources = transaction.getRelatedResources();
				for(RelatedResources relatedResource:relatedResources) {
					Sale sale = relatedResource.getSale();
					if(sale!=null && "completed".equalsIgnoreCase(sale.getState())) {
						Amount amount = sale.getAmount();
						if(amount!=null) {
							// 对支付结果中的业务内容进行二次校验
							String orderCode=paymentId;
							BigDecimal payAmount=new BigDecimal(amount.getTotal());
							LocalDateTime payTime=LocalDateTime.now();
							//业务验证和处理
							log.debug(orderCode+payAmount+payTime);
							isSuccess=true;
						}
					}
				}
			}
		}
		return isSuccess;
	}
	
	/**
	 * 发起退款
	 * @param paymentId
	 * @param refundAmount
	 * @return
	 * @throws PayPalRESTException
	 */
	public boolean refund(String paymentId,BigDecimal refundAmount) throws PayPalRESTException {
		Payment payment = Payment.get(aPIContext, paymentId);
		List<Transaction> transactions = payment.getTransactions();
		for(Transaction transaction:transactions) {
			List<RelatedResources> relatedResources = transaction.getRelatedResources();
			for(RelatedResources relatedResource:relatedResources) {
				Sale oldSale = relatedResource.getSale();
				if(oldSale!=null) {
					Sale sale = new Sale();
					sale.setId(oldSale.getId());
					Amount amount = new Amount();
					amount.setTotal(refundAmount.toString());
					amount.setCurrency("USD");
					RefundRequest refundRequest = new RefundRequest();
					refundRequest.setAmount(amount);
					// Refund sale
					DetailedRefund detailedRefund = sale.refund(aPIContext, refundRequest);
					log.info(detailedRefund.toJSON());
					if ("pending".equalsIgnoreCase(detailedRefund.getState()) || "completed".equalsIgnoreCase(detailedRefund.getState())) {
						return true;
					} else {
						return false;
					}
				}
			}
		}
		return false;
	}
	
	//退款查询
	public boolean refundQuery(String paymentId) throws PayPalRESTException {
		Payment payment = Payment.get(aPIContext, paymentId);
		List<Transaction> transactions = payment.getTransactions();
		for(Transaction transaction:transactions) {
			List<RelatedResources> relatedResources = transaction.getRelatedResources();
			for(RelatedResources relatedResource:relatedResources) {
				Refund refund = relatedResource.getRefund();
				if(refund!=null && "completed".equalsIgnoreCase(refund.getState())) {
					return true;
				}
			}
		}
		return false;
	}

	// 支付查询
	public boolean orderQuery(String paymentId) throws PayPalRESTException {
		Payment payment = Payment.get(aPIContext, paymentId);
		log.info(payment.toJSON());
		if ("approved".equalsIgnoreCase(payment.getState())) {
			List<Transaction> transactions = payment.getTransactions();
			for(Transaction transaction:transactions) {
				List<RelatedResources> relatedResources = transaction.getRelatedResources();
				for(RelatedResources relatedResource:relatedResources) {
					Sale sale = relatedResource.getSale();
					if(sale!=null && "completed".equalsIgnoreCase(sale.getState())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	//创建Payment
	private Payment createPayment(BigDecimal total, String currency, String description, String cancelUrl,
			String successUrl) throws PayPalRESTException {

		Amount amount = new Amount();
		amount.setCurrency(currency);
		amount.setTotal(total.toString());

		Transaction transaction = new Transaction();
		transaction.setDescription(description);
		transaction.setAmount(amount);
		
		Item item = new Item();
		item.setName(description).setQuantity("1").setCurrency(currency).setPrice(amount.getTotal());
		ItemList itemList = new ItemList();
		List<Item> items = new ArrayList<Item>();
		items.add(item);
		itemList.setItems(items);
		transaction.setItemList(itemList);

		List<Transaction> transactions = new ArrayList<>();
		transactions.add(transaction);

		Payer payer = new Payer();
		payer.setPaymentMethod("paypal");

		Payment payment = new Payment();
		payment.setIntent("sale");
		payment.setPayer(payer);
		payment.setTransactions(transactions);
		RedirectUrls redirectUrls = new RedirectUrls();
		redirectUrls.setCancelUrl(cancelUrl);
		redirectUrls.setReturnUrl(successUrl);
		payment.setRedirectUrls(redirectUrls);
		return payment.create(aPIContext);
	}

	//执行Payment
	private Payment executePayment(String paymentId, String payerId) throws PayPalRESTException {
		Payment payment = new Payment();
		payment.setId(paymentId);
		PaymentExecution paymentExecute = new PaymentExecution();
		paymentExecute.setPayerId(payerId);
		return payment.execute(aPIContext, paymentExecute);
	}
}
