package com.gudu.xsd.modules.contentsecurity.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** content_media_check 表持久化对象（V80；复核字段 V87）。member_id 存上传账号 id。 */
@Data
@TableName("content_media_check")
public class ContentMediaCheckPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private String mediaUrl;
    private Long memberId;
    private String purpose;
    private String status;
    private Integer label;
    private String reviewStatus;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
