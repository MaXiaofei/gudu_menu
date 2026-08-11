import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/app_theme.dart';
import '../../services/dish_service.dart';
import '../../services/ingredient_service.dart';
import '../../services/upload_service.dart';
import 'dish_preview_page.dart';

/// 写菜谱页 — 两种模式（DESIGN.md §16.1，录入页无标题只有返回箭头）：
/// 1. 写菜谱：封面图 + 菜名 + 时间/难度 + 用料 + 步骤（图文）
/// 2. 导入链接：粘贴下厨房/美食杰/豆果链接，后端 Jsoup 解析落库
///
/// [draftId] 非空 = 从草稿箱「继续编辑」进入（?draftId=），加载草稿回填表单；
/// 「存草稿」无 id 新建、有 id 更新；正式保存（发布）成功后删除草稿（§16.4）。
class CreateDishPage extends StatefulWidget {
  final int? draftId;
  const CreateDishPage({super.key, this.draftId});

  @override
  State<CreateDishPage> createState() => _CreateDishPageState();
}

class _StepData {
  final TextEditingController textCtrl;
  File? imageFile; // 压缩后的本地临时文件
  String? imageUrl; // 上传后的服务端 URL

  _StepData({String text = ''}) : textCtrl = TextEditingController(text: text);

  void dispose() {
    textCtrl.dispose();
    _cleanFile();
  }

  void _cleanFile() {
    try {
      if (imageFile != null && imageFile!.existsSync()) imageFile!.deleteSync();
    } catch (_) {}
  }
}

/// 用料行（下厨房式，§16.3）：
/// 食材（绑定食材库 id）+ 数字用量 + 单位（可输入，实时匹配字典自动选中；
/// 匹配不到提交/存草稿时自动补进 unit 字典）。
class _IngredientRow {
  final int ingredientId;
  final String name;
  final TextEditingController amountCtrl; // 用量数字
  final TextEditingController unitCtrl; // 单位原文（个/g/适量/斤…）

  _IngredientRow({
    required this.ingredientId,
    required this.name,
    String amount = '',
    String unit = '',
  })  : amountCtrl = TextEditingController(text: amount),
        unitCtrl = TextEditingController(text: unit);

  void dispose() {
    amountCtrl.dispose();
    unitCtrl.dispose();
  }
}

