package com.gudu.xsd.modules.preference.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** user_preference 表持久化对象（V84）。 */
@Data
@TableName("user_preference")
public class UserPreferencePO {

    @TableId(type = IdType.INPUT)
    private Long memberId;
    private Integer personalizedRecommend;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
