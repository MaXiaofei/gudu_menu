import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseAmount, toGrams, cnNumber, nameCandidates, resolveIngredient, buildIndex, isWater, isHeading, inferEffort } from './lib/parse.mjs';
import { assertTarget } from './lib/db.mjs';

const UNITS = new Set(['克', 'g', '小匙', '大匙', '根', '个', '汤匙', '适量', '少许', '片', '千克', 'ml', '毫升']);

test('分数与区间', () => {
  assert.deepEqual(parseAmount('1/2小匙', UNITS), { amount: 0.5, unit: '小匙' });
  assert.deepEqual(parseAmount('2-3根', UNITS), { amount: 3, unit: '根' });
  assert.deepEqual(parseAmount('2~3 根', UNITS), { amount: 3, unit: '根' });
  assert.deepEqual(parseAmount('1/4小匙', UNITS), { amount: 0.25, unit: '小匙' });
});

test('中文数字与「半」', () => {
  assert.equal(cnNumber('十二'), 12);
  assert.equal(cnNumber('二十'), 20);
  assert.deepEqual(parseAmount('三汤匙', UNITS), { amount: 3, unit: '汤匙' });
  assert.deepEqual(parseAmount('一汤匙半', UNITS), { amount: 1.5, unit: '汤匙' });
  assert.deepEqual(parseAmount('1个半', UNITS), { amount: 1.5, unit: '个' });
  assert.deepEqual(parseAmount('半个', UNITS), { amount: 0.5, unit: '个' });
  assert.deepEqual(parseAmount('两根', UNITS), { amount: 2, unit: '根' });
});

test('不定量与单位同义', () => {
  assert.deepEqual(parseAmount('适量'), { amount: 0, unit: '适量' });
  assert.deepEqual(parseAmount('少许'), { amount: 0, unit: '少许' });
  assert.deepEqual(parseAmount('一些'), { amount: 0, unit: '适量' });
  assert.deepEqual(parseAmount('1kg', UNITS), { amount: 1, unit: '千克' });
  assert.deepEqual(parseAmount('500g', UNITS), { amount: 500, unit: 'g' });
  assert.deepEqual(parseAmount('2根左右', UNITS), { amount: 2, unit: '根' });
  assert.deepEqual(parseAmount('', UNITS), { amount: null, unit: null });
  assert.deepEqual(parseAmount('看个人口味', UNITS), { amount: null, unit: null });
});

test('克换算', () => {
  assert.equal(toGrams(1, '千克'), 1000);
  assert.equal(toGrams(1, '斤'), 500);
  assert.equal(toGrams(2, '根'), null);
});

test('食材名归一与匹配', () => {
  const ings = [{ id: 1, name: '番茄' }, { id: 2, name: '马铃薯' }, { id: 3, name: '小葱' }, { id: 4, name: '芋头' },
    { id: 5, name: '牛肉(瘦)' }, { id: 6, name: '牛肉(肥瘦)' }, { id: 7, name: '大蒜' }, { id: 8, name: '鸡翅' }, { id: 9, name: '姜' }];
  const idx = buildIndex(ings, { 西红柿: '番茄', 土豆: '马铃薯', 葱: '小葱', 蒜: '大蒜', 牛肉: '牛肉(肥瘦)' });
  const r = (s, combos) => nameCandidates(s, combos).map((c) => resolveIngredient(c, idx)?.id ?? null);
  assert.deepEqual(r('西红柿'), [1]);
  assert.deepEqual(r('中等土豆'), [2]);
  assert.deepEqual(r('葱花'), [3]);
  assert.deepEqual(r('蒜末'), [7]);
  assert.deepEqual(r('槟樃芋头'), [4]);
  assert.deepEqual(r('牛肉'), [6]);
  assert.deepEqual(r('鸡翅中'), [8]);
  assert.deepEqual(r('姜片'), [9]);
  assert.deepEqual(r('葱姜', { 葱姜: ['葱', '姜'] }), [3, 9]);
  assert.deepEqual(r('香草精'), [null]);
});

test('调味酱/油不退化成原料；连写拆分；序号与小标题', () => {
  const ings = [{ id: 1, name: '红辣椒' }, { id: 2, name: '花椒' }, { id: 3, name: '辣椒油' }, { id: 4, name: '食盐' },
    { id: 5, name: '老抽' }, { id: 6, name: '料酒' }, { id: 7, name: '大豆油' }, { id: 8, name: '豆瓣酱' }];
  const idx = buildIndex(ings, { 盐: '食盐', 油: '大豆油' });
  const r = (s) => nameCandidates(s).map((c) => resolveIngredient(c, idx)?.id ?? null);
  assert.deepEqual(r('韩式辣椒酱'), [null]);
  assert.deepEqual(r('花椒油'), [null]);
  assert.deepEqual(r('郫县豆瓣酱'), [8]);
  assert.deepEqual(r('老抽，盐'), [5, 4]);
  assert.deepEqual(r('1.料酒'), [6]);
  assert.deepEqual(r('油面'), [null]);
  assert.ok(isHeading('腌制鸡肉材料:1～6'));
  assert.ok(!isHeading('鸡胸肉'));
});

test('水不计入用料', () => {
  assert.ok(isWater('清水'));
  assert.ok(isWater('高汤(清水)'));
  assert.ok(!isWater('水淀粉'));
});

test('难度/时长推断', () => {
  const easy = inferEffort(['虾洗净', '水开下虾煮 3 分钟'], 3);
  assert.equal(easy.difficulty, 1);
  assert.equal(easy.cookTime, 5);
  const hard = inferEffort(['面粉加酵母揉面', '发酵 1 小时', '擀成面皮', '烤箱 180 度烤 25 分钟', 'a', 'b', 'c'], 12);
  assert.ok(hard.difficulty >= 4);
  assert.equal(hard.prepTime, 60);
  assert.equal(hard.cookTime, 25);
});

test('环境守卫：拒绝生产端口与未知环境', () => {
  assert.throws(() => assertTarget({ env: 'prod' }), /只能是 staging 或 local/);
  assert.throws(() => assertTarget({ env: 'staging', remotePort: 3306, password: 'x' }), /生产库端口禁止写入/);
  assert.throws(() => assertTarget({ env: 'staging', remotePort: 13306, password: 'same', prodPassword: 'same' }), /与生产相同/);
  assert.throws(() => assertTarget({ env: 'staging', remotePort: 13306, password: '' }), /缺少/);
  assert.ok(assertTarget({ env: 'staging', remotePort: 13306, password: 'a', prodPassword: 'b' }));
  assert.ok(assertTarget({ env: 'local' }));
});
