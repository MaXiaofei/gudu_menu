import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/image_helper.dart';
import '../../models/menu.dart';
import '../../models/prep.dart';
import '../../services/dish_service.dart';
import '../../services/menu_service.dart';
import 'cook_confirm_sheet.dart';
import '../../services/prep_service.dart';
import '../../services/shopping_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/loading_empty.dart';

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
  static const _tabs = ['菜', '备菜', '聚餐'];

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

  Future<void> _load() async {
    try {
      _detail = await MenuService.detail(widget.id);
    } catch (_) {}
    try {
      _togetherCount = await MenuService.getTogetherCount(widget.id);
    } catch (_) {}
    if (mounted) setState(() {
      _loading = false;
      _dataTick++;
    });
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
    final ready = !_loading && _detail != null;
    final m = _detail?.menu;
    // DESIGN.md §13：去掉「食集详情」AppBar；食集名 + 副信息 + 状态胶囊移入 BackHeader。
    // 加载中/错误时 BackHeader 不传 title（只显箭头）。
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
                      ? const _StatusChip('已完成', AppTokens.success)
                      : const _StatusChip('进行中', AppTokens.warning)),
              subtitle: m == null
                  ? null
                  : Text(
                      '${_relativeDate(m.createdAt)} · 份数 ${m.servingCount ?? 1} · '
                      '关联 ${_detail!.dishes.length} 道菜 · 约 ${_detail!.totalMinutes} 分钟',
                      style: t.textStyles.sm.copyWith(color: t.caption),
                    ),
            ),
            Expanded(
              child: !ready
                  ? (_loading
                      ? const LoadingView()
                      : const EmptyView(text: '加载详情失败'))
                  : Column(
                      children: [
                        _buildTabBar(),
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
                              const _Placeholder(
                                  title: '聚餐', desc: '协同点菜 · 即将上线'),
                            ],
                          ),
                        ),
                      ],
                    ),
            ),
          ],
        ),
      ),
      // 底部操作区仅菜 tab 显示（原型：整集做/去评价属菜品维度，备菜/采购/聚餐不重复出现）
      bottomNavigationBar: (ready && _tabIndex == 0)
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp12),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTokens.success,
                        minimumSize: const Size.fromHeight(48),
                      ),
                      onPressed:
                          (_detail!.menu.isDone || _cooking) ? null : _cookMenu,
                      child: _cooking
                          ? const SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                  strokeWidth: 2, color: Colors.white),
                            )
                          : Text(_detail!.menu.isDone ? '已完成' : '开始做饭'),
                    ),
                    // 完成态：整集做后显「去评价」（选一道菜进评价页）
                    if (_detail!.menu.isDone)
                      Padding(
                        padding: const EdgeInsets.only(top: AppTokens.sp8),
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            minimumSize: const Size.fromHeight(48),
                            side: BorderSide(
                                color: AppTokens.warning, width: 1.5),
                          ),
                          onPressed: () => _goReview(context),
                          child: Text('去评价',
                              style: t.textStyles.md.copyWith(
                                  color: AppTokens.warning,
                                  fontWeight: FontWeight.w600)),
                        ),
                      ),
                  ],
                ),
              ),
            )
          : null,
    );
  }

  /// 完成态「去评价」：直接进统一评价页（食集整体 + 每道菜，V43）。
  Future<void> _goReview(BuildContext context) async {
    await context.push('/menu/${widget.id}/review');
  }

  Widget _buildTabBar() {
    final t = AppTokens.of(context);
    return Container(
        decoration: BoxDecoration(
          color: t.card,
          border: Border(bottom: BorderSide(color: t.border)),
        ),
        child: Row(
          children: [
            for (int i = 0; i < _tabs.length; i++)
              _tabItem(i, _tabs[i], _tabCount(i)),
          ],
        ),
      );
  }

  /// 各 tab 汇总数量（原型「菜 · 3」；备菜/采购由子 tab 加载后上报，聚餐占位 0）。
  int _tabCount(int idx) {
    switch (idx) {
      case 0:
        return _detail?.dishes.length ?? 0;
      case 1:
        return _prepCount;
      case 2:
        return _togetherCount;
      default:
        return 0;
    }
  }

  Widget _tabItem(int idx, String label, int count) {
    final t = AppTokens.of(context);
    final selected = _tabIndex == idx;
    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _tabIndex = idx),
        behavior: HitTestBehavior.opaque,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: AppTokens.sp12),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: selected ? t.primary : Colors.transparent,
                width: 2,
              ),
            ),
          ),
          child: Text(
            '$label · $count',
            textAlign: TextAlign.center,
            style: t.textStyles.body.copyWith(
              fontWeight: selected ? FontWeight.bold : FontWeight.normal,
              color: selected ? t.primary : t.body,
            ),
          ),
        ),
      ),
    );
  }
}

