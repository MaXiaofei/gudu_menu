package com.gudu.xsd.modules.community.infrastructure.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** community_post_topic（V71，联合主键）。 */
@Data
@TableName("community_post_topic")
public class CommunityPostTopicPO {

    private Long postId;
    private Long topicId;
}
