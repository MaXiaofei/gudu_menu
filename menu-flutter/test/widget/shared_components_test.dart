import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/core/connectivity.dart';
import 'package:menu_flutter/core/error_text.dart';
import 'package:menu_flutter/models/dish.dart';
import 'package:menu_flutter/widgets/dish_image.dart';
import 'package:menu_flutter/widgets/error_view.dart';
import 'package:menu_flutter/widgets/hero_gallery.dart';
import 'package:menu_flutter/widgets/ingredient_icon.dart';
import 'package:menu_flutter/widgets/member_avatar.dart';
import 'package:menu_flutter/widgets/offline_banner.dart';
import 'package:menu_flutter/widgets/segment_tabs.dart';
import 'package:menu_flutter/widgets/stat_grid.dart';
import 'package:menu_flutter/widgets/step_carousel.dart';
import 'package:menu_flutter/widgets/tone_badge.dart';

/// prototype-ui-rollout 0.4：共享组件 × 两套皮肤。
void main() {
  for (final skin in [('cream', AppTokens.cream), ('matcha', AppTokens.matcha)]) {
    Widget host(Widget child) => MaterialApp(
          theme: buildBrandTheme(skin.$2),
          home: Scaffold(body: SingleChildScrollView(child: child)),
        );

    group('共享组件 · ${skin.$1}', () {
      testWidgets('DishImage：无 url → 首字占位，不留白', (tester) async {
        await tester.pumpWidget(host(const DishImage.square(url: null, name: '清蒸鲈鱼', size: 80)));
        expect(find.text('清'), findsOneWidget);
        expect(find.byType(Image), findsNothing);
      });

      testWidgets('DishImage：有 url → 走网络图（加载失败回退首字）', (tester) async {
        await tester.pumpWidget(host(const DishImage.square(url: '/gudu/uploads/original/1.jpg', name: '红烧肉', size: 80)));
        expect(find.byType(Image), findsOneWidget);
        final img = tester.widget<Image>(find.byType(Image));
        expect((img.image as NetworkImage).url, contains('/thumbnail/1.jpg'));
      });

      testWidgets('IngredientIcon：iconKey/名称命中插画，未命中退首字', (tester) async {
        await tester.pumpWidget(host(const Column(children: [
          IngredientIcon(name: '鲈鱼'),
          IngredientIcon(name: '秋葵', category: '蔬菜'),
          IngredientIcon(name: '藜麦'),
        ])));
        final assets = tester
            .widgetList<Image>(find.byType(Image))
            .map((i) => (i.image as AssetImage).assetName)
            .toList();
        expect(assets, ['assets/ing/fish.png', 'assets/ing/cabbage.png']);
        expect(find.text('藜'), findsOneWidget);
      });

      testWidgets('AvatarStack：超过 max 显示 +N；宝宝档案用宝宝插画', (tester) async {
        await tester.pumpWidget(host(AvatarStack(max: 2, people: const [
          (name: '小王', avatarUrl: null),
          (name: '林林', avatarUrl: null),
          (name: '妈妈', avatarUrl: null),
          (name: '朵朵', avatarUrl: null),
        ])));
        expect(find.text('+2'), findsOneWidget);
        expect(MemberAvatar.defaultKey('任意', baby: true), 'duoduo');
        expect(MemberAvatar.defaultKey('林林'), 'linlin');
      });

      testWidgets('ToneBadge：覆盖率三档 + 到期徽章', (tester) async {
        await tester.pumpWidget(host(Column(children: [
          ToneBadge.coverage(const Coverage(enough: 3, total: 3))!,
          ToneBadge.coverage(const Coverage(enough: 4, total: 5, missingNames: ['小葱']))!,
          ToneBadge.coverage(const Coverage(enough: 1, total: 5))!,
          ToneBadge.expiry(1)!,
          ToneBadge.expiry(2)!,
        ])));
        expect(find.text('家里够 3/3 样'), findsOneWidget);
        expect(find.text('家里够 4/5 样 · 缺小葱'), findsOneWidget);
        expect(find.text('家里够 1/5 样'), findsOneWidget);
        expect(find.text('明天到期'), findsOneWidget);
        expect(find.text('剩 2 天'), findsOneWidget);
        expect(ToneBadge.coverage(const Coverage(enough: 0, total: 0)), isNull);
        expect(ToneBadge.expiry(10), isNull);
      });

      testWidgets('SegmentTabs / StatGrid：点击回调', (tester) async {
        var idx = 0;
        var tapped = false;
        await tester.pumpWidget(host(StatefulBuilder(
          builder: (_, set) => Column(children: [
            SegmentTabs(labels: const ['菜', '备菜', '采购', '一起吃'], index: idx, onChanged: (i) => set(() => idx = i)),
            StatGrid(items: [StatItem('24', '充足', tone: StatTone.success, onTap: () => tapped = true)]),
          ]),
        )));
        await tester.tap(find.text('采购'));
        await tester.pump();
        expect(idx, 2);
        await tester.tap(find.text('24'));
        expect(tapped, isTrue);
      });

      testWidgets('HeroGallery：多图显示页码；StepCarousel：STEP n', (tester) async {
        await tester.pumpWidget(host(Column(children: [
          const HeroGallery(urls: ['/a.jpg', '/b.jpg', '/c.jpg'], name: '可乐鸡翅', height: 200),
          StepCarousel(dishName: '可乐鸡翅', steps: const [(text: '鸡翅焯水', image: null), (text: '加可乐收汁', image: null)]),
        ])));
        expect(find.text('1 / 3'), findsOneWidget);
        expect(find.text('STEP 1'), findsOneWidget);
        expect(find.text('加可乐收汁'), findsOneWidget);
      });

      testWidgets('ErrorView：带报错编号（X-Trace-Id 前 8 位）', (tester) async {
        final e = DioException(requestOptions: RequestOptions(path: '/x', extra: {'traceId': '7f3a91c2deadbeef'}));
        expect(errorIdOf(e), '7f3a-91c2');
        await tester.pumpWidget(host(ErrorView(text: '没加载出来', error: e, onRetry: () {})));
        expect(find.text('没加载出来'), findsOneWidget);
        expect(find.text('重试'), findsOneWidget);
        expect(find.textContaining('7f3a-91c2'), findsOneWidget);
      });

      testWidgets('OfflineBanner：离线显示、恢复隐藏', (tester) async {
        Connectivity.online.value = true;
        await tester.pumpWidget(host(const OfflineBanner()));
        expect(find.textContaining('已离线'), findsNothing);
        Connectivity.online.value = false;
        await tester.pump();
        expect(find.textContaining('已离线'), findsOneWidget);
        Connectivity.online.value = true;
        await tester.pump();
        expect(find.textContaining('已离线'), findsNothing);
      });

      testWidgets('DishCollage / DishCircleStack：渲染 N 张', (tester) async {
        await tester.pumpWidget(host(const Column(children: [
          SizedBox(width: 300, child: DishCollage(urls: [null, null, null], names: ['红', '绿', '蓝'])),
          DishCircleStack(urls: [null, null, null, null], names: ['甲', '乙', '丙', '丁'], max: 3),
        ])));
        expect(find.text('红'), findsOneWidget);
        expect(find.text('丁'), findsNothing); // 超出 max 不显示
      });
    });
  }
}