/// Tab 0：菜品列表（原 _buildBody 内容）。
class _DishesTab extends StatelessWidget {
  final MenuDetail detail;
  /// 备注/删菜修改成功后刷新详情。
  final VoidCallback onNoteChanged;
  /// 已完成（整集做后）：隐藏加菜入口，行内按钮全部禁用。
  final bool isDone;
  const _DishesTab({
    required this.detail,
    required this.onNoteChanged,
    required this.isDone,
  });

  /// 加菜：跳菜谱选择页（/dish-picker?menuId=），返回后刷新。
  Future<void> _addDish(BuildContext context) async {
    await context.push('/dish-picker?menuId=${detail.menu.id}');
    onNoteChanged();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    // 食集名 + 状态胶囊 + 份数副信息已移入 BackHeader（§13.2）。
    return ListView(
      children: [
        const _SectionTitle('包含菜品'),
        ...detail.dishes.map((d) => _DishRow(
              menuId: detail.menu.id,
              dishId: d.dishId,
              dishName: d.dishName,
              servingFactor: d.servingFactor,
              coverUrl: d.coverUrl,
              note: d.note,
              readOnly: isDone,
              onNoteChanged: onNoteChanged,
            )),
        // 加菜入口（原型「+ 加菜（去菜谱找）」虚线框）；已完成不显示
        if (!isDone)
          GestureDetector(
            onTap: () => _addDish(context),
            child: Container(
              margin: const EdgeInsets.fromLTRB(
                  AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, 0),
              padding: const EdgeInsets.all(11),
              decoration: BoxDecoration(
                border: Border.all(color: t.primary, width: 1.5),
                borderRadius: BorderRadius.circular(AppTokens.rMd),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text('+', style: t.textStyles.md.copyWith(color: t.primary)),
                  const SizedBox(width: AppTokens.sp4),
                  Text('加菜（去菜谱找）',
                      style: t.textStyles.md.copyWith(color: t.primary)),
                ],
              ),
            ),
          ),
        const SizedBox(height: AppTokens.sp24),
      ],
    );
  }
}

/// Tab 1：备菜（Plan C）。加载 GET /menu/{id}/prep + 状态交互（点/长按）。
class _PrepTab extends StatefulWidget {
  final int menuId;
  /// 父级数据版本号：变化时（加菜/删菜等）重载，解决 IndexedStack 保留状态不刷新。
  final int refreshTick;
  /// 备料总数（totalCount）加载后上报父级，用于 tab 汇总数量。
  final ValueChanged<int> onCountChanged;
  /// 已完成食集：状态 chip 不可点（只读展示）。
  final bool isDone;
  const _PrepTab({
    required this.menuId,
    required this.refreshTick,
    required this.onCountChanged,
    required this.isDone,
  });
  @override
  State<_PrepTab> createState() => _PrepTabState();
}

class _PrepTabState extends State<_PrepTab> {
  MenuPrep? _prep;
  bool _loading = true;
  bool _condimentExpanded = false;
  int _lastTick = 0;

  @override
  void initState() {
    super.initState();
    _lastTick = widget.refreshTick;
    _load();
  }

