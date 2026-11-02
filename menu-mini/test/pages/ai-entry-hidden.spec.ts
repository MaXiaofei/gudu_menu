import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const read = (p: string) => readFileSync(resolve(__dirname, '../../src', p), 'utf8')
const tpl = (p: string) => {
  const s = read(p)
  return s.slice(0, s.indexOf('<script'))
}

// 生成式 AI 按类别暂停：入口隐藏、页面盖暂停提示（源码守护，防止新增入口漏接开关）
describe('拍照识别入口', () => {
  it('咕嘟页 / 对话页相机按钮、余量页拍照 FAB 受 featureStatus.vision 控制', () => {
    expect(tpl('pages/agent/Index.vue')).toMatch(/v-if="featureStatus\.vision"[^>]*aria-label="拍照识别"/)
    expect(tpl('pkg-agent/Chat.vue')).toMatch(/v-if="featureStatus\.vision"[^>]*@click="goScan"/)
    expect(tpl('pages/pantry/List.vue')).toMatch(/v-if="featureStatus\.vision"[^>]*data-testid="fab-scan"/)
  })
  it('Scan / ScanResult 页面挂暂停盖层', () => {
    expect(tpl('pkg-ai/Scan.vue')).toContain('<ui-feature-paused feature="vision" />')
    expect(tpl('pkg-ai/ScanResult.vue')).toContain('<ui-feature-paused feature="vision" />')
  })
  it('进入拍照识别的入口文件都接了开关；咕嘟页「拍…入库」快捷提问随之隐藏', () => {
    for (const f of ['pages/agent/Index.vue', 'pkg-agent/Chat.vue', 'pages/pantry/List.vue']) {
      expect(read(f)).toContain('featureStatus.vision')
    }
    expect(read('pages/agent/Index.vue')).toContain('visiblePrompts')
  })
})

describe('一周餐程入口', () => {
  it('食集页入口卡、营养报告「换进餐程」、对话里的餐程草稿卡受 featureStatus.mealPlan 控制', () => {
    expect(tpl('pages/menu/List.vue')).toMatch(/v-if="featureStatus\.mealPlan"[^>]*data-testid="go-weekplan"/)
    expect(tpl('pkg-ai/Nutrition.vue')).toMatch(/v-if="featureStatus\.mealPlan"[^>]*data-testid="swap-into-plan"/)
    expect(tpl('pkg-agent/parts/AssistantMessage.vue')).toContain("c.type === 'plan' && featureStatus.mealPlan")
  })
  it('WeekPlan 页面挂暂停盖层；营养报告页本身不受拦（非大模型）', () => {
    expect(tpl('pkg-ai/WeekPlan.vue')).toContain('<ui-feature-paused feature="mealPlan" />')
    expect(tpl('pkg-ai/Nutrition.vue')).not.toContain('ui-feature-paused')
  })
})

describe('咕嘟助手 / 问咕嘟', () => {
  it('咕嘟页、对话页、语音页挂咕嘟助手暂停盖层；跟做模式问咕嘟受 cookAsk 控制', () => {
    for (const f of ['pages/agent/Index.vue', 'pkg-agent/Chat.vue', 'pkg-agent/Voice.vue']) {
      expect(tpl(f)).toContain('<ui-agent-paused')
    }
    expect(tpl('pkg-ai/CookMode.vue')).toContain('featureStatus.cookAsk')
  })
})
