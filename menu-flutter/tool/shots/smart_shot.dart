import 'package:flutter/material.dart' show Size;
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:menu_flutter/pages/ai/recommend_page.dart';

import '../../test/helpers/mock_http.dart';
import 'fixtures.dart';
import 'harness.dart';
import 'list_shot.dart' show demoMembers;

void main() {
  for (final skin in ['cream', 'matcha']) {
    testWidgets('smart $skin', (tester) async {
      await shot(tester, 'smart', const AiRecommendPage(), skin: skin,
          providers: [ChangeNotifierProvider.value(value: demoMembers())],
          size: const Size(390, 1500),
          responder: (o) => switch (o.path) {
                '/recommend/home' => okResponse(recommendHome),
                '/cookbook/recent' => okResponse(recentCooked),
                _ => okResponse([]),
              });
    });
  }
}
