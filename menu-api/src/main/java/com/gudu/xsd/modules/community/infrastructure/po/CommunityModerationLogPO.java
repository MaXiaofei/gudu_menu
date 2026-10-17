package com.gudu.xsd.modules.community.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** community_moderation_log（V71）审核审计日志。 */
@Data
@TableName("community_moderation_log")
public class CommunityModerationLogPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long operatorId;
    private String action;
    private String reason;
    private String note;
    private LocalDateTime createTime;
}
