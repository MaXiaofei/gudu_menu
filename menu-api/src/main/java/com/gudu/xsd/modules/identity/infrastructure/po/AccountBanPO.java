package com.gudu.xsd.modules.identity.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** account_ban 表持久化对象（V86，全局表；主键 = account_id）。 */
@Data
@TableName("account_ban")
public class AccountBanPO {

    @TableId(type = IdType.INPUT)
    private Long accountId;
    private String reason;
    private Long bannedBy;
    private LocalDateTime bannedAt;
}
