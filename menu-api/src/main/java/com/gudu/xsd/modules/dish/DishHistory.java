package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜品历史版本：编辑前的完整快照 JSON。
 */
@Data
@TableName("dish_history")
public class DishHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dishId;

    /** 编辑前完整快照（菜品+步骤+关联）。 */
    private String snapshot;

    private LocalDateTime createTime;
}
