<!-- 学习目录 -->
<template>
  <div class="catalogueWrapper">
    <el-collapse accordion v-model="actIndex">
      <el-collapse-item :name="item.id" v-for="item in data" :key="item.id">
        <template #title>
          <div class="title"><span class="ft-wt-600">{{item.name}}</span></div>
        </template>
        <div class="subTitle fx-sb" :class="isPlay(it)"  v-for="it in (item.sections || [])" :key="it.id" >
          <i  @click="play(it, playTypeForSection(it))" :class="startIcon(it)"></i>
          <div class="subTit fx-1">
            <span @click="play(it, playTypeForSection(it))" class="marg-rt-10">{{it.name}}</span>
            <span v-if="it.hasTest" @click="play(it, '2')" class="chapter">练习</span>
            <span v-if="it.trailer" class="trailer-font">试看</span>
          </div>
          <div> 
            <span @click="play(it, playTypeForSection(it))" v-if="Number(it.mediaDuration) > 0">{{ formatDuration(it.mediaDuration) }}</span>
          </div>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>
<script setup>
import { onMounted, ref, watch, inject } from 'vue'
// 引入父级传参
const props = defineProps({
  data:{
    type: Array,
    default: () => []
  },
  statusList:{
    type: Array,
    default: () => []
  },
  playId:{
    type: String,
    default: ''
  },
  finished: {
    type: Boolean,
    default: false
  }
})


const currentPlayData = inject('currentPlayData')

 // SectionVO 中 type 为 2 表示视频小节、3 表示考试；playHandle 的 tp 为 1 表示视频、2 表示练习、3 表示考试。
const SECTION_TYPE_VIDEO = 2
const SECTION_TYPE_EXAM = 3
const PLAY_TYPE_VIDEO = '1'
const PLAY_TYPE_EXAM = '3'

const playTypeForSection = (item) => {
  const sectionType = Number(item?.type)
  if (sectionType === SECTION_TYPE_VIDEO) return PLAY_TYPE_VIDEO
  if (sectionType === SECTION_TYPE_EXAM) return PLAY_TYPE_EXAM
  return null
}
// 默认打开项
const playId = ref(props.playId)
const actIndex = ref("")
const syncActiveChapter = () => {
  const chapters = Array.isArray(props.data) ? props.data : []
  const chapter = chapters.find(item =>
    Array.isArray(item?.sections) && item.sections.some(section =>
      String(section?.id) === String(playId.value)
    )
  )
  actIndex.value = chapter?.id ?? ''
}
onMounted(syncActiveChapter)
watch(() => currentPlayData?.sectionId, (sectionId) => {
  playId.value = sectionId || props.playId
  syncActiveChapter()
}, { immediate: true })
watch(() => props.playId, (value) => {
  if (!currentPlayData?.sectionId) playId.value = value
  syncActiveChapter()
})
watch(() => props.data, syncActiveChapter, { deep: true })
watch(() => props.finished, (value, oldValue) => {
  if (value && !oldValue) next()
})
// 展开章、节
const isPlay = (section) => ({
  playAct: String(playId.value) === String(section?.id)
})

// 根据播放状态调整icon
const formatDuration = (value) => {
  const totalSeconds = Math.max(0, Math.floor(Number(value) || 0))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = String(totalSeconds % 60).padStart(2, '0')
  return `${minutes}:${seconds}`
}

const startIcon = (item) => {
  let data = 'iconfont zhy-a-ico-sp-sei2x'

  if (String(item.type) === '2') {
    if (item.finished === false) {
      data = 'iconfont zhy-a-ico-502x1'
    } else if (item.finished === true) {
      data = 'iconfont zhy-a-ico-wc2x'
    }
  } else if (String(item.type) === '3') {
    data = 'iconfont zhy-a-ico-jdks2x'
  }
  return data
}

// emit数据载入
const emit = defineEmits(['sortHandle', 'playHadle', 'openCatalogue'])
const openItem = val => {
  emit('openCatalogue', val)
}
// 排序选中参数定义
const activeKey = ref('all')

// 点击小节 type == 1 是点击视频 2 点击练习
const play = (item, tp) => {
  if (!item) return
  playId.value = item.id
  emit('playHadle', { item, tp: String(tp) })
}
const next = () => {
  const chapters = Array.isArray(props.data) ? props.data : []
  const sections = chapters.flatMap(chapter => Array.isArray(chapter.sections) ? chapter.sections : [])
  const currentIndex = sections.findIndex(section => String(section.id) === String(playId.value))
  const nextItem = currentIndex >= 0 ? sections[currentIndex + 1] : sections[0]
  if (!nextItem) {
    emit('playHadle', { item: null, tp: '0' })
    return
  }
  if (Number(nextItem.type) === SECTION_TYPE_EXAM) {
    // 下一小节是考试时，打开考试页面
    emit('playHadle', { item: nextItem, tp: PLAY_TYPE_EXAM })
    return
  }
  playId.value = nextItem.id
  emit('playHadle', { item: nextItem, tp: '1' })
}
</script>
<style lang="scss" scoped>
.catalogueWrapper {
  padding: 0 10px;

  ::deep(.el-collapse-item__header) {
    background: transparent;
  }

  .title {
    font-size: 16px;
    height: 40px;
    line-height: 40px;
    width: 280px;
    overflow: hidden;

    span {
      display: inline-block;
      height: 40px;
    }
  }

  .subTitle {
    position: relative;

    ::before {
      position: relative;
      z-index: 2;
    }

    ::after {
      content: '';
      position: absolute;
      left: 7px;
      top: 21px;
      border-left: 1px dashed #667280;
      height: calc(100% - 2px);
    }

    line-height: 20px;

    i {
      position: relative;
      top: 1px;
      margin-right: 4px;
    }

    cursor: pointer;
    margin: 5px 0 20px 0;
    color: #A0A9B2;

    .subTit {
      width: 230px;
      line-height: 20px;

      .chapter {
        display: inline-block;
        width: 32px;
        text-align: center;
        line-height: 15.5px;
        font-weight: 400;
        font-size: 12px;
        border-radius: 2px;
        background: #A0A9B2;
        color: #1B2127;
      }
    }

    &:hover {
      color: #fff;

      .chapter {
        background: #ffffff !important;
        color: #1B2127 !important;
      }
    }

  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 5px;
  }

  .subTitle:last-child {
    margin-bottom: 0;

    ::after {
      display: none;
    }
  }

  .playAct {
    ::after {
      border-color: var(--color-main);
    }

    color: var(--color-main);

    .chapter {
      background: var(--color-main) !important;
      color: #FFFFFF !important;
    }
  }
  .trailer-font{
    padding: 3px 12px;
    font-size: 12px;
    //background: rgba(242,13,13,.1);
    border-radius: 12px;
    color: #f20d0d;
    font-weight: 700;
    line-height: 20px;
  }
}
</style>
