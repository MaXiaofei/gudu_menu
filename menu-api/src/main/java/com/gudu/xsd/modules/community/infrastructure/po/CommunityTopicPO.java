package com.gudu.xsd.modules.community.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** community_topic（V71）。 */
@Data
@TableName("community_topic")
public class CommunityTopicPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String intro;
    private String rule;
    private Integer challenge;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer sort;
}
