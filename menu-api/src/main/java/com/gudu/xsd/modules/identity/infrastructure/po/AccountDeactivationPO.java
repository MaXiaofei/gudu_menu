package com.gudu.xsd.modules.identity.infrastructure.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** account_deactivation 表持久化对象（V86，全局表）。 */
@Data
@TableName("account_deactivation")
public class AccountDeactivationPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private LocalDateTime requestedAt;
    private String status;
    private String steps;
    private Integer attempts;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;
}
