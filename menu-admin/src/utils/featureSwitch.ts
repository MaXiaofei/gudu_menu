/** 咕嘟助手 / 问咕嘟紧急关停开关的展示与二次确认文案（原因校验、最后操作文案复用 communitySwitch）。 */

export { lastOperationText, validateSwitchReason, SWITCH_REASON_MAX } from './communitySwitch'

export function featureStatusText(label: string, enabled: boolean): string {
  return enabled ? `${label}运行中` : `${label}已暂停`
}

/** 各开关关停后用户看到的影响说明。 */
const IMPACT: Record<string, string> = {
  咕嘟助手: '用户无法再向咕嘟发新消息（咕嘟页显示「咕嘟助手暂停服务」），已有会话与历史仍可查看、删除',
  问咕嘟: '跟做模式里的「问咕嘟」提问暂停，跟做模式其余功能不受影响',
}

/** 二次确认：标题 / 正文 / 按钮。 */
export function featureConfirmCopy(label: string, nextEnabled: boolean): { title: string; message: string; confirm: string } {
  return nextEnabled
    ? {
        title: `恢复${label}`,
        message: `恢复后${label}立即可用（其他服务实例最迟 15 秒生效）。确认风险已处理？`,
        confirm: '确认恢复',
      }
    : {
        title: `紧急关停${label}`,
        message: `${IMPACT[label] ?? `${label}相关功能暂停`}（最迟 15 秒全部生效）。仅用于出现内容风险时，确认关停？`,
        confirm: '确认关停',
      }
}
