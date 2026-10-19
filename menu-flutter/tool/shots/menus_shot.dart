import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/pages/menu/list_page.dart';

import '../../test/helpers/mock_http.dart';
import 'fixtures.dart';
import 'harness.dart';

void main() {
  testWidgets('menus', (tester) async {
    await shot(tester, 'menus', const MenuListPage(),
        responder: (o) => o.path == '/menu' ? okResponse(menuList) : okResponse({}));
  });
}
