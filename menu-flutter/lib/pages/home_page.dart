import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/api_client.dart';
import '../core/theme.dart';
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
    final hour = DateTime.now().hour;
    final greeting = hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好';

    return Scaffold(
      backgroundColor: const Color(0xFFFAF8F5),
      appBar: AppBar(
        title: const Text('咕嘟小食单'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(48),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
            child: Row(children: [
              Text('$greeting，今天给家人做什么好吃的？',
                  style: const TextStyle(color: Colors.white70, fontSize: 13)),
            ]),
          ),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const MemberBar(),
          const SizedBox(height: 16),
          _buildStatsRow(),
          const SizedBox(height: 20),

          _sectionHeader('🍳', '厨房'),
          const SizedBox(height: 10),
          _buildGridRow([
            _entryCard('菜库', '📖', '/dish', AppColors.primary),
            _entryCard('排菜计划', '📅', '/mealplan', const Color(0xFF6BA8E8)),
          ]),
          const SizedBox(height: 10),
          _buildGridRow([
            _entryCard('饮食记录', '📝', '/dailylog', AppColors.saveGreen),
            _entryCard('新菜', '➕', '/create-dish', AppColors.warnOrange),
          ]),
          const SizedBox(height: 10),
          _buildGridRow([
            _entryCard('食材', '🥬', '/create-ingredient', const Color(0xFF8B5E3C)),
            _entryCard('点评', '⭐', '/dish', const Color(0xFFE8A33D)),
          ]),

          const SizedBox(height: 20),

          _sectionHeader('📦', '库存与采购'),
          const SizedBox(height: 10),
          _buildGridRow([
            _entryCard('库存', '📦', '/pantry', const Color(0xFF5B8C5A)),
            _entryCard('采购', '🛒', '/shopping', const Color(0xFFD4843A)),
          ]),

          const SizedBox(height: 20),

          _sectionHeader('🤖', 'AI 帮你'),
          const SizedBox(height: 10),
          _buildGridRow([
            _entryCard('智能荐菜', '📋', '/ai-recommend', const Color(0xFF7B68EE)),
            _entryCard('估营养', '🔍', '/ai-estimate', const Color(0xFF3A7BD5)),
          ]),

          const SizedBox(height: 24),

          Center(
            child: TextButton.icon(
              onPressed: () => context.read<AuthStore>().logout(),
              icon: const Icon(Icons.logout, size: 16),
              label: const Text('退出登录'),
              style: TextButton.styleFrom(foregroundColor: AppColors.textSecondary),
            ),
          ),
          const SizedBox(height: 16),
        ]),
      ),
    );
  }

  Widget _sectionHeader(String emoji, String title) {
    return Row(children: [
      Text(emoji, style: const TextStyle(fontSize: 16)),
      const SizedBox(width: 6),
      Text(title,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF2D2A26))),
    ]);
  }

  Widget _buildGridRow(List<Widget> children) {
    return Row(children: [
      Expanded(child: children[0]),
      const SizedBox(width: 10),
      Expanded(child: children[1]),
    ]);
  }

  Widget _buildStatsRow() {
    return Row(children: [
      Expanded(child: _statCard('$_dishCount', '道菜品', '📖', AppColors.primary)),
      const SizedBox(width: 8),
      Expanded(child: _statCard('$_logCount', '今日记录', '🍽️', AppColors.saveGreen)),
      const SizedBox(width: 8),
      Expanded(child: _statCard('$_pantryCount', '件库存', '📦', AppColors.warnOrange)),
    ]);
  }

  Widget _statCard(String value, String label, String emoji, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        boxShadow: [
          BoxShadow(color: Colors.black.withAlpha(8), blurRadius: 8, offset: const Offset(0, 2)),
        ],
      ),
      child: Column(children: [
        Text(emoji, style: const TextStyle(fontSize: 22)),
        const SizedBox(height: 4),
        Text(value, style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color)),
        Text(label, style: const TextStyle(fontSize: 11, color: AppColors.textSecondary)),
      ]),
    );
  }

  Widget _entryCard(String title, String emoji, String route, Color color) {
    return GestureDetector(
      onTap: () => context.push(route),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 12),
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(12),
          boxShadow: [
            BoxShadow(color: Colors.black.withAlpha(6), blurRadius: 6, offset: const Offset(0, 2)),
          ],
        ),
        child: Column(children: [
          Text(emoji, style: const TextStyle(fontSize: 28)),
          const SizedBox(height: 8),
          Text(title,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Color(0xFF2D2A26))),
        ]),
      ),
    );
  }
}
