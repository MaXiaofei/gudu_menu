import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { installHttpMock } from '../helpers/http'
import { clearSessions, deleteSession } from '@/api/agent'

const read = (p: string) => readFileSync(resolve(__dirname, '../../src', p), 'utf8')

// 提审合规的源码守护：条件编译 / 标识接入不被改坏
describe('小程序只保留微信登录', () => {
  it('账号密码表单与「账号密码登录」入口都在 #ifndef MP-WEIXIN 内', () => {
    const src = read('pages/login/Login.vue')
    const tpl = src.slice(0, src.indexOf('</template>'))
    const open = tpl.indexOf('<!-- #ifndef MP-WEIXIN -->')
    const close = tpl.indexOf('<!-- #endif -->', open)
    expect(open).toBeGreaterThan(-1)
    const form = tpl.indexOf('class="account"')
    expect(form).toBeGreaterThan(open)
    expect(form).toBeLessThan(close)
    expect(tpl).not.toContain('账号密码登录')
    expect(tpl).not.toContain('收起账号登录')
  })
})

describe('跟做模式 AI 回复带标识与反馈入口', () => {
  it('CookMode 的 AI 回复使用 ui-ai-badge（source=agent + feedback-ref）', () => {
    const src = read('pkg-ai/CookMode.vue')
    expect(src).toMatch(/<ui-ai-badge[^>]*source="agent"[^>]*:feedback-ref="m\.messageId"/)
  })
})

// 隐私政策 §三.2：AI 对话可自行删除
describe('AI 对话删除 api/agent', () => {
  it('删除单个会话：DELETE /agent/sessions/{id}；清空：DELETE /agent/sessions', async () => {
    const http = installHttpMock({ 'DELETE /agent/sessions/ses_1': {}, 'DELETE /agent/sessions': { deleted: 2 } })
    await deleteSession('ses_1')
    expect(http.calls[0].url).toBe('/agent/sessions/ses_1')
    const r = await clearSessions()
    expect(r.deleted).toBe(2)
    expect(http.calls[1].url).toBe('/agent/sessions')
  })
})
