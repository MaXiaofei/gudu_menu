#!/usr/bin/env node
// 补登记迁移（与 scripts/deploy.sh 2.1「登记表为空 → 一次性登记，不执行」同语义）。
// 用于：迁移已被手工执行、但 schema_migration_log 未登记的库（2026-10-16 staging V64–V67）。
// 用法：node register-migrations.mjs --env=staging --upto=V67 [--dry-run]
// 只登记 <= upto 的文件；已登记的文件只比对 checksum，不覆盖。
import { readFileSync, readdirSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join } from 'node:path';
import { connect, parseEnvArg } from './lib/db.mjs';
import { ROOT } from './lib/env.mjs';

const argv = process.argv.slice(2);
const target = parseEnvArg(argv);
const upto = Number((argv.find((a) => a.startsWith('--upto=')) || '').replace(/^--upto=V?/, ''));
const dry = argv.includes('--dry-run');
if (!upto) { console.error('需要 --upto=V<号>'); process.exit(2); }

const SQL_DIR = join(ROOT, 'menu-api/sql');
const files = readdirSync(SQL_DIR).filter((f) => /^V\d+__[A-Za-z0-9_]+\.sql$/.test(f))
  .filter((f) => Number(f.match(/^V(\d+)/)[1]) <= upto)
  .sort((a, b) => Number(a.match(/^V(\d+)/)[1]) - Number(b.match(/^V(\d+)/)[1]) || a.localeCompare(b));
const sums = files.map((f) => [f, createHash('sha256').update(readFileSync(join(SQL_DIR, f))).digest('hex')]);

const { conn, close, describe } = await connect(target);
try {
  console.log(`目标：${describe}；待登记文件 ${files.length} 个（≤ V${upto}）${dry ? '【dry-run】' : ''}`);
  if (!dry) await conn.query(readFileSync(join(SQL_DIR, 'V63__schema_migration_log.sql'), 'utf8').replace(/--.*$/gm, ''));
  const [rows] = dry ? [[]] : await conn.query('SELECT filename, checksum FROM schema_migration_log');
  const reg = new Map(rows.map((r) => [r.filename, r.checksum]));
  let added = 0; const mismatch = [];
  for (const [f, s] of sums) {
    if (reg.has(f)) { if (reg.get(f) !== s) mismatch.push(f); continue; }
    if (!dry) await conn.query('INSERT INTO schema_migration_log(filename, checksum) VALUES (?, ?)', [f, s]);
    added++;
  }
  console.log(`新登记 ${added} 个；已登记 ${reg.size} 个；checksum 不一致 ${mismatch.length} 个${mismatch.length ? '：' + mismatch.join(', ') : ''}`);
  if (mismatch.length) process.exitCode = 1;
} finally {
  await close();
}
