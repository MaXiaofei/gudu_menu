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
 * 家庭成员。healthProfile 存 JSON（身高/体重/忌口/特殊人群/营养约束等），用 JacksonTypeHandler 映射成 Map。
 */
@Data
@TableName(value = "member", autoResultMap = true)
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 登录手机号(V29 合并 user 后,member 自带账号)。admin 用字面量 "admin"。 */
    private String phone;

    /** BCrypt 密码哈希。null 表示该成员未开通登录(纯家庭成员)。仅服务端比对用，禁止外泄。 */
    @JsonIgnore
    private String passwordHash;

    /** 是否超管:1 绕过 @MpPerm 全权,0 走角色+个人勾选权限矩阵。 */
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

    /** 健康档案 JSON，灵活存三高指标/忌口/特殊人群/营养约束。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> healthProfile;

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
