import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../services/notification_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/illustration.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/select_chip.dart';
import '../../widgets/skeleton.dart';

/// 消息与提醒（原型 Notices）：全部 / 提醒 / 系统，未读圆点，全部已读。
class NoticesPage extends StatefulWidget {
  const NoticesPage({super.key});

  @override
  State<NoticesPage> createState() => _NoticesPageState();
}

class _NoticesPageState extends State<NoticesPage> {
  List<AppNotification> _items = const [];
  bool _loading = true;
  Object? _error;
  String _filter = 'all';

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final list = await NotificationService.list();
      if (!mounted) return;
      setState(() {
        _items = list;
        _error = null;
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _read(AppNotification n) async {
    if (n.read) return;
    try {
      await NotificationService.markRead(n.id);
      if (!mounted) return;
      setState(() => _items = [for (final i in _items) i.id == n.id ? i.markedRead() : i]);
    } catch (_) {
      // request 已 toast
    }
  }

  Future<void> _readAll() async {
    for (final n in _items.where((n) => !n.read).toList()) {
      await _read(n);
    }
  }

  void _open(AppNotification n) {
    _read(n);
    if (n.type == 'expiry') context.go('/pantry');
    if (n.type == 'shopping') context.push('/shopping');
  }

  @override
  Widget build(BuildContext context) {
    final unread = _items.where((n) => !n.read).length;
    final shown = _items.where((n) => switch (_filter) {
          'reminder' => n.isReminder,
          'system' => !n.isReminder,
          _ => true,
        }).toList();
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            BackHeader(
              title: '消息',
              action: unread > 0
                  ? TextButton(onPressed: _readAll, child: const Text('全部已读'))
                  : null,
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp16, 0, AppTokens.sp16, AppTokens.sp8),
              child: Row(children: [
                SelectChip(
                    label: unread > 0 ? '全部 · $unread' : '全部',
                    selected: _filter == 'all',
                    onTap: () => setState(() => _filter = 'all')),
                const SizedBox(width: AppTokens.sp8),
                SelectChip(label: '提醒', selected: _filter == 'reminder', onTap: () => setState(() => _filter = 'reminder')),
                const SizedBox(width: AppTokens.sp8),
                SelectChip(label: '系统', selected: _filter == 'system', onTap: () => setState(() => _filter = 'system')),
              ]),
            ),
            Expanded(
              child: _loading
                  ? const SkeletonList(count: 4, thumb: 44)
                  : _error != null
                      ? ErrorView(text: '消息加载失败', onRetry: _load, error: _error)
                      : shown.isEmpty
                          ? const EmptyView(
                              text: '暂时没有消息',
                              subtitle: '食材快到期、家人点了菜，都会在这里提醒你',
                              illustration: StateIllustration(keys: ['milk', 'egg', 'tomato'], faded: true),
                            )
                          : RefreshIndicator(
                              onRefresh: _load,
                              child: ListView.separated(
                                padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp24),
                                itemCount: shown.length,
                                separatorBuilder: (_, __) => const SizedBox(height: AppTokens.sp10),
                                itemBuilder: (_, i) => _NoticeCard(n: shown[i], onTap: () => _open(shown[i])),
                              ),
                            ),
            ),
          ],
        ),
      ),
    );
  }
}

class _NoticeCard extends StatelessWidget {
  final AppNotification n;
  final VoidCallback onTap;
  const _NoticeCard({required this.n, required this.onTap});

  static String _ago(DateTime? d) {
    if (d == null) return '';
    final diff = DateTime.now().difference(d);
    if (diff.inMinutes < 60) return '${diff.inMinutes.clamp(1, 59)} 分钟前';
    if (diff.inHours < 24) return '${diff.inHours} 小时前';
    if (diff.inDays == 1) return '昨天';
    return '${d.month}/${d.day}';
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final (icon, bg, fg) = switch (n.type) {
      'expiry' => (Icons.schedule_rounded, AppTokens.errorBg, AppTokens.errorText),
      'shopping' => (Icons.shopping_cart_outlined, AppTokens.infoBg, AppTokens.infoText),
      _ => (Icons.notifications_none_rounded, t.secondary, t.primaryDeep),
    };
    return InkWell(
      borderRadius: BorderRadius.circular(AppTokens.rLg),
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.all(AppTokens.sp12),
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rLg),
          border: Border.all(color: n.read ? t.border : t.primarySoft),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(AppTokens.rMd)),
              child: Icon(icon, color: fg, size: 22),
            ),
            const SizedBox(width: AppTokens.sp12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(children: [
                    Expanded(child: Text(n.title, style: ts.cardTitle.copyWith(color: t.title))),
                    Text(_ago(n.createTime), style: ts.caption),
                  ]),
                  if (n.content.isNotEmpty) ...[
                    const SizedBox(height: AppTokens.sp4),
                    Text(n.content, maxLines: 3, overflow: TextOverflow.ellipsis, style: ts.caption),
                  ],
                ],
              ),
            ),
            if (!n.read) ...[
              const SizedBox(width: AppTokens.sp8),
              Container(
                key: ValueKey('unread-${n.id}'),
                width: 8,
                height: 8,
                margin: const EdgeInsets.only(top: AppTokens.sp6),
                decoration: const BoxDecoration(color: AppTokens.error, shape: BoxShape.circle),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
