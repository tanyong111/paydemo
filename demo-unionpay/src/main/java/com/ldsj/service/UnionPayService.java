package com.ldsj.service;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ldsj.utils.AcpService;
import com.ldsj.utils.DemoBase;
import com.ldsj.utils.LogUtil;
import com.ldsj.utils.PayUtils;
import com.ldsj.utils.SDKConfig;
import com.ldsj.utils.SDKConstants;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class UnionPayService {

	@Value("${unionpay.merId}")
	String merId; // 40 仅支持JSON

	/**
	 * 发起支付
	 * 
	 * @throws IOException
	 * @throws AlipayApiException
	 */
	public void launchPay(String orderCode, String payAmount, HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse) throws IOException {
		log.info(orderCode + payAmount);
		// 前台页面传过来的
		String txnAmt = payAmount;
		String orderId = orderCode;
		String txnTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("YYYYMMddHHmmss"));
		Map<String, String> requestData = new HashMap<String, String>();
		/*** 银联全渠道系统，产品参数，除了encoding自行选择外其他不需修改 ***/
		requestData.put("version", DemoBase.version); // 版本号，全渠道默认值
		requestData.put("encoding", DemoBase.encoding); // 字符集编码，可以使用UTF-8,GBK两种方式
		requestData.put("signMethod", SDKConfig.getConfig().getSignMethod()); // 签名方法
		requestData.put("txnType", "01"); // 交易类型 ，01：消费
		requestData.put("txnSubType", "01"); // 交易子类型， 01：自助消费
		requestData.put("bizType", "000201"); // 业务类型，B2C网关支付，手机wap支付
		requestData.put("channelType", "07"); // 渠道类型，这个字段区分B2C网关支付和手机wap支付；07：PC,平板 08：手机

		/*** 商户接入参数 ***/
		requestData.put("merId", merId); // 商户号码，请改成自己申请的正式商户号或者open上注册得来的777测试商户号
		requestData.put("accessType", "0"); // 接入类型，0：直连商户
		requestData.put("orderId", orderId); // 商户订单号，8-40位数字字母，不能含“-”或“_”，可以自行定制规则
		System.out.println("txnTime:" + txnTime);
		requestData.put("txnTime", txnTime); // 订单发送时间，取系统时间，格式为YYYYMMDDhhmmss，必须取当前时间，否则会报txnTime无效
		requestData.put("currencyCode", "156"); // 交易币种（境内商户一般是156 人民币）
		requestData.put("txnAmt", txnAmt); // 交易金额，单位分，不要带小数点
		// requestData.put("reqReserved", "透传字段");
		// //请求方保留域，如需使用请启用即可；透传字段（可以实现商户自定义参数的追踪）本交易的后台通知,对本交易的交易状态查询交易、对账文件中均会原样返回，商户可以按需上传，长度为1-1024个字节。出现&={}[]符号时可能导致查询接口应答报文解析失败，建议尽量只传字母数字并使用|分割，或者可以最外层做一次base64编码(base64编码之后出现的等号不会导致解析失败可以不用管)。

		requestData.put("riskRateInfo", "{commodityName=测试商品名称}");

		// 前台通知地址 （需设置为外网能访问 http https均可），支付成功后的页面 点击“返回商户”按钮的时候将异步通知报文post到该地址
		// 如果想要实现过几秒中自动跳转回商户页面权限，需联系银联业务申请开通自动返回商户权限
		// 异步通知参数详见open.unionpay.com帮助中心 下载 产品接口规范 网关支付产品接口规范 消费交易 商户通知
		requestData.put("frontUrl", PayUtils.getContextUrl(httpServletRequest) + "returnurl");

		// 后台通知地址（需设置为【外网】能访问 http
		// https均可），支付成功后银联会自动将异步通知报文post到商户上送的该地址，失败的交易银联不会发送后台通知
		// 后台通知参数详见open.unionpay.com帮助中心 下载 产品接口规范 网关支付产品接口规范 消费交易 商户通知
		// 注意:1.需设置为外网能访问，否则收不到通知 2.http https均可 3.收单后台通知后需要10秒内返回http200或302状态码
		// 4.如果银联通知服务器发送通知后10秒内未收到返回状态码或者应答码非http200，那么银联会间隔一段时间再次发送。总共发送5次，每次的间隔时间为0,1,2,4分钟。
		// 5.后台通知地址如果上送了带有？的参数，例如：http://abc/web?a=b&c=d
		// 在后台通知处理程序验证签名之前需要编写逻辑将这些字段去掉再验签，否则将会验签失败
		requestData.put("backUrl", PayUtils.getContextUrl(httpServletRequest) + "successPay");

		// 订单超时时间。
		// 超过此时间后，除网银交易外，其他交易银联系统会拒绝受理，提示超时。 跳转银行网银交易如果超时后交易成功，会自动退款，大约5个工作日金额返还到持卡人账户。
		// 此时间建议取支付时的北京时间加15分钟。
		// 超过超时时间调查询接口应答origRespCode不是A6或者00的就可以判断为失败。
		requestData.put("payTimeout",
				new SimpleDateFormat("yyyyMMddHHmmss").format(new Date().getTime() + 15 * 60 * 1000));

		//////////////////////////////////////////////////
		//
		// 报文中特殊用法请查看 PCwap网关跳转支付特殊用法.txt
		//
		//////////////////////////////////////////////////

		/** 请求参数设置完毕，以下对请求参数进行签名并生成html表单，将表单写入浏览器跳转打开银联页面 **/
		Map<String, String> submitFromData = AcpService.sign(requestData, DemoBase.encoding); // 报文中certId,signature的值是在signData方法中获取并自动赋值的，只要证书配置正确即可。

		String requestFrontUrl = SDKConfig.getConfig().getFrontRequestUrl(); // 获取请求银联的前台地址：对应属性文件acp_sdk.properties文件中的acpsdk.frontTransUrl
		String html = AcpService.createAutoFormHtml(requestFrontUrl, submitFromData, DemoBase.encoding); // 生成自动跳转的Html表单

		LogUtil.writeLog("打印请求HTML，此为请求报文，为联调排查问题的依据：" + html);
		log.info("支付请求回来的html:" + html);
		httpServletResponse.setContentType("text/html; charset=" + DemoBase.encoding);
		httpServletResponse.getWriter().write(html);
		httpServletResponse.getWriter().flush();
		httpServletResponse.getWriter().close();
	}

	/**
	 * 异步通知回调处理方法
	 * 
	 * @param inputParams
	 * @param httpRequest
	 * @param httpResponse
	 * @throws AlipayApiException
	 * @throws IOException
	 */
	public void notifyUrl(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
		LogUtil.writeLog("BackRcvResponse接收后台通知开始");
		String encoding = httpRequest.getParameter(SDKConstants.param_encoding);
		// 获取银联通知服务器发送的后台通知参数
		Map<String, String> reqParam = PayUtils.getAllRequestParam(httpRequest);
		LogUtil.printRequestLog(reqParam);
		log.info("打印后台异步支付成功通知原始消息：" + reqParam.toString());
		// 重要！验证签名前不要修改reqParam中的键值对的内容，否则会验签不过
		Map<String, String> valideData = null;
		if (null != reqParam && !reqParam.isEmpty()) {
			Iterator<Entry<String, String>> it = reqParam.entrySet().iterator();
			valideData = new HashMap<String, String>(reqParam.size());
			while (it.hasNext()) {
				Entry<String, String> e = it.next();
				String key = (String) e.getKey();
				String value = (String) e.getValue();
				valideData.put(key, value);
			}
		}
		if (!AcpService.validate(valideData, encoding)) {
			LogUtil.writeLog("验证签名结果[失败].");
			// 验签失败，需解决验签问题
			httpResponse.sendError(HttpStatus.SC_NOT_ACCEPTABLE, "validatefail");
		} else {
			LogUtil.writeLog("验证签名结果[成功].");
			// 【注：为了安全验签成功才应该写商户的成功处理逻辑】交易成功，更新商户订单状态
			log.info("打印后台异步支付成功通知验签成功后的消息：" + valideData.toString());
			String orderId = valideData.get("orderId"); // 获取后台通知的数据，其他字段也可用类似方式获取
			String respCode = valideData.get("respCode");
			String settleAmt = valideData.get("settleAmt");
			String txnAmt = valideData.get("txnAmt");
			// 判断respCode=00、A6后，对涉及资金类的交易，请再发起查询接口查询，确定交易成功后更新数据库。
			System.out.println(
					"orderId:" + orderId + "respCode:" + respCode + "settleAmt:" + settleAmt + "respCode:" + txnAmt);
			// 返回给银联服务器http 200 状态码
			httpResponse.getWriter().print("ok");
		}
		LogUtil.writeLog("BackRcvResponse接收后台通知结束");
		httpResponse.setStatus(HttpStatus.SC_OK);
		httpResponse.getWriter().close();
	}

	/**
	 * 支付宝退款调用
	 * 
	 * @param orderInfo
	 * @throws AlipayApiException
	 * @throws JsonProcessingException
	 */
	public boolean refund(String origQryId, String orderId, String txnAmt) {
		Map<String, String> data = new HashMap<String, String>();

		/*** 银联全渠道系统，产品参数，除了encoding自行选择外其他不需修改 ***/
		data.put("version", DemoBase.version); // 版本号
		data.put("encoding", DemoBase.encoding); // 字符集编码 可以使用UTF-8,GBK两种方式
		data.put("signMethod", SDKConfig.getConfig().getSignMethod()); // 签名方法
		data.put("txnType", "04"); // 交易类型 04-退货
		data.put("txnSubType", "00"); // 交易子类型 默认00
		data.put("bizType", "000201"); // 业务类型 B2C网关支付，手机wap支付
		data.put("channelType", "07"); // 渠道类型，07-PC，08-手机

		/*** 商户接入参数 ***/
		data.put("merId", merId); // 商户号码，请改成自己申请的商户号或者open上注册得来的777商户号测试
		data.put("accessType", "0"); // 接入类型，商户接入固定填0，不需修改
		data.put("orderId", orderId);// 不能跟支付传入的orderId相同 //商户订单号，8-40位数字字母，不能含“-”或“_”，可以自行定制规则，重新产生，不同于原消费
		data.put("txnTime", DemoBase.getCurrentTime()); // 订单发送时间，格式为YYYYMMDDhhmmss，必须取当前时间，否则会报txnTime无效
		data.put("currencyCode", "156"); // 交易币种（境内商户一般是156 人民币）
		data.put("txnAmt", txnAmt); // ****退货金额，单位分，不要带小数点。退货金额小于等于原消费金额，当小于的时候可以多次退货至退货累计金额等于原消费金额
		// data.put("reqReserved", "透传信息");
		// //请求方保留域，如需使用请启用即可；透传字段（可以实现商户自定义参数的追踪）本交易的后台通知,对本交易的交易状态查询交易、对账文件中均会原样返回，商户可以按需上传，长度为1-1024个字节。出现&={}[]符号时可能导致查询接口应答报文解析失败，建议尽量只传字母数字并使用|分割，或者可以最外层做一次base64编码(base64编码之后出现的等号不会导致解析失败可以不用管)。
		// 不需要发后台通知，可以固定上送http://www.specialUrl.com
		data.put("backUrl", "http://www.specialUrl.com"); // 后台通知地址，后台通知参数详见open.unionpay.com帮助中心 下载 产品接口规范 网关支付产品接口规范
															// 退货交易 商户通知,其他说明同消费交易的后台通知

		/*** 要调通交易以下字段必须修改 ***/
		data.put("origQryId", origQryId); // ****原消费交易返回的的queryId，可以从消费交易后台通知接口中或者交易状态查询接口中获取

		/** 请求参数设置完毕，以下对请求参数进行签名并发送http post请求，接收同步应答报文-------------> **/
		Map<String, String> reqData = AcpService.sign(data, DemoBase.encoding);// 报文中certId,signature的值是在signData方法中获取并自动赋值的，只要证书配置正确即可。
		String url = SDKConfig.getConfig().getBackRequestUrl();// 交易请求url从配置文件读取对应属性文件acp_sdk.properties中的
																// acpsdk.backTransUrl

		Map<String, String> rspData = AcpService.post(reqData, url, DemoBase.encoding);// 这里调用signData之后，调用submitUrl之前不能对submitFromData中的键值对做任何修改，如果修改会导致验签不通过

		/** 对应答码的处理，请根据您的业务逻辑来编写程序,以下应答码处理逻辑仅供参考-------------> **/
		// 应答码规范参考open.unionpay.com帮助中心 下载 产品接口规范 《平台接入接口规范-第5部分-附录》
		if (!rspData.isEmpty()) {
			if (AcpService.validate(rspData, DemoBase.encoding)) {
				LogUtil.writeLog("验证签名成功");
				log.info("打印退款验签成功消息："+rspData.toString());
				String respCode = rspData.get("respCode");
				if ("00".equals(respCode)) {
					// 交易已受理，等待接收后台通知更新订单状态,也可以主动发起 查询交易确定交易状态。
					// TODO
					return true;
				} else if ("03".equals(respCode) || "04".equals(respCode) || "05".equals(respCode)) {
					// 后续需发起交易状态查询交易确定交易状态
					// TODO
					return true;
				} else {
					// 其他应答码为失败请排查原因
					// TODO
					return false;
				}
			} else {
				LogUtil.writeErrorLog("验证签名失败");
				// TODO 检查验证签名失败的原因
				return false;
			}
		} else {
			// 未返回正确的http状态
			LogUtil.writeErrorLog("未获取到返回报文或返回http状态码非200");
		}
//		String reqMessage = DemoBase.genHtmlResult(reqData);
//		String rspMessage = DemoBase.genHtmlResult(rspData);
		return false;
	}

	/**
	 * 退款查询
	 * 
	 * @param orderPayDetail
	 * @return
	 * @throws AlipayApiException
	 * @throws JsonProcessingException
	 */
	public boolean refundQuery(String orderId) {
		String txnTime = DemoBase.getCurrentTime();

		Map<String, String> data = new HashMap<String, String>();

		/*** 银联全渠道系统，产品参数，除了encoding自行选择外其他不需修改 ***/
		data.put("version", DemoBase.version); // 版本号
		data.put("encoding", DemoBase.encoding); // 字符集编码 可以使用UTF-8,GBK两种方式
		data.put("signMethod", SDKConfig.getConfig().getSignMethod()); // 签名方法
		data.put("txnType", "00"); // 交易类型 00-默认
		data.put("txnSubType", "00"); // 交易子类型 默认00
		data.put("bizType", "000201"); // 业务类型 B2C网关支付，手机wap支付

		/*** 商户接入参数 ***/
		data.put("merId", merId); // 商户号码，请改成自己申请的商户号或者open上注册得来的777商户号测试
		data.put("accessType", "0"); // 接入类型，商户接入固定填0，不需修改

		/*** 要调通交易以下字段必须修改 ***/
		data.put("orderId", orderId); // ****商户订单号，每次发交易测试需修改为被查询的交易的订单号
		data.put("txnTime", txnTime); // ****订单发送时间，每次发交易测试需修改为被查询的交易的订单发送时间

		/** 请求参数设置完毕，以下对请求参数进行签名并发送http post请求，接收同步应答报文-------------> **/

		Map<String, String> reqData = AcpService.sign(data, DemoBase.encoding);// 报文中certId,signature的值是在signData方法中获取并自动赋值的，只要证书配置正确即可。

		String url = SDKConfig.getConfig().getSingleQueryUrl();// 交易请求url从配置文件读取对应属性文件acp_sdk.properties中的
																// acpsdk.singleQueryUrl
		// 这里调用signData之后，调用submitUrl之前不能对submitFromData中的键值对做任何修改，如果修改会导致验签不通过
		Map<String, String> rspData = AcpService.post(reqData, url, DemoBase.encoding);

		/** 对应答码的处理，请根据您的业务逻辑来编写程序,以下应答码处理逻辑仅供参考-------------> **/
		// 应答码规范参考open.unionpay.com帮助中心 下载 产品接口规范 《平台接入接口规范-第5部分-附录》
		if (!rspData.isEmpty()) {
			if (AcpService.validate(rspData, DemoBase.encoding)) {
				LogUtil.writeLog("验证签名成功");
				log.info("打印退款查询验签成功后的消息："+rspData.toString());
				if ("00".equals(rspData.get("respCode"))) {// 如果查询交易成功
					// 处理被查询交易的应答码逻辑
					String origRespCode = rspData.get("origRespCode");
					if ("00".equals(origRespCode)) {
						// 交易成功，更新商户订单状态
						// TODO
						String settleAmt = rspData.get("settleAmt");
						String txnAmt = rspData.get("txnAmt");
						System.out.println("settleAmt" + settleAmt + "txnAmt" + txnAmt);
						return true;
					} else if ("03".equals(origRespCode) || "04".equals(origRespCode) || "05".equals(origRespCode)) {
						// 需再次发起交易状态查询交易
						// TODO
						return false;
					} else {
						// 其他应答码为失败请排查原因
						// TODO
						return false;
					}
				} else {// 查询交易本身失败，或者未查到原交易，检查查询交易报文要素
						// TODO
					return false;
				}
			} else {
				LogUtil.writeErrorLog("验证签名失败");
				// TODO 检查验证签名失败的原因
			}
		} else {
			// 未返回正确的http状态
			LogUtil.writeErrorLog("未获取到返回报文或返回http状态码非200");
		}
//		String reqMessage = DemoBase.genHtmlResult(reqData);
//		String rspMessage = DemoBase.genHtmlResult(rspData);
		return false;
	}

	/**
	 * 支付查询
	 * 
	 * @param out_trade_no
	 * @return true支付成功，false支付失败
	 * @throws AlipayApiException
	 */
	public boolean payQuery(String orderId) {
		String txnTime = DemoBase.getCurrentTime();

		Map<String, String> data = new HashMap<String, String>();

		/*** 银联全渠道系统，产品参数，除了encoding自行选择外其他不需修改 ***/
		data.put("version", DemoBase.version); // 版本号
		data.put("encoding", DemoBase.encoding); // 字符集编码 可以使用UTF-8,GBK两种方式
		data.put("signMethod", SDKConfig.getConfig().getSignMethod()); // 签名方法
		data.put("txnType", "00"); // 交易类型 00-默认
		data.put("txnSubType", "00"); // 交易子类型 默认00
		data.put("bizType", "000201"); // 业务类型 B2C网关支付，手机wap支付

		/*** 商户接入参数 ***/
		data.put("merId", merId); // 商户号码，请改成自己申请的商户号或者open上注册得来的777商户号测试
		data.put("accessType", "0"); // 接入类型，商户接入固定填0，不需修改

		/*** 要调通交易以下字段必须修改 ***/
		data.put("orderId", orderId); // ****商户订单号，每次发交易测试需修改为被查询的交易的订单号
		data.put("txnTime", txnTime); // ****订单发送时间，每次发交易测试需修改为被查询的交易的订单发送时间

		/** 请求参数设置完毕，以下对请求参数进行签名并发送http post请求，接收同步应答报文-------------> **/

		Map<String, String> reqData = AcpService.sign(data, DemoBase.encoding);// 报文中certId,signature的值是在signData方法中获取并自动赋值的，只要证书配置正确即可。

		String url = SDKConfig.getConfig().getSingleQueryUrl();// 交易请求url从配置文件读取对应属性文件acp_sdk.properties中的
																// acpsdk.singleQueryUrl
		// 这里调用signData之后，调用submitUrl之前不能对submitFromData中的键值对做任何修改，如果修改会导致验签不通过
		Map<String, String> rspData = AcpService.post(reqData, url, DemoBase.encoding);

		/** 对应答码的处理，请根据您的业务逻辑来编写程序,以下应答码处理逻辑仅供参考-------------> **/
		// 应答码规范参考open.unionpay.com帮助中心 下载 产品接口规范 《平台接入接口规范-第5部分-附录》
		if (!rspData.isEmpty()) {
			if (AcpService.validate(rspData, DemoBase.encoding)) {
				LogUtil.writeLog("验证签名成功");
				log.info("打印支付查询验签成功后的消息："+rspData.toString());
				if ("00".equals(rspData.get("respCode"))) {// 如果查询交易成功
					// 处理被查询交易的应答码逻辑
					String origRespCode = rspData.get("origRespCode");
					if ("00".equals(origRespCode)) {
						// 交易成功，更新商户订单状态
						// TODO
						String settleAmt = rspData.get("settleAmt");
						String txnAmt = rspData.get("txnAmt");
						System.out.println("settleAmt" + settleAmt + "txnAmt" + txnAmt);
						return true;
					} else if ("03".equals(origRespCode) || "04".equals(origRespCode) || "05".equals(origRespCode)) {
						// 需再次发起交易状态查询交易
						// TODO
						return false;
					} else {
						// 其他应答码为失败请排查原因
						// TODO
						return false;
					}
				} else {// 查询交易本身失败，或者未查到原交易，检查查询交易报文要素
						// TODO
					return false;
				}
			} else {
				LogUtil.writeErrorLog("验证签名失败");
				// TODO 检查验证签名失败的原因
			}
		} else {
			// 未返回正确的http状态
			LogUtil.writeErrorLog("未获取到返回报文或返回http状态码非200");
		}
//		String reqMessage = DemoBase.genHtmlResult(reqData);
//		String rspMessage = DemoBase.genHtmlResult(rspData);
		return false;
	}

