package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("menu")
public class Menu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属家庭（租户，multi-tenant-family）：租户插件按当前家庭自动写入与过滤，客户端不可写、不可改。 */
    @com.baomidou.mybatisplus.annotation.TableField(
            insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
            updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long familyId;

    private String name;

    /** 关联 sys_dict(menu_type)。 */
    private Long typeId;

    private Long targetMemberId;

    /** 份数 / 人数。 */
    private Integer servingCount;

    /** 状态：ACTIVE 进行中 / DONE 已完成。V36 加。 */
    private String status;

    /** 完成时间（做菜扣库存成功后写）。V36 加。 */
    private LocalDateTime finishedAt;

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;

    /** 关联菜数（列表接口回填，不入库）。 */
    @TableField(exist = false)
    private Integer dishCount;

    /** 前 3 个菜的封面 URL（列表缩略堆叠用，列表接口回填，不入库）。 */
    @TableField(exist = false)
    private List<String> covers;
}
