import 'package:flutter/material.dart';

import '../../core/app_theme.dart';
import '../../widgets/action_bar.dart';
import '../../widgets/error_view.dart';
import '../../widgets/loading_empty.dart';
import '../../widgets/markdown_text.dart';

/// 法律文本种类（G14）。
enum LegalKind { terms, privacy }

extension LegalKindX on LegalKind {
  String get title => this == LegalKind.terms ? '用户协议' : '隐私政策';

  /// 文案来源：仓库根 `docs/legal/*.md`，拷贝至 `assets/legal/`（修改后需同步）。
  String get asset =>
      this == LegalKind.terms ? 'assets/legal/terms.md' : 'assets/legal/privacy.md';

  /// 路由路径（登录前也可访问，见 router redirect 白名单）。
  String get path => this == LegalKind.terms ? '/legal/terms' : '/legal/privacy';
}

/// 《用户协议》《隐私政策》静态页：读取内置 Markdown 资源渲染。
class LegalPage extends StatefulWidget {
  final LegalKind kind;
  const LegalPage({super.key, required this.kind});

  @override
  State<LegalPage> createState() => _LegalPageState();
}

class _LegalPageState extends State<LegalPage> {
  String? _text;
  bool _error = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_text == null && !_error) _load();
  }

  Future<void> _load() async {
    setState(() {
      _error = false;
      _text = null;
    });
    try {
      final s = await DefaultAssetBundle.of(context).loadString(widget.kind.asset);
      if (mounted) setState(() => _text = s);
    } catch (_) {
      // 资源缺失/读取失败 → 错误态 + 重试（不静默）
      if (mounted) setState(() => _error = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
      backgroundColor: t.bg,
      body: SafeArea(
        bottom: false,
        child: Column(
          children: [
            BackHeader(title: widget.kind.title),
            Expanded(
              child: _error
                  ? ErrorView(text: '${widget.kind.title}加载失败', onRetry: _load)
                  : _text == null
                      ? const LoadingView()
                      : SingleChildScrollView(
                          padding: const EdgeInsets.fromLTRB(AppTokens.sp16,
                              AppTokens.sp8, AppTokens.sp16, AppTokens.sp32),
                          child: MarkdownText(_text!),
                        ),
            ),
          ],
        ),
      ),
    );
  }
}
