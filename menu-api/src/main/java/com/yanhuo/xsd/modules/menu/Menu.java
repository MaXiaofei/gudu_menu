package com.yanhuo.xsd.modules.menu;

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

    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}
