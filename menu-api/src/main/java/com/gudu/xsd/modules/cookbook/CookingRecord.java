package com.gudu.xsd.modules.cookbook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
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

    /** 关联食集（整集做时填；单菜直做为 null）。V36 加。 */
    private Long menuId;

    /** 份数（该菜这次做了几份）。V36 加。 */
    private BigDecimal servingFactor;

    /** 来源：menu=整集做 / dish=单菜直做 / manual=旧 markDone。V36 加。 */
    private String source;

    /** 欠量明细（家里不够、没扣成的部分），格式 "ingredientId:克g;..."。V36 加。 */
    private String memo;

    private LocalDateTime createTime;
}
