package com.gudu.xsd.modules.agent.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent_eval_run")
public class AgentAssessRunPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String promptVersion;
    private String provider;
    private String status;
    private BigDecimal toolAccuracy;
    private BigDecimal argAccuracy;
    private BigDecimal pointCoverage;
    private BigDecimal avgCostFen;
    private Integer p95Ms;
    private Integer passed;
    private Integer hardGateFailures;
    private Integer baseline;
    private String reportJson;
    private String error;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
