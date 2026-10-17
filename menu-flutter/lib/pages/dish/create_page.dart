import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/app_theme.dart';
import '../../widgets/search_box.dart';
import '../../services/dish_service.dart';
import '../../services/ingredient_service.dart';
import '../../services/upload_service.dart';
import 'dish_preview_page.dart';

part 'create/save.dart';
part 'create/ingredient_rows.dart';
part 'create/ingredient_add.dart';
part 'create/form_basic.dart';
part 'create/form_steps.dart';

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

  /// 供 part 文件里的扩展方法刷新（setState 是 @protected，扩展里不能直接调）。
  void _refresh(VoidCallback fn) => setState(fn);

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
    _tabCtrl.addListener(_onTabChanged);
    _draftId = widget.draftId;
    _initialSignature = _signature();
    _loadIngredientBase();
    if (_draftId != null) _loadDraft();
  }

  /// 分段切换：TabController 变化不会自动重建外层（§16.1 写菜谱/导入链接），
  /// 监听后 setState 刷新分段按钮选中态。
  void _onTabChanged() {
    if (mounted) setState(() {});
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
  /// 各项独立加载、互不阻塞；任一失败汇总提示一次（不静默吞错）。
  Future<void> _loadIngredientBase() async {
    final failed = <String>[];
    Future<void> load(String what, Future<void> Function() f) async {
      try {
        await f();
      } catch (_) {
        failed.add(what);
      }
    }

    await Future.wait([
      load('单位', () async {
        final units = await IngredientService.listDictByGroup('unit');
        if (mounted) setState(() => _unitDict = units);
      }),
      // 常用食材：按本账号做过的菜 + 录入菜谱用料频次（GET /ingredient/frequent，G11）
      load('常用食材', () async {
        final frequent = await IngredientService.frequent(limit: 8);
        if (mounted) setState(() => _commonIngredients = frequent);
      }),
      load('标签', () async {
        final tags = await IngredientService.listDictByGroup('tag');
        if (mounted) setState(() => _tagDict = tags);
      }),
      load('采购品类', () async {
        final cats = await IngredientService.listDictByGroup('purchase_category');
        if (mounted) setState(() => _catDict = cats);
      }),
      load('菜系', () async {
        final cuisines = await IngredientService.listDictByGroup('cuisine');
        if (mounted) setState(() => _cuisineDict = cuisines);
      }),
    ]);
    if (failed.isNotEmpty && mounted) {
      _showSnack('${failed.join('、')}加载失败，可稍后重进本页');
    }
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



  // ========== 写菜谱 Tab ==========

}
