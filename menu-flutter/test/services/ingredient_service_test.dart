import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/services/ingredient_service.dart';
import '../helpers/mock_http.dart';

/// IngredientService：字典/创建/全量列表/新增字典项 + DictItem 解析。
void main() {
  group('IngredientService.listDictByGroup', () {
    test('GET /dict?group=&pageNum=1&pageSize=1000 → List<DictItem>', () async {
      final captor = installMock((_) => okResponse({
            'records': [
              {'id': 1, 'name': '克'},
              {'id': 2, 'name': '个'},
            ],
          }));

      final list = await IngredientService.listDictByGroup('unit');

      expect(captor.last!.path, '/dict');
      expect(captor.last!.queryParameters['group'], 'unit');
      expect(captor.last!.queryParameters['pageSize'], 1000);
      expect(list.length, 2);
      expect(list[0].name, '克');
    });

    test('records 非 List → 空列表', () async {
      installMock((_) => okResponse(null));

      expect(await IngredientService.listDictByGroup('unit'), isEmpty);
    });
  });

  group('IngredientService.createIngredient', () {
    test('POST /ingredient body 透传，返回 id', () async {
      final captor = installMock((_) => okResponse(99));

      final id = await IngredientService.createIngredient({'name': '土豆'});

      expect(captor.last!.path, '/ingredient');
      expect(captor.last!.data, {'name': '土豆'});
      expect(id, 99);
    });
  });

  group('IngredientService.listAll', () {
    test('GET /ingredient?pageSize=1000 → List<DictItem>', () async {
      final captor = installMock((_) => okResponse({
            'records': [
              {'id': 10, 'name': '猪肉'},
            ],
          }));

      final list = await IngredientService.listAll();

      expect(captor.last!.path, '/ingredient');
      expect(list.length, 1);
      expect(list[0].id, 10);
      expect(list[0].name, '猪肉');
    });

    test('非 IPage 结构 → 空列表', () async {
      installMock((_) => okResponse('不是分页'));

      expect(await IngredientService.listAll(), isEmpty);
    });
  });

  group('IngredientService.upsertDict', () {
    test('POST /dict body 含 name + dictGroup，返回 id', () async {
      final captor = installMock((_) => okResponse(5));

      final id = await IngredientService.upsertDict('份', 'unit');

      expect(captor.last!.path, '/dict');
      expect(captor.last!.data, {'name': '份', 'dictGroup': 'unit'});
      expect(id, 5);
    });
  });

  group('DictItem.fromJson', () {
    test('完整字段', () {
      final d = DictItem.fromJson({'id': 8, 'name': '勺'});
      expect(d.id, 8);
      expect(d.name, '勺');
    });

    test('name 缺省 → 空串', () {
      final d = DictItem.fromJson({'id': 8});
      expect(d.name, '');
    });
  });

  group('parseAmountText（用量自由文本，§16.3）', () {
    const units = [
      DictItem(id: 20, name: 'g'),
      DictItem(id: 21, name: 'ml'),
      DictItem(id: 22, name: '个'),
      DictItem(id: 23, name: '把'),
    ];

    test('数字 + 单位', () {
      expect(parseAmountText('500 g', units), (500.0, 20));
      expect(parseAmountText('2 个', units), (2.0, 22));
      expect(parseAmountText('1把', units), (1.0, 23));
      expect(parseAmountText('15 ml', units), (15.0, 21));
    });

    test('纯数字 → 默认 g', () {
      expect(parseAmountText('300', units), (300.0, 20));
    });

    test('适量/少许/一小把 = 字典量词单位（V46）→ (null, 量词id)', () {
      const withFuzzy = [
        ...units,
        DictItem(id: 30, name: '适量'),
        DictItem(id: 31, name: '少许'),
        DictItem(id: 32, name: '一小把'),
      ];
      expect(parseAmountText('适量', withFuzzy), (null, 30));
      expect(parseAmountText('少许', withFuzzy), (null, 31));
      expect(parseAmountText('一小把', withFuzzy), (null, 32));
      // 字典里没有量词 → 仍回落 (null, null)
      expect(parseAmountText('适量', units), (null, null));
    });

    test('字典外的自由文本 → (null, null)', () {
      expect(parseAmountText('一小把', units), (null, null));
      expect(parseAmountText('半勺', units), (null, null));
      expect(parseAmountText('', units), (null, null));
    });

    test('字典外的单位 → 记 amount 不记 unitId', () {
      // 单位字典只有 g/ml/个/把，「斤」不匹配
      expect(parseAmountText('1 斤', units), (1.0, null));
      expect(parseAmountText('1 块', units), (1.0, null));
    });

    test('单位字典为空 → 记 amount 不记 unitId', () {
      expect(parseAmountText('500 g', const []), (500.0, null));
      expect(parseAmountText('2', const []), (2.0, null));
      expect(parseAmountText('适量', const []), (null, null));
    });
  });
}
