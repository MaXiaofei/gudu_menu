import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
import '../stores/auth_store.dart';

/// 登录页（复刻 menu-mini/src/pages/login/Login.vue）。
/// 用户名+密码登录；成功后 AuthStore 变更触发 go_router redirect 到首页。
class LoginPage extends StatefulWidget {
  const LoginPage({super.key});
  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _username = TextEditingController(text: 'admin');
  final _password = TextEditingController();

  @override
  void dispose() {
    _username.dispose();
    _password.dispose();
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
    // 登录成功后 AuthStore 变更触发 redirect 到首页
    await context.read<AuthStore>().login(u, p);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppTokens.of(context);
    return Scaffold(
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppTokens.sp24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 48),
                Text(
                  '小食单',
                  textAlign: TextAlign.center,
                  style: t.textStyles.h1.copyWith(color: t.primary),
                ),
                const SizedBox(height: AppTokens.sp32),
                TextField(
                  controller: _username,
                  decoration: const InputDecoration(labelText: '用户名'),
                ),
                const SizedBox(height: AppTokens.sp16),
                TextField(
                  controller: _password,
                  obscureText: true,
                  decoration: const InputDecoration(labelText: '密码'),
                ),
                const SizedBox(height: AppTokens.sp24),
                Consumer<AuthStore>(
                  builder: (_, auth, __) => ElevatedButton(
                    onPressed: auth.loading ? null : _login,
                    child: auth.loading
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(
                                strokeWidth: 2, color: Colors.white),
                          )
                        : const Text('登录'),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
  }
}
