part of '../detail_page.dart';

/// Tab 2：采购（原型 MenuShop）——本食集全部用料按「家里没有 / 有但不够 / 家里够」分组，
/// 勾选买到的 →「买到的放进余量」批量入库。完整编辑/分享进全局采购清单页。
class _ShopTab extends StatefulWidget {
  final int menuId;
  final int refreshTick;
  const _ShopTab({required this.menuId, required this.refreshTick});

  @override
  State<_ShopTab> createState() => _ShopTabState();
}

class _ShopTabState extends State<_ShopTab> {
  ShoppingListVO? _list;
  bool _loading = true;
  Object? _error;
  bool _busy = false;
  final Set<int> _picked = {};

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(_ShopTab old) {
    super.didUpdateWidget(old);
    if (old.refreshTick != widget.refreshTick) _load();
  }

  Future<void> _load() async {
    try {
      final l = await ShoppingService.getByMenu(widget.menuId);
      if (!mounted) return;
      setState(() {
        _list = l;
        _error = null;
        _loading = false;
        _picked.removeWhere((id) => !(l?.items.any((i) => i.id == id && !i.isPurchased) ?? false));
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

  Future<void> _generate() async {
    setState(() => _busy = true);
    try {
      await ShoppingService.generateFrom('menu', sourceId: widget.menuId);
      await _load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('生成失败：${errorText(e)}')));
      }
    }
    if (mounted) setState(() => _busy = false);
  }

  Future<void> _restock() async {
    if (_picked.isEmpty) return;
    setState(() => _busy = true);
    try {
      final r = await ShoppingService.restock(_picked.toList());
      _picked.clear();
      await _load();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('已放进余量 ${r.restocked} 样')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('放进余量失败：${errorText(e)}')));
      }
    }
    if (mounted) setState(() => _busy = false);
  }

  static String _bucket(ShoppingItemVO i) => switch (i.stockStatus) {
        'ENOUGH' => 'enough',
        'LOW' => 'low',
        _ => 'none',
      };

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    if (_loading) return const SkeletonList(count: 5, thumb: 40);
    if (_error != null) return ErrorView(text: '采购清单加载失败', onRetry: _load, error: _error, compact: true);
    final l = _list;
    if (l == null || l.items.isEmpty) {
      return ListView(children: [
        const SizedBox(height: 40),
        EmptyView(
          text: '还没有采购清单',
          subtitle: '按这顿的菜把用料列出来，并标出家里有没有',
          illustration: const StateIllustration(keys: ['tomato', 'egg', 'scallion']),
          actionLabel: _busy ? '生成中…' : '生成采购清单',
          onAction: _busy ? null : _generate,
        ),
      ]);
    }
    final groups = <String, List<ShoppingItemVO>>{'none': [], 'low': [], 'enough': []};
    for (final i in l.items) {
      groups[_bucket(i)]!.add(i);
    }
    const titles = {'none': '家里没有', 'low': '有但不够', 'enough': '家里够 · 不用买'};
    return Column(
      children: [
        Expanded(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp24),
            children: [
              Container(
                padding: const EdgeInsets.all(AppTokens.sp12),
                decoration: BoxDecoration(color: AppTokens.infoBg, borderRadius: BorderRadius.circular(AppTokens.rMd)),
                child: Text('这里列出这顿饭的全部用料，并标出家里有没有。家里够的不用买。',
                    style: ts.caption.copyWith(color: AppTokens.infoText)),
              ),
              const SizedBox(height: AppTokens.sp12),
              StatGrid(height: 58, items: [
                StatItem('${groups['none']!.length}', '没有', tone: StatTone.error),
                StatItem('${groups['low']!.length}', '有但不够', tone: StatTone.warning),
                StatItem('${groups['enough']!.length}', '家里够', tone: StatTone.success),
              ]),
              for (final k in ['none', 'low', 'enough'])
                if (groups[k]!.isNotEmpty) ...[
                  Padding(
                    padding: const EdgeInsets.only(top: AppTokens.sp16, bottom: AppTokens.sp8),
                    child: Text('${titles[k]} · ${groups[k]!.length}', style: ts.sectionLabel),
                  ),
                  for (final it in groups[k]!) _row(t, it, muted: k == 'enough'),
                ],
            ],
          ),
        ),
        Container(
          decoration: BoxDecoration(color: t.card, border: Border(top: BorderSide(color: t.border))),
          padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp10, AppTokens.sp16, AppTokens.sp10),
          child: SafeArea(
            top: false,
            child: Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => context.push('/shopping?listId=${l.id}'),
                    icon: const Icon(Icons.ios_share_rounded, size: 18),
                    label: const Text('转图片 / 编辑'),
                  ),
                ),
                const SizedBox(width: AppTokens.sp12),
                Expanded(
                  child: ElevatedButton(
                    onPressed: (_busy || _picked.isEmpty) ? null : _restock,
                    child: Text(_picked.isEmpty ? '勾选买到的' : '放进余量 · ${_picked.length} 样'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _row(AppTokens t, ShoppingItemVO it, {required bool muted}) {
    final ts = t.textStyles;
    final bought = it.isPurchased;
    final picked = _picked.contains(it.id);
    final sub = bought ? '已买' : (it.amountText.isEmpty ? '' : it.amountText);
    return Opacity(
      opacity: muted || bought ? 0.6 : 1,
      child: Container(
        margin: const EdgeInsets.only(bottom: AppTokens.sp8),
        decoration: BoxDecoration(
          color: t.card,
          borderRadius: BorderRadius.circular(AppTokens.rLg),
          border: Border.all(color: picked ? t.primaryDeep : t.border),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(AppTokens.rLg),
          onTap: bought || muted
              ? null
              : () => setState(() => picked ? _picked.remove(it.id) : _picked.add(it.id)),
          child: Padding(
            padding: const EdgeInsets.all(AppTokens.sp10),
            child: Row(
              children: [
                Icon(
                  bought || picked ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded,
                  size: 22,
                  color: bought ? AppTokens.success : (picked ? t.primaryDeep : t.border),
                ),
                const SizedBox(width: AppTokens.sp10),
                IngredientIcon(
                  name: it.displayName,
                  iconKey: it.iconKey,
                  category: it.purchaseCategoryName,
                  size: 40,
                ),
                const SizedBox(width: AppTokens.sp12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(it.displayName,
                          style: ts.cardTitle.copyWith(
                            color: t.title,
                            decoration: bought ? TextDecoration.lineThrough : null,
                          )),
                      if (sub.isNotEmpty) Text(sub, style: ts.caption),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
