<!-- 课程学习 -->
<template>
  <div class="classLearning">
    <div class="videoCont">
      <div class="head fx-sb">
        <div class="fx cur-pt" @click="goBack">
          <img src="@/assets/icon_back.png" alt="">
          <div>返回 <span class="line">|</span> {{ currentPlayData.sectionName }}</div>
        </div>
      </div>
      <div class="videoCont">
        <div class="video" :class="{'video-ready': videoReady}" v-show="pageType == 1" ref="videoContainerRef"></div>
        <Practise v-if="pageType == 2" @playHadle="playHadle" :examInfo="examInfo"
                  :key="currentPlayData.sectionId"></Practise>
      </div>
    </div>
    <!-- 右侧目录、问答、笔记 - start -->
    <div class="learn" :class="{close: isClose}">
      <div class="closeRt cur-pt" :class="{close: isClose}" @click="close"><i class="iconfont zhy-a-shouqi2x"></i></div>
      <div class="teachInfo fx">
        <img @click="() => $router.push({path:'/details/index', query:{id: learningClassDetails.id}})"
          :src="learningClassDetails && learningClassDetails.coverUrl" alt="">
        <div class="">
          <div class="tit">{{learningClassDetails && learningClassDetails.name}}</div>
          <div class="teacher ft-14"> 讲师 : {{learningClassDetails && learningClassDetails.teacherName}}</div>
        </div>
      </div>
      <div class="cont">
        <div class="pd-lr-10">
          <TableSwitchBar :data="tableBar" @changeTable="changeTable"></TableSwitchBar>
        </div>
        <!-- 目录 -->
        <div class="catalogue" v-show="actId == 1" v-infinite-scroll="load" style="overflow: auto">
          <Catalogue :data="chapters" :playId="playId"
                     :finished="finished"   @playHadle="playHadle" @openCatalogue="openCatalogue"></Catalogue>
        </div>
        <!-- 问答 -->
        <div class="question" v-if="actId == 2" v-infinite-scroll="load" style="overflow: auto">
          <Question></Question>
        </div>
        <!-- 笔记 -->
        <div class="note" v-if="actId == 3" v-infinite-scroll="load" style="overflow: auto">
          <Note :currentTime="currentPlayTime"></Note>
        </div>
      </div>
    </div>
    <!-- 右侧弹出问答、笔记 - start -->
    <div class="askAndNote" :class="{close: !isClose}" @click="open">
      <div class="fx-cl-ct">
        <img src="../../assets/btn-wd.png" alt="">
        <p>问答</p>
      </div>
      <div class="fx-cl-ct">
        <img src="../../assets/btn-bj.png" alt="">
        <p>笔记</p>
      </div>
    </div>
  </div>
</template>
<script setup>
/** 数据导入 **/
import { onMounted, ref, onUnmounted, provide, h, reactive, nextTick } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { getMediasSignature, addPlayLog, getLearningClassDetails } from "@/api/class.js";
import { useRoute } from "vue-router";
import { dataCacheStore } from "@/store"
// 组件导入
import TableSwitchBar from "./components/TableSwitchBar.vue";
import Catalogue from "./components/Catalogue.vue";
import Question from "./components/Question.vue";
import Practise from "./components/Practise.vue";
import Note from "./components/Note.vue";
import icon from '@/assets/icon_good.png'

import router from "../../router";

const route = useRoute()
const store = dataCacheStore()

// 主展示区域 1 为视频 2 为练习 3 考试
const pageType = ref(1)
const SECTION_TYPE_VIDEO = 2
const SECTION_TYPE_EXAM = 3
// 结果 - 详情Id
const detailsId = ref({})
// 课程信息及讲师信息
const tableBar = [{id: 1, name: '目录'}, {id: 2, name: '问答'}, {id: 3, name: '笔记'}]
// 课程目录
const classListData = ref([])

const videoRef = ref(null)
const videoContainerRef = ref(null)
const videoReady = ref(false)
let videoElementSequence = 0

/** 方法定义 **/
/*
* 本节有三种模式播放  
* 一、免费课程 - 先点击点击到学习计划然后进入这里学习
* 二、购买的课程 - 购买成功自动加入学习计划到这里学习
* 三、试听 - 无需加入学习计划直接学习
* 先加载当前课程信息
* 然后获取学习计划信息
* 通过课程计划自动调整到对应的小节 - 通过小节Id 获取视频签名（用于视频播放）
* 点击小节 - 通过小节Id 获取视频签名（用于视频播放）
*
*/

