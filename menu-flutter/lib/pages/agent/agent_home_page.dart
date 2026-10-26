import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../core/app_theme.dart';
import '../../core/ingredient_icons.dart';
import '../../models/member.dart';
import '../../services/agent_service.dart';
import '../../stores/auth_store.dart';
import '../../stores/member_store.dart';
import '../../widgets/ai_badge.dart';
import '../../widgets/dish_image.dart';
import '../../widgets/ingredient_icon.dart';
import '../../widgets/me_avatar_button.dart';
import '../../widgets/member_avatar.dart';
import '../../widgets/skeleton.dart';
import 'agent_widgets.dart';

/// 咕嘟首屏（原型 Agent.html，底部 Tab 中心）：问候 → 今晚首推大图卡 → 快到期提醒 →
/// 快捷提问 → 最近会话；底部输入栏点击进入对话页。
class AgentHomePage extends StatefulWidget {
  const AgentHomePage({super.key});

  @override
  State<AgentHomePage> createState() => _AgentHomePageState();
}

class _AgentHomePageState extends State<AgentHomePage> {
  AgentSuggestions? _s;
  List<AgentSessionItem> _sessions = const [];
  bool _loading = true;
  bool _offline = false;

  bool _precached = false;

  /// 快到期条与首推占位用到食材插画：首屏前预解码，避免第一次显示为空白方块。
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_precached) return;
    _precached = true;
    for (final k in IngredientIcons.keys) {
      precacheImage(AssetImage(IngredientIcons.asset(k)), context);
    }
  }

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    AgentSuggestions? s;
    var offline = false;
    try {
      s = await AgentService.suggestions();
    } catch (e) {
      offline = isNotDeployed(e);
    }
    List<AgentSessionItem> sessions = const [];
    try {
      sessions = (await AgentService.sessions(size: 3)).items;
    } catch (_) {}
    if (!mounted) return;
    setState(() {
      _s = s;
      _sessions = sessions;
      _offline = offline;
      _loading = false;
    });
  }

  void _ask(String prompt) {
    if (prompt.contains('拍冰箱') || prompt.contains('拍照')) {
      context.push('/scan');
      return;
    }
    context.push('/agent/chat', extra: {'text': prompt});
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    List<Member> members = const [];
    String nickname = '';
    try {
      members = context.watch<MemberStore>().members;
      nickname = context.watch<AuthStore>().nickname;
    } catch (_) {}
    final s = _s;
    final prompts = (s?.prompts.isNotEmpty ?? false) ? s!.prompts : AgentSuggestions.defaultPrompts;

    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Stack(
          children: [
            RefreshIndicator(
              color: t.primary,
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, 96),
                children: [
                  _header(t, members, nickname),
                  const SizedBox(height: AppTokens.sp12),
                  _greeting(t, s),
                  const SizedBox(height: AppTokens.sp8),
                  // AI 身份说明（A.4）
                  const AiAssistantIntro(),
                  const SizedBox(height: AppTokens.sp20),
                  Row(
                    children: [
                      Expanded(child: Text('今晚首推', style: ts.subtitle)),
                      InkWell(
                        onTap: () => context.push('/ai-recommend'),
                        child: Text('全部推荐 ›', style: ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700)),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppTokens.sp10),
                  if (_loading)
                    const SkeletonBox(height: 220, radius: AppTokens.rLg)
                  else if (s?.tonight != null)
                    _tonightCard(t, s!)
                  else
                    _emptyTonight(t),
                  if ((s?.expiring ?? const []).isNotEmpty) ...[
                    const SizedBox(height: AppTokens.sp12),
                    _expiring(t, s!),
                  ],
                  const SizedBox(height: AppTokens.sp20),
                  Row(children: [
                    Expanded(child: Text('试试这样问', style: ts.subtitle)),
                    Text('说或打字都行', style: ts.caption),
                  ]),
                  const SizedBox(height: AppTokens.sp10),
                  _prompts(t, prompts),
                  if (_sessions.isNotEmpty) ...[
                    const SizedBox(height: AppTokens.sp20),
                    Text('最近的对话', style: ts.subtitle),
                    const SizedBox(height: AppTokens.sp8),
                    for (final it in _sessions) _sessionRow(t, it),
                  ],
                ],
              ),
            ),
            Positioned(
              left: AppTokens.sp16,
              right: AppTokens.sp16,
              bottom: AppTokens.sp12,
              child: AgentInputBar(
                readOnly: true,
                onTapField: () => context.push('/agent/chat'),
                onCamera: () => context.push('/scan'),
                onVoice: () => context.push('/agent/voice'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _header(AppTokens t, List<Member> members, String nickname) {
    final ts = t.textStyles;
    final now = DateTime.now();
    const week = ['一', '二', '三', '四', '五', '六', '日'];
    final part = now.hour < 10 ? '早上好' : now.hour < 14 ? '中午好' : now.hour < 18 ? '下午好' : '晚上好';
    final meal = now.hour < 10 ? '早饭' : now.hour < 14 ? '午饭' : '今晚';
    final hm = '${now.hour}:${now.minute.toString().padLeft(2, '0')}';
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('$part${nickname.isEmpty ? '' : '，$nickname'} · 周${week[now.weekday - 1]} $hm', style: ts.caption),
              const SizedBox(height: AppTokens.sp4),
              Row(children: [
                Text(members.isEmpty ? '$meal吃什么' : '$meal ${members.length} 口人', style: ts.h2),
                if (members.isNotEmpty) ...[
                  const SizedBox(width: AppTokens.sp8),
                  AvatarStack(size: 24, people: [for (final m in members) (name: m.name, avatarUrl: m.avatarUrl)]),
                ],
              ]),
            ],
          ),
        ),
        IconButton(
          tooltip: '消息',
          onPressed: () => context.push('/notices'),
          icon: Icon(Icons.notifications_none_rounded, color: t.title),
        ),
        const MeAvatarButton(),
      ],
    );
  }

  Widget _greeting(AppTokens t, AgentSuggestions? s) {
    final ts = t.textStyles;
    final text = _offline
        ? '咕嘟的大脑还在部署中，先看看今天的推荐吧。下面的快捷提问上线后就能直接用。'
        : (s?.greeting.isNotEmpty ?? false)
            ? s!.greeting
            : '我看了下家里的余量，有想吃的直接告诉我，或者点下面的问题试试。';
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const GuduAvatar(size: 34),
        const SizedBox(width: AppTokens.sp10),
        Expanded(
          child: Container(
            padding: const EdgeInsets.all(AppTokens.sp12),
            decoration: BoxDecoration(
              color: t.card,
              borderRadius: const BorderRadius.only(
                topLeft: Radius.circular(AppTokens.rXs),
                topRight: Radius.circular(AppTokens.rLg),
                bottomLeft: Radius.circular(AppTokens.rLg),
                bottomRight: Radius.circular(AppTokens.rLg),
              ),
              border: Border.all(color: t.border),
            ),
            child: Text(text, style: ts.body.copyWith(color: t.title, height: 1.55)),
          ),
        ),
      ],
    );
  }

  Widget _tonightCard(AppTokens t, AgentSuggestions s) {
    final ts = t.textStyles;
    final d = s.tonight!;
    final cov = d.coverageText;
    return ClipRRect(
      borderRadius: BorderRadius.circular(AppTokens.rLg),
      child: SizedBox(
        height: 236,
        child: Stack(
          fit: StackFit.expand,
          children: [
            GestureDetector(
              onTap: () => context.push('/dish/${d.id}'),
              child: (d.coverUrl ?? '').isEmpty
                  ? _noCover(t, d.name, s)
                  : DishImage(url: d.coverUrl, name: d.name, radius: 0, original: true),
            ),
            const DecoratedBox(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [Colors.transparent, AppTokens.photoScrimStrong],
                  stops: [0.35, 1],
                ),
              ),
            ),
            Positioned(
              left: AppTokens.sp12,
              top: AppTokens.sp12,
              child: Row(children: [
                if (s.expiring.isNotEmpty)
                  _pill(t, '${s.expiring.first.name}${s.expiring.first.daysLeft <= 1 ? '明天到期' : '快到期'}',
                      AppTokens.error, AppTokens.onPhoto),
              ]),
            ),
            Positioned(
              right: AppTokens.sp12,
              top: AppTokens.sp12,
              child: _pill(t, '咕嘟推荐', AppTokens.onPhoto, t.title, icon: Icons.auto_awesome_rounded),
            ),
            Positioned(
              left: AppTokens.sp16,
              right: AppTokens.sp16,
              bottom: AppTokens.sp12,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(d.name, style: ts.h2.copyWith(color: AppTokens.onPhoto)),
                  const SizedBox(height: AppTokens.sp4),
                  Text(
                    [
                      if (d.minutes != null) '${d.minutes} 分钟',
                      ...d.tags.take(2),
                      if (cov != null) cov,
                    ].join(' · '),
                    style: ts.caption.copyWith(color: AppTokens.onPhoto),
                  ),
                  const SizedBox(height: AppTokens.sp10),
                  Row(
                    children: [
                      Expanded(
                        child: ElevatedButton(
                          key: const Key('agent-tonight-cook'),
                          onPressed: () => context.push('/dish/${d.id}'),
                          child: const Text('现在就做'),
                        ),
                      ),
                      const SizedBox(width: AppTokens.sp10),
                      OutlinedButton.icon(
                        key: const Key('agent-tonight-swap'),
                        // 深色渐变底上的次按钮：半透明白底 + 白字（主题默认 OutlinedButton 是白底，会把白字盖掉）
                        style: OutlinedButton.styleFrom(
                          minimumSize: const Size(0, 48),
                          backgroundColor: AppTokens.onPhoto.withAlpha(46),
                          foregroundColor: AppTokens.onPhoto,
                          side: BorderSide(color: AppTokens.onPhoto.withAlpha(150)),
                        ),
                        onPressed: _load,
                        icon: const Icon(Icons.refresh_rounded, size: 18),
                        label: const Text('换一道'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 首推菜没有封面时：主色渐变 + 食材插画拼贴（不用巨大首字）。
  /// 插画取菜名里能识别的食材，再补快到期食材，最多 3 个。
  Widget _noCover(AppTokens t, String name, AgentSuggestions s) {
    final keys = <String>[];
    void add(String? k) {
      if (k != null && !keys.contains(k) && keys.length < 3) keys.add(k);
    }
    for (final ch in IngredientIcons.keywords) {
      if (ch.$2.any(name.contains)) add(ch.$1);
    }
    for (final e in s.expiring) {
      add(IngredientIcons.resolve(iconKey: e.iconKey, name: e.name));
    }
    if (keys.isEmpty) add('egg');
    return DecoratedBox(
      key: const Key('agent-tonight-nocover'),
      decoration: BoxDecoration(gradient: t.primaryGradient),
      child: Align(
        alignment: const Alignment(0.55, -0.35),
        child: Wrap(
          spacing: AppTokens.sp10,
          children: [
            for (final k in keys)
              Container(
                width: 68,
                height: 68,
                decoration: BoxDecoration(
                  color: AppTokens.onPhoto.withAlpha(235),
                  shape: BoxShape.circle,
                  boxShadow: t.elevationLg,
                ),
                alignment: Alignment.center,
                child: Image.asset(IngredientIcons.asset(k), width: 44, height: 44),
              ),
          ],
        ),
      ),
    );
  }

  Widget _pill(AppTokens t, String text, Color bg, Color fg, {IconData? icon}) => Container(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AppTokens.rPill)),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          if (icon != null) ...[Icon(icon, size: 12, color: fg), const SizedBox(width: AppTokens.sp4)],
          Text(text, style: t.textStyles.xs.copyWith(color: fg)),
        ]),
      );

  Widget _emptyTonight(AppTokens t) {
    final ts = t.textStyles;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp16),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: t.border),
      ),
      child: Row(
        children: [
          Expanded(child: Text('还没有今晚的推荐，看看全部推荐，或者直接问咕嘟。', style: ts.body)),
          TextButton(onPressed: () => context.push('/ai-recommend'), child: const Text('全部推荐')),
        ],
      ),
    );
  }

  Widget _expiring(AppTokens t, AgentSuggestions s) {
    final ts = t.textStyles;
    final names = s.expiring.map((e) => e.name).toList();
    final rest = s.expiring.length > 1 ? s.expiring.skip(1).toList() : s.expiring;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp10),
      decoration: BoxDecoration(
        color: AppTokens.warningBg,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: AppTokens.warning.withAlpha(90)),
      ),
      child: Row(
        children: [
          for (final e in rest.take(2))
            Padding(
              padding: const EdgeInsets.only(right: AppTokens.sp4),
              child: IngredientIcon(name: e.name, iconKey: e.iconKey, size: 30),
            ),
          const SizedBox(width: AppTokens.sp4),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('还有 ${rest.length} 样快到期', style: ts.cardTitle.copyWith(color: AppTokens.warningText)),
                Text(
                  rest.map((e) => '${e.name}剩 ${e.daysLeft} 天').join('、'),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: ts.caption.copyWith(color: AppTokens.warningText),
                ),
              ],
            ),
          ),
          ElevatedButton.icon(
            style: ElevatedButton.styleFrom(minimumSize: const Size(0, 36), padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10)),
            onPressed: () => _ask('${names.join('、')}快到期了，今晚怎么做掉？'),
            icon: const Icon(Icons.auto_awesome_rounded, size: 16),
            label: const Text('问咕嘟怎么做'),
          ),
        ],
      ),
    );
  }

  Widget _prompts(AppTokens t, List<String> prompts) {
    final ts = t.textStyles;
    const icons = [Icons.kitchen_outlined, Icons.child_care_rounded, Icons.groups_2_outlined, Icons.photo_camera_outlined];
    return LayoutBuilder(builder: (_, c) {
      final w = (c.maxWidth - AppTokens.sp10) / 2;
      return Wrap(
        spacing: AppTokens.sp10,
        runSpacing: AppTokens.sp10,
        children: [
          for (var i = 0; i < prompts.length && i < 6; i++)
            SizedBox(
              width: w,
              child: Material(
                color: t.card,
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                child: InkWell(
                  key: Key('agent-prompt-$i'),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  onTap: () => _ask(prompts[i]),
                  child: Container(
                    height: 64,
                    padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(AppTokens.rMd),
                      border: Border.all(color: t.border),
                    ),
                    child: Row(
                      children: [
                        Container(
                          width: 32,
                          height: 32,
                          decoration: BoxDecoration(color: t.secondary, borderRadius: BorderRadius.circular(AppTokens.rSm)),
                          child: Icon(icons[i % icons.length], size: 18, color: t.primaryDeep),
                        ),
                        const SizedBox(width: AppTokens.sp8),
                        Expanded(
                          child: Text(prompts[i], maxLines: 2, overflow: TextOverflow.ellipsis,
                              style: ts.caption.copyWith(color: t.title, fontWeight: FontWeight.w600)),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      );
    });
  }

  Widget _sessionRow(AppTokens t, AgentSessionItem it) {
    final ts = t.textStyles;
    return InkWell(
      onTap: () => context.push('/agent/chat', extra: {'sessionId': it.sessionId}),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: AppTokens.sp8),
        child: Row(
          children: [
            it.coverUrl != null
                ? DishImage(url: it.coverUrl, name: it.title, width: 44, height: 44, radius: AppTokens.rSm)
                : Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(color: t.secondary, borderRadius: BorderRadius.circular(AppTokens.rSm)),
                    child: Icon(Icons.chat_bubble_outline_rounded, color: t.primaryDeep, size: 20),
                  ),
            const SizedBox(width: AppTokens.sp10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(it.title.isEmpty ? '对话' : it.title, maxLines: 1, overflow: TextOverflow.ellipsis,
                      style: ts.cardTitle.copyWith(color: t.title)),
                  Text(it.lastMessage, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.caption),
                ],
              ),
            ),
            Icon(Icons.chevron_right_rounded, color: t.body),
          ],
        ),
      ),
    );
  }
}
