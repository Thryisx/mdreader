export const PASSWORD_PATTERN = /^(?=(?:.*\d){8})(?=(?:.*[A-Za-z]){2})[A-Za-z0-9]{10}$/

export const PASSWORD_MESSAGE = '密码必须为10位，且包含8位数字和2位英文字母'

export function isValidPassword(value: string) {
  return PASSWORD_PATTERN.test(value)
}
