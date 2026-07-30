import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../../services/dailylog_service.dart';
import '../../services/mealplan_service.dart';
import '../../stores/member_store.dart';

/// 智能荐菜：输入预算/范围/筛选 → AI 推荐菜品组合。
class AiRecommendPage extends StatefulWidget {
  const AiRecommendPage({super.key});
  @override
  State<AiRecommendPage> createState() => _AiRecommendPageState();
}

class _AiRecommendPageState extends State<AiRecommendPage> {
  String _scope = 'DAY';
  String _budget = '50';
  String _maxMinutes = '';
  String _maxDifficulty = '';
  bool _loading = false;
  List<dynamic>? _groups;
  String? _error;
  bool _hasHealthProfile = true; // 当前成员是否有健康档案

  Future<void> _recommend() async {
    setState(() { _loading = true; _error = null; _groups = null; });
    try {
      final memberId = context.read<MemberStore>().currentId;
      // 检查是否有健康档案（营养目标接口返回 null 表示没填身高体重/goal）
      try {
        final target = await DailyLogService.nutritionTarget(memberId);
        _hasHealthProfile = target != null;
      } catch (_) {
        _hasHealthProfile = false;
      }

      final body = <String, dynamic>{
        'memberId': memberId,
        'budget': double.tryParse(_budget) ?? 50,
        'scope': _scope,
      };
      if (_maxMinutes.isNotEmpty) body['maxMinutes'] = int.tryParse(_maxMinutes);
      if (_maxDifficulty.isNotEmpty) body['maxDifficulty'] = int.tryParse(_maxDifficulty);

      final data = await ApiClient.instance.post('/ai/menu/recommend', body: body);
      final groups = (data as List?) ?? [];
      setState(() {
        _groups = groups;
        if (groups.isEmpty) {
          _error = '暂无推荐，菜库菜品较少时建议先录入更多菜品';
        }
      });
    } catch (e) {
      setState(() => _error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('智能荐菜')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: const LinearGradient(colors: [Color(0xFF7B68EE), Color(0xFF9B6FE8)]),
              borderRadius: BorderRadius.circular(AppTokens.rMd),
            ),
            child: Column(children: [
              Text('📋 智能荐菜', style: TextStyle(color: t.card, fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text('根据预算和健康约束推荐菜品组合', style: TextStyle(color: Colors.white70, fontSize: 12)),
            ]),
          ),
          const SizedBox(height: 16),

          // 范围 + 预算
          Row(children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              decoration: BoxDecoration(
                color: t.bg, borderRadius: BorderRadius.circular(AppTokens.rSm),
              ),
              child: Row(children: [
                _scopeChip('一天', 'DAY'), _scopeChip('一周', 'WEEK'),
              ]),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: TextField(
                controller: TextEditingController(text: _budget),
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: '预算(元)', isDense: true,
                  filled: true, fillColor: t.bg,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm)),
                ),
                onChanged: (v) => _budget = v,
              ),
            ),
          ]),
          const SizedBox(height: 12),

          // 筛选条件
          Row(children: [
            Expanded(
              child: TextField(
                controller: TextEditingController(text: _maxMinutes),
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: '最长烹饪(分)', isDense: true,
                  filled: true, fillColor: t.bg,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm)),
                ),
                onChanged: (v) => _maxMinutes = v,
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: TextField(
                controller: TextEditingController(text: _maxDifficulty),
                keyboardType: TextInputType.number,
                decoration: InputDecoration(
                  labelText: '难度上限(1-5)', isDense: true,
                  filled: true, fillColor: t.bg,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm)),
                ),
                onChanged: (v) => _maxDifficulty = v,
              ),
            ),
          ]),
          const SizedBox(height: 16),

          SizedBox(
            height: 44,
            child: ElevatedButton.icon(
              onPressed: _loading ? null : _recommend,
              icon: _loading
                  ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: t.card))
                  : const Icon(Icons.auto_awesome, size: 18),
              label: const Text('推荐菜单'),
            ),
          ),

          // 错误/空态
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF3F0), borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Text(_error!, style: const TextStyle(color: AppTokens.error, fontSize: 12)),
              ),
            ),

          // 结果
          if (_groups != null && _groups!.isNotEmpty) ...[
            const SizedBox(height: 16),
            ..._groups!.asMap().entries.map((e) => _buildGroupCard(e.key + 1, e.value as Map<String, dynamic>)),
            if (!_hasHealthProfile) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFFBF0),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  border: Border.all(color: const Color(0xFFE8D8B8)),
                ),
                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Text('⚠️', style: TextStyle(fontSize: 12)),
                  const SizedBox(width: 8),
                  Expanded(child: Text(
                    '当前成员未设置健康约束（糖上限/热量上限/过敏原），推荐未做健康过滤。建议在成员档案中完善。',
                    style: TextStyle(fontSize: 12, color: const Color(0xFF9B8060)),
                  )),
                ]),
              ),
            ],
          ],
        ]),
      ),
    );
  }

  /// 把推荐组合排入周计划：选日期 → 挂到当前 plan
  Future<void> _addToPlan(List<Map<String, dynamic>> dishes) async {
    final plans = await MealPlanService.list();
    if (plans.isEmpty) {
      _snack('请先创建周计划');
      return;
    }
    final planId = plans.first.id;

    final picked = await showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime.now(),
      lastDate: DateTime.now().add(const Duration(days: 30)),
    );
    if (picked == null) return;
    final dateStr = '${picked.year}-${picked.month.toString().padLeft(2, '0')}-${picked.day.toString().padLeft(2, '0')}';

    int added = 0;
    for (final d in dishes) {
      final dishId = d['dishId'] as int?;
      if (dishId == null) continue;
      try {
        await MealPlanService.addItem(planId, MealPlanItem(
          date: dateStr, meal: '午餐', dishId: dishId, servingFactor: 1,
        ));
        added++;
      } catch (_) {}
    }
    _snack('已排入 $added 道到 $dateStr 午餐');
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(msg), duration: const Duration(seconds: 2)));
  }

  Widget _scopeChip(String label, String value) {
    final t = AppTokens.of(context);
    final active = _scope == value;
    return InkWell(
      onTap: () => setState(() => _scope = value),
      borderRadius: BorderRadius.circular(AppTokens.rSm),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: active ? t.primary : Colors.transparent,
          borderRadius: BorderRadius.circular(AppTokens.rSm),
        ),
        child: Text(label, style: TextStyle(fontSize: 12, color: active ? t.card : t.caption)),
      ),
    );
  }

  Widget _buildGroupCard(int index, Map<String, dynamic> group) {
    final t = AppTokens.of(context);
    final dishes = (group['dishes'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    final reasons = (group['reasons'] as List?)?.cast<String>() ?? [];
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd),
          side: BorderSide(color: t.border)),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            CircleAvatar(
              radius: 12, backgroundColor: t.primary,
              child: Text('$index', style: TextStyle(color: t.card, fontSize: 12, fontWeight: FontWeight.bold)),
            ),
            const SizedBox(width: 8),
            Text('推荐组合', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: t.title)),
          ]),
          const SizedBox(height: 12),
          // 菜品 chips（可点击跳详情）
          Wrap(
            spacing: 8, runSpacing: 4,
            children: dishes.map((d) {
              final dishId = d['dishId'] as int?;
              final name = d['name'] as String? ?? '';
              return InkWell(
                onTap: () {
                  if (dishId != null) context.push('/dish/$dishId');
                },
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  decoration: BoxDecoration(
                    color: t.primary.withAlpha(15), borderRadius: BorderRadius.circular(AppTokens.rMd),
                  ),
                  child: Text(name, style: TextStyle(fontSize: 12, color: t.primary,
                      decoration: TextDecoration.underline, decorationColor: t.primary.withAlpha(50))),
                ),
              );
            }).toList(),
          ),
          if (reasons.isNotEmpty) ...[
            const SizedBox(height: 12),
            ...reasons.map((r) => Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('·', style: TextStyle(fontSize: 12, color: t.caption)),
                const SizedBox(width: 8),
                Expanded(child: Text(r, style: TextStyle(fontSize: 12, color: t.caption))),
              ]),
            )),
          ],
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: () => _addToPlan(dishes),
              icon: const Icon(Icons.calendar_today, size: 14),
              label: const Text('排入计划', style: TextStyle(fontSize: 12)),
            ),
          ),
        ]),
      ),
    );
  }
}
