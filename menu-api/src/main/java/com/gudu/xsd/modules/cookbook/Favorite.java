package com.gudu.xsd.modules.cookbook;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private Long memberId;

    private Long dishId;
}
