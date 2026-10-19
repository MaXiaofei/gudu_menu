import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/pages/dish/detail_page.dart';

import '../../test/helpers/mock_http.dart';
import 'fixtures.dart';
import 'harness.dart';

void main() {
  for (final skin in ['cream', 'matcha']) {
    testWidgets('detail $skin', (tester) async {
      await shot(tester, 'detail', const DishDetailPage(id: 2), skin: skin, responder: (o) {
        if (o.path == '/dish/2') return okResponse(luyuDetail);
        return okResponse({});
      });
    });
  }
}
