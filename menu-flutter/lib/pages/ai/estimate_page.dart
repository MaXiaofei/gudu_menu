import 'package:flutter/material.dart';

import '../../core/api_client.dart';
import '../../core/app_theme.dart';

/// AI 估营养：输入菜品描述 → AI 估算总营养。
class AiEstimatePage extends StatefulWidget {
  const AiEstimatePage({super.key});
  @override
  State<AiEstimatePage> createState() => _AiEstimatePageState();
}

class _AiEstimatePageState extends State<AiEstimatePage> {
  final _descCtrl = TextEditingController();
  String _servings = '1';
  bool _loading = false;
  Map<String, dynamic>? _result;
  String? _error;

  static const _labels = {
    '1': '热量(kcal)', '2': '蛋白质(g)', '3': '脂肪(g)',
    '4': '碳水(g)', '5': '糖(g)', '6': '升糖指数',
  };
  static const _icons = {'1': '🔥', '2': '🥩', '3': '🥑', '4': '🍚', '5': '🍬', '6': '📊'};
  static const _colors = {
    '1': AppTokens.error, '2': AppTokens.error, '3': AppTokens.success,
    '4': AppTokens.warning, '5': AppTokens.warning, '6': AppTokens.info,
  };

  Future<void> _estimate() async {
    final desc = _descCtrl.text.trim();
    if (desc.isEmpty) {
      setState(() => _error = '请输入菜品描述');
      return;
    }
    setState(() { _loading = true; _error = null; _result = null; });
    try {
      final data = await ApiClient.instance.post(
        '/ai/dish/estimate',
        body: {'description': desc, 'servingFactor': double.tryParse(_servings) ?? 1},
      );
      setState(() => _result = data as Map<String, dynamic>);
    } catch (e) {
      setState(() => _error = e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() {
    _descCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('AI 估营养')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(AppTokens.sp16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          // 说明卡
          Container(
            padding: const EdgeInsets.all(AppTokens.sp16),
            decoration: BoxDecoration(
              gradient: LinearGradient(colors: [t.primary, t.primaryDeep]),
              borderRadius: BorderRadius.circular(AppTokens.rMd),
            ),
            child: Column(children: [
              Text('🔍 描述一道菜或一餐', style: TextStyle(color: t.card, fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: AppTokens.sp4),
              Text('AI 估算总热量和营养', style: TextStyle(color: t.card.withValues(alpha: 0.7), fontSize: 12)),
            ]),
          ),
          const SizedBox(height: AppTokens.sp16),

          // 输入框
          TextField(
            controller: _descCtrl,
            maxLines: 4,
            style: const TextStyle(fontSize: 14),
            decoration: InputDecoration(
              hintText: '如：番茄炒蛋一份，加了两个鸡蛋',
              filled: true, fillColor: t.bg,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rMd)),
            ),
          ),
          const SizedBox(height: AppTokens.sp12),

          // 份数
          Row(children: [
            const Text('份数', style: TextStyle(fontSize: 14)),
            const SizedBox(width: AppTokens.sp12),
            SizedBox(
              width: 80,
              child: TextField(
                controller: TextEditingController(text: _servings),
                keyboardType: TextInputType.number,
                textAlign: TextAlign.center,
                decoration: InputDecoration(
                  isDense: true, contentPadding: const EdgeInsets.symmetric(vertical: AppTokens.sp8),
                  filled: true, fillColor: t.bg,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(AppTokens.rSm)),
                ),
                onChanged: (v) => _servings = v,
              ),
            ),
            const Spacer(),
            SizedBox(
              height: 44,
              child: ElevatedButton.icon(
                onPressed: _loading ? null : _estimate,
                icon: _loading
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                    : const Icon(Icons.auto_awesome, size: 18),
                label: const Text('估算'),
              ),
            ),
          ]),

          // 错误
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: AppTokens.sp12),
              child: Text(_error!, style: const TextStyle(color: AppTokens.error, fontSize: 14)),
            ),

          // 结果
          if (_result != null) ...[
            const SizedBox(height: AppTokens.sp16),
            _buildResult(),
          ],
        ]),
      ),
    );
  }

  Widget _buildResult() {
    final t = AppTokens.of(context);
    final nutrition = _result!['nutrition'] as Map<String, dynamic>? ?? {};
    final source = _result!['source'] as String? ?? '';
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(children: [
        Container(width: 4, height: 20, decoration: BoxDecoration(color: t.primary, borderRadius: BorderRadius.circular(AppTokens.rXs))),
        const SizedBox(width: AppTokens.sp8),
        const Text('估算结果', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        const Spacer(),
        Text(source, style: TextStyle(fontSize: 12, color: t.caption)),
      ]),
      const SizedBox(height: AppTokens.sp12),
      GridView.count(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        crossAxisCount: 2,
        mainAxisSpacing: AppTokens.sp8,
        crossAxisSpacing: AppTokens.sp8,
        childAspectRatio: 2.2,
        children: _labels.entries.map((e) {
          final val = nutrition[e.key];
          final displayVal = val != null ? (val is num ? val.toStringAsFixed(val.toDouble() == val.toInt() ? 0 : 1) : val) : '-';
          return Container(
            padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp12, vertical: AppTokens.sp8),
            decoration: BoxDecoration(
              color: t.card,
              borderRadius: BorderRadius.circular(AppTokens.rMd),
              boxShadow: t.elevationSm,
            ),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.center, children: [
              Row(children: [
                Text(_icons[e.key]!, style: const TextStyle(fontSize: 16)),
                const SizedBox(width: AppTokens.sp4),
                Text(e.value, style: TextStyle(fontSize: 12, color: t.caption)),
              ]),
              const SizedBox(height: AppTokens.sp4),
              Text('$displayVal', style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: _colors[e.key])),
            ]),
          );
        }).toList(),
      ),
    ]);
  }
}