//	/**
//	 * 	支付关闭
//	 * @param out_trade_no
//	 * @return
//	 * @throws AlipayApiException
//	 * @throws JsonProcessingException 
//	 */
//	public boolean payClose(String out_trade_no,String trade_no) throws AlipayApiException, JsonProcessingException {
//		Map<String, String> paramsMap = new HashMap<String, String>();
//		paramsMap.put("out_trade_no", out_trade_no);
//		paramsMap.put("trade_no", trade_no);
//		AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
//		request.setBizContent(objectMapper.writeValueAsString(paramsMap));
//		AlipayTradeCloseResponse response = alipayClient.execute(request);
//		if (response.isSuccess()) {
//			System.out.println("调用成功");
//			return true;
//		} else {
//			System.out.println("调用失败");
//			return false;
//		}
//	}
//	
//	public boolean payCancel(String out_trade_no) throws JsonProcessingException, AlipayApiException {
//		Map<String, String> paramsMap = new HashMap<String, String>();
//		paramsMap.put("out_trade_no", out_trade_no);
//		AlipayTradeCancelRequest request = new AlipayTradeCancelRequest();
//		request.setBizContent(objectMapper.writeValueAsString(paramsMap));
//		AlipayTradeCancelResponse response = alipayClient.execute(request);
//		if (response.isSuccess()) {
//			System.out.println("调用成功");
//			return true;
//		} else {
//			System.out.println("调用失败");
//			return false;
//		}
//	}
//	
//	/**
//	 * 	查询对账单下载地址
//	 * @return
//	 * @throws AlipayApiException
//	 */
//	public String payBillDownloadurl() throws AlipayApiException {
//		AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
//		request.setBizContent("{" +
//		"\"bill_type\":\"trade\"," +
//		"\"bill_date\":\"2016-04-05\"" +
//		"  }");
//		AlipayDataDataserviceBillDownloadurlQueryResponse response = alipayClient.execute(request);
//		if(response.isSuccess()){
//			System.out.println("调用成功");
//			return response.getBillDownloadUrl();
//		} else {
//			System.out.println("调用失败");
//			return null;
//		}
//	}
//	
//	/**
//	 * 	发票下载链接获取
//	 * 	参数较复杂，不理解
//	 * @throws AlipayApiException 
//	 */
//	public void InvoiceInfoQuery() throws AlipayApiException {
//		AlipayEbppInvoiceInfoSendRequest request = new AlipayEbppInvoiceInfoSendRequest();
//		request.setBizContent("{" +
//		"\"m_short_name\":\"XSD\"," +
//		"\"sub_m_short_name\":\"XSD_HL\"," +
//		"      \"invoice_info_list\":[{" +
//		"        \"user_id\":\"2088399922382233\"," +
//		"\"apply_id\":\"2016112800152005000000000239\"," +
//		"\"invoice_code\":\"4112740003\"," +
//		"\"invoice_no\":\"41791003\"," +
//		"\"invoice_date\":\"2017-10-10\"," +
//		"\"sum_amount\":\"101.00\"," +
//		"\"ex_tax_amount\":\"100.00\"," +
//		"\"tax_amount\":\"1.00\"," +
//		"          \"invoice_content\":[{" +
//		"            \"item_name\":\"餐饮费\"," +
//		"\"item_no\":\"1010101990000000000\"," +
//		"\"item_spec\":\"G39\"," +
//		"\"item_unit\":\"台\"," +
//		"\"item_quantity\":1," +
//		"\"item_unit_price\":\"100.00\"," +
//		"\"item_ex_tax_amount\":\"100.00\"," +
//		"\"item_tax_rate\":\"0.01\"," +
//		"\"item_tax_amount\":\"1.00\"," +
//		"\"item_sum_amount\":\"101.00\"," +
//		"\"row_type\":\"0\"" +
//		"            }]," +
//		"\"out_trade_no\":\"20171023293456785924325\"," +
//		"\"invoice_type\":\"BLUE\"," +
//		"\"invoice_kind\":\"PLAIN\"," +
//		"\"invoice_title\":{" +
//		"\"title_name\":\"支付宝（中国）网络技术有限公司\"," +
//		"\"payer_register_no\":\"9133010060913454XP\"," +
//		"\"payer_address_tel\":\"杭州市西湖区天目山路黄龙时代广场0571-11111111\"," +
//		"\"payer_bank_name_account\":\"中国建设银行11111111\"" +
//		"        }," +
//		"\"payee_register_no\":\"310101000000090\"," +
//		"\"payee_register_name\":\"支付宝（杭州）信息技术有限公司\"," +
//		"\"payee_address_tel\":\"杭州市西湖区某某办公楼 0571-237405862\"," +
//		"\"payee_bank_name_account\":\"西湖区建行11111111111\"," +
//		"\"check_code\":\"15170246985745164986\"," +
//		"\"out_invoice_id\":\"201710283459661232435535\"," +
//		"\"ori_blue_inv_code\":\"4112740002\"," +
//		"\"ori_blue_inv_no\":\"41791002\"," +
//		"\"file_download_type\":\"PDF\"," +
//		"\"file_download_url\":\"http://img.hadalo.com/aa/kq/ddhrtdefgxKVXXXXa6apXXXXXXXXXX.pdf\"," +
//		"\"payee\":\"张三\"," +
//		"\"checker\":\"李四\"," +
//		"\"clerk\":\"赵吴\"," +
//		"\"invoice_memo\":\"订单号：2017120800001\"," +
//		"\"extend_fields\":\"m_invoice_detail_url=http://196.021.871.011:8080/invoice/detail.action?fpdm= 4112740003&fphm=41791003\"" +
//		"        }]" +
//		"  }");
//		AlipayEbppInvoiceInfoSendResponse response = alipayClient.execute(request);
//		if(response.isSuccess()){
//		System.out.println("调用成功");
//		} else {
//		System.out.println("调用失败");
//		}
//	}
}
