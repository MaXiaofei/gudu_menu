// P3-9 扩展：登录流 + 核心页签导航 integration test。
//
// 与 smoke（启动→登录页渲染）互补，覆盖真实登录交互与底部导航：
//   登录页填表（预填 admin/admin123）→ 点登录 → 进首页（菜谱列表）
//   → 底部 tab 依次切换（食集/库存/我的）→ 断言各页关键元素。
//
// 只读导航（不写业务数据），可重复跑；若上次登录态残留（SharedPreferences
// 有 token），APP 直达首页，测试自适应跳过登录步骤直接断言首页。

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

import 'package:menu_flutter/main.dart' as app;

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('登录流 + 底部页签导航（食集/库存/我的）', (tester) async {
    await app.main();
    await tester.pumpAndSettle(const Duration(seconds: 4));

    // 若停在登录页 → 填密码（账号 admin 已预填）→ 点「登录」
    final loginBtn = find.text('登录');
    if (loginBtn.evaluate().isNotEmpty) {
      // 密码框：登录页第二个输入框（第一个是账号，已预填 admin）
      final fields = find.byType(TextField);
      if (fields.evaluate().length >= 2) {
        await tester.enterText(fields.at(1), 'admin123');
        await tester.pump();
      }
      await tester.ensureVisible(loginBtn.first);
      await tester.tap(loginBtn.first);
      await tester.pump();
    }

    // 真实 HTTP 登录 + 首页数据加载有网络延迟：轮询等待「菜谱」tab 出现（最多 15s）
    var homeReady = false;
    for (var i = 0; i < 15; i++) {
      if (find.text('菜谱').evaluate().isNotEmpty) {
        homeReady = true;
        break;
      }
      await tester.pump(const Duration(seconds: 1));
    }
    expect(homeReady, isTrue, reason: '登录后 15s 内应进入首页（菜谱 tab）');
    expect(find.text('食集'), findsWidgets, reason: '应有「食集」tab');
    expect(find.text('库存'), findsWidgets, reason: '应有「库存」tab');
    expect(find.text('我的'), findsWidgets, reason: '应有「我的」tab');

    // 切「食集」tab
    await tester.tap(find.text('食集').first);
    await tester.pumpAndSettle(const Duration(seconds: 3));
    expect(find.text('新建食集'), findsWidgets, reason: '食集页应有「新建食集」按钮');

    // 切「库存」tab
    await tester.tap(find.text('库存').first);
    await tester.pumpAndSettle(const Duration(seconds: 3));
    // 库存页头部（档位制：充足/不足/用完 分组标题或空态）
    final hasStockUi =
        find.textContaining(RegExp('充足|不足|用完|暂无')).evaluate().isNotEmpty;
    expect(hasStockUi, isTrue, reason: '库存页应显示档位分组或空态');

    // 切「我的」tab：食材库入口必现
    await tester.tap(find.text('我的').first);
    await tester.pumpAndSettle(const Duration(seconds: 3));
    expect(find.text('食材库'), findsOneWidget, reason: '「我的」页应有食材库入口');

    // 进食材库列表（V55 去单位核心页）
    await tester.tap(find.text('食材库'));
    await tester.pumpAndSettle(const Duration(seconds: 4));
    // 底部注是 V55 新文案；搜索框必现
    final hasV55Footer = find
        .text('食材从「我的」进入；点击食材可编辑食用属性')
        .evaluate()
        .isNotEmpty;
    expect(hasV55Footer, isTrue, reason: '食材库应显示 V55 新底部注文案');
  });
}
