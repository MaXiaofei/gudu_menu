package com.gudu.xsd.modules.identity.infrastructure.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** account 表持久化对象（V75）。全局表（不按家庭过滤）。可空字段 updateStrategy=ALWAYS：注销匿名化要能写 NULL。 */
@Data
@TableName("account")
public class AccountPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String wxOpenid;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String wxUnionid;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String passwordHash;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nickname;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String avatarUrl;
    private String platformRole;
    private String status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long lastFamilyId;
    private Long mergedInto;
    private LocalDateTime createdAt;
    private LocalDateTime deactivatedAt;
}
