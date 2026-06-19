package com.yanhuo.xsd.modules.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 调用日志：每次调用 AiClient 记一条（场景、请求/响应、token、费用、provider、延迟、状态）。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** nutrition_fill / menu_recommend。 */
    private String scene;
    private Long memberId;
    private String request;
    private String response;
    private Integer tokensIn;
    private Integer tokensOut;
    private BigDecimal cost;
    /** mock / glm。 */
    private String provider;
    private Integer latencyMs;
    /** ok / fail。 */
    private String status;
    private String errorMsg;
    private LocalDateTime createTime;
}
