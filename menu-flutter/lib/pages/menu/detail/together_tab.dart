part of '../detail_page.dart';

/// Tab 2：聚餐（V45，原型 menu-detail-xietong.html）。
/// 邀请（口令 + 二维码 + 复制/分享，三载体同效指向 H5 together.html?token=）
/// → 成员（昵称 + 最后活跃，轮询即心跳）→ 动态流（谁点的/谁删的）。
/// 轮询 10s 刷新清单；朋友加的菜在菜 Tab 显示「XX 点的」标记。
class _TogetherTab extends StatefulWidget {
  final int menuId;
  /// 父级数据版本号：变化时重载（与备菜 tab 同机制）。
  final int refreshTick;
  /// 已完成食集：邀请区隐藏（不再邀请），动态只读展示。
  final bool isDone;
  /// 成员数加载后上报父级（tab 汇总数量）。
  final ValueChanged<int> onCountChanged;
  const _TogetherTab({
    required this.menuId,
    required this.refreshTick,
    required this.isDone,
    required this.onCountChanged,
  });
  @override
  State<_TogetherTab> createState() => _TogetherTabState();
}

class _TogetherTabState extends State<_TogetherTab> {
  TogetherVO? _vo;
  /// 单独持有的邀请（刷新后更新，优先于 _vo.invite）。
  TogetherInvite? _invite;
  bool _loading = true;
  String? _err;
  Timer? _timer;
  bool _inviting = false;

  TogetherInvite? get _effectiveInvite =>
      _invite ?? _vo?.invite;

  @override
  void initState() {
    super.initState();
    // 先加载成功再启动轮询：失败时页面给重试，避免每 10s 重复弹错误 toast
    _load();
  }

  /// 轮询定时器（加载成功后启动；失败即停，避免反复弹错误）。
  void _ensureTimer() {
    _timer ??= Timer.periodic(
        const Duration(seconds: 10), (_) => _load(quiet: true));
  }

