﻿微信支付demo
本地可使用内网穿透工具natapp得到一个公网域名，使微信能够回调我们。
资源文件下wxcert里面有个apiclient_cert.p12是商户证书文件，在退款的的时候是必须要的，使用时请替换成对应自己对应的证书
具体实现看代码

微信比较坑的是没有给普通用户提供测试账号，必须要商户申请一个账号才能测试接入，具体申请商户账号步骤还请慢慢去商户平台尝试。
反正需要得到：appid，mchid（商户号），key（密钥），商户证书文件apiclient_cert.p12

使用微信提供的javaSDK包开发接入NATIVE支付：
首先到微信商户开发平台https://pay.weixin.qq.com/wiki/doc/api/native.php?chapter=11_1下载javaSDK
解压后可以看到里面只有一个包，可以阅读README.md配合微信商户平台开发平台对应支付文档大致了解一下接入方法。
根据README.md，首先他要我们继承一个WXPayConfig抽象类，这个类里面的抽象方法都没有显试声明public，默认访问级别是不能被其他包继承的,所以在其他包下继承此类会报错。
如果要在其他包下继承WXPayConfig抽象类，还请修改WXPayConfig抽象类的所有抽象方法为public。（备注：本demo就是修改为了public）

在这里有个巨坑，我感觉是个非常大的bug，就是如果你在调用微信统一下单接口的时候加密用的是HMACSHA256，
那么买家支付成功后，微信的异步通知也是使用的HMACSHA256，但是异步通知里并没有携带签名类型sign_type，导致默认使用MD5加密算法比对，导致验签失败！
所以在使用微信的SDK时要小心了，我是修改了WXPay里面WXPay构造函数里面默认使用的加密算法，具体看本demo的WXPay方法里面的构造函数。

然后就可以根据README.md编写支付接入了。主要是下单，查询，退款，查询退款接口。

本demo只提供了native支付，即扫码支付。如果是H5支付的话有些问题需要注意，1.H5支付必须要开通，2.H5支付时服务器的域名必须填写在微信H5支付功能的域名里，不然会出错。
