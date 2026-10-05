package com.macro.mall.portal.service;

import com.macro.mall.portal.domain.OssCallbackResult;
import com.macro.mall.portal.domain.OssPolicyResult;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Oss对象存储管理Service
 * Created by cyh on 2026/10/3.
 */
public interface OssService {
    /**
     * Oss上传策略生成
     */
    OssPolicyResult policy();

    /**
     * Oss上传成功回调
     */
    OssCallbackResult callback(HttpServletRequest request);

    /**
     * Oss文件删除（传入直传后返回的完整URL）
     */
    void delete(String url);
}
