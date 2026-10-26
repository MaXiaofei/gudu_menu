import 'dart:async';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/ai_label.dart';
import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../core/lite_markdown.dart';
import '../../models/showcase_types.dart';
import '../../services/agent_service.dart';
import '../../stores/member_store.dart';
import '../../widgets/ai_badge.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/tone_badge.dart';

/// 动作卡变化回调：[replacing] 为被 revise 取代的旧 actionId（新卡原位替换）。
typedef ActionChanged = void Function(PendingAction action, {String? replacing});

/// 咕嘟头像（品牌 logo 圆底）。
class GuduAvatar extends StatelessWidget {
  final double size;
  const GuduAvatar({super.key, this.size = 32});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        gradient: LinearGradient(colors: [t.primary, t.primaryDeep]),
        shape: BoxShape.circle,
      ),
      alignment: Alignment.center,
      child: Icon(Icons.soup_kitchen_rounded, size: size * 0.58, color: t.card),
    );
  }
}

/// 用户消息气泡（右对齐，主色底白字）。
class UserBubble extends StatelessWidget {
  final String text;
  const UserBubble(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Align(
      alignment: Alignment.centerRight,
      child: Container(
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.78),
        margin: const EdgeInsets.only(left: AppTokens.sp32, bottom: AppTokens.sp12),
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp10),
        decoration: BoxDecoration(
          color: t.primaryDeep,
          borderRadius: const BorderRadius.only(
            topLeft: Radius.circular(AppTokens.rLg),
            topRight: Radius.circular(AppTokens.rLg),
            bottomLeft: Radius.circular(AppTokens.rLg),
            bottomRight: Radius.circular(AppTokens.rXs),
          ),
        ),
        child: Text(text, style: t.textStyles.body.copyWith(color: t.card)),
      ),
    );
  }
}

/// 助手消息：工具调用链 → 正文（流式光标）→ 卡片 → 待确认动作 → 评价与报错编号。
class AssistantMessageView extends StatelessWidget {
  final AgentMessage message;
  final ActionChanged onActionChanged;
  final ValueChanged<int>? onRate;
  final VoidCallback? onRetry;

  const AssistantMessageView({
    super.key,
    required this.message,
    required this.onActionChanged,
    this.onRate,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final m = message;
    final children = <Widget>[
      if (m.tools.isNotEmpty) ToolChainView(tools: m.tools, initiallyExpanded: m.streaming || m.cards.isEmpty),
      if (m.text.isNotEmpty || (m.streaming && m.tools.isEmpty))
        Padding(
          padding: const EdgeInsets.only(top: AppTokens.sp6),
          child: StreamingText(text: m.text, streaming: m.streaming),
        ),
      for (final c in m.cards) Padding(padding: const EdgeInsets.only(top: AppTokens.sp10), child: AgentCardView(card: c)),
      for (final a in m.actions)
        Padding(
          padding: const EdgeInsets.only(top: AppTokens.sp10),
          child: ActionCardView(key: ValueKey(a.actionId), action: a, onChanged: onActionChanged),
        ),
      if (m.error != null)
        Padding(
          padding: const EdgeInsets.only(top: AppTokens.sp8),
          child: Container(
            padding: const EdgeInsets.all(AppTokens.sp10),
            decoration: BoxDecoration(
              color: AppTokens.errorBg,
              borderRadius: BorderRadius.circular(AppTokens.rMd),
            ),
            child: Row(
              children: [
                const Icon(Icons.error_outline_rounded, size: 18, color: AppTokens.errorText),
                const SizedBox(width: AppTokens.sp6),
                Expanded(child: Text(m.error!, style: ts.caption.copyWith(color: AppTokens.errorText))),
                if (onRetry != null)
                  TextButton(onPressed: onRetry, child: const Text('重试')),
              ],
            ),
          ),
        ),
      if (m.degraded)
        const Padding(
          padding: EdgeInsets.only(top: AppTokens.sp6),
          child: ToneBadge('主模型繁忙，已切换备用', tone: BadgeTone.warning, icon: Icons.bolt_rounded),
        ),
      if (!m.streaming && (m.text.isNotEmpty || m.error != null)) _footer(t),
    ];
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTokens.sp16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const GuduAvatar(size: 30),
          const SizedBox(width: AppTokens.sp8),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: children)),
        ],
      ),
    );
  }

  Widget _footer(AppTokens t) {
    final ts = t.textStyles;
    final id = message.shortTraceId;
    Widget thumb(IconData icon, IconData on, int v, String label) => InkResponse(
          radius: 18,
          onTap: onRate == null ? null : () => onRate!(message.rating == v ? 0 : v),
          child: Padding(
            padding: const EdgeInsets.all(AppTokens.sp4),
            child: Icon(message.rating == v ? on : icon,
                size: 16, color: message.rating == v ? t.primaryDeep : t.body, semanticLabel: label),
          ),
        );
    return Padding(
      padding: const EdgeInsets.only(top: AppTokens.sp6),
      child: Row(
        children: [
          AiBadge(source: AiSource.agent, feedbackRef: message.messageId.startsWith('_') ? null : message.messageId),
          const SizedBox(width: AppTokens.sp6),
          thumb(Icons.thumb_up_alt_outlined, Icons.thumb_up_alt, 1, '有帮助'),
          thumb(Icons.thumb_down_alt_outlined, Icons.thumb_down_alt, -1, '没帮助'),
          if (id != null) ...[
            const SizedBox(width: AppTokens.sp6),
            Flexible(
              child: Text('报错编号 $id',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: ts.meta.copyWith(color: t.body, fontWeight: FontWeight.w500)),
            ),
          ],
        ],
      ),
    );
  }
}

