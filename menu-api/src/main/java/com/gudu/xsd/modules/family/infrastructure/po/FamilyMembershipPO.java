package com.gudu.xsd.modules.family.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** family_membership 表（V75）：UNIQUE(family_id, account_id)。全局表（成员关系本身是租户判定依据）。 */
@Data
@TableName("family_membership")
public class FamilyMembershipPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long familyId;
    private Long accountId;
    private String role;
    private LocalDateTime joinedAt;
}