// 默认播放小节
const playId = ref('')
// 是否播完
const finished = ref(false);
// 记录播放相关参数
const fileId = ref('')
const signature = ref('')
const playUrl = ref('')
const playAppId = ref('')
let timer = -1;
let playing = false;
let playRequestSequence = 0;
let pageUnmounted = false
const currentPlayTime = ref(0)
const player = ref(null)
// 当前播放小节信息缓存 
const currentPlayData = reactive({
  courseId: route.query.id,
  lastPlaySectionId: '',
  prevSectionId: '',
  sectionId: '',
  sectionName: '',
  nestSectionId: '',
  moment: '',
  duration: '',
  type: '',
  lessonId: null,
  chapterId: null,
})

provide('currentPlayData', currentPlayData)
// 学习页面数据
const learningClassDetails = ref(null)
const chapters = ref([])
const sectionMap = ref({})

// 根据当前小节反查所属章节，保证问答和笔记提交时携带正确的章ID。
const updateCurrentChapter = (sectionId) => {
  const chapter = chapters.value.find(item =>
    Array.isArray(item?.sections) && item.sections.some(section =>
      String(section?.id) === String(sectionId)
    )
  )
  currentPlayData.chapterId = chapter?.id ?? null
}

onMounted(async () => {
  pageUnmounted = false
  detailsId.value = route.query.id
  const loaded = await getLearningClassDetailsData()
  if (!loaded || !learningClassDetails.value || !currentPlayData.sectionId) return

  // 根据上次学习位置恢复页面
  const sectionType = Number(currentPlayData.type)
  if (sectionType === SECTION_TYPE_VIDEO) {
    pageType.value = 1
    await getMediasSignatureData(currentPlayData.sectionId)
  } else if (sectionType === SECTION_TYPE_EXAM) {
    startExaminationHandle({
      id: currentPlayData.sectionId,
      type: sectionType,
      name: currentPlayData.sectionName,
    })
  } else {
    ElMessage.warning('暂不支持该小节类型')
  }
})

const getLearningClassDetailsData = async () => {
  try {
    const res = await getLearningClassDetails(detailsId.value)
    if (res.code !== 200 || !res.data) {
      ElMessage.error(res.msg || "课程信息加载失败")
      return false
    }
    const data = res.data
    const courseChapters = Array.isArray(data.chapters) ? data.chapters : []
    sectionMap.value = {}
    courseChapters.forEach(chapter => {
      const sections = Array.isArray(chapter.sections) ? chapter.sections : []
      chapter.sections = sections
      sections.forEach(section => {
        sectionMap.value[section.id] = section
        section.hasTest = !!section.subjectNum
      })
    })

    const requestedSectionId = route.query.sectionId || data.latestSectionId
    const firstSection = courseChapters
      .flatMap(chapter => chapter.sections)
      .find(Boolean)
    const section = sectionMap.value[requestedSectionId] || firstSection
    if (!section) {
      learningClassDetails.value = data
      chapters.value = courseChapters
      ElMessage.error("课程暂无可学习的小节")
      return false
    }

    data.latestSectionMoment = section.moment
    data.latestSectionName = section.name
    learningClassDetails.value = data
    chapters.value = courseChapters
    currentPlayData.duration = section.mediaDuration || 0
    currentPlayData.sectionId = section.id
    updateCurrentChapter(section.id)
    currentPlayData.moment = section.moment || 0
    currentPlayData.sectionName = section.name || ''
    currentPlayData.type = section.type
    currentPlayData.lessonId = data.lessonId
    playId.value = currentPlayData.sectionId || ''
    store.setCurrentPlayData(currentPlayData)
    return true
  } catch (e) {
    ElMessage.error("课程信息请求失败，请稍后重试")
    return false
  }
}

const stopPlaySession = () => {
  if (timer !== -1) {
    window.clearInterval(timer)
    timer = -1
  }
  playing = false
}

