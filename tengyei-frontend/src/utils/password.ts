const STRONG_PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,20}$/

export const PASSWORD_TIP = '密码须为 8-20 位，且包含大小写字母和数字'

export function isStrongPassword(value: string): boolean {
  return STRONG_PASSWORD_PATTERN.test(value)
}

export const strongPasswordPattern = STRONG_PASSWORD_PATTERN

/**
 * Element Plus 表单校验规则：强密码（必填）。
 */
export function strongPasswordRule(trigger: string = 'blur') {
  return {
    validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
      if (!value) callback(new Error('请输入密码'))
      else if (!isStrongPassword(value)) callback(new Error(PASSWORD_TIP))
      else callback()
    },
    trigger,
  }
}

/**
 * Element Plus 表单校验规则：强密码（可选，留空放行，用于编辑态）。
 */
export function optionalStrongPasswordRule(trigger: string = 'blur') {
  return {
    validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
      if (value && !isStrongPassword(value)) callback(new Error(PASSWORD_TIP))
      else callback()
    },
    trigger,
  }
}
