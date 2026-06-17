package com.yanhuo.xsd.modules.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private String type;
    private String channel;
    private String title;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}
