part of '../create_page.dart';

/// 写菜谱页拆分（≤500 行）：State 方法以扩展形式放在 part 文件，私有成员同库可见。
extension _CreateFormBasic on _CreateDishPageState {
  Widget _buildManualEntry() {
    final t = AppTokens.of(context);
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 字段顺序：封面 → 菜名 → 备料/烹饪 → 难度 → 标签(必选) → 菜系(可选) → 菜谱介绍
          _buildCoverImage(),
          const SizedBox(height: 16),
          _buildNameField(),
          const SizedBox(height: 16),
          _buildTimeRow(),
          const SizedBox(height: 16),
          _buildDifficultySelector(),
          const SizedBox(height: 16),
          _buildTagSelector(),
          const SizedBox(height: 16),
          _buildCuisineSelector(),
          const SizedBox(height: 16),
          _buildNoteField(),
          const SizedBox(height: 24),
          _buildSectionDivider('用料'),
          const SizedBox(height: 12),
          // 无空态提示卡：没加用料时直接是「加用料」按钮
          ..._ingredients.asMap().entries.map(
              (e) => _buildIngredientRow(e.key, e.value)),
          const SizedBox(height: 8),
          _buildAddIngredientButton(),
          const SizedBox(height: 24),
          _buildSectionDivider('做法步骤'),
          const SizedBox(height: 12),
          ..._steps.asMap().entries.map((e) => _buildStepCard(e.key, e.value)),
          const SizedBox(height: 8),
          _buildAddStepButton(),
          const SizedBox(height: 16),
          // 三操作：存草稿（不校验必填）/ 预览（发布前看详情）/ 发布（§16.4）
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 48,
                  child: OutlinedButton(
                    onPressed: _saving ? null : _onSaveDraft,
                    child: Text('存草稿',
                        style: t.textStyles.lg.copyWith(color: t.accent)),
                  ),
                ),
              ),
              const SizedBox(width: AppTokens.sp8),
              Expanded(
                child: SizedBox(
                  height: 48,
                  child: OutlinedButton(
                    onPressed: _saving ? null : _onPreview,
                    child: Text('预览',
                        style: t.textStyles.lg.copyWith(color: t.accent)),
                  ),
                ),
              ),
              const SizedBox(width: AppTokens.sp8),
              Expanded(
                child: SizedBox(
                  height: 48,
                  child: ElevatedButton(
                    onPressed: _saving ? null : _onSave,
                    child: _saving
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: Colors.white,
                            ),
                          )
                        : Text('发布',
                            style: t.textStyles.lg.copyWith(color: Colors.white)),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildCoverImage() {
    final t = AppTokens.of(context);
    // 本地新选图 / 草稿回填的已上传图（§16.4）都算"已有封面"
    final hasCover = _coverFile != null || _coverUrl != null;
    return InkWell(
      onTap: hasCover ? null : _onPickCover,
      borderRadius: BorderRadius.circular(AppTokens.rMd),
      hoverColor: t.primary.withValues(alpha: 0.08),
      child: Container(
        height: 96, // 原型 ③ 屏：封面是矮横幅（可选）
        decoration: BoxDecoration(
          color: t.secondary,
          borderRadius: BorderRadius.circular(AppTokens.rMd),
          border: !hasCover
              ? Border.all(color: t.border, style: BorderStyle.solid, width: 1.5)
              : null,
        ),
        clipBehavior: Clip.antiAlias,
        child: hasCover
            ? Stack(
                fit: StackFit.expand,
                children: [
                  if (_coverFile != null)
                    Image.file(_coverFile!, fit: BoxFit.cover)
                  else
                    // 草稿回填 URL；加载失败回退首字占位（§10）
                    Image.network(
                      _coverUrl!,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => ColoredBox(
                        color: t.primarySoft,
                        child: Center(
                          child: Text('菜',
                              style: t.textStyles.h1
                                  .copyWith(color: t.primary.withValues(alpha: 0.4))),
                        ),
                      ),
                    ),
                  // 半透明遮罩 + 操作按钮
                  Positioned(
                    bottom: 0,
                    left: 0,
                    right: 0,
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: const BoxDecoration(
                        gradient: LinearGradient(
                          begin: Alignment.bottomCenter,
                          end: Alignment.topCenter,
                          colors: [
                            Colors.black54,
                            Colors.transparent,
                          ],
                        ),
                      ),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          _coverActionChip(
                            Icons.refresh,
                            '更换',
                            _onPickCover,
                          ),
                          const SizedBox(width: 16),
                          _coverActionChip(
                            Icons.delete_outline,
                            '删除',
                            _onRemoveCover,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              )
            : Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.add_a_photo_outlined,
                      size: 32, color: t.primary),
                  const SizedBox(height: 6),
                  Text(
                    '添加封面（可选）',
                    style: t.textStyles.sm.copyWith(
                        color: t.accent, fontWeight: FontWeight.w700),
                  ),
                ],
              ),
      ),
    );
  }

  Widget _coverActionChip(IconData icon, String label, VoidCallback onTap) {
    final t = AppTokens.of(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(AppTokens.rPill),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: t.card.withValues(alpha: 0.9),
          borderRadius: BorderRadius.circular(AppTokens.rPill),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 16, color: t.primary),
            const SizedBox(width: 4),
            Text(label,
                style: t.textStyles.sm.copyWith(color: t.primary, fontWeight: FontWeight.w600)),
          ],
        ),
      ),
    );
  }

  Widget _buildNameField() {
    final t = AppTokens.of(context);
    return TextField(
      controller: _nameCtrl,
      style: t.textStyles.lg,
      decoration: InputDecoration(
        labelText: '菜名',
        hintText: '如：番茄炒蛋',
        prefixIcon: const Icon(Icons.restaurant_menu_outlined),
        filled: true,
        fillColor: t.bg,
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
    );
  }

  Widget _buildTimeRow() {
    return Row(
      children: [
        Expanded(
          child: _numberField(
            _prepCtrl,
            '备料(分)',
            Icons.kitchen_outlined,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: _numberField(
            _cookCtrl,
            '烹饪(分)',
            Icons.local_fire_department_outlined,
          ),
        ),
      ],
    );
  }

  Widget _numberField(
      TextEditingController ctrl, String label, IconData icon) {
    final t = AppTokens.of(context);
    return TextField(
      controller: ctrl,
      keyboardType: TextInputType.number,
      style: t.textStyles.md,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon, size: 20),
        filled: true,
        fillColor: t.bg,
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
        contentPadding:
            const EdgeInsets.symmetric(horizontal: 12, vertical: 16),
      ),
    );
  }

  /// 难度档位文案（1-5 星，随选择变化显示）。
  static const _difficultyLabels = ['超简单', '简单', '中等', '困难', '炼狱'];

  Widget _buildDifficultySelector() {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('难度',
            style: ts.md),
        const SizedBox(height: 8),
        Row(
          children: List.generate(5, (i) {
            final active = i < _difficulty;
            return InkWell(
              onTap: () => _refresh(() => _difficulty = i + 1),
              borderRadius: BorderRadius.circular(AppTokens.rPill),
              child: Padding(
                padding: const EdgeInsets.only(right: 8),
                // 星级与评价页一致（§16.2 / cookbook-add.html）
                child: Text(
                  active ? '★' : '☆',
                  style: ts.h3.copyWith(
                    color: active ? t.primary : t.border,
                    letterSpacing: 2,
                  ),
                ),
              ),
            );
          }),
        ),
        const SizedBox(height: 6),
        // 星级下方：按当前选择显示难度文案（不显示「点星选」）
        Text(_difficultyLabels[_difficulty - 1],
            style: ts.xs.copyWith(color: t.caption)),
      ],
    );
  }

  /// 标签（必选）chips：默认只展示一行，▾ 展开/收起全部（找菜分类筛选的数据源，§16.2）。
  /// 标签（必选）chips：默认一行折叠，▾ 展开/收起全部（找菜分类筛选数据源，§16.2）。
  Widget _buildTagSelector() => _buildFoldableChips(
        title: '标签',
        required: true,
        dict: _tagDict,
        selected: _selectedTagIds,
        expanded: _tagExpanded,
        onToggleExpanded: () => _refresh(() => _tagExpanded = !_tagExpanded),
        onToggleItem: (id) => _refresh(() {
          if (!_selectedTagIds.add(id)) _selectedTagIds.remove(id);
        }),
      );

  /// 菜系（可选，采用标签的方式处理：多选 chips + 一行折叠 ▾，§16.2）。
  Widget _buildCuisineSelector() => _buildFoldableChips(
        title: '菜系（可选）',
        required: false,
        dict: _cuisineDict,
        selected: _selectedCuisineIds,
        expanded: _cuisineExpanded,
        onToggleExpanded: () =>
            _refresh(() => _cuisineExpanded = !_cuisineExpanded),
        onToggleItem: (id) => _refresh(() {
          if (!_selectedCuisineIds.add(id)) _selectedCuisineIds.remove(id);
        }),
      );

  /// 折叠 chips 共用组件：标题（可选红 *）+ ▾ 收起/展开 + 一行横向滚动 ⇄ 多行 Wrap。
  Widget _buildFoldableChips({
    required String title,
    required bool required,
    required List<DictItem> dict,
    required Set<int> selected,
    required bool expanded,
    required VoidCallback onToggleExpanded,
    required ValueChanged<int> onToggleItem,
  }) {
    final t = AppTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Text(title,
                style: t.textStyles.xs.copyWith(
                    color: t.caption,
                    fontWeight: FontWeight.w800,
                    letterSpacing: 1)),
            if (required)
              Text(' *', style: t.textStyles.xs.copyWith(color: AppTokens.error)),
            const Spacer(),
            // 倒三角：一行折叠 ⇄ 全部展开
            InkWell(
              onTap: onToggleExpanded,
              borderRadius: BorderRadius.circular(AppTokens.rPill),
              child: Padding(
                padding: const EdgeInsets.all(4),
                child: Icon(
                  expanded ? Icons.expand_less : Icons.expand_more,
                  size: 18,
                  color: t.caption,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 6),
        if (expanded)
          Wrap(
            spacing: 6,
            runSpacing: 6,
            children: _chips(dict, selected, onToggleItem, t),
          )
        else
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(children: _chips(dict, selected, onToggleItem, t)),
          ),
      ],
    );
  }

  List<Widget> _chips(
      List<DictItem> dict, Set<int> selected, ValueChanged<int> onToggle, AppTokens t) {
    return dict.map((item) {
      final isSelected = selected.contains(item.id);
      return Padding(
        padding: const EdgeInsets.only(right: 6),
        child: InkWell(
          onTap: () => onToggle(item.id),
          borderRadius: BorderRadius.circular(AppTokens.rSm),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 5),
            decoration: BoxDecoration(
              color: isSelected ? t.primary : t.card,
              borderRadius: BorderRadius.circular(AppTokens.rSm),
              border: isSelected ? null : Border.all(color: t.border),
            ),
            child: Text(item.name,
                style: t.textStyles.xs.copyWith(
                  color: isSelected ? t.card : t.body,
                  fontWeight: FontWeight.w800,
                )),
          ),
        ),
      );
    }).toList();
  }

  Widget _buildNoteField() {
    final t = AppTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 分区标题（原型 ③ 屏样式：10px w800 letter-spacing 灰）
        Text('菜谱介绍',
            style: t.textStyles.xs.copyWith(
                color: t.caption, fontWeight: FontWeight.w800, letterSpacing: 1)),
        const SizedBox(height: 5),
        TextField(
          controller: _noteCtrl,
          maxLines: 2,
          style: t.textStyles.sm.copyWith(color: t.body),
          decoration: InputDecoration(
            filled: true,
            fillColor: t.card,
            contentPadding: const EdgeInsets.symmetric(
                horizontal: 12, vertical: 9),
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
      ],
    );
  }

  Widget _buildSectionDivider(String title) {
    final t = AppTokens.of(context);
    return Row(
      children: [
        Container(
          width: 4,
          height: 20,
          decoration: BoxDecoration(
            color: t.primary,
            borderRadius: BorderRadius.circular(AppTokens.rXs),
          ),
        ),
        const SizedBox(width: 12),
        Text(
          title,
          style: t.textStyles.subtitle,
        ),
      ],
    );
  }
}
