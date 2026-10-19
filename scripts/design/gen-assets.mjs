#!/usr/bin/env node
// 原型自绘插画（食材 / 默认头像 / App 图标）→ 三端素材。
//
// 源：docs/design/prototype/img/{ing,avatar}/*.svg、img/extra/logo.svg
// 产物：
//   Flutter  menu-flutter/assets/{ing,avatar}/<key>.png（144px，3x 于 48pt）
//   小程序   menu-mini/src/static/{ing,avatar}/<key>.png（同上）
//   admin    menu-admin/src/assets/{ing,avatar}/<key>.svg（原样复制）
//   清单     docs/design/assets.json（key 列表，三端与后端 icon_key 共用）
//
// 用法：NODE_PATH=menu-admin/node_modules node scripts/design/gen-assets.mjs [--check]
// 依赖 playwright（menu-admin devDependency）做 SVG 光栅化，避免 Flutter 引入 flutter_svg。
import { createRequire } from 'node:module';
import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync, copyFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const SRC = join(ROOT, 'docs/design/prototype/img');
const SIZE = 144;
const CHECK = process.argv.includes('--check');

// 品牌图（App 图标等，源在 img/extra/）：只出 Flutter / 小程序 PNG
const BRAND = ['logo'];

const GROUPS = {
  ing: readdirSync(join(SRC, 'ing')).filter((f) => f.endsWith('.svg')).map((f) => f.slice(0, -4)).sort(),
  avatar: readdirSync(join(SRC, 'avatar')).filter((f) => f.endsWith('.svg')).map((f) => f.slice(0, -4)).sort(),
};
const manifest = { generatedBy: 'scripts/design/gen-assets.mjs', size: SIZE, ...GROUPS };
const manifestPath = join(ROOT, 'docs/design/assets.json');
const manifestText = JSON.stringify(manifest, null, 2) + '\n';

const targets = (group, key) => [
  join(ROOT, 'menu-flutter/assets', group, `${key}.png`),
  join(ROOT, 'menu-mini/src/static', group, `${key}.png`),
];

if (CHECK) {
  const missing = [];
  if (!existsSync(manifestPath) || readFileSync(manifestPath, 'utf8') !== manifestText) missing.push(manifestPath);
  for (const [group, keys] of Object.entries(GROUPS)) {
    for (const key of keys) {
      for (const p of [...targets(group, key), join(ROOT, 'menu-admin/src/assets', group, `${key}.svg`)]) {
        if (!existsSync(p)) missing.push(p);
      }
    }
  }
  if (missing.length) {
    console.error(`✗ 素材缺失或过期（${missing.length}）：\n  ` + missing.slice(0, 10).join('\n  '));
    process.exit(1);
  }
  console.log(`✓ 素材齐全（食材 ${GROUPS.ing.length}、头像 ${GROUPS.avatar.length}）`);
  process.exit(0);
}

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: SIZE, height: SIZE } });

for (const [group, keys] of Object.entries(GROUPS)) {
  for (const key of keys) {
    const svgPath = join(SRC, group, `${key}.svg`);
    const svg = readFileSync(svgPath, 'utf8').replace('<svg ', `<svg width="${SIZE}" height="${SIZE}" `);
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
    const png = await page.locator('svg').screenshot({ omitBackground: true });
    for (const out of targets(group, key)) {
      mkdirSync(dirname(out), { recursive: true });
      writeFileSync(out, png);
    }
    const adminOut = join(ROOT, 'menu-admin/src/assets', group, `${key}.svg`);
    mkdirSync(dirname(adminOut), { recursive: true });
    copyFileSync(svgPath, adminOut);
  }
}
for (const key of BRAND) {
  const svg = readFileSync(join(SRC, 'extra', `${key}.svg`), 'utf8').replace('<svg ', `<svg width="${SIZE * 2}" height="${SIZE * 2}" `);
  await page.setViewportSize({ width: SIZE * 2, height: SIZE * 2 });
  await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
  const png = await page.locator('svg').screenshot({ omitBackground: true });
  for (const out of [join(ROOT, 'menu-flutter/assets/brand', `${key}.png`), join(ROOT, 'menu-mini/src/static/brand', `${key}.png`)]) {
    mkdirSync(dirname(out), { recursive: true });
    writeFileSync(out, png);
  }
}
await browser.close();
writeFileSync(manifestPath, manifestText);
console.log(`✓ 生成 食材 ${GROUPS.ing.length}、头像 ${GROUPS.avatar.length} → Flutter / 小程序 / admin`);
