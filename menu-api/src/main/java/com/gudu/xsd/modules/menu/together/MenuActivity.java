package com.gudu.xsd.modules.menu.together;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聚餐活动流（V45）：加菜/删菜留痕（谁加的/谁删的），供聚餐 Tab「动态」列表。
 */
@Data
@TableName("menu_activity")
public class MenuActivity {

    public static final String ACTION_ADD = "add";
    public static final String ACTION_REMOVE = "remove";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long menuId;

    private Long memberId;

    private String nickname;

    /** add 点菜 / remove 删菜。 */
    private String action;

    private Long dishId;

    /** 菜名（自定义菜名或 dish 名，删除后仍可展示）。 */
    private String dishName;

    private LocalDateTime createTime;
}
