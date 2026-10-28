package com.gudu.xsd.modules.nutrition.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.gudu.xsd.common.BizException;
import com.gudu.xsd.common.tenant.TenantContextHolder;
import com.gudu.xsd.modules.contentsecurity.application.ContentSecurityAppService;
import com.gudu.xsd.modules.contentsecurity.domain.model.ContentPurpose;
import com.gudu.xsd.modules.dict.CatalogWriteGuard;
import com.gudu.xsd.modules.nutrition.Ingredient;
import com.gudu.xsd.modules.nutrition.IngredientAlias;
import com.gudu.xsd.modules.nutrition.IngredientService;
import com.gudu.xsd.modules.nutrition.domain.repository.IngredientReferences;
import com.gudu.xsd.modules.nutrition.domain.service.IngredientNameNormalizer;
import com.gudu.xsd.modules.nutrition.mapper.IngredientAliasMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 食材目录（家庭侧）用例：新建去重 / 改 / 删的归属规则。
 * <ul>
 *   <li>公共食材（family_id NULL）：只有管理后台登录的平台运营能改 / 删；</li>
 *   <li>本家私有食材：本家成员可改名 / 改分类 / 改食用属性，删除前检查是否在用；别家私有读不到（按不存在处理）。</li>
 * </ul>
 * 读可见性（公共 + 本家）由 {@code IngredientVisibilityInnerInterceptor} 兜底，这里读到的一定是可见的。
 */
@Service
@RequiredArgsConstructor
public class IngredientCatalogAppService {

    static final int NAME_MAX = 64;

    private final IngredientService ingredients;
    private final IngredientAliasMapper aliases;
    private final IngredientReferences references;
    private final CatalogWriteGuard guard;
    private final ContentSecurityAppService contentSecurity;

    /** 新建 / 去重结果：existed = public（已有公共食材，含别名命中）/ family（本家已有）/ created（新建）。 */
    public record Resolved(Long id, String name, String existed, boolean familyOwned) {

        public String tip() {
            return "public".equals(existed) ? "已有公共食材：" + name : null;
        }
    }

    /** 普通用户新建：名称规整后先对公共名 / 别名、再对本家已有，命中则返回已有；否则新建本家私有食材（不写营养）。 */
    @Transactional(rollbackFor = Exception.class)
    public Resolved resolveOrCreate(Ingredient in) {
        String name = requireName(in == null ? null : in.getName());
        Ingredient pub = findPublicByNameOrAlias(name);
        if (pub != null) return new Resolved(pub.getId(), pub.getName(), "public", false);
        Long familyId = TenantContextHolder.requireFamilyId();
        Ingredient own = findOwnByName(familyId, name, null);
        if (own != null) return new Resolved(own.getId(), own.getName(), "family", true);
        contentSecurity.assertUserText(ContentPurpose.CATALOG_NAME, name);
        Ingredient n = new Ingredient();
        n.setName(name);
        n.setFamilyId(familyId);
        n.setPurchaseCategoryId(in.getPurchaseCategoryId());
        n.setEdible(validEdible(in.getEdible()));
        n.setCreateTime(LocalDateTime.now());
        ingredients.save(n);
        return new Resolved(n.getId(), n.getName(), "created", true);
    }

    /** 名称是否命中公共食材名或其别名；命中返回公共食材，否则 null。 */
    public Ingredient findPublicByNameOrAlias(String normalizedName) {
        Ingredient pub = ingredients.getOne(new QueryWrapper<Ingredient>()
                .eq("name", normalizedName).isNull("family_id").orderByAsc("id").last("LIMIT 1"), false);
        if (pub != null) return pub;
        IngredientAlias a = aliases.selectOne(new QueryWrapper<IngredientAlias>()
                .eq("alias", normalizedName).last("LIMIT 1"));
        if (a == null) return null;
        return ingredients.getOne(new QueryWrapper<Ingredient>()
                .eq("id", a.getIngredientId()).isNull("family_id"), false);
    }

    private Ingredient findOwnByName(Long familyId, String name, Long excludeId) {
        QueryWrapper<Ingredient> qw = new QueryWrapper<Ingredient>().eq("name", name).eq("family_id", familyId);
        if (excludeId != null) qw.ne("id", excludeId);
        return ingredients.getOne(qw.orderByAsc("id").last("LIMIT 1"), false);
    }

    /** 修改：公共食材仅管理后台运营（整条更新）；本家私有食材本家成员可改名 / 分类 / 食用属性。 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Ingredient in) {
        if (in == null || in.getId() == null) throw new BizException("缺少食材 id");
        Ingredient old = ingredients.getById(in.getId());
        if (old == null) throw BizException.notFound("食材不存在");
        if (old.getFamilyId() == null) {
            if (!guard.canMaintainPublic()) throw BizException.forbidden("平台食材，由平台维护");
            in.setFamilyId(null);
            ingredients.updateById(in);
            return;
        }
        Ingredient u = new Ingredient();
        u.setId(old.getId());
        if (in.getName() != null) {
            String name = requireName(in.getName());
            if (!name.equals(old.getName())) {
                Ingredient pub = findPublicByNameOrAlias(name);
                if (pub != null) throw new BizException("已有公共食材「" + pub.getName() + "」，不能重名");
                if (findOwnByName(old.getFamilyId(), name, old.getId()) != null) {
                    throw new BizException("你家已经有叫「" + name + "」的食材了");
                }
                contentSecurity.assertUserText(ContentPurpose.CATALOG_NAME, name);
                u.setName(name);
            }
        }
        if (in.getPurchaseCategoryId() != null) u.setPurchaseCategoryId(in.getPurchaseCategoryId());
        if (in.getEdible() != null) u.setEdible(validEdible(in.getEdible()));
        if (u.getName() == null && u.getPurchaseCategoryId() == null && u.getEdible() == null) return; // 无变化
        ingredients.updateById(u);
    }

    /** 删除：公共食材仅管理后台运营；本家私有食材未被引用才可删（软删）。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Ingredient old = id == null ? null : ingredients.getById(id);
        if (old == null) throw BizException.notFound("食材不存在");
        if (old.getFamilyId() == null) {
            if (!guard.canMaintainPublic()) throw BizException.forbidden("平台食材，由平台维护");
            aliases.delete(new QueryWrapper<IngredientAlias>().eq("ingredient_id", id));
            ingredients.removeById(id);
            return;
        }
        Map<String, Long> used = references.usage(id);
        if (!used.isEmpty()) {
            throw new BizException("这个食材正在使用（" + String.join("/", used.keySet()) + "），不能删除");
        }
        ingredients.removeById(id);
    }

    private static String requireName(String raw) {
        String name = IngredientNameNormalizer.normalize(raw);
        if (name.isEmpty()) throw new BizException("食材名称不能为空");
        if (name.length() > NAME_MAX) throw new BizException("食材名称不超过 " + NAME_MAX + " 字");
        return name;
    }

    private static Integer validEdible(Integer v) {
        return v != null && v >= 1 && v <= 3 ? v : 1;
    }
}
