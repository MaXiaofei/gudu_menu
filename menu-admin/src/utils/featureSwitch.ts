/** 生成式 AI 紧急关停开关的展示、分组与二次确认文案（原因校验、最后操作文案复用 communitySwitch）。 */
import type { FeatureCategory, FeaturePlatform, FeatureSwitchState } from '@/api/featureSwitch'
import type { SwitchChange } from '@/api/communityAdmin'
import { lastOperationText as baseLastOperationText } from './communitySwitch'

export { validateSwitchReason, SWITCH_REASON_MAX } from './communitySwitch'

export const PLATFORM_LABEL: Record<FeaturePlatform, string> = { mp: '小程序', app: 'App' }

/** AI 表格行顺序。 */
export const CATEGORY_ORDER: FeatureCategory[] = ['agentChat', 'cookAsk', 'vision', 'mealPlan', 'estimate', 'postText']

/** 页面入口表格行顺序：与 AI 独立分组。 */
export const ENTRY_CATEGORY_ORDER: FeatureCategory[] = ['recipeWrite', 'dishDrafts', 'shoppingShare']

/** AI 各端的默认状态：小程序默认开启，App 默认关闭（App 暂未上线，防止入口被恶意调用）。 */
export const DEFAULT_ENABLED: Record<FeaturePlatform, boolean> = { mp: true, app: false }

/** 该类别在该端未配置时的默认状态：页面入口两端默认开启，AI 沿用端默认。 */
export function defaultEnabled(category: FeatureCategory | undefined, platform: FeaturePlatform): boolean {
  return ENTRY_CATEGORY_ORDER.includes(category as FeatureCategory) ? true : DEFAULT_ENABLED[platform]
}

/** 「最后操作：运营小王 · 2026-10-26 18:00」；从未切换过按该类别默认写「默认开启 / 默认关闭，尚未操作过」。 */
export function lastOperationText(
  s: Pick<SwitchChange, 'updatedByName' | 'updatedBy' | 'updatedAt'> | null,
  platform: FeaturePlatform = 'mp',
  category?: FeatureCategory,
): string {
  if (!s || (!s.updatedBy && !s.updatedAt))
    return defaultEnabled(category, platform) ? '默认开启，尚未操作过' : '默认关闭，尚未操作过'
  return baseLastOperationText(s)
}

export function featureStatusText(label: string, enabled: boolean): string {
  return enabled ? `${label}运行中` : `${label}已暂停`
}

/** 「小程序咕嘟助手」。 */
export function switchName(platform: FeaturePlatform, label: string): string {
  return `${PLATFORM_LABEL[platform]}${label}`
}

export interface MatrixRow {
  category: FeatureCategory
  label: string
  mp: FeatureSwitchState | null
  app: FeatureSwitchState | null
}

/** 后端扁平列表 → 行 = 类别、列 = 端（默认 AI 六类，页面入口传 ENTRY_CATEGORY_ORDER）。 */
export function buildMatrix(states: FeatureSwitchState[], order: FeatureCategory[] = CATEGORY_ORDER): MatrixRow[] {
  return order.map((category) => {
    const of = (p: FeaturePlatform) => states.find((s) => s.category === category && s.platform === p) ?? null
    const mp = of('mp')
    const app = of('app')
    return { category, label: (mp ?? app)?.label ?? category, mp, app }
  }).filter((r) => r.mp || r.app)
}

export interface RecentRow extends SwitchChange {
  label: string
  platform: FeaturePlatform
}

/** 最近修改记录：合并全部开关的记录，带类别与端，按时间倒序取前 limit 条。 */
export function recentChangeRows(states: FeatureSwitchState[], limit = 20): RecentRow[] {
  return states
    .flatMap((s) => (s.recentChanges ?? []).map((c) => ({ ...c, label: s.label, platform: s.platform })))
    .sort((a, b) => (b.updatedAt ?? '').localeCompare(a.updatedAt ?? ''))
    .slice(0, limit)
}

/** 各类别关停后用户看到的影响说明。 */
const IMPACT: Record<string, string> = {
  咕嘟助手: '用户无法再向咕嘟发新消息（咕嘟页显示「咕嘟助手暂停服务」），跟做模式里的提问同时暂停，已有会话与历史仍可查看、删除',
  问咕嘟: '跟做模式里的「问咕嘟」提问暂停（跟做模式提问需咕嘟助手与问咕嘟同时开启），跟做模式其余功能不受影响',
  拍照识别: '无法再新建拍照识别任务（入口隐藏，页面显示「拍照识别暂停服务」），已有识别结果仍可查看、应用',
  一周餐程:
    '无法再生成 AI 一周餐程草稿（入口隐藏，页面显示「一周餐程暂停服务」），咕嘟对话里的「排一周」同样暂停（咕嘟会告知暂停），咕嘟首页也不再给「一周怎么吃」类快捷提问，已生成的餐程仍可查看、应用',
  营养估算:
    '无法再用 AI 估算一餐营养、补全食材营养（入口暂停，提示「营养估算暂停服务」）；管理后台运营维护食材时补全营养不受影响，已有营养数据照常显示',
  'AI 帮写': '发帖页的「AI 帮写」暂停（提示「AI 帮写暂停服务」），自己写文案和发帖照常',
  写菜谱: '菜谱页的「写菜谱」按钮与「我的 → 写菜谱」入口隐藏，已有菜谱与草稿不受影响',
  草稿箱: '「我的 → 草稿箱」入口隐藏，草稿箱页显示「草稿箱暂停服务」，已有草稿仍保留，其他功能不受影响',
  采购清单分享: '采购清单详情页的「分享」按钮隐藏，清单查看、添加、入库与已有分享内容不受影响',
}

/** 二次确认：标题 / 正文 / 按钮。name 形如「小程序咕嘟助手」，label 为类别名（用于查影响说明）。 */
export function featureConfirmCopy(
  name: string,
  nextEnabled: boolean,
  label: string = name,
): { title: string; message: string; confirm: string } {
  return nextEnabled
    ? {
        title: `恢复${name}`,
        message: `恢复后${name}立即可用（其他服务实例最迟 15 秒生效）。确认风险已处理？`,
        confirm: '确认恢复',
      }
    : {
        title: `紧急关停${name}`,
        message: `${IMPACT[label] ?? `${label}相关功能暂停`}（仅${name.replace(label, '')}，最迟 15 秒全部生效）。确认关停？`,
        confirm: '确认关停',
      }
}

/** 某端一键切换全部的确认文案。 */
export function platformBatchCopy(platform: FeaturePlatform, nextEnabled: boolean): { title: string; message: string; confirm: string } {
  const p = PLATFORM_LABEL[platform]
  return nextEnabled
    ? {
        title: `${p}一键恢复全部 AI`,
        message: `将恢复${p}的咕嘟助手、问咕嘟、拍照识别、一周餐程、营养估算、AI 帮写（最迟 15 秒生效）。`,
        confirm: '确认全部恢复',
      }
    : {
        title: `${p}一键关闭全部 AI`,
        message: `将同时关闭${p}的咕嘟助手、问咕嘟、拍照识别、一周餐程、营养估算、AI 帮写，用户看到「暂停服务」，其他功能不受影响（最迟 15 秒生效）。`,
        confirm: '确认全部关闭',
      }
}
