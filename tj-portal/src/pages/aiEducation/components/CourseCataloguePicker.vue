<template>
  <div class="catalogue-picker">
    <el-form-item label="课程" class="is-required">
      <div class="course-field">
        <el-select
          v-if="!manualMode"
          v-model="courseIdInput"
          filterable
          :remote="courseSource !== 'mine'"
          :remote-method="searchCourses"
          :loading="courseLoading"
          :disabled="courseReadonly"
          clearable
          reserve-keyword
          placeholder="请选择课程，可输入课程名称搜索"
          @visible-change="handleCourseSelectVisible"
          @change="handleCourseChange"
        >
          <el-option
            v-for="course in displayedCourses"
            :key="course.id"
            :label="courseOptionLabel(course)"
            :value="course.id"
          />
        </el-select>
        <div v-else class="manual-course-field">
          <el-input
            v-model="courseIdInput"
            placeholder="请输入课程 ID"
            clearable
            @keyup.enter="loadCatalogues"
          />
          <el-button type="primary" plain :loading="loading" @click="loadCatalogues">加载目录</el-button>
        </div>
        <el-button v-if="!courseReadonly" link type="primary" class="mode-button" @click="toggleManualMode">
          {{ manualMode ? '返回课程选择' : '手工输入 ID' }}
        </el-button>
      </div>
    </el-form-item>
    <el-alert
      v-if="courseListError && !manualMode"
      :title="courseListError"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-alert v-if="loaded && !chapters.length" title="该课程暂无可用目录" type="warning" :closable="false" show-icon />
    <el-alert v-if="showTarget && loaded && chapters.length && !targetPracticeCount" title="当前出题范围没有可用的练习/测试发布目录，请先在课程目录中创建练习或测试目录。" type="warning" :closable="false" show-icon />
    <template v-if="chapters.length">
      <el-form-item v-if="showScope" label="出题范围" class="is-required">
        <el-radio-group v-model="scopeTypeValue" @change="handleScopeTypeChange">
          <el-radio v-if="allowSection" label="SECTION">视频小节</el-radio>
          <el-radio v-if="allowChapter" label="CHAPTER">整章内容</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="showScope && scopeTypeValue === 'CHAPTER'" label="章节" class="is-required">
        <el-select v-model="scopeIdValue" filterable placeholder="请选择章节" @change="handleChapterChange">
          <el-option v-for="chapter in chapters" :key="chapter.id" :label="chapter.name" :value="chapter.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="(showScope || showSection) && scopeTypeValue === 'SECTION'" label="视频小节" class="is-required">
        <el-select v-model="scopeIdValue" filterable placeholder="请选择小节" @change="handleSectionChange">
          <el-option-group v-for="chapter in chapters" :key="chapter.id" :label="chapter.name">
            <el-option v-for="section in videoSections(chapter)" :key="section.id" :label="section.name" :value="section.id" />
          </el-option-group>
        </el-select>
      </el-form-item>
      <el-form-item v-if="showMedia" label="媒资" class="is-required">
        <el-select v-model="mediaIdValue" filterable placeholder="请先选择包含视频的小节" @change="handleMediaChange">
          <el-option v-for="section in selectableMediaSections" :key="section.mediaId" :label="`${section.name}（媒资 ${section.mediaId}）`" :value="section.mediaId" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showTarget" label="发布目录" class="is-required">
        <el-select
          v-model="targetBizIdValue"
          filterable
          :disabled="!targetPracticeCount"
          :placeholder="targetPracticeCount ? '请选择练习或测试目录' : '当前范围暂无练习/测试目录'"
          @change="emitValues"
        >
          <el-option-group v-for="chapter in selectableTargetChapters" :key="chapter.id" :label="chapter.name">
            <el-option v-for="practice in practiceSections(chapter)" :key="practice.id" :label="practice.name" :value="practice.id" />
          </el-option-group>
        </el-select>
      </el-form-item>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAiErrorMessage, queryCourseCatalogues, unwrapAiResponse } from '@/api/aiEducation'
import { loadCourseOptions } from '../courseOptions'

