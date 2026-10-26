package com.gudu.xsd.modules.family.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** family 上下文对外视图（接口层直接序列化）。 */
public final class FamilyViews {

    private FamilyViews() {
    }

    /** 登录 / 切换后写入会话的家庭身份。 */
    public record SessionFamily(Long familyId, Long profileId, String role) {
    }

    /** 我加入的家庭（登录响应 families[]、家庭切换器）。 */
    public record FamilySummary(Long id, String name, String role, int memberCount) {
    }

    public record MemberView(Long accountId, String nickname, String role, LocalDateTime joinedAt, Long profileId) {
    }

    public record InvitationView(String code, LocalDateTime expiresAt, int maxUses, int usedCount, boolean revoked) {
    }

    /** 当前家庭详情：成员与角色；邀请列表仅 OWNER/ADMIN 可见。 */
    public record FamilyDetail(Long id, String name, String status, String myRole, List<MemberView> members,
                               List<InvitationView> invitations) {
    }

    /** 成员每日营养目标（kcal / g）。 */
    public record NutritionTargetView(int calorieTarget, int proteinTarget, int carbTarget, int fatTarget,
                                      String goal, int bmr) {
    }

    /** 邀请预览（游客可看）：家庭名 / 邀请人 / 是否仍可用。 */
    public record InvitationPreview(String familyName, String inviterNickname, LocalDateTime expiresAt,
                                    boolean usable, String reason) {
    }
}
