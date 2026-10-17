import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/api_client.dart';
import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../stores/member_store.dart';
import '../../models/member.dart';
import '../../services/notification_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/member_avatar.dart';
import 'smart_home_sections.dart';

/// 智荐 tab：顶部「临期主推 / 你常做 / 季节鲜 / 最近做过」分区（G4/G5，/recommend/home），
/// 下方 想吃什么（自然语言）→ 菜谱向量库语义召回 → 组合推荐（2026-08 向量化版）。
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
  /// 语义找菜失败文案（与组合推荐错误分开展示）。
  String? _semanticError;
  final _homeKey = GlobalKey<SmartHomeSectionsState>();
  final _bellKey = GlobalKey<_NoticeBellState>();

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
      if (mounted) setState(() => _error = errorText(e, '推荐失败，请稍后重试'));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// 语义找菜（即时）：自然语言 → 向量相似 Top8，chips 展示点进详情。
  Future<void> _semanticSearch() async {
    final q = _prefCtrl.text.trim();
    if (q.isEmpty) return;
    setState(() { _semanticLoading = true; _semanticError = null; });
    try {
      final data = await ApiClient.instance.post('/dish/semantic-search', body: {
        'query': q, 'topK': 8,
      });
      if (mounted) setState(() => _semanticHits = (data as List?) ?? []);
    } catch (e) {
      if (mounted) {
        setState(() {
          _semanticHits = null;
          _semanticError = errorText(e, '找菜失败，请稍后重试');
        });
      }
    } finally {
      if (mounted) setState(() => _semanticLoading = false);
    }
  }

  @override
  void initState() {
    super.initState();
    // 进页面即展示默认推荐（冷启动热门兜底 / 有历史口味画像召回）
    _loadDefault();
  }

  /// 默认推荐：GET /ai/menu/recommend/default（memberId 空走热门兜底）。
  Future<void> _loadDefault() async {
    setState(() => _loading = true);
    try {
      final memberId = context.read<MemberStore>().currentId;
      final data = await ApiClient.instance.get('/ai/menu/recommend/default',
          query: {'memberId': memberId, 'topN': 3});
      if (mounted) setState(() => _groups = (data as List?) ?? []);
    } catch (e) {
      // 默认推荐失败：给出提示（可点「组合推荐」重试），不静默
      if (mounted) setState(() => _error = errorText(e, '默认推荐加载失败，可点「组合推荐」重试'));
    } finally {
      if (mounted) setState(() => _loading = false);
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
            // 原「智荐」Tab 改为咕嘟首屏的二级页「全部推荐」（showcase-ai-community）
            if (Navigator.of(context).canPop()) const BackHeader(title: '全部推荐'),
            Expanded(
              child: RefreshIndicator(
                color: t.primary,
                onRefresh: () async {
                  _bellKey.currentState?.refresh();
                  await _homeKey.currentState?.reload();
                },
                child: SingleChildScrollView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          _header(t),
          const SizedBox(height: AppTokens.sp16),
          // 智荐分区：今晚首推 / 快到期的 / 你家常做 / 应季 / 最近做过（空分区隐藏）
          SmartHomeSections(key: _homeKey),
          const SizedBox(height: AppTokens.sp24),
          Text('想吃点别的？', style: t.textStyles.subtitle),
          const SizedBox(height: AppTokens.sp4),
          Text('说说口味，从菜谱库里找菜或组合一顿', style: t.textStyles.caption),
          const SizedBox(height: AppTokens.sp12),

          // 想吃什么（语义主入口）：自然语言 + 快捷口味 chips
          TextField(
            controller: _prefCtrl,
            decoration: InputDecoration(
              hintText: '想吃什么？如：清淡下饭、酸甜开胃',
              prefixIcon: const Icon(Icons.search, size: 18),
              isDense: true,
              filled: true, fillColor: t.card,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
            onSubmitted: (_) => _semanticSearch(),
            // 输入变化即清空旧结果（相似菜/组合均随新输入失效）
            onChanged: (_) {
              if (_semanticHits != null || _groups != null || _error != null || _semanticError != null) {
                setState(() { _semanticHits = null; _groups = null; _error = null; _semanticError = null; });
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
                          color: t.card, borderRadius: BorderRadius.circular(AppTokens.rPill),
                          border: Border.all(color: t.border),
                        ),
                        child: Text(s, style: t.textStyles.caption.copyWith(color: t.title)),
                      ),
                    )).toList(),
          ),

          // 语义找菜即时结果（列表行样式，对齐菜谱列表）
          if (_semanticLoading)
            const Padding(padding: EdgeInsets.all(12), child: Center(child: SizedBox(
                width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)))),
          if (_semanticError != null)
            Padding(
              padding: const EdgeInsets.only(top: AppTokens.sp8),
              child: Text(_semanticError!,
                  style: t.textStyles.sm.copyWith(color: AppTokens.error)),
            ),
          if (_semanticHits != null && _semanticHits!.isEmpty)
            Padding(
              padding: const EdgeInsets.only(top: AppTokens.sp8),
              child: Text('没找到相似的菜，换个说法试试',
                  style: t.textStyles.caption.copyWith(color: t.caption)),
            ),
          if (_semanticHits != null && _semanticHits!.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp12),
            Text('找菜结果', style: t.textStyles.subtitle),
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

          // 推荐结果（默认进页即展示；展示在按钮上方）
          if (_loading && _groups == null)
            const Padding(padding: EdgeInsets.all(12), child: Center(child: SizedBox(
                width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)))),
          if (_groups != null && _groups!.isNotEmpty) ...[
            const SizedBox(height: AppTokens.sp12),
            Text('为你推荐', style: t.textStyles.subtitle),
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
                  color: AppTokens.error.withAlpha(15), borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Text(_error!, style: t.textStyles.sm.copyWith(color: AppTokens.error)),
              ),
            ),

        ]),
              ),
              ), // RefreshIndicator
            ), // Expanded → SingleChildScrollView
          ], // Column
        ),
      ),
    );
  }

  /// 顶部问候：家人头像 + 「周四晚上 · N 人吃饭」+ 今晚吃点什么。
  Widget _header(AppTokens t) {
    final ts = t.textStyles;
    List<Member> members;
    try {
      members = context.watch<MemberStore>().members;
    } catch (_) {
      members = const [];
    }
    final now = DateTime.now();
    const week = ['一', '二', '三', '四', '五', '六', '日'];
    final part = now.hour < 10 ? '早上' : now.hour < 14 ? '中午' : now.hour < 18 ? '下午' : '晚上';
    final meal = now.hour < 10 ? '早饭' : now.hour < 14 ? '午饭' : '晚饭';
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(children: [
                if (members.isNotEmpty) ...[
                  AvatarStack(size: 24, people: [for (final m in members) (name: m.name, avatarUrl: m.avatarUrl)]),
                  const SizedBox(width: AppTokens.sp8),
                ],
                Text('周${week[now.weekday - 1]}$part${members.isEmpty ? '' : ' · ${members.length} 人吃饭'}', style: ts.caption),
              ]),
              const SizedBox(height: AppTokens.sp6),
              Text('今${meal == '晚饭' ? '晚' : '天'}吃点什么', style: ts.h2),
            ],
          ),
        ),
        _NoticeBell(key: _bellKey),
      ],
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

