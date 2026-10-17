import 'dart:io';

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/app_theme.dart';
import '../../core/error_text.dart';
import '../../services/community_service.dart';
import '../../services/upload_service.dart';
import '../../widgets/dish_image.dart';

/// 从食记 / 话题带入发布页的参数（GoRoute extra）。
class PostDraftSeed {
  final int? dishId;
  final String? dishName;
  final String? dishCoverUrl;
  final int? cookRecordId;
  final List<String> imageUrls;
  final String? fromText; // 「今天 18:40 做了 滑蛋虾仁」
  final int? topicId;
  final String? topicName;
  const PostDraftSeed({
    this.dishId,
    this.dishName,
    this.dishCoverUrl,
    this.cookRecordId,
    this.imageUrls = const [],
    this.fromText,
    this.topicId,
    this.topicName,
  });
}

/// 发布到厨房圈（原型 PostCreate.html）：从食记带入、图片网格、标题正文、AI 帮写、
/// 关联菜谱、话题、可见范围、隐私提示。成功返回 true。
class PostCreatePage extends StatefulWidget {
  final PostDraftSeed seed;
  const PostCreatePage({super.key, this.seed = const PostDraftSeed()});

  @override
  State<PostCreatePage> createState() => _PostCreatePageState();
}

class _PostCreatePageState extends State<PostCreatePage> {
  static const _maxImages = 9;
  static const _titleMax = 30;
  late final List<String> _images = [...widget.seed.imageUrls];
  final _title = TextEditingController();
  final _text = TextEditingController();
  final Set<int> _topicIds = {};
  final Map<int, String> _topics = {};
  bool _followersOnly = false;
  bool _uploading = false;
  bool _publishing = false;
  String? _aiDraft;
  bool _aiLoading = false;

  @override
  void initState() {
    super.initState();
    final s = widget.seed;
    if (s.topicId != null) {
      _topics[s.topicId!] = s.topicName ?? '话题';
      _topicIds.add(s.topicId!);
    }
    _loadTopics();
  }

  Future<void> _loadTopics() async {
    try {
      final b = await CommunityService.banner();
      final tp = b.topic;
      if (tp != null && mounted) setState(() => _topics.putIfAbsent(tp.topicId, () => tp.name));
    } catch (_) {}
  }

  @override
  void dispose() {
    _title.dispose();
    _text.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    if (_images.length >= _maxImages || _uploading) return;
    final files = await ImagePicker().pickMultiImage(limit: _maxImages - _images.length);
    if (files.isEmpty) return;
    setState(() => _uploading = true);
    try {
      for (final f in files) {
        final r = await UploadService.uploadOne(File(f.path));
        if (!mounted) return;
        setState(() => _images.add(r.url));
      }
    } catch (e) {
      _toast('上传失败：${errorText(e)}');
    } finally {
      if (mounted) setState(() => _uploading = false);
    }
  }

  Future<void> _ai([String style = 'default']) async {
    if (_aiLoading) return;
    setState(() => _aiLoading = true);
    try {
      final text = await CommunityService.aiText(
        dishId: widget.seed.dishId,
        cookRecordId: widget.seed.cookRecordId,
        imageUrls: _images,
        style: style,
      );
      if (mounted) setState(() => _aiDraft = text);
    } catch (e) {
      _toast('AI 帮写暂不可用：${errorText(e)}');
    } finally {
      if (mounted) setState(() => _aiLoading = false);
    }
  }

  Future<void> _publish() async {
    final title = _title.text.trim();
    if (_images.isEmpty) return _toast('至少放一张成品图');
    if (title.isEmpty) return _toast('写个标题吧');
    setState(() => _publishing = true);
    try {
      final r = await CommunityService.publish(
        title: title,
        text: _text.text.trim(),
        imageUrls: _images,
        dishId: widget.seed.dishId,
        topicIds: _topicIds.toList(),
        followersOnly: _followersOnly,
        fromCookRecordId: widget.seed.cookRecordId,
      );
      if (!mounted) return;
      _toast(r.status == 'REVIEWING' ? '已提交，审核通过后大家就能看到' : '发布成功');
      context.pop(true);
    } catch (e) {
      _toast('发布失败：${errorText(e)}');
    } finally {
      if (mounted) setState(() => _publishing = false);
    }
  }

