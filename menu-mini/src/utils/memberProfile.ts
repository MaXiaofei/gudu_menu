import type { HealthProfile, Member, MemberSave } from '@/api/member'

/** 成员档案表单（本家成员档案：有账号的家人 / 无账号的宝宝、老人共用）。 */
export interface ProfileForm {
  name: string
  gender: '' | 'male' | 'female'
  age: string
  height: string
  weight: string
  audiences: string[]
  /** 逗号 / 顿号 / 空格分隔 */
  allergies: string
  goal: '' | 'LOSE' | 'MAINTAIN' | 'GAIN'
  activityLevel: '' | 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'ACTIVE'
}

/** 特殊人群（术语约定：高血压 / 高血糖 / 高血脂 / 宝宝辅食）。 */
export const AUDIENCES = ['高血压', '高血糖', '高血脂', '宝宝辅食'] as const

export function emptyForm(name = ''): ProfileForm {
  return { name, gender: '', age: '', height: '', weight: '', audiences: [], allergies: '', goal: '', activityLevel: '' }
}

const str = (v: unknown) => (v === undefined || v === null || v === 0 ? '' : String(v))

export function toForm(m?: Member | null): ProfileForm {
  if (!m) return emptyForm()
  const hp: HealthProfile = m.healthProfile || {}
  return {
    name: m.name || '',
    gender: hp.gender === 'male' || hp.gender === 'female' ? hp.gender : '',
    age: str(hp.age),
    height: str(hp.height),
    weight: str(hp.weight),
    audiences: Array.isArray(hp.audiences) ? [...hp.audiences] : [],
    allergies: Array.isArray(hp.allergies) ? hp.allergies.join('、') : '',
    goal: (m.goal as ProfileForm['goal']) || '',
    activityLevel: (m.activityLevel as ProfileForm['activityLevel']) || '',
  }
}

function num(s: string, min: number, max: number): number | null | 'bad' {
  const t = s.trim()
  if (!t) return null
  const n = Number(t)
  if (!Number.isFinite(n) || n < min || n > max) return 'bad'
  return n
}

/** 校验：返回错误文案，合法返回空串。 */
export function validateForm(f: ProfileForm): string {
  const name = f.name.trim()
  if (!name) return '请填写称呼'
  if (name.length > 32) return '称呼最多 32 个字'
  if (num(f.age, 0, 120) === 'bad') return '年龄需在 0–120 之间'
  if (num(f.height, 30, 250) === 'bad') return '身高需在 30–250 cm 之间'
  if (num(f.weight, 2, 300) === 'bad') return '体重需在 2–300 kg 之间'
  return ''
}

/**
 * 表单 → 保存入参。保留原健康档案里表单不管的字段（如糖 / 盐上限，由后台维护），只覆盖表单字段。
 */
export function toSave(f: ProfileForm, base?: Member | null): MemberSave {
  const hp: HealthProfile = { ...(base?.healthProfile || {}) }
  const put = (k: string, v: unknown) => {
    if (v === null || v === '' || (Array.isArray(v) && !v.length)) delete hp[k]
    else hp[k] = v
  }
  put('gender', f.gender || null)
  put('age', num(f.age, 0, 120))
  put('height', num(f.height, 30, 250))
  put('weight', num(f.weight, 2, 300))
  put('audiences', f.audiences)
  put(
    'allergies',
    f.allergies
      .split(/[,，、\s]+/)
      .map((s) => s.trim())
      .filter(Boolean),
  )
  return {
    ...(base?.id ? { id: base.id } : {}),
    name: f.name.trim(),
    healthProfile: hp,
    goal: f.goal || null,
    activityLevel: f.activityLevel || null,
  }
}