const props = defineProps({
  courseId: [String, Number],
  scopeType: { type: String, default: 'SECTION' },
  scopeId: [String, Number],
  targetBizId: [String, Number],
  mediaId: [String, Number],
  courseReadonly: { type: Boolean, default: false },
  courseSource: { type: String, default: 'managed' },
  showScope: { type: Boolean, default: true },
  showTarget: { type: Boolean, default: true },
  allowChapter: { type: Boolean, default: true },
  allowSection: { type: Boolean, default: true },
  showSection: { type: Boolean, default: false },
  showMedia: { type: Boolean, default: false },
})
const emit = defineEmits(['update:courseId', 'update:scopeType', 'update:scopeId', 'update:targetBizId', 'update:mediaId', 'loaded'])
const courseIdInput = ref(props.courseId ? String(props.courseId) : '')
const scopeTypeValue = ref(props.allowChapter && !props.allowSection ? 'CHAPTER' : (props.allowChapter ? (props.scopeType || 'SECTION') : 'SECTION'))
const scopeIdValue = ref(props.scopeId || '')
const targetBizIdValue = ref(props.targetBizId || '')
const mediaIdValue = ref(props.mediaId || '')
const chapters = ref([])
const courses = ref([])
const loading = ref(false)
const courseLoading = ref(false)
const loaded = ref(false)
const manualMode = ref(false)
const courseListError = ref('')
const courseSearchTimer = ref(null)
let courseRequestVersion = 0
let catalogueRequestVersion = 0
const allVideoSections = computed(() => chapters.value.flatMap(chapter => videoSections(chapter)))
const selectableMediaSections = computed(() => {
  if (!scopeIdValue.value) return allVideoSections.value
  return allVideoSections.value.filter(section => String(section.id) === String(scopeIdValue.value))
})
/** 根据当前出题范围筛选可用的发布目录所在章节。 */
const selectableTargetChapters = computed(() => {
  if (!scopeIdValue.value) return chapters.value
  if (scopeTypeValue.value === 'CHAPTER') {
    return chapters.value.filter(chapter => String(chapter.id) === String(scopeIdValue.value))
  }
  return chapters.value.filter(chapter => (chapter.sections || [])
    .some(section => Number(section.type) === 2 && String(section.id) === String(scopeIdValue.value)))
})
const targetPracticeCount = computed(() => selectableTargetChapters.value
  .reduce((count, chapter) => count + practiceSections(chapter).length, 0))
const displayedCourses = computed(() => {
  if (!courseIdInput.value || courses.value.some(course => course.id === String(courseIdInput.value))) return courses.value
  return [{ id: String(courseIdInput.value), name: `课程 ${courseIdInput.value}` }, ...courses.value]
})

