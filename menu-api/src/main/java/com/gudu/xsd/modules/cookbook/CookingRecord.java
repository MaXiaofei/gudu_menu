package com.gudu.xsd.modules.cookbook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cooking_record")
public class CookingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    private Long memberId;

    private LocalDateTime cookedAt;

    private String note;

    private LocalDateTime createTime;
}