// 由播放器逻辑独立创建 video 节点，避免 Vue 和 TCPlayer 同时修改同一个 DOM。
const createVideoElement = async () => {
  await nextTick()
  const container = videoContainerRef.value
  videoReady.value = false
  if (pageUnmounted || !container || !container.isConnected) return null
  while (container.firstChild) {
    container.removeChild(container.firstChild)
  }
  const element = document.createElement('video')
  element.id = `videoRef-${++videoElementSequence}`
  element.setAttribute('playsinline', '')
  element.setAttribute('webkit-playsinline', '')
// 播放器初始化处理。
  element.style.position = 'absolute'
  element.style.inset = '0'
  element.style.display = 'block'
  element.style.width = '100%'
  element.style.height = '100%'
  element.style.opacity = '1'
  container.appendChild(element)
  videoRef.value = element
  return element
}

// 销毁旧播放器后，在稳定的容器中重新创建 video 节点。
const disposePlayer = async () => {
  const instance = player.value
  videoReady.value = false
  player.value = null
  videoRef.value = null
  if (instance) {
    try {
      instance.pause()
    } catch (e) {
      // 播放器已经暂停或销毁时无需重复处理。
    }
    try {
      instance.dispose()
    } catch (e) {
      // 播放器已经销毁时无需重复处理。
    }
  }
  if (pageUnmounted) return null
  return createVideoElement()
}

const getSourceType = (url) => /\.m3u8(?:$|\?)/i.test(url || '')
  ? 'application/x-mpegURL'
  : 'video/mp4'

// 等待新视频加载完成后再定位播放时间，避免切换后播放器只有黑屏。
const playWhenReady = (instance, sectionId, requestId, moment) => {
  let handled = false
  let timeout = 0
  const play = () => {
    if (handled) return
    if (pageUnmounted || player.value !== instance
        || requestId !== playRequestSequence
        || String(currentPlayData.sectionId) !== String(sectionId)) return
    handled = true
    window.clearTimeout(timeout)
    try {
      instance.currentTime(Number(moment || 0))
      const playResult = instance.play()
      if (playResult && typeof playResult.catch === 'function') {
        playResult.catch(() => {})
      }
    } catch (e) {
      stopPlaySession()
    }
  }
  instance.one('loadedmetadata', play)
  instance.one('canplay', play)
  timeout = window.setTimeout(play, 2000)
}

// 播放器初始化处理。
const waitForTCPlayer = async () => {
  for (let index = 0; index < 50; index += 1) {
    const constructor = globalThis.TCPlayer
    if (typeof constructor === 'function') return constructor
    await new Promise(resolve => window.setTimeout(resolve, 100))
  }
  return null
}

const initPlay = async (fileID, psign, url, requestId = playRequestSequence) => {
  const videoElement = videoRef.value
  if (pageUnmounted || !videoElement || !videoElement.isConnected) return null
  const sectionIdSnapshot = currentPlayData.sectionId
  const lessonIdSnapshot = currentPlayData.lessonId
  const durationSnapshot = Number(currentPlayData.duration || 0)
  const options = {
    posterImage: true,
    autoplay: false,
    preload: 'auto',
    hlsConfig: {},
  }
  if (url) {
    options.sources = [{ src: url, type: getSourceType(url) }]
  } else {
    options.appID = playAppId.value
    options.fileID = fileID
    options.psign = psign
  }
  const TCPlayerConstructor = await waitForTCPlayer()
  if (!TCPlayerConstructor || pageUnmounted || requestId !== playRequestSequence) {
    if (requestId === playRequestSequence && !pageUnmounted) {
      ElMessage.error('视频播放器资源尚未加载完成，请刷新页面后重试')
    }
    return null
  }
  const instance = new TCPlayerConstructor(videoElement, options)
  videoReady.value = true
  player.value = instance
  instance.on('timeupdate', () => {
    if (player.value !== instance || String(sectionIdSnapshot) !== String(currentPlayData.sectionId)) return
    const moment = Number(instance.currentTime() || 0)
    currentPlayData.moment = moment
    currentPlayTime.value = moment
  })
  instance.on('pause', () => {
    if (player.value === instance) stopPlaySession()
  })
  instance.on('play', () => {
    if (player.value !== instance || playing || String(sectionIdSnapshot) !== String(currentPlayData.sectionId)) return
    playing = true
    finished.value = false
    if (!lessonIdSnapshot) return
    addPlayLogHandle(sectionIdSnapshot, lessonIdSnapshot, instance, durationSnapshot)
    timer = window.setInterval(() => {
      addPlayLogHandle(sectionIdSnapshot, lessonIdSnapshot, instance, durationSnapshot)
    }, 15000)
  })
  instance.on('error', () => {
    if (player.value === instance) {
      stopPlaySession()
      ElMessage.error('视频播放失败，请刷新页面后重试')
    }
  })
  instance.on('ended', () => {
    if (player.value !== instance) return
    stopPlaySession()
    finished.value = true
  })
  let readyHandled = false
  const handlePlayerReady = () => {
    if (readyHandled || player.value !== instance) return
    readyHandled = true
    videoReady.value = true
    playWhenReady(instance, sectionIdSnapshot, requestId, currentPlayData.moment)
  }
  instance.ready(handlePlayerReady)
  // 播放器就绪后开始播放。
  window.setTimeout(handlePlayerReady, 1500)
  return instance
}

