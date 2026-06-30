import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme.dart';
import '../../services/dish_service.dart';
import '../../services/ingredient_service.dart';
import '../../services/mealplan_service.dart';
import '../../services/shopping_service.dart';

/// 排菜计划页：竖向日期列表，每天按动态餐段展开。
/// 支持翻周导航、份数选择、一键生成采购单。
class MealPlanPage extends StatefulWidget {
  const MealPlanPage({super.key});
  @override
  State<MealPlanPage> createState() => _MealPlanPageState();
}

class _MealPlanPageState extends State<MealPlanPage> {
  // 周计划数据
  List<MealPlan> _allPlans = [];
  int _currentPlanIndex = 0;
  PlanDetail? _detail;
  bool _loading = true;

  // 动态餐段
  List<String> _meals = ['早餐', '午餐', '晚餐'];

  // 菜名缓存
  final Map<int, String> _dishNames = {};

  // 每日营养小计：date → {calorie, protein}
  final Map<String, _DayNutrition> _dayNutrition = {};

  // 选菜弹窗
  String? _pickDate;
  String? _pickMeal;
  List<_DishLite> _dishSearchResults = [];

  int? get _currentPlanId => _allPlans.isNotEmpty && _currentPlanIndex < _allPlans.length
      ? _allPlans[_currentPlanIndex].id : null;

  @override
  void initState() {
    super.initState();
    _loadMeals();
    _loadLatestPlan();
  }

  // ===== 餐段字典 =====
  Future<void> _loadMeals() async {
    try {
      final items = await IngredientService.listDictByGroup('meal');
      if (items.isNotEmpty) {
        setState(() => _meals = items.map((e) => e.name).toList());
      }
    } catch (_) {}
  }

