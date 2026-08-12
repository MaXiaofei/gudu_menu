// P3-9: APP integration smoke test（启动级回归捕获）。
//
// 与 widget/unit 测试不同，integration_test 在真机/模拟器上跑完整 APP，
// 验证「启动 → 持久化恢复 → 登录页渲染」这条最基础的链路。
// 用法：连上模拟器/真机后 `flutter test integration_test/smoke_test.dart`。
//
// 设计为只读 smoke（不真的登录、不写数据），保证可重复跑、不污染 staging：
//   - 启动 APP（app.main()，含 ApiClient/AuthStore 初始化）
//   - 等待登录页渲染（未登录 → 跳登录页）
//   - 断言登录页关键元素可见
// 失败即说明启动级回归（入口崩溃、路由错配、持久化异常等）。

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:menu_flutter/main.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('启动 APP → 未登录进登录页（smoke）', (tester) async {
    // 启动完整 APP（含 ApiClient.init / AuthStore.init / ThemeController.load）
    await app.main();
    // 启动 + 持久化恢复 + 首帧：给足时间（不连网络也不卡——未登录直接进登录页）
    await tester.pumpAndSettle(const Duration(seconds: 3));

    // 登录页标题「小食单」必现（见 LoginPage：contentDescription "小食单"）
    expect(find.text('小食单'), findsWidgets,
        reason: '登录页应显示「小食单」标题');
    // 用户名 / 密码输入框必现
    expect(find.byType(TextField), findsWidgets,
        reason: '登录页应有用户名/密码输入框');
    // 登录按钮必现（ElevatedButton，登录页唯一的实底按钮）
    expect(find.byType(ElevatedButton), findsWidgets,
        reason: '登录页应有登录按钮');
  });
}
