package com.gudu.xsd.modules.dict;

import cn.dev33.satoken.stp.StpUtil;
import com.gudu.xsd.modules.identity.application.AccountQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 全局共享目录（字典 / 食材库 / 营养指标）写权限判定：
 * 这些数据所有家庭共用，改删只允许平台运营（OPERATOR / SUPER_ADMIN）；
 * 普通用户仅能受限新建（见 DictController / IngredientController）。
 */
@Component
@RequiredArgsConstructor
public class CatalogWriteGuard {

    private final AccountQueryApi accounts;

    /** 当前登录账号是否平台运营及以上；未登录或查询异常一律按非运营处理。 */
    public boolean isOperator() {
        try {
            return StpUtil.isLogin() && accounts.isOperator(StpUtil.getLoginIdAsLong());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