/// 右上角消息铃铛 + 未读角标（进 /notices）。
class _NoticeBell extends StatefulWidget {
  const _NoticeBell({super.key});
  @override
  State<_NoticeBell> createState() => _NoticeBellState();
}

class _NoticeBellState extends State<_NoticeBell> {
  int _unread = 0;

  @override
  void initState() {
    super.initState();
    refresh();
  }

  Future<void> refresh() async {
    try {
      final c = await NotificationService.unreadCount();
      if (mounted) setState(() => _unread = c);
    } catch (_) {
      // 角标失败不影响页面
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Semantics(
      button: true,
      label: _unread > 0 ? '消息，$_unread 条未读' : '消息',
      child: GestureDetector(
        onTap: () async {
          await context.push('/notices');
          refresh();
        },
        child: Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(color: t.card, shape: BoxShape.circle, border: Border.all(color: t.border)),
          child: Stack(alignment: Alignment.center, children: [
            Icon(Icons.notifications_none_rounded, color: t.title),
            if (_unread > 0)
              Positioned(
                right: 9,
                top: 9,
                child: Container(
                  width: 9,
                  height: 9,
                  decoration: BoxDecoration(
                    color: AppTokens.error,
                    shape: BoxShape.circle,
                    border: Border.all(color: t.card, width: 1.5),
                  ),
                ),
              ),
          ]),
        ),
      ),
    );
  }
}
