import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../services/pantry_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/status_chip.dart';

/// 食材详情页（盘点纠偏 + 变动明细，对齐 pantry-page-preview.html 右屏）。
///
/// 点库存页某行进入。顶部食材信息 + 当前合计；中部加减盘（输入实际数量，
/// 显示系统差 ±N）；底部明细时间线（最近 6 条流水）。保存调盘点接口。
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

  /// 盘点输入值（实际数了多少，按食材默认单位）。
  double _countValue = 0;
  bool _saving = false;

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
        _countValue = d.totalAmount; // 默认填当前值，用户改完算差
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
    // DESIGN.md §13：去掉「食材详情」AppBar；食材名 + 副信息移入 BackHeader。
    // 加载中/错误时 BackHeader 不传 title（只显箭头）。
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
                      '系统记 ${_fmt(d.totalAmount)} ${d.unitName ?? ''}',
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
    final delta = _countValue - d.totalAmount;
    final color = stockColor(d.status);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // 食材头（名称 + 系统记已移入 BackHeader，这里留头像 + 库存状态徽）
        Row(children: [
          Container(
            width: 48, height: 48,
            decoration: BoxDecoration(color: t.primarySoft, borderRadius: BorderRadius.circular(AppTokens.rMd)),
            alignment: Alignment.center,
            child: Text(d.displayName.characters.first, style: t.textStyles.h3.copyWith(color: _t.primaryDeep)),
          ),
          const Spacer(),
          StatusChip(label: stockLabel(d.status), color: color),
        ]),
        const SizedBox(height: 20),

        // 加减盘
        Text('实际数了多少？', style: t.textStyles.caption),
        const SizedBox(height: 10),
        Container(
          padding: const EdgeInsets.symmetric(vertical: 20),
          decoration: BoxDecoration(
            color: t.bg,
            border: Border.all(color: t.border),
            borderRadius: BorderRadius.circular(AppTokens.rLg),
          ),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            _circleBtn(Icons.remove, AppTokens.error, () => setState(() => _countValue = (_countValue - 1).clamp(0, 999999))),
            const SizedBox(width: 24),
            Column(children: [
              Text(_fmt(_countValue), style: t.textStyles.display.copyWith(height: 1)),
              const SizedBox(height: 4),
              Text('${d.unitName ?? ''} · 系统差 ${delta >= 0 ? '+' : ''}${_fmt(delta)}',
                  style: t.textStyles.tiny.copyWith(color: delta.abs() < 0.01 ? _t.caption : _t.primary)),
            ]),
            const SizedBox(width: 24),
            _circleBtn(Icons.add, AppTokens.success, () => setState(() => _countValue = (_countValue + 1).clamp(0, 999999))),
          ]),
        ),
        const SizedBox(height: 12),

        // 说明条
        Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: _t.highlight, border: Border.all(color: _t.border),
            borderRadius: BorderRadius.circular(AppTokens.rSm),
          ),
          child: Text(
            '纠正差额会记一笔「盘点」，下次做菜扣减更准。区别于「添加」手动入库（带来源标签）。',
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
                  StatusChip(label: c.sourceLabel, color: _sourceColor(c.source), fontSize: 10),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      c.sourceNote != null && c.sourceNote!.isNotEmpty ? c.sourceNote! : _fmtTime(c.createTime),
                      style: t.textStyles.sectionLabel.copyWith(color: t.body),
                    ),
                  ),
                  Text(c.deltaText,
                      style: t.textStyles.sectionLabel.copyWith(color: c.delta >= 0 ? AppTokens.success : AppTokens.error)),
                ]),
              )).toList(),
            ),
          ),
      ],
    );
  }

  Widget _circleBtn(IconData icon, Color color, VoidCallback onTap) {
    final t = AppTokens.of(context);
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 40, height: 40,
        decoration: BoxDecoration(
          color: t.card, border: Border.all(color: t.border),
          shape: BoxShape.circle,
        ),
        child: Icon(icon, size: 20, color: color),
      ),
    );
  }

  Color _sourceColor(String source) {
    switch (source) {
      case 'cook': return AppTokens.error;
      case 'purchase': return AppTokens.success;
      case 'inventory': return AppTokens.info;
      case 'manual': return _t.primary;
      default: return AppTokens.of(context).caption;
    }
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
      await PantryService.adjust(widget.ingredientId, _countValue);
      if (!mounted) return;
      Navigator.of(context).pop();
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('保存失败：$e')));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  static String _fmt(double v) {
    if (v == v.roundToDouble()) return v.toInt().toString();
    return v.toStringAsFixed(1);
  }
}