  @override
  void didUpdateWidget(covariant _PrepTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.refreshTick != _lastTick) {
      _lastTick = widget.refreshTick;
      _load();
    }
  }

  Future<void> _load() async {
    try {
      _prep = await PrepService.getPrep(widget.menuId);
    } catch (_) {}
    if (mounted) {
      widget.onCountChanged(_prep?.totalCount ?? 0);
      setState(() => _loading = false);
    }
  }

  /// 点 chip：PENDING ↔ READY。
  Future<void> _toggle(PrepItem item) async {
    final next = item.status == PrepStatus.ready
        ? PrepStatus.pending
        : PrepStatus.ready;
    await _update(item, next);
  }

  /// 长按：弹「化冻 / 腌制 / 重置」三选一。
  Future<void> _longPress(PrepItem item) async {
    final t = AppTokens.of(context);
    final next = await showModalBottomSheet<PrepStatus>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.ac_unit, color: AppTokens.info),
              title: const Text('化冻中'),
              onTap: () => Navigator.pop(ctx, PrepStatus.thawing),
            ),
            ListTile(
              leading: const Icon(Icons.schedule, color: AppTokens.warning),
              title: const Text('腌制中'),
              onTap: () => Navigator.pop(ctx, PrepStatus.marinating),
            ),
            ListTile(
              leading: Icon(Icons.refresh, color: t.caption),
              title: const Text('重置为待备'),
              onTap: () => Navigator.pop(ctx, PrepStatus.pending),
            ),
          ],
        ),
      ),
    );
    if (next == null || next == item.status) return;
    await _update(item, next);
  }

  /// 乐观更新 + 失败回滚。
  Future<void> _update(PrepItem item, PrepStatus next) async {
    final prev = _prep;
    setState(() => _prep = _rebuiltWith(item.ingredientId, next));
    try {
      await PrepService.updateStatus(widget.menuId, item.ingredientId, next);
    } catch (_) {
      if (mounted) {
        setState(() => _prep = prev);
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('更新失败')));
      }
    }
  }

  /// 一键加采购：弹勾选备菜项（默认勾家里用完/不足）→ 送进该食集采购清单。
  Future<void> _showAddShopping() async {
    final p = _prep;
    if (p == null) return;
    final items = [...p.items, ...p.condiments];
    if (items.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('没有可加入的备菜')));
      return;
    }
    final selected = await showModalBottomSheet<Set<int>>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PrepShoppingSheet(items: items),
    );
    if (selected == null || selected.isEmpty) return;
    try {
      await ShoppingService.fromPrep(widget.menuId, selected.toList());
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('已加入采购清单')));
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('加入采购清单失败')));
      }
    }
  }

  /// 重建 _prep（改某食材 status + 重算 readyCount；调料改 status 同样计入进度）。
  MenuPrep _rebuiltWith(int ingredientId, PrepStatus next) {
    final p = _prep;
    if (p == null) {
      return const MenuPrep(
          items: [], condiments: [], readyCount: 0, totalCount: 0);
    }
    final items = p.items
        .map((it) =>
            it.ingredientId == ingredientId ? it.copyWithStatus(next) : it)
        .toList();
    final condiments = p.condiments
        .map((it) =>
            it.ingredientId == ingredientId ? it.copyWithStatus(next) : it)
        .toList();
    final readyCount = [...items, ...condiments]
        .where((it) => it.status == PrepStatus.ready)
        .length;
    return MenuPrep(
      items: items,
      condiments: condiments,
      readyCount: readyCount,
      totalCount: p.totalCount,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const LoadingView();
    final p = _prep;
    if (p == null) return const EmptyView(text: '加载备菜失败');
    return ListView(
      children: [
        _buildProgress(p),
        const _SectionTitle('备料清单'),
        ...p.items.map((it) => _PrepItemRow(
              item: it,
              // 已完成：chip 不可点
              onTap: widget.isDone ? null : () => _toggle(it),
              onLongPress: widget.isDone ? null : () => _longPress(it),
            )),
        // 调料折叠组（与菜分组；计入总数/进度，文案不再写「无需备料」）
        if (p.condiments.isNotEmpty) ...[
          const SizedBox(height: AppTokens.sp8),
          _buildCondimentHeader(p.condiments.length),
          if (_condimentExpanded)
            ...p.condiments.map((it) => _PrepItemRow(
                  item: it,
                  isCondiment: true,
                  // 已完成：chip 不可点
                  onTap: widget.isDone ? null : () => _toggle(it),
                  onLongPress: widget.isDone ? null : () => _longPress(it),
                )),
        ],
        const SizedBox(height: AppTokens.sp24),
      ],
    );
  }

  Widget _buildProgress(MenuPrep p) {
    final t = AppTokens.of(context);
    final ratio = p.totalCount == 0 ? 0.0 : p.readyCount / p.totalCount;
    return Padding(
      padding: const EdgeInsets.all(AppTokens.sp16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text('备料进度',
                  style: t.textStyles.cardTitle.copyWith(color: t.title)),
              const Spacer(),
              Text('已备 ${p.readyCount} / 共 ${p.totalCount} 样',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            ],
          ),
          const SizedBox(height: AppTokens.sp8),
          // 一键加采购：把要买的送进采购清单（完成态隐藏，采购走库存页「去采购」）
          if (!widget.isDone)
            Align(
              alignment: Alignment.centerRight,
              child: GestureDetector(
                onTap: _showAddShopping,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 5),
                  decoration: BoxDecoration(
                    color: t.primary,
                    borderRadius: BorderRadius.circular(AppTokens.rPill),
                  ),
                  child: Text('一键加采购',
                      style: t.textStyles.tiny.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
                ),
              ),
            ),
          const SizedBox(height: AppTokens.sp8),
          ClipRRect(
            borderRadius: BorderRadius.circular(AppTokens.rXs),
            child: LinearProgressIndicator(
              value: ratio,
              minHeight: 8,
              backgroundColor: t.border,
              color: AppTokens.success,
            ),
          ),
        ],
      ),
    );
  }

  /// 调料折叠头（与菜分组；调料计入总数/进度，故文案只说「调料 N 样」）。
  Widget _buildCondimentHeader(int count) {
    final t = AppTokens.of(context);
    return Material(
      color: t.secondary,
      child: InkWell(
        onTap: () => setState(() => _condimentExpanded = !_condimentExpanded),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
          child: Row(
            children: [
              Text('调料 $count 样',
                  style: t.textStyles.sm),
              const Spacer(),
              Icon(
                  _condimentExpanded ? Icons.expand_less : Icons.expand_more,
                  color: t.caption),
            ],
          ),
        ),
      ),
    );
  }
}

