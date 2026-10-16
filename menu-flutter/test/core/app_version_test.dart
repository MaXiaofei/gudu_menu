import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/constants.dart';

/// 「关于」页展示的版本号常量须与 pubspec.yaml 的语义版本一致（未引入 package_info_plus）。
void main() {
  test('AppConstants.appVersion == pubspec version（去掉 +build）', () {
    final pubspec = File('pubspec.yaml').readAsStringSync();
    final m = RegExp(r'^version:\s*([0-9.]+)', multiLine: true).firstMatch(pubspec);
    expect(m, isNotNull);
    expect(AppConstants.appVersion, m!.group(1));
  });

  test('协议资源已同步到 assets/legal/', () {
    expect(File('assets/legal/terms.md').existsSync(), isTrue);
    expect(File('assets/legal/privacy.md').existsSync(), isTrue);
  });
}
