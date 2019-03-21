package com.ldsj.config;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

/**
 * @author TommyDeng <250575979@qq.com>
 * @version 创建时间：2016年9月21日 下午7:48:08
 *
 */

@Configuration
public class DefaultSetting {
	public final static Charset CHARSET = Charset.forName("UTF-8");
	public final static String DateTimeFormat = "yyyy-MM-dd HH:mm:ss";
	
	@Value("${ali.url}")
	String url; //支付宝接口地址
	@Value("${ali.app-id}")
	String app_id; //32	支付宝分配给开发者的应用ID
	@Value("${ali.format}")
	String format; //40	仅支持JSON	
	@Value("${ali.charset}")
	String charset; //10	请求使用的编码格式，如utf-8,gbk,gb2312等	utf-8
	@Value("${ali.sign-type}")
	String sign_type; //是	10	商户生成签名字符串所使用的签名算法类型，目前支持RSA2和RSA，推荐使用RSA2	RSA2
	@Value("${ali.version}")
	String version; //3	调用的接口版本，固定为：1.0	1.0
	@Value("${ali.app-private-key}")
	String app_private_key; //用户私钥
	@Value("${ali.alipay-public-key}")
	String alipay_public_key; // ali公钥

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();

		JavaTimeModule module = new JavaTimeModule();
		LocalDateTimeDeserializer localDateTimeDeserializer = new LocalDateTimeDeserializer(
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		module.addDeserializer(LocalDateTime.class, localDateTimeDeserializer);
		LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer(
				DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		module.addSerializer(LocalDateTime.class, localDateTimeSerializer);
		mapper.registerModule(module);

		// 空属性不显示
		// spring.jackson.default-property-inclusion=non-empty
		mapper.setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS);
		mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

		// #反序列化设置(parse json)
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
		mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true);

		// #日期格式json序列化字段散列问题(to json)
		mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		return mapper;
	}
	
	@Bean
	public AlipayClient alipayClient() {
		AlipayClient alipayClient = new DefaultAlipayClient(url, app_id,
				app_private_key, format, charset, alipay_public_key, sign_type);
		return alipayClient;
	}
	
}