/// 备菜列表行：食材名 + 用量 + 共用高亮 + 来自哪些菜 + 行尾状态 chip。
/// 原型（menu-detail-beicai.html）：chip 在行尾；已备整行变淡、名称删除线、
/// 实绿底 chip「✓ 已备」；待备白底描边 chip。
class _PrepItemRow extends StatelessWidget {
  final PrepItem item;
  /// 可空：已完成食集传 null 禁用（chip 不可点）。
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final bool isCondiment;
  const _PrepItemRow({
    required this.item,
    required this.onTap,
    required this.onLongPress,
    this.isCondiment = false,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final s = item.status;
    final isReady = s == PrepStatus.ready;
    final chipColor = switch (s) {
      PrepStatus.ready => AppTokens.success,
      PrepStatus.thawing => AppTokens.info,
      PrepStatus.marinating => AppTokens.warning,
      PrepStatus.pending => t.caption,
    };
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        hoverColor: t.primary.withValues(alpha: 0.08),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
          decoration: BoxDecoration(
            border: Border(top: BorderSide(color: t.border)),
            // 已备整行变色（浅绿底）；共用项淡橙底（聚焦"一次备够"），已备优先
            color: isReady
                ? AppTokens.success.withValues(alpha: 0.06)
                : (item.shared ? t.highlight : null),
          ),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(item.ingredientName,
                              style: t.textStyles.md.copyWith(
                                  fontWeight: FontWeight.w500,
                                  // 已备：名称变灰 + 删除线（原型 line-through）
                                  color: isReady ? t.caption : t.title,
                                  decoration: isReady
                                      ? TextDecoration.lineThrough
                                      : null)),
                        ),
                      ],
                    ),
                    if (item.dishNames.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          item.dishNames.length >= 2
                              ? '${item.dishCount} 道菜共用 · ${item.dishNames.join("、")}'
                              : item.dishNames.first,
                          style: t.textStyles.sm.copyWith(color: t.caption),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                ),
              ),
              // 家里：充足/不足/用完 徽标（V42，备料时一眼知道要不要先买）
              if (item.stockLevel != null && item.stockLevel!.isNotEmpty)
                Container(
                  margin: const EdgeInsets.only(right: AppTokens.sp6),
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp6, vertical: 2),
                  decoration: BoxDecoration(
                    color: _prepStockColor(item.stockLevel!).withAlpha(20),
                    borderRadius: BorderRadius.circular(AppTokens.rPill),
                  ),
                  child: Text('家里：${_prepStockLabel(item.stockLevel!)}',
                      style: t.textStyles.chip.copyWith(
                          color: _prepStockColor(item.stockLevel!), fontWeight: FontWeight.w700)),
                ),
              Text('${item.totalGrams.toStringAsFixed(0)}g',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
              const SizedBox(width: AppTokens.sp8),
              // 状态 chip 在行尾（原型：待备白底描边；已备/化冻中/腌制中实底白字）
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
                decoration: BoxDecoration(
                  color: s == PrepStatus.pending
                      ? t.card
                      : chipColor,
                  border: Border.all(
                      color: s == PrepStatus.pending
                          ? t.border
                          : chipColor),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Text(
                    // 原型「✓ 已备」；✓ 为几何符号非 emoji，可入 UI
                    s == PrepStatus.ready ? '✓ ${s.label}' : s.label,
                    style: t.textStyles.sm.copyWith(
                        color: s == PrepStatus.pending ? chipColor : t.card,
                        fontWeight: FontWeight.w600)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 占位 Tab（采购 / 聚餐）。
class _Placeholder extends StatelessWidget {
  final String title;
  final String desc;
  const _Placeholder({required this.title, required this.desc});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Center(
        child: Padding(
          padding: const EdgeInsets.all(AppTokens.sp32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(title, style: t.textStyles.subtitle),
              const SizedBox(height: AppTokens.sp8),
              Text(desc,
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            ],
          ),
        ),
      );
  }
}

/// 菜品行（原型 menu-detail-cai.html）：38px 缩略图 + 菜名/份数 + 备注行。
/// 整行可点进菜谱详情（复用 /dish/:id，extra 隐藏底部操作按钮）；
/// 备注可点弹输入框修改，清空提交 = 删除备注（回显「加备注/忌口…」）。
class _DishRow extends StatelessWidget {
  final int menuId;
  final int dishId;
  final String? dishName;
  final double? servingFactor;
  final String? coverUrl;
  final String? note;
  /// 已完成食集：行点击/备注/移出全部禁用（只读展示）。
  final bool readOnly;
  /// 备注修改成功后的回调（上层刷新详情）。
  final VoidCallback onNoteChanged;
  const _DishRow({
    required this.menuId,
    required this.dishId,
    this.dishName,
    this.servingFactor,
    this.coverUrl,
    this.note,
    this.readOnly = false,
    required this.onNoteChanged,
  });

  /// 弹备注输入框（预填当前值）；确定 → 调接口 → 回调刷新。
  Future<void> _editNote(BuildContext context) async {
    final ctrl = TextEditingController(text: note ?? '');
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('菜备注'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          maxLength: 255,
          decoration: const InputDecoration(
            hintText: '如：宝宝那份少盐',
            isDense: true,
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text),
              child: const Text('确定')),
        ],
      ),
    );
    if (result == null) return;
    final trimmed = result.trim();
    try {
      await MenuService.updateDishNote(menuId, dishId, trimmed);
      if (context.mounted) onNoteChanged();
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('备注更新失败')));
      }
    }
  }

  /// 从食集移除这道菜：确认弹窗 → 调接口 → 回调刷新（原型菜行行尾 ✕）。
  Future<void> _removeDish(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('移出食集'),
        content: Text('确认将「${dishName ?? '菜 #$dishId'}」移出食集？'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('移出')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await MenuService.removeDishFromMenu(menuId, dishId);
      if (context.mounted) onNoteChanged();
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('移出失败')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final name =
        (dishName == null || dishName!.isEmpty) ? '菜 #$dishId' : dishName!;
    final hasNote = note != null && note!.isNotEmpty;
    final hasCover = coverUrl != null && coverUrl!.isNotEmpty;
    final thumbUrl = hasCover
        ? ImageHelper.toThumbnail(ImageHelper.toAbsolute(coverUrl!))
        : null;

    return InkWell(
      onTap: readOnly
          ? null
          : () => context.push('/dish/$dishId', extra: {'showActions': false}),
      child: Container(
        padding: const EdgeInsets.fromLTRB(
            AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp8),
        decoration: BoxDecoration(
            border: Border(top: BorderSide(color: t.border))),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                // 38px 缩略图，10px 圆角（原型 width:38px;border-radius:10px）
                ClipRRect(
                  borderRadius: BorderRadius.circular(10),
                  child: SizedBox(
                    width: 38,
                    height: 38,
                    child: thumbUrl != null
                        ? Image.network(
                            thumbUrl,
                            fit: BoxFit.cover,
                            errorBuilder: (_, __, ___) =>
                                _thumbPlaceholder(t, name),
                            loadingBuilder: (_, child, progress) =>
                                progress == null ? child : _thumbPlaceholder(t, name),
                          )
                        : _thumbPlaceholder(t, name),
                  ),
                ),
                const SizedBox(width: AppTokens.sp8),
                Expanded(
                  child: Text(name,
                      style: t.textStyles.md.copyWith(color: t.title),
                      overflow: TextOverflow.ellipsis),
                ),
                Text(
                  '× ${servingFactor?.toStringAsFixed(1) ?? '1.0'} 份',
                  style: t.textStyles.sm.copyWith(color: t.caption),
                ),
                const SizedBox(width: AppTokens.sp4),
                Icon(Icons.chevron_right, size: 18, color: t.caption),
              ],
            ),
            // 备注行（原型 dashed 分隔）：有备注=浅橙胶囊，无备注=灰字占位；点击弹输入框
            GestureDetector(
              onTap: readOnly ? null : () => _editNote(context),
              behavior: HitTestBehavior.opaque,
              child: Container(
                width: double.infinity,
                margin: const EdgeInsets.only(top: 8),
                padding: const EdgeInsets.only(top: 8),
                decoration: BoxDecoration(
                  border: Border(
                    top: BorderSide(color: t.border.withValues(alpha: 0.8)),
                  ),
                ),
                child: Row(
                  children: [
                    Text('备注', style: t.textStyles.tiny),
                    const SizedBox(width: AppTokens.sp8),
                    Expanded(
                      child: hasNote
                          ? Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: AppTokens.sp8, vertical: 2),
                              decoration: BoxDecoration(
                                color: t.secondary,
                                borderRadius:
                                    BorderRadius.circular(AppTokens.rSm),
                              ),
                              child: Text(
                                note!,
                                style: t.textStyles.tiny
                                    .copyWith(color: t.title),
                                overflow: TextOverflow.ellipsis,
                              ),
                            )
                          : Text(
                              '加备注/忌口…',
                              style: t.textStyles.tiny
                                  .copyWith(color: t.caption),
                              overflow: TextOverflow.ellipsis,
                            ),
                    ),
                    const SizedBox(width: AppTokens.sp8),
                    Icon(Icons.edit_outlined, size: 14, color: t.caption),
                    const SizedBox(width: AppTokens.sp8),
                    // 移出食集（原型菜行行尾 ✕）；已完成禁用
                    GestureDetector(
                      onTap: readOnly ? null : () => _removeDish(context),
                      behavior: HitTestBehavior.opaque,
                      child: Padding(
                        padding: const EdgeInsets.all(4),
                        child: Text('✕',
                            style: t.textStyles.md
                                .copyWith(color: t.caption)),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 无封面图时的缩略占位：奶油底 + 菜名首字（DESIGN.md §10.4，不用 emoji 顶替）。
  Widget _thumbPlaceholder(AppTokens t, String name) {
    final initial = name.trim().isNotEmpty ? name.trim().characters.first : '菜';
    return Container(
      color: t.secondary,
      alignment: Alignment.center,
      child: Text(
        initial,
        style: t.textStyles.sm.copyWith(
          color: t.title.withAlpha(115), // ≈ 0.45 透明度
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Padding(
        padding: const EdgeInsets.all(AppTokens.sp16),
        child: Text(text, style: t.textStyles.pageTitle),
      );
  }
}

class _StatusChip extends StatelessWidget {
  final String text;
  final Color color;
  const _StatusChip(this.text, this.color);
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
      decoration: BoxDecoration(
        color: color.withAlpha(20),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: Text(text,
          style: t.textStyles.sm.copyWith(
              color: color, fontWeight: FontWeight.w600)),
    );
  }
}

/// 相对日期：今天 / 昨天 / N 天前 / M/D（对齐食集列表页，不引第三方库）。
String _relativeDate(DateTime? dt) {
  if (dt == null) return '';
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final that = DateTime(dt.year, dt.month, dt.day);
  final diff = today.difference(that).inDays;
  if (diff <= 0) return '今天';
  if (diff == 1) return '昨天';
  if (diff < 7) return '$diff 天前';
  return '${dt.month}/${dt.day}';
}

/// 备菜行库存徽标颜色。
Color _prepStockColor(String level) {
  return switch (level) {
    'ENOUGH' => AppTokens.success,
    'LOW' => AppTokens.warning,
    _ => AppTokens.error,
  };
}

/// 备菜行库存徽标文案。
String _prepStockLabel(String level) {
  return switch (level) {
    'ENOUGH' => '充足',
    'LOW' => '不足',
    _ => '用完',
  };
}

/// 一键加采购弹窗（V42，对齐 menu-detail-beicai.html）：
/// 备菜项列表，默认勾选家里 用完/不足 的项，可改；确认 → 加入采购清单。
class _PrepShoppingSheet extends StatefulWidget {
  final List<PrepItem> items;
  const _PrepShoppingSheet({required this.items});

  @override
  State<_PrepShoppingSheet> createState() => _PrepShoppingSheetState();
}

class _PrepShoppingSheetState extends State<_PrepShoppingSheet> {
  late final Set<int> _selected;

  @override
  void initState() {
    super.initState();
    _selected = {
      for (final it in widget.items)
        if (it.stockLevel == 'NONE' || it.stockLevel == 'LOW') it.ingredientId,
    };
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 10),
          Container(width: 36, height: 4, decoration: BoxDecoration(
            color: t.border, borderRadius: BorderRadius.circular(2))),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 12, 18, 4),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('加入采购清单', style: t.textStyles.subtitle),
              const SizedBox(height: 2),
              Text('默认勾选家里没有/不足的，可改',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            ]),
          ),
          Flexible(
            child: ListView(
              shrinkWrap: true,
              children: [
                for (final it in widget.items)
                  InkWell(
                    onTap: () => setState(() {
                      if (!_selected.remove(it.ingredientId)) {
                        _selected.add(it.ingredientId);
                      }
                    }),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
                      child: Row(children: [
                        Container(
                          width: 18, height: 18,
                          decoration: BoxDecoration(
                            color: _selected.contains(it.ingredientId) ? _prepStockColor(it.stockLevel ?? 'NONE') : null,
                            border: Border.all(
                                color: _selected.contains(it.ingredientId)
                                    ? _prepStockColor(it.stockLevel ?? 'NONE')
                                    : t.border),
                            borderRadius: BorderRadius.circular(AppTokens.rXs),
                          ),
                          child: _selected.contains(it.ingredientId)
                              ? const Icon(Icons.check, size: 13, color: Colors.white)
                              : null,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(it.ingredientName,
                              style: t.textStyles.md.copyWith(color: t.title)),
                        ),
                        Text('${it.totalGrams.toStringAsFixed(0)}g',
                            style: t.textStyles.sm.copyWith(color: t.caption)),
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                          decoration: BoxDecoration(
                            color: _prepStockColor(it.stockLevel ?? 'NONE'),
                            borderRadius: BorderRadius.circular(AppTokens.rPill),
                          ),
                          child: Text('家里：${_prepStockLabel(it.stockLevel ?? 'NONE')}',
                              style: t.textStyles.chip.copyWith(color: Colors.white)),
                        ),
                      ]),
                    ),
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 6, 18, 12),
            child: Row(children: [
              Expanded(
                child: OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(color: t.border),
                    foregroundColor: t.caption,
                    minimumSize: const Size.fromHeight(46),
                  ),
                  onPressed: () => Navigator.pop(context),
                  child: const Text('取消'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                flex: 2,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: t.primary,
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(46),
                  ),
                  onPressed: () => Navigator.pop(context, Set<int>.of(_selected)),
                  child: Text('加入 · ${_selected.length} 项'),
                ),
              ),
            ]),
          ),
        ],
      ),
    );
  }
}
