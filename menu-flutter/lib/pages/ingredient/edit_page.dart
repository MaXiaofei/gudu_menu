import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../core/app_theme.dart';
import '../../core/api_client.dart';
import '../../services/ingredient_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/initial_avatar.dart';
import '../../widgets/loading_empty.dart';

/// 食材编辑页（对齐原型 pantry-ingredient.html 右侧卡片）。
///
/// V55（食材去单位）：只读头部（名称 + 品类）+ 食用属性卡片 + ✕ 删除食材。
/// 原默认单位「改」/ 单位→克换算表 / 单价卡片随单位解绑一并删除。
/// 保存：updateIngredient(食用属性)。
class IngredientEditPage extends StatefulWidget {
  final int ingredientId;
  const IngredientEditPage({super.key, required this.ingredientId});

  @override
  State<IngredientEditPage> createState() => _IngredientEditPageState();
}

class _IngredientEditPageState extends State<IngredientEditPage> {
  AppTokens get _t => AppTokens.of(context);

  bool _loading = true;
  bool _saving = false;

  // 食材信息（只读展示 + 可编辑项）
  String _name = '';
  String? _categoryName;
  int _edible = 1; // 1食用/2饮料零食/3生活用品

  static const _edibleNames = {1: '食用', 2: '饮料零食', 3: '生活用品'};

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final ing = await ApiClient.instance.get('/ingredient/${widget.ingredientId}');
      if (mounted) {
        setState(() {
          final m = ing is Map ? ing : const <String, dynamic>{};
          _name = (m['name'] ?? '') as String;
          _categoryName = m['categoryName'] as String?;
          _edible = (m['edible'] as num?)?.toInt() ?? 1;
          _loading = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await IngredientService.updateIngredient({
        'id': widget.ingredientId,
        'edible': _edible,
      });
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('已保存')));
        context.pop();
      }
    } catch (e) {
      if (mounted) _snack('保存失败: $e');
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  /// 食用属性卡片：弹三选。
  Future<void> _editEdible() async {
    final v = await showModalBottomSheet<int>(
      context: context,
      backgroundColor: _t.card,
      builder: (ctx) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('食用属性', style: _t.textStyles.md.copyWith(
                  fontWeight: FontWeight.w800, color: _t.title)),
            ),
            for (final e in _edibleNames.entries)
              ListTile(
                title: Text(e.value,
                    style: _t.textStyles.sm.copyWith(color: _t.body)),
                trailing: e.key == _edible
                    ? Icon(Icons.check, size: 18, color: _t.primary)
                    : null,
                onTap: () => Navigator.pop(ctx, e.key),
              ),
          ],
        ),
      ),
    );
    if (v != null && mounted) setState(() => _edible = v);
  }

  /// 删除食材确认。
  Future<void> _confirmDelete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('删除食材'),
        content: Text('确定删除「$_name」？关联的用量记录会保留。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
          TextButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('删除', style: TextStyle(color: AppTokens.error))),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await IngredientService.deleteIngredient(widget.ingredientId);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('已删除')));
        context.pop();
      }
    } catch (_) {
      if (mounted) _snack('删除失败');
    }
  }

  void _snack(String msg) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  Widget build(BuildContext context) {
    final t = _t;
    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: Column(children: [
          // §13.2/§13.3 详情页头：BackHeader（箭头 + 真实名 + ✕ 删除 + 首字块/品类副信息）
          BackHeader(
            title: _loading ? '' : _name,
            action: GestureDetector(
              onTap: _confirmDelete,
              child: Padding(
                padding: const EdgeInsets.all(4),
                child: Text('✕', style: t.textStyles.md.copyWith(color: t.caption)),
              ),
            ),
            subtitle: _loading
                ? null
                : Row(children: [
                    InitialAvatar(name: _name, size: 36, radius: 10),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _categoryName == null || _categoryName!.isEmpty
                            ? '未分类'
                            : _categoryName!,
                        style: t.textStyles.sm.copyWith(color: t.caption),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ]),
          ),
          Expanded(
            child: _loading ? const LoadingView() : _buildBody(t),
          ),
          // 底部保存
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: t.primary,
                  foregroundColor: Colors.white,
                  minimumSize: const Size.fromHeight(48),
                ),
                onPressed: _saving ? null : _save,
                child: _saving
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                            strokeWidth: 2, color: Colors.white))
                    : const Text('保存', style: TextStyle(fontWeight: FontWeight.w800)),
              ),
            ),
          ),
        ]),
      ),
    );
  }

  Widget _buildBody(AppTokens t) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      children: [
        // 食用属性卡片（点卡片编辑）
        GestureDetector(
          onTap: _editEdible,
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(
              color: t.card,
              border: Border.all(color: t.border),
              borderRadius: BorderRadius.circular(AppTokens.rMd),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('食用属性', style: t.textStyles.xs.copyWith(color: t.caption)),
                const SizedBox(height: 2),
                Text(_edibleNames[_edible] ?? '食用',
                    style: t.textStyles.sm.copyWith(
                        fontWeight: FontWeight.w800, color: t.title)),
              ],
            ),
          ),
        ),
        const SizedBox(height: 14),
      ],
    );
  }
}
