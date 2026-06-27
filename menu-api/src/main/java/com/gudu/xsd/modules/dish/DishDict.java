package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 菜品-字典关联（菜系/标签/分类多对多），用 rel_type 区分。
 */
@Data
@TableName("dish_dict")
public class DishDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    private Long dictId;

    /** cuisine / tag / category */
    private String relType;
}
