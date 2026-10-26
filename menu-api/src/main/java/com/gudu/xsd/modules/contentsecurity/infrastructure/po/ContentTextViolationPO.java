package com.gudu.xsd.modules.contentsecurity.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** content_text_violation 表持久化对象（V86，全局表）。 */
@Data
@TableName("content_text_violation")
public class ContentTextViolationPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private String purpose;
    private String suggest;
    private Integer label;
    private String source;
    private String excerpt;
    private String reviewStatus;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
}
