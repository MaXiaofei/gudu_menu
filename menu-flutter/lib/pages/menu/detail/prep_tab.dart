part of '../detail_page.dart';

/// Tab 1：备菜（Plan C）。加载 GET /menu/{id}/prep + 状态交互（点/长按）。
class _PrepTab extends StatefulWidget {
  final int menuId;
  /// 父级数据版本号：变化时（加菜/删菜等）重载，解决 IndexedStack 保留状态不刷新。
  final int refreshTick;
  /// 备料总数（totalCount）加载后上报父级，用于 tab 汇总数量。
  final ValueChanged<int> onCountChanged;
  /// 已完成食集：状态 chip 不可点（只读展示）。
  final bool isDone;
  const _PrepTab({
    required this.menuId,
    required this.refreshTick,
    required this.onCountChanged,
    required this.isDone,
  });
  @override
  State<_PrepTab> createState() => _PrepTabState();
}

class _PrepTabState extends State<_PrepTab> {
  MenuPrep? _prep;
  bool _loading = true;
  bool _condimentExpanded = false;
  int _lastTick = 0;

  @override
  void initState() {
    super.initState();
    _lastTick = widget.refreshTick;
    _load();
  }

  @override
  void didUpdateWidget(covariant _PrepTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.refreshTick != _lastTick) {
      _lastTick = widget.refreshTick;
      _load();
    }
  }

  Future<void> _load() async {
    try {
      _prep = await PrepService.getPrep(widget.menuId);
    } catch (_) {}
    if (mounted) {
      widget.onCountChanged(_prep?.totalCount ?? 0);
      setState(() => _loading = false);
    }
  }

  /// 点 chip：PENDING ↔ READY。
  Future<void> _toggle(PrepItem item) async {
    final next = item.status == PrepStatus.ready
        ? PrepStatus.pending
        : PrepStatus.ready;
    await _update(item, next);
  }

  /// 长按：弹「化冻 / 腌制 / 重置」三选一。
  Future<void> _longPress(PrepItem item) async {
    final t = AppTokens.of(context);
    final next = await showModalBottomSheet<PrepStatus>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.ac_unit, color: AppTokens.info),
              title: const Text('化冻中'),
              onTap: () => Navigator.pop(ctx, PrepStatus.thawing),
            ),
            ListTile(
              leading: const Icon(Icons.schedule, color: AppTokens.warning),
              title: const Text('腌制中'),
              onTap: () => Navigator.pop(ctx, PrepStatus.marinating),
            ),
            ListTile(
              leading: Icon(Icons.refresh, color: t.caption),
              title: const Text('重置为待备'),
              onTap: () => Navigator.pop(ctx, PrepStatus.pending),
            ),
          ],
        ),
      ),
    );
    if (next == null || next == item.status) return;
    await _update(item, next);
  }

  /// 乐观更新 + 失败回滚。
  Future<void> _update(PrepItem item, PrepStatus next) async {
    final prev = _prep;
    setState(() => _prep = _rebuiltWith(item.ingredientId, next));
    try {
      await PrepService.updateStatus(widget.menuId, item.ingredientId, next);
    } catch (_) {
      if (mounted) {
        setState(() => _prep = prev);
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('更新失败')));
      }
    }
  }

  /// 一键加采购：弹勾选备菜项（默认勾家里用完/不足）→ 送进该食集采购清单。
  Future<void> _showAddShopping() async {
    final p = _prep;
    if (p == null) return;
    final items = [...p.items, ...p.condiments];
    if (items.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('没有可加入的备菜')));
      return;
    }
    final selected = await showModalBottomSheet<Set<int>>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => _PrepShoppingSheet(items: items),
    );
    if (selected == null || selected.isEmpty) return;
    try {
      final listId = await ShoppingService.fromPrep(widget.menuId, selected.toList());
      if (!mounted) return;
      // 加购成功：直接跳转到该采购清单的详情
      context.push('/shopping?listId=$listId');
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('加入采购清单失败')));
      }
    }
  }

  /// 重建 _prep（改某食材 status + 重算 readyCount；调料改 status 同样计入进度）。
  MenuPrep _rebuiltWith(int ingredientId, PrepStatus next) {
    final p = _prep;
    if (p == null) {
      return const MenuPrep(
          items: [], condiments: [], readyCount: 0, totalCount: 0);
    }
    final items = p.items
        .map((it) =>
            it.ingredientId == ingredientId ? it.copyWithStatus(next) : it)
        .toList();
    final condiments = p.condiments
        .map((it) =>
            it.ingredientId == ingredientId ? it.copyWithStatus(next) : it)
        .toList();
    final readyCount = [...items, ...condiments]
        .where((it) => it.status == PrepStatus.ready)
        .length;
    return MenuPrep(
      items: items,
      condiments: condiments,
      readyCount: readyCount,
      totalCount: p.totalCount,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const SkeletonList(count: 5, thumb: 40);
    final p = _prep;
    if (p == null) return ErrorView(text: '加载备菜失败', onRetry: _load, compact: true);
    return ListView(
      children: [
        _buildProgress(p),
        const SectionTitle('备料清单'),
        ...p.items.map((it) => _PrepItemRow(
              item: it,
              // 已完成：chip 不可点
              onTap: widget.isDone ? null : () => _toggle(it),
              onLongPress: widget.isDone ? null : () => _longPress(it),
            )),
        // 调料折叠组（与菜分组；计入总数/进度，文案不再写「无需备料」）
        if (p.condiments.isNotEmpty) ...[
          const SizedBox(height: AppTokens.sp8),
          _buildCondimentHeader(p.condiments.length),
          if (_condimentExpanded)
            ...p.condiments.map((it) => _PrepItemRow(
                  item: it,
                  isCondiment: true,
                  // 已完成：chip 不可点
                  onTap: widget.isDone ? null : () => _toggle(it),
                  onLongPress: widget.isDone ? null : () => _longPress(it),
                )),
        ],
        const SizedBox(height: AppTokens.sp24),
      ],
    );
  }

  Widget _buildProgress(MenuPrep p) {
    final t = AppTokens.of(context);
    final ratio = p.totalCount == 0 ? 0.0 : p.readyCount / p.totalCount;
    return Padding(
      padding: const EdgeInsets.all(AppTokens.sp16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 一键加采购：把要买的送进采购清单（放在备菜进度上方，完成态隐藏）
          if (!widget.isDone)
            Align(
              alignment: Alignment.centerRight,
              child: GestureDetector(
                onTap: _showAddShopping,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: 5),
                  decoration: BoxDecoration(
                    color: t.primary,
                    borderRadius: BorderRadius.circular(AppTokens.rPill),
                  ),
                  child: Text('一键加采购',
                      style: t.textStyles.tiny.copyWith(color: Colors.white, fontWeight: FontWeight.w800)),
                ),
              ),
            ),
          if (!widget.isDone) const SizedBox(height: AppTokens.sp8),
          Row(
            children: [
              Text('备料进度',
                  style: t.textStyles.cardTitle.copyWith(color: t.title)),
              const Spacer(),
              Text('已备 ${p.readyCount} / 共 ${p.totalCount} 样',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            ],
          ),
          const SizedBox(height: AppTokens.sp8),
          ClipRRect(
            borderRadius: BorderRadius.circular(AppTokens.rXs),
            child: LinearProgressIndicator(
              value: ratio,
              minHeight: 8,
              backgroundColor: t.border,
              color: AppTokens.success,
            ),
          ),
        ],
      ),
    );
  }

  /// 调料折叠头（与菜分组；调料计入总数/进度，故文案只说「调料 N 样」）。
  Widget _buildCondimentHeader(int count) {
    final t = AppTokens.of(context);
    return Material(
      color: t.secondary,
      child: InkWell(
        onTap: () => setState(() => _condimentExpanded = !_condimentExpanded),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
          child: Row(
            children: [
              Text('调料 $count 样',
                  style: t.textStyles.sm),
              const Spacer(),
              Icon(
                  _condimentExpanded ? Icons.expand_less : Icons.expand_more,
                  color: t.caption),
            ],
          ),
        ),
      ),
    );
  }
}

