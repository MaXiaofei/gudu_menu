part of '../shopping_page.dart';

extension _ShoppingDetailView on _ShoppingPageState {
  Widget _buildDetailView() {
    final t = AppTokens.of(context);
    final d = _detail!;
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(icon: const Icon(Icons.arrow_back), onPressed: _closeDetail),
        title: _buildTitle(t, d),
        actions: [
          IconButton(icon: const Icon(Icons.share), onPressed: _openShare),
        ],
      ),
      body: _detailLoading
          ? const LoadingView()
          : Column(
              children: [
                // 全选行（§15：有勾选必须提供全选）
                if (d.items.isNotEmpty) _buildSelectAllRow(t),
                Expanded(
                  child: d.items.isEmpty
                      ? _buildEmptyDetail(t)
                      : ListView(
                          padding: const EdgeInsets.all(12),
                          children: [
                            _buildDetailHeader(d),
                            const SizedBox(height: 4),
                            for (final entry in d.grouped.entries)
                              _buildCategorySection(
                                  entry.key, d.categoryNames[entry.key] ?? '其他', entry.value),
                            const SizedBox(height: 60),
                          ],
                        ),
                ),
              ],
            ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: Row(children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _showAddSheet,
                icon: const Icon(Icons.add, size: 18),
                label: const Text('添加'),
              ),
            ),
            // 空清单：只有添加入口（无入库）；有清单后才出现批量入库
            if (d.items.isNotEmpty) ...[
              const SizedBox(width: 10),
              Expanded(
                flex: 2,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: t.primary,
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(44),
                  ),
                  onPressed: _selectedItems.isEmpty ? null : _saveRestock,
                  child: Text('保存入库 · ${_selectedItems.length} 项'),
                ),
              ),
            ],
          ]),
        ),
      ),
    );
  }

  /// 标题：自定义清单显示 name + ✎ 改名；食集清单显示「采购单 #N」。
  Widget _buildTitle(AppTokens t, ShoppingListVO d) {
    final hasName = d.name != null && d.name!.isNotEmpty;
    return GestureDetector(
      onTap: hasName ? _showRenameSheet : null,
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Text(hasName ? d.name! : '采购单 #${d.id}',
            style: t.textStyles.subtitle),
        if (hasName) ...[
          const SizedBox(width: 4),
          Icon(Icons.edit_outlined, size: 16, color: t.primary),
        ],
      ]),
    );
  }

  /// 全选行：勾选/取消勾选全部未入库项。
  Widget _buildSelectAllRow(AppTokens t) {
    final unpurchased = _detail!.items.where((it) => it.purchased == 0).toList();
    final allSelected =
        unpurchased.isNotEmpty && unpurchased.every((it) => _selectedItems.contains(it.id));
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      decoration: BoxDecoration(
        color: t.card,
        border: Border(bottom: BorderSide(color: t.border)),
      ),
      child: Row(children: [
        GestureDetector(
          onTap: () => _refresh(() {
            if (allSelected) {
              _selectedItems.removeAll(unpurchased.map((it) => it.id));
            } else {
              _selectedItems.addAll(unpurchased.map((it) => it.id));
            }
          }),
          child: Row(children: [
            Icon(
              allSelected ? Icons.check_box : Icons.check_box_outline_blank,
              color: allSelected ? t.primary : t.caption,
              size: 22,
            ),
            const SizedBox(width: 6),
            Text('全选', style: t.textStyles.md.copyWith(color: t.title, fontWeight: FontWeight.w600)),
          ]),
        ),
        const Spacer(),
        Text('已选 ${_selectedItems.length} 项',
            style: t.textStyles.sm.copyWith(color: _selectedItems.isEmpty ? t.caption : t.primary)),
      ]),
    );
  }

  /// 空清单（自定义采购）：提示 + 底栏「添加」由 bottomNavigationBar 提供。
  Widget _buildEmptyDetail(AppTokens t) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('还没有采购项', style: t.textStyles.md.copyWith(color: t.title)),
            const SizedBox(height: 8),
            Text('点下方「添加」列要买的——食材、生活用品都行，当备忘单用。\n匹配到食材库的勾选后可入库，匹配不到的照常列、只标已买',
                textAlign: TextAlign.center,
                style: t.textStyles.sm.copyWith(color: t.caption, height: 1.7)),
          ],
        ),
      ),
    );
  }

  Widget _buildDetailHeader(ShoppingListVO d) {
    final t = AppTokens.of(context);
    final hasName = d.name != null && d.name!.isNotEmpty;
    return Row(children: [
      Container(
        width: 4, height: 18,
        decoration: BoxDecoration(color: t.primary, borderRadius: BorderRadius.circular(2)),
      ),
      const SizedBox(width: 8),
      Expanded(
        child: Text(hasName ? d.name! : '${d.sourceLabel} · #${d.id}',
            style: t.textStyles.pageTitle),
      ),
      Text(d.dateRange, style: t.textStyles.sm.copyWith(color: t.caption)),
    ]);
  }

  Widget _buildCategorySection(
      String catKey, String catName, List<ShoppingItemVO> items) {
    final t = AppTokens.of(context);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const SizedBox(height: 12),
      Text(catName, style: t.textStyles.md.copyWith(fontWeight: FontWeight.w600, color: t.primary)),
      const SizedBox(height: 4),
      ...items.map((it) => _buildItemTile(it)),
    ]);
  }

  /// 单项：
  /// - 未入库：勾选框=本地选择态；行尾 ✕ = 移除确认
  /// - 已入库：✓ 固定 + 删除线 + 已入库徽标；行尾 ✕ = 撤回入库确认
  Widget _buildItemTile(ShoppingItemVO it) {
    final t = AppTokens.of(context);
    final bought = it.purchased == 1;
    final selected = _selectedItems.contains(it.id);
    return InkWell(
      // 点整行切换勾选（未入库项；已入库项固定不可点）
      onTap: bought
          ? null
          : () => _refresh(() {
                if (!_selectedItems.remove(it.id)) {
                  _selectedItems.add(it.id);
                }
              }),
      child: Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
      decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: t.border))),
      child: Row(children: [
        // 勾选：未入库项本地选择态；已入库项固定 ✓
        Container(
          width: 22, height: 22,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppTokens.rXs),
            border: Border.all(
                color: bought
                    ? AppTokens.success
                    : (selected ? t.primary : t.border),
                width: bought || selected ? 1.5 : 1),
            color: bought ? AppTokens.success : (selected ? t.primary : null),
          ),
          child: bought || selected
              ? Icon(Icons.check, size: 14, color: t.card)
              : null,
        ),
        const SizedBox(width: AppTokens.sp10),
        // 原型 MenuShop：每行食材插画（已入库去色）
        IngredientIcon(
          name: it.displayName,
          iconKey: it.iconKey,
          category: it.purchaseCategoryName,
          size: 36,
          depleted: bought,
        ),
        const SizedBox(width: AppTokens.sp10),
        Expanded(
          child: Text(
            it.displayName,
            style: t.textStyles.md.copyWith(
              color: bought ? t.body : t.title,
              decoration: bought ? TextDecoration.lineThrough : null,
            ),
          ),
        ),
        Text(it.amountText, style: t.textStyles.caption),
        const SizedBox(width: 8),
        _buildStockBadge(it),
        const SizedBox(width: 8),
        // 行尾 ✕：未入库=移除；已入库=撤回入库
        GestureDetector(
          onTap: bought ? () => _confirmUndoRestock(it) : () => _confirmRemove(it),
          child: Padding(
            padding: const EdgeInsets.all(4),
            child: Icon(Icons.close, size: 16, color: t.caption),
          ),
        ),
      ]),
      ),
    );
  }

  /// 行尾三色余量徽章（V42 档位版：家里：用完/不足/充足）。
  Widget _buildStockBadge(ShoppingItemVO it) {
    final t = AppTokens.of(context);
    final status = it.stockStatus;
    if (status == 'RED_NONE') return _badge('家里：用完', AppTokens.error);
    if (status == 'YELLOW_SHORT') return _badge('家里：不足', AppTokens.warning);
    if (status == 'GREEN_ENOUGH') return _badge('家里：充足', AppTokens.success);
    // null：customName 手动加项或无食材关联 → 灰
    return _badge('手动加', t.caption);
  }

  Widget _badge(String text, Color color) {
    final t = AppTokens.of(context);
    return Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(AppTokens.rPill),
        ),
        child: Text(text, style: t.textStyles.chip.copyWith(color: t.card)),
      );
  }

  // ===== 批量保存入库 / 移除 / 撤回 =====

  /// 保存入库：勾选的项一次性入库（默认记充足），手动项只标已买。
  Future<void> _saveRestock() async {
    if (_selectedItems.isEmpty) return;
    try {
      final r = await ShoppingService.restock(_selectedItems.toList());
      if (!mounted) return;
      final msg = r.markedOnly > 0
          ? '已入库 ${r.restocked} 项，${r.markedOnly} 项只标已买'
          : '已入库 ${r.restocked} 项';
      _snack(msg);
      _openDetail(_detail!.id); // 刷新详情（清空选择态）
    } catch (e) {
      _snack('保存入库失败：$e');
    }
  }

  /// 未入库项 ✕：移除确认（不会丢，可从备菜一键加采购/重新生成加回）。
  Future<void> _confirmRemove(ShoppingItemVO it) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('移除采购项'),
        content: Text('移除「${it.displayName}」？\n以后要买，从备菜「一键加采购」或重新生成清单就能加回来。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text('移除', style: AppTokens.of(ctx).textStyles.danger)),
        ],
      ),
    );
    if (ok == true) {
      try {
        await ShoppingService.deleteItem(it.id);
        _openDetail(_detail!.id);
      } catch (_) {
        _snack('移除失败');
      }
    }
  }

  /// 已入库项 ✕：撤回入库确认（恢复入库前档位，流水记「撤回入库」）。
  Future<void> _confirmUndoRestock(ShoppingItemVO it) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('撤回「${it.displayName}」的入库？'),
        content: const Text('撤回后库存回到入库前的状态，这项从清单移除。流水里会记一笔「撤回入库」。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('留着')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: Text('撤回入库', style: AppTokens.of(ctx).textStyles.danger)),
        ],
      ),
    );
    if (ok == true) {
      try {
        await ShoppingService.undoRestock(it.id);
        _snack('已撤回入库');
        _openDetail(_detail!.id);
      } catch (_) {
        _snack('撤回失败');
      }
    }
  }

  // ===== 分享预览视图 =====
}
