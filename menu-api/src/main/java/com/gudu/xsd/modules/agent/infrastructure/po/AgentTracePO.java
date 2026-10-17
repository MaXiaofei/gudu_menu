package com.gudu.xsd.modules.agent.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_trace")
public class AgentTracePO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private String messageId;
    private Long memberId;
    private String firstText;
    private String status;
    private String provider;
    private String model;
    private String promptVersion;
    private Integer degraded;
    private Integer ms;
    private Integer firstTokenMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private BigDecimal costFen;
    private Integer phoneMasked;
    private Integer feedback;
    private LocalDateTime createdAt;
}
