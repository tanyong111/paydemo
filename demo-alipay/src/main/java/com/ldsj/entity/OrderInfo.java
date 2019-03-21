package com.ldsj.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class OrderInfo {

	String id;
	String orderCode; //订单号
	String projectId; //订单关联的商品
	String userId; //订单关联的用户
	String status; //订单状态
	String payMethod; //支付方式
	BigDecimal payAmount; //支付金额
	String payType; //金额类型
	String createdBy;
	LocalDateTime createdTime;
	String updatedBy;
	LocalDateTime updatedTime;
	Boolean isDeleted;
	String payStatus; //支付状态
	BigDecimal rebackAmount; //退款金额
}