/// 流式文本：生成中末尾显示闪烁光标。
class StreamingText extends StatefulWidget {
  final String text;
  final bool streaming;
  const StreamingText({super.key, required this.text, required this.streaming});

  @override
  State<StreamingText> createState() => _StreamingTextState();
}

class _StreamingTextState extends State<StreamingText> {
  Timer? _blink;
  bool _on = true;

  @override
  void initState() {
    super.initState();
    _sync();
  }

  @override
  void didUpdateWidget(covariant StreamingText old) {
    super.didUpdateWidget(old);
    _sync();
  }

  void _sync() {
    if (widget.streaming && _blink == null) {
      _blink = Timer.periodic(const Duration(milliseconds: 500), (_) {
        if (mounted) setState(() => _on = !_on);
      });
    } else if (!widget.streaming) {
      _blink?.cancel();
      _blink = null;
    }
  }

  @override
  void dispose() {
    _blink?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final style = t.textStyles.body.copyWith(color: t.title, height: 1.6);
    if (widget.text.isEmpty && widget.streaming) {
      return Text('咕嘟正在想…', style: style.copyWith(color: t.body));
    }
    return Text.rich(TextSpan(children: [
      ...LiteMarkdown.spans(widget.text),
      if (widget.streaming)
        WidgetSpan(
          alignment: PlaceholderAlignment.middle,
          child: Opacity(
            opacity: _on ? 1 : 0,
            child: Container(width: 2, height: 16, margin: const EdgeInsets.only(left: 2), color: t.primaryDeep),
          ),
        ),
    ]), style: style);
  }
}

/// 工具调用链（折叠：「查了 3 项 · 用时 1.8 秒」；展开：每步 ✓ 名称 · 摘要 · 耗时）。
class ToolChainView extends StatefulWidget {
  final List<AgentToolCall> tools;
  final bool initiallyExpanded;
  const ToolChainView({super.key, required this.tools, this.initiallyExpanded = true});

  @override
  State<ToolChainView> createState() => _ToolChainViewState();
}