/// 备菜列表行：食材名 + 用量 + 共用高亮 + 来自哪些菜 + 行尾状态 chip。
/// 原型（menu-detail-beicai.html）：chip 在行尾；已备整行变淡、名称删除线、
/// 实绿底 chip「✓ 已备」；待备白底描边 chip。
class _PrepItemRow extends StatelessWidget {
  final PrepItem item;
  /// 可空：已完成食集传 null 禁用（chip 不可点）。
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final bool isCondiment;
  const _PrepItemRow({
    required this.item,
    required this.onTap,
    required this.onLongPress,
    this.isCondiment = false,
  });

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final s = item.status;
    final isReady = s == PrepStatus.ready;
    final chipColor = switch (s) {
      PrepStatus.ready => AppTokens.success,
      PrepStatus.thawing => AppTokens.info,
      PrepStatus.marinating => AppTokens.warning,
      PrepStatus.pending => t.caption,
    };
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        hoverColor: t.primary.withValues(alpha: 0.08),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
          decoration: BoxDecoration(
            border: Border(top: BorderSide(color: t.border)),
            // 已备整行变色（浅绿底）；共用项淡橙底（聚焦"一次备够"），已备优先
            color: isReady
                ? AppTokens.success.withValues(alpha: 0.06)
                : (item.shared ? t.highlight : null),
          ),
          child: Row(
            children: [
              // 原型 MenuPrep：每个备菜项配食材插画（已备去色）
              IngredientIcon(name: item.ingredientName, size: 40, depleted: isReady),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Flexible(
                          child: Text(item.ingredientName,
                              style: t.textStyles.md.copyWith(
                                  fontWeight: FontWeight.w500,
                                  // 已备：名称变灰 + 删除线（原型 line-through）
                                  color: isReady ? t.caption : t.title,
                                  decoration: isReady
                                      ? TextDecoration.lineThrough
                                      : null)),
                        ),
                      ],
                    ),
                    if (item.usageTexts.isNotEmpty)
                      Text(
                        item.usageTexts.join(' + '),
                        style: t.textStyles.caption,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    if (item.dishNames.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Text(
                          item.dishNames.length >= 2
                              ? '${item.dishCount} 道菜共用 · ${item.dishNames.join("、")}'
                              : item.dishNames.first,
                          style: t.textStyles.caption,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                  ],
                ),
              ),
              // 家里：充足/不足/用完 徽标（V42，备料时一眼知道要不要先买）
              if (item.stockLevel != null && item.stockLevel!.isNotEmpty)
                Container(
                  margin: const EdgeInsets.only(right: AppTokens.sp6),
                  padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp6, vertical: 2),
                  decoration: BoxDecoration(
                    color: _prepStockColor(item.stockLevel!).withAlpha(20),
                    borderRadius: BorderRadius.circular(AppTokens.rPill),
                  ),
                  child: Text('家里：${_prepStockLabel(item.stockLevel!)}',
                      style: t.textStyles.chip.copyWith(
                          color: _prepStockColor(item.stockLevel!), fontWeight: FontWeight.w700)),
                ),
              const SizedBox(width: AppTokens.sp4),
              // 状态 chip 在行尾（原型：待备白底描边；已备/化冻中/腌制中实底白字）
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: AppTokens.sp8, vertical: AppTokens.sp4),
                decoration: BoxDecoration(
                  color: s == PrepStatus.pending
                      ? t.card
                      : chipColor,
                  border: Border.all(
                      color: s == PrepStatus.pending
                          ? t.border
                          : chipColor),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Text(
                    // 原型「✓ 已备」；✓ 为几何符号非 emoji，可入 UI
                    s == PrepStatus.ready ? '✓ ${s.label}' : s.label,
                    style: t.textStyles.sm.copyWith(
                        color: s == PrepStatus.pending ? chipColor : t.card,
                        fontWeight: FontWeight.w600)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
