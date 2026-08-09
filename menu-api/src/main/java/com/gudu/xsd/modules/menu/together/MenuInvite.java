package com.gudu.xsd.modules.menu.together;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 食集聚餐邀请凭证（V45）：一食集一邀请，刷新即换 code/token。
 * 三载体同效：口令 code / 二维码（内容=url） / 链接（url=/{base}/together.html?token=）。
 * 房主 = created_by（邀请生成人）。
 */
@Data
@TableName("menu_invite")
public class MenuInvite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long menuId;

    /** 6 位口令（短码分享）。 */
    private String code;

    /** 深链 token（二维码/链接）。 */
    private String token;

    /** 房主 member_id（邀请生成人）。 */
    private Long createdBy;

    private LocalDateTime createTime;
}
