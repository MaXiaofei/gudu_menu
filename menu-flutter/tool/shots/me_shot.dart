import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:menu_flutter/pages/profile_page.dart';
import 'package:menu_flutter/stores/auth_store.dart';

import '../../test/helpers/mock_http.dart';
import 'fixtures.dart';
import 'harness.dart';
import 'list_shot.dart' show demoMembers;

void main() {
  testWidgets('me', (tester) async {
    final ms = demoMembers()..currentId = 1;
    await shot(tester, 'me', const ProfilePage(),
        providers: [
          ChangeNotifierProvider.value(value: AuthStore()..nickname = '小王'),
          ChangeNotifierProvider.value(value: ms),
        ],
        responder: (o) => switch (o.path) {
              '/me/stats' => okResponse({'cookedThisMonth': 21, 'familyDishes': 32, 'savedFromWaste': 4}),
              '/cookbook/recent' => okResponse([
                  ...recentCooked,
                  {'dishId': 1, 'name': '滑蛋虾仁', 'coverUrl': img('dish/huadan-xiaren.jpg'), 'cookedCount': 12},
                  {'dishId': 2, 'name': '清蒸鲈鱼', 'coverUrl': img('dish/qingzheng-luyu.jpg'), 'cookedCount': 4},
                  {'dishId': 7, 'name': '麻婆豆腐', 'coverUrl': img('dish/mapo-doufu.jpg'), 'cookedCount': 3},
                ]),
              '/dish/draft/list' => okResponse({'records': [{'id': 1}, {'id': 2}], 'total': 2}),
              _ => okResponse(null),
            });
  });
}
