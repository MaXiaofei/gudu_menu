part of '../shopping_page.dart';

extension _ShoppingShareView on _ShoppingPageState {
  void _openShare() => _refresh(() => _showShare = true);

  Widget _buildShareView() {
    final t = AppTokens.of(context);
    final d = _detail!;
    // 未入库项（要买的）= 分享内容
    final unpurchased = d.items.where((it) => it.purchased == 0).toList();
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
            icon: const Icon(Icons.arrow_back), onPressed: () => _refresh(() => _showShare = false)),
        title: const Text('分享采购清单'),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
            child: Text('预览与导出的内容、格式完全一致',
                style: t.textStyles.sm.copyWith(color: t.caption)),
          ),
          Expanded(
            child: Stack(
              children: [
                ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    if (unpurchased.isEmpty)
                      Text('清单里的都入库了，没有要分享的项',
                          style: t.textStyles.sm.copyWith(color: t.caption)),
                  ],
                ),
                // 屏幕外分享卡片：仅用于截图（RepaintBoundary.toImage），不参与显示
                Positioned(
                  left: -10000,
                  top: 0,
                  child: RepaintBoundary(
                    key: _shareBoundaryKey,
                    child: _ShareCard(listName: _shareTitle(d), items: unpurchased),
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            decoration: BoxDecoration(
              color: t.card,
              border: Border(top: BorderSide(color: t.border)),
            ),
            child: Column(children: [
              _shareAction(t, '文', '复制文字', '粘贴到微信/备忘录，别人照着买', _copyShare),
              const SizedBox(height: 8),
              _shareAction(t, '图', '转图片分享', '生成一张清单图，发群里/相册', _shareImage),
            ]),
          ),
        ],
      ),
    );
  }

  Widget _shareAction(AppTokens t, String icon, String title, String desc, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 11),
        decoration: BoxDecoration(
          border: Border.all(color: t.border, width: 1.5),
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
        child: Row(children: [
          Container(
            width: 34, height: 34,
            decoration: BoxDecoration(color: t.secondary, borderRadius: BorderRadius.circular(10)),
            alignment: Alignment.center,
            child: Text(icon, style: t.textStyles.sm.copyWith(color: t.primaryDeep)),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: t.textStyles.md.copyWith(color: t.title, fontWeight: FontWeight.w800)),
              Text(desc, style: t.textStyles.sm.copyWith(color: t.caption)),
            ]),
          ),
        ]),
      ),
    );
  }

  /// 分享标题：自定义清单名 或 食集清单「采购单 #N」。
  String _shareTitle(ShoppingListVO d) {
    final hasName = d.name != null && d.name!.isNotEmpty;
    return hasName ? d.name! : '采购单 #${d.id}';
  }

  /// 分享文字：标题 + 日期 + 未入库项逐行「名称 用量」。
  Future<void> _copyShare() async {
    final d = _detail!;
    final now = DateTime.now();
    final lines = d.items
        .where((it) => it.purchased == 0)
        .map((it) => it.amountText.isEmpty
            ? it.displayName
            : '${it.displayName} ${it.amountText}')
        .toList();
    final text = [
      '${_shareTitle(d)} · ${now.month} 月 ${now.day} 日',
      ...lines,
    ].join('\n');
    await Clipboard.setData(ClipboardData(text: text));
    if (mounted) _snack('采购清单已复制');
  }

  /// 转图片分享：截图屏幕外分享卡片 → PNG → 系统分享面板（share_plus）。
  Future<void> _shareImage() async {
    try {
      await WidgetsBinding.instance.endOfFrame;
      final boundary = _shareBoundaryKey.currentContext?.findRenderObject()
          as RenderRepaintBoundary?;
      if (boundary == null) return;
      final image = await boundary.toImage(pixelRatio: 3);
      final bytes = (await image.toByteData(format: ui.ImageByteFormat.png))!
          .buffer
          .asUint8List();
      await Share.shareXFiles(
        [XFile.fromData(bytes, mimeType: 'image/png', name: 'shopping_list.png')],
        text: '采购清单',
      );
    } catch (_) {
      if (mounted) _snack('生成分享图片失败');
    }
  }

  // ===== 手动添加（自定义采购） =====
}

/// 分享卡片（仅截图用，置于屏幕外）：白底 + 清单名·日期 + 未入库项列表。
class _ShareCard extends StatelessWidget {
  final String listName;
  final List<ShoppingItemVO> items;
  const _ShareCard({required this.listName, required this.items});

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final now = DateTime.now();
    // 按采购品类分组（原型 ShopShare）
    final groups = <String, List<ShoppingItemVO>>{};
    for (final it in items) {
      groups.putIfAbsent(it.purchaseCategoryName ?? '其他', () => []).add(it);
    }
    return Container(
      width: 320,
      color: t.card,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 品牌头：浅主色底 + App 图标 + 清单名
          Container(
            padding: const EdgeInsets.all(AppTokens.sp16),
            color: t.secondary,
            child: Row(children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(AppTokens.rMd),
                child: Image.asset('assets/brand/logo.png', width: 40, height: 40),
              ),
              const SizedBox(width: AppTokens.sp12),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('咕嘟小食单 · 采购清单', style: ts.sectionLabel),
                  Text(listName, style: ts.lg.copyWith(fontWeight: FontWeight.w700)),
                ]),
              ),
              Text('${now.month} 月 ${now.day} 日 · ${items.length} 样', style: ts.chip),
            ]),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              for (final e in groups.entries) ...[
                Padding(
                  padding: const EdgeInsets.only(top: AppTokens.sp10, bottom: AppTokens.sp4),
                  child: Text(e.key, style: ts.sectionLabel),
                ),
                for (final it in e.value)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: AppTokens.sp4),
                    child: Row(children: [
                      IngredientIcon(
                        name: it.displayName,
                        iconKey: it.iconKey,
                        category: it.purchaseCategoryName,
                        size: 28,
                      ),
                      const SizedBox(width: AppTokens.sp10),
                      Expanded(child: Text(it.displayName, style: ts.body.copyWith(color: t.title))),
                      if (it.amountText.isNotEmpty) Text(it.amountText, style: ts.caption),
                    ]),
                  ),
              ],
            ]),
          ),
        ],
      ),
    );
  }
}
