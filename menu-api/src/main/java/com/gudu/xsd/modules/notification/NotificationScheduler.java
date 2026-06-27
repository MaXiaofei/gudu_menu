package com.gudu.xsd.modules.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.modules.member.Member;
import com.gudu.xsd.modules.member.mapper.MemberMapper;
import com.gudu.xsd.modules.pantry.PantryService;
import com.gudu.xsd.modules.pantry.PantryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知业务触发调度器：把已有业务事件（临期库存等）接到 NotificationService，发站内通知。
 *
 * <p>不新建表，复用 notification + NotificationService + InAppChannel。
 * 通知对象：
 * <ul>
 *   <li>临期库存 → 家庭「掌勺」成员（role_tags 含 32），提醒掌勺的人处理快过期食材。</li>
 * </ul>
 *
 * <p>{@link #scanExpiring()} 每天 8 点跑；{@link #checkExpiring(int)} 可被 Controller 手动触发（便于测试/演示）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationScheduler {

    /** role_tags 中代表「掌勺」的字典 id（sys_dict group=role）。 */
    private static final String CHEF_ROLE_TAG = "32";
    /** 默认临期窗口（天）。 */
    private static final int DEFAULT_EXPIRING_DAYS = 3;

    private final PantryService pantryService;
    private final NotificationService notificationService;
    private final MemberMapper memberMapper;

    /** 每天早 8 点扫临期库存。 */
    @Scheduled(cron = "0 0 8 * * *")
    public void scanExpiring() {
        int n = checkExpiring(DEFAULT_EXPIRING_DAYS);
        log.info("临期扫描完成：发送 {} 条提醒", n);
    }

    /**
     * 扫距今 N 天内临期的库存，逐条通知掌勺成员。
     *
     * @param days 临期窗口（天）
     * @return 发送的通知条数（等于临期食材数；无掌勺成员或无临期返回 0）
     */
    public int checkExpiring(int days) {
        List<PantryVO> expiring = pantryService.listExpiring(days);
        if (expiring.isEmpty()) return 0;
        Long chefId = findChefMemberId();
        if (chefId == null) {
            log.warn("未找到掌勺成员（role_tags 含 {}），跳过临期通知", CHEF_ROLE_TAG);
            return 0;
        }
        for (PantryVO p : expiring) {
            notificationService.send(
                    new NotificationPayload(chefId, "expiry",
                            "食材临期提醒",
                            "「" + pantryIngredientName(p) + "」将在 " + days + " 天内过期"),
                    "in_app");
        }
        return expiring.size();
    }

    /** 找第一个 role_tags 含掌勺(32)的成员 id；没有返回 null。 */
    private Long findChefMemberId() {
        Member m = memberMapper.selectOne(
                new QueryWrapper<Member>().like("role_tags", CHEF_ROLE_TAG).last("LIMIT 1"));
        return m == null ? null : m.getId();
    }

    /** 取食材中文名：优先 VO 上的 ingredientName，取不到回退 ingredientId。 */
    private String pantryIngredientName(PantryVO p) {
        if (p.getIngredientName() != null && !p.getIngredientName().isBlank()) {
            return p.getIngredientName();
        }
        return p.getIngredientId() == null ? "未知食材" : ("食材#" + p.getIngredientId());
    }
}
