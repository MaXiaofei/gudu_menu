import 'package:flutter/material.dart';

import '../../core/image_helper.dart';
import '../../core/app_theme.dart';
import '../../models/dish.dart';
import '../../models/menu.dart';
import '../../services/dish_service.dart';
import '../../services/menu_service.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/image_viewer.dart';
import '../../widgets/loading_empty.dart';

/// 菜品详情。
/// 封面 + 营养区 + 用料 + 做法步骤 + 加到食集/去点评。
///
/// 图片策略：
/// - 列表/详情默认加载缩略图（/thumbnail/xxx.jpg），节省流量 + 加载快。
/// - 点击图片弹出全屏可查看器，加载原图（/original/xxx.jpg），支持双指缩放。
class DishDetailPage extends StatefulWidget {
  final int id;
  /// 是否显示底部操作按钮（加到食集 / 去点评）。
  /// 从食集详情点进来查看菜谱时为 false，避免冗余操作。
  final bool showActions;
  const DishDetailPage({super.key, required this.id, this.showActions = true});
  @override
  State<DishDetailPage> createState() => _DishDetailPageState();
}

class _DishDetailPageState extends State<DishDetailPage> {
  DishDetail? _detail;
  final int _serving = 1;
  bool _loading = true;

  /// 详情头部展示的标签（菜系 + 分类 + 标签去空合并）。
  List<String> get _dishTags => _detail == null
      ? const []
      : [
          ..._detail!.dish.cuisineNames,
          ..._detail!.dish.categoryNames,
          ..._detail!.dish.tagNames,
        ].where((s) => s.isNotEmpty).toList();

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      _detail = await DishService.detail(widget.id);
    } catch (_) {}
    if (mounted) setState(() => _loading = false);
  }

  /// 打开全屏图片查看器（加载原图）。
  void _openViewer(String url) {
    final urls = ImageHelper.resolve(url);
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => ImageViewer(
          thumbnailUrl: urls.thumbnail,
          originalUrl: urls.original,
        ),
      ),
    );
  }

  /// 加到食集：
  /// - 查"今天及以后"创建的食集 → 有则弹窗让用户选择加到哪个
  /// - 没有则弹输入框新建（名字预填菜谱名，可自定义）
  /// - 新建食集名字默认取菜谱名，用户可修改
  bool _adding = false;
  Future<void> _addToMenu() async {
    if (_adding) return;
    setState(() => _adding = true);
    final dishName = _detail?.dish.name ?? '新食集';
    try {
      final page = await MenuService.list(pageNum: 1, pageSize: 50);
      // 过滤：今天及以后创建的食集（createTime >= 今天 0 点）
      final todayStart = DateTime(DateTime.now().year, DateTime.now().month, DateTime.now().day);
      final recentMenus = page.records.where((m) {
        final created = m.createdAt;
        return created != null && !created.isBefore(todayStart);
      }).toList();

      if (!mounted) return;
      if (recentMenus.isEmpty) {
        // 无今天及以后的食集 → 弹输入框新建（预填菜谱名）
        final name = await _showNameDialog(dishName);
        if (name == null) {
          if (mounted) setState(() => _adding = false);
          return;
        }
        await MenuService.createMenu(name, dishIds: [widget.id]);
        if (!mounted) return;
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('已加入新食集「$name」')));
      } else {
        // 有今天及以后的食集 → 弹窗选择
        final selectedId = await _showMenuPicker(recentMenus);
        if (selectedId == null) {
          if (mounted) setState(() => _adding = false);
          return;
        }
        if (selectedId == -1) {
          // 选"新建食集" → 弹输入框（预填菜谱名）
          final name = await _showNameDialog(dishName);
          if (name == null) {
            if (mounted) setState(() => _adding = false);
            return;
          }
          await MenuService.createMenu(name, dishIds: [widget.id]);
          if (!mounted) return;
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('已加入新食集「$name」')));
        } else {
          // 加入已有食集（传 dishName 用于拼接食集名）
          await MenuService.addDishToMenu(selectedId, widget.id, dishName: dishName);
          final menuName = recentMenus.firstWhere((m) => m.id == selectedId).name;
          if (!mounted) return;
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text('已加入食集「$menuName」')));
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('加入食集失败')));
      }
    }
    if (mounted) setState(() => _adding = false);
  }

  /// 弹输入框让用户输入食集名字，[defaultName] 预填默认值（菜谱名）。
  /// 返回用户输入的名字，取消返回 null。
  Future<String?> _showNameDialog(String defaultName) async {
    final ctrl = TextEditingController(text: defaultName);
    return showDialog<String>(
      context: context,
      builder: (ctx) {
        final t = AppTokens.of(ctx);
        return AlertDialog(
          title: Text('新建食集',
              style: t.textStyles.lg),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            decoration: const InputDecoration(
              labelText: '食集名字',
              hintText: '输入食集名字',
              isDense: true,
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(null),
              child: const Text('取消'),
            ),
            ElevatedButton(
              onPressed: () {
                final name = ctrl.text.trim();
                Navigator.of(ctx).pop(name.isEmpty ? defaultName : name);
              },
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }

  /// 弹窗选择当天食集，返回选中的 menuId（-1=新建，null=取消）。
  Future<int?> _showMenuPicker(List<Menu> menus) async {
    return showModalBottomSheet<int>(
      context: context,
      builder: (ctx) {
        final t = AppTokens.of(ctx);
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Padding(
                padding: const EdgeInsets.all(AppTokens.sp16),
                child: Text('加到哪个食集？', style: t.textStyles.lg),
              ),
              const Divider(height: 1),
              ...menus.map((m) {
                    final created = m.createdAt;
                    final dateStr = created != null
                        ? '${created.month}/${created.day} ${created.hour.toString().padLeft(2, '0')}:${created.minute.toString().padLeft(2, '0')}'
                        : '';
                    return ListTile(
                      leading: Icon(Icons.restaurant_menu, color: t.primary),
                      title: Text(m.name),
                      subtitle: Text(
                        '$dateStr · 份数 ${m.servingCount ?? 1} · ${m.isDone ? '已完成' : '进行中'}',
                        style: t.textStyles.caption.copyWith(color: t.caption),
                      ),
                    onTap: () => Navigator.of(ctx).pop(m.id),
                    );
                  }),
              const Divider(height: 1),
              ListTile(
                leading: Icon(Icons.add_circle, color: t.primary),
                title: const Text('新建食集'),
                onTap: () => Navigator.of(ctx).pop(-1),
              ),
              const SizedBox(height: AppTokens.sp8),
            ],
          ),
        );
      },
    );
  }

  /// 构建可点击的缩略图（点一下弹全屏原图）。
  /// [placeholder] 自定义占位（封面传首字占位，§10.5）；默认图片图标（辅助图位）。
  Widget _thumbnailImage(String url,
      {double? width,
      double? height,
      BoxFit fit = BoxFit.cover,
      Widget Function(AppTokens, double?, double?)? placeholder}) {
    final t = AppTokens.of(context);
    final urls = ImageHelper.resolve(url);
    Widget fallback(AppTokens tt, double? w, double? h) =>
        (placeholder ?? _imagePlaceholder)(tt, w, h);
    return GestureDetector(
      onTap: () => _openViewer(url),
      child: Image.network(
        urls.thumbnail,
        width: width,
        height: height,
        fit: fit,
        // 加载失败/加载中统一走占位（DESIGN.md §1 禁止 spinner、§10.1 禁止空白破图）。
        errorBuilder: (_, __, ___) => fallback(t, width, height),
        loadingBuilder: (_, child, progress) =>
            progress == null ? child : fallback(t, width, height),
      ),
    );
  }

  /// 无封面 url 时的封面占位：奶油底 + 菜名首字（与菜谱缩略图占位规则一致，DESIGN.md §10.4，不用 emoji 顶替）。
  Widget _coverPlaceholder(AppTokens t, String name) {
    final initial = name.trim().isNotEmpty ? name.trim().characters.first : '菜';
    return Container(
      width: double.infinity,
      height: 220,
      color: t.secondary,
      alignment: Alignment.center,
      child: Text(
        initial,
        style: t.textStyles.display.copyWith(
          color: t.title.withAlpha(115), // ≈ 0.45 透明度
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  /// 图片占位：奶油色底 + 图片图标（DESIGN.md §10.2，加载中/失败都用它，禁 spinner/禁空白）。
  Widget _imagePlaceholder(AppTokens t, double? width, double? height) {
    final w = width ?? 80;
    final h = height ?? 80;
    final iconSize = (w < h ? w : h) * 0.4;
    return Container(
      width: width,
      height: height,
      color: t.secondary,
      alignment: Alignment.center,
      child: Icon(Icons.image_outlined,
          color: t.caption.withValues(alpha: 0.5), size: iconSize),
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    // DESIGN.md §13：去掉「菜品详情」废话标签 AppBar；菜名移进 BackHeader（箭头行+sp8+真实名 h3）。
    // 加载中/错误时 BackHeader 不传 title（只显箭头）。
    return Scaffold(
        body: SafeArea(
      bottom: false,
      child: Column(
        children: [
          BackHeader(
            title: _detail?.dish.name,
            subtitle: _detail == null
                ? null
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        [
                          if (_detail!.dish.prepTime != null)
                            '备料 ${_detail!.dish.prepTime}分',
                          if (_detail!.dish.cookTime != null)
                            '烹饪 ${_detail!.dish.cookTime}分',
                          '难度 ${_detail!.dish.difficulty ?? '-'}/5',
                        ].join(' · '),
                        style: t.textStyles.sm.copyWith(color: t.caption),
                      ),
                      // 来源（V49：自己创建/下厨房/美食杰…；导入菜谱显示来源名）
                      if (_detail!.dish.sourceName != null &&
                          _detail!.dish.sourceName!.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(top: 2),
                          child: Text('来源：${_detail!.dish.sourceName}',
                              style: t.textStyles.xs.copyWith(
                                  color: t.caption.withValues(alpha: 0.8))),
                        ),
                    ],
                  ),
          ),
          Expanded(
            child: _loading
                ? const LoadingView()
                : _detail == null
                    ? ErrorView(text: '加载详情失败', onRetry: _load) // §14.1 错误态
                    : ListView(
                        children: [
                          // 封面（DESIGN.md §10：有 url 显示图，加载中/失败走占位；无 url 也渲染占位容器，不留白）
                          if (_detail!.dish.coverUrl != null &&
                              _detail!.dish.coverUrl!.isNotEmpty)
                            _thumbnailImage(
                              _detail!.dish.coverUrl!,
                              width: double.infinity,
                              height: 220,
                              // 主视觉位占位统一首字（§10.5，与列表缩略图一致）
                              placeholder: (t, w, h) =>
                                  _coverPlaceholder(t, _detail!.dish.name),
                            )
                          else
                            _coverPlaceholder(t, _detail!.dish.name),
                          // 标签 + 备注（菜名/副信息已移入 BackHeader）
                          if (_dishTags.isNotEmpty || (_detail!.dish.note ?? '').isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.fromLTRB(
                                  AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, 0),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (_dishTags.isNotEmpty) ...[
                                    Wrap(
                                      spacing: AppTokens.sp8,
                                      runSpacing: AppTokens.sp4,
                                      children: _dishTags
                                          .map((tag) => Container(
                                                padding: const EdgeInsets.symmetric(
                                                    horizontal: 8, vertical: 2),
                                                decoration: BoxDecoration(
                                                  color: t.primarySoft,
                                                  borderRadius: BorderRadius.circular(
                                                      AppTokens.rSm),
                                                ),
                                                child: Text(tag,
                                                    style: t.textStyles.xs.copyWith(color: t.primary)),
                                              ))
                                          .toList(),
                                    ),
                                  ],
                                  if ((_detail!.dish.note ?? '').isNotEmpty) ...[
                                    const SizedBox(height: AppTokens.sp8),
                                    Text(_detail!.dish.note!,
                                        style: t.textStyles.sm),
                                  ],
                                ],
                              ),
                            ),
                          // 用料（份数 1）
                      if (_detail!.ingredients.isNotEmpty) ...[
                        Padding(
                          padding: const EdgeInsets.fromLTRB(
                              AppTokens.sp16, AppTokens.sp8, AppTokens.sp16, AppTokens.sp4),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text('用料', style: t.textStyles.subtitle),
                              Text('份数 $_serving · 共 ${_detail!.ingredients.length} 样',
                                  style: t.textStyles.xs.copyWith(color: t.caption)),
                            ],
                          ),
                        ),
                        Container(
                          margin: const EdgeInsets.symmetric(horizontal: AppTokens.sp16),
                          decoration: BoxDecoration(
                            color: t.card,
                            border: Border.all(color: t.border),
                            borderRadius: BorderRadius.circular(AppTokens.rMd),
                          ),
                          child: Column(
                            children: [
                              for (final ing in _detail!.ingredients) ...[
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: AppTokens.sp12, vertical: 10),
                                  child: Row(
                                    children: [
                                      Container(
                                        width: 28, height: 28,
                                        decoration: BoxDecoration(
                                          color: t.primarySoft,
                                          borderRadius: BorderRadius.circular(AppTokens.rSm),
                                        ),
                                        alignment: Alignment.center,
                                        child: Text(ing.displayName.characters.first,
                                            style: t.textStyles.md.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w600)),
                                      ),
                                      const SizedBox(width: AppTokens.sp8),
                                      Expanded(
                                        child: Text(ing.displayName,
                                            style: t.textStyles.sm.copyWith(
                                                fontWeight: FontWeight.w700,
                                                color: t.title)),
                                      ),
                                      // 用量（自然单位优先：「2 个」「适量」，§16.3；与库存解耦不标档位）
                                      Text(ing.amountText,
                                          style: t.textStyles.sm.copyWith(
                                              fontWeight: FontWeight.w800,
                                              color: t.title)),
                                    ],
                                  ),
                                ),
                                if (ing != _detail!.ingredients.last)
                                  Divider(height: 1, thickness: 1, color: t.border),
                              ],
                            ],
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.fromLTRB(
                              AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, 0),
                          child: Text('用量为 $_serving 份基准；做菜时按份数自动放大。',
                              style: t.textStyles.xs.copyWith(color: t.caption)),
                        ),
                      ],
                      const _SectionTitle('做法'),
                      ..._detail!.steps.asMap().entries.map((entry) {
                        final i = entry.key;
                        final s = entry.value;
                        return Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
                          decoration: BoxDecoration(
                              border: Border(
                                  top: BorderSide(
                                      color: t.border))),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text('步骤 ${i + 1}', style: TextStyle(color: t.title, fontWeight: FontWeight.w700)),
                              const SizedBox(height: AppTokens.sp8),
                              Text(s.text, style: TextStyle(color: t.body)),
                              if (s.imageList.isNotEmpty) ...[
                                const SizedBox(height: AppTokens.sp8),
                                Wrap(
                                  spacing: AppTokens.sp8,
                                  runSpacing: AppTokens.sp8,
                                  children: s.imageList
                                      .map((img) => _thumbnailImage(
                                            img,
                                            width: 80,
                                            height: 80,
                                          ))
                                      .toList(),
                                ),
                              ],
                            ],
                          ),
                        );
                      }),
                      const SizedBox(height: AppTokens.sp16),
                      if (widget.showActions)
                        Padding(
                          padding: const EdgeInsets.symmetric(
                              horizontal: AppTokens.sp16, vertical: AppTokens.sp12),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              ElevatedButton(
                                style: ElevatedButton.styleFrom(
                                    backgroundColor: AppTokens.success),
                                onPressed: _adding ? null : _addToMenu,
                                child: _adding
                                    ? const SizedBox(
                                        width: 18,
                                        height: 18,
                                        child: CircularProgressIndicator(
                                            strokeWidth: 2, color: Colors.white))
                                    : const Text('加到食集'),
                              ),
                            ],
                          ),
                        ),
                      const SizedBox(height: AppTokens.sp24),
                    ],
                  ),
            ),
          ], // Column children
        ), // Column
      ), // SafeArea
    ); // Scaffold
  }
}

class _SectionTitle extends StatelessWidget {
  final String text;
  const _SectionTitle(this.text);
  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Padding(
        padding: const EdgeInsets.all(AppTokens.sp16),
        child: Text(text,
            style: t.textStyles.subtitle),
      );
  }
}
