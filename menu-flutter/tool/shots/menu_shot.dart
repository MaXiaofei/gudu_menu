import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/pages/menu/detail_page.dart';
import 'package:menu_flutter/widgets/segment_tabs.dart';

import '../../test/helpers/mock_http.dart';
import 'fixtures.dart';
import 'harness.dart';

Object _r(RequestOptionsLike o) => switch (o.path) {
      '/menu/5' => menuDetail,
      '/shopping/by-menu/5' => menuShop,
      '/menu/5/together-count' => 3,
      _ => {},
    };

typedef RequestOptionsLike = dynamic;

void main() {
  testWidgets('menu dishes', (tester) async {
    await shot(tester, 'menu-dishes', const MenuDetailPage(id: 5), responder: (o) => okResponse(_r(o)));
  });
  testWidgets('menu shop', (tester) async {
    await shot(tester, 'menu-shop', Builder(builder: (c) => const MenuDetailPage(id: 5)), responder: (o) => okResponse(_r(o)),
        before: (tester) async {
      await tester.tap(find.descendant(of: find.byType(SegmentTabs), matching: find.textContaining('采购')));
    });
  });
}
