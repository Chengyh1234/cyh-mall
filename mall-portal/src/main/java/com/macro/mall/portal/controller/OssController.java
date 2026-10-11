package com.macro.mall.portal.controller;

import com.aliyun.oss.OSS;
import com.macro.mall.common.api.CommonResult;
import com.macro.mall.portal.domain.OssCallbackResult;
import com.macro.mall.portal.domain.OssPolicyResult;
import com.macro.mall.portal.service.OssService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 前台Oss对象存储管理Controller
 * Created by cyh on 2026/10/3.
 */
@Controller
@Tag(name = "OssController", description = "前台Oss对象存储管理")
@RequestMapping("/aliyun/oss")
public class OssController {
    @Autowired
    private OssService ossService;

    @Operation(summary = "Oss上传签名生成")
    @RequestMapping(value = "/policy", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<OssPolicyResult> policy() {
        OssPolicyResult result = ossService.policy();
        return CommonResult.success(result);
    }

   // ① 前端 GET /aliyun/oss/policy  → 拿到 policy + signature + callback(签名参数)
   //（callback 是 base64 编码的"回调约定"，内含回调地址、要带的字段）
   //
   //         ② 前端把文件 multipart 直传 OSS（POST host）
   //         │
   //         ▼
   //         ③ OSS 校验签名 → 文件成功落库（object 生成）
   //         │
   //         ▼
   //         ④ OSS 按 callback 里约定的地址，POST 回服务端 /aliyun/oss/callback  ← 触发点
   //  回调接口信息会返回给前端
    @Operation(summary = "Oss上传成功回调")
    @RequestMapping(value = "/callback", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult<OssCallbackResult> callback(HttpServletRequest request) {
        OssCallbackResult ossCallbackResult = ossService.callback(request);
        return CommonResult.success(ossCallbackResult);
    }

    @Operation(summary = "Oss文件删除")
    @RequestMapping(value = "/delete", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult delete(@RequestParam("url") String url) {
        ossService.delete(url);
        return CommonResult.success(null);
    }
}
