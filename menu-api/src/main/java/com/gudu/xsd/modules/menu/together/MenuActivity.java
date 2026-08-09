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
