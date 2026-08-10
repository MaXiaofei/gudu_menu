package com.gudu.xsd.modules.dish;

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

    /** 来源名（自己创建/下厨房/美食杰/豆果/抖音…，V49）。 */
    private String sourceName;

    /** 第三方来源地址（导入时记录，V49）。 */
    private String sourceUrl;

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

    /** 做过次数（按当前就餐成员统计 cooking_record，不入库，search 时批量回填）。 */
    @TableField(exist = false)
    private Integer cookedCount;
}
