package com.gudu.xsd.modules.ai.gateway.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** ai_route 持久化对象（主键 = 能力）。 */
@Data
@TableName("ai_route")
public class AiRoutePO {

    @TableId(type = IdType.INPUT)
    private String capability;
    private Long primaryProviderId;
    private String primaryModel;
    private String fallbacks;
    private Integer timeoutMs;
    private Integer maxRetries;
    private String thinking;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
