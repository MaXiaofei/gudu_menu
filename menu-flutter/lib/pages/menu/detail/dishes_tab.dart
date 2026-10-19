part of '../detail_page.dart';

/// Tab 0：菜品列表（原 _buildBody 内容）。
class _DishesTab extends StatelessWidget {
  final MenuDetail detail;
  /// 备注/删菜修改成功后刷新详情。
  final VoidCallback onNoteChanged;
  /// 已完成（整集做后）：隐藏加菜入口，行内按钮全部禁用。
  final bool isDone;
  const _DishesTab({
    required this.detail,
    required this.onNoteChanged,
    required this.isDone,
  });

  /// 加菜：跳菜谱选择页（/dish-picker?menuId=），返回后刷新。
  Future<void> _addDish(BuildContext context) async {
    await context.push('/dish-picker?menuId=${detail.menu.id}');
    onNoteChanged();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return ListView(
      padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp24),
      children: [
        if (detail.dishes.isEmpty)
          const Padding(
            padding: EdgeInsets.only(top: AppTokens.sp24),
            child: EmptyView(text: '这顿还没有菜'),
          ),
        ...detail.dishes.map((d) => _DishRow(
              menuId: detail.menu.id,
              dishId: d.dishId,
              dishName: d.dishName,
              servingFactor: d.servingFactor,
              coverUrl: d.coverUrl,
              note: d.note,
              addedByNickname: d.addedByNickname,
              readOnly: isDone,
              done: isDone,
              onNoteChanged: onNoteChanged,
            )),
        // 加菜入口（原型「从菜谱加菜」描边虚框）；已完成不显示
        if (!isDone)
          InkWell(
            borderRadius: BorderRadius.circular(AppTokens.rLg),
            onTap: () => _addDish(context),
            child: Container(
              padding: const EdgeInsets.all(AppTokens.sp16),
              decoration: BoxDecoration(
                border: Border.all(color: t.primarySoft, width: 1.5),
                borderRadius: BorderRadius.circular(AppTokens.rLg),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.add_rounded, size: 18, color: t.primaryDeep),
                  const SizedBox(width: AppTokens.sp4),
                  Text('从菜谱加菜', style: ts.cardTitle.copyWith(color: t.primaryDeep)),
                ],
              ),
            ),
          ),
      ],
    );
  }
}

/// 菜品行（原型 menu-detail-cai.html）：38px 缩略图 + 菜名/份数 + 备注行。
/// 整行可点进菜谱详情（复用 /dish/:id，extra 隐藏底部操作按钮）；
/// 备注可点弹输入框修改，清空提交 = 删除备注（回显「加备注/忌口…」）。
class _DishRow extends StatelessWidget {
  final int menuId;
  /// 菜品 id（V45 自定义菜名为 null：不可点进菜谱详情）。
  final int? dishId;
  final String? dishName;
  final double? servingFactor;
  final String? coverUrl;
  final String? note;
  /// 谁点的（V45 聚餐：朋友加的菜带昵称，显示「XX 点的」小标签）。
  final String? addedByNickname;
  /// 已完成食集：行点击/备注/移出全部禁用（只读展示）。
  final bool readOnly;
  /// 已做（照片盖绿色勾）。
  final bool done;
  /// 备注修改成功后的回调（上层刷新详情）。
  final VoidCallback onNoteChanged;
  const _DishRow({
    required this.menuId,
    this.dishId,
    this.dishName,
    this.servingFactor,
    this.coverUrl,
    this.note,
    this.addedByNickname,
    this.readOnly = false,
    this.done = false,
    required this.onNoteChanged,
  });

