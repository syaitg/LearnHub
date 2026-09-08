import { getAiErrorMessage, queryManagedCourses, queryMyCourses, unwrapAiResponse } from '@/api/aiEducation'

const courseOptionCache = new Map()
const courseOptionRequests = new Map()
const COURSE_PAGE_SIZE = 100
const MAX_COURSE_PAGES = 100

/** 将不同课程接口的数据转换为统一的选择项结构。 */
const normalizeCourses = list => (list || []).map(item => ({
  id: String(item.courseId || item.id || ''),
  name: item.name || item.courseName || '',
  status: item.status,
})).filter(item => item.id)

/** 按课程 ID 去除重复课程。 */
const uniqueCourses = list => Array.from(new Map(list.map(item => [item.id, item])).values())

/** 查询一个课程状态下的全部分页数据，并保留接口失败信息。 */
const queryManagedCourseStatus = async (status, keyword) => {
  const list = []
  let firstError = ''
  for (let pageNo = 1; pageNo <= MAX_COURSE_PAGES; pageNo += 1) {
    const response = await queryManagedCourses({
      pageNo,
      pageSize: COURSE_PAGE_SIZE,
      status,
      keyword: keyword || undefined,
    })
    const page = unwrapAiResponse(response)
    if (!page) {
      firstError = getAiErrorMessage(response, '课程列表加载失败')
      break
    }
    list.push(...normalizeCourses(page.list))
    const pages = Number(page.pages || 0)
    if (!pages || pageNo >= pages || !(page.list || []).length) break
  }
  return { list, error: firstError }
}

/** 查询当前课程创建者可管理的全部状态课程，避免单页数量造成课程遗漏。 */
const queryAllCourses = async keyword => {
  const results = []
  for (const status of [1, 2, 3, 4]) {
    results.push(await queryManagedCourseStatus(status, keyword))
  }
  const list = uniqueCourses(results.flatMap(result => result.list))
  const errors = results.map(result => result.error).filter(Boolean)
  if (!list.length && errors.length) return { list: [], error: errors[0] }
  return {
    list,
    error: errors.length ? '部分课程状态加载失败，课程列表可能不完整，请稍后刷新重试' : '',
  }
}

/** 查询当前学生已经加入的全部课程，避免课程超过一页后无法选择。 */
const queryStudentCourses = async () => {
  const list = []
  for (let pageNo = 1; pageNo <= MAX_COURSE_PAGES; pageNo += 1) {
    const response = await queryMyCourses({ pageNo, pageSize: COURSE_PAGE_SIZE })
    const page = unwrapAiResponse(response)
    if (!page) {
      if (!list.length) return { list: [], error: getAiErrorMessage(response, '我的课程加载失败') }
      return { list: uniqueCourses(list), error: '部分我的课程加载失败，课程列表可能不完整，请稍后刷新重试' }
    }
    list.push(...normalizeCourses(page.list))
    const pages = Number(page.pages || 0)
    if (!pages || pageNo >= pages || !(page.list || []).length) break
  }
  return { list: uniqueCourses(list), error: '' }
}

/** 加载课程选择项，并在不同页面组件之间共享成功结果和进行中的请求。 */
export const loadCourseOptions = async (source = 'managed', keyword = '') => {
  const normalizedKeyword = source !== 'mine' ? String(keyword || '').trim() : ''
  const cacheKey = `${source}:${normalizedKeyword}`
  if (courseOptionCache.has(cacheKey)) return { list: courseOptionCache.get(cacheKey), error: '' }
  if (courseOptionRequests.has(cacheKey)) return courseOptionRequests.get(cacheKey)

  const requestPromise = (source === 'mine' ? queryStudentCourses() : queryAllCourses(normalizedKeyword))
    .then(result => {
      if (!result.error || result.list.length) courseOptionCache.set(cacheKey, result.list)
      return result
    })
    .finally(() => courseOptionRequests.delete(cacheKey))
  courseOptionRequests.set(cacheKey, requestPromise)
  return requestPromise
}
