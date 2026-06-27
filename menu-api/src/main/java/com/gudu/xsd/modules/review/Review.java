package com.gudu.xsd.modules.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("review")
public class Review {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private Long memberId;
    private Integer starRating;
    private String text;
    private String images;
    private LocalDateTime createTime;
    @TableLogic
    private Integer deleted;
}
