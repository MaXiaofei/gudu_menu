package com.gudu.xsd.modules.member;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 家庭成员。healthProfile 存 JSON（身高/体重/忌口/特殊人群/营养约束等），AES-GCM 加密落库（B.3，EncryptedJsonMapTypeHandler）。
 */
@Data
@TableName(value = "member", autoResultMap = true)
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    /**
     * 关联登录账号（account.id，V76）：有账号的家庭成员与其账号一一关联（同一家庭内唯一）；
     * null = 无账号的成员档案（宝宝、老人）。由 family 上下文维护，客户端不可写。
     */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long accountId;

    private String name;

    /** 头像（V66，本站 /uploads/ 地址，可空）。 */
    private String avatarUrl;

    /**
     * 【旧列，保留一个版本周期】登录手机号。V75+ 起登录凭证迁到 account 表，本列不再作为登录依据；
     * 不对外输出（审计：曾经 GET /member 泄露全部用户手机号）。
     */
    @JsonIgnore
    @TableField(typeHandler = com.gudu.xsd.common.crypto.EncryptedStringTypeHandler.class)
    private String phone;

    /** 【旧列】微信 openid。V75+ 起迁到 account.wx_openid；禁止外泄。 */
    @JsonIgnore
    private String openid;

    /** 【旧列】BCrypt 密码哈希。V75+ 起迁到 account.password_hash；禁止外泄。 */
    @JsonIgnore
    private String passwordHash;

    /** 【旧列】是否超管。V75+ 起平台角色以 account.platform_role 为准，本列仅兼容旧前端展示。 */
    private Integer isAdmin;

    /**
     * 后台表单传入的明文密码(仅写入用,不入库)。
     * 新增:必填,落 passwordHash;编辑:留空表示不改密码,非空则重置。
     */
    @TableField(exist = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // 允许表单写入，但响应里不输出
    private String password;

    /** 角色标签，逗号分隔（关联 sys_dict role）。 */
    private String roleTags;

    /** 健康档案 JSON，灵活存三高指标/忌口/特殊人群/营养约束。敏感个人信息：加密存储，保存前须有单独同意（B.2）。 */
    @TableField(typeHandler = com.gudu.xsd.common.crypto.EncryptedJsonMapTypeHandler.class)
    private Map<String, Object> healthProfile;

    /**
     * 提供（并单独同意）本档案健康信息的账号（V88，B.2）：撤回同意时据此删除其提供的健康档案。
     * 服务端按登录账号写入，客户端不可写、不输出。
     */
    @JsonIgnore
    private Long healthConsentAccountId;

    /** 体控目标：MAINTAIN(维持) / LOSE(减脂) / GAIN(增肌)。null 为未设定。 */
    private String goal;

    /** 活动水平：SEDENTARY / LIGHT / MODERATE / ACTIVE。null 为未设定。 */
    private String activityLevel;

    /**
     * 小程序功能权限 key 数组(JSON)。如 ["dish.create","menu.plan"]。
     * null 时走角色默认模板;非空时与角色默认取并集(个人勾选只能增不能减)。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> mpPermissions;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