class _ToolChainViewState extends State<ToolChainView> {
  late bool _open = widget.initiallyExpanded;

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final running = widget.tools.any((e) => e.running);
    final totalMs = widget.tools.fold<int>(0, (n, e) => n + e.ms);
    final head = running
        ? '正在${widget.tools.lastWhere((e) => e.running).label}…'
        : '查了 ${widget.tools.length} 项 · 用时 ${(totalMs / 1000).toStringAsFixed(1)} 秒';
    return Container(
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            onTap: () => setState(() => _open = !_open),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10, vertical: AppTokens.sp8),
              child: Row(
                children: [
                  Icon(running ? Icons.autorenew_rounded : Icons.auto_awesome_rounded, size: 16, color: t.primaryDeep),
                  const SizedBox(width: AppTokens.sp6),
                  Expanded(child: Text(head, style: ts.caption.copyWith(color: t.title, fontWeight: FontWeight.w600))),
                  Text(_open ? '收起' : '展开', style: ts.caption.copyWith(color: t.body)),
                  Icon(_open ? Icons.expand_less_rounded : Icons.expand_more_rounded, size: 16, color: t.body),
                ],
              ),
            ),
          ),
          if (_open)
            Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp10, 0, AppTokens.sp10, AppTokens.sp8),
              child: Column(children: [for (final tool in widget.tools) _row(t, tool)]),
            ),
        ],
      ),
    );
  }

  Widget _row(AppTokens t, AgentToolCall tool) {
    final ts = t.textStyles;
    final color = tool.running ? t.primary : (tool.ok ? AppTokens.success : AppTokens.error);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppTokens.sp4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 18,
            height: 18,
            margin: const EdgeInsets.only(top: 1),
            decoration: BoxDecoration(color: color.withAlpha(36), shape: BoxShape.circle),
            child: Icon(
                tool.running ? Icons.more_horiz_rounded : (tool.ok ? Icons.check_rounded : Icons.close_rounded),
                size: 12,
                color: color),
          ),
          const SizedBox(width: AppTokens.sp8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(tool.summary.isEmpty ? tool.label : '${tool.label} · ${tool.summary}',
                    style: ts.caption.copyWith(color: t.title)),
                const SizedBox(height: 2),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp4),
                  decoration: BoxDecoration(color: t.secondary, borderRadius: BorderRadius.circular(AppTokens.rXs)),
                  child: Text(tool.name, style: ts.meta.copyWith(color: t.body, fontFamily: 'monospace')),
                ),
              ],
            ),
          ),
          if (!tool.running) Text('${(tool.ms / 1000).toStringAsFixed(1)}s', style: ts.meta.copyWith(color: t.body)),
        ],
      ),
    );
  }
}

/// 对话内卡片。
class AgentCardView extends StatelessWidget {
  final AgentCard card;
  const AgentCardView({super.key, required this.card});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    switch (card.type) {
      case 'recipes':
        return RecipeCardsRow(dishes: card.recipes);
      case 'menu':
        final covers = Json.strs(card.raw['coverUrls']);
        return _plainCard(
          t,
          onTap: () => context.push('/menu/${Json.i(card.raw['menuId'])}'),
          leading: SizedBox(
            width: 48,
            height: 48,
            child: DishImage(url: covers.isEmpty ? null : covers.first, name: Json.s(card.raw['name']), width: 48, height: 48),
          ),
          title: Json.s(card.raw['name']),
          subtitle: '${Json.s(card.raw['date'])} · ${Json.i(card.raw['dishCount'])} 道菜',
          trailing: Text('查看 ›', style: ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700)),
        );
      case 'pantry':
        return Container(
          padding: const EdgeInsets.all(AppTokens.sp10),
          decoration: BoxDecoration(
            color: t.card,
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            border: Border.all(color: t.border),
          ),
          child: Wrap(
            spacing: AppTokens.sp8,
            runSpacing: AppTokens.sp8,
            children: [
              for (final it in card.pantryItems)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IngredientIcon(name: Json.s(it['name']), iconKey: Json.sOrNull(it['iconKey']), size: 28),
                    const SizedBox(width: AppTokens.sp4),
                    Text(Json.s(it['name']), style: ts.caption.copyWith(color: t.title)),
                    if (Json.iOrNull(it['daysLeft']) != null) ...[
                      const SizedBox(width: AppTokens.sp4),
                      ToneBadge('剩 ${Json.i(it['daysLeft'])} 天',
                          tone: Json.i(it['daysLeft']) <= 1 ? BadgeTone.error : BadgeTone.warning),
                    ],
                  ],
                ),
            ],
          ),
        );
      case 'plan':
        return _plainCard(
          t,
          onTap: () => context.push('/mealplan?draftId=${Json.i(card.raw['draftId'])}'),
          leading: Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(color: t.primaryDeep, borderRadius: BorderRadius.circular(AppTokens.rMd)),
            child: Icon(Icons.calendar_month_rounded, color: t.card),
          ),
          title: '一周餐程 · ${Json.i(card.raw['days'], 7)} 天',
          subtitle: Json.s(card.raw['range']),
          trailing: Text('打开 ›', style: ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700)),
        );
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _plainCard(AppTokens t,
      {required VoidCallback onTap, required Widget leading, required String title, required String subtitle, Widget? trailing}) {
    final ts = t.textStyles;
    return Material(
      color: t.card,
      borderRadius: BorderRadius.circular(AppTokens.rMd),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        onTap: onTap,
        child: Container(
          padding: const EdgeInsets.all(AppTokens.sp10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            border: Border.all(color: t.border),
          ),
          child: Row(
            children: [
              ClipRRect(borderRadius: BorderRadius.circular(AppTokens.rSm), child: leading),
              const SizedBox(width: AppTokens.sp10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: ts.cardTitle.copyWith(color: t.title)),
                    Text(subtitle, style: ts.caption),
                  ],
                ),
              ),
              if (trailing != null) trailing,
            ],
          ),
        ),
      ),
    );
  }
}

