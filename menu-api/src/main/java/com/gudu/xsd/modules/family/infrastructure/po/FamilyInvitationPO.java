package com.gudu.xsd.modules.family.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** family_invitation 表（V75）：code 全局唯一。全局表（游客凭码预览需跨家庭查询）。 */
@Data
@TableName("family_invitation")
public class FamilyInvitationPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private String code;
    private Long createdBy;
    private LocalDateTime expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private Integer revoked;
    private LocalDateTime createdAt;
}
