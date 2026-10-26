package com.gudu.xsd.modules.identity.infrastructure.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gudu.xsd.common.crypto.EncryptedStringTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/** account 表持久化对象（V75）。全局表（不按家庭过滤）。可空字段 updateStrategy=ALWAYS：注销匿名化要能写 NULL。 */
@Data
@TableName(value = "account", autoResultMap = true)
public class AccountPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 登录手机号：AES-GCM 加密落库（mini-release-compliance B.3）；唯一与按手机号登录走 {@link #phoneHash}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS, typeHandler = EncryptedStringTypeHandler.class)
    private String phone;
    /** 手机号盲索引 hex(HMAC-SHA256)，唯一索引 uk_account_phone_hash（V88）；仓储由明文手机号计算。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phoneHash;
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