/// 菜谱卡横滑（图 + 菜名 + 家里够/缺几样 + 用时）。
class RecipeCardsRow extends StatelessWidget {
  final List<DishCard> dishes;
  final double width;
  const RecipeCardsRow({super.key, required this.dishes, this.width = 150});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return SizedBox(
      height: width * 0.75 + 78,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: dishes.length,
        separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp10),
        itemBuilder: (_, i) {
          final d = dishes[i];
          final cov = d.coverageText;
          return Material(
            color: t.card,
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: () => context.push('/dish/${d.id}'),
              child: Container(
                width: width,
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  border: Border.all(color: t.border),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    DishImage(url: d.coverUrl, name: d.name, width: width, height: width * 0.75, radius: 0),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(AppTokens.sp8, AppTokens.sp6, AppTokens.sp8, 0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(d.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.cardTitle.copyWith(color: t.title)),
                          const SizedBox(height: AppTokens.sp4),
                          if (cov != null)
                            ToneBadge(cov, tone: d.fullyCovered ? BadgeTone.success : BadgeTone.warning)
                          else if (d.tags.isNotEmpty)
                            ToneBadge(d.tags.first, tone: BadgeTone.info),
                          const SizedBox(height: AppTokens.sp4),
                          Text(
                            [if (d.minutes != null) '${d.minutes} 分钟', ...d.tags.take(1)].join(' · '),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: ts.caption,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

/// 待确认动作卡：PENDING 显示预览 + 有效期倒计时 + 取消/确认；DONE 显示结果 + 撤销。
class ActionCardView extends StatefulWidget {
  final PendingAction action;
  final ActionChanged onChanged;
  const ActionCardView({super.key, required this.action, required this.onChanged});

  @override
  State<ActionCardView> createState() => _ActionCardViewState();
}

class _ActionCardViewState extends State<ActionCardView> {
  Timer? _tick;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    if (widget.action.isPending && widget.action.expiresAt != null) {
      _tick = Timer.periodic(const Duration(seconds: 1), (_) {
        if (!mounted) return;
        if (widget.action.expiredAt(DateTime.now())) {
          _tick?.cancel();
          widget.onChanged(widget.action.copyWith(status: 'EXPIRED'));
        } else {
          setState(() {});
        }
      });
    }
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  Future<void> _run(Future<PendingAction> Function() f, String failText) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final next = await f();
      _tick?.cancel();
      widget.onChanged(next);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$failText：${errorText(e)}')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// 改一下（mark_cooked：人份 / 谁吃了 / 实际用量）→ revise → 新卡原位替换旧卡。
  Future<void> _revise() async {
    final a = widget.action;
    final args = await showCookedReviseSheet(context, a);
    if (args == null || !mounted) return;
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final next = await AgentService.revise(a, args);
      _tick?.cancel();
      widget.onChanged(next, replacing: a.actionId);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('修改失败：${errorText(e)}')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _undo() async {
    final token = widget.action.undoToken;
    if (token == null) return;
    await _run(() async {
      await AgentService.undo(token);
      return widget.action.copyWith(status: 'UNDONE');
    }, '撤销失败');
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final a = widget.action;
    final pending = a.isPending;
    final remain = a.expiresAt?.difference(DateTime.now());
    final statusText = switch (a.status) {
      'DONE' => '已完成',
      'CANCELLED' => '已取消',
      'EXPIRED' => '已过期，请重新发起',
      'UNDONE' => '已撤销',
      'SUPERSEDED' => '已按修改重新生成',
      _ => remain == null
          ? '确认后才会执行'
          : '5 分钟内有效 · 剩 ${remain.inMinutes}:${(remain.inSeconds % 60).toString().padLeft(2, '0')}',
    };
    return Container(
      decoration: BoxDecoration(
        color: pending ? t.highlight : t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: pending ? t.primary.withAlpha(120) : t.border),
      ),
      padding: const EdgeInsets.all(AppTokens.sp12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Icon(_iconFor(a.tool), size: 18, color: t.primaryDeep),
              const SizedBox(width: AppTokens.sp6),
              Expanded(child: Text(a.title, style: ts.cardTitle.copyWith(color: t.title))),
              if (a.status == 'DONE') const ToneBadge('已执行', tone: BadgeTone.success, icon: Icons.check_rounded),
            ],
          ),
          const SizedBox(height: AppTokens.sp8),
          if (a.tool == 'mark_cooked') CookedPreview(action: a) else _namesPreview(t, a),
          const SizedBox(height: AppTokens.sp8),
          Row(
            children: [
              Expanded(child: Text(statusText, style: ts.caption)),
              if (pending) ...[
                if (a.tool == 'mark_cooked')
                  TextButton(
                    key: Key('action-revise-${a.actionId}'),
                    onPressed: _busy ? null : _revise,
                    child: const Text('改一下'),
                  ),
                TextButton(
                  key: Key('action-cancel-${a.actionId}'),
                  onPressed: _busy ? null : () => _run(() => AgentService.cancel(a), '取消失败'),
                  child: const Text('取消'),
                ),
                const SizedBox(width: AppTokens.sp4),
                ElevatedButton(
                  key: Key('action-confirm-${a.actionId}'),
                  onPressed: _busy || a.expiredAt(DateTime.now()) ? null : () => _run(() => AgentService.confirm(a), '执行失败'),
                  style: ElevatedButton.styleFrom(minimumSize: const Size(72, 36)),
                  child: Text(_busy ? '执行中…' : '确认'),
                ),
              ] else if (a.status == 'DONE' && a.undoToken != null)
                TextButton.icon(
                  key: Key('action-undo-${a.actionId}'),
                  onPressed: _busy ? null : _undo,
                  icon: const Icon(Icons.undo_rounded, size: 16),
                  label: const Text('撤销'),
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _namesPreview(AppTokens t, PendingAction a) {
    final names = a.previewNames;
    if (names.isEmpty) return const SizedBox.shrink();
    return Wrap(
      spacing: AppTokens.sp6,
      runSpacing: AppTokens.sp6,
      children: [
        for (final n in names)
          Container(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp4, AppTokens.sp2, AppTokens.sp8, AppTokens.sp2),
            decoration: BoxDecoration(
              color: t.card,
              borderRadius: BorderRadius.circular(AppTokens.rPill),
              border: Border.all(color: t.border),
            ),
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              IngredientIcon(name: n, size: 22),
              const SizedBox(width: AppTokens.sp4),
              Text(n, style: t.textStyles.caption.copyWith(color: t.title)),
            ]),
          ),
      ],
    );
  }

  IconData _iconFor(String tool) => switch (tool) {
        'add_to_menu' => Icons.playlist_add_rounded,
        'add_to_shopping' => Icons.shopping_cart_outlined,
        'mark_cooked' => Icons.local_fire_department_rounded,
        'add_pantry' => Icons.kitchen_outlined,
        _ => Icons.task_alt_rounded,
      };
}

/// 做完回写预览：成品图、人份、谁吃了、用料去向。
class CookedPreview extends StatelessWidget {
  final PendingAction action;
  const CookedPreview({super.key, required this.action});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final dish = action.cookedDish;
    final eaters = action.eaters;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (dish != null)
          Row(
            children: [
              DishImage(url: dish.coverUrl, name: dish.name, width: 52, height: 52, radius: AppTokens.rSm),
              const SizedBox(width: AppTokens.sp10),
              Expanded(child: Text(dish.name, style: ts.cardTitle.copyWith(color: t.title))),
              Text('${action.servings} 人份', style: ts.caption.copyWith(color: t.title, fontWeight: FontWeight.w700)),
            ],
          ),
        if (eaters.isNotEmpty) ...[
          const SizedBox(height: AppTokens.sp10),
          Text('谁吃了', style: ts.sectionLabel.copyWith(color: t.body)),
          const SizedBox(height: AppTokens.sp6),
          Wrap(
            spacing: AppTokens.sp12,
            children: [
              for (final e in eaters)
                Column(mainAxisSize: MainAxisSize.min, children: [
                  MemberAvatar(name: e.name, avatarUrl: e.avatarUrl, size: 36, ringColor: AppTokens.success),
                  const SizedBox(height: 2),
                  Text(e.name, style: ts.meta.copyWith(color: t.title)),
                ]),
            ],
          ),
        ],
        if (action.lines.isNotEmpty) ...[
          const SizedBox(height: AppTokens.sp10),
          Text('用料去向', style: ts.sectionLabel.copyWith(color: t.body)),
          for (final l in action.lines)
            Padding(
              padding: const EdgeInsets.only(top: AppTokens.sp6),
              child: Row(
                children: [
                  IngredientIcon(name: l.name, iconKey: l.iconKey, size: 30, depleted: l.status == 'USED_UP'),
                  const SizedBox(width: AppTokens.sp8),
                  Expanded(
                    child: Text(l.after.isEmpty ? l.name : '${l.name} · 剩 ${l.after}',
                        style: ts.caption.copyWith(color: t.title)),
                  ),
                  Text(l.status == 'SKIP' ? '不扣' : l.delta,
                      style: ts.caption.copyWith(
                          color: l.status == 'USED_UP' ? AppTokens.errorText : t.title, fontWeight: FontWeight.w700)),
                  if (l.status == 'USED_UP') ...[
                    const SizedBox(width: AppTokens.sp4),
                    const ToneBadge('用完了', tone: BadgeTone.error),
                  ],
                ],
              ),
            ),
        ],
      ],
    );
  }
}

/// 页面底部输入栏（文字 + 相机 + 语音 / 发送）。
class AgentInputBar extends StatelessWidget {
  final TextEditingController? controller;
  final String hint;
  final bool readOnly;
  final VoidCallback? onTapField;
  final ValueChanged<String>? onSubmit;
  final VoidCallback? onCamera;
  final VoidCallback? onVoice;
  final bool sending;

  const AgentInputBar({
    super.key,
    this.controller,
    this.hint = '问咕嘟：今晚吃什么、帮我加采购…',
    this.readOnly = false,
    this.onTapField,
    this.onSubmit,
    this.onCamera,
    this.onVoice,
    this.sending = false,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Container(
      padding: const EdgeInsets.fromLTRB(AppTokens.sp12, AppTokens.sp6, AppTokens.sp6, AppTokens.sp6),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rPill),
        border: Border.all(color: t.border),
        boxShadow: t.elevationMd,
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              key: const Key('agent-input'),
              controller: controller,
              readOnly: readOnly,
              onTap: onTapField,
              enabled: !sending,
              textInputAction: TextInputAction.send,
              onSubmitted: onSubmit,
              style: ts.input,
              decoration: InputDecoration(
                isDense: true,
                hintText: hint,
                hintStyle: ts.caption,
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                disabledBorder: InputBorder.none,
                filled: false,
                contentPadding: const EdgeInsets.symmetric(vertical: AppTokens.sp8),
              ),
            ),
          ),
          if (onCamera != null)
            IconButton(
              tooltip: '拍照识别',
              onPressed: onCamera,
              icon: Icon(Icons.photo_camera_outlined, color: t.body),
            ),
          if (onSubmit != null && controller != null)
            ValueListenableBuilder<TextEditingValue>(
              valueListenable: controller!,
              builder: (_, v, __) => v.text.trim().isEmpty && onVoice != null
                  ? _roundBtn(t, Icons.mic_rounded, '语音', onVoice)
                  : _roundBtn(t, Icons.arrow_upward_rounded, '发送', sending ? null : () => onSubmit!(v.text)),
            )
          else if (onVoice != null)
            _roundBtn(t, Icons.mic_rounded, '语音', onVoice),
        ],
      ),
    );
  }

