import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../../services/dailylog_service.dart';
import '../../stores/member_store.dart';
import '../../widgets/action_bar.dart';

/// 智能荐菜：输入范围/筛选 → AI 推荐菜品组合（V55：预算输入随价格链路删除）。
class AiRecommendPage extends StatefulWidget {
  const AiRecommendPage({super.key});
  @override
  State<AiRecommendPage> createState() => _AiRecommendPageState();
}

class _AiRecommendPageState extends State<AiRecommendPage> {
  String _scope = 'DAY';
  String _maxMinutes = '';
  String _maxDifficulty = '';
  final _prefCtrl = TextEditingController();
  List<dynamic>? _semanticHits; // 语义找菜即时结果
  bool _semanticLoading = false;
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
        'scope': _scope,
        // 语义偏好（可空）：参与向量召回查询，如「清淡下饭」「酸甜开胃」
        if (_prefCtrl.text.trim().isNotEmpty) 'preference': _prefCtrl.text.trim(),
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

  /// 语义找菜（即时）：自然语言 → 向量相似 Top8，chips 展示点进详情。
  Future<void> _semanticSearch() async {
    final q = _prefCtrl.text.trim();
    if (q.isEmpty) return;
    setState(() { _semanticLoading = true; });
    try {
      final data = await ApiClient.instance.post('/dish/semantic-search', body: {
        'query': q, 'topK': 8,
        if (_maxMinutes.isNotEmpty) 'maxMinutes': int.tryParse(_maxMinutes),
        if (_maxDifficulty.isNotEmpty) 'maxDifficulty': int.tryParse(_maxDifficulty),
      });
      if (mounted) setState(() => _semanticHits = (data as List?) ?? []);
    } catch (_) {
      if (mounted) setState(() => _semanticHits = []);
    } finally {
      if (mounted) setState(() => _semanticLoading = false);
    }
  }

  @override
  void dispose() {
    _prefCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      // DESIGN.md §13：Tab 主页无标题（不放「智能荐菜」），顶部用 ActionBar。
      // 推荐 tab 无操作，ActionBar() 不传 action → 返回 SizedBox.shrink。
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            const ActionBar(),
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(16),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              gradient: const LinearGradient(colors: [Color(0xFF7B68EE), Color(0xFF9B6FE8)]),
              borderRadius: BorderRadius.circular(AppTokens.rMd),
            ),
            child: Column(children: [
              Text('智能荐菜', style: t.textStyles.subtitle.copyWith(color: t.card)),
              const SizedBox(height: 8),
              Text('说出想吃的口味，结合做菜历史从菜谱语义库推荐', style: t.textStyles.sm.copyWith(color: Colors.white70)),
            ]),
          ),
          const SizedBox(height: 16),

          // 想吃什么（语义主入口）：自然语言 + 快捷口味 chips
          TextField(
            controller: _prefCtrl,
            decoration: InputDecoration(
              labelText: '想吃什么',
              hintText: '如：清淡下饭、酸甜开胃、来点汤',
              prefixIcon: const Icon(Icons.auto_awesome_outlined),
              isDense: true,
              filled: true, fillColor: t.bg,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            onSubmitted: (_) => _semanticSearch(),
          ),
          const SizedBox(height: AppTokens.sp8),
          Wrap(
            spacing: AppTokens.sp6, runSpacing: AppTokens.sp4,
            children: ['清淡下饭', '酸甜开胃', '快手菜', '来点硬菜', '暖暖的汤']
                .map((s) => InkWell(
                      onTap: () { _prefCtrl.text = s; _semanticSearch(); },
                      borderRadius: BorderRadius.circular(AppTokens.rPill),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 5),
                        decoration: BoxDecoration(
                          color: t.secondary, borderRadius: BorderRadius.circular(AppTokens.rPill),
                          border: Border.all(color: t.primarySoft),
                        ),
                        child: Text(s, style: t.textStyles.xs.copyWith(color: t.accent)),
                      ),
                    )).toList(),
          ),

          // 语义找菜即时结果（Top8 chips，点进详情）
          if (_semanticLoading)
            const Padding(padding: EdgeInsets.all(12), child: Center(child: SizedBox(
                width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)))),
          if (_semanticHits != null && _semanticHits!.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp8),
            Wrap(
              spacing: AppTokens.sp6, runSpacing: AppTokens.sp4,
              children: _semanticHits!.map((h) {
                final dishId = h['dishId'] as int?;
                final name = h['name'] as String? ?? '';
                final score = ((h['score'] as num?) ?? 0).toDouble();
                return InkWell(
                  onTap: () { if (dishId != null) context.push('/dish/$dishId'); },
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                    decoration: BoxDecoration(
                      color: t.primary.withAlpha(15), borderRadius: BorderRadius.circular(AppTokens.rMd),
                    ),
                    child: Text('$name ${(score * 100).toStringAsFixed(0)}%',
                        style: t.textStyles.xs.copyWith(color: t.primary)),
                  ),
                );
              }).toList(),
            ),
          ],
          const SizedBox(height: 12),

          // 范围（可选）
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            decoration: BoxDecoration(
              color: t.bg, borderRadius: BorderRadius.circular(AppTokens.rSm),
            ),
            child: Row(children: [
              _scopeChip('一天', 'DAY'), _scopeChip('一周', 'WEEK'),
            ]),
          ),
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

          Row(children: [
            Expanded(
              child: SizedBox(
                height: 44,
                child: OutlinedButton(
                  onPressed: _semanticLoading ? null : _semanticSearch,
                  child: const Text('找菜'),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              flex: 2,
              child: SizedBox(
                height: 44,
                child: ElevatedButton.icon(
                  onPressed: _loading ? null : _recommend,
                  icon: _loading
                      ? SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: t.card))
                      : const Icon(Icons.auto_awesome, size: 18),
                  label: const Text('推荐菜单'),
                ),
              ),
            ),
          ]),

          // 错误/空态
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFFFF3F0), borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Text(_error!, style: t.textStyles.sm.copyWith(color: AppTokens.error)),
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
                  Expanded(child: Text(
                    '当前成员未设置健康约束（糖上限/热量上限/过敏原），推荐未做健康过滤。建议在成员档案中完善。',
                    style: t.textStyles.sm.copyWith(color: const Color(0xFF9B8060)),
                  )),
                ]),
              ),
            ],
          ],
        ]),
              ),
            ), // Expanded → SingleChildScrollView
          ], // Column
        ),
      ),
    );
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
        child: Text(label, style: t.textStyles.sm.copyWith(color: active ? t.card : t.caption)),
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
              child: Text('$index', style: t.textStyles.sm.copyWith(fontWeight: FontWeight.bold, color: t.card)),
            ),
            const SizedBox(width: 8),
            Text('推荐组合', style: t.textStyles.cardTitle.copyWith(color: t.title)),
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
                  child: Text(name, style: t.textStyles.sm.copyWith(color: t.primary,
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
                Text('·', style: t.textStyles.sm.copyWith(color: t.caption)),
                const SizedBox(width: 8),
                Expanded(child: Text(r, style: t.textStyles.sm.copyWith(color: t.caption))),
              ]),
            )),
          ],
        ]),
      ),
    );
  }
}
