part of '../create_page.dart';

/// 写菜谱页拆分（≤500 行）：State 方法以扩展形式放在 part 文件，私有成员同库可见。
extension _CreateIngredientRows on _CreateDishPageState {
  Widget _buildIngredientRow(int index, _IngredientRow row) {
    final t = AppTokens.of(context);
    // 单位输入实时匹配字典：匹配到 = 自动选中（文字橙色），不用再点一次
    final matchedUnit = _matchUnit(row.unitCtrl.text);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        side: BorderSide(color: t.border),
      ),
      color: t.card,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
        child: Row(
              children: [
                _ingAvatar(row.name),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(row.name,
                      style:
                          t.textStyles.md.copyWith(fontWeight: FontWeight.w700)),
                ),
                const SizedBox(width: 8),
                // 用量数字（下厨房式：单位自动带出，只填数字）
                SizedBox(
                  width: 56,
                  child: TextField(
                    controller: row.amountCtrl,
                    keyboardType: TextInputType.number,
                    textAlign: TextAlign.right,
                    style: t.textStyles.sm.copyWith(color: t.title),
                    decoration: InputDecoration(
                      hintText: '用量',
                      hintStyle: t.textStyles.sm.copyWith(color: t.caption),
                      isDense: true,
                      filled: true,
                      fillColor: t.bg,
                      contentPadding:
                          const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        borderSide: BorderSide(color: t.border),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        borderSide: BorderSide(color: t.border),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        borderSide: BorderSide(color: t.primary, width: 1.5),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 6),
                // 单位：可输入（实时匹配自动选中），右侧 ▾ 点开选择弹层（chips 收起）
                SizedBox(
                  width: 72,
                  child: TextField(
                    controller: row.unitCtrl,
                    onChanged: (_) => _refresh(() {}),
                    textAlign: TextAlign.center,
                    style: t.textStyles.sm.copyWith(
                      color: matchedUnit != null ? t.primary : t.title,
                      fontWeight:
                          matchedUnit != null ? FontWeight.w800 : FontWeight.w400,
                    ),
                    decoration: InputDecoration(
                      hintText: '单位',
                      hintStyle: t.textStyles.sm.copyWith(color: t.caption),
                      isDense: true,
                      filled: true,
                      fillColor: t.bg,
                      contentPadding:
                          const EdgeInsets.symmetric(horizontal: 6, vertical: 6),
                      suffixIcon: GestureDetector(
                        onTap: () => _pickUnit(row),
                        child: Icon(Icons.arrow_drop_down,
                            size: 18, color: t.caption),
                      ),
                      suffixIconConstraints: const BoxConstraints(
                          minWidth: 20, minHeight: 20),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        borderSide: BorderSide(
                            color: matchedUnit != null ? t.primary : t.border),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        borderSide: BorderSide(
                            color: matchedUnit != null ? t.primary : t.border),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        borderSide: BorderSide(color: t.primary, width: 1.5),
                      ),
                    ),
                  ),
                ),
                InkWell(
                  onTap: () => _refresh(() {
                    _ingredients.removeAt(index).dispose();
                  }),
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Icon(Icons.close, size: 18, color: t.caption),
                  ),
                ),
              ],
        ),
      ),
    );
  }

  /// 单位选择弹层（▾ 点开）：列出单位字典，点选填入单位框；也可直接输入自动匹配。
  Future<void> _pickUnit(_IngredientRow row) async {
    final t = AppTokens.of(context);
    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: t.card,
      shape: const RoundedRectangleBorder(
        borderRadius:
            BorderRadius.vertical(top: Radius.circular(AppTokens.rLg)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 36,
                  height: 4,
                  decoration: BoxDecoration(
                    color: t.border,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Text('选择单位', style: t.textStyles.subtitle),
              const SizedBox(height: 10),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: _unitDict.map((u) {
                  final sel = _matchUnit(row.unitCtrl.text)?.id == u.id;
                  return GestureDetector(
                    onTap: () {
                      _refresh(() => row.unitCtrl.text = u.name);
                      Navigator.pop(ctx);
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 12, vertical: 6),
                      decoration: BoxDecoration(
                        color: sel ? t.primary : t.bg,
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        border: sel ? null : Border.all(color: t.border),
                      ),
                      child: Text(u.name,
                          style: t.textStyles.xs.copyWith(
                            color: sel ? Colors.white : t.body,
                            fontWeight: FontWeight.w800,
                          )),
                    ),
                  );
                }).toList(),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 单位文本精确匹配单位字典（匹配到即自动选中，§16.3）。
  DictItem? _matchUnit(String text) {
    final s = text.trim();
    if (s.isEmpty) return null;
    for (final u in _unitDict) {
      if (u.name == s) return u;
    }
    return null;
  }

  Widget _buildAddIngredientButton() {
    final t = AppTokens.of(context);
    return SizedBox(
      height: 44,
      child: OutlinedButton.icon(
        onPressed: _onAddIngredient,
        icon: const Icon(Icons.add, size: 18),
        label: const Text('加用料'),
        style: OutlinedButton.styleFrom(
          foregroundColor: t.accent,
          side: BorderSide(color: t.primary.withAlpha(100)),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppTokens.rMd),
          ),
        ),
      ),
    );
  }
}
