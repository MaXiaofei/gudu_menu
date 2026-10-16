package com.gudu.xsd.modules.member;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/member")
@RequiredArgsConstructor
@Tag(name = "家庭成员")
public class MemberController {

    private final MemberService svc;
    private final MpPermissionService permSvc;
    private final AdminChecker adminChecker;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ========== Mifflin-St Jeor BMR 常量 ==========
    private static final double MALE_BMR_CONST = 5;
    private static final double FEMALE_BMR_CONST = -161;

    @GetMapping
    public R<IPage<Member>> list(PageQuery q) {
        return R.ok(svc.page(q));
    }

    /**
     * 切换当前就餐成员（存 Sa-Token session）。
     * 仅能切到本人或纯成员档案（宝宝/老人等无账号成员）；超管可切任意成员。
     * 注：功能权限按登录账号判定，就餐成员只决定「谁在吃」。
     */
    @PostMapping("/current")
    public R<?> setCurrent(@RequestParam Long memberId) {
        long caller = StpUtil.getLoginIdAsLong();
        if (memberId != caller && !adminChecker.isAdmin(caller)) {
            Member target = svc.getById(memberId);
            if (!AdminChecker.isProfileOnly(target)) {
                throw BizException.forbidden("只能切换为本人或家庭成员档案");
            }
        }
        StpUtil.getSession().set("currentMemberId", memberId);
        return R.ok(null);
    }

    /** 读取当前就餐成员 id。 */
    @GetMapping("/current")
    public R<Long> getCurrent() {
        return R.ok(StpUtil.getSession().getLong("currentMemberId"));
    }

    /** 解析某成员最终权限集合(角色默认 + 个人勾选并集)。供前端按钮显隐。 */
    @GetMapping("/{id}/permissions")
    public R<Set<String>> permissions(@PathVariable Long id) {
        return R.ok(permSvc.resolveByMemberId(id));
    }

    /** 全量功能 key + 中文映射(供后台 member 表单多选项)。 */
    @GetMapping("/permissions/keys")
    public R<Map<String, String>> permKeys() {
        return R.ok(MpPermissionService.allPermLabels());
    }

    /** 新增成员。非超管只能建纯成员档案（账号/权限字段被忽略）。 */
    @PostMapping
    public R<?> add(@RequestBody @Valid MemberSaveRequest req) {
        boolean admin = adminChecker.isAdmin(StpUtil.getLoginIdAsLong());
        Member m = toEntity(req, admin);
        m.setId(null);
        svc.save(m);
        return R.ok(m.getId());
    }

    /** 编辑成员。超管可改任意成员全部字段；其他人只能改本人或纯成员档案的档案字段。 */
    @PutMapping
    public R<?> update(@RequestBody @Valid MemberSaveRequest req) {
        if (req.getId() == null) throw new BizException("缺少成员 id");
        long caller = StpUtil.getLoginIdAsLong();
        boolean admin = adminChecker.isAdmin(caller);
        if (!admin && req.getId() != caller && !AdminChecker.isProfileOnly(svc.getById(req.getId()))) {
            throw BizException.forbidden("只能编辑本人或家庭成员档案");
        }
        svc.updateById(toEntity(req, admin));
        return R.ok(null);
    }

