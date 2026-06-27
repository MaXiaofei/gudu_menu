package com.gudu.xsd.modules.member;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限矩阵纯函数测试(算法地基,参照 PantryService 纯函数范式)。
 * new MpPermissionService(null):不依赖 Mapper,只测 resolvePermissions / hasPermission。
 */
class MpPermissionServiceTest {

    private final MpPermissionService svc = new MpPermissionService(null);

    @Test
    void 掌勺角色_默认全权() {
        // role=32(掌勺), 无个人勾选
        Set<String> perms = svc.resolvePermissions("32", null);
        assertThat(perms).contains(
                "dish.create", "menu.plan", "pantry.manage", "shopping.generate", "health.view");
    }

    @Test
    void 普通成员_默认只能点评和看菜单() {
        // role=34(普通成员)
        Set<String> perms = svc.resolvePermissions("34", null);
        assertThat(perms).contains("review.create", "menu.view");
        assertThat(perms).doesNotContain("dish.create", "menu.plan");
    }

    @Test
    void 个人勾选_覆盖角色默认() {
        // 普通成员 + 勾选了排菜权限(并集语义:个人勾选只能增不能减)
        Set<String> perms = svc.resolvePermissions("34", List.of("menu.plan"));
        assertThat(perms).contains("menu.plan");
    }

    @Test
    void hasPermission_按解析集合判定() {
        assertThat(svc.hasPermission("32", null, "dish.create")).isTrue();
        assertThat(svc.hasPermission("34", null, "dish.create")).isFalse();
    }

    @Test
    void 多角色取并集默认模板() {
        // 33(备菜)+ 34(普通):合并备菜相关 + 点评只读
        Set<String> perms = svc.resolvePermissions("33,34", null);
        assertThat(perms).contains("menu.plan", "pantry.manage", "shopping.generate", "review.create");
    }
}
