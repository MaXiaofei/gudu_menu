part of '../detail_page.dart';

/// 一键加采购弹窗（V42，对齐 menu-detail-beicai.html）：
/// 备菜项列表，默认勾选家里 用完/不足 的项，可改；确认 → 加入采购清单。
class _PrepShoppingSheet extends StatefulWidget {
  final List<PrepItem> items;
  const _PrepShoppingSheet({required this.items});

  @override
  State<_PrepShoppingSheet> createState() => _PrepShoppingSheetState();
}

class _PrepShoppingSheetState extends State<_PrepShoppingSheet> {
  late final Set<int> _selected;

  @override
  void initState() {
    super.initState();
    _selected = {
      for (final it in widget.items)
        if (it.stockLevel == 'NONE' || it.stockLevel == 'LOW') it.ingredientId,
    };
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const SizedBox(height: 10),
          Container(width: 36, height: 4, decoration: BoxDecoration(
            color: t.border, borderRadius: BorderRadius.circular(2))),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 12, 18, 4),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('加入采购清单', style: t.textStyles.subtitle),
              const SizedBox(height: 2),
              Text('默认勾选家里没有/不足的，可改',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
            ]),
          ),
          Flexible(
            child: ListView(
              shrinkWrap: true,
              children: [
                for (final it in widget.items)
                  InkWell(
                    onTap: () => setState(() {
                      if (!_selected.remove(it.ingredientId)) {
                        _selected.add(it.ingredientId);
                      }
                    }),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
                      child: Row(children: [
                        Container(
                          width: 18, height: 18,
                          decoration: BoxDecoration(
                            color: _selected.contains(it.ingredientId) ? _prepStockColor(it.stockLevel ?? 'NONE') : null,
                            border: Border.all(
                                color: _selected.contains(it.ingredientId)
                                    ? _prepStockColor(it.stockLevel ?? 'NONE')
                                    : t.border),
                            borderRadius: BorderRadius.circular(AppTokens.rXs),
                          ),
                          child: _selected.contains(it.ingredientId)
                              ? const Icon(Icons.check, size: 13, color: Colors.white)
                              : null,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(it.ingredientName,
                              style: t.textStyles.md.copyWith(color: t.title)),
                        ),
                        // V55 去单位：加采购弹窗不再显示克数
                        const SizedBox(width: 8),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                          decoration: BoxDecoration(
                            color: _prepStockColor(it.stockLevel ?? 'NONE'),
                            borderRadius: BorderRadius.circular(AppTokens.rPill),
                          ),
                          child: Text('家里：${_prepStockLabel(it.stockLevel ?? 'NONE')}',
                              style: t.textStyles.chip.copyWith(color: Colors.white)),
                        ),
                      ]),
                    ),
                  ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 6, 18, 12),
            child: Row(children: [
              Expanded(
                child: OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(color: t.border),
                    foregroundColor: t.caption,
                    minimumSize: const Size.fromHeight(46),
                  ),
                  onPressed: () => Navigator.pop(context),
                  child: const Text('取消'),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                flex: 2,
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: t.primary,
                    foregroundColor: Colors.white,
                    minimumSize: const Size.fromHeight(46),
                  ),
                  onPressed: () => Navigator.pop(context, Set<int>.of(_selected)),
                  child: Text('加入 · ${_selected.length} 项'),
                ),
              ),
            ]),
          ),
        ],
      ),
    );
  }
}
