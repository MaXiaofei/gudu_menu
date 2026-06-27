package com.gudu.xsd.modules.member;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gudu.xsd.common.PageQuery;
import com.gudu.xsd.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping
    public R<IPage<Member>> list(PageQuery q) {
        return R.ok(svc.page(q));
    }

    /** 切换当前就餐成员（存 Sa-Token session）。 */
    @PostMapping("/current")
    public R<?> setCurrent(@RequestParam Long memberId) {
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

    @PostMapping
    public R<?> add(@RequestBody Member m) {
        applyPassword(m);
        svc.save(m);
        return R.ok(m.getId());
    }

    @PutMapping
    public R<?> update(@RequestBody Member m) {
        applyPassword(m);
        svc.updateById(m);
        return R.ok(null);
    }

    /**
     * 把表单传入的明文 password( transient )加密为 passwordHash。
     * 留空则不动现有哈希(编辑时不改密码场景)。
     */
    private void applyPassword(Member m) {
        if (m.getPassword() != null && !m.getPassword().isBlank()) {
            m.setPasswordHash(passwordEncoder.encode(m.getPassword()));
        }
        m.setPassword(null);
    }

    @DeleteMapping("/{id}")
    public R<?> del(@PathVariable Long id) {
        svc.removeById(id);
        return R.ok(null);
    }
}
