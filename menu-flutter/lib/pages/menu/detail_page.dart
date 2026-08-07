import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/image_helper.dart';
import '../../models/menu.dart';
import '../../models/prep.dart';
import '../../services/menu_service.dart';
import '../../services/prep_service.dart';
import '../../services/shopping_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/loading_empty.dart';

/// 食集详情（对应 menu-mini/src/pages/menu/Detail.vue）。
///
/// 四 Tab：菜（菜品列表）/ 备菜（Plan C）/ 采购（Plan E）/ 一起吃（占位）。
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
  /// 采购 tab 汇总数量（_ShoppingTab 加载后回调上报）。
  int _shoppingCount = 0;
  /// 一起吃 tab 汇总数量（占位接口，协同点菜待建）。
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

  /// 整集做菜：POST /menu/{id}/cook。
  bool _cooking = false;
  Future<void> _cookMenu() async {
    if (_cooking) return;
    setState(() => _cooking = true);
    try {
      final result = await MenuService.cookMenu(widget.id);
      if (!mounted) return;
      final msg = result.hasShortage
          ? '已做菜，库存已扣；缺量：${result.shortages.length} 项'
          : '已做菜，库存已扣';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
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
                              _DishesTab(detail: _detail!, onNoteChanged: _load),
                              _PrepTab(
                                  menuId: widget.id,
                                  refreshTick: _dataTick,
                                  onCountChanged: (c) =>
                                      setState(() => _prepCount = c)),
                              _ShoppingTab(
                                  menuId: widget.id,
                                  refreshTick: _dataTick,
                                  onCountChanged: (c) =>
                                      setState(() => _shoppingCount = c)),
                              const _Placeholder(
                                  title: '一起吃', desc: '协同点菜 · 即将上线'),
                            ],
                          ),
                        ),
                      ],
                    ),
            ),
          ],
        ),
      ),
      bottomNavigationBar: ready
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp12),
                child: ElevatedButton(
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
                      : Text(_detail!.menu.isDone ? '已完成' : '整集做菜'),
                ),
              ),
            )
          : null,
    );
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

  /// 各 tab 汇总数量（原型「菜 · 3」；备菜/采购由子 tab 加载后上报，一起吃占位 0）。
  int _tabCount(int idx) {
    switch (idx) {
      case 0:
        return _detail?.dishes.length ?? 0;
      case 1:
        return _prepCount;
      case 2:
        return _shoppingCount;
      case 3:
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
  const _DishesTab({required this.detail, required this.onNoteChanged});

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
              onNoteChanged: onNoteChanged,
            )),
        // 加菜入口（原型「+ 加菜（去菜谱找）」虚线框）
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
  const _PrepTab({
    required this.menuId,
    required this.refreshTick,
    required this.onCountChanged,
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

  /// 重建 _prep（改某食材 status + 重算 readyCount；调料改 status 不影响进度）。
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
    final readyCount =
        items.where((it) => it.status == PrepStatus.ready).length;
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
              onTap: () => _toggle(it),
              onLongPress: () => _longPress(it),
            )),
        if (p.condiments.isNotEmpty) ...[
          const SizedBox(height: AppTokens.sp8),
          _buildCondimentHeader(p.condiments.length),
          if (_condimentExpanded)
            ...p.condiments.map((it) => _PrepItemRow(
                  item: it,
                  isCondiment: true,
                  onTap: () => _toggle(it),
                  onLongPress: () => _longPress(it),
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
              Text('调料 $count 样 · 无需备料',
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
  final VoidCallback onTap;
  final VoidCallback onLongPress;
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

/// Tab 2：采购清单（Plan E）。GET /shopping/by-menu/{menuId}（未生成→显"生成"按钮）；
/// 勾选已购走 PUT /shopping/item/{id}/purchased（0→1 后端回写 pantry，Plan D）。
class _ShoppingTab extends StatefulWidget {
  final int menuId;
  /// 父级数据版本号：变化时重载（与备菜 tab 同机制）。
  final int refreshTick;
  /// 采购项数量（items.length）加载后上报父级，用于 tab 汇总数量。
  final ValueChanged<int> onCountChanged;
  const _ShoppingTab({
    required this.menuId,
    required this.refreshTick,
    required this.onCountChanged,
  });
  @override
  State<_ShoppingTab> createState() => _ShoppingTabState();
}

class _ShoppingTabState extends State<_ShoppingTab> {
  ShoppingListVO? _vo;
  bool _loading = true;
  String? _err;
  final Map<int, int> _purchOverride = {}; // 乐观：itemId → purchased(0/1)
  int _lastTick = 0;

  @override
  void initState() {
    super.initState();
    _lastTick = widget.refreshTick;
    _load();
  }

  @override
  void didUpdateWidget(covariant _ShoppingTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.refreshTick != _lastTick) {
      _lastTick = widget.refreshTick;
      _load();
    }
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _err = null;
    });
    try {
      _vo = await ShoppingService.getByMenu(widget.menuId);
    } catch (_) {
      _err = '加载采购清单失败';
    }
    if (mounted) {
      widget.onCountChanged(_vo?.items.length ?? 0);
      setState(() => _loading = false);
    }
  }

  Future<void> _generate() async {
    try {
      await ShoppingService.generateFrom('menu', sourceId: widget.menuId);
      await _load();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('生成失败')));
      }
    }
  }

  /// 勾选/取消勾选：乐观更新 _purchOverride，失败回滚。
  Future<void> _toggle(ShoppingItemVO it) async {
    final cur = _eff(it);
    final next = cur == 1 ? 0 : 1;
    setState(() => _purchOverride[it.id] = next);
    try {
      await ShoppingService.togglePurchased(it.id);
    } catch (_) {
      if (mounted) {
        setState(() => _purchOverride.remove(it.id));
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('更新失败')));
      }
    }
  }

  int _eff(ShoppingItemVO it) => _purchOverride[it.id] ?? it.purchased;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    if (_loading) return const LoadingView();
    if (_err != null) return _RetryView(onRetry: _load, msg: _err!);
    final vo = _vo;
    if (vo == null) {
      // 未生成：显"按食集生成"按钮。
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(AppTokens.sp32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('尚未生成采购清单',
                  style: t.textStyles.md.copyWith(color: t.caption)),
              const SizedBox(height: AppTokens.sp16),
              ElevatedButton.icon(
                onPressed: _generate,
                icon: const Icon(Icons.shopping_cart_outlined),
                label: const Text('按食集生成'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: t.primary,
                  foregroundColor: t.card,
                ),
              ),
            ],
          ),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        children: [
          ...vo.items.map((it) => _itemCard(it)),
          const SizedBox(height: AppTokens.sp24),
        ],
      ),
    );
  }

  Widget _itemCard(ShoppingItemVO it) {
    final t = AppTokens.of(context);
    final bought = _eff(it) == 1;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: () => _toggle(it),
        hoverColor: t.primary.withValues(alpha: 0.08),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
          decoration: BoxDecoration(
              border: Border(top: BorderSide(color: t.border))),
          child: Row(
            children: [
              Icon(
                bought ? Icons.check_circle : Icons.radio_button_unchecked,
                color: bought ? AppTokens.success : t.body,
                size: 22,
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(it.displayName,
                        style: t.textStyles.md.copyWith(
                          fontWeight: FontWeight.w500,
                          decoration:
                              bought ? TextDecoration.lineThrough : null,
                          color: bought
                              ? t.body
                              : t.title,
                        )),
                    if (it.amountText.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(it.amountText,
                            style: t.textStyles.sm.copyWith(color: t.caption)),
                      ),
                  ],
                ),
              ),
              if (it.ingredientId != null && it.stockStatus != null)
                _stockBadge(it.stockStatus!, it.shortageGrams ?? 0,
                    it.pantryGrams),
            ],
          ),
        ),
      ),
    );
  }

  Widget _stockBadge(String status, double shortage, double? pantry) {
    final t = AppTokens.of(context);
    final color = _stockColor(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
      decoration: BoxDecoration(
        color: color.withAlpha(20),
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: Text(_stockLabel(status, shortage, pantry),
          style: t.textStyles.sm.copyWith(
              color: color, fontWeight: FontWeight.w600)),
    );
  }

  Color _stockColor(String status) {
    final t = AppTokens.of(context);
    switch (status) {
      case 'RED_NONE':
        return AppTokens.error;
      case 'YELLOW_SHORT':
        return AppTokens.warning;
      case 'GREEN_ENOUGH':
        return AppTokens.success;
      default:
        return t.caption;
    }
  }

  String _stockLabel(String status, double shortage, double? pantry) {
    switch (status) {
      case 'RED_NONE':
        return '没有';
      case 'YELLOW_SHORT':
        return '差 ${shortage.toInt()}g';
      case 'GREEN_ENOUGH':
        return '够·${pantry?.toInt() ?? 0}g';
      default:
        return '';
    }
  }
}

/// 加载失败重试视图。
class _RetryView extends StatelessWidget {
  final VoidCallback onRetry;
  final String msg;
  const _RetryView({required this.onRetry, required this.msg});
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Center(
        child: Padding(
          padding: const EdgeInsets.all(AppTokens.sp32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(msg,
                  style: t.textStyles.md.copyWith(color: t.caption)),
              const SizedBox(height: AppTokens.sp16),
              OutlinedButton(onPressed: onRetry, child: const Text('重试')),
            ],
          ),
        ),
      );
  }
}

/// 占位 Tab（采购 / 一起吃）。
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
  /// 备注修改成功后的回调（上层刷新详情）。
  final VoidCallback onNoteChanged;
  const _DishRow({
    required this.menuId,
    required this.dishId,
    this.dishName,
    this.servingFactor,
    this.coverUrl,
    this.note,
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
      onTap: () => context.push('/dish/$dishId', extra: {'showActions': false}),
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
              onTap: () => _editNote(context),
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
                    // 移出食集（原型菜行行尾 ✕）
                    GestureDetector(
                      onTap: () => _removeDish(context),
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
