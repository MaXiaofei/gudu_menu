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

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long menuId;

    /** 6 位口令（短码分享）。 */
    private String code;

    /** 深链 token（二维码/链接）。 */
    private String token;

    /** 房主 member_id（邀请生成人）。 */
    private Long createdBy;

    /** 凭证有效期截止（V90）；为空视为已过期（fail closed）。 */
    private LocalDateTime expiresAt;

    /** 是否已撤销（V90）：房主主动撤销，或刷新邀请时旧凭证作废。 */
    private Integer revoked;

    private LocalDateTime createTime;

    /** 凭证当前是否有效：未撤销且未过期。 */
    public boolean isUsable(LocalDateTime now) {
        return (revoked == null || revoked == 0) && expiresAt != null && expiresAt.isAfter(now);
    }
}
