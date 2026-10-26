package com.gudu.xsd.modules.family.domain.repository;

import java.util.Optional;

/**
 * 成员角色缓存端口（TenantInterceptor 每请求校验成员关系，design §4.1：Redis 5 分钟，事件主动失效）。
 * 取值 OWNER/ADMIN/MEMBER 或 {@link #NONE}（非成员，负缓存）。
 */
public interface FamilyRoleCache {

    String NONE = "NONE";

    Optional<String> get(Long familyId, Long accountId);

    void put(Long familyId, Long accountId, String roleOrNone);

    /** 失效整个家庭（成员变更 / 解散）。 */
    void evictFamily(Long familyId);
}
