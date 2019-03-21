package com.ldsj.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
@Slf4j
public class PayUtils {
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
	
	/**
	 * 获取请求参数中所有的信息
	 * 当商户上送frontUrl或backUrl地址中带有参数信息的时候，
	 * 这种方式会将url地址中的参数读到map中，会导多出来这些信息从而致验签失败，这个时候可以自行修改过滤掉url中的参数或者使用getAllRequestParamStream方法。
	 * @param request
	 * @return
	 */
	public static Map<String, String> getAllRequestParam(
			final HttpServletRequest request) {
		Map<String, String> res = new HashMap<String, String>();
		Enumeration<?> temp = request.getParameterNames();
		if (null != temp) {
			while (temp.hasMoreElements()) {
				String en = (String) temp.nextElement();
				String value = request.getParameter(en);
				res.put(en, value);
				// 在报文上送时，如果字段的值为空，则不上送<下面的处理为在获取所有参数数据时，判断若值为空，则删除这个字段>
				if (res.get(en) == null || "".equals(res.get(en))) {
					// System.out.println("======为空的字段名===="+en);
					res.remove(en);
				}
			}
		}
		return res;
	}
	
	/**
	  * 获取请求参数中所有的信息。
	  * 非struts可以改用此方法获取，好处是可以过滤掉request.getParameter方法过滤不掉的url中的参数。
	  * struts可能对某些content-type会提前读取参数导致从inputstream读不到信息，所以可能用不了这个方法。理论应该可以调整struts配置使不影响，但请自己去研究。
	  * 调用本方法之前不能调用req.getParameter("key");这种方法，否则会导致request取不到输入流。
	  * @param request
	  * @return
	  */
	 public static Map<String, String> getAllRequestParamStream(
	   final HttpServletRequest request) {
		  Map<String, String> res = new HashMap<String, String>();
		  try {
		   String notifyStr = new String(IOUtils.toByteArray(request.getInputStream()),DemoBase.encoding);
		   LogUtil.writeLog("收到通知报文：" + notifyStr);
		   String[] kvs= notifyStr.split("&");
		   for(String kv : kvs){
		    String[] tmp = kv.split("=");
		    if(tmp.length >= 2){
		     String key = tmp[0];
		     String value = URLDecoder.decode(tmp[1],DemoBase.encoding);
		     res.put(key, value);
		    }
		   }
		  } catch (UnsupportedEncodingException e) {
		   LogUtil.writeLog("getAllRequestParamStream.UnsupportedEncodingException error: " + e.getClass() + ":" + e.getMessage());
		  } catch (IOException e) {
		   LogUtil.writeLog("getAllRequestParamStream.IOException error: " + e.getClass() + ":" + e.getMessage());
		  }
		  return res;
	 }
}
