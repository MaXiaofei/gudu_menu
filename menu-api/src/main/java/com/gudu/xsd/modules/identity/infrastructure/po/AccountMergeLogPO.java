package com.gudu.xsd.modules.identity.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** account_merge_log 表（V79）：账号合并审计。 */
@Data
@TableName("account_merge_log")
public class AccountMergeLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceId;
    private Long targetId;
    private Long operatorId;
    private String detail;
    private LocalDateTime createdAt;
}
