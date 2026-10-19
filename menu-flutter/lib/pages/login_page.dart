import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../core/ingredient_icons.dart';
import 'settings/legal_page.dart';
import '../stores/auth_store.dart';
import '../stores/member_store.dart';

/// 登录页（复刻 menu-mini/src/pages/login/Login.vue）。
/// 用户名+密码登录；成功后 AuthStore 变更触发 go_router redirect 到首页。
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _username = TextEditingController();
  final _password = TextEditingController();
  /// 已阅读并同意《用户协议》《隐私政策》（G14：必须勾选才能登录，默认不勾）。
  bool _agreed = false;
  late final TapGestureRecognizer _termsTap = TapGestureRecognizer()
    ..onTap = () => context.push(LegalKind.terms.path);
  late final TapGestureRecognizer _privacyTap = TapGestureRecognizer()
    ..onTap = () => context.push(LegalKind.privacy.path);

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
    _termsTap.dispose();
    _privacyTap.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    final u = _username.text.trim();
    final p = _password.text;
    if (u.isEmpty || p.isEmpty) {
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('请输入用户名和密码')));
      return;
    }
    if (!_agreed) {
      ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('请先阅读并同意《用户协议》和《隐私政策》')));
      return;
    }
    // await 前先取 store 引用，避免跨异步 gap 用 context（analyze 规则）
    final memberStore = context.read<MemberStore>();
    // 登录成功后 AuthStore 变更触发 redirect 到首页
    await context.read<AuthStore>().login(u, p);
    // 登录即定就餐成员（后端 login 时写 currentMemberId），重拉成员列表 + 当前成员
    await memberStore.load();
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final ts = t.textStyles;
    return Scaffold(
      backgroundColor: t.bg,
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const _LoginHero(),
            Padding(
              padding: const EdgeInsets.fromLTRB(AppTokens.sp24, 0, AppTokens.sp24, AppTokens.sp32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text('咕嘟小食单', textAlign: TextAlign.center, style: ts.h1),
                  const SizedBox(height: AppTokens.sp6),
                  Text('家里有什么，今晚就吃什么', textAlign: TextAlign.center, style: ts.body),
                  const SizedBox(height: AppTokens.sp32),
                  TextField(
                    controller: _username,
                    decoration: const InputDecoration(
                      labelText: '用户名',
                      prefixIcon: Icon(Icons.person_outline_rounded),
                    ),
                  ),
                  const SizedBox(height: AppTokens.sp12),
                  TextField(
                    controller: _password,
                    obscureText: true,
                    decoration: const InputDecoration(
                      labelText: '密码',
                      prefixIcon: Icon(Icons.lock_outline_rounded),
                    ),
                  ),
                  const SizedBox(height: AppTokens.sp16),
                  // 协议前置：未勾选不发起任何登录请求（原型 Login）
                  if (!_agreed)
                    Container(
                      margin: const EdgeInsets.only(bottom: AppTokens.sp8),
                      padding: const EdgeInsets.all(AppTokens.sp10),
                      decoration: BoxDecoration(
                        color: AppTokens.warningBg,
                        border: Border.all(color: t.primarySoft),
                        borderRadius: BorderRadius.circular(AppTokens.rMd),
                      ),
                      child: Text('请先阅读并勾选下方协议。勾选前不会发起任何登录。',
                          style: ts.caption.copyWith(color: AppTokens.warningText)),
                    ),
                  _agreement(t),
                  const SizedBox(height: AppTokens.sp16),
                  Consumer<AuthStore>(
                    builder: (_, auth, __) => SizedBox(
                      width: double.infinity,
                      height: 52,
                      child: ElevatedButton(
                        onPressed: auth.loading ? null : _login,
                        child: Center(
                          child: auth.loading
                              ? SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: t.card),
                                )
                              : const Text('登录'),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 协议勾选行：☐ 我已阅读并同意《用户协议》和《隐私政策》（书名号可点开全文）。
  Widget _agreement(AppTokens t) {
    final ts = t.textStyles;
    final link = ts.caption.copyWith(color: t.primaryDeep, fontWeight: FontWeight.w700);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(
          width: 32,
          height: 32,
          child: Checkbox(
            key: const Key('login-agree'),
            value: _agreed,
            activeColor: t.primaryDeep,
            side: BorderSide(color: t.body, width: 1.5),
            onChanged: (v) => setState(() => _agreed = v ?? false),
          ),
        ),
        Expanded(
          child: GestureDetector(
            onTap: () => setState(() => _agreed = !_agreed),
            child: Text.rich(
              TextSpan(
                style: ts.caption,
                children: [
                  const TextSpan(text: '我已阅读并同意'),
                  TextSpan(text: '《用户协议》', style: link, recognizer: _termsTap),
                  const TextSpan(text: '和'),
                  TextSpan(text: '《隐私政策》', style: link, recognizer: _privacyTap),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// 登录页头图：浅主色渐变 + 错落的食材插画环绕 App 图标（自绘插画，可离线、无版权问题）。
class _LoginHero extends StatelessWidget {
  const _LoginHero();

  static const _spots = [
    ('tomato', -0.36, 0.18, 54.0, -0.2),
    ('egg', 0.34, 0.12, 46.0, 0.3),
    ('broccoli', -0.28, 0.72, 50.0, 0.15),
    ('fish', 0.32, 0.70, 58.0, -0.25),
    ('shrimp', -0.06, 0.06, 40.0, 0.4),
    ('scallion', 0.06, 0.86, 44.0, -0.1),
  ];

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    final top = MediaQuery.of(context).padding.top;
    return Container(
      height: 280 + top,
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [t.primarySoft, t.bg],
        ),
      ),
      child: LayoutBuilder(builder: (_, c) {
        final w = c.maxWidth;
        const h = 240.0;
        return Stack(
          children: [
            for (final (key, dx, dy, size, rot) in _spots)
              Positioned(
                left: w / 2 + dx * w - size / 2,
                top: top + 16 + dy * h - size / 2,
                child: Transform.rotate(
                  angle: rot,
                  child: Container(
                    width: size + 16,
                    height: size + 16,
                    decoration: BoxDecoration(color: t.card, shape: BoxShape.circle, boxShadow: t.elevationSm),
                    alignment: Alignment.center,
                    child: Image.asset(IngredientIcons.asset(key), width: size, height: size),
                  ),
                ),
              ),
            Positioned(
              left: w / 2 - 48,
              top: top + 16 + h * 0.42 - 48,
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(AppTokens.rXl + 4),
                  boxShadow: t.elevationLg,
                ),
                child: Image.asset('assets/brand/logo.png', width: 96, height: 96),
              ),
            ),
          ],
        );
      }),
    );
  }
}
