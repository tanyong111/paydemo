package com.ldsj.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class PayUtil {
	public static final String LOCAL_IP = "127.0.0.1";//本地ip地址
	public static final String DEFAULT_IP = "0:0:0:0:0:0:0:1";//默认ip地址
	public static final int DEFAULT_IP_LENGTH = 15;//默认ip地址长度
	
	/**
	 * 获取合法ip地址
	 * @param request
	 * @return
	 */
	public static String getRealIpAddress(HttpServletRequest request) {
	    String ip = request.getHeader("x-forwarded-for");//squid 服务代理
	    if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
	        ip = request.getHeader("Proxy-Client-IP");//apache服务代理
	    }
	    if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
	        ip = request.getHeader("WL-Proxy-Client-IP");//weblogic 代理
	    }

	    if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
	        ip = request.getHeader("HTTP_CLIENT_IP");//有些代理
	    }

	    if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
	        ip = request.getHeader("X-Real-IP"); //nginx代理
	    }

	    /*
	    * 如果此时还是获取不到ip地址，那么最后就使用request.getRemoteAddr()来获取
	    * */
	    if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
	        ip = request.getRemoteAddr();
	        if(LOCAL_IP.equals(ip) || DEFAULT_IP.equals(ip)){
	            //根据网卡取本机配置的IP
	            InetAddress iNet = null;
	            try {
	                iNet = InetAddress.getLocalHost();
	            } catch (UnknownHostException e) {
	                log.error("InetAddress getLocalHost error In HttpUtils getRealIpAddress: " ,e);
	            }
	            ip= iNet.getHostAddress();
	        }
	    }
	    //对于通过多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
	    //"***.***.***.***".length() = 15
	    if(!StringUtils.isEmpty(ip) && ip.length()> DEFAULT_IP_LENGTH){
	        if(ip.indexOf(",") > 0){
	            ip = ip.substring(0,ip.indexOf(","));
	        }
	    }
	    return ip;
	}
	
	/**
	 * 判断是否是手机端
	 * @param httpServletRequest
	 * @return
	 */
	public static boolean isMobileDevice(HttpServletRequest httpServletRequest) {
		/**
		 * android : 所有android设备 
		 * mac os : iphone ipad 
		 * windows phone:Nokia等windows系统的手机
		 */
		String requestHeader = httpServletRequest.getHeader("user-agent");
		String[] deviceArray = new String[] { "android", "mac os", "windows phone" };
		if (requestHeader == null)
			return false;
		requestHeader = requestHeader.toLowerCase();
		for (int i = 0; i < deviceArray.length; i++) {
			if (requestHeader.indexOf(deviceArray[i]) > 0) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * 获取本站域名网址
	 * @param httpServletRequest
	 * @return
	 */
	public static String getContextUrl(HttpServletRequest httpServletRequest) {
		StringBuffer url = httpServletRequest.getRequestURL();
		String tempContextUrl = url.delete(url.length() - httpServletRequest.getRequestURI().length(), url.length())
				.append("/").toString();
		return tempContextUrl;
	}
	
}
