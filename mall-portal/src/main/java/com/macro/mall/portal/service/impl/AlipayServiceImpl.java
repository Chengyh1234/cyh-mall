package com.macro.mall.portal.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.portal.config.AlipayConfig;
import com.macro.mall.portal.domain.AliPayParam;
import com.macro.mall.portal.service.AlipayService;
import com.macro.mall.portal.service.OmsPortalOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @auther macrozheng
 * @description 支付宝支付Service实现类
 * @date 2023/9/8
 * @github https://github.com/macrozheng
 */
@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {
    @Autowired
    private AlipayConfig alipayConfig;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private OmsOrderMapper orderMapper;
    @Autowired
    private OmsPortalOrderService portalOrderService;
    /**
     * 当面付下单：调用支付宝预下单接口（alipay.trade.precreate），
     * 生成支付二维码内容，买家扫码完成支付
     *
     * @param aliPayParam 支付参数（商户订单号、订单标题等）
     * @return 支付二维码内容，下单失败时抛出异常
     */
    @Override
    public String pay(AliPayParam aliPayParam) {
        //电脑网站采用「当面付款」：alipay.trade.precreate 预下单，返回二维码内容，买家手机扫码支付
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        if(StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())){
            //异步接收地址，公网可访问，当面付依赖异步通知确认支付结果
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        //******必传参数******
        JSONObject bizContent = new JSONObject();
        //商户订单号，商家自定义，保持唯一性
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());
        //订单总金额以数据库中订单表中为准，防止前端篡改支付金额
        OmsOrder order = portalOrderService.getOrderByOrderSn(aliPayParam.getOutTradeNo());
        //防御性校验：订单不存在或金额为空时直接终止下单，避免以非法金额发起支付
        if(order==null||order.getPayAmount()==null){
            Asserts.fail("订单信息或金额不能为空！");
        }
        BigDecimal totalAmount = order.getPayAmount();
        //支付金额，最小值0.01元
        bizContent.put("total_amount", totalAmount);
        //订单标题，不可使用特殊符号
        bizContent.put("subject", aliPayParam.getSubject());
        //当面付场景固定传值FACE_TO_FACE_PAYMENT
        bizContent.put("product_code", "FACE_TO_FACE_PAYMENT");
        request.setBizContent(bizContent.toString());
        String qrCode = null;
        try {
            //调用支付宝SDK发起预下单请求，成功则返回二维码内容
            AlipayTradePrecreateResponse response = alipayClient.execute(request);
            if(response.isSuccess()){
                //二维码内容，前端用二维码生成器渲染后供买家扫码付款
                qrCode = response.getQrCode();
            }else{
                Asserts.fail("支付宝下单失败：" + response.getMsg() + " " + response.getSubMsg());
            }
        } catch (AlipayApiException e) {
            log.error("支付宝当面付下单异常！",e);
            Asserts.fail("支付宝下单异常：" + e.getErrMsg());
        }
        return qrCode;
    }

    /**
     * 处理支付宝异步通知：验签通过且交易状态为支付成功时，
     * 将订单更新为已支付状态
     *
     * @param params 支付宝异步通知参数
     * @return "success"表示处理成功，通知支付宝停止重试；否则返回"failure"
     */
    @Override
    public String notify(Map<String, String> params) {
        //异步通知处理：买家付款成功后，支付宝会回调此接口推送支付结果，验签通过后才视为可信通知
        String result = "failure";
        boolean signVerified = false;
        try {
            //调用SDK验证签名，防止伪造回调
            signVerified = AlipaySignature.rsaCheckV1(params, alipayConfig.getAlipayPublicKey(), alipayConfig.getCharset(), alipayConfig.getSignType());
        } catch (AlipayApiException e) {
            log.error("支付回调签名校验异常！",e);
            e.printStackTrace();
        }
        if (signVerified) {
            //验签通过后再校验交易状态，双重确认后才将订单置为已支付
            String tradeStatus = params.get("trade_status");
            if("TRADE_SUCCESS".equals(tradeStatus)){
                result = "success";
                log.info("notify方法被调用了，tradeStatus:{}",tradeStatus);
                String outTradeNo = params.get("out_trade_no");
                portalOrderService.paySuccessByOrderSn(outTradeNo,1);
            }else{
                log.warn("订单未支付成功，trade_status:{}",tradeStatus);
            }
        } else {
            log.warn("支付回调签名校验失败！");
        }
        //返回"success"告知支付宝停止重试通知，否则支付宝会按策略多次重试回调
        return result;
    }

    /**
     * 查询订单支付状态：主动向支付宝发起交易查询，
     * 用于前端轮询支付结果、订单超时对账等场景
     *
     * @param outTradeNo 商户订单号
     * @param tradeNo    支付宝交易号（两者至少传一个）
     * @return 交易状态（如TRADE_SUCCESS、WAIT_BUYER_PAY等），查询异常时返回null
     */
    @Override
    public String query(String outTradeNo, String tradeNo) {
        //主动查询订单支付状态：用于前端轮询支付结果、订单超时对账等场景
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        //******必传参数******
        JSONObject bizContent = new JSONObject();
        //设置查询参数，out_trade_no和trade_no至少传一个
        if(StrUtil.isNotEmpty(outTradeNo)){
            bizContent.put("out_trade_no",outTradeNo);
        }
        if(StrUtil.isNotEmpty(tradeNo)){
            bizContent.put("trade_no",tradeNo);
        }
        //交易结算信息: trade_settle_info
        String[] queryOptions = {"trade_settle_info"};
        bizContent.put("query_options", queryOptions);
        request.setBizContent(bizContent.toString());
        AlipayTradeQueryResponse response = null;
        try {
            //调用支付宝SDK发起交易查询请求
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            //查询异常时response保持为null，下方直接返回null，由调用方兜底处理
            log.error("查询支付宝账单异常！",e);
        }
        if(response.isSuccess()){
            log.info("查询支付宝账单成功！");
            if("TRADE_SUCCESS".equals(response.getTradeStatus())){
                portalOrderService.paySuccessByOrderSn(outTradeNo,1);
            }
        } else {
            log.error("查询支付宝账单失败！");
        }
        //交易状态：WAIT_BUYER_PAY（交易创建，等待买家付款）、TRADE_CLOSED（未付款交易超时关闭，或支付完成后全额退款）、TRADE_SUCCESS（交易支付成功）、TRADE_FINISHED（交易结束，不可退款）
        return response.getTradeStatus();
    }

    /**
     * 手机网站支付（WAP）：构建支付请求并生成自动提交的form表单HTML，
     * 页面加载后自动跳转到支付宝收银台完成支付
     *
     * @param aliPayParam 支付参数（商户订单号、订单标题等）
     * @return 自动提交的表单HTML，用于前端页面渲染
     */
    @Override
    public String webPay(AliPayParam aliPayParam) {
        //手机网站支付（WAP）：返回自动提交的form表单HTML，页面加载后自动跳转到支付宝收银台完成支付
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest ();
        if(StrUtil.isNotEmpty(alipayConfig.getNotifyUrl())){
            //异步接收地址，公网可访问
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
        }
        if(StrUtil.isNotEmpty(alipayConfig.getReturnUrl())){
            //同步跳转地址
            request.setReturnUrl(alipayConfig.getReturnUrl());
        }
        //******必传参数******
        JSONObject bizContent = new JSONObject();
        //商户订单号，商家自定义，保持唯一性
        bizContent.put("out_trade_no", aliPayParam.getOutTradeNo());
        //订单总金额以数据库中订单表中为准，防止前端篡改支付金额
        OmsOrder order = portalOrderService.getOrderByOrderSn(aliPayParam.getOutTradeNo());
        //防御性校验：订单不存在或金额为空时直接终止下单，避免以非法金额发起支付
        if(order==null||order.getPayAmount()==null){
            Asserts.fail("订单信息或金额不能为空！");
        }
        BigDecimal totalAmount = order.getPayAmount();
        //支付金额，最小值0.01元
        bizContent.put("total_amount", totalAmount);
        //订单标题，不可使用特殊符号
        bizContent.put("subject", aliPayParam.getSubject());
        //手机网站支付默认传值FAST_INSTANT_TRADE_PAY
        bizContent.put("product_code", "QUICK_WAP_WAY");
        request.setBizContent(bizContent.toString());
        String formHtml = null;
        try {
            //pageExecute生成含自动提交脚本的表单页面，浏览器加载后即跳转至支付宝收银台
            formHtml = alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return formHtml;
    }
}