onUnmounted(() => {
  addPlayLogHandle(
    currentPlayData.sectionId,
    currentPlayData.lessonId,
    player.value,
    currentPlayData.duration,
    true
  )
  pageUnmounted = true
  ++playRequestSequence
  stopPlaySession()
  disposePlayer()
})

const load = () => {}
const t = (n) => {
  return n < 10 ? '0'+n : n;
}
const now = () => {
  let d = new Date();
  return d.getFullYear() + "-" + t(d.getMonth() + 1) + "-" +t(d.getDate()) +
      " " + t(d.getHours()) + ":" + t(d.getMinutes())+":" + t(d.getSeconds());
}

// 播放新的小节的时候提交相关记录
const addPlayLogHandle = (
  sectionIdSnapshot,
  lessonIdSnapshot,
  playerSnapshot = player.value,
  durationSnapshot = 0,
  force = false
) => {
  const sectionId = sectionIdSnapshot ?? currentPlayData.sectionId
  const lessonId = lessonIdSnapshot ?? currentPlayData.lessonId
  if ((!force && pageUnmounted) || !sectionId || !lessonId || String(sectionId) !== String(currentPlayData.sectionId)
      || (playerSnapshot && player.value !== playerSnapshot)) return
  const moment = Number(playerSnapshot?.currentTime?.() ?? currentPlayData.moment ?? 0)
  const playerDuration = Number(playerSnapshot?.duration?.() ?? 0)
  const duration = playerDuration > 0 ? playerDuration : Number(durationSnapshot || currentPlayData.duration || 0)
  addPlayLog({lessonId, sectionId, moment, duration, sectionType: 1, commitTime: now()})
      .catch(err => console.warn("播放记录提交失败", err))
};

const getMediasSignatureData = async (sectionId, requestId = ++playRequestSequence) => {
  try {
    const res = await getMediasSignature({sectionId})
    if (pageUnmounted || requestId !== playRequestSequence) return null
    if (res.code !== 200 || !res.data) {
      const message = Number(res.code) === 403
        ? (res.msg || '当前课程尚未报名，只能播放试看小节')
        : (res.msg || '获取视频播放信息失败')
      ElMessage.error(message)
      return null
    }
    const data = res.data
    if (!data.playUrl && (!data.fileId || !data.signature || !data.appId)) {
      ElMessage.error('课程信息请求失败，请稍后重试')
      return null
    }
    fileId.value = data.fileId || ''
    signature.value = data.signature || ''
    playUrl.value = data.playUrl || ''
    playAppId.value = data.appId || ''

    if (player.value == null) {
      const element = videoRef.value?.isConnected
        ? videoRef.value
        : await createVideoElement()
      if (pageUnmounted || requestId !== playRequestSequence || !element?.isConnected) return null
      const instance = await initPlay(fileId.value, signature.value, playUrl.value, requestId)
      return instance ? data : null
    }

    // 每次切换视频都重新创建播放器，避免播放器内部旧的视频节点或流实例残留。
    const element = await disposePlayer()
    if (pageUnmounted || requestId !== playRequestSequence || !element?.isConnected) return null
    const instance = await initPlay(fileId.value, signature.value, playUrl.value, requestId)
    return instance ? data : null
  } catch (e) {
    if (requestId === playRequestSequence) {
      const responseData = e?.response?.data
      const message = responseData?.msg
        || (typeof responseData === 'string' ? responseData : '')
        || e?.message
        || '获取视频播放信息失败，请稍后重试'
      ElMessage.error(message)
    }
    return null
  }
}

