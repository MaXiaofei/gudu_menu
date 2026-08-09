package com.gudu.xsd.modules.menu.together;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聚餐成员（V45）：参与过的人 + 最后活跃时间（轮询即心跳）。
 * 登录用户走 member_id；H5 访客走 guest_key（输昵称获得，存 localStorage）。
 */
@Data
@TableName("menu_join")
public class MenuJoin {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long menuId;

    /** 登录用户（H5 访客为空）。 */
    private Long memberId;

    /** H5 访客凭证（唯一）。 */
    private String guestKey;

    private String nickname;

    /** 轮询时更新（心跳）。 */
    private LocalDateTime lastActiveAt;
}
