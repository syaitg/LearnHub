<!-- 我的练习/测试 -->
<template>
  <div class="myExamDetails">
    <BreadCrumb />
    <div class="examHeadle" v-if="session">
      <div class="tit">练习/测试详情</div>
      <div class="table">
        <div class="fx-sb">
          <div class="td fx-1">
            <div class="marg-bt-10 ft-wt-600 ft-cl-1">所属课程</div>
            <div>{{ courseName }}</div>
          </div>
          <div class="td fx-1">
            <div class="marg-bt-10 ft-wt-600 ft-cl-1">练习/测试</div>
            <div>{{ session.targetName || '--' }}</div>
          </div>
          <div class="td fx-1">
            <div class="marg-bt-10 ft-wt-600 ft-cl-1">学员名称</div>
            <div>{{ store.getUserInfo?.name || '--' }}</div>
          </div>
        </div>
        <div class="fx-sb">
          <div class="td fx-1">
            <div class="marg-bt-10 ft-wt-600 ft-cl-1">答题进度</div>
            <div>{{ session.answeredCount || 0 }} / {{ session.totalQuestions || 0 }}</div>
          </div>
          <div class="td fx-1">
            <div class="marg-bt-10 ft-wt-600 ft-cl-1">提交时间</div>
            <div>{{ formatDateTime(session.submittedTime) }}</div>
          </div>
          <div class="td fx-1">
            <div class="marg-bt-10 ft-wt-600 ft-cl-1">得分</div>
            <div>{{ session.status === 'IN_PROGRESS' ? '--' : `${session.finalScore || 0} / ${session.totalScore || 0}` }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="answerCardTitle" v-if="questions.length">答题卡</div>
    <div class="answerCards">
      <span
        v-for="(item, index) in questions"
        :key="item.id"
        :class="{ right: item.correct === true, wrong: item.correct === false && hasAnswer(item.studentAnswer) }"
      >{{ index + 1 }}</span>
    </div>

    <div class="examCont">
      <div class="item" v-for="(item, index) in questions" :key="item.id">
        <div class="examTitle">
          <div>
            <img v-if="item.correct === true" src="@/assets/icon_right.png" alt="正确">
            <img v-else-if="item.correct === false" src="@/assets/icon_wrong.png" alt="错误">
          </div>
          <div class="quest fx">
            {{ index + 1 }}. <span v-html="item.name"></span>
          </div>
        </div>
        <div class="answer" v-if="item.options?.length">
          <li v-for="(option, optionIndex) in item.options" :key="optionIndex">
            <span>{{ String.fromCharCode(65 + optionIndex) }}. </span><span v-html="option"></span>
          </li>
        </div>
        <div class="analysis">
          <div class="fx marg-bt-20">
            <div class="col ft-wt-600">你的答案：{{ answerChange(item.type, item.studentAnswer) }}</div>
            <div class="col rt ft-wt-600">正确答案：{{ answerChange(item.type, item.standardAnswer) }}</div>
            <div class="col">难易程度：{{ difficultyText(item.difficulty) }}</div>
            <div>得分：{{ item.actualScore ?? '--' }} / {{ item.score || 0 }}</div>
          </div>
          <div class="fx" v-if="item.analysis">答案解析：<span v-html="item.analysis"></span></div>
          <div class="fx" v-if="item.improvementSuggestion">改进建议：{{ item.improvementSuggestion }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store'
import { getExamDetails } from '@/api/class.js'
import { getClassDetails } from '@/api/classDetails.js'
import { difficultyText, formatDateTime } from '@/pages/aiEducation/dictionaries'
import BreadCrumb from './components/BreadCrumb.vue'

const store = useUserStore()
const route = useRoute()
const session = ref(null)
const questions = ref([])
const courseName = ref(route.query.courseName || '')

const getExamDetailsData = async () => {
  if (!route.query.id) {
    ElMessage.error('缺少练习会话 ID')
    return
  }
  try {
    const res = await getExamDetails(route.query.id)
    if (res.code !== 200 || !res.data) {
      ElMessage.error(res.msg || '练习详情加载失败')
      return
    }
    session.value = res.data
    questions.value = res.data.questions || []

    if (!courseName.value && res.data.courseId) {
      try {
        const courseRes = await getClassDetails(res.data.courseId)
        courseName.value = courseRes?.code === 200 ? courseRes.data?.name : ''
      } catch (error) {
        console.warn('课程名称加载失败', error)
      }
    }
    if (!courseName.value) {
      courseName.value = res.data.courseId ? `课程 ${res.data.courseId}` : '--'
    }
  } catch (error) {
    ElMessage.error(error?.message || '练习详情加载失败')
  }
}

onMounted(getExamDetailsData)

const hasAnswer = value => value !== null && value !== undefined && String(value).trim() !== ''

// 题型：1 单选，2 多选，3 不定项选择，4 判断，5 主观
const answerChange = (type, value) => {
  if (!hasAnswer(value)) return '未作答'
  const normalizedType = Number(type)
  if (normalizedType === 4) {
    return ['1', 'true', '正确', '对', '是'].includes(String(value).trim().toLowerCase()) ? '正确' : '错误'
  }
  if ([1, 2, 3].includes(normalizedType)) {
    const indexes = String(value).split(',').map(item => Number(item.trim()))
    if (indexes.some(index => !Number.isInteger(index) || index < 0)) return String(value)
    return indexes.map(index => String.fromCharCode(65 + index)).join('、')
  }
  return String(value)
}
</script>
<style lang="scss" src="./index.scss"> </style>
