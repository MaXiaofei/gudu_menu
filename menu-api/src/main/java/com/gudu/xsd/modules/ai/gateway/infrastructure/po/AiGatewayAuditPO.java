package com.gudu.xsd.modules.ai.gateway.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** ai_gateway_audit 持久化对象。 */
@Data
@TableName("ai_gateway_audit")
public class AiGatewayAuditPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String actor;
    private String action;
    private String target;
    private String detail;
    private LocalDateTime createTime;
}
