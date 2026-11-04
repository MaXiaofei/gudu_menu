package com.gudu.xsd.modules.backoffice.domain.model;

/**
 * 账号所属家庭的简要元数据（运营后台只读）：只含家庭 id / 名称 / 该账号的角色 / 家庭状态，
 * 不含菜单、余量、采购、成员档案、健康信息。
 *
 * @param role OWNER / ADMIN / MEMBER
 */
public record FamilyBrief(Long familyId, String name, String role, String status) {
}
