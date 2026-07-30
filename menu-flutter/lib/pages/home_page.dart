import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/api_client.dart';
import '../core/app_theme.dart';
import '../stores/auth_store.dart';
import '../stores/member_store.dart';
import '../widgets/member_bar.dart';

/// 首页 — 场景分区的功能入口 + 统计概览。
class HomePage extends StatefulWidget {
  const HomePage({super.key});
  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  int _dishCount = 0;
  int _pantryCount = 0;
  int _logCount = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<MemberStore>().load();
      _loadStats();
    });
  }

  Future<void> _loadStats() async {
    try {
      final dishes = await ApiClient.instance.get('/dish/search', query: {'pageNum': 1, 'pageSize': 1});
      final pantry = await ApiClient.instance.get('/pantry', query: {'pageNum': 1, 'pageSize': 1});
      final today = DateTime.now();
      final dateStr = '${today.year}-${today.month.toString().padLeft(2, '0')}-${today.day.toString().padLeft(2, '0')}';
      final log = await ApiClient.instance.get('/dailylog', query: {'date': dateStr});
      if (mounted) {
        setState(() {
          _dishCount = (dishes is Map) ? (dishes['total'] as num?)?.toInt() ?? 0 : 0;
          _pantryCount = (pantry is Map) ? (pantry['total'] as num?)?.toInt() ?? 0 : 0;
          _logCount = (log is Map && log['items'] is List) ? (log['items'] as List).length : 0;
        });
      }
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final hour = DateTime.now().hour;
    final greeting = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好';

    return Scaffold(
      backgroundColor: t.bg,
      appBar: AppBar(
        title: const Text('咕嘟小食单'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(48),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Row(children: [
              Text('$greeting，今天给家人做什么好吃的？',
                  style: TextStyle(color: t.card.withValues(alpha: 0.7), fontSize: 12)),
            ]),
          ),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(AppTokens.sp16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const MemberBar(),
          const SizedBox(height: AppTokens.sp16),
          _buildStatsRow(),
          const SizedBox(height: AppTokens.sp16),

          _sectionHeader('🍳', '厨房'),
          const SizedBox(height: AppTokens.sp8),
          _buildGridRow([
            _entryCard('菜库', '📖', '/dish', t.primary),
            _entryCard('排菜计划', '📅', '/mealplan', const Color(0xFF6BA8E8)),
          ]),
          const SizedBox(height: AppTokens.sp8),
          _buildGridRow([
            _entryCard('饮食记录', '📝', '/dailylog', AppTokens.success),
            _entryCard('新菜', '➕', '/create-dish', AppTokens.warning),
          ]),
          const SizedBox(height: AppTokens.sp8),
          _buildGridRow([
            _entryCard('食材', '🥬', '/ingredient', const Color(0xFF8B5E3C)),
            _entryCard('点评', '⭐', '/dish', const Color(0xFFE8A33D)),
          ]),

          const SizedBox(height: AppTokens.sp16),

          _sectionHeader('📦', '库存与采购'),
          const SizedBox(height: AppTokens.sp8),
          _buildGridRow([
            _entryCard('库存', '📦', '/pantry', const Color(0xFF5B8C5A)),
            _entryCard('采购', '🛒', '/shopping', const Color(0xFFD4843A)),
          ]),
          const SizedBox(height: AppTokens.sp8),
          _buildGridRow([
            _entryCard('食集', '🍱', '/menu', const Color(0xFF4FAE6E)),
            _entryCard('饮食记录', '📝', '/dailylog', AppTokens.success),
          ]),

          const SizedBox(height: AppTokens.sp16),

          _sectionHeader('🤖', 'AI 帮你'),
          const SizedBox(height: AppTokens.sp8),
          _buildGridRow([
            _entryCard('智能荐菜', '📋', '/ai-recommend', const Color(0xFF7B68EE)),
            _entryCard('估营养', '🔍', '/ai-estimate', const Color(0xFF3A7BD5)),
          ]),

          const SizedBox(height: AppTokens.sp24),

          Center(
            child: TextButton.icon(
              onPressed: () => context.read<AuthStore>().logout(),
              icon: const Icon(Icons.logout, size: 16),
              label: const Text('退出登录'),
              style: TextButton.styleFrom(foregroundColor: t.caption),
            ),
          ),
          const SizedBox(height: AppTokens.sp16),
        ]),
      ),
    );
  }

  Widget _sectionHeader(String emoji, String title) {
    final t = AppTokens.of(context);
    return Row(children: [
      Text(emoji, style: const TextStyle(fontSize: 16)),
      const SizedBox(width: AppTokens.sp4),
      Text(title,
          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: t.title)),
    ]);
  }

  Widget _buildGridRow(List<Widget> children) {
    return Row(children: [
      Expanded(child: children[0]),
      const SizedBox(width: AppTokens.sp8),
      Expanded(child: children[1]),
    ]);
  }

  Widget _buildStatsRow() {
    final t = AppTokens.of(context);
    return Row(children: [
      Expanded(child: _statCard('$_dishCount', '道菜品', '📖', t.primary)),
      const SizedBox(width: AppTokens.sp8),
      Expanded(child: _statCard('$_logCount', '今日记录', '🍽️', AppTokens.success)),
      const SizedBox(width: AppTokens.sp8),
      Expanded(child: _statCard('$_pantryCount', '件库存', '📦', AppTokens.warning)),
    ]);
  }

  Widget _statCard(String value, String label, String emoji, Color color) {
    final t = AppTokens.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: AppTokens.sp12),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        boxShadow: t.elevationSm,
      ),
      child: Column(children: [
        Text(emoji, style: const TextStyle(fontSize: 22)),
        const SizedBox(height: AppTokens.sp4),
        Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
        Text(label, style: TextStyle(fontSize: 12, color: t.caption)),
      ]),
    );
  }

  Widget _entryCard(String title, String emoji, String route, Color color) {
    final t = AppTokens.of(context);
    return InkWell(
      onTap: () => context.push(route),
      borderRadius: BorderRadius.circular(AppTokens.rMd),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: AppTokens.sp16, horizontal: AppTokens.sp12),
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rMd),
          boxShadow: t.elevationSm,
        ),
        child: Column(children: [
          Text(emoji, style: const TextStyle(fontSize: 28)),
          const SizedBox(height: AppTokens.sp8),
          Text(title,
              style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: t.title)),
        ]),
      ),
    );
  }
}
