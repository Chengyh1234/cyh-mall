package com.macro.mall.portal.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 会员个人信息修改参数
 * 仅包含允许用户自行编辑的字段，避免通过接口篡改积分、等级等敏感属性
 */
@Getter
@Setter
public class UmsMemberUpdateParam implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String icon;

    @Schema(description = "性别：0->未知；1->男；2->女")
    private Integer gender;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "个性签名")
    private String signature;
}
