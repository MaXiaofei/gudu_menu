import 'package:flutter_test/flutter_test.dart';

import 'package:menu_flutter/services/shopping_service.dart';
import '../helpers/mock_http.dart';

/// ShoppingService：列表/详情/生成(多种来源)/切换/更新/增删 + VO 逻辑。
void main() {
  group('ShoppingService.detail', () {
    test('GET /shopping/{id} → 解析 items + grouped + categoryNames', () async {
      final captor = installMock((_) => okResponse({
            'id': 1,
            'timeRange': 'plan',
            'items': [
              {'id': 10, 'ingredientName': '鸡蛋', 'purchaseAmount': 10, 'purchaseUnitName': '个', 'purchased': 0},
            ],
            'grouped': {
              '1': [
                {'id': 10, 'ingredientName': '鸡蛋', 'purchaseAmount': 10, 'purchased': 0},
              ],
            },
            'categoryNames': {'1': '蛋类'},
          }));

      final vo = await ShoppingService.detail(1);

      expect(captor.last!.path, '/shopping/1');
      expect(vo.id, 1);
      expect(vo.items.length, 1);
      expect(vo.grouped['1']!.length, 1);
      expect(vo.categoryNames['1'], '蛋类');
    });
  });

  group('ShoppingService.generate / generateFrom / generateFromText', () {
    test('generate POST /shopping/generate 返回 id', () async {
      final captor = installMock((_) => okResponse(5));

      final id = await ShoppingService.generate({'sourceType': 'custom', 'customText': '鸡蛋 2斤'});

      expect(captor.last!.path, '/shopping/generate');
      expect(id, 5);
    });

    test('generateFrom(menu) → body 含 sourceType=menu + sourceId', () async {
      final captor = installMock((_) => okResponse(1));

      await ShoppingService.generateFrom('menu', sourceId: 3);

      expect(captor.last!.data, {'sourceType': 'menu', 'sourceId': 3});
    });

    test('generateFrom(dish) → sourceIds 列表', () async {
      final captor = installMock((_) => okResponse(1));

      await ShoppingService.generateFrom('dish', sourceIds: [1, 2, 3]);

      expect(captor.last!.data, {'sourceType': 'dish', 'sourceIds': [1, 2, 3]});
    });

    test('generateFromText → sourceType=custom + customText', () async {
      final captor = installMock((_) => okResponse(1));

      await ShoppingService.generateFromText('西红柿 土豆');

      expect(captor.last!.data, {'sourceType': 'custom', 'customText': '西红柿 土豆'});
    });
  });

  group('ShoppingService.createEmpty', () {
    test('POST /shopping/create 返回 id', () async {
      final captor = installMock((_) => okResponse(9));

      expect(await ShoppingService.createEmpty(), 9);
      expect(captor.last!.path, '/shopping/create');
    });
  });

  group('ShoppingService 增删改', () {
    test('togglePurchased PUT /shopping/item/{id}/purchased', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.togglePurchased(20);

      expect(captor.last!.method, 'PUT');
      expect(captor.last!.path, '/shopping/item/20/purchased');
    });

    test('updatePurchase body 含 purchaseAmount + 可选 purchaseUnitId', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.updatePurchase(20, 2.5, 3);

      expect(captor.last!.path, '/shopping/item/20');
      expect(captor.last!.data, {'purchaseAmount': 2.5, 'purchaseUnitId': 3});
    });

    test('updatePurchase unitId=null → body 不含 purchaseUnitId', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.updatePurchase(20, 2.5, null);

      expect(captor.last!.data, {'purchaseAmount': 2.5});
    });

    test('addCustomItem 返回 id + body 含 listId/name', () async {
      final captor = installMock((_) => okResponse(30));

      final id = await ShoppingService.addCustomItem(1, '酱油', amount: 1, unitId: 2);

      expect(captor.last!.path, '/shopping/item/custom');
      expect(captor.last!.data['listId'], 1);
      expect(captor.last!.data['name'], '酱油');
      expect(captor.last!.data['amount'], 1);
      expect(id, 30);
    });

    test('deleteItem DELETE /shopping/item/{id}', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.deleteItem(40);

      expect(captor.last!.method, 'DELETE');
      expect(captor.last!.path, '/shopping/item/40');
    });

    test('deleteList DELETE /shopping/{id}', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.deleteList(5);

      expect(captor.last!.path, '/shopping/5');
    });
  });

  group('ShoppingService restock / undoRestock / renameList / fromPrep（V42）', () {
    test('togglePurchased 带 level → body 含 level', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.togglePurchased(20, level: 'LOW');

      expect(captor.last!.path, '/shopping/item/20/purchased');
      expect(captor.last!.data, {'level': 'LOW'});
    });

    test('togglePurchased 不带 level → body 为空', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.togglePurchased(20);

      expect(captor.last!.path, '/shopping/item/20/purchased');
      expect(captor.last!.data, isEmpty);
    });

    test('restock POST /shopping/restock → RestockResult 解析', () async {
      final captor = installMock((_) => okResponse({'restocked': 3, 'markedOnly': 1}));

      final r = await ShoppingService.restock([1, 2, 3, 4]);

      expect(captor.last!.path, '/shopping/restock');
      expect(captor.last!.data, {'itemIds': [1, 2, 3, 4]});
      expect(r.restocked, 3);
      expect(r.markedOnly, 1);
    });

    test('undoRestock POST /shopping/item/{id}/undo-restock', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.undoRestock(9);

      expect(captor.last!.method, 'POST');
      expect(captor.last!.path, '/shopping/item/9/undo-restock');
    });

    test('renameList PUT /shopping/{id}/name body 含 name', () async {
      final captor = installMock((_) => okResponse(null));

      await ShoppingService.renameList(5, '周末采购');

      expect(captor.last!.method, 'PUT');
      expect(captor.last!.path, '/shopping/5/name');
      expect(captor.last!.data, {'name': '周末采购'});
    });

    test('fromPrep POST /shopping/from-prep body 含 menuId + ingredientIds', () async {
      final captor = installMock((_) => okResponse(7));

      final listId = await ShoppingService.fromPrep(3, [10, 11]);

      expect(captor.last!.path, '/shopping/from-prep');
      expect(captor.last!.data, {'menuId': 3, 'ingredientIds': [10, 11]});
      expect(listId, 7);
    });
  });

  group('ShoppingList.sourceLabel', () {
    test('各 timeRange 映射', () {
      expect(ShoppingList.fromJson({'id': 1, 'timeRange': 'menu'}).sourceLabel, '菜单');
      expect(ShoppingList.fromJson({'id': 1, 'timeRange': 'dish'}).sourceLabel, '菜品');
      expect(ShoppingList.fromJson({'id': 1, 'timeRange': 'plan'}).sourceLabel, '周计划');
      expect(ShoppingList.fromJson({'id': 1, 'timeRange': 'custom'}).sourceLabel, '自定义');
      expect(ShoppingList.fromJson({'id': 1, 'timeRange': 'custom_text'}).sourceLabel, '文本录入');
    });

    test('未知 timeRange → 原样返回', () {
      expect(ShoppingList.fromJson({'id': 1, 'timeRange': 'unknown'}).sourceLabel, 'unknown');
    });

    test('timeRange 为 null → 空串', () {
      expect(ShoppingList.fromJson({'id': 1}).sourceLabel, '');
    });
  });

  group('ShoppingList.dateRange', () {
    test('起止齐全 → "start ~ end"', () {
      final s = ShoppingList.fromJson({
        'id': 1, 'startDate': '2026-06-30', 'endDate': '2026-07-06',
      });
      expect(s.dateRange, '2026-06-30 ~ 2026-07-06');
    });

    test('缺一端 → 空串', () {
      expect(ShoppingList.fromJson({'id': 1, 'startDate': '2026-06-30'}).dateRange, '');
    });
  });

  group('ShoppingListVO.sourceLabel', () {
    test('timeRange 为 null → 兜底"采购单"', () {
      final vo = ShoppingListVO.fromJson({'id': 1});
      expect(vo.sourceLabel, '采购单');
    });
  });

  group('ShoppingItemVO', () {
    test('displayName: ingredientName 优先', () {
      final v = ShoppingItemVO.fromJson({'id': 1, 'ingredientName': '鸡蛋', 'customName': 'X'});
      expect(v.displayName, '鸡蛋');
    });

    test('displayName: 无 ingredientName 回退 customName', () {
      final v = ShoppingItemVO.fromJson({'id': 1, 'customName': '自制酱'});
      expect(v.displayName, '自制酱');
    });

    test('displayName: 都没有 → #ingredientId', () {
      final v = ShoppingItemVO.fromJson({'id': 1, 'ingredientId': 7});
      expect(v.displayName, '#7');
    });

    test('isPurchased: purchased==1 → true', () {
      expect(
        ShoppingItemVO.fromJson({'id': 1, 'purchased': 1}).isPurchased,
        isTrue,
      );
    });

    test('isPurchased: purchased 缺省 → false', () {
      expect(
        ShoppingItemVO.fromJson({'id': 1}).isPurchased,
        isFalse,
      );
    });

    test('amountText: 有 purchaseAmount → "N 单位"', () {
      final v = ShoppingItemVO.fromJson({
        'id': 1, 'purchaseAmount': 2.5, 'purchaseUnitName': '斤',
      });
      expect(v.amountText, '2.5 斤');
    });

    test('amountText: purchaseAmount 整数 → 无小数', () {
      final v = ShoppingItemVO.fromJson({
        'id': 1, 'purchaseAmount': 3, 'purchaseUnitName': '个',
      });
      expect(v.amountText, '3 个');
    });

    test('amountText: 都没有 → 空串（V55 referenceGrams 已删）', () {
      expect(ShoppingItemVO.fromJson({'id': 1}).amountText, '');
    });
  });
}