  /// 弹备注输入框（预填当前值）；确定 → 调接口 → 回调刷新。
  Future<void> _editNote(BuildContext context) async {
    final ctrl = TextEditingController(text: note ?? '');
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('菜备注'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          maxLength: 255,
          decoration: const InputDecoration(
            hintText: '如：宝宝那份少盐',
            isDense: true,
          ),
          onSubmitted: (v) => Navigator.pop(ctx, v),
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text),
              child: const Text('确定')),
        ],
      ),
    );
    if (result == null) return;
    final trimmed = result.trim();
    try {
      await MenuService.updateDishNote(menuId, dishId!, trimmed);
      if (context.mounted) onNoteChanged();
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('备注更新失败')));
      }
    }
  }

  /// 从食集移除这道菜：确认弹窗 → 调接口 → 回调刷新（原型菜行行尾 ✕）。
  Future<void> _removeDish(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('移出食集'),
        content: Text('确认将「${dishName ?? '菜 #$dishId'}」移出食集？'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('移出')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await MenuService.removeDishFromMenu(menuId, dishId!);
      if (context.mounted) onNoteChanged();
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('移出失败')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final isCustom = dishId == null;
    final name =
        (dishName == null || dishName!.isEmpty) ? (dishId != null ? '菜 #$dishId' : '自定义菜') : dishName!;
    final hasNote = note != null && note!.isNotEmpty;
    final who = (addedByNickname ?? '').isNotEmpty ? addedByNickname! : null;
    final serving = servingFactor ?? 1;
    return Container(
      margin: const EdgeInsets.only(bottom: AppTokens.sp10),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        border: Border.all(color: t.border),
        boxShadow: t.elevationSm,
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(AppTokens.rLg),
        // 自定义菜名（dishId 为 null）：没有菜谱详情，整行不可点
        onTap: (readOnly || dishId == null)
            ? null
            : () => context.push('/dish/$dishId', extra: {'showActions': false}),
        child: Padding(
          padding: const EdgeInsets.all(AppTokens.sp10),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Stack(
                children: [
                  DishImage.square(url: coverUrl, name: name, size: 76),
                  if (done)
                    Positioned.fill(
                      child: Container(
                        decoration: BoxDecoration(
                          color: AppTokens.success.withValues(alpha: 0.55),
                          borderRadius: BorderRadius.circular(AppTokens.rMd),
                        ),
                        child: const Icon(Icons.check_rounded, color: AppTokens.onPhoto, size: 32),
                      ),
                    ),
                ],
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Expanded(
                          child: Text(name,
                              maxLines: 1, overflow: TextOverflow.ellipsis, style: ts.cardTitle.copyWith(color: t.title)),
                        ),
                        if (!isCustom && !readOnly)
                          GestureDetector(
                            onTap: () => _removeDish(context),
                            behavior: HitTestBehavior.opaque,
                            child: Padding(
                              padding: const EdgeInsets.only(left: AppTokens.sp8),
                              child: Icon(Icons.close_rounded, size: 18, color: t.body),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: AppTokens.sp4),
                    Row(children: [
                      Text('× ${serving % 1 == 0 ? serving.toInt() : serving.toStringAsFixed(1)} 份', style: ts.caption),
                      if (who != null) ...[
                        const SizedBox(width: AppTokens.sp8),
                        MemberAvatar(name: who, size: 18),
                        const SizedBox(width: AppTokens.sp4),
                        Flexible(child: Text('$who 点的', overflow: TextOverflow.ellipsis, style: ts.caption)),
                      ],
                    ]),
                    const SizedBox(height: AppTokens.sp6),
                    // 备注 / 忌口：有备注=浅底徽章，无备注=提示；自定义菜（朋友 H5 加的）只读
                    GestureDetector(
                      onTap: (readOnly || isCustom) ? null : () => _editNote(context),
                      behavior: HitTestBehavior.opaque,
                      child: hasNote
                          ? ToneBadge(note!, tone: BadgeTone.warning, icon: Icons.edit_note_rounded)
                          : (readOnly || isCustom)
                              ? const SizedBox.shrink()
                              : Text('+ 加备注/忌口…', style: ts.caption.copyWith(color: t.primaryDeep)),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 相对日期：今天 / 昨天 / N 天前 / M/D（对齐食集列表页，不引第三方库）。
String _relativeDate(DateTime? dt) {
  if (dt == null) return '';
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final that = DateTime(dt.year, dt.month, dt.day);
  final diff = today.difference(that).inDays;
  if (diff <= 0) return '今天';
  if (diff == 1) return '昨天';
  if (diff < 7) return '$diff 天前';
  return '${dt.month}/${dt.day}';
}

/// 备菜行库存徽标颜色。
Color _prepStockColor(String level) {
  return switch (level) {
    'ENOUGH' => AppTokens.success,
    'LOW' => AppTokens.warning,
    _ => AppTokens.error,
  };
}

/// 备菜行库存徽标文案。
String _prepStockLabel(String level) {
  return switch (level) {
    'ENOUGH' => '充足',
    'LOW' => '不足',
    _ => '用完',
  };
}

