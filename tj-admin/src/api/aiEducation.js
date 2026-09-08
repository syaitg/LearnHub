import request from '@/utils/request.js'

/**
 * AI 教育能力接口统一封装。
 * 新增功能均通过网关访问考试服务和 AIGC 服务，避免页面直接拼接接口地址。
 */
const EXAM_API_PREFIX = '/es'
const AIGC_API_PREFIX = '/ais'

/**
 * 统一处理 AI 页面请求，避免每个页面重复编写相同的异常捕获逻辑。
 * 网络异常也转换为页面可识别的中文业务响应，不影响原有请求工具的行为。
 */
const requestAi = async (config) => {
  try {
    return await request(config)
  } catch (error) {
    const responseData = error?.response?.data
    return {
      code: responseData?.code || 0,
      msg: responseData?.msg || responseData?.message || (error?.message === 'Network Error' ? '网络连接失败，请检查网络后重试' : '服务暂时不可用，请稍后重试'),
    }
  }
}

/** 分页查询 AI 出题批次。 */
export const queryAiQuestionBatches = (params) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-batches/page`, method: 'get', params })
/** 查询 AI 出题批次详情。 */
export const getAiQuestionBatch = (id) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-batches/${id}`, method: 'get' })
/** 创建 AI 出题批次。 */
export const createAiQuestionBatch = (data) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-batches`, method: 'post', data })
/** 确认一批题目草稿。 */
export const confirmAiQuestionBatch = (id, data = {}) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-batches/${id}/confirm`, method: 'post', data })
/** 发布已确认题目。 */
export const publishAiQuestionBatch = (id) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-batches/${id}/publish`, method: 'post' })
/** 重试 AI 出题批次。 */
export const retryAiQuestionBatch = (id) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-batches/${id}/retry`, method: 'post' })
/** 编辑题目草稿。 */
export const updateAiQuestionDraft = (id, data) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-drafts/${id}`, method: 'put', data })
/** 确认单道题目草稿。 */
export const confirmAiQuestionDraft = (id) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-drafts/${id}/confirm`, method: 'post' })
/** 驳回单道题目草稿。 */
export const rejectAiQuestionDraft = (id, data) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-drafts/${id}/reject`, method: 'post', data })
/** 删除尚未发布的题目草稿。 */
export const deleteAiQuestionDraft = (id) => requestAi({ url: `${EXAM_API_PREFIX}/ai-question-drafts/${id}`, method: 'delete' })

/** 创建学生练习会话。 */
export const createPracticeSession = (data) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions`, method: 'post', data })
/** 分页查询学生练习记录。 */
export const queryPracticeSessions = (params) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions/page`, method: 'get', params })
/** 查询练习详情。 */
export const getPracticeSession = (id) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions/${id}`, method: 'get' })
/** 保存练习答案。 */
export const savePracticeAnswer = (sessionId, questionId, data) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions/${sessionId}/questions/${questionId}/answer`, method: 'put', data })
/** 提交练习。 */
export const submitPracticeSession = (id) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions/${id}/submit`, method: 'post' })
/** 重试主观题 AI 评估。 */
export const retryPracticeAiReview = (id) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions/${id}/ai-review/retry`, method: 'post' })
/** 查询主观题 AI 待审核列表。 */
export const queryPendingAiReviews = (params) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions/ai-review/pending/page`, method: 'get', params })
/** 确认主观题 AI 评估结果。 */
export const confirmPracticeAiReview = (sessionId, data) => requestAi({ url: `${EXAM_API_PREFIX}/practice-sessions/${sessionId}/ai-review/confirm`, method: 'post', data })
/** 分页查询错题。 */
export const queryWrongQuestions = (params) => requestAi({ url: `${EXAM_API_PREFIX}/wrong-questions/page`, method: 'get', params })
/** 查询错题重做记录。 */
export const queryWrongQuestionReviews = (id) => requestAi({ url: `${EXAM_API_PREFIX}/wrong-questions/${id}/reviews`, method: 'get' })
/** 提交错题重做。 */
export const reviewWrongQuestion = (id, data) => requestAi({ url: `${EXAM_API_PREFIX}/wrong-questions/${id}/reviews`, method: 'post', data })

/** 创建视频 AI 处理任务。 */
export const createVideoAiTask = (data) => requestAi({ url: `${AIGC_API_PREFIX}/video-ai-tasks`, method: 'post', data })
/** 分页查询视频 AI 任务。 */
export const queryVideoAiTasks = (params) => requestAi({ url: `${AIGC_API_PREFIX}/video-ai-tasks/page`, method: 'get', params })
/** 查询视频 AI 任务详情。 */
export const getVideoAiTask = (id) => requestAi({ url: `${AIGC_API_PREFIX}/video-ai-tasks/${id}`, method: 'get' })
/** 重试视频 AI 任务。 */
export const retryVideoAiTask = (id) => requestAi({ url: `${AIGC_API_PREFIX}/video-ai-tasks/${id}/retry`, method: 'post' })
/** 创建小节视频测验草稿。 */
export const createVideoQuizDrafts = (id, data) => requestAi({ url: `${AIGC_API_PREFIX}/video-ai-tasks/${id}/quiz-drafts`, method: 'post', data })
/** 创建章级视频综合测试草稿。 */
export const createChapterQuizDrafts = (data) => requestAi({ url: `${AIGC_API_PREFIX}/video-ai-tasks/chapter-quiz-drafts`, method: 'post', data })

/** 查询课程草稿目录，供课程创建者在 AI 工作台选择小节和练习目录。 */
export const queryCourseCatalogues = (courseId) => requestAi({ url: `/cs/courses/catas/${courseId}`, method: 'get', params: { see: 0, withPractice: 1 } })
/** 分页查询课程，供工作台选择课程。 */
export const queryCourses = (params) => requestAi({ url: '/cs/courses/page', method: 'get', params })
/** 分页查询当前课程创建者可管理的课程，供 AI 教学工作台选择课程。 */
export const queryManagedCourses = (params) => requestAi({ url: '/cs/courses/page/mine', method: 'get', params })
/** 分页查询当前学生的课程，供智能练习选择课程。 */
export const queryMyCourses = (params) => requestAi({ url: '/ls/lessons/page', method: 'get', params })

/** 统一取出项目包装响应中的业务数据。 */
export const unwrapAiResponse = (response) => response && response.code === 200 ? response.data : null
/** 取得后端错误消息，避免页面重复编写响应判断。 */
export const getAiErrorMessage = (response, fallback = '操作失败，请稍后重试') => response && (response.msg || response.message) ? (response.msg || response.message) : fallback
