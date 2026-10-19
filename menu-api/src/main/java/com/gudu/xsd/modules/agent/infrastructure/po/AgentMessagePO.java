package com.gudu.xsd.modules.agent.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_message")
public class AgentMessagePO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String sessionId;
    private Long memberId;
    private String role;
    private String text;
    private String toolsJson;
    private String cardsJson;
    private String actionsJson;
    private String traceId;
    private String reasoning;
    private Integer rating;
    private String feedbackComment;
    private LocalDateTime createdAt;
}
