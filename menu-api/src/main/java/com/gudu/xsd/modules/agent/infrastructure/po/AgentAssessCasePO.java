package com.gudu.xsd.modules.agent.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_eval_case")
public class AgentAssessCasePO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String caseId;
    private String split;
    private String category;
    private String json;
    private String source;
    private String fromTraceId;
    private String status;
    private LocalDateTime createdAt;
}
