import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/app_theme.dart';
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
    return Scaffold(
        body: SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppTokens.sp24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const SizedBox(height: 48),
                Text(
                  '咕嘟小食单',
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
                  builder: (_, auth, __) => SizedBox(
                    width: double.infinity,
                    height: 48,
                    child: ElevatedButton(
                      onPressed: auth.loading ? null : _login,
                      // 文案居中（Center 兜底：任何主题/布局下均居中）
                      child: Center(
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
                  ),
                ),
              ],
            ),
          ),
        ),
      );
  }
}
