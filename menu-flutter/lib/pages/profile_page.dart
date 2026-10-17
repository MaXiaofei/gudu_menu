import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../core/constants.dart';
import '../core/theme_controller.dart';
import '../models/page.dart';
import '../services/dish_service.dart';
import '../services/me_service.dart';
import '../services/recommend_service.dart';
import '../stores/auth_store.dart';
import '../stores/member_store.dart';
import '../widgets/app_card.dart';
import '../widgets/dish_image.dart';
import '../widgets/member_avatar.dart';
import '../widgets/stat_grid.dart';

/// 「我的」Tab（原型 Me）：渐变头部 + 大头像 → 三格统计 → 最近做的照片条 → 分组设置 → 退出登录。
class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  MeStats? _stats;
  List<RecentCooked> _recent = const [];
  /// 草稿数（只在进页时拉一次；放 build 里会每次重建都发请求）。
  late final Future<PageData<DishDraftItem>> _drafts = DishService.listDrafts();

  @override
  void initState() {
    super.initState();
    _loadExtras();
  }

  /// 统计与最近做的各自失败不影响页面（旧后端无 /me/stats 时隐藏统计）。
  Future<void> _loadExtras() async {
    final stats = await MeService.stats();
    List<RecentCooked> recent = const [];
    try {
      recent = await RecommendService.recent(limit: 6);
    } catch (_) {
      recent = const [];
    }
    if (!mounted) return;
    setState(() {
      _stats = stats;
      _recent = recent;
    });
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final auth = context.watch<AuthStore>();
    final member = context.watch<MemberStore>();
    final name = auth.nickname.isNotEmpty ? auth.nickname : '掌勺人';
    final top = MediaQuery.of(context).padding.top;
    return Scaffold(
      backgroundColor: t.bg,
      body: ListView(
        padding: EdgeInsets.zero,
        children: [
          // 渐变头部：大头像 + 名字 + 当前就餐成员（点进家庭成员切换）
          Container(
            padding: EdgeInsets.fromLTRB(AppTokens.sp16, top + AppTokens.sp4, AppTokens.sp16, AppTokens.sp24),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [t.primarySoft, t.bg],
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 「我的」改为二级页（主 Tab 右上角头像进入）：顶部返回
                SizedBox(
                  height: 44,
                  child: Row(children: [
                    if (Navigator.of(context).canPop())
                      IconButton(
                        tooltip: '返回',
                        padding: EdgeInsets.zero,
                        onPressed: () => Navigator.of(context).maybePop(),
                        icon: Icon(Icons.arrow_back_rounded, color: t.title),
                      ),
                    Text('我的', style: ts.pageTitle),
                  ]),
                ),
                const SizedBox(height: AppTokens.sp12),
                Row(
              children: [
                MemberAvatar(name: name, size: 68, ringColor: t.card),
                const SizedBox(width: AppTokens.sp16),
                Expanded(
                  child: GestureDetector(
                    onTap: () => context.push('/members'),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(name, style: ts.h3),
                        const SizedBox(height: AppTokens.sp4),
                        Text(
                          member.currentName.isNotEmpty
                              ? '当前就餐：${member.currentName}'
                              : '当前就餐：$name',
                          style: ts.caption,
                        ),
                      ],
                    ),
                  ),
                ),
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    minimumSize: const Size(0, 36),
                    padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12),
                    shape: const StadiumBorder(),
                  ),
                  onPressed: () => context.push('/profile/edit'),
                  child: const Text('编辑资料'),
                ),
              ],
            ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, 0, AppTokens.sp16, AppTokens.sp32),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (_stats != null) ...[
                  StatGrid(items: [
                    StatItem('${_stats!.cookedThisMonth}', '本月做饭'),
                    StatItem('${_stats!.familyDishes}', '家庭菜谱'),
                    StatItem('${_stats!.savedFromWaste}', '本月少扔', tone: StatTone.success),
                  ]),
                  const SizedBox(height: AppTokens.sp16),
                ],
                if (_recent.isNotEmpty) ...[
                  _RecentStrip(recent: _recent),
                  const SizedBox(height: AppTokens.sp16),
                ],
                _group(t, '家庭', [
                  _SettingTile(
                    key: const Key('profile-nutrition'),
                    icon: Icons.donut_large_rounded,
                    tone: _Tone.green,
                    label: '营养报告',
                    onTap: () => context.push('/nutrition'),
                  ),
                  _SettingTile(
                    icon: Icons.people_outline,
                    tone: _Tone.orange,
                    label: '家庭成员',
                    value: member.currentName.isNotEmpty ? member.currentName : null,
                    onTap: () => context.push('/members'),
                  ),
                  _SettingTile(
                    icon: Icons.eco_outlined,
                    tone: _Tone.green,
                    label: '食材库',
                    onTap: () => context.push('/ingredient'),
                  ),
                  _SettingTile(
                    icon: Icons.shopping_cart_outlined,
                    tone: _Tone.blue,
                    label: '采购清单',
                    onTap: () => context.push('/shopping'),
                  ),
                ]),
                _group(t, '我的内容', [
                  _SettingTile(
                    icon: Icons.restaurant_menu,
                    tone: _Tone.orange,
                    label: '食记',
                    onTap: () => context.push('/food-log'),
                  ),
                  _SettingTile(
                    icon: Icons.edit_note,
                    tone: _Tone.green,
                    label: '写菜谱',
                    onTap: () => context.push('/create-dish'),
                  ),
                  FutureBuilder<PageData<DishDraftItem>>(
                    future: _drafts,
                    builder: (_, snap) => _SettingTile(
                      icon: Icons.description_outlined,
                      tone: _Tone.yellow,
                      label: '草稿箱',
                      value: (snap.data?.records.length ?? 0) > 0 ? '${snap.data!.records.length}' : null,
                      badge: true,
                      onTap: () => context.push('/dish-drafts'),
                    ),
                  ),
                  _SettingTile(
                    icon: Icons.star_outline,
                    tone: _Tone.yellow,
                    label: '我的评价',
                    onTap: () => context.push('/my-reviews'),
                  ),
                ]),
                _group(t, '设置', [
                  _SettingTile(
                    icon: Icons.palette_outlined,
                    tone: _Tone.orange,
                    label: '主题外观',
                    value: context.watch<ThemeController>().label,
                    onTap: () => context.push('/settings/theme'),
                  ),
                  _SettingTile(
                    icon: Icons.chat_bubble_outline,
                    tone: _Tone.blue,
                    label: '意见反馈',
                    onTap: () => context.push('/feedback'),
                  ),
                  _SettingTile(
                    icon: Icons.info_outline,
                    tone: _Tone.neutral,
                    label: '关于小食单',
                    value: 'v${AppConstants.appVersion}',
                    onTap: () => context.push('/about'),
                  ),
                ]),
                const SizedBox(height: AppTokens.sp8),
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppTokens.errorText,
                    side: const BorderSide(color: AppTokens.errorBg),
                  ),
                  onPressed: () => auth.logout(),
                  child: const Text('退出登录'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _group(AppTokens t, String title, List<Widget> tiles) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTokens.sp16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: AppTokens.sp4, bottom: AppTokens.sp8),
            child: Text(title, style: t.textStyles.sectionLabel),
          ),
          AppCard.outlined(
            padding: EdgeInsets.zero,
            radius: AppTokens.rLg,
            child: Column(
              children: [
                for (var i = 0; i < tiles.length; i++) ...[
                  if (i > 0) Divider(height: 1, indent: 60, color: t.border),
                  tiles[i],
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// 最近做的照片条（原型 Me「食记 · 最近做的」），点进食记。
class _RecentStrip extends StatelessWidget {
  final List<RecentCooked> recent;
  const _RecentStrip({required this.recent});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return AppCard.outlined(
      radius: AppTokens.rLg,
      padding: const EdgeInsets.all(AppTokens.sp12),
      onTap: () => context.push('/food-log'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Expanded(child: Text('最近做的', style: ts.lg)),
            Text('全部食记 ›', style: ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700)),
          ]),
          const SizedBox(height: AppTokens.sp10),
          SizedBox(
            height: 86,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: recent.length,
              separatorBuilder: (_, __) => const SizedBox(width: AppTokens.sp8),
              itemBuilder: (_, i) => SizedBox(
                width: 64,
                child: Column(children: [
                  DishImage.square(url: recent[i].coverImage, name: recent[i].name, size: 64),
                  const SizedBox(height: AppTokens.sp4),
                  Text(recent[i].name, maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.chip),
                ]),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

enum _Tone { orange, green, blue, yellow, neutral }

class _SettingTile extends StatelessWidget {
  final IconData icon;
  final _Tone tone;
  final String label;
  final String? value;
  final bool badge; // value 以胶囊角标展示（草稿计数）
  final VoidCallback onTap;

  const _SettingTile({
    super.key,
    required this.icon,
    required this.tone,
    required this.label,
    this.value,
    this.badge = false,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final (bg, fg) = switch (tone) {
      _Tone.orange => (t.secondary, t.primaryDeep),
      _Tone.green => (AppTokens.successBg, AppTokens.successText),
      _Tone.blue => (AppTokens.infoBg, AppTokens.infoText),
      _Tone.yellow => (AppTokens.warningBg, AppTokens.warningText),
      _Tone.neutral => (t.bg, t.body),
    };
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp12),
        child: Row(
          children: [
            Container(
              width: 34,
              height: 34,
              decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AppTokens.rMd)),
              child: Icon(icon, size: 19, color: fg),
            ),
            const SizedBox(width: AppTokens.sp12),
            Expanded(child: Text(label, style: ts.body.copyWith(color: t.title))),
            if (value != null)
              badge
                  ? Container(
                      padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: 2),
                      decoration: BoxDecoration(color: t.primaryDeep, borderRadius: BorderRadius.circular(AppTokens.rPill)),
                      child: Text(value!, style: ts.sectionLabel.copyWith(color: t.card)),
                    )
                  : Text(value!, style: ts.caption),
            const SizedBox(width: AppTokens.sp4),
            Icon(Icons.chevron_right_rounded, color: t.body, size: 20),
          ],
        ),
      ),
    );
  }
}
