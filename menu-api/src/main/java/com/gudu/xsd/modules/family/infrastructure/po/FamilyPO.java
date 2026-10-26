package com.gudu.xsd.modules.family.infrastructure.po;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** family 表（V75）。全局表：家庭本身不按租户过滤，访问控制在 family 上下文应用层。 */
@Data
@TableName("family")
public class FamilyPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 冗余当前 OWNER（便于运营统计与迁移校验）；以 family_membership 为准。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ownerAccountId;
    private String status;
    private LocalDateTime createdAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime dissolvedAt;
    /** 乐观锁版本（仓储手写 WHERE version=? 校验）。 */
    private Long version;
}
