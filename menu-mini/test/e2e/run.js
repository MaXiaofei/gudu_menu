/**
 * E2E 入口：node test/e2e/run.js
 * 前置：
 *   1. 微信开发者工具已开启「服务端口」
 *   2. 已启动自动化：cli auto --project <dist/build/mp-weixin> --auto-port 9420
 *   （可用 npm run e2e:auto 一键启动）
 * 环境变量：E2E_WS（默认 ws://localhost:9420）
 */
const path = require('node:path')
const { connect, E2ERunner } = require('./helpers')

const suites = [
  require('./login.e2e.js'),
  require('./dish.e2e.js'),
  require('./menu.e2e.js'),
  require('./pantry.e2e.js'),
  require('./misc.e2e.js'),
]

async function main() {
  const mini = await connect()
  const r = new E2ERunner()
  console.log('========== E2E 开始 ==========')

  for (const suite of suites) {
    try {
      await suite(mini, r)
    } catch (e) {
      r.log(`套件异常：${e.message}`.slice(0, 80), false)
    }
  }

  // 截图留档
  try {
    const shot = path.resolve(__dirname, 'e2e-final.png')
    await mini.screenshot({ path: shot })
    console.log(`\n已截图 ${shot}`)
  } catch {}

  const { pass, total } = r.summary()
  console.log(`========== E2E 结束：${pass}/${total} 通过 ==========`)
  process.exit(pass === total ? 0 : 1)
}

main().catch((e) => {
  console.error('E2E 异常：', e.message)
  console.error('请确认：服务端口已开启 + npm run e2e:auto 已运行')
  process.exit(1)
})
