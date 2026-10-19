// 数据库连接 + 环境守卫。
// 只允许两类目标：
//   staging：SSH 隧道到 STAGING_SSH_HOST 的 127.0.0.1:13306（gudu-mysql-staging，生产为 3306）
//   local  ：LOCAL_DB_HOST/PORT（默认 127.0.0.1:3306，本机开发库）
// 其他一律拒绝。生产与 staging 同机，靠「远端端口必须为 13306 + 密码必须不同于生产 MYSQL_ROOT_PASSWORD」双重确认。
import { spawn } from 'node:child_process';
import net from 'node:net';
import mysql from 'mysql2/promise';
import { readEnv } from './env.mjs';

export const STAGING_REMOTE_PORT = 13306;
const PROD_PORTS = new Set([3306]);

export function assertTarget({ env, remotePort, password, prodPassword }) {
  if (env !== 'staging' && env !== 'local') throw new Error(`拒绝：--env 只能是 staging 或 local（收到 ${env}）`);
  if (env === 'staging') {
    if (PROD_PORTS.has(remotePort) || remotePort !== STAGING_REMOTE_PORT) {
      throw new Error(`拒绝：staging 目标端口必须是 ${STAGING_REMOTE_PORT}（收到 ${remotePort}），生产库端口禁止写入`);
    }
    if (!password) throw new Error('拒绝：.env 缺少 STAGING_MYSQL_ROOT_PASSWORD');
    if (prodPassword && password === prodPassword) throw new Error('拒绝：staging 凭据与生产相同，无法确认目标不是生产');
  }
  return true;
}

function freePort() {
  return new Promise((res, rej) => {
    const s = net.createServer();
    s.listen(0, '127.0.0.1', () => { const p = s.address().port; s.close(() => res(p)); });
    s.on('error', rej);
  });
}

function waitPort(port, ms = 30000) {
  const t0 = Date.now();
  return new Promise((res, rej) => {
    const tryOnce = () => {
      const c = net.connect(port, '127.0.0.1');
      c.on('connect', () => { c.destroy(); res(); });
      c.on('error', () => { c.destroy(); Date.now() - t0 > ms ? rej(new Error('SSH 隧道建立超时')) : setTimeout(tryOnce, 300); });
    };
    tryOnce();
  });
}

/** 打开连接；返回 { conn, close, describe }。staging SSH 偶发握手超时，最多重试 3 次 */
export async function connect(target) {
  let last;
  for (let i = 1; i <= 3; i++) {
    try { return await connectOnce(target); } catch (e) {
      last = e;
      if (target !== 'staging' || /^拒绝/.test(e.message)) throw e;
      console.warn(`连接失败（第 ${i} 次）：${e.message}`);
      await new Promise((r) => setTimeout(r, 5000 * i));
    }
  }
  throw last;
}

async function connectOnce(target) {
  const e = readEnv();
  if (target === 'local') {
    assertTarget({ env: 'local' });
    const conn = await mysql.createConnection({
      host: e.LOCAL_DB_HOST || '127.0.0.1', port: Number(e.LOCAL_DB_PORT || 3306),
      user: e.LOCAL_DB_USER || 'root', password: e.LOCAL_DB_PASSWORD || '', database: e.LOCAL_DB_NAME || 'gudu',
      charset: 'utf8mb4', multipleStatements: false,
    });
    return { conn, close: () => conn.end(), describe: 'local' };
  }
  assertTarget({ env: target, remotePort: STAGING_REMOTE_PORT, password: e.STAGING_MYSQL_ROOT_PASSWORD, prodPassword: e.MYSQL_ROOT_PASSWORD });
  if (!e.STAGING_SSH_HOST || !e.STAGING_SSH_USER || !e.STAGING_SSH_PASS) throw new Error('.env 缺少 STAGING_SSH_HOST/USER/PASS');
  const port = await freePort();
  // 密码经 SSHPASS 环境变量传给 sshpass -e，不出现在命令行参数里
  const ssh = spawn('sshpass', ['-e', 'ssh', '-N', '-o', 'StrictHostKeyChecking=no', '-o', 'ExitOnForwardFailure=yes',
    '-o', 'ServerAliveInterval=20', '-o', 'ConnectTimeout=30', '-L', `${port}:127.0.0.1:${STAGING_REMOTE_PORT}`, `${e.STAGING_SSH_USER}@${e.STAGING_SSH_HOST}`],
  { env: { PATH: process.env.PATH, HOME: process.env.HOME, SSHPASS: e.STAGING_SSH_PASS }, stdio: ['ignore', 'ignore', 'pipe'] });
  let sshErr = '';
  ssh.stderr.on('data', (d) => { sshErr += d; });
  try {
    await waitPort(port);
  } catch (err) {
    ssh.kill();
    throw new Error(`${err.message}${sshErr ? `：${sshErr.trim().split('\n').pop()}` : ''}`);
  }
  const conn = await mysql.createConnection({
    host: '127.0.0.1', port, user: 'root', password: e.STAGING_MYSQL_ROOT_PASSWORD, database: 'gudu',
    charset: 'utf8mb4', multipleStatements: false, connectTimeout: 20000,
  });
  return {
    conn,
    close: async () => { try { await conn.end(); } finally { ssh.kill(); } },
    describe: `staging（隧道 → :${STAGING_REMOTE_PORT}）`,
  };
}

/** 在 staging 主机上执行远程命令（rsync/curl 用），返回 stdout */
export function sshEnv() {
  const e = readEnv();
  return { host: e.STAGING_SSH_HOST, user: e.STAGING_SSH_USER, env: { PATH: process.env.PATH, HOME: process.env.HOME, SSHPASS: e.STAGING_SSH_PASS } };
}

export function parseEnvArg(argv) {
  const a = argv.find((x) => x.startsWith('--env='));
  return a ? a.slice(6) : '';
}
