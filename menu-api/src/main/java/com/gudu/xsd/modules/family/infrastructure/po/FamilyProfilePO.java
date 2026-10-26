package com.gudu.xsd.modules.family.infrastructure.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 成员档案在 family 上下文的持久化视图（member 表，家庭域表）。
 * 仓储以系统模式访问并显式带 family_id 条件；familyId 由本上下文写入（加入其他家庭时建档）。
 */
@Data
@TableName(value = "member", autoResultMap = true)
public class FamilyProfilePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long accountId;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> healthProfile;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String goal;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String activityLevel;
    /** 新建档案默认角色标签 34（普通成员），与旧 MpPerm 模板兼容。 */
    private String roleTags;
    private Integer isAdmin;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
