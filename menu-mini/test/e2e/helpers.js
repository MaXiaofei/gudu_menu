/** E2E 共享：连接、断言收集、工具。 */
const automator = require('miniprogram-automator')

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

class E2ERunner {
  constructor() {
    this.results = []
  }
  log(step, ok, extra = '') {
    const line = `  ${ok ? '✓' : '✗'} ${step}${extra ? ' — ' + extra : ''}`
    console.log(line)
    this.results.push({ step, ok })
    return ok
  }
  summary() {
    const pass = this.results.filter((r) => r.ok).length
    return { pass, total: this.results.length }
  }
}

async function connect() {
  const endpoint = process.env.E2E_WS || 'ws://localhost:9420'
  console.log(`连接自动化端口 ${endpoint} ...`)
  const mini = await automator.connect({ wsEndpoint: endpoint })
  console.log('已连接\n')
  return mini
}

module.exports = { sleep, E2ERunner, connect }
