package com.gudu.xsd.modules.agent.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("agent_span")
public class AgentSpanPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String spanId;
    private String parentId;
    private String type;
    private String name;
    private Integer startMs;
    private Integer ms;
    private Integer tokens;
    private BigDecimal costFen;
    private Integer ok;
    private String inputJson;
    private String outputJson;
}
