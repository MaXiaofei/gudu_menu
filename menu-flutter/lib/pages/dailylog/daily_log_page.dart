import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/theme.dart';
import '../../models/nutrition_metric.dart';
import '../../services/dailylog_service.dart';
import '../../services/dish_service.dart';
import '../../stores/member_store.dart';

/// 每日饮食记录。
///
/// 轻量模式：一句话摘要 + 当日摄入列表 + 快速记菜名。
/// 精准模式（右上角开关）：热量环形进度 + 三大宏量条 + 实时对比营养目标。
/// 默认轻量模式；当前成员有 goal 且已填健康档案时自动开精准模式。
class DailyLogPage extends StatefulWidget {
  const DailyLogPage({super.key});
  @override
  State<DailyLogPage> createState() => _DailyLogPageState();
}

class _DailyLogPageState extends State<DailyLogPage> {
  late DateTime _date;
  DailyLogVO? _log;
  Map<int, double> _nutrition = {};
  List<NutritionMetric> _metrics = [];
  NutritionTarget? _target;
  bool _loading = true;
  bool _preciseMode = false;

  // 录入
  final _nameCtrl = TextEditingController();
  final _amountCtrl = TextEditingController();

  // 预加载菜库
  List<_DishLite> _dishes = [];

  @override
  void initState() {
    super.initState();
    _date = DateTime.now();
    _load();
  }

  @override
  void dispose() {
    _nameCtrl.dispose();
    _amountCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    final dateStr = _dateStr;

    // 并行加载日志 + 指标字典 + 选项列表
    final results = await Future.wait([
      DailyLogService.getDailyLog(dateStr),
      DishService.metrics(),
    ]);
    _log = results[0] as DailyLogVO?;
    _metrics = results[1] as List<NutritionMetric>;

    // 加载营养汇总 + 目标
    _nutrition = _log != null ? await DailyLogService.nutrition(_log!.id) : {};
    _target = await DailyLogService.nutritionTarget(
      context.read<MemberStore>().currentId,
    );

    // 预加载菜库
    _loadOptions();

    if (mounted) {
      if (_target != null && !_preciseMode) _preciseMode = true;
      setState(() => _loading = false);
    }
  }

  Future<void> _loadOptions() async {
    try {
      // 仅预载 8 条作为「快速记」的热门 chips；「从菜库选」走服务端搜索（每页 10）。
      final r = await DishService.search(pageSize: 8);
      _dishes = r.records.map((d) => _DishLite(d.id, d.name)).toList();
    } catch (_) {}
    if (mounted) setState(() {});
  }

  String get _dateStr =>
      '${_date.year}-${_date.month.toString().padLeft(2, '0')}-${_date.day.toString().padLeft(2, '0')}';

  bool get _isToday {
    final n = DateTime.now();
    return _date.year == n.year && _date.month == n.month && _date.day == n.day;
  }

  // ===== 日期 =====
  void _prevDay() {
    _date = _date.subtract(const Duration(days: 1));
    _load();
  }

  void _nextDay() {
    final n = _date.add(const Duration(days: 1));
    if (n.isAfter(DateTime.now())) return;
    _date = n;
    _load();
  }

  Future<void> _pickDate() async {
    final p = await showDatePicker(
      context: context,
      initialDate: _date,
      firstDate: DateTime(2024),
      lastDate: DateTime.now(),
    );
    if (p != null) { _date = p; _load(); }
  }

  String _weekLabel(int wd) =>
      const ['', '周一', '周二', '周三', '周四', '周五', '周六', '周日'][wd];

  // ===== 录入 =====

