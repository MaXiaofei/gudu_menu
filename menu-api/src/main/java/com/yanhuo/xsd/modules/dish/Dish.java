package com.yanhuo.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("dish")
public class Dish {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String note;

    private String coverUrl;

    /** 备料时间（分钟）。 */
    private Integer prepTime;

    /** 烹饪时间（分钟）。 */
    private Integer cookTime;

    private BigDecimal price;

    /** 难度 1-5。 */
    private Integer difficulty;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