/** 筛选章节内的视频小节。 */
const videoSections = chapter => (chapter.sections || []).filter(item => Number(item.type) === 2 && item.mediaId)
/** 筛选章节内的练习或测试目录。 */
const practiceSections = chapter => (chapter.sections || []).filter(item => Number(item.type) === 3)
/** 生成课程选择项文案。 */
const courseOptionLabel = course => `${course.name || '未命名课程'}（${course.id}）`
/** 向父组件同步当前选择。 */
const emitValues = () => {
  emit('update:courseId', courseIdInput.value ? String(courseIdInput.value).trim() : '')
  emit('update:scopeType', scopeTypeValue.value)
  emit('update:scopeId', scopeIdValue.value ? String(scopeIdValue.value) : '')
  emit('update:targetBizId', targetBizIdValue.value ? String(targetBizIdValue.value) : '')
  emit('update:mediaId', mediaIdValue.value ? String(mediaIdValue.value) : '')
}
/** 加载共享课程选项，并忽略过期的远程搜索响应。 */
const loadCourses = async (keyword = '') => {
  const currentVersion = ++courseRequestVersion
  courseLoading.value = true
  courseListError.value = ''
  const result = await loadCourseOptions(props.courseSource, keyword)
  if (currentVersion !== courseRequestVersion) return
  courseLoading.value = false
  courses.value = result.list
  courseListError.value = result.error
    ? `${result.error}，请使用手工输入 ID`
    : ''
}
/** 延迟搜索工作台课程，避免连续输入触发过多请求。 */
const searchCourses = keyword => {
  if (props.courseSource === 'mine') return
  if (courseSearchTimer.value) clearTimeout(courseSearchTimer.value)
  courseSearchTimer.value = setTimeout(() => loadCourses(keyword), 300)
}
/** 展开课程选择器时按需加载课程。 */
const handleCourseSelectVisible = visible => {
  if (visible && !courses.value.length && !courseLoading.value) loadCourses()
}
/** 清理课程目录及所有依赖目录的选择值。 */
const clearCatalogueSelection = () => {
  catalogueRequestVersion += 1
  chapters.value = []
  loading.value = false
  loaded.value = false
  scopeIdValue.value = ''
  targetBizIdValue.value = ''
  mediaIdValue.value = ''
}
/** 选择课程后自动加载该课程目录。 */
const handleCourseChange = async courseId => {
  clearCatalogueSelection()
  emitValues()
  if (courseId) await loadCatalogues()
}
/** 切换课程选择和手工输入模式。 */
const toggleManualMode = () => {
  manualMode.value = !manualMode.value
  courseListError.value = ''
  if (!manualMode.value && !courses.value.length) loadCourses()
}
/** 加载课程完整目录。 */
const loadCatalogues = async () => {
  const courseId = courseIdInput.value ? String(courseIdInput.value).trim() : ''
  if (!courseId) return ElMessage.warning(manualMode.value ? '请先输入课程 ID' : '请先选择课程')
  const currentVersion = ++catalogueRequestVersion
  courseIdInput.value = courseId
  loading.value = true
  const response = await queryCourseCatalogues(courseId)
  if (currentVersion !== catalogueRequestVersion || courseId !== String(courseIdInput.value)) return
  loading.value = false
  const data = unwrapAiResponse(response)
  if (!data) return ElMessage.error(getAiErrorMessage(response, '课程目录加载失败'))
  chapters.value = (data || []).filter(item => Number(item.type) === 1)
  loaded.value = true
  emitValues()
  emit('loaded', chapters.value)
}
/** 切换出题范围时清空原有章节或小节选择。 */
const handleScopeTypeChange = () => {
  scopeIdValue.value = ''
  targetBizIdValue.value = ''
  mediaIdValue.value = ''
  emitValues()
}
/** 选择章节后同步当前选择。 */
const handleChapterChange = () => {
  targetBizIdValue.value = ''
  mediaIdValue.value = ''
  emitValues()
}
/** 选择视频小节时自动同步媒资 ID。 */
const handleSectionChange = id => {
  targetBizIdValue.value = ''
  const section = allVideoSections.value.find(item => String(item.id) === String(id))
  mediaIdValue.value = section?.mediaId || ''
  emitValues()
}
/** 选择媒资时同步对应的视频小节 ID，避免小节与媒资不匹配。 */
const handleMediaChange = mediaId => {
  const section = allVideoSections.value.find(item => String(item.mediaId) === String(mediaId))
  if (section) scopeIdValue.value = section.id
  emitValues()
}
/** 清空组件选择；只读课程场景保留当前课程。 */
const reset = () => {
  courseIdInput.value = props.courseReadonly && props.courseId ? String(props.courseId) : ''
  manualMode.value = false
  clearCatalogueSelection()
  scopeTypeValue.value = props.allowChapter && !props.allowSection
    ? 'CHAPTER'
    : (props.allowChapter ? (props.scopeType || 'SECTION') : 'SECTION')
  if (!props.showScope && !props.showSection && props.scopeId) {
    scopeIdValue.value = String(props.scopeId)
  }
  emitValues()
}

watch(() => props.courseId, value => {
  const nextValue = value ? String(value) : ''
  if (nextValue !== String(courseIdInput.value)) {
    courseIdInput.value = nextValue
    clearCatalogueSelection()
  }
})
watch(() => props.scopeType, value => {
  if (props.allowChapter && !props.allowSection) scopeTypeValue.value = 'CHAPTER'
  else if (props.allowChapter) scopeTypeValue.value = value || 'SECTION'
  else scopeTypeValue.value = 'SECTION'
})
watch(() => [props.allowChapter, props.allowSection], ([allowChapter, allowSection]) => {
  if (!allowChapter) scopeTypeValue.value = 'SECTION'
  else if (!allowSection) scopeTypeValue.value = 'CHAPTER'
  emitValues()
})
watch(() => props.scopeId, value => { scopeIdValue.value = value || '' })
watch(() => props.targetBizId, value => { targetBizIdValue.value = value || '' })
watch(() => props.mediaId, value => { mediaIdValue.value = value || '' })
watch(() => props.courseSource, () => {
  courses.value = []
  courseListError.value = ''
  loadCourses()
})
/** 初始化课程选择项。 */
onMounted(() => { loadCourses() })
/** 销毁组件时清理课程搜索定时器。 */
onBeforeUnmount(() => {
  courseRequestVersion += 1
  catalogueRequestVersion += 1
  if (courseSearchTimer.value) clearTimeout(courseSearchTimer.value)
})
defineExpose({ loadCatalogues, loadCourses, reset, chapters, allVideoSections })
</script>

<style scoped>
.course-field { display: flex; align-items: center; gap: 10px; width: 100%; }
.course-field > .el-select { flex: 1; }
.manual-course-field { display: flex; gap: 10px; flex: 1; }
.manual-course-field .el-input { flex: 1; }
.mode-button { flex: 0 0 auto; }
.catalogue-picker :deep(.el-select) { width: 100%; }
.catalogue-picker .el-alert { margin-bottom: 14px; }
</style>