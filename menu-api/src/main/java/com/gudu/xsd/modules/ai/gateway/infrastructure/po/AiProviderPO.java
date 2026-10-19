package com.gudu.xsd.modules.ai.gateway.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** ai_provider 持久化对象。 */
@Data
@TableName("ai_provider")
public class AiProviderPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private String baseUrl;
    private String apiKeyEnc;
    private String keySource;
    private String keyEnv;
    private Integer enabled;
    private Integer healthOk;
    private Integer healthLatencyMs;
    private LocalDateTime healthCheckedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    // 不映射 deleted 列：全局 logic-delete-field=deleted 会把删除变成逻辑删除，而名称唯一键要求物理删除才能同名重建
}
