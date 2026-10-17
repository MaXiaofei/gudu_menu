part of '../shopping_page.dart';

extension _ShoppingSheets on _ShoppingPageState {
  /// 添加弹窗：名称 + 数量单位一个框（如 2斤），逐条添加、行尾删除、最后保存。
  void _showAddSheet() {
    final t = AppTokens.of(context);
    final rows = <(String, String)>[]; // (名称, 数量文本)
    final nameCtrl = TextEditingController();
    final amountCtrl = TextEditingController();
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(AppTokens.rLg))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheetState) {
          // 「再加一行」：当前输入行入列（名称空忽略），清空输入继续编辑
          void commitRow() {
            setSheetState(() {
              final name = nameCtrl.text.trim();
              if (name.isEmpty) return;
              rows.add((name, amountCtrl.text.trim()));
              nameCtrl.clear();
              amountCtrl.clear();
            });
          }

          // 待保存总行数：已添加列表 + 当前输入行（空行不计）
          final pendingName = nameCtrl.text.trim();
          final saveCount = rows.length + (pendingName.isNotEmpty ? 1 : 0);
          final canSave = saveCount > 0;

          return Padding(
            padding: EdgeInsets.only(
                left: 16, right: 16, top: 16, bottom: MediaQuery.of(ctx).viewInsets.bottom + 16),
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text('添加采购项', style: AppTokens.of(ctx).textStyles.subtitle),
              const SizedBox(height: 4),
              Text('食材、生活用品都可以记（当备忘单用）',
                  style: t.textStyles.sm.copyWith(color: t.caption)),
              const SizedBox(height: 12),
              // 输入行：名称 + 数量单位一个框 + 「再加一行」（入列后清空继续编辑）
              Row(children: [
                Expanded(
                  flex: 2,
                  child: TextField(
                    controller: nameCtrl,
                    onChanged: (_) => setSheetState(() {}),
                    onSubmitted: (_) => commitRow(),
                    decoration: InputDecoration(
                        hintText: '名称',
                        isDense: true,
                        filled: true,
                        fillColor: t.bg,
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd))),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: TextField(
                    controller: amountCtrl,
                    onChanged: (_) => setSheetState(() {}),
                    decoration: InputDecoration(
                        hintText: '数量+单位 · 如 2斤',
                        isDense: true,
                        filled: true,
                        fillColor: t.bg,
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd))),
                  ),
                ),
                const SizedBox(width: 8),
                ElevatedButton(
                  // 全局主题是 Size(inf,48)（全宽 CTA），本按钮在 Row（无界宽）里
                  // 会触发 "infinite width" 布局崩溃，必须显式有限 minimumSize
                  style: ElevatedButton.styleFrom(
                    backgroundColor: t.primary,
                    foregroundColor: Colors.white,
                    minimumSize: const Size(0, 40),
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                  ),
                  onPressed: commitRow,
                  child: const Text('再加一行'),
                ),
              ]),
              const SizedBox(height: 12),
              // 已添加列表：行尾 ✕ 删除
              if (rows.isNotEmpty) ...[
                Text('已添加 ${rows.length} 种', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
                const SizedBox(height: 4),
                for (int i = 0; i < rows.length; i++)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 5),
                    child: Row(children: [
                      Expanded(
                        child: Text(
                          '${rows[i].$1}${rows[i].$2.isEmpty ? '' : '  ${rows[i].$2}'}',
                          style: t.textStyles.md.copyWith(color: t.title),
                        ),
                      ),
                      GestureDetector(
                        onTap: () => setSheetState(() => rows.removeAt(i)),
                        child: const Padding(
                          padding: EdgeInsets.all(4),
                          child: Icon(Icons.close, size: 16),
                        ),
                      ),
                    ]),
                  ),
              ],
              const SizedBox(height: 12),
              SizedBox(
                height: 44,
                child: ElevatedButton(
                  // 输入即能保存（不再必须先点「再加一行」）；保存时自动纳入当前输入行并过滤空行
                  onPressed: canSave
                      ? () async {
                          final allRows = [
                            ...rows,
                            if (pendingName.isNotEmpty) (pendingName, amountCtrl.text.trim()),
                          ];
                          try {
                            for (final (name, amountText) in allRows) {
                              // 数量单位一个框：解析数字部分，单位忽略（备忘单场景够用）
                              final amount = double.tryParse(
                                  RegExp(r'\d+(\.\d+)?').firstMatch(amountText)?.group(0) ?? '');
                              await ShoppingService.addCustomItem(_detail!.id, name, amount: amount);
                            }
                            if (ctx.mounted) Navigator.pop(ctx);
                            _openDetail(_detail!.id);
                          } catch (e) {
                            debugPrint('添加采购项失败: $e'); // 便于日志定位
                            if (ctx.mounted) {
                              ScaffoldMessenger.of(ctx)
                                  .showSnackBar(SnackBar(content: Text('添加失败: $e')));
                            }
                          }
                        }
                      : null,
                  child: Text(canSave ? '添加 · 保存 $saveCount 种' : '保存'),
                ),
              ),
            ]),
          );
        },
      ),
    );
  }

  /// 改名弹窗（自定义采购 ✎）。
  Future<void> _showRenameSheet() async {
    final t = AppTokens.of(context);
    final d = _detail!;
    final ctrl = TextEditingController(text: d.name ?? '');
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('修改清单名称'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('列表页和分享内容会同步显示新名字',
              style: t.textStyles.sm.copyWith(color: t.caption)),
          const SizedBox(height: 10),
          TextField(
            controller: ctrl,
            autofocus: true,
            decoration: InputDecoration(
              hintText: '清单名称',
              isDense: true,
              filled: true,
              fillColor: t.bg,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
          ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
              child: const Text('保存')),
        ],
      ),
    );
    if (result == null || result.isEmpty) return;
    try {
      await ShoppingService.renameList(d.id, result);
      _openDetail(d.id);
    } catch (_) {
      _snack('改名失败');
    }
  }
}