  void _showAddSheet() {
    _nameCtrl.clear();
    _amountCtrl.clear();
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (_) => StatefulBuilder(
        builder: (ctx, setSheetState) {
          String sheetMode = 'quick'; // quick / library
          List<_DishLite> searchResults = [];
          return Padding(
            padding: EdgeInsets.only(
              left: 16, right: 16, top: 20,
              bottom: MediaQuery.of(ctx).viewInsets.bottom + 16,
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text('记一餐', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 12),
                // 模式切换
                Row(children: [
                  _sheetTab('快速记', sheetMode == 'quick', () => setSheetState(() => sheetMode = 'quick')),
                  const SizedBox(width: 8),
                  _sheetTab('从菜库选', sheetMode == 'library', () => setSheetState(() => sheetMode = 'library')),
                ]),
                const SizedBox(height: 12),
                if (sheetMode == 'quick') ...[
                  TextField(
                    controller: _nameCtrl,
                    autofocus: true,
                    decoration: InputDecoration(
                      hintText: '吃了什么？如 番茄炒蛋',
                      prefixIcon: const Icon(Icons.restaurant),
                      filled: true, fillColor: const Color(0xFFFAFAFA),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(children: [
                    const Text('份数', style: TextStyle(fontSize: 13)),
                    const SizedBox(width: 8),
                    SizedBox(
                      width: 80,
                      child: TextField(
                        controller: _amountCtrl,
                        keyboardType: TextInputType.number,
                        decoration: InputDecoration(
                          hintText: '1',
                          filled: true, fillColor: const Color(0xFFFAFAFA),
                          contentPadding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
                          border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    const Text('份', style: TextStyle(fontSize: 13, color: AppColors.textSecondary)),
                  ]),
                  const SizedBox(height: 12),
                  // 菜库热门快捷选
                  if (_dishes.isNotEmpty)
                    Wrap(
                      spacing: 6, runSpacing: 6,
                      children: _dishes.take(8).map((d) {
                        return GestureDetector(
                          onTap: () {
                            _nameCtrl.text = d.name;
                            Navigator.pop(ctx);
                            _doAddDish(d.id, d.name, 1);
                          },
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                            decoration: BoxDecoration(
                              color: AppColors.primary.withAlpha(15),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Text(d.name, style: const TextStyle(fontSize: 12, color: AppColors.primary)),
                          ),
                        );
                      }).toList(),
                    ),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 44,
                    child: ElevatedButton(
                      onPressed: () { Navigator.pop(ctx); _submitQuickAdd(); },
                      child: const Text('快速记录', style: TextStyle(fontSize: 15)),
                    ),
                  ),
                ] else ...[
                  // 从菜库选（服务端搜索，每页 10 条）
                  TextField(
                    decoration: InputDecoration(
                      hintText: '搜索菜品…',
                      prefixIcon: const Icon(Icons.search),
                      filled: true, fillColor: const Color(0xFFFAFAFA),
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                    ),
                    onChanged: (v) async {
                      final kw = v.trim();
                      if (kw.isEmpty) {
                        setSheetState(() => searchResults = []);
                        return;
                      }
                      try {
                        final r = await DishService.search(keyword: kw, pageSize: 10);
                        if (!ctx.mounted) return;
                        setSheetState(() =>
                            searchResults = r.records.map((d) => _DishLite(d.id, d.name)).toList());
                      } catch (_) {}
                    },
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 240,
                    child: searchResults.isEmpty
                        ? const Center(
                            child: Text('输入菜名搜索',
                                style: TextStyle(color: AppColors.textSecondary, fontSize: 13)),
                          )
                        : ListView.builder(
                            itemCount: searchResults.length,
                            itemBuilder: (_, i) {
                              final d = searchResults[i];
                              return ListTile(
                                dense: true,
                                title: Text(d.name, style: const TextStyle(fontSize: 14)),
                                onTap: () {
                                  Navigator.pop(ctx);
                                  _doAddDish(d.id, d.name, 1);
                                },
                              );
                            },
                          ),
                  ),
                ],
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _sheetTab(String label, bool active, VoidCallback onTap) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        decoration: BoxDecoration(
          color: active ? AppColors.primary : const Color(0xFFF5F5F5),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Text(label,
            style: TextStyle(fontSize: 12, color: active ? Colors.white : const Color(0xFF666666))),
      ),
    );
  }

  Future<void> _submitQuickAdd() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      _showSnack('请输入菜名');
      return;
    }
    Navigator.pop(context);
    // 快速模式：只记名字，不记营养
    final existItems = (_log?.items ?? [])
        .map((it) => _itemToJson(it))
        .toList();
    existItems.add({
      'ingredientId': null,
      'dishId': null,
      'amount': 1,
      'dishName': name,
    });
    try {
      await DailyLogService.submitDailyLog({
        'date': _dateStr,
        'items': existItems,
      });
      _showSnack('已记录');
      _load();
    } catch (e) {
      _showSnack('保存失败');
    }
  }

  Future<void> _doAddDish(int dishId, String name, double servings) async {
    final existItems = (_log?.items ?? [])
        .map((it) => _itemToJson(it))
        .toList();
    existItems.add({
      'dishId': dishId,
      'amount': servings,
      'servingFactor': 1,
    });
    try {
      await DailyLogService.submitDailyLog({
        'date': _dateStr,
        'items': existItems,
      });
      _showSnack('已记录: $name');
      _load();
    } catch (e) {
      _showSnack('保存失败');
    }
  }

  Map<String, dynamic> _itemToJson(DailyLogItemVO it) {
    return {
      if (it.dishId != null) 'dishId': it.dishId,
      if (it.ingredientId != null) 'ingredientId': it.ingredientId,
      'amount': it.amount,
      if (it.servingFactor != null) 'servingFactor': it.servingFactor,
    };
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg), duration: const Duration(seconds: 2)));
  }

  void _showSnack(String msg) => _snack(msg);

  // ===== UI =====

  @override
  Widget build(BuildContext context) {
    final memberName = context.watch<MemberStore>().currentName;

    return Scaffold(
      appBar: AppBar(
        title: const Text('每日饮食'),
        actions: [
          if (_target != null)
            Padding(
              padding: const EdgeInsets.only(right: 4),
              child: Row(mainAxisSize: MainAxisSize.min, children: [
                const Text('精准', style: TextStyle(fontSize: 12, color: Colors.white70)),
                Switch(
                  value: _preciseMode,
                  onChanged: (v) => setState(() => _preciseMode = v),
                  activeTrackColor: Colors.white38,
                ),
              ]),
            ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                _buildDateBar(),
                if (memberName.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                    child: Text('当前成员：$memberName',
                        style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                  ),
                if (_isToday && _preciseMode && _target != null)
                  _buildPreciseCard()
                else if (_isToday)
                  _buildLightSummary(),
                const SizedBox(height: 8),
                Expanded(
                  child: _log == null || _log!.items.isEmpty
                      ? Center(
                          child: Text(
                            _isToday ? '今天还没记录，点下方 + 记一餐' : '当天暂无记录',
                            style: const TextStyle(color: AppColors.textSecondary, fontSize: 14),
                          ),
                        )
                      : ListView.builder(
                          padding: const EdgeInsets.symmetric(horizontal: 14),
                          itemCount: _log!.items.length,
                          itemBuilder: (_, i) {
                            final it = _log!.items[i];
                            return ListTile(
                              leading: CircleAvatar(
                                radius: 16,
                                backgroundColor:
                                    it.isDish ? AppColors.primary.withAlpha(30) : AppColors.warnOrange.withAlpha(30),
                                child: Icon(
                                  it.isDish ? Icons.restaurant : Icons.eco,
                                  size: 16,
                                  color: it.isDish ? AppColors.primary : AppColors.warnOrange,
                                ),
                              ),
                              title: Text(it.displayName, style: const TextStyle(fontSize: 14)),
                              subtitle: Text(
                                '${it.amount.toStringAsFixed(it.isDish ? 0 : 0)}${it.isDish ? " 份" : " g"}',
                                style: const TextStyle(fontSize: 12, color: AppColors.textSecondary),
                              ),
                              dense: true,
                            );
                          },
                        ),
                ),
              ],
            ),
      floatingActionButton: _isToday
          ? FloatingActionButton.extended(
              onPressed: _showAddSheet,
              icon: const Icon(Icons.add),
              label: const Text('记一餐'),
            )
          : null,
    );
  }

  Widget _buildDateBar() {
    final isToday = _isToday;
    return GestureDetector(
      onHorizontalDragEnd: (details) {
        if (details.primaryVelocity == null) return;
        if (details.primaryVelocity! > 50) {
          _prevDay(); // 右滑 → 前一天
        } else if (details.primaryVelocity! < -50) {
          if (!isToday) _nextDay(); // 左滑 → 后一天（不超过今天）
        }
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        color: const Color(0xFFFAF8F5),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            IconButton(icon: const Icon(Icons.chevron_left), onPressed: _prevDay, iconSize: 26),
            GestureDetector(
              onTap: _pickDate,
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Text('${_date.month}月${_date.day}日',
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                Text(
                  isToday ? '今天 · 左右滑动切换' : _weekLabel(_date.weekday),
                  style: TextStyle(fontSize: 12, color: isToday ? AppColors.primary : AppColors.textSecondary),
                ),
              ]),
            ),
            IconButton(
              icon: const Icon(Icons.chevron_right),
              onPressed: isToday ? null : _nextDay,
              iconSize: 26,
              color: isToday ? Colors.grey.shade300 : null,
            ),
          ],
        ),
      ),
    );
  }

  // ===== 轻量摘要 =====
  Widget _buildLightSummary() {
    final items = _log?.items ?? [];
    if (items.isEmpty) {
      return const Padding(
        padding: EdgeInsets.all(12),
        child: Text('今天还没记录', style: TextStyle(color: AppColors.textSecondary)),
      );
    }
    final cal = _totalCalorie();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Text('今天 ${items.length} 项 · ${cal > 0 ? "约 $cal kcal" : ""}',
          style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600)),
    );
  }

  // ===== 精准卡片 =====
  Widget _buildPreciseCard() {
    if (_target == null) return const SizedBox.shrink();
    final actualCal = _totalCalorie();
    final remaining = _target!.calorieTarget - actualCal;
    final ratio = _target!.calorieTarget > 0
        ? (actualCal / _target!.calorieTarget).clamp(0.0, 1.3)
        : 0.0;

    final ringColor = ratio > 1.0
        ? AppColors.warnRed
        : ratio > 0.85
            ? AppColors.warnOrange
            : AppColors.saveGreen;

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(children: [
          // 热量环
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            SizedBox(
              width: 72, height: 72,
              child: Stack(alignment: Alignment.center, children: [
                SizedBox(
                  width: 72, height: 72,
                  child: CircularProgressIndicator(
                    value: ratio.clamp(0.0, 1.0),
                    strokeWidth: 6,
                    backgroundColor: Colors.grey.shade200,
                    valueColor: AlwaysStoppedAnimation(ringColor),
                  ),
                ),
                Text('${(ratio * 100).toInt()}%',
                    style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: ringColor)),
              ]),
            ),
            const SizedBox(width: 20),
            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('热量预算', style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
              Text('$actualCal / ${_target!.calorieTarget} kcal',
                  style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              Text(
                remaining >= 0 ? '剩余 $remaining kcal' : '超出 ${-remaining} kcal',
                style: TextStyle(
                    fontSize: 13,
                    color: remaining >= 0 ? AppColors.saveGreen : AppColors.warnRed,
                    fontWeight: FontWeight.w600),
              ),
              Text('${_target!.goalLabel} · BMR ${_target!.bmr}',
                  style: const TextStyle(fontSize: 11, color: AppColors.textSecondary)),
            ]),
          ]),
          const SizedBox(height: 14),
          _bar('🥩 蛋白质', _nutVal('protein'), _target!.proteinTarget, AppColors.warnRed),
          const SizedBox(height: 6),
          _bar('🍚 碳水', _nutVal('carb'), _target!.carbTarget, AppColors.warnOrange),
          const SizedBox(height: 6),
          _bar('🥑 脂肪', _nutVal('fat'), _target!.fatTarget, AppColors.saveGreen),
        ]),
      ),
    );
  }

  Widget _bar(String label, int actual, int target, Color color) {
    final ratio = target > 0 ? (actual / target).clamp(0.0, 1.2) : 0.0;
    final c = ratio > 1.0 ? AppColors.warnRed : ratio > 0.85 ? AppColors.warnOrange : color;
    return Row(children: [
      SizedBox(width: 66, child: Text(label, style: const TextStyle(fontSize: 12))),
      Expanded(
        child: ClipRRect(
          borderRadius: BorderRadius.circular(3),
          child: LinearProgressIndicator(
            value: ratio.clamp(0.0, 1.0), minHeight: 7,
            backgroundColor: Colors.grey.shade200,
            valueColor: AlwaysStoppedAnimation(c),
          ),
        ),
      ),
      const SizedBox(width: 6),
      SizedBox(width: 72, child: Text('$actual/$target g',
          style: TextStyle(fontSize: 11, color: Colors.grey.shade600))),
    ]);
  }

  // ===== 营养计算 =====

  int _totalCalorie() => _nutVal('calorie');

  int _nutVal(String name) {
    for (final m in _metrics) {
      if (m.name == name && _nutrition.containsKey(m.id)) {
        return _nutrition[m.id]!.round();
      }
    }
    return 0;
  }
}

class _DishLite {
  final int id;
  final String name;
  const _DishLite(this.id, this.name);
}
