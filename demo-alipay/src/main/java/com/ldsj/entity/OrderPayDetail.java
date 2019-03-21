package com.ldsj.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 	订单明细
 * @author tan
 *
 */
@Data
public class OrderPayDetail {

	String orderId;// 订单号
	String status;// 状态
	String refundNumber;// 退款号，此号在发起不完全退款时必传，查询退款时也是必传
	String createdBy;
	LocalDateTime createdTime;
}
