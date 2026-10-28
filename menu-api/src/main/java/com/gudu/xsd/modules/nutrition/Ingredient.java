package com.gudu.xsd.modules.nutrition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ingredient")
public class Ingredient {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 所属家庭（V95）：NULL = 公共食材（只有平台运营能改）；非空 = 该家庭私有食材（别家不可见）。
     * 由服务端按登录身份写入，客户端不可写（只读序列化）；读可见性由 IngredientVisibilityInnerInterceptor 兜底。
     */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    /** 插画 key（V68，docs/design/assets.json ing）；写入时由 IngredientService 按 IngredientIconPolicy 计算。 */
    private String iconKey;

    /** 关联 sys_dict(purchase_category)。 */
    private Long purchaseCategoryId;

    /** 食用属性：1食用/2饮料零食/3生活用品（V53）。非食用跳过营养计算。 */
    private Integer edible;

    /** 创建时间（V95）：仅家庭新建的食材写入；存量公共食材为空。 */
    private java.time.LocalDateTime createTime;

    private Integer purchaseCount;

    private Integer usageCount;

    @TableLogic
    private Integer deleted;
}
