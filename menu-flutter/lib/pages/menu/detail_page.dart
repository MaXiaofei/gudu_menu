import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../models/menu.dart';
import '../../models/prep.dart';
import '../../services/menu_service.dart';
import '../../services/prep_service.dart';
import '../../services/shopping_service.dart';
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

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      _detail = await MenuService.detail(widget.id);
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
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
    final ready = !_loading && _detail != null;
    return Scaffold(
      appBar: AppBar(title: const Text('食集详情')),
      body: !ready
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
                      _DishesTab(detail: _detail!),
                      _PrepTab(menuId: widget.id),
                      _ShoppingTab(menuId: widget.id),
                      const _Placeholder(title: '一起吃', desc: '协同点菜 · 即将上线'),
                    ],
                  ),
                ),
              ],
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
            for (int i = 0; i < _tabs.length; i++) _tabItem(i, _tabs[i]),
          ],
        ),
      );
  }

  Widget _tabItem(int idx, String label) {
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
            label,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: 14,
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
  const _DishesTab({required this.detail});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final m = detail.menu;
    return ListView(
      children: [
        Padding(
          padding: const EdgeInsets.all(AppTokens.sp16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(m.name,
                        style: TextStyle(
                            fontSize: 20, fontWeight: FontWeight.bold, color: t.title)),
                  ),
                  if (m.isDone)
                    const _StatusChip('已完成', AppTokens.success)
                  else
                    const _StatusChip('进行中', AppTokens.warning),
                ],
              ),
              const SizedBox(height: AppTokens.sp8),
              Text(
                '份数 ${m.servingCount ?? 1} · 关联 ${detail.dishes.length} 道菜',
                style: TextStyle(
                    fontSize: 12, color: t.caption),
              ),
            ],
          ),
        ),
        const _SectionTitle('包含菜品'),
        ...detail.dishes.map((d) => _DishRow(
              dishId: d.dishId,
              dishName: d.dishName,
              servingFactor: d.servingFactor,
            )),
        const SizedBox(height: AppTokens.sp24),
      ],
    );
  }
}

/// Tab 1：备菜（Plan C）。加载 GET /menu/{id}/prep + 状态交互（点/长按）。
class _PrepTab extends StatefulWidget {
  final int menuId;
  const _PrepTab({required this.menuId});
  @override
  State<_PrepTab> createState() => _PrepTabState();
}

class _PrepTabState extends State<_PrepTab> {
  MenuPrep? _prep;
  bool _loading = true;
  bool _condimentExpanded = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      _prep = await PrepService.getPrep(widget.menuId);
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
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
                  style:
                      TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: t.title)),
              const Spacer(),
              Text('已备 ${p.readyCount} / 共 ${p.totalCount} 样',
                  style: TextStyle(
                      fontSize: 12, color: t.caption)),
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
              const Text('🧂', style: TextStyle(fontSize: 16)),
              const SizedBox(width: AppTokens.sp8),
              Text('调料 $count 样 · 无需备料',
                  style: TextStyle(
                      fontSize: 12, color: t.body)),
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

/// 备菜列表行：状态 chip + 食材名 + 用量 + 共用高亮 + 来自哪些菜。
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
            // 共用项淡橙底（聚焦"一次备够"）
            color: item.shared ? t.highlight : null,
          ),
          child: Row(
            children: [
              // 状态 chip（PENDING 白底灰边；其它浅色底 + 状态色边/字）
              Container(
                padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
                decoration: BoxDecoration(
                  color: s == PrepStatus.pending ? null : chipColor.withAlpha(20),
                  border: Border.all(
                      color: s == PrepStatus.pending
                          ? t.border
                          : chipColor),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Text(s.label,
                    style: TextStyle(
                        fontSize: 12,
                        color: chipColor,
                        fontWeight: FontWeight.w600)),
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(item.ingredientName,
                              style: TextStyle(
                                  fontSize: 14, fontWeight: FontWeight.w500, color: t.title)),
                        ),
                        if (item.shared) ...[
                          const SizedBox(width: AppTokens.sp4),
                          const Text('🔥', style: TextStyle(fontSize: 12)),
                        ],
                      ],
                    ),
                    if (item.dishNames.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          item.dishNames.length >= 2
                              ? '${item.dishCount} 道菜共用 · ${item.dishNames.join("、")}'
                              : item.dishNames.first,
                          style: TextStyle(
                              fontSize: 12, color: t.caption),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                ),
              ),
              Text('${item.totalGrams.toStringAsFixed(0)}g',
                  style: TextStyle(
                      fontSize: 12, color: t.caption)),
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
  const _ShoppingTab({required this.menuId});
  @override
  State<_ShoppingTab> createState() => _ShoppingTabState();
}

class _ShoppingTabState extends State<_ShoppingTab> {
  ShoppingListVO? _vo;
  bool _loading = true;
  String? _err;
  final Map<int, int> _purchOverride = {}; // 乐观：itemId → purchased(0/1)

  @override
  void initState() {
    super.initState();
    _load();
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
    if (mounted) setState(() => _loading = false);
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
                  style:
                      TextStyle(fontSize: 14, color: t.caption)),
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
                        style: TextStyle(
                          fontSize: 14,
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
                            style: TextStyle(
                                fontSize: 12, color: t.caption)),
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
    final color = _stockColor(status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
      decoration: BoxDecoration(
        color: color.withAlpha(20),
        border: Border.all(color: color),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: Text(_stockLabel(status, shortage, pantry),
          style: TextStyle(
              fontSize: 12, color: color, fontWeight: FontWeight.w600)),
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
                  style: TextStyle(
                      fontSize: 14, color: t.caption)),
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
              Text(title,
                  style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: t.title)),
              const SizedBox(height: AppTokens.sp8),
              Text(desc,
                  style: TextStyle(
                      fontSize: 12, color: t.caption)),
            ],
          ),
        ),
      );
  }
}

/// 菜品行：直接用后端冗余的菜名（dishes 带 dishName）。
class _DishRow extends StatelessWidget {
  final int dishId;
  final String? dishName;
  final double? servingFactor;
  const _DishRow({required this.dishId, this.dishName, this.servingFactor});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
        decoration: BoxDecoration(
            border: Border(top: BorderSide(color: t.border))),
        child: Row(
          children: [
            Expanded(
              child: Text(
                (dishName == null || dishName!.isEmpty)
                    ? '菜 #$dishId'
                    : dishName!,
                style: TextStyle(fontSize: 14, color: t.title),
              ),
            ),
            Text(
              '× ${servingFactor?.toStringAsFixed(1) ?? '1.0'} 份',
              style: TextStyle(
                  fontSize: 12, color: t.caption),
            ),
          ],
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
        child: Text(text,
            style: TextStyle(
                fontSize: 16, fontWeight: FontWeight.bold, color: t.title)),
      );
  }
}

class _StatusChip extends StatelessWidget {
  final String text;
  final Color color;
  const _StatusChip(this.text, this.color);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
        decoration: BoxDecoration(
          color: color.withAlpha(20),
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
        child: Text(text,
            style: TextStyle(
                fontSize: 12, color: color, fontWeight: FontWeight.w600)),
      );
}
