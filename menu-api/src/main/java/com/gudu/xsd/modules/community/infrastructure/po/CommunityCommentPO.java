package com.gudu.xsd.modules.community.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** community_comment（V71）。 */
@Data
@TableName("community_comment")
public class CommunityCommentPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long authorId;
    private Long parentId;
    private String text;
    private Integer likeCount;
    private LocalDateTime createTime;
}
