import 'package:flutter/material.dart';

import '../../core/image_helper.dart';
import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../models/dish.dart';
import '../../models/menu.dart';
import '../../services/dish_service.dart';
import '../../services/menu_service.dart';
import '../../services/shopping_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/hero_gallery.dart';
import '../../widgets/image_viewer.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/skeleton.dart';
import '../../widgets/step_carousel.dart';
import '../../widgets/tone_badge.dart';
import '../menu/cook_confirm_sheet.dart';

/// 菜品详情。
/// 封面 + 营养区 + 用料 + 做法步骤 + 加到食集/去点评。
///
/// 图片策略：
/// - 列表/详情默认加载缩略图（/thumbnail/xxx.jpg），节省流量 + 加载快。
/// - 点击图片弹出全屏可查看器，加载原图（/original/xxx.jpg），支持双指缩放。
class DishDetailPage extends StatefulWidget {
  final int id;
  /// 是否显示底部操作按钮（加到食集 / 去点评）。
  /// 从食集详情点进来查看菜谱时为 false，避免冗余操作。
  final bool showActions;
  const DishDetailPage({super.key, required this.id, this.showActions = true});
  @override
  State<DishDetailPage> createState() => _DishDetailPageState();
}

class _DishDetailPageState extends State<DishDetailPage> {
  DishDetail? _detail;
  final int _serving = 1;
  bool _loading = true;

