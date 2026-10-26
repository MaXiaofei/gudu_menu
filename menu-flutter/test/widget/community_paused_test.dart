import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/api_client.dart';
import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/core/community_status.dart';
import 'package:menu_flutter/widgets/community_paused_gate.dart';
import '../helpers/mock_http.dart';

/// mini-release-compliance A.12：厨房圈紧急关停——5031 显示暂停提示，不弹报错。
void main() {
  setUp(() => CommunityStatus.suspended.value = false);

  test('路径判断：只认用户侧 /community', () {
    expect(CommunityStatus.isCommunityPath('/community/feed'), isTrue);
    expect(CommunityStatus.isCommunityPath('/gudu/community/posts/1'), isTrue);
    expect(CommunityStatus.isCommunityPath('/admin/community/queue'), isFalse);
    expect(CommunityStatus.isCommunityPath('/communityx'), isFalse);
  });

  test('ApiClient 收到 5031：置暂停、不弹 toast、抛异常；恢复后成功请求解除', () async {
    String? toasted;
    var code = CommunityStatus.suspendedCode;
    installMock(
      (_) => code == 0 ? okResponse({'items': []}) : errResponse(code, '厨房圈暂停服务'),
      onErrorToast: (m) => toasted = m,
    );
    await expectLater(ApiClient.instance.get('/community/feed'), throwsA(isA<DioException>()));
    expect(CommunityStatus.suspended.value, isTrue);
    expect(toasted, isNull);

    code = 0;
    await ApiClient.instance.get('/dish/1');
    expect(CommunityStatus.suspended.value, isTrue, reason: '非厨房圈请求不影响');
    await ApiClient.instance.get('/community/feed');
    expect(CommunityStatus.suspended.value, isFalse);
  });

  testWidgets('暂停时页面换成「厨房圈暂停服务」，点「再看看」回到原页面', (tester) async {
    await tester.pumpWidget(MaterialApp(
      theme: buildBrandTheme(AppTokens.cream),
      home: const CommunityPausedGate(showBack: false, child: Text('厨房圈内容')),
    ));
    expect(find.text('厨房圈内容'), findsOneWidget);

    CommunityStatus.suspended.value = true;
    await tester.pump();
    expect(find.byKey(const Key('community-paused')), findsOneWidget);
    expect(find.text('厨房圈暂停服务'), findsOneWidget);
    expect(find.text('厨房圈内容'), findsNothing);

    await tester.tap(find.text('再看看'));
    await tester.pump();
    expect(find.text('厨房圈内容'), findsOneWidget);
  });
}
