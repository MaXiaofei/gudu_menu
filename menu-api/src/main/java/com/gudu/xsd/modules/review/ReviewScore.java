package com.gudu.xsd.modules.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("review_score")
public class ReviewScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reviewId;
    private Long dimensionId;
    private Integer score;
}
