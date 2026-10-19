package com.gudu.xsd.modules.feedback.infrastructure.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** feedback 表持久化对象（V67）。images 存 JSON 数组字符串。 */
@Data
@TableName("feedback")
public class FeedbackPO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private String content;
    private String contact;
    private String images;
    private String clientInfo;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
