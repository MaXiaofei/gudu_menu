package com.gudu.xsd.modules.dish.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.modules.backoffice.domain.model.OperationAction;
import com.gudu.xsd.modules.backoffice.domain.model.OperationLog;
import com.gudu.xsd.modules.backoffice.domain.repository.OperationLogRepository;
import com.gudu.xsd.modules.dish.Dish;
import com.gudu.xsd.modules.dish.DishService;
import com.gudu.xsd.modules.dish.application.dto.DishLibraryViews.PageResult;
import com.gudu.xsd.modules.dish.application.dto.DishLibraryViews.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 公共菜谱库运营（V101）：列表（按上架状态筛选）、下架（必填原因）、重新上架；写操作落运营操作日志。
 * 只允许公共菜谱（family_id 为空）；家庭私有菜谱一律拒绝。
 */
@Service
@RequiredArgsConstructor
public class DishLibraryAppService {

    public static final String STATUS_ONLINE = "online";
    public static final String STATUS_OFFLINE = "offline";
    static final int REASON_MAX = 200;

    private final DishService dishService;
    private final OperationLogRepository logs;
    private final Clock clock;

    /** 公共菜谱分页。status：online 仅上架 / offline 仅已下架 / 其余（含空）全部。 */
    public PageResult page(String keyword, String status, int pageNum, int pageSize) {
        QueryWrapper<Dish> w = new QueryWrapper<Dish>().isNull("family_id");
        if (keyword != null && !keyword.isBlank()) w.like("name", keyword.trim());
        if (STATUS_ONLINE.equals(status)) w.isNull("offline_at");
        if (STATUS_OFFLINE.equals(status)) w.isNotNull("offline_at");
        w.orderByDesc("create_time").orderByDesc("id");
        IPage<Dish> p = dishService.page(new Page<>(Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100)), w);
        return new PageResult(p.getRecords().stream().map(DishLibraryAppService::row).toList(), p.getTotal());
    }

    /** 下架：原因必填（≤200 字）；已下架重复下架拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public Row offline(Long operatorId, Long dishId, String reason) {
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) throw new BizException("请填写下架原因");
        if (why.length() > REASON_MAX) throw new BizException("下架原因不能超过 " + REASON_MAX + " 字");
        Dish d = requirePublic(dishId);
        if (d.isOffline()) throw new BizException("这道菜已经下架了");
        LocalDateTime now = LocalDateTime.now(clock);
        dishService.update(new UpdateWrapper<Dish>().eq("id", dishId)
                .set("offline_at", now).set("offline_reason", why).set("offline_by", operatorId));
        DishService.bumpCatalogEpoch();
        logs.save(OperationLog.record(operatorId, OperationAction.OFFLINE_DISH, dishId, why, d.getName(), now));
        d.setOfflineAt(now);
        d.setOfflineReason(why);
        return row(d);
    }

    /** 重新上架：只对已下架的公共菜谱；原因选填。 */
    @Transactional(rollbackFor = Exception.class)
    public Row online(Long operatorId, Long dishId, String reason) {
        Dish d = requirePublic(dishId);
        if (!d.isOffline()) throw new BizException("这道菜没有下架");
        dishService.update(new UpdateWrapper<Dish>().eq("id", dishId)
                .set("offline_at", null).set("offline_reason", null).set("offline_by", null));
        DishService.bumpCatalogEpoch();
        logs.save(OperationLog.record(operatorId, OperationAction.ONLINE_DISH, dishId, reason, d.getName(),
                LocalDateTime.now(clock)));
        d.setOfflineAt(null);
        d.setOfflineReason(null);
        return row(d);
    }

    /** 读到的必须是公共菜谱（别家私有菜谱读不到 = 404；本家私有菜谱 = 400）。 */
    private Dish requirePublic(Long dishId) {
        Dish d = dishId == null ? null : dishService.getById(dishId);
        if (d == null) throw BizException.notFound("菜谱不存在");
        if (d.getFamilyId() != null) throw new BizException("只有公共菜谱可以上下架");
        return d;
    }

    private static Row row(Dish d) {
        return new Row(d.getId(), d.getName(), d.getCoverUrl(), d.getPrepTime(), d.getCookTime(), d.getSource(),
                d.getSourceName(), d.getCreateTime(), d.isOffline(), d.getOfflineAt(), d.getOfflineReason());
    }
}
