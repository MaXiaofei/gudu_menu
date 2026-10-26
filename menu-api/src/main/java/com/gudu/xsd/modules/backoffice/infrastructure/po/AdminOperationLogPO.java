package com.gudu.xsd.modules.backoffice.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** admin_operation_log 表持久化对象（V86，全局表）。 */
@Data
@TableName("admin_operation_log")
public class AdminOperationLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String action;
    private String targetType;
    private Long targetId;
    private String reason;
    private String detail;
    private LocalDateTime createdAt;
}
