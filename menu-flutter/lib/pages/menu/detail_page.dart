import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_plus/share_plus.dart';

import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../models/menu.dart';
import '../../models/prep.dart';
import '../../services/dish_service.dart';
import '../../services/menu_service.dart';
import 'cook_confirm_sheet.dart';
import '../../services/prep_service.dart';
import '../../services/shopping_service.dart';
import '../../services/together_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/error_view.dart';
import '../../widgets/illustration.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/section_title.dart';
import '../../widgets/segment_tabs.dart';
import '../../widgets/skeleton.dart';
import '../../widgets/stat_grid.dart';
import '../../widgets/tone_badge.dart';

part 'detail/dishes_tab.dart';
part 'detail/prep_tab.dart';
part 'detail/prep_shopping_sheet.dart';
part 'detail/shop_tab.dart';
part 'detail/together_tab.dart';

/// 食集详情（对应 menu-mini/src/pages/menu/Detail.vue）。
///
/// 四 Tab：菜（菜品列表）/ 备菜（Plan C）/ 采购（Plan E）/ 聚餐（占位）。
/// 用 IndexedStack 持有各 Tab state（备菜 Tab 切走再切回不丢进度/状态）。
///
/// 底部「整集做菜」（Plan A）：POST /menu/{id}/cook → 聚合用量→扣 pantry →
/// 每菜写 cooking_record → 食集标 DONE；欠量时提示缺几项。
///
/// 菜名：后端 /menu/{id} 的 dishes 冗余带 dishName/coverUrl，直接渲染，
/// 无需再逐菜 GET /dish/{id}（评审 N+1 gap 已修）。
class MenuDetailPage extends StatefulWidget {
  final int id;
  const MenuDetailPage({super.key, required this.id});
  @override
  State<MenuDetailPage> createState() => _MenuDetailPageState();
}

class _MenuDetailPageState extends State<MenuDetailPage> {
  static const _tabs = ['菜', '备菜', '采购', '一起吃'];

  MenuDetail? _detail;
  bool _loading = true;
  int _tabIndex = 0;

  /// 备菜 tab 汇总数量（_PrepTab 加载后回调上报）。
  int _prepCount = 0;
  /// 聚餐 tab 汇总数量（占位接口，协同点菜待建）。
  int _togetherCount = 0;

  /// 数据版本号：每次 _load 成功 +1，作为 refreshTick 传给备菜/采购 tab。
  /// IndexedStack 不重建子树，子 tab 靠 didUpdateWidget 比较版本号触发重载
  ///（加菜/删菜/改备注后 _load 会刷新全部 tab，而非只刷新当前可见 tab）。
  int _dataTick = 0;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Object? _error;

  Future<void> _load() async {
    try {
      _detail = await MenuService.detail(widget.id);
      _error = null;
    } catch (e) {
      _error = e; // 首屏失败 → ErrorView（带报错编号 + 重试）
    }
    try {
      _togetherCount = await MenuService.getTogetherCount(widget.id);
    } catch (_) {}
    if (mounted) {
      setState(() {
        _loading = false;
        _dataTick++;
      });
    }
  }

