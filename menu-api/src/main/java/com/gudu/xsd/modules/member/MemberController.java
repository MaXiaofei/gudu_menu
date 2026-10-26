package com.gudu.xsd.modules.member;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import com.gudu.xsd.common.security.TenantSession;
import com.gudu.xsd.common.tenant.TenantContext;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.family.application.FamilyAppService;
import com.gudu.xsd.modules.family.application.FamilyQueryApi;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 本家成员档案（multi-tenant-family E.5）。member 表按当前家庭自动过滤：跨家庭 id 一律「不存在」（404）。
 *
 * <ul>
 *   <li>读：本家成员；</li>
 *   <li>写：OWNER/ADMIN 可写本家任意档案；普通成员只能改本人档案；</li>
 *   <li>账号字段（手机号 / 密码 / 超管）不再经此接口写——走 /account；
 *       唯一例外：平台超管带 phone+password 新增 = 代建手机号账号并加入当前家庭（兼容旧后台与 E2E）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
@Tag(name = "家庭成员")
public class MemberController {

    static final String NOT_FOUND = "成员不存在";

    private final MemberService svc;
    private final MpPermissionService permSvc;
    private final AdminChecker adminChecker;
    private final FamilyAppService familyApp;
    private final FamilyQueryApi familyQuery;

    /** 本家成员档案（分页）。 */
    @GetMapping
    public R<IPage<Member>> list(PageQuery q) {
        return R.ok(svc.page(q));
    }

    /**
     * 切换当前就餐成员（只决定「谁在吃」，权限仍按登录账号）。只能切到本家成员：
     * 本人或无账号档案；OWNER/ADMIN 可切到本家任意成员。
     */
    @PostMapping("/current")
    public R<?> setCurrent(@RequestParam Long memberId) {
        Member target = inFamily(memberId);
        if (!canManage() && !isMine(target) && !AdminChecker.isProfileOnly(target)) {
            throw BizException.forbidden("只能切换为本人或家庭成员档案");
        }
        TenantSession.setCurrentMember(memberId);
        return R.ok(null);
    }

    /** 读取当前就餐成员 id（本人在当前家庭的档案，或切换后的档案）。 */
    @GetMapping("/current")
    public R<Long> getCurrent() {
        Long id = TenantContextHolder.profileIdOrNull();
        return R.ok(id != null ? id : TenantSession.currentMemberId());
    }

    /** 解析本家某成员最终权限集合(家庭角色默认 + 档案勾选)。供前端按钮显隐。 */
    @GetMapping("/{id}/permissions")
    public R<Set<String>> permissions(@PathVariable Long id) {
        inFamily(id);
        return R.ok(permSvc.resolveByMemberId(id));
    }

    /** 全量功能 key + 中文映射(供后台 member 表单多选项)。 */
    @GetMapping("/permissions/keys")
    public R<Map<String, String>> permKeys() {
        return R.ok(MpPermissionService.allPermLabels());
    }

    /**
     * 新增本家成员档案（OWNER/ADMIN）。平台超管带 phone+password：代建手机号账号并加入当前家庭，返回其档案 id。
     */
    @PostMapping
    public R<?> add(@RequestBody @Valid MemberSaveRequest req) {
        long caller = StpUtil.getLoginIdAsLong();
        boolean admin = adminChecker.isAdmin(caller);
        if (!admin && !canManage()) throw BizException.forbidden("只有主理人或管理员可以添加成员");
        if (admin && notBlank(req.getPhone()) && notBlank(req.getPassword())) {
            Long familyId = TenantContextHolder.requireFamilyId();
            Long accountId = familyApp.registerPhoneAccount(req.getPhone(), req.getPassword(), req.getName(), familyId);
            Long profileId = familyQuery.profileIdOf(familyId, accountId).orElseThrow();
            Member m = toEntity(req, true);
            m.setId(profileId);
            svc.updateById(m);
            return R.ok(profileId);
        }
        Member m = toEntity(req, true);
        m.setId(null);
        svc.save(m);
        return R.ok(m.getId());
    }

    /** 编辑本家成员档案：OWNER/ADMIN 任意；普通成员只能改本人档案。 */
    @PutMapping
    public R<?> update(@RequestBody @Valid MemberSaveRequest req) {
        if (req.getId() == null) throw new BizException("缺少成员 id");
        Member target = inFamily(req.getId());
        boolean manager = canManage();
        if (!manager && !isMine(target)) throw BizException.forbidden("只能编辑本人档案");
        svc.updateById(toEntity(req, manager));
        return R.ok(null);
    }

    /** 删除本家无账号的成员档案（OWNER/ADMIN）；有账号的成员请在家庭页「移除成员」。 */
    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        Member target = inFamily(id);
        if (isMine(target)) throw BizException.forbidden("不能删除自己");
        if (!canManage()) throw BizException.forbidden("只有主理人或管理员可以删除成员");
        if (!AdminChecker.isProfileOnly(target)) throw BizException.forbidden("有账号的成员请在家庭页移除");
        svc.removeById(id);
        return R.ok(null);
    }

    /**
     * 本家成员每日营养目标（BMR × 活动系数 + 体控目标调整，见 NutritionTargetCalculator）。
     * 资料不全或档案不属于本家 → data=null。
     */
    @GetMapping("/{id}/nutrition-target")
    public R<NutritionTargetResponse> nutritionTarget(@PathVariable Long id) {
        Long familyId = TenantContextHolder.familyIdOrNull();
        return R.ok(familyQuery.nutritionTarget(familyId, id).map(t -> {
            NutritionTargetResponse rsp = new NutritionTargetResponse();
            rsp.setCalorieTarget(t.calorieTarget());
            rsp.setProteinTarget(t.proteinTarget());
            rsp.setCarbTarget(t.carbTarget());
            rsp.setFatTarget(t.fatTarget());
            rsp.setGoal(t.goal());
            rsp.setBmr(t.bmr());
            return rsp;
        }).orElse(null));
    }

    // ------------------------------------------------------------------

    /** 本家成员（租户插件已按当前家庭过滤）；查不到 = 不存在或不属于本家 → 404。 */
    private Member inFamily(Long id) {
        Member m = id == null ? null : svc.getById(id);
        if (m == null) throw BizException.notFound(NOT_FOUND);
        return m;
    }

    private static boolean canManage() {
        TenantContext ctx = TenantContextHolder.get();
        return ctx != null && ctx.canManage();
    }

    private static boolean isMine(Member m) {
        TenantContext ctx = TenantContextHolder.get();
        Long me = ctx != null && ctx.accountId() != null ? ctx.accountId() : loginIdOrNull();
        return me != null && Objects.equals(me, m.getAccountId());
    }

    private static Long loginIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 入参 → 实体：只拷白名单档案字段；角色标签 / 档案级功能勾选仅 OWNER/ADMIN（manager）可写。
     * 账号字段（phone/password/isAdmin）一律不写入 member（V75 起凭证在 account 表）。
     */
    private Member toEntity(MemberSaveRequest req, boolean manager) {
        Member m = new Member();
        m.setId(req.getId());
        m.setName(req.getName());
        m.setHealthProfile(req.getHealthProfile());
        m.setGoal(req.getGoal());
        m.setActivityLevel(req.getActivityLevel());
        if (manager) {
            m.setRoleTags(req.getRoleTags());
            m.setMpPermissions(req.getMpPermissions());
        }
        return m;
    }
}
