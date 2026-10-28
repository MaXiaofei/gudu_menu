part of '../create_page.dart';

/// 写菜谱页拆分（≤500 行）：State 方法以扩展形式放在 part 文件，私有成员同库可见。
extension _CreateSave on _CreateDishPageState {
  Future<void> _onSave() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      _showSnack('请输入菜名');
      return;
    }
    if (_selectedTagIds.isEmpty) {
      _showSnack('请选择标签');
      return;
    }

    _refresh(() => _saving = true);
    try {
      // 先上传所有图片
      await _uploadImages();

      // 组装 payload（对齐后端 DishSaveDTO；参考价格已从表单移除，§16.2）
      final dish = <String, dynamic>{
        'name': name,
        if (_coverUrl != null) 'coverUrl': _coverUrl,
        if (_noteCtrl.text.trim().isNotEmpty) 'note': _noteCtrl.text.trim(),
        'prepTime':
            _prepCtrl.text.trim().isNotEmpty ? int.tryParse(_prepCtrl.text.trim()) : null,
        'cookTime':
            _cookCtrl.text.trim().isNotEmpty ? int.tryParse(_cookCtrl.text.trim()) : null,
        'difficulty': _difficulty,
      };

      final validSteps = <_StepData>[];
      for (final s in _steps) {
        if (s.textCtrl.text.trim().isNotEmpty) validSteps.add(s);
      }

      final stepsJson = validSteps.asMap().entries.map((e) {
        final i = e.key;
        final s = e.value;
        return {
          'seq': i + 1,
          'text': s.textCtrl.text.trim(),
          'sortOrder': i + 1,
          if (s.imageUrl != null) 'images': s.imageUrl,
        };
      }).toList();

      // 用料：数字 + 单位（匹配字典自动选中；匹配不到的提交时补进 unit 字典，§16.3）
      final ingredientsJson = <Map<String, dynamic>>[];
      for (final r in _ingredients) {
        final amount = double.tryParse(r.amountCtrl.text.trim());
        final unitText = r.unitCtrl.text.trim();
        var unitId = _matchUnit(unitText)?.id;
        if (unitId == null && unitText.isNotEmpty) {
          // 单位字典为全局数据，普通用户无权新增（403）：失败则不带 unitId，不阻断发布
          try {
            unitId = await IngredientService.upsertDict(unitText, 'unit');
          } catch (_) {}
        }
        ingredientsJson.add({
          'ingredientId': r.ingredientId,
          if (amount != null) 'amount': amount,
          if (unitId != null) 'unitId': unitId,
        });
      }

      final payload = {
        'dish': dish,
        'steps': stepsJson,
        'ingredients': ingredientsJson,
        'tagIds': _selectedTagIds.toList(),
        'cuisineIds': _selectedCuisineIds.toList(),
      };

      await DishService.saveDish(payload);
      // 发布成功 → 清掉草稿（§16.4）
      if (_draftId != null) {
        try {
          await DishService.deleteDraft(_draftId!);
        } catch (_) {}
      }
      _showSnack('已保存');
      // 发布成功 → 回菜谱页并按「最新」排序（§16）
      if (mounted) {
        context.go('/dish?sort=latest');
      }
    } catch (e) {
      _showSnack('保存失败: $e');
    } finally {
      if (mounted) _refresh(() => _saving = false);
    }
  }

  /// 存草稿（§16.4）：不校验必填（没填菜名也存）；无 id 新建、有 id 更新。
  /// 图片先上传（封面 + 步骤图），用量自由文本原样存，发布时再解析。
  /// 返回是否成功；从草稿箱进入的编辑模式保存成功后返回草稿箱。
  Future<bool> _onSaveDraft() async {
    _refresh(() => _saving = true);
    try {
      await _uploadImages();

      final ingredientsJson = _ingredients.map((r) => {
            'ingredientId': r.ingredientId,
            'ingredientName': r.name,
            if (r.amountCtrl.text.trim().isNotEmpty)
              'amount': r.amountCtrl.text.trim(),
            if (r.unitCtrl.text.trim().isNotEmpty)
              'unitText': r.unitCtrl.text.trim(),
          }).toList();
      // 存草稿时也把未匹配的单位补进字典（回填后仍能匹配到，§16.3）
      for (final r in _ingredients) {
        final unitText = r.unitCtrl.text.trim();
        if (unitText.isNotEmpty && _matchUnit(unitText) == null) {
          try {
            await IngredientService.upsertDict(unitText, 'unit');
          } catch (_) {}
        }
      }

      final validSteps = <_StepData>[];
      for (final s in _steps) {
        if (s.textCtrl.text.trim().isNotEmpty) validSteps.add(s);
      }
      final stepsJson = validSteps.asMap().entries.map((e) {
        final s = e.value;
        return {
          'seq': e.key + 1,
          'text': s.textCtrl.text.trim(),
          if (s.imageUrl != null) 'images': s.imageUrl,
        };
      }).toList();

      final body = <String, dynamic>{
        if (_draftId != null) 'id': _draftId,
        if (_nameCtrl.text.trim().isNotEmpty)
          'name': _nameCtrl.text.trim(),
        if (_coverUrl != null) 'coverUrl': _coverUrl,
        if (_prepCtrl.text.trim().isNotEmpty)
          'prepTime': int.tryParse(_prepCtrl.text.trim()),
        if (_cookCtrl.text.trim().isNotEmpty)
          'cookTime': int.tryParse(_cookCtrl.text.trim()),
        'difficulty': _difficulty,
        if (_noteCtrl.text.trim().isNotEmpty) 'note': _noteCtrl.text.trim(),
        'tagIds': _selectedTagIds.toList(),
        'cuisineIds': _selectedCuisineIds.toList(),
        'ingredients': ingredientsJson,
        'steps': stepsJson,
      };

      final id = await DishService.saveDraft(body);
      _refresh(() {
        _draftId = id;
        _initialSignature = _signature(); // 已保存，重置 dirty 基准
      });
      _showSnack('已存草稿');
      // 从草稿箱进入的编辑模式：保存修改后返回草稿箱
      if (widget.draftId != null && mounted) {
        Navigator.of(context).pop();
      }
      return true;
    } catch (e) {
      _showSnack('存草稿失败: $e');
      return false;
    } finally {
      if (mounted) _refresh(() => _saving = false);
    }
  }

  /// 预览（§16.4，原型 ⑦ 屏）：把当前表单组装成预览数据，进预览页；发布回调 = _onSave。
  void _onPreview() {
    final validSteps = <_StepData>[];
    for (final s in _steps) {
      if (s.textCtrl.text.trim().isNotEmpty) validSteps.add(s);
    }
    context.push('/dish-preview', extra: DishPreviewData(
      name: _nameCtrl.text.trim(),
      coverUrl: _coverUrl,
      coverFile: _coverFile,
      prepText: _prepCtrl.text.trim(),
      cookText: _cookCtrl.text.trim(),
      difficulty: _difficulty,
      tags: _tagDict
          .where((t) => _selectedTagIds.contains(t.id))
          .map((t) => t.name)
          .toList(),
      cuisines: _cuisineDict
          .where((c) => _selectedCuisineIds.contains(c.id))
          .map((c) => c.name)
          .toList(),
      note: _noteCtrl.text.trim().isEmpty ? null : _noteCtrl.text.trim(),
      ingredients: _ingredients.map((r) {
        final amount = r.amountCtrl.text.trim();
        final unit = r.unitCtrl.text.trim();
        final display = amount.isNotEmpty && unit.isNotEmpty
            ? '$amount $unit'
            : amount.isNotEmpty
                ? amount
                : unit;
        return (r.name, display);
      }).toList(),
      steps: validSteps
          .map((s) => DishPreviewStep(text: s.textCtrl.text.trim(), imageUrl: s.imageUrl))
          .toList(),
      onPublish: _onSave,
    ));
  }


  /// 首字色块（图片占位规则 §10：无图时首字兜底）。
  Widget _ingAvatar(String text) {
    final t = AppTokens.of(context);
    return Container(
      width: 26,
      height: 26,
      decoration: BoxDecoration(
        color: t.secondary,
        borderRadius: BorderRadius.circular(AppTokens.rSm),
      ),
      alignment: Alignment.center,
      child: Text(
        text.isEmpty ? '食' : text.characters.first,
        style: t.textStyles.sm.copyWith(
            color: t.accent, fontWeight: FontWeight.w700),
      ),
    );
  }

  /// 返回确认：有未保存改动时拦截（编辑草稿 = 放弃修改；新建 = 可先存草稿再走）。
  Future<void> _confirmDiscard() async {
    final t = AppTokens.of(context);
    final isEdit = widget.draftId != null;
    final action = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(isEdit ? '放弃修改？' : '先存草稿再走？'),
        content: Text(isEdit ? '这次改动还没有保存' : '写了一半的内容，可以先存成草稿'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, 'cancel'),
            child: Text('取消', style: t.textStyles.md),
          ),
          if (!isEdit)
            TextButton(
              onPressed: () => Navigator.pop(ctx, 'draft'),
              child: Text('存草稿',
                  style: t.textStyles.md.copyWith(color: t.accent)),
            ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, 'discard'),
            child: Text('放弃',
                style: t.textStyles.md.copyWith(color: AppTokens.error)),
          ),
        ],
      ),
    );
    if (!mounted || action == null || action == 'cancel') return;
    if (action == 'discard') {
      Navigator.of(context).pop(); // 放弃修改，强制返回
    } else if (action == 'draft') {
      final ok = await _onSaveDraft();
      if (ok && mounted) Navigator.of(context).pop(); // 存完离开
    }
  }}
