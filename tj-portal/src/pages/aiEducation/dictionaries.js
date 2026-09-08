/**
 * AI 教育功能展示字典与通用格式化方法。
 */
export const questionTypeOptions = [
  { value: 1, label: '单选题' }, { value: 2, label: '多选题' }, { value: 3, label: '不定项选择题' },
  { value: 4, label: '判断题' }, { value: 5, label: '主观题' },
]
export const difficultyOptions = [
  { value: 1, label: '简单' }, { value: 2, label: '中等' }, { value: 3, label: '困难' },
]
const batchStatusMap = {
  CREATED: ['已创建', 'info'], GENERATING: ['生成中', 'warning'], VALIDATING: ['校验中', 'warning'], PENDING: ['待处理', 'warning'],
  PENDING_CONFIRMATION: ['待确认', 'primary'], PARTIALLY_PUBLISHED: ['部分发布', 'warning'], PUBLISHED: ['已发布', 'success'],
  GENERATION_FAILED: ['生成失败', 'danger'], FAILED: ['处理失败', 'danger'], INVALID: ['全部无效', 'danger'], CANCELLED: ['已取消', 'info'],
}
const draftStatusMap = {
  PENDING: ['待处理', 'warning'], PENDING_CONFIRMATION: ['待确认', 'primary'], CONFIRMED: ['已确认', 'success'], REJECTED: ['已驳回', 'info'],
  INVALID: ['校验不通过', 'danger'], DUPLICATE: ['疑似重复', 'warning'], PUBLISHED: ['已发布', 'success'], FAILED: ['处理失败', 'danger'],
}
const videoStatusMap = {
  CREATED: ['等待处理', 'info'], QUEUED: ['排队中', 'info'], PROCESSING: ['处理中', 'warning'], TRANSCRIBING: ['正在转写', 'warning'],
  TRANSCRIBED: ['转写完成', 'primary'], ANALYZING: ['正在分析', 'warning'], COMPLETED: ['处理完成', 'success'], SUCCESS: ['处理完成', 'success'], FAILED: ['处理失败', 'danger'], CANCELLED: ['已取消', 'info'],
}
const practiceStatusMap = {
  IN_PROGRESS: ['答题中', 'primary'], SUBMITTED: ['待完成批改', 'warning'], COMPLETED: ['已完成', 'success'], MASTERED: ['已掌握', 'success'], PENDING: ['待处理', 'warning'], FAILED: ['处理失败', 'danger'],
}
const answerStatusMap = {
  UNANSWERED: '未作答', SAVED: '已保存', OBJECTIVE_GRADED: '已判分', PENDING_AI_REVIEW: '等待 AI 评估', AI_REVIEWING: 'AI 评估中', AI_REVIEWED: 'AI 已评估', AI_REVIEW_FAILED: 'AI 评估失败', MANUALLY_REVIEWED: '人工已确认',
}
const sourceTypeMap = { MANUAL_TOPIC: '手工主题', MATERIAL_TEXT: '材料文本', VIDEO_TRANSCRIPT: '视频转写', VIDEO_SUMMARY: '视频摘要' }
const importanceMap = { 1: '低', 2: '较低', 3: '中等', 4: '较高', 5: '高' }
const difficultyTextMap = { 1: '简单', 2: '中等', 3: '困难', EASY: '简单', MEDIUM: '中等', HARD: '困难' }

/** 将来源类型转换为中文。 */
export const sourceTypeText = source => sourceTypeMap[source] || (source ? '其他来源' : '-')
/** 取得状态中文名称。 */
export const statusText = (status, category = 'batch') => {
  const maps = { batch: batchStatusMap, draft: draftStatusMap, video: videoStatusMap, practice: practiceStatusMap }
  return maps[category]?.[status]?.[0] || (status ? '未知状态' : '-')
}
/** 取得状态标签类型。 */
export const statusType = (status, category = 'batch') => {
  const maps = { batch: batchStatusMap, draft: draftStatusMap, video: videoStatusMap, practice: practiceStatusMap }
  return maps[category]?.[status]?.[1] || 'info'
}
/** 取得题型中文名称。 */
export const questionTypeText = type => questionTypeOptions.find(item => item.value === Number(type))?.label || '未知题型(' + type + ')'
/** 取得难度中文名称。 */
export const difficultyText = difficulty => difficultyTextMap[difficulty] || difficultyOptions.find(item => item.value === Number(difficulty))?.label || '-'
export const knowledgeDifficultyText = difficulty => difficultyText(difficulty) || '未知'
export const importanceText = importance => {
  if (importance === null || importance === undefined || importance === '') return '-'
  const key = Number(importance)
  return importanceMap[key] ? importanceMap[key] + '（' + key + '）' : '未知（' + importance + '）'
}
/** 取得答案状态中文名称。 */
export const answerStatusText = status => answerStatusMap[status] || status || '-'
export const answerDisplayText = (answer, type) => {
  if (answer === null || answer === undefined || answer === '') return '-'
  const normalizedType = Number(type)
  const optionLabel = value => {
    const index = Number(value)
    return Number.isInteger(index) && index >= 0 && index < 26 ? String.fromCharCode(65 + index) : String(value)
  }
  if (normalizedType === 2 || normalizedType === 3) return String(answer).split(',').filter(Boolean).map(optionLabel).join('、') || '-'
  if (normalizedType === 1) return optionLabel(answer)
  if (normalizedType === 4) return ['1', 'true', '正确', '对', '是'].includes(String(answer).trim().toLowerCase()) ? '正确' : '错误'
  return String(answer)
}
/** 格式化日期时间。 */
export const formatDateTime = value => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
/** 格式化毫秒时间点。 */
export const formatDuration = ms => {
  if (ms === null || ms === undefined) return '--:--'
  const seconds = Math.max(0, Math.floor(Number(ms) / 1000))
  return String(Math.floor(seconds / 60)).padStart(2, '0') + ':' + String(seconds % 60).padStart(2, '0')
}
/** 将后端 0-based 逗号答案转换为表单值。 */
export const parseAnswer = (answer, type) => {
  const normalizedType = Number(type)
  if (answer === null || answer === undefined || answer === '') return normalizedType === 2 || normalizedType === 3 ? [] : ''
  if (normalizedType === 2 || normalizedType === 3) return String(answer).split(',').filter(Boolean).map(Number)
  if (normalizedType === 1) return Number(answer)
  if (normalizedType === 4) return ['1', 'true', '正确', '对', '是'].includes(String(answer).trim().toLowerCase())
  return String(answer)
}
/** 将表单中的 0-based 答案转换为后端文本。 */
export const serializeAnswer = (answer, type) => {
  const normalizedType = Number(type)
  if (normalizedType === 2 || normalizedType === 3) return Array.isArray(answer) ? [...answer].sort((a, b) => a - b).join(',') : ''
  return answer === null || answer === undefined ? '' : String(answer)
}
