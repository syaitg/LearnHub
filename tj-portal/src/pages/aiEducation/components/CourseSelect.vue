<template>
  <el-select
    :model-value="normalizedValue"
    filterable
    :remote="source !== 'mine'"
    :remote-method="searchCourses"
    :loading="loading"
    clearable
    reserve-keyword
    :placeholder="placeholder"
    @visible-change="handleVisibleChange"
    @change="handleChange"
  >
    <el-option
      v-for="course in displayedCourses"
      :key="course.id"
      :label="courseLabel(course)"
      :value="course.id"
    />
  </el-select>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { loadCourseOptions } from '../courseOptions'

const props = defineProps({
  modelValue: [String, Number],
  source: { type: String, default: 'managed' },
  placeholder: { type: String, default: '请选择课程' },
  showId: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue', 'change'])
const courses = ref([])
const loading = ref(false)
const searchTimer = ref(null)
let requestVersion = 0
const normalizedValue = computed(() => props.modelValue ? String(props.modelValue) : '')
const displayedCourses = computed(() => {
  if (!normalizedValue.value || courses.value.some(item => item.id === normalizedValue.value)) return courses.value
  return [{ id: normalizedValue.value, name: `课程 ${normalizedValue.value}` }, ...courses.value]
})

/** 生成课程下拉项显示文本。 */
const courseLabel = course => props.showId ? `${course.name || '未命名课程'}（${course.id}）` : (course.name || `课程 ${course.id}`)
/** 加载共享课程选项，并忽略过期的远程搜索响应。 */
const loadCourses = async (keyword = '') => {
  const currentVersion = ++requestVersion
  loading.value = true
  const result = await loadCourseOptions(props.source, keyword)
  if (currentVersion !== requestVersion) return
  loading.value = false
  courses.value = result.list
  if (result.error) ElMessage.error(result.error)
}
/** 延迟执行课程远程搜索，避免连续输入触发过多请求。 */
const searchCourses = keyword => {
  if (props.source === 'mine') return
  if (searchTimer.value) clearTimeout(searchTimer.value)
  searchTimer.value = setTimeout(() => loadCourses(keyword), 300)
}
/** 展开选择器时按需加载课程数据。 */
const handleVisibleChange = visible => {
  if (visible && !courses.value.length && !loading.value) loadCourses()
}
/** 向父组件同步课程选择。 */
const handleChange = value => {
  const normalized = value ? String(value) : ''
  emit('update:modelValue', normalized)
  emit('change', normalized)
}

watch(() => props.source, () => {
  courses.value = []
  loadCourses()
})
/** 初始化课程列表。 */
onMounted(() => { loadCourses() })
/** 销毁组件时清理搜索定时器。 */
onBeforeUnmount(() => {
  requestVersion += 1
  if (searchTimer.value) clearTimeout(searchTimer.value)
})
</script>