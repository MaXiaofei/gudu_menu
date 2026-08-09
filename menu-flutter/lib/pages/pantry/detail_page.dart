import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/status_chip.dart';

/// 食材详情页（V42 改档位：3 档单选 + 变动流水，对齐 pantry-page.html 右屏）。
///
/// 点库存页某行进入。顶部食材信息 + 当前档位徽；中部 3 档单选（充足/不足/用完）；
/// 底部明细时间线（最近 6 条流水，stock_log）。保存调 PUT /pantry/{id}/level。
class PantryDetailPage extends StatefulWidget {
  final int ingredientId;
  const PantryDetailPage({super.key, required this.ingredientId});

  @override
  State<PantryDetailPage> createState() => _PantryDetailPageState();
}

class _PantryDetailPageState extends State<PantryDetailPage> {
  /// 主题 token 缓存（State 内 context 可用）。
  AppTokens get _t => AppTokens.of(context);

  PantryItemDetail? _detail;
  bool _loading = true;
  String? _error;

  /// 选中档位（默认当前档位）。
  String _selectedLevel = StockLevel.none;
  bool _saving = false;

  /// 3 档选项：值 → (标题, 副文案, 颜色)。
  static const _options = [
    (StockLevel.enough, '充足', '还有不少', AppTokens.success),
    (StockLevel.low, '不足', '剩一点点', AppTokens.warning),
    (StockLevel.none, '用完', '用光了', AppTokens.error),
  ];

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = _detail == null;
      _error = null;
    });
    try {
      final d = await PantryService.itemDetail(widget.ingredientId);
      if (!mounted) return;
      setState(() {
        _detail = d;
        _selectedLevel = d.level;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final d = _detail;
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            BackHeader(
              title: d?.displayName,
              subtitle: d == null
                  ? null
                  : Text(
                      d.levelLabel,
                      style: t.textStyles.sm.copyWith(color: t.caption),
                    ),
            ),
            Expanded(
              child: _loading
                  ? const LoadingView()
                  : _error != null
                      ? EmptyView(text: '加载失败：$_error')
                      : d == null
                          ? const EmptyView(text: '食材不存在')
                          : _buildBody(t),
            ),
          ],
        ),
      ),
      bottomNavigationBar: _detail == null || _saving
          ? null
          : SafeArea(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    ElevatedButton(
                      onPressed: _save,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: _t.primary,
                        foregroundColor: Colors.white,
                        // 显式有限 minimumSize：全局主题是 Size(inf,48)（全宽 CTA），
                        // 本按钮在 Row（无界宽）里会触发 "infinite width" 布局崩溃
                        minimumSize: const Size(200, 48),
                        padding: const EdgeInsets.symmetric(horizontal: 48, vertical: 12),
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
                      ),
                      child: const Text('保存', style: TextStyle(fontWeight: FontWeight.w800)),
                    ),
                  ],
                ),
              ),
            ),
    );
  }

  Widget _buildBody(AppTokens t) {
    final d = _detail!;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 食材头（名称 + 档位徽）
        Row(children: [
          InitialAvatar(name: d.displayName, size: 52),
          const Spacer(),
          StatusChip(label: d.levelLabel, color: stockColor(d.level)),
        ]),
        const SizedBox(height: 20),

        // 3 档单选
        Text('现在家里是什么情况？', style: t.textStyles.caption),
        const SizedBox(height: 10),
        ..._options.map((o) => _levelCard(t, o.$1, o.$2, o.$3, o.$4)),
        const SizedBox(height: 12),

        // 说明条
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: _t.highlight, border: Border.all(color: _t.border),
            borderRadius: BorderRadius.circular(AppTokens.rSm),
          ),
          child: Text(
            '选「用完」记一笔用完了，选「不足」记用了一些。昨天用完忘记的，现在补上就行。',
            style: t.textStyles.sectionLabel.copyWith(color: t.body, height: 1.5),
          ),
        ),
        const SizedBox(height: 20),

        // 明细时间线
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          Text('明细', style: t.textStyles.sectionLabel.copyWith(letterSpacing: 1)),
          Text('最近 ${d.changes.length} 条', style: t.textStyles.meta.copyWith(color: _t.primaryDeep)),
        ]),
        const SizedBox(height: 8),
        if (d.changes.isEmpty)
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(color: t.bg, borderRadius: BorderRadius.circular(AppTokens.rSm)),
            child: Text('暂无变动记录', style: t.textStyles.sectionLabel),
          )
        else
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(color: t.bg, borderRadius: BorderRadius.circular(AppTokens.rSm)),
            child: Column(
              children: d.changes.map((c) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(children: [
                  StatusChip(label: c.actionLabel, color: _actionColor(c.action), fontSize: 10),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      c.note != null && c.note!.isNotEmpty ? c.note! : c.changeText,
                      style: t.textStyles.sectionLabel.copyWith(color: t.body),
                    ),
                  ),
                  Text(_fmtTime(c.createTime),
                      style: t.textStyles.meta.copyWith(color: t.caption)),
                ]),
              )).toList(),
            ),
          ),
      ],
    );
  }

  /// 3 档单选卡片：选中主色实心圆点 + 主色描边；未选灰描边。
  Widget _levelCard(AppTokens t, String level, String title, String desc, Color color) {
    final selected = _selectedLevel == level;
    return GestureDetector(
      onTap: () => setState(() => _selectedLevel = level),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        decoration: BoxDecoration(
          color: t.bg,
          border: Border.all(color: selected ? t.primary : t.border, width: selected ? 2 : 1),
          borderRadius: BorderRadius.circular(AppTokens.rMd),
        ),
        child: Row(children: [
          Container(
            width: 20, height: 20,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: selected ? t.primary : null,
              border: Border.all(color: selected ? t.primary : t.border, width: 1.5),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Row(children: [
              Text(title, style: t.textStyles.md.copyWith(color: t.title, fontWeight: FontWeight.w800)),
              const SizedBox(width: 8),
              Text(desc, style: t.textStyles.sm.copyWith(color: t.caption)),
            ]),
          ),
        ]),
      ),
    );
  }

  Color _actionColor(String action) {
    final t = AppTokens.of(context);
    return switch (action) {
      'cook' => AppTokens.error,
      'cook_partial' => AppTokens.warning,
      'purchase' => AppTokens.success,
      'undo' => t.caption,
      _ => t.primary, // manual
    };
  }

  String _fmtTime(String? iso) {
    if (iso == null) return '';
    final dt = DateTime.tryParse(iso);
    if (dt == null) return '';
    return '${dt.month}/${dt.day} ${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
  }

  Future<void> _save() async {
    final d = _detail;
    if (d == null) return;
    setState(() => _saving = true);
    try {
      await PantryService.setLevel(widget.ingredientId, _selectedLevel);
      if (!mounted) return;
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('保存失败：$e')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}