  void _toast(String s) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(s)));
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    final seed = widget.seed;
    return Scaffold(
      backgroundColor: t.bg,
      appBar: AppBar(
        backgroundColor: t.bg,
        surfaceTintColor: t.bg,
        leading: TextButton(onPressed: () => context.pop(), child: const Text('取消')),
        leadingWidth: 72,
        centerTitle: true,
        title: Text('发布到厨房圈', style: ts.pageTitle),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: AppTokens.sp12),
            child: ElevatedButton(
              key: const Key('post-publish'),
              style: ElevatedButton.styleFrom(minimumSize: const Size(64, 36), shape: const StadiumBorder()),
              onPressed: _publishing || _uploading ? null : _publish,
              child: Text(_publishing ? '发布中' : '发布'),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(AppTokens.sp16, AppTokens.sp4, AppTokens.sp16, AppTokens.sp32),
        children: [
          if (seed.fromText != null) ...[_fromBar(t, seed), const SizedBox(height: AppTokens.sp12)],
          _imageGrid(t),
          const SizedBox(height: AppTokens.sp4),
          Text('长按拖动调整顺序，第一张为封面', style: ts.caption),
          const SizedBox(height: AppTokens.sp16),
          TextField(
            key: const Key('post-title'),
            controller: _title,
            maxLength: _titleMax,
            style: ts.subtitle,
            decoration: const InputDecoration(hintText: '标题：一句话说说这道菜'),
          ),
          TextField(
            key: const Key('post-text'),
            controller: _text,
            minLines: 4,
            maxLines: 10,
            maxLength: 1000,
            style: ts.body.copyWith(color: t.title),
            decoration: const InputDecoration(hintText: '做法心得、火候、给家人的改良…'),
          ),
          if (_aiDraft != null) _aiBox(t),
          Align(
            alignment: Alignment.centerLeft,
            child: OutlinedButton.icon(
              key: const Key('post-ai'),
              onPressed: _aiLoading ? null : () => _ai(),
              icon: const Icon(Icons.auto_awesome_rounded, size: 18),
              label: Text(_aiLoading ? 'AI 正在写…' : 'AI 帮我写'),
            ),
          ),
          const SizedBox(height: AppTokens.sp16),
          if (seed.dishName != null) _recipeRow(t, seed),
          const SizedBox(height: AppTokens.sp16),
          Text('话题', style: ts.sectionLabel.copyWith(color: t.body)),
          const SizedBox(height: AppTokens.sp8),
          _topicsWrap(t),
          const SizedBox(height: AppTokens.sp16),
          Text('谁可以看', style: ts.sectionLabel.copyWith(color: t.body)),
          const SizedBox(height: AppTokens.sp8),
          Row(children: [
            Expanded(child: _visOption(t, '所有人', Icons.public_rounded, !_followersOnly, () => setState(() => _followersOnly = false))),
            const SizedBox(width: AppTokens.sp10),
            Expanded(child: _visOption(t, '仅关注我的', Icons.group_outlined, _followersOnly, () => setState(() => _followersOnly = true))),
          ]),
          const SizedBox(height: AppTokens.sp16),
          Container(
            padding: const EdgeInsets.all(AppTokens.sp12),
            decoration: BoxDecoration(color: AppTokens.infoBg, borderRadius: BorderRadius.circular(AppTokens.rMd)),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.shield_outlined, size: 18, color: AppTokens.infoText),
                const SizedBox(width: AppTokens.sp8),
                Expanded(
                  child: Text('不会带上家庭成员名与余量，只发布菜品、照片和你写的文字。',
                      style: ts.caption.copyWith(color: AppTokens.infoText)),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppTokens.sp8),
          Text('发布后会经过 AI 预审；含外链、广告的内容不会展示。', style: ts.caption),
        ],
      ),
    );
  }

  Widget _fromBar(AppTokens t, PostDraftSeed seed) {
    final ts = t.textStyles;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp10),
      decoration: BoxDecoration(
        color: t.highlight,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.primary.withAlpha(90)),
      ),
      child: Row(
        children: [
          DishImage(url: seed.dishCoverUrl, name: seed.dishName ?? '食记', width: 40, height: 40, radius: AppTokens.rSm),
          const SizedBox(width: AppTokens.sp10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('从食记带入', style: ts.cardTitle.copyWith(color: t.title)),
                Text('${seed.fromText} · 已带入 ${seed.imageUrls.length} 张照片', style: ts.caption),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _imageGrid(AppTokens t) {
    return LayoutBuilder(builder: (_, c) {
      final w = (c.maxWidth - AppTokens.sp8 * 3) / 4;
      final tiles = <Widget>[
        for (var i = 0; i < _images.length; i++)
          LongPressDraggable<int>(
            data: i,
            feedback: DishImage(url: _images[i], name: '', width: w, height: w, radius: AppTokens.rSm),
            child: DragTarget<int>(
              onAcceptWithDetails: (d) => setState(() {
                final from = d.data;
                final img = _images.removeAt(from);
                _images.insert(i, img);
              }),
              builder: (_, __, ___) => Stack(children: [
                DishImage(url: _images[i], name: '', width: w, height: w, radius: AppTokens.rSm),
                if (i == 0)
                  Positioned(
                    left: 0,
                    right: 0,
                    bottom: 0,
                    child: Container(
                      color: AppTokens.photoScrim,
                      alignment: Alignment.center,
                      child: Text('封面', style: t.textStyles.tiny.copyWith(color: AppTokens.onPhoto)),
                    ),
                  ),
                Positioned(
                  right: 2,
                  top: 2,
                  child: InkResponse(
                    onTap: () => setState(() => _images.removeAt(i)),
                    child: Container(
                      decoration: const BoxDecoration(color: AppTokens.photoScrim, shape: BoxShape.circle),
                      child: const Icon(Icons.close_rounded, size: 16, color: AppTokens.onPhoto),
                    ),
                  ),
                ),
              ]),
            ),
          ),
        if (_images.length < _maxImages)
          InkWell(
            key: const Key('post-add-image'),
            onTap: _pick,
            borderRadius: BorderRadius.circular(AppTokens.rSm),
            child: Container(
              width: w,
              height: w,
              decoration: BoxDecoration(
                color: t.card,
                borderRadius: BorderRadius.circular(AppTokens.rSm),
                border: Border.all(color: t.border),
              ),
              child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                Icon(_uploading ? Icons.cloud_upload_outlined : Icons.add_rounded, color: t.body),
                Text(_uploading ? '上传中' : '添加', style: t.textStyles.caption),
              ]),
            ),
          ),
      ];
      return Wrap(spacing: AppTokens.sp8, runSpacing: AppTokens.sp8, children: tiles);
    });
  }

  Widget _aiBox(AppTokens t) {
    final ts = t.textStyles;
    Widget btn(String label, VoidCallback onTap, {bool primary = false}) => Padding(
          padding: const EdgeInsets.only(right: AppTokens.sp6),
          child: primary
              ? ElevatedButton(
                  style: ElevatedButton.styleFrom(minimumSize: const Size(0, 32), padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10)),
                  onPressed: onTap,
                  child: Text(label))
              : OutlinedButton(
                  style: OutlinedButton.styleFrom(minimumSize: const Size(0, 32), padding: const EdgeInsets.symmetric(horizontal: AppTokens.sp10)),
                  onPressed: onTap,
                  child: Text(label)),
        );
    return Container(
      margin: const EdgeInsets.only(bottom: AppTokens.sp10),
      padding: const EdgeInsets.all(AppTokens.sp12),
      decoration: BoxDecoration(
        color: t.highlight,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.primary.withAlpha(110)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Icon(Icons.auto_awesome_rounded, size: 14, color: t.primaryDeep),
            const SizedBox(width: AppTokens.sp4),
            Text('AI 草稿 · 根据你的食记和菜谱步骤生成', style: ts.xs.copyWith(color: t.primaryDeep)),
          ]),
          const SizedBox(height: AppTokens.sp6),
          Text(_aiDraft!, style: ts.body.copyWith(color: t.title, height: 1.6)),
          const SizedBox(height: AppTokens.sp8),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(children: [
              btn('用这段', () => setState(() {
                    _text.text = _aiDraft!;
                    _aiDraft = null;
                  }), primary: true),
              btn('换一版', () => _ai()),
              btn('更口语', () => _ai('casual')),
              btn('更短', () => _ai('short')),
            ]),
          ),
        ],
      ),
    );
  }

  Widget _recipeRow(AppTokens t, PostDraftSeed seed) {
    final ts = t.textStyles;
    return Container(
      padding: const EdgeInsets.all(AppTokens.sp10),
      decoration: BoxDecoration(
        color: t.card,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        border: Border.all(color: t.border),
      ),
      child: Row(children: [
        DishImage(url: seed.dishCoverUrl, name: seed.dishName!, width: 44, height: 44, radius: AppTokens.rSm),
        const SizedBox(width: AppTokens.sp10),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('关联菜谱', style: ts.sectionLabel.copyWith(color: t.body)),
            Text(seed.dishName!, style: ts.cardTitle.copyWith(color: t.title)),
          ]),
        ),
        Text('别人可一键照着做', style: ts.caption),
      ]),
    );
  }

  Widget _topicsWrap(AppTokens t) {
    final ts = t.textStyles;
    if (_topics.isEmpty) return Text('本周暂无话题挑战', style: ts.caption);
    return Wrap(
      spacing: AppTokens.sp8,
      runSpacing: AppTokens.sp8,
      children: [
        for (final e in _topics.entries)
          FilterChip(
            label: Text(e.value.startsWith('#') ? e.value : '#${e.value}'),
            selected: _topicIds.contains(e.key),
            onSelected: (v) => setState(() => v ? _topicIds.add(e.key) : _topicIds.remove(e.key)),
            selectedColor: t.primarySoft,
            checkmarkColor: t.primaryDeep,
            side: BorderSide(color: t.border),
            shape: const StadiumBorder(),
          ),
      ],
    );
  }

  Widget _visOption(AppTokens t, String label, IconData icon, bool selected, VoidCallback onTap) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppTokens.rMd),
        child: Container(
          height: 48,
          decoration: BoxDecoration(
            color: selected ? t.highlight : t.card,
            borderRadius: BorderRadius.circular(AppTokens.rMd),
            border: Border.all(color: selected ? t.primaryDeep : t.border, width: selected ? 1.5 : 1),
          ),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            Icon(icon, size: 18, color: selected ? t.primaryDeep : t.body),
            const SizedBox(width: AppTokens.sp6),
            Text(label, style: t.textStyles.body.copyWith(color: t.title, fontWeight: selected ? FontWeight.w700 : FontWeight.w400)),
          ]),
        ),
      );
}
