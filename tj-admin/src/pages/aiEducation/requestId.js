/**
 * 创建长度不超过 64 个字符的客户端幂等请求标识。
 * 同一次表单提交失败后应复用该标识，只有表单内容变更、重置或提交成功后才重新生成。
 *
 * @param {string} prefix 业务前缀
 * @returns {string} 客户端幂等请求标识
 */
export const createClientRequestId = (prefix) => {
  const normalizedPrefix = String(prefix || 'ai').replace(/[^a-zA-Z0-9-]/g, '').slice(0, 20) || 'ai'
  const randomId = globalThis.crypto?.randomUUID
    ? globalThis.crypto.randomUUID().replace(/-/g, '')
    : `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 18)}`
  return `${normalizedPrefix}-${randomId}`.slice(0, 64)
}