  /// 详情头部展示的标签（菜系 + 分类 + 标签去空合并）。
  List<String> get _dishTags => _detail == null
      ? const []
      : [
          ..._detail!.dish.cuisineNames,
          ..._detail!.dish.categoryNames,
          ..._detail!.dish.tagNames,
        ].where((s) => s.isNotEmpty).toList();

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (!_loading) setState(() => _loading = true);
    try {
      _detail = await DishService.detail(widget.id);
    } catch (_) {
      // 失败 → _detail 为 null，下方渲染 ErrorView（含重试），非静默
      _detail = null;
    }
    if (mounted) setState(() => _loading = false);
  }

  /// 现在就做（单菜直做，G1）：拉本菜用料 → 「这顿饭用了什么」确认弹窗
  /// （默认 食材=用完了 / 调料=用了一些）→ POST /cook/dish/{id} → 结果弹窗。
  /// 「跳过，不更新库存」= 只写食记（usedUp/partiallyUsed 为空）。
  bool _cooking = false;
  Future<void> _cookNow() async {
    if (_cooking) return;
    setState(() => _cooking = true);
    try {
      final materials =
          await DishService.dishCookMaterials(widget.id, servings: _serving);
      if (!mounted) return;
      final result = await showModalBottomSheet<CookConfirmResult>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (_) => CookConfirmSheet(
          materials: materials,
          dishName: _detail?.dish.name,
          coverUrl: _detail?.dish.coverUrl,
        ),
      );
      if (!mounted || result == null) return; // 关弹窗 = 取消
      await DishService.cookDish(
        widget.id,
        servings: _serving,
        usedUp: result.usedUp,
        partiallyUsed: result.partiallyUsed,
      );
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (_) => CookResultSheet(
          dishId: widget.id,
          items: materials.items,
          usedUp: result.usedUp,
          partiallyUsed: result.partiallyUsed,
        ),
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('做菜记录失败：${errorText(e)}')));
      }
    } finally {
      if (mounted) setState(() => _cooking = false);
    }
  }

  /// 打开全屏图片查看器（加载原图）。
  void _openViewer(String url) {
    final urls = ImageHelper.resolve(url);
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ImageViewer(
          thumbnailUrl: urls.thumbnail,
          originalUrl: urls.original,
        ),
      ),
    );
  }

  /// 加到食集：
  /// - 查"今天及以后"创建的食集 → 有则弹窗让用户选择加到哪个
  /// - 没有则弹输入框新建（名字预填菜谱名，可自定义）
  /// - 新建食集名字默认取菜谱名，用户可修改
  bool _adding = false;
  Future<void> _addToMenu() async {
    if (_adding) return;
    setState(() => _adding = true);
    final dishName = _detail?.dish.name ?? '新食集';
    try {
      final page = await MenuService.list(pageNum: 1, pageSize: 50);
      // 过滤：今天及以后创建的食集（createTime >= 今天 0 点）
      final todayStart = DateTime(DateTime.now().year, DateTime.now().month, DateTime.now().day);
      final recentMenus = page.records.where((m) {
        final created = m.createdAt;
        return created != null && !created.isBefore(todayStart);
      }).toList();

      if (!mounted) return;
      if (recentMenus.isEmpty) {
        // 无今天及以后的食集 → 弹输入框新建（预填菜谱名）
        final name = await _showNameDialog(dishName);
        if (name == null) {
          if (mounted) setState(() => _adding = false);
          return;
        }
        await MenuService.createMenu(name, dishIds: [widget.id]);
        if (!mounted) return;
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('已加入新食集「$name」')));
      } else {
        // 有今天及以后的食集 → 弹窗选择
        final selectedId = await _showMenuPicker(recentMenus);
        if (selectedId == null) {
          if (mounted) setState(() => _adding = false);
          return;
        }
        if (selectedId == -1) {
          // 选"新建食集" → 弹输入框（预填菜谱名）
          final name = await _showNameDialog(dishName);
          if (name == null) {
            if (mounted) setState(() => _adding = false);
            return;
          }
          await MenuService.createMenu(name, dishIds: [widget.id]);
          if (!mounted) return;
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('已加入新食集「$name」')));
        } else {
          // 加入已有食集（传 dishName 用于拼接食集名）
          await MenuService.addDishToMenu(selectedId, widget.id, dishName: dishName);
          final menuName = recentMenus.firstWhere((m) => m.id == selectedId).name;
          if (!mounted) return;
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('已加入食集「$menuName」')));
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('加入食集失败')));
      }
    }
    if (mounted) setState(() => _adding = false);
  }

  /// 弹输入框让用户输入食集名字，[defaultName] 预填默认值（菜谱名）。
  /// 返回用户输入的名字，取消返回 null。
  Future<String?> _showNameDialog(String defaultName) async {
    final ctrl = TextEditingController(text: defaultName);
    return showDialog<String>(
      context: context,
      builder: (ctx) {
        final t = AppTokens.of(ctx);
        return AlertDialog(
          title: Text('新建食集',
              style: t.textStyles.lg),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            decoration: const InputDecoration(
              labelText: '食集名字',
              hintText: '输入食集名字',
              isDense: true,
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(null),
              child: const Text('取消'),
            ),
            ElevatedButton(
              onPressed: () {
                final name = ctrl.text.trim();
                Navigator.of(ctx).pop(name.isEmpty ? defaultName : name);
              },
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }

  /// 弹窗选择当天食集，返回选中的 menuId（-1=新建，null=取消）。
  Future<int?> _showMenuPicker(List<Menu> menus) async {
    return showModalBottomSheet<int>(
      context: context,
      builder: (ctx) {
        final t = AppTokens.of(ctx);
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.all(AppTokens.sp16),
                child: Text('加到哪个食集？', style: t.textStyles.lg),
              ),
              const Divider(height: 1),
              ...menus.map((m) {
                    final created = m.createdAt;
                    final dateStr = created != null
                        ? '${created.month}/${created.day} ${created.hour.toString().padLeft(2, '0')}:${created.minute.toString().padLeft(2, '0')}'
                        : '';
                    return ListTile(
                      leading: Icon(Icons.restaurant_menu, color: t.primary),
                      title: Text(m.name),
                      subtitle: Text(
                        '$dateStr · 份数 ${m.servingCount ?? 1} · ${m.isDone ? '已完成' : '进行中'}',
                        style: t.textStyles.caption.copyWith(color: t.caption),
                      ),
                    onTap: () => Navigator.of(ctx).pop(m.id),
                    );
                  }),
              const Divider(height: 1),
              ListTile(
                leading: Icon(Icons.add_circle, color: t.primary),
                title: const Text('新建食集'),
                onTap: () => Navigator.of(ctx).pop(-1),
              ),
              const SizedBox(height: AppTokens.sp8),
            ],
          ),
        );
      },
    );
  }

  /// 用料在家状态（原型 RecipeDetail：家里有 / 家里不多 / 家里没有 + 加入采购）。
  Widget _stockStatus(AppTokens t, DishIngredient ing) {
    final ts = t.textStyles;
    switch (ing.stockLevel) {
      case 'ENOUGH':
        return _dotLabel(t, AppTokens.success, '家里有');
      case 'LOW':
        return _dotLabel(t, AppTokens.warning, '家里不多');
      default:
        return InkWell(
          key: ValueKey('add-shop-${ing.ingredientId}'),
          borderRadius: BorderRadius.circular(AppTokens.rPill),
          onTap: () => _addToShopping(ing),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: t.card,
              border: Border.all(color: t.primarySoft),
              borderRadius: BorderRadius.circular(AppTokens.rPill),
            ),
            child: Text('+ 加入采购', style: ts.sectionLabel.copyWith(color: t.primaryDeep)),
          ),
        );
    }
  }

  Widget _dotLabel(AppTokens t, Color c, String label) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 7, height: 7, decoration: BoxDecoration(color: c, shape: BoxShape.circle)),
          const SizedBox(width: AppTokens.sp6),
          Text(label, style: t.textStyles.caption),
        ],
      );

  /// 缺的用料一键加入采购清单（新建一份含该食材的清单）。
  Future<void> _addToShopping(DishIngredient ing) async {
    try {
      await ShoppingService.generateFromText(ing.displayName);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('已把「${ing.displayName}」加入采购清单')));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('加入采购失败：${errorText(e)}')));
      }
    }
  }

  /// 家里够 X/Y 样（按用料档位：充足/不足算有）。
  Coverage? get _coverage {
    final ings = _detail?.ingredients ?? const [];
    if (ings.isEmpty) return null;
    final enough = ings.where((i) => i.stockLevel == 'ENOUGH' || i.stockLevel == 'LOW').length;
    return Coverage(
      enough: enough,
      total: ings.length,
      missingNames: ings
          .where((i) => i.stockLevel != 'ENOUGH' && i.stockLevel != 'LOW')
          .map((i) => i.displayName)
          .toList(),
    );
  }

  String _difficultyText(int? d) => switch (d) {
        null => '',
        <= 2 => '简单',
        3 => '中等',
        _ => '有挑战',
      };

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    if (_loading || _detail == null) {
      return Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              const BackHeader(),
              Expanded(
                child: _loading
                    ? const SkeletonList(count: 4)
                    : ErrorView(text: '加载详情失败', onRetry: _load),
              ),
            ],
          ),
        ),
      );
    }
    final d = _detail!;
    final dish = d.dish;
    final ts = t.textStyles;
    final cover = (dish.coverUrl ?? '').trim();
    final stepImages = [for (final st in d.steps) ...st.imageList];
    final gallery = [if (cover.isNotEmpty) cover, ...stepImages.take(5)];
    final cov = _coverage;
    final meta = [
      if ((dish.cookTime ?? 0) + (dish.prepTime ?? 0) > 0) '${(dish.cookTime ?? 0) + (dish.prepTime ?? 0)} 分钟',
      if (_difficultyText(dish.difficulty).isNotEmpty) _difficultyText(dish.difficulty),
      if (dish.cookedCount > 0) '做过 ${dish.cookedCount} 次',
    ];
    return Scaffold(
      backgroundColor: t.bg,
      body: Stack(
        children: [
          ListView(
            padding: EdgeInsets.only(bottom: widget.showActions ? 104 : AppTokens.sp32),
            children: [
              HeroGallery(
                urls: gallery,
                name: dish.name,
                height: 262,
                onTapImage: (i) => _openViewer(gallery[i]),
                leading: HeroIconButton(
                  icon: Icons.arrow_back_ios_new_rounded,
                  tooltip: '返回',
                  onTap: () => Navigator.of(context).maybePop(),
                ),
              ),
              Transform.translate(
                offset: const Offset(0, -20),
                child: Container(
                  decoration: BoxDecoration(
                    color: t.bg,
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(AppTokens.rXl)),
                  ),
                  padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp20, AppTokens.sp16, 0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.center,
                        children: [
                          Flexible(child: Text(dish.name, style: ts.h2)),
                          if ((dish.sourceName ?? '').isNotEmpty) ...[
                            const SizedBox(width: AppTokens.sp8),
                            ToneBadge(dish.sourceName!, tone: BadgeTone.info),
                          ],
                        ],
                      ),
                      if (meta.isNotEmpty) ...[
                        const SizedBox(height: AppTokens.sp6),
                        Row(children: [
                          Icon(Icons.schedule_rounded, size: 14, color: t.body),
                          const SizedBox(width: AppTokens.sp4),
                          Expanded(child: Text(meta.join(' · '), style: ts.caption)),
                        ]),
                      ],
                      if (_dishTags.isNotEmpty) ...[
                        const SizedBox(height: AppTokens.sp10),
                        Wrap(
                          spacing: AppTokens.sp6,
                          runSpacing: AppTokens.sp6,
                          children: [for (final tag in _dishTags) ToneBadge(tag, tone: BadgeTone.success)],
                        ),
                      ],
                      if ((dish.note ?? '').isNotEmpty) ...[
                        const SizedBox(height: AppTokens.sp12),
                        Text(dish.note!, maxLines: 4, overflow: TextOverflow.ellipsis, style: ts.body),
                      ],
                      if (d.ingredients.isNotEmpty) ...[
                        const SizedBox(height: AppTokens.sp20),
                        _ingredientCard(t, d.ingredients, cov),
                      ],
                    ],
                  ),
                ),
              ),
              if (d.steps.isNotEmpty) ...[
                Padding(
                  padding: const EdgeInsets.fromLTRB(AppTokens.sp16, 0, AppTokens.sp16, AppTokens.sp12),
                  child: Text('步骤 · ${d.steps.length} 步', style: ts.subtitle),
                ),
                StepCarousel(
                  dishName: dish.name,
                  steps: [
                    for (final st in d.steps)
                      (text: st.text, image: st.imageList.isEmpty ? null : st.imageList.first),
                  ],
                  onTapImage: (i) => _openViewer(d.steps[i].imageList.first),
                ),
              ],
            ],
          ),
          if (widget.showActions)
            Positioned(
              left: 0,
              right: 0,
              bottom: 0,
              child: Container(
                padding: EdgeInsets.fromLTRB(
                    AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp12 + MediaQuery.of(context).padding.bottom),
                decoration: BoxDecoration(
                  color: t.card,
                  border: Border(top: BorderSide(color: t.border)),
                ),
                child: Row(
                  children: [
                    Expanded(
                      flex: 2,
                      child: OutlinedButton.icon(
                        onPressed: _adding ? null : _addToMenu,
                        icon: const Icon(Icons.add_rounded, size: 18),
                        label: Text(_adding ? '加入中…' : '加入食集'),
                      ),
                    ),
                    const SizedBox(width: AppTokens.sp12),
                    Expanded(
                      flex: 3,
                      child: ElevatedButton.icon(
                        key: const Key('dish-cook-now'),
                        onPressed: _cooking ? null : _cookNow,
                        icon: const Icon(Icons.local_fire_department_rounded, size: 18),
                        label: Text(_cooking ? '准备中…' : '现在就做'),
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _ingredientCard(AppTokens t, List<DishIngredient> ings, Coverage? cov) {
    final ts = t.textStyles;
    final badge = ToneBadge.coverage(cov);
    return Container(
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: t.border),
        boxShadow: t.elevationSm,
      ),
      padding: const EdgeInsets.fromLTRB(AppTokens.sp12, AppTokens.sp12, AppTokens.sp12, AppTokens.sp4),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(child: Text('用料', style: ts.lg)),
              if (badge != null) badge,
            ],
          ),
          const SizedBox(height: AppTokens.sp8),
          for (final ing in ings) ...[
            Padding(
              padding: const EdgeInsets.symmetric(vertical: AppTokens.sp8),
              child: Row(
                children: [
                  IngredientIcon(name: ing.displayName, iconKey: ing.iconKey, size: 40),
                  const SizedBox(width: AppTokens.sp12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(ing.displayName, style: ts.cardTitle.copyWith(color: t.title)),
                        if (ing.amountText.isNotEmpty) Text(ing.amountText, style: ts.caption),
                      ],
                    ),
                  ),
                  _stockStatus(t, ing),
                ],
              ),
            ),
            if (ing != ings.last) Divider(height: 1, color: t.border),
          ],
        ],
      ),
    );
  }
}
