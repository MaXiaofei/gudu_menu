package com.gudu.xsd.modules.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 通用字典项。用 dict_group 区分：cuisine/tag/category/menu_type/audience/unit/purchase_category/role。
 */
@Data
@TableName("sys_dict")
public class SysDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictGroup;

    private String name;

    private Integer sort;
}
