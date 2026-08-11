import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/services/upload_service.dart';

/// UploadService：uploadOne/compress 依赖平台通道（FlutterImageCompress + 文件 IO），
/// 无法在纯单元测试环境运行，此处不伪造平台通道。
/// 仅覆盖可测部分——UploadResult 值对象构造。
void main() {
  group('UploadResult', () {
    test('可构造并持有 url / thumbnailUrl / name 三个字段', () {
      const r = UploadResult(
        url: '/gudu/uploads/original/123.jpg',
        thumbnailUrl: '/gudu/uploads/thumbnail/123.jpg',
        name: 'photo.jpg',
      );

      expect(r.url, '/gudu/uploads/original/123.jpg');
      expect(r.thumbnailUrl, '/gudu/uploads/thumbnail/123.jpg');
      expect(r.name, 'photo.jpg');
    });

    test('空串字段可构造', () {
      const r = UploadResult(url: '', thumbnailUrl: '', name: '');

      expect(r.url, '');
      expect(r.thumbnailUrl, '');
      expect(r.name, '');
    });

    test('thumbnailUrl 可与 url 不同', () {
      const r = UploadResult(
        url: 'http://x/a.jpg',
        thumbnailUrl: 'http://x/a_thumb.jpg',
        name: 'a.jpg',
      );

      expect(r.thumbnailUrl, isNot(r.url));
    });

    test('为 const 构造函数（编译期常量）', () {
      // 能写成 const 字面量即证明是 const 构造函数
      const r = UploadResult(url: 'u', thumbnailUrl: 't', name: 'n');
      expect(r.url, 'u');
    });
  });
}
