package com.gudu.xsd.modules.vision.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** vision_job 持久化对象（V72）。items / recipe 存 JSON。 */
@Data
@TableName("vision_job")
public class VisionJobPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private String mode;
    private String imageUrl;
    private String status;
    private String error;
    private String itemsJson;
    private String recipeJson;
    private Boolean degraded;
    private LocalDateTime appliedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
