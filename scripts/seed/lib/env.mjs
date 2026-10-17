// 读根 .env（不导出到 process.env，避免子进程/日志意外带出凭据）
import { readFileSync, appendFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const ENV_PATH = join(ROOT, '.env');

export function readEnv() {
  if (!existsSync(ENV_PATH)) throw new Error('根目录缺少 .env（模板见 .env.example）');
  const env = {};
  for (const line of readFileSync(ENV_PATH, 'utf8').split('\n')) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (!m) continue;
    let v = m[2];
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    env[m[1]] = v;
  }
  return env;
}

/** 只追加缺失的键，不改动已有内容 */
export function appendEnvIfMissing(key, value, comment) {
  const env = readEnv();
  if (env[key]) return false;
  appendFileSync(ENV_PATH, `${comment ? `\n# ${comment}` : ''}\n${key}=${value}\n`);
  return true;
}
