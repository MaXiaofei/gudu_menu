import 'package:flutter/material.dart' show Size;
import 'package:flutter_test/flutter_test.dart';
import 'package:menu_flutter/pages/pantry/list_page.dart';

import '../../test/helpers/mock_http.dart';
import 'fixtures.dart';
import 'harness.dart';

void main() {
  testWidgets('pantry', (tester) async {
    await shot(tester, 'pantry', const PantryListPage(),
        size: const Size(390, 1200),
        responder: (o) => okResponse(pantryResponder(o.path, o.queryParameters)));
  });
}
