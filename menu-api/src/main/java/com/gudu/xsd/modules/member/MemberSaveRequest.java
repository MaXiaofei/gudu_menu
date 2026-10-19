package com.gudu.xsd.modules.member;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 成员新增/编辑入参（白名单字段，替代直接收 {@link Member} 实体）。
 *
 * <p>字段分两级：
 * <ul>
 *   <li>档案字段（name/healthProfile/goal/activityLevel）：登录用户可写（本人或纯成员档案）；</li>
 *   <li>账号与权限字段（phone/password/isAdmin/roleTags/mpPermissions）：仅平台超管可写，其他人传了也忽略。</li>
 * </ul>
 */
@Data
public class MemberSaveRequest {

    private Long id;

    @Size(max = 32, message = "名字最多 32 个字")
    private String name;

    private Map<String, Object> healthProfile;

    private String goal;

    private String activityLevel;

    // ---- 以下仅超管可写 ----

    private String phone;

    /** 明文密码（仅写入）：新增必填才可登录；编辑留空 = 不改。 */
    @Size(max = 64, message = "密码过长")
    private String password;

    private Integer isAdmin;

    private String roleTags;

    private List<String> mpPermissions;
}
