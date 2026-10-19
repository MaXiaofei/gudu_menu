package com.gudu.xsd.modules.community.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** community_topic_checkin（V71）。 */
@Data
@TableName("community_topic_checkin")
public class CommunityTopicCheckinPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long topicId;
    private Long userId;
    private LocalDate checkinDate;
    private Long postId;
}