    /** 删除成员。超管可删任意成员（除本人）；其他人只能删纯成员档案。 */
    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        long caller = StpUtil.getLoginIdAsLong();
        if (id == caller) throw BizException.forbidden("不能删除自己");
        if (!adminChecker.isAdmin(caller) && !AdminChecker.isProfileOnly(svc.getById(id))) {
            throw BizException.forbidden("只能删除家庭成员档案");
        }
        svc.removeById(id);
        return R.ok(null);
    }

    /**
     * 入参 → 实体：只拷白名单字段；非超管丢弃账号与权限字段。
     * 未拷字段保持 null，updateById 按非空字段更新，不会覆盖现有值。
     */
    private Member toEntity(MemberSaveRequest req, boolean admin) {
        Member m = new Member();
        m.setId(req.getId());
        m.setName(req.getName());
        m.setHealthProfile(req.getHealthProfile());
        m.setGoal(req.getGoal());
        m.setActivityLevel(req.getActivityLevel());
        if (admin) {
            m.setPhone(req.getPhone());
            m.setIsAdmin(req.getIsAdmin());
            m.setRoleTags(req.getRoleTags());
            m.setMpPermissions(req.getMpPermissions());
            if (req.getPassword() != null && !req.getPassword().isBlank()) {
                m.setPasswordHash(passwordEncoder.encode(req.getPassword()));
            }
        }
        return m;
    }

    /**
     * 计算成员每日营养目标（BMR × 活动系数 + 体控目标调整）。
     *
     * 高度数据从 healthProfile JSON 中取 height(cm)/weight(kg)/age/gender。
     * 目标从 goal 字段取，活动水平从 activityLevel 字段取。
     * 任一缺失返回 null（data=null），前端据此判断是否支持精准模式。
     */
    @GetMapping("/{id}/nutrition-target")
    public R<NutritionTargetResponse> nutritionTarget(@PathVariable Long id) {
        Member m = svc.getById(id);
        if (m == null) return R.ok(null);

        Map<String, Object> hp = m.getHealthProfile();
        if (hp == null) return R.ok(null);

        Object weightObj = hp.get("weight");
        Object heightObj = hp.get("height");
        Object ageObj = hp.get("age");
        Object genderObj = hp.get("gender");

        if (weightObj == null || heightObj == null || ageObj == null) return R.ok(null);

        double weight = toDouble(weightObj);
        double height = toDouble(heightObj);
        int age = toInt(ageObj);
        String gender = genderObj != null ? genderObj.toString() : "male";

        if (weight <= 0 || height <= 0 || age <= 0) return R.ok(null);

        // Mifflin-St Jeor BMR
        double bmr = 10 * weight + 6.25 * height - 5 * age;
        bmr += "female".equalsIgnoreCase(gender) ? FEMALE_BMR_CONST : MALE_BMR_CONST;

        // 活动系数
        String al = m.getActivityLevel();
        double activityMultiplier = activityMultiplier(al);

        double tdee = bmr * activityMultiplier;

        // 体控目标调整
        String goal = m.getGoal();
        double goalAdjust = goalAdjust(goal);
        int calorieTarget = (int) Math.round(tdee + goalAdjust);

        // 蛋白目标：体重(kg) × 系数
        double proteinPerKg = "GAIN".equalsIgnoreCase(goal) ? 2.0
                : "LOSE".equalsIgnoreCase(goal) ? 2.0 : 1.6;
        int proteinTarget = (int) Math.round(weight * proteinPerKg);

        // 脂肪：总热量 × 25%
        int fatTarget = (int) Math.round(calorieTarget * 0.25 / 9);

        // 碳水：剩余热量
        int carbTarget = (int) Math.round((calorieTarget - proteinTarget * 4 - fatTarget * 9) / 4.0);
        if (carbTarget < 0) carbTarget = 0;

        NutritionTargetResponse rsp = new NutritionTargetResponse();
        rsp.setCalorieTarget(calorieTarget);
        rsp.setProteinTarget(proteinTarget);
        rsp.setCarbTarget(carbTarget);
        rsp.setFatTarget(fatTarget);
        rsp.setGoal(goal);
        rsp.setBmr((int) Math.round(bmr));
        return R.ok(rsp);
    }

    private double activityMultiplier(String level) {
        if (level == null) return 1.2;
        return switch (level.toUpperCase()) {
            case "SEDENTARY" -> 1.2;
            case "LIGHT" -> 1.375;
            case "MODERATE" -> 1.55;
            case "ACTIVE" -> 1.725;
            default -> 1.2;
        };
    }

    private double goalAdjust(String goal) {
        if (goal == null) return 0;
        return switch (goal.toUpperCase()) {
            case "LOSE" -> -500;
            case "GAIN" -> 300;
            default -> 0;
        };
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }
}
