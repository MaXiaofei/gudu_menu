package com.gudu.xsd.modules.dish;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("dish")
public class Dish {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属家庭（V76）：null = 平台公共菜谱（所有家庭可读，仅平台运营可写）；非空 = 该家庭私有菜谱。
     * 由 DishService 按登录身份写入，客户端不可写；读可见性由 DishVisibilityInnerInterceptor 兜底。
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    /** 「复制到我家」来源菜谱 id（V76，可空）。客户端不可写。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long sourceDishId;

    private String name;

    private String note;

    private String coverUrl;

    /** 备料时间（分钟）。 */
    private Integer prepTime;

    /** 烹饪时间（分钟）。 */
    private Integer cookTime;

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

    /** 最近一次做这道菜的时间（不入库；/dish/search 回填，无记录 null）。 */
    @TableField(exist = false)
    private java.time.LocalDateTime lastCookedAt;

    /** 最近一次谁做的（不入库；/dish/search 回填，无记录 null）。 */
    @TableField(exist = false)
    private com.gudu.xsd.modules.dish.application.dto.CookerView lastCookedBy;

    /** 本家余量覆盖率（不入库；登录才算，未登录 null。契约 Coverage：{enough,total,missing[]}）。 */
    @TableField(exist = false)
    private com.gudu.xsd.modules.pantry.application.dto.CoverageView coverage;
}
