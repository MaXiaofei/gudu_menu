part of '../create_page.dart';

/// 写菜谱页拆分（≤500 行）：State 方法以扩展形式放在 part 文件，私有成员同库可见。
extension _CreateIngredientAdd on _CreateDishPageState {
  /// 「＋ 加用料」弹层：常用食材 chips 快捷 + 搜索匹配（结果搜索后才显示）+ 新建食材。
  Future<void> _onAddIngredient() async {
    final t = AppTokens.of(context);
    var keyword = '';
    var results = <IngredientItem>[];

    Future<void> onSearch(String v) async {
      final kw = v.trim();
      keyword = kw;
      if (kw.isEmpty) {
        results = [];
        return;
      }
      final r = await IngredientService.search(kw);
      results = r;
    }

    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: t.card,
      shape: const RoundedRectangleBorder(
        borderRadius:
            BorderRadius.vertical(top: Radius.circular(AppTokens.rLg)),
      ),
      isScrollControlled: true,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheet) => Padding(
          padding: EdgeInsets.only(
              bottom: MediaQuery.of(ctx).viewInsets.bottom),
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 20),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 拖拽把手
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
                Text('常用直接点；其他输入名称匹配，没有就新建',
                    style: t.textStyles.xs.copyWith(color: t.caption)),
                const SizedBox(height: 10),
                // 搜索框（统一 SearchBox：白底单层 + 框内放大镜，§11.1.2）
                SearchBox(
                  hint: '搜食材库',
                  onChanged: (v) async {
                    await onSearch(v);
                    if (ctx.mounted) setSheet(() {});
                  },
                ),
                // 常用食材 chips（未搜索时显示）
                if (keyword.isEmpty && _commonIngredients.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text('常用',
                      style: t.textStyles.xs.copyWith(
                          color: t.caption, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: _commonIngredients.map((ing) {
                      return InkWell(
                        onTap: () => _pickIngredient(ctx, ing),
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 11, vertical: 5),
                          decoration: BoxDecoration(
                            color: t.highlight,
                            borderRadius:
                                BorderRadius.circular(AppTokens.rSm),
                            border: Border.all(color: t.border),
                          ),
                          child: Text(ing.name,
                              style: t.textStyles.xs.copyWith(
                                  color: t.accent,
                                  fontWeight: FontWeight.w800)),
                        ),
                      );
                    }).toList(),
                  ),
                ],
                // 搜索结果（输入后才显示）
                if (keyword.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text('匹配「$keyword」',
                      style: t.textStyles.xs.copyWith(
                          color: t.caption, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 4),
                  if (results.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      child: Text('没有匹配的食材',
                          style: t.textStyles.sm.copyWith(color: t.caption)),
                    )
                  else
                    ...results.map((ing) {
                      return InkWell(
                        onTap: () => _pickIngredient(ctx, ing),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 8),
                          child: Row(
                            children: [
                              _ingAvatar(ing.name),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(ing.name,
                                    style: t.textStyles.md
                                        .copyWith(fontWeight: FontWeight.w700)),
                              ),
                              Text('选',
                                  style: t.textStyles.sm.copyWith(
                                      color: t.accent,
                                      fontWeight: FontWeight.w800)),
                            ],
                          ),
                        ),
                      );
                    }),
                ],
                const SizedBox(height: 12),
                // 新建食材（共用弹层 §16.5：名称/分类/默认单位/克换算/单价，建档即加行）
                InkWell(
                  onTap: () => _onCreateIngredient(ctx, presetName: keyword),
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(AppTokens.rMd),
                      border:
                          Border.all(color: t.primary.withAlpha(100)),
                    ),
                    child: Text('＋ 新建食材',
                        textAlign: TextAlign.center,
                        style: t.textStyles.md.copyWith(
                            color: t.accent, fontWeight: FontWeight.w800)),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _pickIngredient(BuildContext ctx, IngredientItem ing) {
    if (_ingredients.any((r) => r.ingredientId == ing.id)) {
      Navigator.pop(ctx);
      _showSnack('已加过「${ing.name}」');
      return;
    }
    // V55（食材去单位）：不再自动带出单位，菜谱用量单位由用户手填
    _refresh(() =>
        _ingredients.add(_IngredientRow(ingredientId: ing.id, name: ing.name)));
    Navigator.pop(ctx);
  }

  /// 新建食材共用弹层（原型 ⑤ 屏，§16.5）：名称 + 分类。
  /// 建档成功 → 自动加为用料行并回到表单（菜谱加料与采购入库同用此弹层）。
  /// V55（食材去单位）：默认单位/克换算/单价随单位解绑删除。
  /// [presetName] 用料弹层搜索词预填（食材库没有时不用再输一遍）。
  Future<void> _onCreateIngredient(BuildContext sheetCtx,
      {String presetName = ''}) async {
    Navigator.pop(sheetCtx); // 先关用料弹层
    final t = AppTokens.of(context);
    final nameCtrl = TextEditingController(text: presetName);
    int? catId; // 分类（可选）
    var saving = false;

    Future<void> createAndPick() async {
      final name = nameCtrl.text.trim();
      if (name.isEmpty) return;
      saving = true;
      try {
        final ingId = await IngredientService.createIngredient({
          'ingredient': {
            'name': name,
            if (catId != null) 'purchaseCategoryId': catId,
          },
          'nutritions': [],
        });
        if (!mounted) return;
        _refresh(() {
          _ingredients.add(_IngredientRow(ingredientId: ingId, name: name));
          _commonIngredients = [
            IngredientItem(id: ingId, name: name),
            ..._commonIngredients,
          ];
        });
        _showSnack('已建档「$name」');
      } catch (e) {
        _showSnack('建档失败: $e');
      }
    }

    await showModalBottomSheet<void>(
      context: context,
      backgroundColor: t.card,
      shape: const RoundedRectangleBorder(
        borderRadius:
            BorderRadius.vertical(top: Radius.circular(AppTokens.rLg)),
      ),
      isScrollControlled: true,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheet) => Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(ctx).viewInsets.bottom),
          child: SingleChildScrollView(
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
                Text('新建食材', style: t.textStyles.subtitle),
                const SizedBox(height: 2),
                Text('名称必填，分类可后补',
                    style: t.textStyles.xs.copyWith(color: t.caption)),
                // 名称 *
                const SizedBox(height: 12),
                Text('名称',
                    style: t.textStyles.xs.copyWith(
                        color: t.caption,
                        fontWeight: FontWeight.w800,
                        letterSpacing: 1)),
                const SizedBox(height: 5),
                TextField(
                  controller: nameCtrl,
                  onChanged: (_) => setSheet(() {}),
                  style: t.textStyles.sm,
                  decoration: InputDecoration(
                    hintText: '如：豆腐',
                    filled: true,
                    fillColor: t.bg,
                    isDense: true,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppTokens.rMd),
                      borderSide: BorderSide(color: t.border),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppTokens.rMd),
                      borderSide: BorderSide(color: t.border),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(AppTokens.rMd),
                      borderSide: BorderSide(color: t.primary, width: 1.5),
                    ),
                  ),
                ),
                // 分类（可选）
                if (_catDict.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  Text('分类（可选）',
                      style: t.textStyles.xs.copyWith(
                          color: t.caption,
                          fontWeight: FontWeight.w800,
                          letterSpacing: 1)),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: _catDict.map((c) {
                      final sel = catId == c.id;
                      return InkWell(
                        onTap: () => setSheet(() => catId = sel ? null : c.id),
                        borderRadius: BorderRadius.circular(AppTokens.rSm),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 11, vertical: 5),
                          decoration: BoxDecoration(
                            color: sel ? t.primary : t.card,
                            borderRadius: BorderRadius.circular(AppTokens.rSm),
                            border: sel ? null : Border.all(color: t.border),
                          ),
                          child: Text(c.name,
                              style: t.textStyles.xs.copyWith(
                                color: sel ? t.card : t.body,
                                fontWeight: FontWeight.w800,
                              )),
                        ),
                      );
                    }).toList(),
                  ),
                ],
                // V55（食材去单位）：默认单位/克换算/单价区块已删除
                const SizedBox(height: 14),
                SizedBox(
                  width: double.infinity,
                  height: 46,
                  child: ElevatedButton(
                    onPressed: nameCtrl.text.trim().isEmpty
                        ? null
                        : () async {
                            setSheet(() => saving = true);
                            await createAndPick();
                            if (ctx.mounted) Navigator.pop(ctx);
                          },
                    child: saving
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white),
                          )
                        : Text('建档',
                            style:
                                t.textStyles.lg.copyWith(color: Colors.white)),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  // ========== URL 导入 ==========
}
