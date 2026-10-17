package com.gudu.xsd.modules.ai.gateway.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** ai_provider_model 持久化对象。 */
@Data
@TableName("ai_provider_model")
public class AiProviderModelPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long providerId;
    private String model;
    private String capabilities;
    private BigDecimal priceCacheHitFen;
    private BigDecimal priceInputFen;
    private BigDecimal priceOutputFen;
    private String peakHours;
    private Integer supportsThinking;
    private Integer sortNo;
}
