import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/status_chip.dart';

/// 库存主页（三色分组版，对齐 pantry-page-preview.html 原型）。
///
/// 结构：顶部三色汇总条（够 N / 低 N / 缺 N）→ 按 缺→低→够 分组列表 →
/// 每行点整行进食材详情页盘点 → 右下浮动「添加」进手动添加页。
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
              action: IconButton(
                icon: const Icon(Icons.playlist_add),
                tooltip: '批量添加',
                onPressed: () => context.push('/pantry/add'),
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
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          await context.push('/pantry/add');
          _load(); // 返回后刷新
        },
        backgroundColor: _t.primary,
        child: Text('添加', style: _t.textStyles.cardTitle.copyWith(color: Colors.white)),
      ),
    );
  }

  Widget _buildBody(AppTokens t) {
    final g = _grouped!;
    final noneItems = g.items.where((i) => i.status == 'NONE').toList();
    final lowItems = g.items.where((i) => i.status == 'LOW').toList();
    final enoughItems = g.items.where((i) => i.status == 'ENOUGH').toList();

    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 80),
      children: [
        // 三色汇总条
        _buildSummaryBar(t, g),
        const SizedBox(height: 12),
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

  /// 三色汇总条：够(绿)/低(黄)/缺(红)，宽度按数量比例。
  Widget _buildSummaryBar(AppTokens t, PantryGrouped g) {
    final total = g.enough + g.low + g.none;
    if (total == 0) return const SizedBox.shrink();
    return ClipRRect(
      borderRadius: BorderRadius.circular(AppTokens.rSm),
      child: Row(
        children: [
          if (g.enough > 0)
            Expanded(flex: g.enough, child: _seg(t, '${g.enough}', AppTokens.success, '够')),
          if (g.low > 0)
            Expanded(flex: g.low, child: _seg(t, '${g.low}', AppTokens.warning, '低')),
          if (g.none > 0)
            Expanded(flex: g.none, child: _seg(t, '${g.none}', AppTokens.error, '缺')),
        ],
      ),
    );
  }

  Widget _seg(AppTokens t, String count, Color color, String label) {
    return Container(
      color: color,
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      child: Text('$label $count',
          style: t.textStyles.chip.copyWith(color: Colors.white)),
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
