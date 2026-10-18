package com.macro.mall.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.macro.mall.common.exception.Asserts;
import com.macro.mall.dao.OmsOrderReturnApplyDao;
import com.macro.mall.dto.OmsOrderReturnApplyResult;
import com.macro.mall.dto.OmsReturnApplyQueryParam;
import com.macro.mall.dto.OmsUpdateStatusParam;
import com.macro.mall.mapper.OmsOrderMapper;
import com.macro.mall.mapper.OmsOrderReturnApplyMapper;
import com.macro.mall.model.OmsOrder;
import com.macro.mall.model.OmsOrderReturnApply;
import com.macro.mall.service.OmsOrderReturnApplyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单退货管理Service实现类
 * Created by macro on 2018/10/18.
 */
@Slf4j
@Service
public class OmsOrderReturnApplyServiceImpl extends ServiceImpl<OmsOrderReturnApplyMapper, OmsOrderReturnApply> implements OmsOrderReturnApplyService {
    @Autowired
    private OmsOrderReturnApplyDao returnApplyDao;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private OmsOrderMapper orderMapper;

    @Override
    public IPage<OmsOrderReturnApply> list(OmsReturnApplyQueryParam queryParam, Integer pageSize, Integer pageNum) {
        //自定义Dao返回全量数据，此处进行内存分页
        List<OmsOrderReturnApply> list = returnApplyDao.getList(queryParam);
        Page<OmsOrderReturnApply> page = new Page<>(pageNum, pageSize);
        if (list == null || list.isEmpty()) {
            page.setTotal(0);
            page.setRecords(new ArrayList<>());
            return page;
        }
        page.setTotal(list.size());
        int fromIndex = (pageNum - 1) * pageSize;
        if (fromIndex >= list.size()) {
            page.setRecords(new ArrayList<>());
        } else {
            int toIndex = Math.min(fromIndex + pageSize, list.size());
            page.setRecords(list.subList(fromIndex, toIndex));
        }
        return page;
    }

    @Override
    public int delete(List<Long> ids) {
        return baseMapper.delete(new LambdaQueryWrapper<OmsOrderReturnApply>()
                .in(OmsOrderReturnApply::getId, ids).eq(OmsOrderReturnApply::getStatus, 3));
    }

    @Override
    public int updateStatus(Long id, OmsUpdateStatusParam statusParam) {
        Integer status = statusParam.getStatus();
        OmsOrderReturnApply returnApply = new OmsOrderReturnApply();
        if (status.equals(1)) {
            //确认退货：退款金额沿用申请时从订单写入的金额，不允许前端修改
            OmsOrderReturnApply apply = baseMapper.selectById(id);
            if (apply == null) {
                return 0;
            }
            returnApply.setId(id);
            returnApply.setStatus(1);
            returnApply.setReturnAmount(apply.getReturnAmount());
            returnApply.setCompanyAddressId(statusParam.getCompanyAddressId());
            returnApply.setHandleTime(LocalDateTime.now());
            returnApply.setHandleMan(getCurrentAdminUsername());
            returnApply.setHandleNote(statusParam.getHandleNote());
        } else if (status.equals(2)) {
            //完成退货：确认收到退货后，先调用支付宝原路退款，退款成功才更新状态
            OmsOrderReturnApply apply = baseMapper.selectById(id);
            if (apply == null) {
                return 0;
            }
            if (apply.getReturnAmount() == null) {
                Asserts.fail("退款金额为空，请先在确认退货时设置退款金额！");
            }
            //退款失败会抛出异常，阻止状态流转为已完成
            alipayRefund(apply);
            returnApply.setId(id);
            returnApply.setStatus(2);
            returnApply.setReceiveTime(LocalDateTime.now());
            returnApply.setReceiveMan(getCurrentAdminUsername());
            returnApply.setReceiveNote(statusParam.getReceiveNote());
        } else if (status.equals(3)) {
            //拒绝退货
            returnApply.setId(id);
            returnApply.setStatus(3);
            returnApply.setHandleTime(LocalDateTime.now());
            returnApply.setHandleMan(getCurrentAdminUsername());
            returnApply.setHandleNote(statusParam.getHandleNote());
        } else {
            return 0;
        }
        return baseMapper.updateById(returnApply);
    }

    /**
     * 获取当前登录管理员用户名（JWT过滤器已写入SecurityContext），
     * 处理人/收货人一律以登录态为准，不允许前端伪造
     */
    private String getCurrentAdminUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * 调用支付宝退款接口（alipay.trade.refund）原路退回支付金额
     * 仅对支付宝支付的订单执行在线退款，退款失败抛出异常由调用方处理
     *
     * @param apply 退货申请单（需包含订单编号、退款金额、申请单ID）
     */
    private void alipayRefund(OmsOrderReturnApply apply) {
        OmsOrder order = orderMapper.selectById(apply.getOrderId());
        if (order == null) {
            Asserts.fail("订单不存在，无法退款！");
        }
        //支付方式：0->未支付；1->支付宝；2->微信，非支付宝支付无法自动退款
        if (order.getPayType() == null || order.getPayType() != 1) {
            Asserts.fail("当前订单非支付宝支付，无法自动退款，请线下处理！");
        }
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        //******必传参数******
        JSONObject bizContent = new JSONObject();
        //商户订单号（原支付时的订单编号）
        bizContent.put("out_trade_no", apply.getOrderSn());
        //退款金额，支持全额/部分退款，须小于等于实付金额
        bizContent.put("refund_amount", apply.getReturnAmount());
        //退款请求号：同一笔订单的退款请求号不可重复，以申请单ID保证幂等，防止重复退款
        bizContent.put("out_request_no", "RETURN_" + apply.getId());
        request.setBizContent(bizContent.toString());
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if (!response.isSuccess()) {
                log.error("支付宝退款失败，订单号：{}，退款金额：{}，原因：{} {}",
                        apply.getOrderSn(), apply.getReturnAmount(), response.getMsg(), response.getSubMsg());
                Asserts.fail("支付宝退款失败：" + response.getMsg() + " " + response.getSubMsg());
            }
            log.info("支付宝退款成功，订单号：{}，退款金额：{}", apply.getOrderSn(), apply.getReturnAmount());
        } catch (AlipayApiException e) {
            log.error("支付宝退款异常，订单号：{}", apply.getOrderSn(), e);
            Asserts.fail("支付宝退款异常：" + e.getErrMsg());
        }
    }

    @Override
    public OmsOrderReturnApplyResult getItem(Long id) {
        return returnApplyDao.getDetail(id);
    }
}