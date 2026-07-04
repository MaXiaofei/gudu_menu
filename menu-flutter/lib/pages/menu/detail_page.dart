import 'package:flutter/material.dart';

import '../../core/theme.dart';
import '../../models/menu.dart';
import '../../models/prep.dart';
import '../../services/menu_service.dart';
import '../../services/prep_service.dart';
import '../../widgets/loading_empty.dart';

/// 食集详情（对应 menu-mini/src/pages/menu/Detail.vue）。
///
/// 四 Tab：菜（菜品列表）/ 备菜（Plan C）/ 采购（占位）/ 一起吃（占位）。
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
                      const _Placeholder(
                          title: '采购清单', desc: '前往首页「采购清单」入口管理'),
                      const _Placeholder(title: '一起吃', desc: '协同点菜 · 即将上线'),
                    ],
                  ),
                ),
              ],
            ),
      bottomNavigationBar: ready
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.success,
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

  Widget _buildTabBar() => Container(
        decoration: const BoxDecoration(
          color: AppColors.cardBg,
          border: Border(bottom: BorderSide(color: AppColors.divider)),
        ),
        child: Row(
          children: [
            for (int i = 0; i < _tabs.length; i++) _tabItem(i, _tabs[i]),
          ],
        ),
      );

  Widget _tabItem(int idx, String label) {
    final selected = _tabIndex == idx;
    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _tabIndex = idx),
        behavior: HitTestBehavior.opaque,
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 12),
          decoration: BoxDecoration(
            border: Border(
              bottom: BorderSide(
                color: selected ? AppColors.primary : Colors.transparent,
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
              color: selected ? AppColors.primary : AppColors.textHint,
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
    final m = detail.menu;
    return ListView(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(m.name,
                        style: const TextStyle(
                            fontSize: 20, fontWeight: FontWeight.bold)),
                  ),
                  if (m.isDone)
                    const _StatusChip('已完成', AppColors.success)
                  else
                    const _StatusChip('进行中', AppColors.warning),
                ],
              ),
              const SizedBox(height: 8),
              Text(
                '份数 ${m.servingCount ?? 1} · 关联 ${detail.dishes.length} 道菜',
                style: const TextStyle(
                    fontSize: 12, color: AppColors.textSecondary),
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
        const SizedBox(height: 24),
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
    final next = await showModalBottomSheet<PrepStatus>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.ac_unit, color: AppColors.info),
              title: const Text('化冻中'),
              onTap: () => Navigator.pop(ctx, PrepStatus.thawing),
            ),
            ListTile(
              leading: const Icon(Icons.schedule, color: AppColors.warning),
              title: const Text('腌制中'),
              onTap: () => Navigator.pop(ctx, PrepStatus.marinating),
            ),
            ListTile(
              leading:
                  const Icon(Icons.refresh, color: AppColors.textSecondary),
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
          const SizedBox(height: 8),
          _buildCondimentHeader(p.condiments.length),
          if (_condimentExpanded)
            ...p.condiments.map((it) => _PrepItemRow(
                  item: it,
                  isCondiment: true,
                  onTap: () => _toggle(it),
                  onLongPress: () => _longPress(it),
                )),
        ],
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _buildProgress(MenuPrep p) {
    final ratio = p.totalCount == 0 ? 0.0 : p.readyCount / p.totalCount;
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text('备料进度',
                  style:
                      TextStyle(fontSize: 14, fontWeight: FontWeight.bold)),
              const Spacer(),
              Text('已备 ${p.readyCount} / 共 ${p.totalCount} 样',
                  style: const TextStyle(
                      fontSize: 13, color: AppColors.textSecondary)),
            ],
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: ratio,
              minHeight: 8,
              backgroundColor: AppColors.border,
              color: AppColors.success,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCondimentHeader(int count) => GestureDetector(
        onTap: () => setState(() => _condimentExpanded = !_condimentExpanded),
        behavior: HitTestBehavior.opaque,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          color: AppColors.secondary,
          child: Row(
            children: [
              const Text('🧂', style: TextStyle(fontSize: 16)),
              const SizedBox(width: 8),
              Text('调料 $count 样 · 无需备料',
                  style: const TextStyle(
                      fontSize: 13, color: AppColors.textHint)),
              const Spacer(),
              Icon(
                  _condimentExpanded ? Icons.expand_less : Icons.expand_more,
                  color: AppColors.textSecondary),
            ],
          ),
        ),
      );
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
    final s = item.status;
    final chipColor = switch (s) {
      PrepStatus.ready => AppColors.success,
      PrepStatus.thawing => AppColors.info,
      PrepStatus.marinating => AppColors.warning,
      PrepStatus.pending => AppColors.textSecondary,
    };
    return GestureDetector(
      onTap: onTap,
      onLongPress: onLongPress,
      behavior: HitTestBehavior.opaque,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          border: const Border(top: BorderSide(color: AppColors.divider)),
          // 共用项淡橙底（聚焦"一次备够"）
          color: item.shared ? const Color(0xFFFFF8EC) : null,
        ),
        child: Row(
          children: [
            // 状态 chip（PENDING 白底灰边；其它浅色底 + 状态色边/字）
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: s == PrepStatus.pending ? null : chipColor.withAlpha(20),
                border: Border.all(
                    color: s == PrepStatus.pending
                        ? AppColors.border
                        : chipColor),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(s.label,
                  style: TextStyle(
                      fontSize: 12,
                      color: chipColor,
                      fontWeight: FontWeight.w600)),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Flexible(
                        child: Text(item.ingredientName,
                            style: const TextStyle(
                                fontSize: 14, fontWeight: FontWeight.w500)),
                      ),
                      if (item.shared) ...[
                        const SizedBox(width: 6),
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
                        style: const TextStyle(
                            fontSize: 11, color: AppColors.textSecondary),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
              ),
            ),
            Text('${item.totalGrams.toStringAsFixed(0)}g',
                style: const TextStyle(
                    fontSize: 13, color: AppColors.textSecondary)),
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
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(title,
                  style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary)),
              const SizedBox(height: 8),
              Text(desc,
                  style: const TextStyle(
                      fontSize: 13, color: AppColors.textSecondary)),
            ],
          ),
        ),
      );
}

/// 菜品行：直接用后端冗余的菜名（dishes 带 dishName）。
class _DishRow extends StatelessWidget {
  final int dishId;
  final String? dishName;
  final double? servingFactor;
  const _DishRow({required this.dishId, this.dishName, this.servingFactor});

  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: const BoxDecoration(
            border: Border(top: BorderSide(color: AppColors.divider))),
        child: Row(
          children: [
            Expanded(
              child: Text(
                (dishName == null || dishName!.isEmpty)
                    ? '菜 #$dishId'
                    : dishName!,
                style: const TextStyle(fontSize: 14),
              ),
            ),
            Text(
              '× ${servingFactor?.toStringAsFixed(1) ?? '1.0'} 份',
              style: const TextStyle(
                  fontSize: 13, color: AppColors.textSecondary),
            ),
          ],
        ),
      );
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);
  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(16),
        child: Text(text,
            style: const TextStyle(
                fontSize: 16, fontWeight: FontWeight.bold)),
      );
}

class _StatusChip extends StatelessWidget {
  final String text;
  final Color color;
  const _StatusChip(this.text, this.color);
  @override
  Widget build(BuildContext context) => Container(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
        decoration: BoxDecoration(
          color: color.withAlpha(20),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Text(text,
            style: TextStyle(
                fontSize: 12, color: color, fontWeight: FontWeight.w600)),
      );
}
