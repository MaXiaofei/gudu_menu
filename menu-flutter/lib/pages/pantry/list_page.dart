import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/status_chip.dart';

/// 库存主页（三色分组版，对齐 pantry-page.html 原型）。
///
/// 结构：顶部筛选条（全部/缺/低/够，带计数，替代三色汇总条）→ 按 缺→低→够 分组列表 →
/// 每行点整行进食材详情页盘点 → 右上角「添加」（手动入库）+「去采购」（采购闭环）。
class PantryListPage extends StatefulWidget {
  const PantryListPage({super.key});

  @override
  State<PantryListPage> createState() => _PantryListPageState();
}

class _PantryListPageState extends State<PantryListPage> {
  /// 主题 token 缓存。
  AppTokens get _t => AppTokens.of(context);

  PantryGrouped? _grouped;
  bool _loading = true;
  String? _error;

  /// 筛选档：all / none / low / enough（缺省「全部」）。
  /// nullable 兜底：热重载时旧 State 实例没有本字段，避免 null 崩溃。
  String? _filter = 'all';

  /// 生效中的筛选档（null 兜底「全部」）。
  String get _activeFilter => _filter ?? 'all';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = _grouped == null;
      _error = null;
    });
    try {
      final g = await PantryService.listGrouped();
      if (!mounted) return;
      setState(() {
        _grouped = g;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      // DESIGN.md §13：Tab 主页无标题，顶部用 ActionBar 放操作（批量添加图标右对齐）。
      // 状态栏由 ActionBar 内置 AnnotatedRegion 控制（奶油底+深色字）。
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            ActionBar(
              action: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  // 「添加」（手动入库）：原型 FAB 上移，放「去采购」左侧
                  _topButton(
                    label: '添加',
                    filled: false,
                    onTap: () async {
                      await context.push('/pantry/add');
                      _load(); // 手动添加后刷新
                    },
                  ),
                  const SizedBox(width: AppTokens.sp8),
                  // 「去采购」：主操作（采购闭环），右对齐独占一行（原型定稿）
                  _topButton(
                    label: '去采购',
                    filled: true,
                    onTap: () async {
                      await context.push('/shopping');
                      _load(); // 采购勾选会回写库存，返回后刷新
                    },
                  ),
                ],
              ),
            ),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _error != null
                      ? ErrorView(text: '加载失败', onRetry: _load) // §14.1：错误态用 ErrorView，不再用 EmptyView 顶替
                      : RefreshIndicator(
                          onRefresh: _load,
                          child: _grouped == null || _grouped!.items.isEmpty
                              ? ListView(
                                  children: const [
                                    SizedBox(height: 200),
                                    Center(child: EmptyView(text: '暂无库存')),
                                  ],
                                )
                              : _buildBody(t),
                        ),
            ),
          ],
        ),
      ),
    );
  }

  /// 顶栏胶囊按钮：`filled` 实心主按钮（去采购，原型 #E89150）/ 空心次按钮（添加）。
  Widget _topButton({
    required String label,
    required bool filled,
    required VoidCallback onTap,
  }) {
    final t = AppTokens.of(context);
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 5),
        decoration: BoxDecoration(
          color: filled ? t.primary : null,
          border: filled ? null : Border.all(color: t.primary),
          borderRadius: BorderRadius.circular(AppTokens.rPill),
        ),
        child: Text(
          label,
          style: t.textStyles.tiny.copyWith(
            color: filled ? Colors.white : t.primary,
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }

  Widget _buildBody(AppTokens t) {
    final g = _grouped!;
    final f = _activeFilter;
    // 按筛选取组（全部 = 三组都显）
    final showNone = f == 'all' || f == 'none';
    final showLow = f == 'all' || f == 'low';
    final showEnough = f == 'all' || f == 'enough';
    final noneItems = showNone
        ? g.items.where((i) => i.status == 'NONE').toList()
        : const <PantryGroupedItem>[];
    final lowItems = showLow
        ? g.items.where((i) => i.status == 'LOW').toList()
        : const <PantryGroupedItem>[];
    final enoughItems = showEnough
        ? g.items.where((i) => i.status == 'ENOUGH').toList()
        : const <PantryGroupedItem>[];

    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 80),
      children: [
        // 筛选条（全部/缺/低/够，替代三色汇总条，原型 pantry-page 定稿）
        _buildFilterChips(t, g),
        const SizedBox(height: 4),
        // 分组列表
        if (noneItems.isNotEmpty) ...[
          _buildSectionTitle(t, '缺 / 空 · ${noneItems.length}', AppTokens.error),
          ...noneItems.map((i) => _buildRow(t, i)),
        ],
        if (lowItems.isNotEmpty) ...[
          _buildSectionTitle(t, '偏低 · ${lowItems.length}', AppTokens.warning),
          ...lowItems.map((i) => _buildRow(t, i)),
        ],
        if (enoughItems.isNotEmpty) ...[
          _buildSectionTitle(t, '够 · ${enoughItems.length}', AppTokens.success),
          ...enoughItems.map((i) => _buildRow(t, i)),
        ],
      ],
    );
  }

  /// 筛选条：全部 / 缺 / 低 / 够（带计数）。选中实心，未选白底描边。
  Widget _buildFilterChips(AppTokens t, PantryGrouped g) {
    return Row(
      children: [
        _chip(t, 'all', '全部', g.items.length, null),
        const SizedBox(width: 6),
        _chip(t, 'none', '缺', g.none, AppTokens.error),
        const SizedBox(width: 6),
        _chip(t, 'low', '低', g.low, AppTokens.warning),
        const SizedBox(width: 6),
        _chip(t, 'enough', '够', g.enough, AppTokens.success),
      ],
    );
  }

  /// 单个筛选 chip：选中实心（全部=深棕底，缺/低/够=各自三色底）白字；
  /// 未选白底 + 描边，文字用三色（全部用正文色）。
  Widget _chip(AppTokens t, String key, String label, int count, Color? color) {
    final selected = _activeFilter == key;
    return GestureDetector(
      onTap: () => setState(() => _filter = key),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: selected ? (color ?? t.title) : t.card,
          border: selected ? null : Border.all(color: t.border),
          borderRadius: BorderRadius.circular(AppTokens.rPill),
        ),
        child: Text(
          '$label $count',
          style: t.textStyles.tiny.copyWith(
            color: selected ? Colors.white : (color ?? t.body),
            fontWeight: FontWeight.w800,
          ),
        ),
      ),
    );
  }

  Widget _buildSectionTitle(AppTokens t, String text, Color color) {
    return Padding(
      padding: const EdgeInsets.only(top: 10, bottom: 4),
      child: Text(text,
          style: t.textStyles.micro.copyWith(color: color, letterSpacing: 1)),
    );
  }

  /// 单行：食材名 + 来源标签 + 数量 + 进入箭头
  Widget _buildRow(AppTokens t, PantryGroupedItem item) {
    final color = stockColor(item.status);
    final hasSource = item.sourceLabel.isNotEmpty;
    return Card(
      margin: const EdgeInsets.only(bottom: 4),
      elevation: 0,
      color: t.card,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        side: BorderSide(color: color.withAlpha(40)),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        onTap: () async {
          await context.push('/pantry/${item.ingredientId}');
          _load(); // 详情页盘点后刷新
        },
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          child: Row(
            children: [
              // 食材前置缩略图：无图走首字色块占位（DESIGN.md §10.4）
              InitialAvatar(name: item.displayName, size: 40),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(item.displayName,
                        style: t.textStyles.cardTitle.copyWith(color: t.title)),
                    const SizedBox(height: 2),
                    Text(
                      hasSource
                          ? '${item.sourceLabel} ${item.sourceSub}'
                          : (item.status == 'NONE' ? '本来就没有' : '无变动记录'),
                      style: t.textStyles.tiny.copyWith(
                        color: hasSource && item.sourceLabel == '手动' ? _t.primary : t.caption,
                        fontWeight: hasSource && item.sourceLabel == '手动' ? FontWeight.w700 : FontWeight.normal,
                      ),
                    ),
                  ],
                ),
              ),
              Text(item.displayAmount,
                  style: t.textStyles.sm.copyWith(color: color)),
              const SizedBox(width: 6),
              Icon(Icons.chevron_right, size: 18, color: t.caption),
            ],
          ),
        ),
      ),
    );
  }
}