  Widget _roundBtn(AppTokens t, IconData icon, String tip, VoidCallback? onTap) => Semantics(
        button: true,
        label: tip,
        child: InkResponse(
          onTap: onTap,
          child: Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(color: onTap == null ? t.border : t.primaryDeep, shape: BoxShape.circle),
            child: Icon(icon, color: t.card, size: 22),
          ),
        ),
      );
}

/// 做完回写「改一下」：人份步进、谁吃了（家庭成员多选）、每样用料的实际去向与用量。
/// 返回 revise 的 args；取消返回 null。
Future<Map<String, dynamic>?> showCookedReviseSheet(BuildContext context, PendingAction action) {
  return showModalBottomSheet<Map<String, dynamic>>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => _CookedReviseSheet(action: action),
  );
}

class _CookedReviseSheet extends StatefulWidget {
  final PendingAction action;
  const _CookedReviseSheet({required this.action});

  @override
  State<_CookedReviseSheet> createState() => _CookedReviseSheetState();
}

class _CookedReviseSheetState extends State<_CookedReviseSheet> {
  late final CookedRevision _r = CookedRevision.from(widget.action);
  late final List<TextEditingController> _amounts = [for (final l in _r.lines) TextEditingController(text: l.amount)];

  @override
  void dispose() {
    for (final c in _amounts) {
      c.dispose();
    }
    super.dispose();
  }