  // ===== 周计划加载 =====
  Future<void> _loadLatestPlan() async {
    setState(() => _loading = true);
    try {
      _allPlans = await MealPlanService.list(pageSize: 50);
      if (_allPlans.isNotEmpty) {
        _currentPlanIndex = 0;
        await _loadPlan();
      }
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  Future<void> _loadPlan() async {
    final planId = _currentPlanId;
    if (planId == null) return;
    try {
      _detail = await MealPlanService.getPlan(planId);
      for (final it in _detail!.items) {
        if (it.dishId != null && it.dishName != null) {
          _dishNames[it.dishId!] = it.dishName!;
        }
      }
      // 并行查营养小计
      _loadDayNutrition();
    } catch (_) {}
    if (mounted) setState(() {});
  }

  /// 按日期汇总每天菜品的热量和蛋白质。
  Future<void> _loadDayNutrition() async {
    if (_detail == null) return;
    _dayNutrition.clear();
    // 按日期分组
    final byDate = _detail!.itemsByDate();
    // 并行查所有菜品的营养
    for (final entry in byDate.entries) {
      final date = entry.key;
      final items = entry.value;
      double cal = 0, protein = 0;
      for (final it in items) {
        if (it.dishId == null) continue;
        try {
          final nut = await DishService.nutrition(it.dishId!, serving: it.servingFactor ?? 1);
          // metricId=1 是热量，metricId=2 是蛋白质
          if (nut['1'] != null) cal += (nut['1'] as num).toDouble();
          if (nut['2'] != null) protein += (nut['2'] as num).toDouble();
        } catch (_) {}
      }
      _dayNutrition[date] = _DayNutrition(calorie: cal.round(), protein: protein.round());
    }
    if (mounted) setState(() {});
  }

  // ===== 翻周 =====
  void _prevWeek() {
    if (_currentPlanIndex < _allPlans.length - 1) {
      _currentPlanIndex++;
      _loadPlan();
    }
  }

  void _nextWeek() {
    if (_currentPlanIndex > 0) {
      _currentPlanIndex--;
      _loadPlan();
    }
  }

  // ===== 复制上周 =====
  Future<void> _copyFromPrev() async {
    if (_currentPlanIndex >= _allPlans.length - 1) {
      _snack('没有更早的计划可复制');
      return;
    }
    final srcId = _allPlans[_currentPlanIndex + 1].id;
    final dstId = _currentPlanId;
    if (dstId == null) return;
    try {
      final count = await MealPlanService.copyFrom(srcId, dstId);
      _snack('已复制 $count 项');
      _loadPlan();
    } catch (_) {
      _snack('复制失败');
    }
  }

  Future<void> _createWeek() async {
    try {
      final monday = _mondayOf(DateTime.now());
      final dateStr = _fmtDate(monday);
      final id = await MealPlanService.createPlan(dateStr);
      _allPlans.insert(0, MealPlan(id: id, weekStart: _fmtDate(_mondayOf(DateTime.now())), name: '本周计划'));
      _currentPlanIndex = 0;
      await _loadPlan();
      _snack('已创建本周计划');
    } catch (_) {
      _snack('创建失败');
    }
  }

  // ===== 选菜 + 挂菜 =====

  Future<void> _openPicker(String date, String meal) async {
    _pickDate = date;
    _pickMeal = meal;
    _dishSearchResults = [];
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => _buildPickerSheet(ctx),
    );
  }

  Widget _buildPickerSheet(BuildContext sheetCtx) {
    String servings = '1';
    return StatefulBuilder(
      builder: (ctx, setSheetState) {
        return Padding(
          padding: EdgeInsets.only(
              left: 16, right: 16, top: 20,
              bottom: MediaQuery.of(ctx).viewInsets.bottom + 16),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Text('$_pickMeal · 选菜', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 12),
            TextField(
              decoration: InputDecoration(
                hintText: '搜索菜品…',
                prefixIcon: const Icon(Icons.search),
                filled: true, fillColor: const Color(0xFFFAFAFA),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
              ),
              onChanged: (v) async {
                if (v.trim().isEmpty) { setSheetState(() => _dishSearchResults = []); return; }
                try {
                  final r = await DishService.search(keyword: v.trim(), pageSize: 20);
                  setSheetState(() {
                    _dishSearchResults = r.records.map((d) => _DishLite(d.id, d.name)).toList();
                  });
                } catch (_) {}
              },
            ),
            const SizedBox(height: 8),
            // 份数选择
            Row(children: [
              const Text('份数', style: TextStyle(fontSize: 13)),
              const SizedBox(width: 8),
              for (final s in ['1', '2', '3'])
                Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: ChoiceChip(
                    label: Text('×$s'),
                    selected: servings == s,
                    onSelected: (_) => setSheetState(() => servings = s),
                  ),
                ),
            ]),
            const SizedBox(height: 8),
            SizedBox(
              height: 220,
              child: _dishSearchResults.isEmpty
                  ? const Center(child: Text('搜索菜品', style: TextStyle(color: AppColors.textSecondary)))
                  : ListView.builder(
                      itemCount: _dishSearchResults.length,
                      itemBuilder: (_, i) {
                        final d = _dishSearchResults[i];
                        return ListTile(
                          dense: true,
                          title: Text(d.name, style: const TextStyle(fontSize: 14)),
                          onTap: () {
                            Navigator.pop(ctx);
                            _doAdd(d, double.tryParse(servings) ?? 1);
                          },
                        );
                      },
                    ),
            ),
          ]),
        );
      },
    );
  }

  Future<void> _doAdd(_DishLite dish, double servings) async {
    if (_currentPlanId == null || _pickDate == null || _pickMeal == null) return;
    try {
      final result = await MealPlanService.addItem(_currentPlanId!, MealPlanItem(
        date: _pickDate, meal: _pickMeal, dishId: dish.id, servingFactor: servings,
      ));
      _dishNames[dish.id] = dish.name;
      _snack(result.hasDuplicate ? '同日同餐已有此菜' : '已挂菜：${dish.name}');
      _loadPlan();
    } catch (_) {
      _snack('添加失败');
    }
  }

  Future<void> _doRemove(MealPlanItem item) async {
    if (item.id == null) return;
    final name = _dishName(item.dishId);
    try {
      await MealPlanService.deleteItem(item.id!);
      _snack('已移除：$name');
      _loadPlan();
    } catch (_) {
      _snack('移除失败');
    }
  }

  // ===== 一键生成采购单 =====

  Future<void> _generateShopping() async {
    if (_currentPlanId == null) return;
    try {
      await ShoppingService.generateFrom('plan', sourceId: _currentPlanId);
      _snack('采购单已生成');
      if (mounted) context.push('/shopping');
    } catch (_) {
      _snack('生成失败');
    }
  }

  // ===== 工具 =====

  String _dishName(int? dishId) {
    if (dishId == null) return '?';
    return _dishNames[dishId] ?? '#$dishId';
  }

  static DateTime _mondayOf(DateTime d) {
    final wd = (d.weekday - 1) % 7;
    return DateTime(d.year, d.month, d.day - wd);
  }

  static String _fmtDate(DateTime d) =>
      '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  List<String> _weekDates() {
    if (_detail?.plan.weekStart == null) return [];
    final start = DateTime.tryParse(_detail!.plan.weekStart!);
    if (start == null) return [];
    return List.generate(7, (i) => _fmtDate(start.add(Duration(days: i))));
  }

  static const _weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(msg), duration: const Duration(seconds: 2)));
  }

  // ===== UI =====

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Row(mainAxisSize: MainAxisSize.min, children: [
          IconButton(
            icon: const Icon(Icons.chevron_left),
            onPressed: _currentPlanIndex < _allPlans.length - 1 ? _prevWeek : null,
            iconSize: 22,
          ),
          Text(_detail?.plan.name ?? '排菜计划', style: const TextStyle(fontSize: 16)),
          IconButton(
            icon: const Icon(Icons.chevron_right),
            onPressed: _currentPlanIndex > 0 ? _nextWeek : null,
            iconSize: 22,
          ),
        ]),
        actions: [
          if (_allPlans.length > 1 && _currentPlanIndex < _allPlans.length - 1)
            IconButton(
              icon: const Icon(Icons.content_copy),
              onPressed: _copyFromPrev,
              tooltip: '复制上周',
            ),
          if (_detail != null)
            IconButton(
              icon: const Icon(Icons.shopping_cart_outlined),
              onPressed: _generateShopping,
              tooltip: '生成采购单',
            ),
          IconButton(
            icon: const Icon(Icons.add_circle_outline),
            onPressed: _createWeek,
            tooltip: '新建本周计划',
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _detail == null
              ? _buildEmpty()
              : _buildPlan(),
      floatingActionButton: FloatingActionButton(
        onPressed: _openAddByDate,
        tooltip: '加菜',
        child: const Icon(Icons.add),
      ),
    );
  }

  Widget _buildEmpty() {
    return Center(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Text('还没有排菜计划', style: TextStyle(color: AppColors.textSecondary, fontSize: 15)),
        const SizedBox(height: 16),
        ElevatedButton.icon(
          onPressed: _createWeek,
          icon: const Icon(Icons.add, size: 18),
          label: const Text('新建本周计划'),
        ),
      ]),
    );
  }

  Widget _buildPlan() {
    final dates = _weekDates();
    final byDate = _detail!.itemsByDate();

    // 严格只展示有挂菜的日期（空日期全隐藏）。加菜走右下角 + 按钮（_openAddByDate）。
    final showIdx = <int>[];
    for (var i = 0; i < dates.length; i++) {
      if ((byDate[dates[i]] ?? []).isNotEmpty) showIdx.add(i);
    }
    if (showIdx.isEmpty) {
      return Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const Text('本周还没排菜', style: TextStyle(color: AppColors.textSecondary, fontSize: 15)),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: _openAddByDate,
            icon: const Icon(Icons.add, size: 18),
            label: const Text('给某天加菜'),
          ),
        ]),
      );
    }
    return ListView.builder(
      padding: const EdgeInsets.all(14),
      itemCount: showIdx.length,
      itemBuilder: (_, k) {
        final i = showIdx[k];
        return _buildDayCard(dates[i], i, byDate[dates[i]] ?? []);
      },
    );
  }

  /// 独立加菜入口：先选「日期 + 餐次」再进选菜。
  /// （空日期被隐藏后，无法点日期卡里的「+」，故提供此入口给任意日期加菜。）
  void _openAddByDate() {
    final dates = _weekDates();
    if (dates.isEmpty || _meals.isEmpty) return;
    String date = dates.first;
    String meal = _meals.first;
    showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (ctx) => StatefulBuilder(
        builder: (_, ss) => Padding(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('选日期', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8, runSpacing: 8,
                children: [
                  for (var i = 0; i < dates.length; i++)
                    ChoiceChip(
                      label: Text('${_weekdays[i]} ${_md(dates[i])}'),
                      selected: date == dates[i],
                      onSelected: (_) => ss(() => date = dates[i]),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              const Text('选餐次', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8, runSpacing: 8,
                children: _meals
                    .map((m) => ChoiceChip(
                          label: Text(m),
                          selected: meal == m,
                          onSelected: (_) => ss(() => meal = m),
                        ))
                    .toList(),
              ),
              const SizedBox(height: 20),
              SizedBox(
                height: 44,
                child: ElevatedButton(
                  onPressed: () {
                    Navigator.pop(ctx);
                    _openPicker(date, meal);
                  },
                  child: const Text('去选菜'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 日期串 → MM/DD（选日期 chip 用）。
  static String _md(String d) =>
      d.length >= 10 ? '${d.substring(5, 7)}/${d.substring(8, 10)}' : d;

  Widget _buildDayCard(String date, int dayIndex, List<MealPlanItem> items) {
    final md = date.length >= 10 ? '${date.substring(5, 7)}/${date.substring(8, 10)}' : date;
    final nut = _dayNutrition[date];
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: Color(0xFFEEEEEE))),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Text('${_weekdays[dayIndex]} $md', style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold)),
            const Spacer(),
            Text('${items.length}菜', style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
          ]),
          const SizedBox(height: 8),
          ..._meals.map((meal) => _buildMealSlot(date, meal, items)),
          // 每日营养小计
          if (nut != null && (nut.calorie > 0 || nut.protein > 0)) ...[
            const SizedBox(height: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: const Color(0xFFF8F5F0),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(children: [
                const Text('营养小计', style: TextStyle(fontSize: 11, color: AppColors.textSecondary)),
                const Spacer(),
                Text('🔥 ${nut.calorie}kcal', style: const TextStyle(fontSize: 11, color: AppColors.warnRed, fontWeight: FontWeight.w600)),
                const SizedBox(width: 10),
                Text('🥩 ${nut.protein}g', style: const TextStyle(fontSize: 11, color: AppColors.primary, fontWeight: FontWeight.w600)),
              ]),
            ),
          ],
        ]),
      ),
    );
  }

  Widget _buildMealSlot(String date, String meal, List<MealPlanItem> dayItems) {
    final slotItems = dayItems.where((it) => it.meal == meal).toList();
    return Padding(
      padding: const EdgeInsets.only(top: 6),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        SizedBox(
          width: 56,
          child: Text(meal, style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
        ),
        Expanded(
          child: Wrap(spacing: 6, runSpacing: 4, children: [
            ...slotItems.map((it) {
              final name = it.dishName ?? _dishName(it.dishId);
              return GestureDetector(
                onLongPress: () => _doRemove(it),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: AppColors.primary.withAlpha(15),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    '$name${it.servingFactor != null && it.servingFactor != 1 ? " ×${_fmtFactor(it.servingFactor!)}" : ""}',
                    style: const TextStyle(fontSize: 12, color: AppColors.primary),
                  ),
                ),
              );
            }),
            GestureDetector(
              onTap: () => _openPicker(date, meal),
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  border: Border.all(color: const Color(0xFFDDDDDD)),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Text('+', style: TextStyle(fontSize: 12, color: Colors.grey)),
              ),
            ),
          ]),
        ),
      ]),
    );
  }

  String _fmtFactor(double f) => f == f.roundToDouble() ? f.toInt().toString() : f.toStringAsFixed(1);
}

class _DishLite {
  final int id;
  final String name;
  const _DishLite(this.id, this.name);
}

/// 每日营养小计。
class _DayNutrition {
  final int calorie;
  final int protein;
  const _DayNutrition({required this.calorie, required this.protein});
}
