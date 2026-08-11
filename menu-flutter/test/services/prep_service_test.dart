import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/models/prep.dart';
import 'package:menu_flutter/services/prep_service.dart';
import '../helpers/mock_http.dart';

/// PrepService：备菜聚合 + 状态更新。
void main() {
  group('PrepService.getPrep', () {
    test('GET /menu/{id}/prep → 解析 items + condiments + progress', () async {
      final captor = installMock((_) => okResponse({
            'items': [
              {
                'ingredientId': 10,
                'ingredientName': '鸡蛋',
                'stockLevel': 'ENOUGH',
                'totalGrams': 200,
                'dishCount': 2,
                'dishNames': ['番茄炒蛋', '蛋花汤'],
                'status': 'READY',
                'shared': true,
              },
              {
                'ingredientId': 11,
                'ingredientName': '番茄',
                'totalGrams': 150,
                'dishCount': 1,
                'dishNames': ['番茄炒蛋'],
                'status': 'PENDING',
              },
            ],
            'condiments': [
              {
                'ingredientId': 20,
                'ingredientName': '盐',
                'totalGrams': 5,
                'dishCount': 3,
                'dishNames': ['番茄炒蛋', '蛋花汤', '红烧肉'],
                'status': 'READY',
              },
            ],
            'readyCount': 2,
            'totalCount': 3,
          }));

      final prep = await PrepService.getPrep(5);

      expect(captor.last!.method, 'GET');
      expect(captor.last!.path, '/menu/5/prep');
      expect(prep.items.length, 2);
      expect(prep.condiments.length, 1);
      expect(prep.items.first.ingredientName, '鸡蛋');
      expect(prep.items.first.status, PrepStatus.ready);
      expect(prep.items.first.shared, isTrue);
      expect(prep.items[1].status, PrepStatus.pending);
      expect(prep.condiments.first.ingredientName, '盐');
      expect(prep.readyCount, 2);
      expect(prep.totalCount, 3);
    });

    test('items/condiments 缺省 → 空列表 + 进度兜底 0', () async {
      installMock((_) => okResponse({}));

      final prep = await PrepService.getPrep(5);

      expect(prep.items, isEmpty);
      expect(prep.condiments, isEmpty);
      expect(prep.readyCount, 0);
      expect(prep.totalCount, 0);
    });
  });

  group('PrepService.updateStatus', () {
    test('PUT /menu/{id}/prep/{ingredientId}?status=READY', () async {
      final captor = installMock((_) => okResponse(null));

      await PrepService.updateStatus(5, 10, PrepStatus.ready);

      expect(captor.last!.method, 'PUT');
      expect(captor.last!.path, '/menu/5/prep/10');
      expect(captor.last!.queryParameters['status'], 'READY');
    });

    test('PUT status=PENDING（待备）', () async {
      final captor = installMock((_) => okResponse(null));

      await PrepService.updateStatus(5, 10, PrepStatus.pending);

      expect(captor.last!.path, '/menu/5/prep/10');
      expect(captor.last!.queryParameters['status'], 'PENDING');
    });

    test('PUT status=THAWING（化冻中）', () async {
      final captor = installMock((_) => okResponse(null));

      await PrepService.updateStatus(5, 10, PrepStatus.thawing);

      expect(captor.last!.queryParameters['status'], 'THAWING');
    });
  });
}
