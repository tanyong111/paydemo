package com.ldsj.config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ldsj.utils.wxpay.IWXPayDomain;
import com.ldsj.utils.wxpay.WXPayConfig;
import com.ldsj.utils.wxpay.WXPayConstants;

import lombok.extern.slf4j.Slf4j;

/**
 * 自定义微信支付配置类
 * 本类跟微信提供的类不一样的地方在于初始化加载证书是放在get方法里面加载，因为构造方法里面加载就不能获取到配置文件里面配置的证书路径了
 * @author tan
 *
 */
@Slf4j
@Component
public class DefaultWXPayConfig extends WXPayConfig {
	@Value("${wx.certpath}")
	private String certpath;
	@Value("${wx.app-id}")
	private String appId;
	@Value("${wx.mch-id}")
	private String mchId;
	@Value("${wx.key}")
	private String key;
	
	private byte[] certData;

	public DefaultWXPayConfig() throws Exception {}

	public String getAppID() {
		return appId;
	}

	public String getMchID() {
		return mchId;
	}

	public String getKey() {
		return key;
	}

	public InputStream getCertStream() {
		synchronized (this) {
			if(certData==null) {
				try (InputStream inputStream = this.getClass().getResourceAsStream(certpath);
						ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
					byte[] buffer = new byte[1024];
					int len = 0;
					while ((len = inputStream.read(buffer)) != -1) {
						baos.write(buffer, 0, len);
					}
					certData = baos.toByteArray();
				} catch (IOException e) {
					log.error(e.getMessage());
				}
			}
		}
		return new ByteArrayInputStream(this.certData);
	}

	public IWXPayDomain getWXPayDomain() {
		IWXPayDomain iwxPayDomain = new IWXPayDomain() {
			@Override
			public void report(String domain, long elapsedTimeMillis, Exception ex) {
			}

			@Override
			public DomainInfo getDomain(WXPayConfig config) {
				return new IWXPayDomain.DomainInfo(WXPayConstants.DOMAIN_API, true);
			}
		};
		return iwxPayDomain;
	}

}