  @override
  void didUpdateWidget(covariant _TogetherTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    // 不随 refreshTick 重载：聚餐清单由 10s 轮询刷新（避免食集详情每次刷新触发一次失败 toast）
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _load({bool quiet = false}) async {
    if (!quiet) {
      setState(() {
        _loading = true;
        _err = null;
      });
    }
    try {
      final vo = await TogetherService.together(widget.menuId);
      if (!mounted) return;
      setState(() {
        _vo = vo;
        _loading = false;
        widget.onCountChanged(vo.members.length);
      });
      _ensureTimer();
    } catch (e) {
      if (!mounted) return;
      _timer?.cancel();
      _timer = null;
      if (!quiet) {
        setState(() {
          _err = '$e';
          _loading = false;
        });
      }
      // 静默轮询失败不打扰（定时器已停，手动重试会重新加载并恢复轮询）
    }
  }

  /// 生成/刷新邀请（口令 + token，url 指向 H5）。
  Future<void> _genInvite() async {
    if (_inviting) return;
    setState(() => _inviting = true);
    try {
      final inv = await TogetherService.invite(widget.menuId);
      if (!mounted) return;
      setState(() => _invite = inv);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('邀请已生成，口令 ${inv.code}')));
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('生成邀请失败')));
      }
    }
    if (mounted) setState(() => _inviting = false);
  }

  Future<void> _copyCode() async {
    final inv = _effectiveInvite;
    if (inv == null) return;
    // 口令 + 入口地址一起复制：朋友拿到地址才能输入口令（地址不带 token，口令是钥匙）
    final text = '咕嘟聚餐邀请\n口令：${inv.code}\n打开链接输入口令加入：${inv.entryUrl}';
    await Clipboard.setData(ClipboardData(text: text));
    if (mounted) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('口令和链接已复制')));
    }
  }

  Future<void> _share() async {
    final inv = _effectiveInvite;
    if (inv == null) return;
    await Share.share(inv.url);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    if (_loading && _vo == null) return const LoadingView();
    if (_err != null && _vo == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(AppTokens.sp24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('加载聚餐失败：$_err',
                  style: t.textStyles.sm.copyWith(color: t.caption),
                  textAlign: TextAlign.center),
              const SizedBox(height: AppTokens.sp8),
              // 诊断：区分「未登录」与「登录态正常但请求失败」
              Text(
                  ApiClient.instance.token == null
                      ? '当前未登录，请先登录后再试'
                      : '已登录；若持续失败，请把本页文字反馈给开发',
                  style: t.textStyles.tiny.copyWith(color: t.primary)),
              const SizedBox(height: AppTokens.sp12),
              OutlinedButton(onPressed: _load, child: const Text('重试')),
            ],
          ),
        ),
      );
    }
    final vo = _vo;
    if (vo == null) return const EmptyView(text: '加载聚餐失败');
    return ListView(
      padding: const EdgeInsets.fromLTRB(
          AppTokens.sp16, AppTokens.sp12, AppTokens.sp16, AppTokens.sp24),
      children: [
        // 邀请区（完成态隐藏）
        if (!widget.isDone) ...[
          _buildInviteCard(t),
          const SizedBox(height: AppTokens.sp16),
        ],
        // 成员区
        Text('成员 · ${vo.members.length}',
            style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: AppTokens.sp8),
        if (vo.members.isEmpty)
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
                color: t.card, borderRadius: BorderRadius.circular(AppTokens.rSm)),
            child: Text('还没有人加入，先邀请朋友吧',
                style: t.textStyles.sm.copyWith(color: t.caption)),
          )
        else
          Wrap(
            spacing: AppTokens.sp8,
            runSpacing: AppTokens.sp8,
            children: vo.members.map((m) => _memberChip(t, m)).toList(),
          ),
        const SizedBox(height: AppTokens.sp16),
        // 动态流
        Text('动态',
            style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
        const SizedBox(height: AppTokens.sp8),
        if (vo.activities.isEmpty)
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
                color: t.card, borderRadius: BorderRadius.circular(AppTokens.rSm)),
            child: Text('暂无动态',
                style: t.textStyles.sm.copyWith(color: t.caption)),
          )
        else
          ...vo.activities.map((a) => _activityRow(t, a)),
        const SizedBox(height: AppTokens.sp16),
        // 说明
        Text(
          '朋友扫码 / 点链接 / 输口令都能加入，加菜直接进菜 Tab（标「XX 点的」），'
          '谁都能删，会记下谁删的。清单每 10 秒自动刷新。',
          style: t.textStyles.sm.copyWith(color: t.caption, height: 1.6),
        ),
      ],
    );
  }

  /// 邀请卡：二维码 + 口令 + 复制/分享（三载体同效）。
  Widget _buildInviteCard(AppTokens t) {
    final inv = _effectiveInvite;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp16),
      decoration: BoxDecoration(
        color: t.card,
        border: Border.all(color: t.border),
        borderRadius: BorderRadius.circular(AppTokens.rMd),
      ),
      child: inv == null
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text('邀请朋友一起点菜',
                    style: t.textStyles.cardTitle.copyWith(color: t.title)),
                const SizedBox(height: AppTokens.sp4),
                Text('生成口令 + 二维码，扫码/点链接/输口令都能加入',
                    style: t.textStyles.sm.copyWith(color: t.caption)),
                const SizedBox(height: AppTokens.sp12),
                ElevatedButton(
                  onPressed: _inviting ? null : () => _genInvite(),
                  style: ElevatedButton.styleFrom(
                      backgroundColor: t.primary, foregroundColor: Colors.white),
                  child: Text(_inviting ? '生成中…' : '生成邀请',
                      style: const TextStyle(fontWeight: FontWeight.w800)),
                ),
              ],
            )
          : Column(
              children: [
                Row(
                  children: [
                    // 二维码（内容=H5 链接）
                    Container(
                      padding: const EdgeInsets.all(6),
                      decoration: BoxDecoration(
                          color: Colors.white,
                          border: Border.all(color: t.border),
                          borderRadius: BorderRadius.circular(AppTokens.rSm)),
                      child: QrImageView(
                        data: inv.url,
                        size: 88,
                        backgroundColor: Colors.white,
                      ),
                    ),
                    const SizedBox(width: AppTokens.sp12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('邀请朋友 · 口令 ${inv.code}',
                              style: t.textStyles.cardTitle
                                  .copyWith(color: t.title)),
                          const SizedBox(height: AppTokens.sp4),
                          Text('扫码 / 点链接 / 输口令，同一邀请三选一即可',
                              style: t.textStyles.sm
                                  .copyWith(color: t.caption)),
                          const SizedBox(height: AppTokens.sp8),
                          Row(
                            children: [
                              _pillButton('复制口令', t, onTap: _copyCode),
                              const SizedBox(width: AppTokens.sp8),
                              _pillButton('分享链接', t, onTap: _share),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
    );
  }

  Widget _pillButton(String label, AppTokens t, {required VoidCallback onTap}) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        decoration: BoxDecoration(
          color: t.primary,
          borderRadius: BorderRadius.circular(AppTokens.rPill),
        ),
        child: Text(label,
            style: t.textStyles.sm.copyWith(
                color: Colors.white, fontWeight: FontWeight.w800)),
      ),
    );
  }

  /// 成员 chip：首字头像圈 + 昵称 + 最后活跃。
  Widget _memberChip(AppTokens t, TogetherMember m) {
    return Container(
      padding: const EdgeInsets.fromLTRB(AppTokens.sp4, AppTokens.sp4, AppTokens.sp10, AppTokens.sp4),
      decoration: BoxDecoration(
        color: t.card,
        border: Border.all(color: t.border),
        borderRadius: BorderRadius.circular(AppTokens.rPill),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        MemberAvatar(name: m.nickname.trim().isEmpty ? '朋友' : m.nickname, size: 26),
        const SizedBox(width: AppTokens.sp6),
        Text(m.nickname, style: t.textStyles.sm.copyWith(color: t.title)),
        const SizedBox(width: AppTokens.sp6),
        Text(_lastActiveText(m.lastActiveAt), style: t.textStyles.chip),
      ]),
    );
  }

  /// 动态行：昵称 + 动作 + 菜名 + 时间。
  Widget _activityRow(AppTokens t, TogetherActivity a) {
    final verb = a.action == 'remove' ? '删了' : '点了';
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          MemberAvatar(name: a.nickname, size: 24),
          const SizedBox(width: AppTokens.sp8),
          Expanded(
            child: Text.rich(TextSpan(
              style: t.textStyles.sm.copyWith(color: t.body, height: 1.5),
              children: [
                TextSpan(
                    text: a.nickname,
                    style: const TextStyle(fontWeight: FontWeight.w800)),
                TextSpan(text: ' $verb「${a.dishName}」'),
              ],
            )),
          ),
          Text(_lastActiveText(a.createTime), style: t.textStyles.chip),
        ],
      ),
    );
  }

  /// 相对时间：刚刚 / N 分钟前 / N 小时前 / M/D。
  static String _lastActiveText(String? iso) {
    if (iso == null) return '';
    final dt = DateTime.tryParse(iso);
    if (dt == null) return '';
    final diff = DateTime.now().difference(dt);
    if (diff.inMinutes < 1) return '刚刚';
    if (diff.inMinutes < 60) return '${diff.inMinutes} 分钟前';
    if (diff.inHours < 24) return '${diff.inHours} 小时前';
    return '${dt.month}/${dt.day}';
  }
}