  /// 可选的人：家庭成员（Provider）∪ 预览里已有的人。
  List<MemberLite> _people() {
    final out = <int, MemberLite>{for (final e in widget.action.eaters) e.memberId: e};
    try {
      for (final m in context.read<MemberStore>().members) {
        out.putIfAbsent(m.id, () => MemberLite(memberId: m.id, name: m.name, avatarUrl: m.avatarUrl));
      }
    } catch (_) {}
    return out.values.toList();
  }

  void _setStatus(int i, String status) {
    final l = _r.lines[i];
    setState(() => _r.lines[i] =
        (name: l.name, ingredientId: l.ingredientId, iconKey: l.iconKey, status: status, amount: _amounts[i].text.trim()));
  }

  Map<String, dynamic> _result() {
    for (var i = 0; i < _r.lines.length; i++) {
      final l = _r.lines[i];
      _r.lines[i] = (name: l.name, ingredientId: l.ingredientId, iconKey: l.iconKey, status: l.status, amount: _amounts[i].text.trim());
    }
    return _r.toArgs();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final people = _people();
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.85),
        child: ListView(
          shrinkWrap: true,
          padding: const EdgeInsets.fromLTRB(AppTokens.sp16, 0, AppTokens.sp16, AppTokens.sp24),
          children: [
            Text('改一下 · ${widget.action.cookedDish?.name ?? widget.action.title}', style: ts.subtitle),
            const SizedBox(height: AppTokens.sp16),
            Row(children: [
              Expanded(child: Text('做了几人份', style: ts.cardTitle.copyWith(color: t.title))),
              IconButton(
                key: const Key('revise-servings-minus'),
                tooltip: '减少',
                onPressed: _r.servings > 1 ? () => setState(() => _r.servings--) : null,
                icon: const Icon(Icons.remove_circle_outline_rounded),
              ),
              SizedBox(width: 32, child: Text('${_r.servings}', textAlign: TextAlign.center, style: ts.h3)),
              IconButton(
                key: const Key('revise-servings-plus'),
                tooltip: '增加',
                onPressed: () => setState(() => _r.servings++),
                icon: const Icon(Icons.add_circle_outline_rounded),
              ),
            ]),
            if (people.isNotEmpty) ...[
              const SizedBox(height: AppTokens.sp8),
              Text('谁吃了', style: ts.sectionLabel.copyWith(color: t.body)),
              const SizedBox(height: AppTokens.sp8),
              Wrap(
                spacing: AppTokens.sp16,
                runSpacing: AppTokens.sp8,
                children: [
                  for (final p in people)
                    InkWell(
                      key: Key('revise-eater-${p.memberId}'),
                      borderRadius: BorderRadius.circular(AppTokens.rMd),
                      onTap: () => setState(() =>
                          _r.eaterIds.contains(p.memberId) ? _r.eaterIds.remove(p.memberId) : _r.eaterIds.add(p.memberId)),
                      child: Column(mainAxisSize: MainAxisSize.min, children: [
                        Opacity(
                          opacity: _r.eaterIds.contains(p.memberId) ? 1 : 0.45,
                          child: MemberAvatar(
                            name: p.name,
                            avatarUrl: p.avatarUrl,
                            size: 44,
                            ringColor: _r.eaterIds.contains(p.memberId) ? AppTokens.success : t.border,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(p.name, style: ts.caption.copyWith(color: t.title)),
                      ]),
                    ),
                ],
              ),
            ],
            if (_r.lines.isNotEmpty) ...[
              const SizedBox(height: AppTokens.sp16),
              Text('用料实际去向', style: ts.sectionLabel.copyWith(color: t.body)),
              for (var i = 0; i < _r.lines.length; i++) _lineRow(t, i),
            ],
            const SizedBox(height: AppTokens.sp16),
            ElevatedButton(
              key: const Key('revise-save'),
              onPressed: () => Navigator.of(context).pop(_result()),
              child: const Text('按这个重新生成'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _lineRow(AppTokens t, int i) {
    final ts = t.textStyles;
    final l = _r.lines[i];
    Widget seg(String status, String label) {
      final on = l.status == status;
      return Padding(
        padding: const EdgeInsets.only(right: AppTokens.sp4),
        child: ChoiceChip(
          key: Key('revise-line-$i-$status'),
          label: Text(label, style: ts.caption.copyWith(color: on ? t.primaryDeep : t.title)),
          selected: on,
          onSelected: (_) => _setStatus(i, status),
          selectedColor: t.primarySoft,
          showCheckmark: false,
          visualDensity: VisualDensity.compact,
          side: BorderSide(color: on ? t.primaryDeep : t.border),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(top: AppTokens.sp10),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            IngredientIcon(name: l.name, iconKey: l.iconKey, size: 30),
            const SizedBox(width: AppTokens.sp8),
            Expanded(child: Text(l.name, style: ts.cardTitle.copyWith(color: t.title))),
            if (l.status != 'SKIP')
              SizedBox(
                width: 96,
                child: TextField(
                  key: Key('revise-amount-$i'),
                  controller: _amounts[i],
                  textAlign: TextAlign.right,
                  style: ts.body.copyWith(color: t.title),
                  decoration: const InputDecoration(isDense: true, hintText: '用量'),
                ),
              ),
          ]),
          const SizedBox(height: AppTokens.sp4),
          Row(children: [seg('USED_UP', '用完了'), seg('LEFT', '用了一些'), seg('SKIP', '没用到')]),
        ],
      ),
    );
  }
}
