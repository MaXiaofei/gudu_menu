import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/core/constants.dart';
import 'package:menu_flutter/services/together_service.dart';
import '../helpers/mock_http.dart';

/// TogetherService：邀请/清单/加菜/删菜 + URL 构造 + 模型解析。
void main() {
  group('TogetherService.invite', () {
    test('POST /menu/{id}/invite → TogetherInvite（code + token）', () async {
      final captor = installMock(
          (_) => okResponse({'code': 'AB12', 'token': 'tok-abc'}));

      final invite = await TogetherService.invite(5);

      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/menu/5/invite');
      expect(invite.code, 'AB12');
      expect(invite.token, 'tok-abc');
    });
  });

  group('TogetherService.together', () {
    test('GET /menu/{id}/together → TogetherVO（members + dishes + activities + invite）',
        () async {
      final captor = installMock((_) => okResponse({
            'members': [
              {
                'memberId': 1,
                'nickname': '小明',
                'lastActiveAt': '2026-08-10 10:00',
              },
            ],
            'dishes': [
              {
                'id': 100,
                'dishId': 10,
                'dishName': '番茄炒蛋',
                'note': '少油',
                'addedByNickname': '小明',
              },
            ],
            'activities': [
              {
                'nickname': '小明',
                'action': 'add',
                'dishName': '番茄炒蛋',
                'createTime': '2026-08-10 10:00',
              },
            ],
            'invite': {'code': 'AB12', 'token': 'tok-abc'},
          }));

      final vo = await TogetherService.together(5);

      expect(captor.last!.method, 'GET');
      expect(captor.last!.path, '/menu/5/together');
      expect(vo.members.length, 1);
      expect(vo.members.first.nickname, '小明');
      expect(vo.dishes.length, 1);
      expect(vo.dishes.first.dishName, '番茄炒蛋');
      expect(vo.dishes.first.note, '少油');
      expect(vo.activities.length, 1);
      expect(vo.activities.first.action, 'add');
      expect(vo.invite, isNotNull);
      expect(vo.invite!.token, 'tok-abc');
    });

    test('数组为 null/缺失 → 兜底空列表；invite 缺失 → null', () async {
      installMock((_) => okResponse({}));

      final vo = await TogetherService.together(5);

      expect(vo.members, isEmpty);
      expect(vo.dishes, isEmpty);
      expect(vo.activities, isEmpty);
      expect(vo.invite, isNull);
    });
  });

  group('TogetherService.addItem', () {
    test('dishId → body 含 dishId', () async {
      final captor = installMock((_) => okResponse(null));

      await TogetherService.addItem(5, dishId: 10);

      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/menu/5/together/items');
      expect(captor.last!.data, {'dishId': 10});
    });

    test('customName + note → body 含两者', () async {
      final captor = installMock((_) => okResponse(null));

      await TogetherService.addItem(5, customName: '可乐', note: '冰的');

      expect(captor.last!.data, {'customName': '可乐', 'note': '冰的'});
    });

    test('note 为空串 → body 不含 note 键', () async {
      final captor = installMock((_) => okResponse(null));

      await TogetherService.addItem(5, dishId: 10, note: '');

      expect(captor.last!.data, {'dishId': 10});
    });
  });

  group('TogetherService.removeItem', () {
    test('DELETE /menu/{id}/together/items/{menuDishId}', () async {
      final captor = installMock((_) => okResponse(null));

      await TogetherService.removeItem(5, 9);

      expect(captor.last!.method, 'DELETE');
      expect(captor.last!.path, '/menu/5/together/items/9');
    });
  });

  group('TogetherInvite.url / entryUrl', () {
    test('url 带 token 参数', () {
      final invite = TogetherInvite(code: 'x', token: 'abc');
      expect(invite.url, '${AppConstants.baseUrl}/together.html?token=abc');
    });

    test('entryUrl 不带 token', () {
      final invite = TogetherInvite(code: 'x', token: 'abc');
      expect(invite.entryUrl, '${AppConstants.baseUrl}/together.html');
    });
  });

  group('TogetherInvite.fromJson', () {
    test('解析 code + token', () {
      final invite =
          TogetherInvite.fromJson({'code': 'Z9', 'token': 't-123'});
      expect(invite.code, 'Z9');
      expect(invite.token, 't-123');
    });
  });

  group('TogetherVO.fromJson 兜底', () {
    test('null/缺失数组 → 空列表；invite 缺失 → null', () {
      final vo = TogetherVO.fromJson({});
      expect(vo.members, isEmpty);
      expect(vo.dishes, isEmpty);
      expect(vo.activities, isEmpty);
      expect(vo.invite, isNull);
    });

    test('invite 显式 null → null', () {
      final vo = TogetherVO.fromJson({'invite': null});
      expect(vo.invite, isNull);
    });
  });
}
