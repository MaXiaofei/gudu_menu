package com.gudu.xsd.modules.menu;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("menu")
public class Menu {

    @TableId(type = IdType.AUTO)
    private Long id;

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
}