  /// 开始做饭（V42）：先弹「这顿饭用了什么」确认弹窗（三态），确认后更新档位 + 写食记 + 食集完成。
  bool _cooking = false;
  Future<void> _cookMenu() async {
    if (_cooking) return;
    setState(() => _cooking = true);
    try {
      final materials = await DishService.cookMaterials(widget.id);
      if (!mounted) return;
      final result = await showModalBottomSheet<CookConfirmResult>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (_) => CookConfirmSheet(materials: materials),
      );
      if (!mounted) return;
      if (result == null) {
        // 关弹窗 = 取消，不完成
        setState(() => _cooking = false);
        return;
      }
      await MenuService.cookMenu(widget.id,
          usedUp: result.usedUp, partiallyUsed: result.partiallyUsed);
      if (!mounted) return;
      await showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.transparent,
        builder: (_) => CookResultSheet(
          menuId: widget.id,
          items: materials.items,
          usedUp: result.usedUp,
          partiallyUsed: result.partiallyUsed,
        ),
      );
      if (!mounted) return;
      await _load();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('做菜失败')));
      }
    }
    if (mounted) setState(() => _cooking = false);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final ready = !_loading && _detail != null;
    final m = _detail?.menu;
    final dishes = _detail?.dishes ?? const <MenuDish>[];
    final eaters = <String>{
      for (final d in dishes)
        if ((d.addedByNickname ?? '').isNotEmpty) d.addedByNickname!,
    }.toList();
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            BackHeader(
              title: m?.name,
              action: m == null
                  ? null
                  : (m.isDone
                      ? const ToneBadge('已完成', tone: BadgeTone.success)
                      : const ToneBadge('进行中', tone: BadgeTone.warning)),
              subtitle: m == null
                  ? null
                  : Row(
                      children: [
                        Flexible(
                          child: Text(
                            '${_relativeDate(m.createdAt)} · ${m.servingCount ?? 1} 人 · '
                            '${dishes.length} 道菜 · 约 ${_detail!.totalMinutes} 分钟',
                            style: ts.caption,
                          ),
                        ),
                        if (eaters.isNotEmpty) ...[
                          const SizedBox(width: AppTokens.sp8),
                          AvatarStack(size: 22, people: [for (final n in eaters) (name: n, avatarUrl: null)]),
                        ],
                      ],
                    ),
            ),
            Expanded(
              child: !ready
                  ? (_loading
                      ? const SkeletonList(count: 4, thumb: 76)
                      : ErrorView(text: '加载详情失败', onRetry: _load, error: _error))
                  : Column(
                      children: [
                        Padding(
                          padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp8),
                          child: SegmentTabs(
                            labels: [
                              for (var i = 0; i < _tabs.length; i++)
                                _tabCount(i) > 0 ? '${_tabs[i]} ${_tabCount(i)}' : _tabs[i],
                            ],
                            index: _tabIndex,
                            onChanged: (i) => setState(() => _tabIndex = i),
                          ),
                        ),
                        Expanded(
                          child: IndexedStack(
                            index: _tabIndex,
                            children: [
                              _DishesTab(
                                  detail: _detail!,
                                  isDone: _detail!.menu.isDone,
                                  onNoteChanged: _load),
                              _PrepTab(
                                  menuId: widget.id,
                                  refreshTick: _dataTick,
                                  isDone: _detail!.menu.isDone,
                                  onCountChanged: (c) =>
                                      setState(() => _prepCount = c)),
                              _ShopTab(menuId: widget.id, refreshTick: _dataTick),
                              _TogetherTab(
                                  menuId: widget.id,
                                  refreshTick: _dataTick,
                                  isDone: _detail!.menu.isDone,
                                  onCountChanged: (c) =>
                                      setState(() => _togetherCount = c)),
                            ],
                          ),
                        ),
                      ],
                    ),
            ),
          ],
        ),
      ),
      // 底部操作区仅「菜」tab：进度 + 全部做完（整集做菜）/ 去评价
      bottomNavigationBar: (ready && _tabIndex == 0) ? _bottomBar(t) : null,
    );
  }

  Widget _bottomBar(AppTokens t) {
    final ts = t.textStyles;
    final done = _detail!.menu.isDone;
    final n = _detail!.dishes.length;
    return Container(
      decoration: BoxDecoration(color: t.card, border: Border(top: BorderSide(color: t.border))),
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp10, AppTokens.sp16, AppTokens.sp10),
          child: Row(
            children: [
              SizedBox(
                width: 44,
                height: 44,
                child: Stack(alignment: Alignment.center, children: [
                  CircularProgressIndicator(
                    value: done ? 1 : 0,
                    strokeWidth: 4,
                    backgroundColor: t.secondary,
                    color: AppTokens.success,
                  ),
                  Text(done ? '$n/$n' : '0/$n', style: ts.chip.copyWith(color: t.title)),
                ]),
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Text(done ? '这顿已经做完啦' : '做完后一次回写余量',
                    style: ts.caption, maxLines: 2),
              ),
              const SizedBox(width: AppTokens.sp12),
              if (done)
                SizedBox(
                  width: 120,
                  child: OutlinedButton(onPressed: () => _goReview(context), child: const Text('去评价')),
                )
              else
                SizedBox(
                  width: 140,
                  child: ElevatedButton(
                    onPressed: (_cooking || n == 0) ? null : _cookMenu,
                    child: Text(_cooking ? '准备中…' : '开始做饭'),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }

  /// 完成态「去评价」：直接进统一评价页（食集整体 + 每道菜，V43）。
  Future<void> _goReview(BuildContext context) async {
    await context.push('/menu/${widget.id}/review');
  }

  /// 各 tab 汇总数量（原型「菜 · 3」；备菜/采购由子 tab 加载后上报，聚餐占位 0）。
  int _tabCount(int idx) {
    switch (idx) {
      case 0:
        return _detail?.dishes.length ?? 0;
      case 1:
        return _prepCount;
      case 3:
        return _togetherCount;
      default:
        return 0;
    }
  }
}
