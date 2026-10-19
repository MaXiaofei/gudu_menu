package com.gudu.xsd.modules.ai;

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
    /** V73 模型网关：能力（非空 = 网关单次尝试行，不计入业务场景用量）。 */
    private String capability;
    /** V73 实际模型名。 */
    private String model;
    /** V73 成本（分，按调用时刻单价）。 */
    private BigDecimal costFen;
    /** V73 关联追踪 id。 */
    private String traceId;
}
