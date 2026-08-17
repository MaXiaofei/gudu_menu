import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../../stores/member_store.dart';
import '../../widgets/action_bar.dart';

/// 推荐：想吃什么（自然语言）→ 菜谱向量库语义召回 → 组合推荐（2026-08 向量化版）。
class AiRecommendPage extends StatefulWidget {
  const AiRecommendPage({super.key});
  @override
  State<AiRecommendPage> createState() => _AiRecommendPageState();
}

class _AiRecommendPageState extends State<AiRecommendPage> {
  final _prefCtrl = TextEditingController();
  List<dynamic>? _semanticHits; // 语义找菜即时结果
  bool _semanticLoading = false;
  bool _loading = false;
  List<dynamic>? _groups;
  String? _error;

  Future<void> _recommend() async {
    setState(() { _loading = true; _error = null; _groups = null; });
    try {
      final memberId = context.read<MemberStore>().currentId;

      final body = <String, dynamic>{
        'memberId': memberId,
        // 语义偏好：参与向量召回查询，如「清淡下饭」「酸甜开胃」
        if (_prefCtrl.text.trim().isNotEmpty) 'preference': _prefCtrl.text.trim(),
      };

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
          // 页面标题（无花哨头部卡：Tab 主页 ActionBar 下直接内容，§13）
          Text('推荐', style: t.textStyles.subtitle),
          const SizedBox(height: AppTokens.sp4),
          Text('说说想吃的口味，从菜谱语义库找菜、组合推荐',
              style: t.textStyles.caption.copyWith(color: t.caption)),
          const SizedBox(height: AppTokens.sp16),

          // 想吃什么（语义主入口）：自然语言 + 快捷口味 chips
          TextField(
            controller: _prefCtrl,
            decoration: InputDecoration(
              hintText: '想吃什么？如：清淡下饭、酸甜开胃',
              prefixIcon: const Icon(Icons.search, size: 18),
              isDense: true,
              filled: true, fillColor: t.bg,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            onSubmitted: (_) => _semanticSearch(),
            // 输入变化即清空旧结果（相似菜/组合均随新输入失效）
            onChanged: (_) {
              if (_semanticHits != null || _groups != null || _error != null) {
                setState(() { _semanticHits = null; _groups = null; _error = null; });
              }
            },
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

          // 语义找菜即时结果（列表行样式，对齐菜谱列表）
          if (_semanticLoading)
            const Padding(padding: EdgeInsets.all(12), child: Center(child: SizedBox(
                width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)))),
          if (_semanticHits != null && _semanticHits!.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp12),
            Text('找菜结果', style: t.textStyles.sectionLabel.copyWith(color: t.caption)),
            const SizedBox(height: AppTokens.sp6),
            ..._semanticHits!.map((h) {
              final dishId = h['dishId'] as int?;
              final name = h['name'] as String? ?? '';
              final cookTime = h['cookTime'] as int?;
              return InkWell(
                onTap: () { if (dishId != null) context.push('/dish/$dishId'); },
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                child: Container(
                  margin: const EdgeInsets.only(bottom: 7),
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                  decoration: BoxDecoration(
                    color: t.card,
                    borderRadius: BorderRadius.circular(AppTokens.rMd),
                    border: Border.all(color: t.border),
                  ),
                  child: Row(children: [
                    Expanded(child: Text(name, style: t.textStyles.cardTitle)),
                    if (cookTime != null)
                      Text('$cookTime 分钟', style: t.textStyles.caption),
                    Icon(Icons.chevron_right, size: 16, color: t.caption),
                  ]),
                ),
              );
            }),
          ],
          const SizedBox(height: AppTokens.sp8),

          // 组合推荐结果（展示在按钮上方）
          if (_groups != null && _groups!.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp12),
            Text('推荐组合', style: t.textStyles.sectionLabel.copyWith(color: t.caption)),
            const SizedBox(height: AppTokens.sp6),
            ..._groups!.asMap().entries.map((e) => _buildGroupCard(e.key + 1, e.value as Map<String, dynamic>)),
          ],
          const SizedBox(height: 12),

          Row(children: [
            Expanded(
              child: SizedBox(
                height: 44,
                child: OutlinedButton(
                  onPressed: _semanticLoading ? null : _semanticSearch,
                  child: const Text('相似菜'),
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
                      : null,
                  label: const Text('组合推荐'),
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

        ]),
              ),
            ), // Expanded → SingleChildScrollView
          ], // Column
        ),
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
            Text('$index', style: t.textStyles.lg.copyWith(color: t.primary, fontWeight: FontWeight.w800)),
            const SizedBox(width: AppTokens.sp8),
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
