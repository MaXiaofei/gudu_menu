package com.yanhuo.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    /** 来源：ORIGINAL(自创) / IMPORT(导入)。 */
    private String source;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;

    /** 菜系名（关联查询，不入库）。 */
    @TableField(exist = false)
    private List<String> cuisineNames;

    /** 分类名（关联查询，不入库）。 */
    @TableField(exist = false)
    private List<String> categoryNames;

    /** 标签名（关联查询，不入库）。 */
    @TableField(exist = false)
    private List<String> tagNames;
}
