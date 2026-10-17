import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:menu_flutter/models/member.dart';
import 'package:menu_flutter/pages/dish/list_page.dart';
import 'package:menu_flutter/stores/member_store.dart';

import '../../test/helpers/mock_http.dart';
import 'fixtures.dart';
import 'harness.dart';

MemberStore demoMembers() => MemberStore()..members = [for (final m in members) Member.fromJson(m)];

void main() {
  testWidgets('recipes', (tester) async {
    await shot(tester, 'recipes', const DishListPage(),
        providers: [ChangeNotifierProvider.value(value: demoMembers())],
        responder: (o) => o.path == '/dish/search' ? okResponse(recipeList) : okResponse([]));
  });
}