class _CreateDishPageState extends State<CreateDishPage>
    with SingleTickerProviderStateMixin {
  late final TabController _tabCtrl;

  // --- 写菜谱表单 ---
  final _nameCtrl = TextEditingController();
  final _prepCtrl = TextEditingController();
  final _cookCtrl = TextEditingController();
  final _noteCtrl = TextEditingController();
  int _difficulty = 3;
  File? _coverFile; // 压缩后的封面临时文件
  String? _coverUrl; // 上传后的服务端 URL
  int? _draftId; // 当前草稿 id（null = 新草稿）
  String _initialSignature = ''; // 进入/回填时的表单签名（返回拦截用）
  final List<_StepData> _steps = [];
  final List<_IngredientRow> _ingredients = [];
  List<DictItem> _unitDict = []; // 单位字典（g/ml/个/把…），用量解析用
  List<IngredientItem> _commonIngredients = []; // 弹层「常用」chips
  List<DictItem> _catDict = []; // 采购分类字典（新建食材共用弹层「分类」chips，§16.5）
  List<DictItem> _tagDict = []; // 标签字典（必选，找菜分类数据源）
  final Set<int> _selectedTagIds = {};
  bool _tagExpanded = false; // 标签一行折叠 ⇄ 全部展开
  List<DictItem> _cuisineDict = []; // 菜系字典（可选，与标签同方式处理）
  final Set<int> _selectedCuisineIds = {}; // 已选菜系（多选）
  bool _cuisineExpanded = false; // 菜系一行折叠 ⇄ 全部展开

  // --- 导入链接 ---
  final _urlCtrl = TextEditingController();

  bool _saving = false;
  bool _importing = false;

  /// 表单内容签名（dirty 检测：有未保存改动时返回拦截确认）。
  String _signature() {
    final buf = StringBuffer()
      ..write(_nameCtrl.text)
      ..write('|')
      ..write(_prepCtrl.text)
      ..write('|')
      ..write(_cookCtrl.text)
      ..write('|')
      ..write(_difficulty)
      ..write('|')
      ..write(_noteCtrl.text)
      ..write('|')
      ..write(
          _coverFile != null || (_coverUrl != null && _coverUrl!.isNotEmpty));
    for (final r in _ingredients) {
      buf
        ..write('|')
        ..write(r.ingredientId)
        ..write(':')
        ..write(r.amountCtrl.text)
        ..write('/')
        ..write(r.unitCtrl.text);
    }
    for (final s in _steps) {
      buf
        ..write('|')
        ..write(s.textCtrl.text)
        ..write(':')
        ..write(s.imageUrl != null || s.imageFile != null);
    }
    return buf.toString();
  }

  bool get _isDirty => _signature() != _initialSignature;

  @override
  void initState() {
    super.initState();
    _tabCtrl = TabController(length: 2, vsync: this);
    _draftId = widget.draftId;
    _initialSignature = _signature();
    _loadIngredientBase();
    if (_draftId != null) _loadDraft();
  }

  /// 草稿箱「继续编辑」：加载草稿回填表单（§16.4）。
  Future<void> _loadDraft() async {
    try {
      final d = await DishService.draftDetail(_draftId!);
      if (!mounted) return;
      setState(() {
        _nameCtrl.text = d.name;
        _prepCtrl.text = d.prepTime?.toString() ?? '';
        _cookCtrl.text = d.cookTime?.toString() ?? '';
        if (d.difficulty != null) _difficulty = d.difficulty!;
        _noteCtrl.text = d.note ?? '';
        _coverUrl = d.coverUrl;
        for (final ing in d.ingredients) {
          _ingredients.add(_IngredientRow(
            ingredientId: ing.ingredientId,
            name: ing.ingredientName ?? '#${ing.ingredientId}',
            amount: ing.amount ?? '',
            unit: ing.unitText ?? '',
          ));
        }
        for (final st in d.steps) {
          final s = _StepData(text: st.text);
          if (st.imageList.isNotEmpty) s.imageUrl = st.imageList.first;
          _steps.add(s);
        }
        if (d.tagIds.isNotEmpty) _selectedTagIds.addAll(d.tagIds);
        if (d.cuisineIds.isNotEmpty) _selectedCuisineIds.addAll(d.cuisineIds);
        // 回填完成后再记初始签名（dirty 对比基准）
        _initialSignature = _signature();
      });
    } catch (_) {
      _showSnack('草稿加载失败');
    }
  }

  /// 预载单位字典（用量解析）+ 常用食材（加用料弹层 chips）+ 标签字典（§16.2）。
  Future<void> _loadIngredientBase() async {
    try {
      final units = await IngredientService.listDictByGroup('unit');
      if (mounted) setState(() => _unitDict = units);
    } catch (_) {}
    try {
      final all = await IngredientService.listAll();
      if (mounted) {
        setState(() =>
            _commonIngredients = all.take(8).toList()); // 常用 = 食材库前 8，TODO 按使用频率
      }
    } catch (_) {}
    try {
      final tags = await IngredientService.listDictByGroup('tag');
      if (mounted) setState(() => _tagDict = tags);
    } catch (_) {}
    try {
      final cats = await IngredientService.listDictByGroup('purchase_category');
      if (mounted) setState(() => _catDict = cats);
    } catch (_) {}
    try {
      final cuisines = await IngredientService.listDictByGroup('cuisine');
      if (mounted) setState(() => _cuisineDict = cuisines);
    } catch (_) {}
  }

  @override
  void dispose() {
    _tabCtrl.dispose();
    _nameCtrl.dispose();
    _prepCtrl.dispose();
    _cookCtrl.dispose();
    _noteCtrl.dispose();
    _urlCtrl.dispose();
    _cleanCoverFile();
    for (final s in _steps) {
      s.dispose();
    }
    for (final ing in _ingredients) {
      ing.dispose();
    }

    super.dispose();
  }

  void _cleanCoverFile() {
    try {
      if (_coverFile != null && _coverFile!.existsSync()) {
        _coverFile!.deleteSync();
      }
    } catch (_) {}
  }

  // ========== 图片选择 + 压缩 ==========

  Future<File?> _pickAndCompress() async {
    final picker = ImagePicker();
    final xfile = await picker.pickImage(
      source: ImageSource.gallery,
      imageQuality: 100, // 先拿原图，后面我们自己压缩
    );
    if (xfile == null) return null;

    final original = File(xfile.path);
    try {
      final compressed = await UploadService.compress(original);
      // 删原图
      try {
        if (original.existsSync()) original.deleteSync();
      } catch (_) {}
      return compressed;
    } catch (_) {
      // 压缩失败：直接返回原图兜底，保留原图
      return original;
    }
  }

  Future<void> _onPickCover() async {
    final f = await _pickAndCompress();
    if (f == null) return;
    setState(() {
      _cleanCoverFile();
      _coverFile = f;
      _coverUrl = null; // 换了新图，重置上传状态
    });
  }

  void _onRemoveCover() {
    setState(() {
      _cleanCoverFile();
      _coverFile = null;
      _coverUrl = null;
    });
  }

  Future<void> _onPickStepImage(_StepData step) async {
    final f = await _pickAndCompress();
    if (f == null) return;
    setState(() {
      step.imageFile = f;
      step.imageUrl = null;
    });
  }

  void _onRemoveStepImage(_StepData step) {
    setState(() {
      try {
        if (step.imageFile != null && step.imageFile!.existsSync()) {
          step.imageFile!.deleteSync();
        }
      } catch (_) {}
      step.imageFile = null;
      step.imageUrl = null;
    });
  }

  // ========== 步骤管理 ==========

  void _addStep() {
    setState(() => _steps.add(_StepData()));
  }

  void _removeStep(int i) {
    setState(() {
      _steps[i].dispose();
      _steps.removeAt(i);
    });
  }

  // ========== 上传（封面 + 步骤图） ==========

  Future<void> _uploadImages() async {
    // 上传封面
    if (_coverFile != null && _coverUrl == null) {
      final result = await UploadService.uploadOne(_coverFile!);
      setState(() => _coverUrl = result.url);
    }
    // 上传步骤图
    for (final step in _steps) {
      if (step.imageFile != null && step.imageUrl == null) {
        final result = await UploadService.uploadOne(step.imageFile!);
        setState(() => step.imageUrl = result.url);
      }
    }
  }

  // ========== 保存 ==========

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

    setState(() => _saving = true);
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
          unitId = await IngredientService.upsertDict(unitText, 'unit');
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
      if (mounted) setState(() => _saving = false);
    }
  }

  /// 存草稿（§16.4）：不校验必填（没填菜名也存）；无 id 新建、有 id 更新。
  /// 图片先上传（封面 + 步骤图），用量自由文本原样存，发布时再解析。
  /// 返回是否成功；从草稿箱进入的编辑模式保存成功后返回草稿箱。
  Future<bool> _onSaveDraft() async {
    setState(() => _saving = true);
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
      setState(() {
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
      if (mounted) setState(() => _saving = false);
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

  // ========== 用料：行 + 加用料弹层（§16.3，原型 cookbook-add.html ④ 屏） ==========

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
                    onChanged: (_) => setState(() {}),
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
                  onTap: () => setState(() {
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
                      setState(() => row.unitCtrl.text = u.name);
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
                // 搜索框
                TextField(
                  onChanged: (v) async {
                    await onSearch(v);
                    if (ctx.mounted) setSheet(() {});
                  },
                  decoration: InputDecoration(
                    hintText: '搜食材库',
                    prefixIcon: const Icon(Icons.search, size: 18),
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
    setState(() =>
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
        setState(() {
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

  Future<void> _onImportUrl() async {
    final url = _urlCtrl.text.trim();
    if (url.isEmpty) {
      _showSnack('请粘贴菜谱链接');
      return;
    }
    // 简单校验
    final uri = Uri.tryParse(url);
    if (uri == null || !uri.hasScheme) {
      _showSnack('链接格式不正确');
      return;
    }

    setState(() => _importing = true);
    try {
      final newId = await DishService.importDishByUrl(url);
      _showSnack('导入成功');
      if (mounted) {
        context.go('/dish/$newId');
      }
    } catch (e) {
      _showSnack('导入失败: $e');
    } finally {
      if (mounted) setState(() => _importing = false);
    }
  }

  void _showSnack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), duration: const Duration(seconds: 2)),
    );
  }

  // ========== UI ==========

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    // 返回拦截：有未保存改动时确认（编辑草稿 = 放弃修改；新建 = 可先存草稿）
    return PopScope(
      canPop: !_isDirty,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop || !mounted) return;
        _confirmDiscard();
      },
      child: Scaffold(
        // 录入页顶栏：只有返回箭头（§13.1，无标题）
        appBar: AppBar(),
        body: Column(
          children: [
            // 写菜谱 / 导入链接 分段（原型 ③ 屏：奶油底圆角 + 深色选中）
            Padding(
              padding: const EdgeInsets.fromLTRB(
                  AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp8),
              child: Container(
                padding: const EdgeInsets.all(3),
                decoration: BoxDecoration(
                  color: t.secondary,
                  borderRadius: BorderRadius.circular(AppTokens.rMd),
                ),
                child: Row(
                  children: [
                    _segItem(0, '写菜谱', t),
                    _segItem(1, '导入链接', t),
                  ],
                ),
              ),
            ),
            Expanded(
              child: TabBarView(
                controller: _tabCtrl,
                children: [
                  _buildManualEntry(),
                  _buildUrlImport(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 分段项（原型：选中深色底白字 / 未选浅字）。
  Widget _segItem(int index, String label, AppTokens t) {
    final selected = _tabCtrl.index == index;
    return Expanded(
      child: InkWell(
        onTap: () => _tabCtrl.animateTo(index),
        borderRadius: BorderRadius.circular(9),
        child: Container(
          padding: const EdgeInsets.symmetric(vertical: 7),
          decoration: BoxDecoration(
            color: selected ? t.title : Colors.transparent,
            borderRadius: BorderRadius.circular(9),
          ),
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: t.textStyles.sm.copyWith(
              color: selected ? t.card : t.body,
              fontWeight: selected ? FontWeight.w800 : FontWeight.w700,
            ),
          ),
        ),
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
  }

  // ========== 写菜谱 Tab ==========

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
              onTap: () => setState(() => _difficulty = i + 1),
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
        onToggleExpanded: () => setState(() => _tagExpanded = !_tagExpanded),
        onToggleItem: (id) => setState(() {
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
            setState(() => _cuisineExpanded = !_cuisineExpanded),
        onToggleItem: (id) => setState(() {
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

  Widget _buildStepCard(int index, _StepData step) {
    final t = AppTokens.of(context);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        side: BorderSide(color: t.border),
      ),
      color: t.card,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 头部：步骤编号 + 删除
            Row(
              children: [
                Container(
                  width: 28,
                  height: 28,
                  decoration: BoxDecoration(
                    color: t.primary,
                    borderRadius: BorderRadius.circular(AppTokens.rPill),
                  ),
                  alignment: Alignment.center,
                  child: Text(
                    '${index + 1}',
                    style: t.textStyles.sm.copyWith(
                      color: t.card,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  '步骤 ${index + 1}',
                  style: t.textStyles.md.copyWith(
                    fontWeight: FontWeight.w600,
                    color: t.title,
                  ),
                ),
                const Spacer(),
                InkWell(
                  onTap: () => _removeStep(index),
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                  hoverColor: t.primary.withValues(alpha: 0.08),
                  child: Icon(Icons.close, size: 20, color: t.caption),
                ),
              ],
            ),
            const SizedBox(height: 12),
            // 描述
            TextField(
              controller: step.textCtrl,
              maxLines: 3,
              style: t.textStyles.md,
              decoration: InputDecoration(
                filled: true,
                fillColor: t.bg,
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
                  borderSide:
                      BorderSide(color: t.primary, width: 1.5),
                ),
                contentPadding:
                    const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
              ),
            ),
            const SizedBox(height: 8),
            // 步骤图
            _buildStepImage(step),
          ],
        ),
      ),
    );
  }

  Widget _buildStepImage(_StepData step) {
    final t = AppTokens.of(context);
    if (step.imageFile != null) {
      return Stack(
        children: [
          ClipRRect(
            borderRadius: BorderRadius.circular(AppTokens.rSm),
            child: Image.file(
              step.imageFile!,
              height: 100,
              width: double.infinity,
              fit: BoxFit.cover,
            ),
          ),
          Positioned(
            top: 4,
            right: 4,
            child: InkWell(
              onTap: () => _onRemoveStepImage(step),
              borderRadius: BorderRadius.circular(AppTokens.rPill),
              hoverColor: t.primary.withValues(alpha: 0.08),
              child: Container(
                width: 24,
                height: 24,
                decoration: BoxDecoration(
                  color: Colors.black54,
                  borderRadius: BorderRadius.circular(AppTokens.rPill),
                ),
                child: Icon(Icons.close, size: 16, color: t.card),
              ),
            ),
          ),
        ],
      );
    }
    return InkWell(
      onTap: () => _onPickStepImage(step),
      borderRadius: BorderRadius.circular(AppTokens.rSm),
      hoverColor: t.primary.withValues(alpha: 0.08),
      child: Container(
        height: 44,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(AppTokens.rSm),
          border: Border.all(
            color: t.border,
            style: BorderStyle.solid,
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.add_photo_alternate_outlined,
                size: 18, color: t.caption),
            const SizedBox(width: 8),
            Text(
              '添加图片（可选）',
              style: t.textStyles.sm.copyWith(color: t.caption),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAddStepButton() {
    final t = AppTokens.of(context);
    return SizedBox(
      height: 44,
      child: OutlinedButton.icon(
        onPressed: _addStep,
        icon: const Icon(Icons.add, size: 18),
        label: const Text('添加步骤'),
        style: OutlinedButton.styleFrom(
          foregroundColor: t.primary,
          side: BorderSide(
            color: t.primary.withAlpha(100),
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppTokens.rMd),
          ),
        ),
      ),
    );
  }

  // ========== 导入链接 Tab ==========

  Widget _buildUrlImport() {
    final t = AppTokens.of(context);
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          const SizedBox(height: 24),
          // 说明卡片
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: t.highlight,
              borderRadius: BorderRadius.circular(AppTokens.rLg),
              border: Border.all(color: t.border),
            ),
            child: Column(
              children: [
                Icon(Icons.download_for_offline_outlined,
                    size: 48, color: t.primary.withAlpha(200)),
                const SizedBox(height: 12),
                Text(
                  '从其他 App 导入菜谱',
                  style: t.textStyles.subtitle,
                ),
                const SizedBox(height: 8),
                Text(
                  '粘贴下厨房、美食杰、豆果的菜谱链接，\n自动解析菜名、步骤和图片',
                  textAlign: TextAlign.center,
                  style: t.textStyles.sm.copyWith(color: t.caption),
                ),
                const SizedBox(height: 16),
                // 支持的平台标签
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  alignment: WrapAlignment.center,
                  children: [
                    _platformChip('下厨房'),
                    _platformChip('美食杰'),
                    _platformChip('豆果美食'),
                  ],
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          // URL 输入
          TextField(
            controller: _urlCtrl,
            style: t.textStyles.md,
            keyboardType: TextInputType.url,
            decoration: InputDecoration(
              hintText: '粘贴菜谱链接…',
              prefixIcon: const Icon(Icons.link),
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
                borderSide:
                    BorderSide(color: t.primary, width: 1.5),
              ),
              suffixIcon: _urlCtrl.text.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear, size: 18),
                      onPressed: () {
                        _urlCtrl.clear();
                        setState(() {});
                      },
                    )
                  : null,
            ),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 16),
          // 导入按钮
          SizedBox(
            height: 48,
            child: ElevatedButton.icon(
              onPressed: _importing ? null : _onImportUrl,
              icon: _importing
                  ? const SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: Colors.white,
                      ),
                    )
                  : const Icon(Icons.cloud_download_outlined),
              label: Text(
                  _importing ? '正在解析菜谱…' : '开始导入',
                  style: t.textStyles.lg.copyWith(color: Colors.white)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _platformChip(String name) {
    final t = AppTokens.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rPill),
        border: Border.all(color: t.border),
      ),
      child: Text(
        name,
        style: t.textStyles.sm.copyWith(color: t.accent),
      ),
    );
  }
}
