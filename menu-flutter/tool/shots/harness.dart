// 视觉自查截图工具（非单测，不进 CI）：真实字体 + 本地原型图片 + mock 接口，
// 把页面渲染成 390×844 PNG，与 docs/design/prototype 对照。
//
// 跑法：flutter test tool/shots/<x>_shot.dart  → 输出到 $SHOT_OUT（默认 build/shots/）
import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';

import 'package:menu_flutter/core/app_theme.dart';
import 'package:menu_flutter/core/theme_controller.dart';

import '../../test/helpers/mock_http.dart';

const _proto = '../docs/design/prototype/img';

/// 原型图片 URL：`img('dish/huadan-xiaren.jpg')` → https://shot.local/img/dish/...
String img(String p) => 'https://shot.local/img/$p';

bool _fontsLoaded = false;
Future<void> loadShotFonts() async {
  if (_fontsLoaded) return;
  final sans = FontLoader('ShotSans')
    ..addFont(Future.value(ByteData.sublistView(
        File('/System/Library/Fonts/Supplemental/Arial Unicode.ttf').readAsBytesSync())));
  await sans.load();
  final icons = FontLoader('MaterialIcons')
    ..addFont(Future.value(ByteData.sublistView(File(
            '/opt/homebrew/share/flutter/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf')
        .readAsBytesSync())));
  await icons.load();
  _fontsLoaded = true;
}

/// 把 shot.local 的请求映射到本地原型图片文件。
class _ShotHttpOverrides extends HttpOverrides {
  @override
  HttpClient createHttpClient(SecurityContext? context) => _FakeClient();
}

class _FakeClient implements HttpClient {
  @override
  bool autoUncompress = true;
  @override
  Future<HttpClientRequest> getUrl(Uri url) async => _FakeRequest(url);
  @override
  Future<HttpClientRequest> openUrl(String method, Uri url) async => _FakeRequest(url);
  @override
  dynamic noSuchMethod(Invocation i) => null;
}

class _FakeRequest implements HttpClientRequest {
  _FakeRequest(this.uri);
  @override
  final Uri uri;
  @override
  final HttpHeaders headers = _NoHeaders();
  @override
  Future<HttpClientResponse> close() async {
    final path = uri.path.replaceFirst('/img/', '').replaceFirst('/thumbnail/', '/').replaceFirst('/original/', '/');
    final f = File('$_proto/$path');
    final bytes = f.existsSync() ? f.readAsBytesSync() : Uint8List(0);
    return _FakeResponse(bytes, f.existsSync() ? 200 : 404);
  }

  @override
  dynamic noSuchMethod(Invocation i) => null;
}

class _NoHeaders implements HttpHeaders {
  @override
  dynamic noSuchMethod(Invocation i) => null;
}

class _FakeResponse extends Stream<List<int>> implements HttpClientResponse {
  _FakeResponse(this.bytes, this.statusCode);
  final Uint8List bytes;
  @override
  final int statusCode;
  @override
  int get contentLength => bytes.length;
  @override
  HttpClientResponseCompressionState get compressionState => HttpClientResponseCompressionState.notCompressed;
  @override
  StreamSubscription<List<int>> listen(void Function(List<int>)? onData,
          {Function? onError, void Function()? onDone, bool? cancelOnError}) =>
      Stream<List<int>>.fromIterable([bytes])
          .listen(onData, onError: onError, onDone: onDone, cancelOnError: cancelOnError);
  @override
  dynamic noSuchMethod(Invocation i) => null;
}

/// 渲染 [page] 并截图到 `<out>/<name>.png`。[responder] 为接口 mock。
Future<void> shot(
  WidgetTester tester,
  String name,
  Widget page, {
  required MockResponder responder,
  String skin = 'cream',
  List<SingleChildWidget> providers = const [],
  Size size = const Size(390, 844),
  Future<void> Function(WidgetTester)? before,
}) async {
  await loadShotFonts();
  debugDisableShadows = false; // 测试默认把阴影画成黑边，截图要真实阴影
  installMock(responder);
  final tc = ThemeController(skin);
  final base = buildBrandTheme(tc.tokens);
  ButtonStyle? f(ButtonStyle? st) => st?.copyWith(
      textStyle: WidgetStatePropertyAll((st.textStyle?.resolve({}) ?? const TextStyle()).copyWith(fontFamily: 'ShotSans')));
  final theme = base.copyWith(
    textTheme: base.textTheme.apply(fontFamily: 'ShotSans'),
    elevatedButtonTheme: ElevatedButtonThemeData(style: f(base.elevatedButtonTheme.style)),
    outlinedButtonTheme: OutlinedButtonThemeData(style: f(base.outlinedButtonTheme.style)),
    textButtonTheme: TextButtonThemeData(style: f(base.textButtonTheme.style)),
    snackBarTheme: base.snackBarTheme.copyWith(contentTextStyle: base.snackBarTheme.contentTextStyle?.copyWith(fontFamily: 'ShotSans')),
  );
  tester.view.physicalSize = size * 2;
  tester.view.devicePixelRatio = 2;
  final key = GlobalKey();
  await HttpOverrides.runWithHttpOverrides(() async {
    await tester.pumpWidget(MultiProvider(
      providers: [ChangeNotifierProvider.value(value: tc), ...providers],
      child: RepaintBoundary(
        key: key,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: theme,
          home: DefaultTextStyle(
            style: const TextStyle(fontFamily: 'ShotSans'),
            child: MediaQuery(
              data: MediaQueryData(size: size, devicePixelRatio: 2, padding: const EdgeInsets.only(top: 44, bottom: 34)),
              child: page,
            ),
          ),
        ),
      ),
    ));
    Future<void> settle() async {
      for (var i = 0; i < 6; i++) {
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 120)));
        await tester.pump(const Duration(milliseconds: 300));
      }
    }

    await settle();
    if (before != null) {
      await before(tester);
      await settle();
    }
  }, _ShotHttpOverrides());
  final boundary = key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
  final out = Platform.environment['SHOT_OUT'] ?? 'build/shots';
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 2);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    Directory(out).createSync(recursive: true);
    File('$out/$name-$skin.png').writeAsBytesSync(data!.buffer.asUint8List());
  });
  debugDisableShadows = true;
  addTearDown(tester.view.reset);
}
