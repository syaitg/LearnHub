<template>
  <div class="practice-page container">
    <div class="practice-header">
      <div>
        <div class="eyebrow">智能练习空间</div>
        <h1>智能学习练习</h1>
        <p>进入课程练习，完成客观题即时判分，并查看主观题的 AI 批改建议与人工确认结果。</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="开始练习" name="start">
        <div class="practice-grid">
          <el-card shadow="never" class="practice-card start-card">
            <template #header>
              <div class="card-title">
                <span>创建练习</span>
                <el-tag type="primary">P1-B/C</el-tag>
              </div>
            </template>
            <el-alert
              title="选择课程和练习目录后开始答题，客观题自动判分，主观题由 AI 提供批改建议。"
              type="info"
              :closable="false"
              show-icon
            />
            <el-form :model="startForm" label-width="92px" class="start-form">
              <CourseCataloguePicker
                ref="startPickerRef"
                v-model:course-id="startForm.courseId"
                v-model:target-biz-id="startForm.targetBizId"
                course-source="mine"
                :show-scope="false"
                :show-section="false"
                :show-media="false"
              />
              <el-form-item>
                <el-button type="primary" :loading="starting" @click="startPractice">开始练习</el-button>
                <el-button @click="resetStartForm">重置</el-button>
              </el-form-item>
            </el-form>
          </el-card>

          <el-card shadow="never" class="practice-card history-card">
            <template #header>
              <div class="card-title">
                <span>历史练习</span>
                <el-button link type="primary" @click="loadSessions">刷新</el-button>
              </div>
            </template>
            <div class="filter-row">
              <CourseSelect v-model="sessionQuery.courseId" source="mine" placeholder="筛选我的课程" @change="reloadSessions" />
              <el-input v-model="sessionQuery.targetBizId" clearable placeholder="练习目录 ID" @keyup.enter="reloadSessions" />
              <el-select v-model="sessionQuery.status" clearable placeholder="会话状态" @change="reloadSessions">
                <el-option label="答题中" value="IN_PROGRESS" />
                <el-option label="待完成批改" value="SUBMITTED" />
                <el-option label="已完成" value="COMPLETED" />
              </el-select>
              <el-button @click="reloadSessions">查询</el-button>
            </div>
            <el-table v-loading="sessionLoading" :data="sessions" empty-text="暂无练习记录" row-key="id">
              <el-table-column prop="id" label="会话" width="100" />
              <el-table-column prop="targetName" label="练习目录" min-width="170" show-overflow-tooltip />
              <el-table-column label="答题进度" width="110">
                <template #default="{ row }">{{ row.answeredCount || 0 }}/{{ row.totalQuestions || 0 }}</template>
              </el-table-column>
              <el-table-column label="正式得分" width="115">
                <template #default="{ row }">{{ row.finalScore ?? 0 }}/{{ row.totalScore ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="客观题得分" width="125">
                <template #default="{ row }">{{ row.objectiveScore ?? 0 }}/{{ row.objectiveFullScore ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="正确率" width="90">
                <template #default="{ row }">{{ accuracyText(row.objectiveAccuracy) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag :type="statusType(row.status, 'practice')">{{ statusText(row.status, 'practice') }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openSession(row.id)">{{ row.status === 'IN_PROGRESS' ? '继续作答' : '查看结果' }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="pager">
              <el-pagination
                v-model:current-page="sessionQuery.pageNo"
                v-model:page-size="sessionQuery.pageSize"
                layout="total, prev, pager, next"
                :total="sessionTotal"
                @current-change="loadSessions"
              />
            </div>
          </el-card>
        </div>
      </el-tab-pane>

      <el-tab-pane label="错题本" name="wrong">
        <el-card shadow="never" class="practice-card">
          <template #header>
            <div class="card-title">
              <span>错题列表</span>
              <el-button link type="primary" @click="loadWrongQuestions">刷新</el-button>
            </div>
          </template>
          <div class="filter-row">
            <CourseSelect v-model="wrongQuery.courseId" source="mine" placeholder="筛选我的课程" @change="reloadWrongQuestions" />
            <el-select v-model="wrongQuery.status" clearable placeholder="错题状态" @change="reloadWrongQuestions">
              <el-option label="待复习" value="ACTIVE" />
              <el-option label="已掌握" value="MASTERED" />
            </el-select>
            <el-button @click="reloadWrongQuestions">查询</el-button>
          </div>
          <el-table v-loading="wrongLoading" :data="wrongQuestions" empty-text="暂无错题" row-key="id">
            <el-table-column prop="name" label="题目" min-width="260" show-overflow-tooltip />
            <el-table-column label="题型" width="110">
              <template #default="{ row }">{{ questionTypeText(row.type) }}</template>
            </el-table-column>
            <el-table-column label="错误次数" width="90" prop="wrongCount" />
            <el-table-column label="复习次数" width="90" prop="reviewCount" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'MASTERED' ? 'success' : 'warning'">{{ row.status === 'MASTERED' ? '已掌握' : '待复习' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }"><el-button link type="primary" @click="openWrongQuestion(row)">重做</el-button></template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="wrongQuery.pageNo"
              v-model:page-size="wrongQuery.pageSize"
              layout="total, prev, pager, next"
              :total="wrongTotal"
              @current-change="loadWrongQuestions"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="sessionVisible" title="练习详情" size="76%" @closed="resetSessionDrawer">
      <template v-if="session">
        <div class="session-summary">
          <div>
            <strong>{{ session.targetName || `练习会话 ${session.id}` }}</strong>
            <p>答题进度：{{ session.answeredCount || 0 }} / {{ session.totalQuestions || 0 }} 题</p>
          </div>
          <el-tag :type="statusType(session.status, 'practice')">{{ statusText(session.status, 'practice') }}</el-tag>
        </div>

        <div v-if="session.status !== 'IN_PROGRESS'" class="score-grid">
          <div><span>当前正式得分</span><strong>{{ session.finalScore ?? 0 }}/{{ session.totalScore ?? 0 }}</strong></div>
          <div><span>客观题得分</span><strong>{{ session.objectiveScore ?? 0 }}/{{ session.objectiveFullScore ?? 0 }}</strong></div>
          <div><span>客观题正确率</span><strong>{{ accuracyText(session.objectiveAccuracy) }}</strong></div>
          <div><span>待完成主观题</span><strong>{{ session.aiReviewPendingCount || 0 }}</strong></div>
        </div>

        <el-alert v-if="session.aiDisclaimer" :title="session.aiDisclaimer" type="warning" :closable="false" show-icon />
        <el-alert v-if="hasAiProcessing" title="练习已提交，系统正在进行 AI 批改，请稍后刷新查看结果。" type="info" :closable="false" show-icon />
        <el-alert v-else-if="hasPendingManualReview" title="AI 已生成主观题批改建议，请等待课程创建者人工确认。" type="warning" :closable="false" show-icon />
        <el-alert v-else-if="hasAiReviewFailure" title="部分主观题 AI 批改失败，可点击下方按钮重试。" type="error" :closable="false" show-icon />
        <el-alert v-else-if="session.status === 'SUBMITTED'" title="练习中的主观题尚未形成正式成绩，请稍后刷新。" type="info" :closable="false" show-icon />

        <div v-for="(question, index) in session.questions || []" :key="question.id" class="question-card">
          <div class="question-title">
            <span class="question-index">{{ index + 1 }}.</span>
            <span class="question-name">{{ question.name }}</span>
            <el-tag size="small">{{ questionTypeText(question.type) }}</el-tag>
            <span class="question-score">{{ question.score }} 分</span>
          </div>

          <el-radio-group
            v-if="Number(question.type) === 1"
            v-model="question.formAnswer"
            :disabled="!canEditSession || submitting"
            @change="saveAnswer(question)"
          >
            <el-radio v-for="(option, optionIndex) in question.options || []" :key="optionIndex" :label="optionIndex">
              {{ optionLabel(optionIndex) }}. {{ option }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group
            v-else-if="[2, 3].includes(Number(question.type))"
            v-model="question.formAnswer"
            :disabled="!canEditSession || submitting"
            @change="saveAnswer(question)"
          >
            <el-checkbox v-for="(option, optionIndex) in question.options || []" :key="optionIndex" :label="optionIndex">
              {{ optionLabel(optionIndex) }}. {{ option }}
            </el-checkbox>
          </el-checkbox-group>

          <el-radio-group
            v-else-if="Number(question.type) === 4"
            v-model="question.formAnswer"
            :disabled="!canEditSession || submitting"
            @change="saveAnswer(question)"
          >
            <el-radio :label="true">正确</el-radio>
            <el-radio :label="false">错误</el-radio>
          </el-radio-group>

          <el-input
            v-else
            v-model="question.formAnswer"
            type="textarea"
            :rows="5"
            maxlength="10000"
            show-word-limit
            :disabled="!canEditSession || submitting"
            placeholder="请输入你的答案"
            @blur="saveAnswer(question)"
          />

          <div v-if="question.saving" class="saving-tip">正在保存答案...</div>
          <div v-if="!canEditSession" class="result-area">
            <div class="result-line"><b>作答状态：</b>{{ answerStatusText(question.answerStatus) }}</div>
            <div class="result-line"><b>学生答案：</b>{{ formatQuestionAnswer(question.studentAnswer, question) }}</div>
            <div v-if="question.standardAnswer" class="result-line"><b>参考答案：</b>{{ formatQuestionAnswer(question.standardAnswer, question) }}</div>
            <div v-if="question.analysis" class="result-line"><b>题目解析：</b>{{ question.analysis }}</div>
            <div v-if="isObjective(question)" class="objective-result">
              <el-tag :type="question.correct ? 'success' : 'danger'">{{ question.correct ? '回答正确' : '回答错误' }}</el-tag>
              <strong>得分：{{ question.actualScore ?? 0 }} / {{ question.score }}</strong>
            </div>
            <div v-else class="ai-review">
              <template v-if="question.answerStatus === 'AI_REVIEW_FAILED'">
                <el-alert :title="question.aiFailureReason || 'AI 批改暂时失败'" type="error" :closable="false" show-icon />
              </template>
              <template v-else-if="question.aiSuggestedScore !== null && question.aiSuggestedScore !== undefined">
                <div class="ai-score-line">
                  <b>AI 建议分</b>{{ question.aiSuggestedScore }} / {{ question.score }}
                  <span>置信度：{{ confidenceText(question.confidence) }}</span>
                  <el-tag v-if="question.manualReviewRecommended" type="warning" size="small">建议重点复核</el-tag>
                </div>
                <div v-if="question.matchedPoints?.length" class="point-line"><b>命中得分点：</b><span v-for="item in question.matchedPoints" :key="item" class="point good">{{ item }}</span></div>
                <div v-if="question.missingPoints?.length" class="point-line"><b>缺失得分点：</b><span v-for="item in question.missingPoints" :key="item" class="point warn">{{ item }}</span></div>
                <div v-if="question.incorrectStatements?.length" class="point-line"><b>问题表述：</b><span v-for="item in question.incorrectStatements" :key="item" class="point bad">{{ item }}</span></div>
                <p class="suggestion"><b>改进建议：</b>{{ question.improvementSuggestion || '暂无' }}</p>
                <div v-if="question.answerStatus === 'MANUALLY_REVIEWED'" class="manual-score">人工确认得分：{{ question.actualScore ?? 0 }} / {{ question.score }}</div>
                <div v-else class="manual-pending">等待人工确认正式得分</div>
              </template>
              <template v-else>
                <span>{{ answerStatusText(question.answerStatus) }}</span>
              </template>
            </div>
          </div>
        </div>

        <div class="session-actions">
          <el-button @click="refreshSession">刷新</el-button>
          <el-button v-if="canRetryReview" type="warning" :loading="retrying" @click="retryReview">重试 AI 批改</el-button>
          <el-button v-if="canEditSession" type="primary" :loading="submitting" @click="submitSession">提交练习</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="wrongVisible" title="错题重做" width="680px" @closed="resetWrongDialog">
      <template v-if="currentWrong">
        <h3 class="wrong-title">{{ currentWrong.name }}</h3>
        <el-tag size="small">{{ questionTypeText(currentWrong.type) }}</el-tag>

        <el-radio-group v-if="Number(currentWrong.type) === 1" v-model="wrongAnswer" class="wrong-control">
          <el-radio v-for="(option, index) in currentWrong.options || []" :key="index" :label="index">
            {{ optionLabel(index) }}. {{ option }}
          </el-radio>
        </el-radio-group>
        <el-checkbox-group v-else-if="[2, 3].includes(Number(currentWrong.type))" v-model="wrongAnswer" class="wrong-control">
          <el-checkbox v-for="(option, index) in currentWrong.options || []" :key="index" :label="index">
            {{ optionLabel(index) }}. {{ option }}
          </el-checkbox>
        </el-checkbox-group>
        <el-radio-group v-else-if="Number(currentWrong.type) === 4" v-model="wrongAnswer" class="wrong-control">
          <el-radio :label="true">正确</el-radio>
          <el-radio :label="false">错误</el-radio>
        </el-radio-group>
        <el-input v-else v-model="wrongAnswer" class="wrong-control" type="textarea" :rows="5" placeholder="请输入你的答案" />

        <el-alert
          v-if="wrongReviewResult"
          :title="wrongReviewResult.correct ? '回答正确，继续保持' : '回答不正确，请结合解析继续复习'"
          :type="wrongReviewResult.correct ? 'success' : 'warning'"
          :closable="false"
          show-icon
        />
        <div v-if="wrongReviewResult" class="wrong-result">
          <p><b>你的答案：</b>{{ formatQuestionAnswer(wrongReviewResult.studentAnswer, currentWrong) }}</p>
          <p><b>标准答案：</b>{{ formatQuestionAnswer(currentWrong.standardAnswer, currentWrong) }}</p>
          <p><b>解析：</b>{{ currentWrong.analysis || '暂无解析' }}</p>
        </div>

        <div v-if="wrongReviewHistory.length" class="review-history">
          <h4>最近重做记录</h4>
          <div v-for="item in wrongReviewHistory" :key="item.id" class="history-item">
            <span>{{ formatDateTime(item.reviewTime) }}</span>
            <span>{{ formatQuestionAnswer(item.studentAnswer, currentWrong) }}</span>
            <el-tag :type="item.correct ? 'success' : 'danger'" size="small">{{ item.correct ? '正确' : '错误' }}</el-tag>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="wrongVisible = false">取消</el-button>
        <el-button type="primary" :loading="wrongSubmitting" @click="submitWrongReview">提交答案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CourseCataloguePicker from './components/CourseCataloguePicker.vue'
import { createClientRequestId } from './requestId'
import CourseSelect from './components/CourseSelect.vue'
import {
  createPracticeSession,
  queryPracticeSessions,
  getPracticeSession,
  savePracticeAnswer,
  submitPracticeSession,
  retryPracticeAiReview,
  queryWrongQuestions,
  queryWrongQuestionReviews,
  reviewWrongQuestion,
  unwrapAiResponse,
  getAiErrorMessage,
} from '@/api/aiEducation'
import {
  answerStatusText,
  parseAnswer,
  serializeAnswer,
  questionTypeText,
  statusText,
  statusType,
  formatDateTime,
} from './dictionaries'

const activeTab = ref('start')
const startForm = reactive({ courseId: '', targetBizId: '', requestId: createClientRequestId('practice') })
const startPickerRef = ref(null)
const sessionQuery = reactive({ pageNo: 1, pageSize: 10, courseId: '', targetBizId: '', status: '' })
const wrongQuery = reactive({ pageNo: 1, pageSize: 10, courseId: '', status: '' })
const sessions = ref([])
const sessionTotal = ref(0)
const sessionLoading = ref(false)
const starting = ref(false)
const submitting = ref(false)
const retrying = ref(false)
const wrongQuestions = ref([])
const wrongTotal = ref(0)
const wrongLoading = ref(false)
const wrongSubmitting = ref(false)
const session = ref(null)
const sessionVisible = ref(false)
const currentWrong = ref(null)
const wrongVisible = ref(false)
const wrongAnswer = ref('')
const wrongReviewResult = ref(null)
const wrongReviewHistory = ref([])
const pendingAnswerSaves = new Map()
const answerSaveStates = new Map()
const answerSaveFailures = new Set()
let sessionListRequestVersion = 0
let sessionDetailRequestVersion = 0
let wrongListRequestVersion = 0
let wrongDetailRequestVersion = 0

/** 是否允许继续编辑练习答案。 */
const canEditSession = computed(() => session.value?.status === 'IN_PROGRESS')
/** 判断当前会话是否仍有正在等待或执行中的 AI 批改任务。 */
const hasAiProcessing = computed(() => (session.value?.questions || []).some(question =>
  ['PENDING_AI_REVIEW', 'AI_REVIEWING'].includes(question.answerStatus)
))
/** 判断当前会话是否存在等待人工确认的主观题。 */
const hasPendingManualReview = computed(() => (session.value?.questions || []).some(question => question.answerStatus === 'AI_REVIEWED'))
/** 判断当前会话是否存在 AI 批改失败的主观题。 */
const hasAiReviewFailure = computed(() => (session.value?.questions || []).some(question => question.answerStatus === 'AI_REVIEW_FAILED'))
/** 判断当前会话是否允许重试 AI 评估。 */
const canRetryReview = computed(() => {
  if (!session.value || session.value.status === 'IN_PROGRESS') return false
  return hasAiReviewFailure.value
    && Number(session.value.aiReviewRetryCount || 0) < Number(session.value.aiReviewMaxRetryCount || 0)
})

/** 将接口响应转换为分页数据。 */
const pageData = response => unwrapAiResponse(response) || { list: [], total: 0 }
/** 统一处理接口成功或失败提示。 */
const ensureSuccess = (response, successMessage, failureMessage = '操作失败') => {
  if (response?.code !== 200) {
    ElMessage.error(getAiErrorMessage(response, failureMessage))
    return false
  }
  ElMessage.success(successMessage)
  return true
}
/** 将选项下标转换为字母。 */
const optionLabel = index => String.fromCharCode(65 + Number(index))
/** 判断题目是否为客观题。 */
const isObjective = question => [1, 2, 3, 4].includes(Number(question?.type))
/** 判断答案是否已填写。 */
const hasAnswer = (answer, type) => {
  if ([2, 3].includes(Number(type))) return Array.isArray(answer) && answer.length > 0
  if (Number(type) === 4) return answer === true || answer === false
  return answer !== null && answer !== undefined && String(answer).trim() !== ''
}
/** 格式化客观题正确率。 */
const accuracyText = value => value === null || value === undefined ? '-' : `${Number(value).toFixed(1)}%`
/** 格式化 AI 评估置信度。 */
const confidenceText = value => value === null || value === undefined ? '-' : `${(Number(value) * 100).toFixed(1)}%`
/** 格式化题目答案。 */
const formatQuestionAnswer = (answer, question) => {
  if (answer === null || answer === undefined || answer === '') return '未作答'
  const type = Number(question?.type)
  if (type === 4) {
    const normalized = String(answer).trim().toLowerCase()
    return ['1', 'true', '正确', '对', '是'].includes(normalized) ? '正确' : '错误'
  }
  if ([1, 2, 3].includes(type)) {
    const indexes = String(answer).split(',').map(item => Number(item.trim())).filter(item => Number.isInteger(item) && item >= 0)
    if (!indexes.length) return String(answer)
    return indexes.map(index => {
      const option = question?.options?.[index]
      return option ? `${optionLabel(index)}. ${option}` : optionLabel(index)
    }).join('、')
  }
  return String(answer)
}
/** 移除查询参数中的空值。 */
const compactQuery = query => Object.fromEntries(Object.entries(query).filter(([, value]) => value !== '' && value !== null && value !== undefined))
/** 加载练习会话列表，并忽略已经过期的筛选请求。 */
const loadSessions = async () => {
  const currentVersion = ++sessionListRequestVersion
  const query = compactQuery({ ...sessionQuery })
  sessionLoading.value = true
  try {
    const response = await queryPracticeSessions(query)
    if (currentVersion !== sessionListRequestVersion) return
    if (response?.code !== 200) return ElMessage.error(getAiErrorMessage(response, '练习记录加载失败'))
    const page = pageData(response)
    sessions.value = page.list || []
    sessionTotal.value = Number(page.total || 0)
  } finally {
    if (currentVersion === sessionListRequestVersion) sessionLoading.value = false
  }
}
/** 将筛选条件重置到第一页。 */
const reloadSessions = async () => {
  sessionQuery.pageNo = 1
  await loadSessions()
}
/**
 * 重置开始练习表单及课程目录选择器。
 */
const resetStartForm = () => {
  Object.assign(startForm, { courseId: '', targetBizId: '', requestId: createClientRequestId('practice') })
  startPickerRef.value?.reset()
}
/** 创建练习会话。 */
const startPractice = async () => {
  if (!startForm.courseId || !startForm.targetBizId) return ElMessage.warning('请选择课程和练习目录')
  starting.value = true
  try {
    const response = await createPracticeSession({
      courseId: String(startForm.courseId),
      targetBizId: String(startForm.targetBizId),
      requestId: startForm.requestId,
    })
    const id = unwrapAiResponse(response)
    if (!id) return ElMessage.error(getAiErrorMessage(response, '练习会话创建失败'))
    ElMessage.success('练习会话创建成功')
    await openSession(id)
    await loadSessions()
  } finally {
    starting.value = false
  }
}
/** 打开练习会话详情，并避免较早打开的会话覆盖当前会话。 */
const openSession = async id => {
  const currentVersion = ++sessionDetailRequestVersion
  if (pendingAnswerSaves.size) {
    await Promise.all(Array.from(pendingAnswerSaves.values()))
  }
  if (currentVersion !== sessionDetailRequestVersion) return
  answerSaveStates.clear()
  answerSaveFailures.clear()
  const response = await getPracticeSession(id)
  if (currentVersion !== sessionDetailRequestVersion) return
  const detail = unwrapAiResponse(response)
  if (!detail) return ElMessage.error(getAiErrorMessage(response, '练习详情加载失败'))
  ;(detail.questions || []).forEach(question => {
    question.formAnswer = parseAnswer(question.studentAnswer, Number(question.type))
    question.saving = false
  })
  if (currentVersion !== sessionDetailRequestVersion) return
  session.value = detail
  sessionVisible.value = true
}
/** 刷新当前练习会话。 */
const refreshSession = async () => {
  const sessionId = session.value?.id
  if (!sessionId) return
  await openSession(sessionId)
  await loadSessions()
}
/** 关闭练习详情抽屉时，使未完成的详情请求失效并清理页面状态。 */
const resetSessionDrawer = () => {
  sessionDetailRequestVersion += 1
  session.value = null
  answerSaveStates.clear()
  answerSaveFailures.clear()
}
/**
 * 保存单题答案，并按修改版本串行写入最新答案。
 *
 * 同一道题在上一次请求完成前再次发生修改时，不会丢弃新答案；当前请求完成后会继续保存最新版本。
 *
 * @param {Object} question 当前练习题
 * @returns {Promise<boolean>} 最新答案是否保存成功
 */
const saveAnswer = question => {
  if (!session.value || !canEditSession.value) return Promise.resolve(false)

  const sessionId = String(session.value.id)
  const questionId = String(question.id)
  const saveKey = `${sessionId}:${questionId}`
  const latestAnswer = serializeAnswer(question.formAnswer, Number(question.type))
  let saveState = answerSaveStates.get(saveKey)
  if (!saveState) {
    saveState = { version: 0, savedVersion: 0, latestAnswer: '', promise: null }
    answerSaveStates.set(saveKey, saveState)
  }
  saveState.version += 1
  saveState.latestAnswer = latestAnswer
  if (saveState.promise) return saveState.promise

  question.saving = true
  const savePromise = (async () => {
    try {
      while (saveState.savedVersion < saveState.version) {
        const savingVersion = saveState.version
        const savingAnswer = saveState.latestAnswer
        const response = await savePracticeAnswer(sessionId, question.id, { answer: savingAnswer })
        if (response?.code !== 200) {
          answerSaveFailures.add(saveKey)
          ElMessage.error(getAiErrorMessage(response, `第 ${question.sequenceNo || ''} 题答案保存失败`))
          return false
        }
        saveState.savedVersion = savingVersion
        question.studentAnswer = savingAnswer
        answerSaveFailures.delete(saveKey)
      }
      if (String(session.value?.id) === sessionId) {
        session.value.answeredCount = (session.value.questions || [])
          .filter(item => hasAnswer(item.formAnswer, item.type)).length
      }
      return true
    } finally {
      question.saving = false
      saveState.promise = null
      if (pendingAnswerSaves.get(saveKey) === savePromise) pendingAnswerSaves.delete(saveKey)
      answerSaveStates.delete(saveKey)
    }
  })()
  saveState.promise = savePromise
  pendingAnswerSaves.set(saveKey, savePromise)
  return savePromise
}
/**
 * 等待当前会话的答案保存完成，任一题保存失败时阻止提交。
 *
 * @returns {Promise<boolean>} 是否可以继续提交
 */
const waitForAnswerSaves = async () => {
  const saves = Array.from(pendingAnswerSaves.values())
  if (saves.length) await Promise.all(saves)
  if (!answerSaveFailures.size) return true
  ElMessage.error('存在未保存成功的答案，请重新作答或稍后重试')
  return false
}
/**
 * 提交前补保存当前表单中尚未写入服务端的答案，避免焦点仍停留在主观题输入框时漏保存。
 *
 * @returns {Promise<boolean>} 是否所有变更都已保存
 */
const flushAnswerChanges = async () => {
  if (!session.value || !canEditSession.value) return true
  const saves = (session.value.questions || [])
    .filter(question => {
      const currentAnswer = serializeAnswer(question.formAnswer, Number(question.type))
      const savedAnswer = serializeAnswer(parseAnswer(question.studentAnswer, Number(question.type)), Number(question.type))
      return currentAnswer !== savedAnswer
    })
    .map(question => saveAnswer(question))
  if (saves.length) await Promise.all(saves)
  return waitForAnswerSaves()
}
/** 提交练习并触发 AI 评估。 */
const submitSession = async () => {
  if (!session.value || !canEditSession.value || submitting.value) return
  const unanswered = (session.value.questions || []).filter(question => !hasAnswer(question.formAnswer, question.type)).length
  const message = unanswered > 0
    ? `还有 ${unanswered} 道题未作答，确定要提交吗？`
    : '确认提交练习吗？'
  const confirmed = await ElMessageBox.confirm(message, '提交确认', { type: 'warning' }).then(() => true).catch(() => false)
  if (!confirmed) return
  if (!await flushAnswerChanges()) return
  submitting.value = true
  try {
    const response = await submitPracticeSession(session.value.id)
    const detail = unwrapAiResponse(response)
    if (!detail) return ElMessage.error(getAiErrorMessage(response, '练习提交失败'))
    ElMessage.success(detail.status === 'SUBMITTED' ? '练习已提交，正在进行 AI 批改' : '练习已完成')
    await openSession(detail.id)
    await loadSessions()
  } finally {
    submitting.value = false
  }
}
/** 重试当前练习的 AI 评估。 */
const retryReview = async () => {
  if (!session.value || !canRetryReview.value || retrying.value) return
  retrying.value = true
  try {
    const response = await retryPracticeAiReview(session.value.id)
    if (ensureSuccess(response, '已重新发起 AI 批改', 'AI 批改重试失败')) {
      await openSession(session.value.id)
      await loadSessions()
    }
  } finally {
    retrying.value = false
  }
}
/** 加载错题列表，并忽略已经过期的筛选请求。 */
const loadWrongQuestions = async () => {
  const currentVersion = ++wrongListRequestVersion
  const query = compactQuery({ ...wrongQuery })
  wrongLoading.value = true
  try {
    const response = await queryWrongQuestions(query)
    if (currentVersion !== wrongListRequestVersion) return
    if (response?.code !== 200) return ElMessage.error(getAiErrorMessage(response, '错题列表加载失败'))
    const page = pageData(response)
    wrongQuestions.value = page.list || []
    wrongTotal.value = Number(page.total || 0)
  } finally {
    if (currentVersion === wrongListRequestVersion) wrongLoading.value = false
  }
}
/** 将错题筛选条件重置到第一页。 */
const reloadWrongQuestions = async () => {
  wrongQuery.pageNo = 1
  await loadWrongQuestions()
}
/** 打开错题重做窗口并加载历史记录。 */
const openWrongQuestion = async row => {
  const wrongId = row?.id
  if (!wrongId) return
  const currentVersion = ++wrongDetailRequestVersion
  currentWrong.value = row
  wrongAnswer.value = parseAnswer('', Number(row.type))
  wrongReviewResult.value = null
  wrongReviewHistory.value = []
  wrongVisible.value = true
  const response = await queryWrongQuestionReviews(wrongId)
  if (currentVersion !== wrongDetailRequestVersion
    || !wrongVisible.value
    || String(currentWrong.value?.id) !== String(wrongId)) return
  if (response?.code !== 200) return ElMessage.error(getAiErrorMessage(response, '重做记录加载失败'))
  wrongReviewHistory.value = unwrapAiResponse(response) || []
}
/** 重置错题重做窗口。 */
const resetWrongDialog = () => {
  wrongDetailRequestVersion += 1
  currentWrong.value = null
  wrongAnswer.value = ''
  wrongReviewResult.value = null
  wrongReviewHistory.value = []
}
/** 提交错题重做答案。 */
const submitWrongReview = async () => {
  const wrongId = currentWrong.value?.id
  if (!wrongId || wrongSubmitting.value) return
  if (!hasAnswer(wrongAnswer.value, currentWrong.value.type)) return ElMessage.warning('请先完成作答')
  wrongSubmitting.value = true
  try {
    const answer = serializeAnswer(wrongAnswer.value, Number(currentWrong.value.type))
    const response = await reviewWrongQuestion(wrongId, { answer })
    const result = unwrapAiResponse(response)
    if (!result) return ElMessage.error(getAiErrorMessage(response, '错题重做失败'))
    if (!wrongVisible.value || String(currentWrong.value?.id) !== String(wrongId)) return
    wrongReviewResult.value = result
    wrongReviewHistory.value = [result, ...wrongReviewHistory.value.filter(item => item.id !== result.id)]
    ElMessage.success(result.correct ? '回答正确，已掌握该知识点' : '回答不正确，请继续复习')
    await loadWrongQuestions()
  } finally {
    wrongSubmitting.value = false
  }
}
/** 切换练习页面标签并按需加载数据。 */
const handleTabChange = name => {
  if (name === 'start' && !sessions.value.length) loadSessions()
  if (name === 'wrong' && !wrongQuestions.value.length) loadWrongQuestions()
}
/** 初始化练习页面。 */
onMounted(() => {
  loadSessions()
})
/** 离开练习页面时使未完成的列表和详情请求失效。 */
onBeforeUnmount(() => {
  sessionListRequestVersion += 1
  sessionDetailRequestVersion += 1
  wrongListRequestVersion += 1
  wrongDetailRequestVersion += 1
})
</script>

<style scoped lang="scss">
.practice-page { padding: 28px 0 70px; }
.practice-header { display: flex; justify-content: space-between; align-items: center; padding: 30px 38px; color: #fff; border-radius: 16px; background: linear-gradient(135deg, #2b6cb0, #22a18c); }
.eyebrow { font-size: 12px; letter-spacing: 2px; opacity: .75; }
.practice-header h1 { margin: 10px 0; font-size: 30px; }
.practice-header p { max-width: 760px; margin: 0; opacity: .9; line-height: 1.8; }
.practice-grid { display: grid; grid-template-columns: minmax(360px, .8fr) minmax(620px, 1.5fr); gap: 20px; }
.practice-card { margin-top: 20px; border: 0; border-radius: 12px; }
.start-card { align-self: start; }
.card-title { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.start-form { margin-top: 20px; }
.filter-row { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 16px; }
.filter-row .el-input { width: 160px; }
.filter-row .el-select { width: 220px; }
.pager { display: flex; justify-content: center; margin-top: 18px; }
.session-summary { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.session-summary strong { font-size: 20px; }
.session-summary p { margin: 8px 0 0; color: #89939d; }
.score-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.score-grid > div { padding: 14px; border-radius: 8px; background: #f5f8fb; }
.score-grid span { display: block; margin-bottom: 8px; color: #7a8793; font-size: 13px; }
.score-grid strong { font-size: 20px; color: #283845; }
.question-card { margin: 16px 0; padding: 20px; border: 1px solid #e7edf3; border-radius: 10px; }
.question-title { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 18px; font-size: 16px; font-weight: 600; line-height: 1.7; }
.question-index { flex: 0 0 auto; }
.question-name { flex: 1; }
.question-score { flex: 0 0 auto; color: #89939d; font-size: 13px; font-weight: normal; }
.question-card :deep(.el-radio-group), .question-card :deep(.el-checkbox-group), .wrong-control { display: flex; flex-direction: column; gap: 12px; align-items: flex-start; }
.saving-tip { margin-top: 8px; color: #409eff; font-size: 12px; }
.result-area { margin-top: 16px; padding: 14px; background: #f7f9fb; line-height: 1.8; color: #5f6a75; }
.result-line { margin: 5px 0; }
.objective-result { display: flex; align-items: center; gap: 12px; margin-top: 12px; }
.ai-review { margin-top: 12px; color: #5d526b; }
.ai-score-line { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; color: #72539c; }
.point-line { margin-top: 10px; }
.point { display: inline-block; margin: 3px 4px; padding: 3px 8px; border-radius: 4px; font-size: 12px; }
.point.good { color: #2c8b63; background: #eaf7f0; }
.point.warn { color: #a06c1d; background: #fff5df; }
.point.bad { color: #b64d4d; background: #fff0f0; }
.suggestion { margin: 10px 0; padding: 8px 10px; background: #fff; border-radius: 5px; }
.manual-score { color: #25825d; font-weight: 600; }
.manual-pending { color: #b5771d; }
.session-actions { position: sticky; bottom: 0; padding: 16px 0; text-align: right; background: #fff; border-top: 1px solid #eef1f5; }
.wrong-title { line-height: 1.7; }
.wrong-control { margin: 18px 0; }
.wrong-result { margin-top: 14px; padding: 12px 14px; background: #f7f9fb; border-radius: 8px; line-height: 1.8; }
.review-history { margin-top: 18px; }
.history-item { display: grid; grid-template-columns: 160px 1fr 60px; gap: 12px; align-items: center; padding: 9px 0; border-bottom: 1px solid #eef1f5; }
@media (max-width: 1100px) {
  .practice-grid { grid-template-columns: 1fr; }
  .score-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>



