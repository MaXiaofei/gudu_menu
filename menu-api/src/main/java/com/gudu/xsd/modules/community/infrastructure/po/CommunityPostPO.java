package com.gudu.xsd.modules.community.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** community_post（V71）。images / riskReasons 存 JSON 数组字符串。 */
@Data
@TableName("community_post")
public class CommunityPostPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String title;
    private String text;
    private String images;
    private String blockedImages;
    private Long dishId;
    private String visibility;
    private String status;
    private String risk;
    private String riskReasons;
    private String riskScores;
    private Integer reviewed;
    private Long fromCookRecordId;
    private String city;
    private java.math.BigDecimal coverRatio;
    private Integer likeCount;
    private Integer collectCount;
    private Integer commentCount;
    private Integer copyCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
