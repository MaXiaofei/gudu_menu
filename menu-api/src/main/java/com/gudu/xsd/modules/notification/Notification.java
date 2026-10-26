package com.gudu.xsd.modules.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知。<b>非家庭域表</b>（V91 起不在 TenantTables）：收件人决定可见性，而不是当前家庭——
 * 账号级消息（反馈回复、厨房圈互动）要跟着人走，不能被写入时的家庭上下文截断。
 *
 * <p>收件人两列二选一非空：账号 id 与成员档案 id 是两张表各自的自增，不能混存在一列里。
 */
@Data
@TableName("notification")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收件账号（account.id = 登录 loginId）；与 {@link #recipientProfileId} 二选一非空。 */
    private Long recipientAccountId;
    /** 收件成员档案（member.id）；与 {@link #recipientAccountId} 二选一非空。 */
    private Long recipientProfileId;
    private String type;
    private String channel;
    private String title;
    private String content;
    /** 点击跳转目标 id（V83；feedback_reply → feedback.id）。 */
    private String refId;
    private Integer isRead;
    private LocalDateTime createTime;
}