const playHadle = async (val) => {
  if (!val) return
  const item = val.item
  const type = String(val.tp)
  if (type === '0') {
    finished.value = false
    ElMessage.success("本章学习完毕，做做练习吧")
    return
  }
  if (!item) return

  // 练习/考试切换前先提交当前视频进度并清理播放器
  if (type === '2' || type === '3') {
    if (Number(item.type) !== SECTION_TYPE_VIDEO) {
      try {
        await ElMessageBox.confirm(
          `温馨提示：考试只能考一次，如果中途退出或未提交结果，将不会允许再次考试，确认考试请点击 继续考试`,
          '确认考试',
          {
            confirmButtonText: '继续考试',
            cancelButtonText: '等会再来',
            type: 'warning',
          }
        )
      } catch (e) {
        return
      }
    }
    addPlayLogHandle(currentPlayData.sectionId, currentPlayData.lessonId, player.value, currentPlayData.duration)
    stopPlaySession()
    await disposePlayer()
    currentPlayData.sectionName = item.name || ''
    currentPlayData.sectionId = item.id
    updateCurrentChapter(item.id)
    currentPlayData.type = item.type
    currentPlayData.moment = item.latestSectionMoment ?? item.moment ?? 0
    currentPlayData.duration = item.mediaDuration || 0
    playId.value = item.id
    store.setCurrentPlayData(currentPlayData)
    startExaminationHandle(item)
    return
  }

  if (type === '9') {
    const section = sectionMap.value[item.id] || item
    currentPlayData.sectionName = section.name || ''
    currentPlayData.sectionId = section.id
    updateCurrentChapter(section.id)
    currentPlayData.type = section.type
    currentPlayData.moment = section.latestSectionMoment ?? section.moment ?? currentPlayData.moment ?? 0
    currentPlayData.duration = section.mediaDuration || currentPlayData.duration || 0
    playId.value = section.id
    pageType.value = 1
    const requestId = ++playRequestSequence
    stopPlaySession()
    await disposePlayer()
    store.setCurrentPlayData(currentPlayData)
    if (Number(section.type) === SECTION_TYPE_VIDEO) {
      const data = await getMediasSignatureData(section.id, requestId)
      if (!data || requestId !== playRequestSequence || !player.value
          || String(currentPlayData.sectionId) !== String(section.id)) return
    }
    return
  }

  // 其他类型默认按视频小节处理，先提交上一节进度
  addPlayLogHandle(currentPlayData.sectionId, currentPlayData.lessonId, player.value, currentPlayData.duration)
  currentPlayData.sectionName = item.name || ''
  currentPlayData.sectionId = item.id
  updateCurrentChapter(item.id)
  currentPlayData.type = item.type
  currentPlayData.moment = item.latestSectionMoment ?? item.moment ?? 0
  currentPlayData.duration = item.mediaDuration || 0
  playId.value = item.id
  finished.value = false

  if (type === '1') {
    pageType.value = 1
    const requestId = ++playRequestSequence
    stopPlaySession()
    const data = await getMediasSignatureData(item.id, requestId)
    if (!data || requestId !== playRequestSequence || !player.value
        || String(currentPlayData.sectionId) !== String(item.id)) return
  }
  store.setCurrentPlayData(currentPlayData)
}

const examInfo = ref({})
const startExaminationHandle = (item) => {
  const sectionType = Number(item?.type)
  if (!item?.id || ![2, 3].includes(sectionType)) {
    ElMessage.error('无效的小节类型，无法打开练习或考试')
    return
  }
  examInfo.value = {
    sectionId: item.id, // 小节id
    type: sectionType - 1,  // 类型，1-练习，2-考试
    courseId: currentPlayData.courseId,
  }
  pageType.value = 2
}

const errorHandle = (val) => {
  pageType.value = 1
}

// 目录 - 打开一个章列表
const openCatalogue = (item) => {
  currentPlayData.chapterId = typeof item === 'object' ? item?.id ?? null : item ?? null
}

// table切换 目录、问答、笔记
const actId = ref(1)
const changeTable = id => {
  actId.value = id
}
// 关闭目录
const isClose = ref(false);
const close = () => {
  isClose.value = true
}
// 打开目录
const open = () => {
  isClose.value = false
}
// 返回上一页
const goBack = async () => {
  ++playRequestSequence
  addPlayLogHandle(currentPlayData.sectionId, currentPlayData.lessonId, player.value, currentPlayData.duration)
  stopPlaySession()
  await router.push({path: '/details/index', query: {id: detailsId.value}})
}
//收藏
</script>
<style lang="scss" src="./index.scss"></style>
