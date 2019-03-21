package com.ldsj.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;

/**
 * @author Binary Wang
 */
@Configuration
public class WxPayConfiguration {

  /**
	 * 微信支付配置
	 */
	@Value("${wx.pay.appId}")
	String appId; // 设置微信公众号或者小程序等的appid
	@Value("${wx.pay.mchId}")
	String mchId; // 微信支付商户号
	@Value("${wx.pay.mchKey}")
	String mchKey; // 微信支付商户密钥
	@Value("${wx.pay.keyPath}")
	String keyPath; // apiclient_cert.p12文件的绝对路径，或者如果放在项目中，请以classpath:开头指定

	@Bean
	public WxPayService wxService() {
		WxPayConfig payConfig = new WxPayConfig();
		payConfig.setAppId(StringUtils.trimToNull(appId));
		payConfig.setMchId(StringUtils.trimToNull(mchId));
		payConfig.setMchKey(StringUtils.trimToNull(mchKey));
		payConfig.setKeyPath(StringUtils.trimToNull(keyPath));
		// 可以指定是否使用沙箱环境
		payConfig.setUseSandboxEnv(false);
		WxPayService wxPayService = new WxPayServiceImpl();
		wxPayService.setConfig(payConfig);
		return wxPayService;
	}
}
