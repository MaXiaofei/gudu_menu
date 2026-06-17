package com.yanhuo.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("dish_step")
public class DishStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    private Integer seq;

    private String text;

    /** 多图逗号分隔 URL。 */
    private String images;

    private Integer sortOrder;
}
