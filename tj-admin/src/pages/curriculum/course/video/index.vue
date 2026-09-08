<!--课程视频-->
<template>
  <div class="contentBox">
    <div class="courseList">
      <el-collapse accordion v-model="activeNames">
        <el-collapse-item v-for="(item, index) in itemData" :key="index">
          <template v-slot:title>
            <div class="titText">
              <span class="icon" v-if="item.sections.length > 0"></span>
              <div class="textL">
                <span
                  ><span v-if="index + 1 > 9">{{ index + 1 }}</span
                  ><span v-else>{{ "0" + (index + 1) }}</span></span
                >
                <span>{{ item.name }}</span>
              </div>
            </div>
          </template>
          <div class="itemCon" v-if="item.sections.length > 0">
            <div class="headTitle">
              <span>序号</span>
              <span style="margin-left: 14px">小节名称</span>
              <span class="textLeft">视频名称</span>
              <span>视频时长</span>
              <span>免费试看</span>
            </div>
            <div class="item">
              <ul>
                <li v-for="(val, i) in item.sections" :key="i">
                  <div class="leftLine"></div>
                  <div class="con">
                    <!-- 序号 -->
                    <div>
                      <span v-if="i + 1 > 9">{{ i + 1 }}</span
                      ><span v-else>{{ "0" + (i + 1) }}</span>
                    </div>
                    <div style="margin-left: 14px; color: #332929">
                      {{ val.name }}
                    </div>
                    <div class="videoName">
                      <div v-if="val.mediaName !== ''"  class="textLeft">
                        <span @click="handleSeeVideo(val.mediaId)"
                          >{{ ellipsis(val.mediaName,8) }} .mp4</span
                        >
                        <i
                          class="deleteIcon"
                          @click="handleDelete(val)"
                          style="margin: 0 0 4px 4px"
                        ></i>
                      </div>
                      <div v-else class="textLeft">
                        <span
                          class="textDefault"
                          @click="handleOpen(val.id)"
                          style="margin-left: 0px"
                          >选择视频</span
                        ><span class="textDefault">
                          <el-upload
                            class="upload-demo"
                            action="#"
                            :multiple="false"
                            :http-request="(param) => httpRequest(param, val.id)"
                            :accept="accept"
                            :limit="1"
                            :show-file-list="false"
                            :on-remove="handleRemove"
                            :on-change="(file, files) => handleChange(file, files, val.id)"
                            :on-progress="handleProgress"
                            :file-list="[]"
                          >
                            <el-button size="small" type="primary"
                              >本地上传</el-button
                            >
                          </el-upload></span
                        >
                      </div>
                    </div>
                    <div>
                      {{
                        val.mediaDuration > 0
                          ? formatSeconds(val.mediaDuration)
                          : ""
                      }}
                    </div>
                    <div class="textWarning">
                      <el-switch
                        v-model="val.trailer"
                        active-color="#00BE76"
                        active-text="试看3分钟"
                        :disabled="free"
                        @change="handleTrailer($event, val)"
                      >
                      </el-switch>
                    </div>
                  </div>
                </li>
              </ul>
            </div>
            <div class="cover"></div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
    <!-- 添加媒资视频弹层 -->
    <AddVideo
      :dialogVisible="dialogVisible"
      :itemData="itemData"
      @setVideoInfo="setVideoInfo"
      @handleClose="handleClose"
    ></AddVideo>
    <!-- end -->
    <!-- 预览弹层 -->
    <Preview
      ref="preview"
      :title="title"
      :mediaId="mediaId"
      :dialogFormVisible="dialogFormVisible"
      @handleClose="handlePreviewClose"
    ></Preview>
    <!-- end -->
  </div>
</template>
<script setup>
import { ref, onMounted, onUnmounted } from "vue"
import { useRouter, useRoute } from "vue-router"
import { ElMessage, ElLoading } from "element-plus"
import TcVod from "vod-js-sdk-v6"
import { formatSeconds,ellipsis } from '@/utils/index'
// 接口
import {
  getCoursesCatalogue,
  baseVideoSave,
  getCoursesDetail,
} from "@/api/curriculum"
import { getMediaUpload, mediaUpload } from "@/api/media"
// 导入组件
// 删除弹层
import Delete from "@/components/Delete/index.vue"
// 添加视频弹层
import AddVideo from "./addVideo.vue"
// 预览弹层
import Preview from "@/components/Preview/index.vue"
// ------定义变量------
const props = defineProps({
  // // 课程id
  // courseId: {
  //   type: Number,
  //   default: 0,
  // },
})
const router = useRouter() //获取全局
const route = useRoute() // 获取当前路由
const emit = defineEmits() // 定义组件事件
const itemData = ref([]) //目录数据
let dialogVisible = ref(false) //选择视频列表弹层
let dialogFormVisible = ref(false) //弹层隐藏显示
let sectionId = ref("") //小节id
const uploaders = new Map()
const uploadSequences = new Map()
let componentUnmounted = false
let videoName = ref("") //上传的视频名称
let loadingInstance = null
let loadingCount = 0
const accept = ".mp4,video/mp4"
let free = ref(false) //是否免费
let courseId = route.params.id //课程id
let mediaId = ref("")//视频id
let preview = ref(null)
const startLoading = () => {
  loadingCount += 1
  if (!loadingInstance) {
    loadingInstance = ElLoading.service({
      lock: true,
      text: "视频上传中…",
      background: "rgba(51, 51, 51, 0.4)",
    })
  }
  let released = false
  return () => {
    if (released) return
    released = true
    loadingCount = Math.max(0, loadingCount - 1)
    if (loadingCount === 0 && loadingInstance) {
      loadingInstance.close()
      loadingInstance = null
    }
  }
}
onMounted(() => {
  getCatalogue()//获取目录数据
  getDetailData() // 获取课程详情
})
// ------定义方法------
// 获取目录数据
const getCatalogue = async () => {
  let params = {
    id: courseId,
    see: false,
    withPractice: 0 //是否带着练习题，1：带着练习题，0：不带练习题，默认
  }
  await getCoursesCatalogue(params)
    .then((res) => {
      if (res.code === 200) {
        itemData.value = res.data
      }
    })
    .catch((err) => {
      ElMessage.error(err?.message || "获取课程目录失败")
    })
}
// 获取详情
let getDetailData = async () => {
  await getCoursesDetail(courseId)
    .then((res) => {
      if (res.code === 200) {
        free.value = res.data.free
      }
    })
    .catch((err) => {
      ElMessage.error(err?.message || "获取课程目录失败")
    })
}
// 提交
const handleSubmit = async (str) => {
  let arr = []
  let data = {}
  itemData.value.map((obj) => {
    obj.sections.map((val) => {
      data = {
        cataId: val.id,
        mediaId: val.mediaId,
        trailer: val.trailer,
        videoName: val.mediaName,
        mediaDuration: val.mediaDuration,
      }
      arr.push(data)
    })
  })
  let params = {
    datas: arr,
    id: courseId,
  }
  await baseVideoSave(params)
    .then((res) => {
      if (res.code === 200) {
        ElMessage({

          message: "保存成功",
          type: "success",
          showClose:false,
        })
        emit("getActive", 3)
        if (str === "getback") {
          router.push({
            path: "/curriculum/index",
          })
        }
      } else {
        ElMessage({

          message: res.msg || "保存课程视频失败",
          type: "error",
          showClose:false,
        })
      }
    })
    .catch((err) => {
      ElMessage.error(err?.message || "获取课程目录失败")
    })
}
const getVodSignature = async () => {
  await getMediaUpload()
    .then((res) => { })
    .catch((err) => {
      ElMessage.error(err?.message || "获取课程目录失败")
    })
}
const closeLoading = () => {
  loadingCount = 0
  if (loadingInstance) {
    loadingInstance.close()
    loadingInstance = null
  }
}

const updateSectionMedia = (id, data) => {
  for (const chapter of itemData.value) {
    const section = chapter.sections?.find((item) => String(item.id) === String(id))
    if (section) {
      section.mediaName = data.filename || ""
      section.mediaId = data.id
      section.mediaDuration = Number(data.duration || 0)
      return true
    }
  }
  return false
}

const httpRequest = async (file, id) => {
  const taskSectionId = id
  const rawFile = file?.file
  if (!rawFile) {
    ElMessage.error("未找到待上传的视频文件")
    return
  }
  if (rawFile.type !== "video/mp4" && !/\.mp4$/i.test(rawFile.name || "")) {
    ElMessage.error("仅支持上传 MP4 格式的视频")
    return
  }

  const releaseLoading = startLoading()
  const key = String(taskSectionId)
  const sequence = (uploadSequences.get(key) || 0) + 1
  uploadSequences.set(key, sequence)
  const isCurrentUpload = () => !componentUnmounted && uploadSequences.get(key) === sequence
  let uploader = null
  try {
    const signatureRes = await getMediaUpload()
    if (signatureRes.code !== 200 || !signatureRes.data) {
      throw new Error(signatureRes.msg || "获取视频上传授权失败")
    }
    if (!isCurrentUpload()) return
    const tcVod = new TcVod({
      getSignature: async () => signatureRes.data,
    })
    uploader = tcVod.upload({
      mediaFile: rawFile,
      fileParallelLimit: 1,
      chunkParallelLimit: 1,
    })
    uploaders.set(key, uploader)
    uploader.on("media_progress", () => {})
    const doneResult = await uploader.done()
    if (!isCurrentUpload()) return
    if (!doneResult?.fileId) {
      throw new Error("腾讯云未返回有效的 fileId")
    }
    const res = await mediaUpload({
      fileId: doneResult.fileId,
      source: "OWN_TENCENT",
      mediaUrl: doneResult.video?.url || "",
      filename: rawFile.name || "",
    })
    if (!isCurrentUpload()) return
    if (res.code !== 200 || !res.data?.id) {
      throw new Error(res.msg || "保存媒资信息失败")
    }
    if (!isCurrentUpload()) return
    if (!updateSectionMedia(taskSectionId, res.data)) {
      throw new Error("找不到对应的小节")
    }
    ElMessage.success("上传成功")
  } catch (err) {
    if (isCurrentUpload()) {
      ElMessage.error(err?.message || "视频上传失败")
    }
  } finally {
    releaseLoading()
    const key = String(taskSectionId)
    if (uploaders.get(key) === uploader) uploaders.delete(key)
  }
}

onUnmounted(() => {
  componentUnmounted = true
  // 先让所有进行中的异步回调失效，再取消 SDK uploader，避免卸载后回调修改页面状态。
  for (const key of uploadSequences.keys()) {
    uploadSequences.set(key, (uploadSequences.get(key) || 0) + 1)
  }
  for (const uploader of uploaders.values()) {
    try {
      uploader.cancel()
    } catch (e) {
      // 上传器已经结束或已取消时，忽略取消异常。
    }
  }
  uploaders.clear()
  uploadSequences.clear()
  closeLoading()
})
// 打开视频选择列表弹层
const handleOpen = (id) => {
  sectionId.value = id
  dialogVisible.value = true
}
// 上传视频前获取小节id
const handleChange = (file, files, id) => {
  if (file?.status === "ready") {
    sectionId.value = id
  }
}
//获取视频信息
const setVideoInfo = (value) => {
  itemData.value.map((obj) => {
    obj.sections.map((val) => {
      if (String(sectionId.value) === String(val.id)) {
        val.mediaName = value.filename
        val.mediaDuration = value.duration
        val.mediaId = value.id
      }
    })
  })
}
// 是否观看
const handleTrailer = (e, value) => {
  itemData.value.map((obj) => {
    obj.sections.map((val) => {
      if (value.id === val.id) {
        val.trailer = e
      }
    })
  })
}
// 关闭视频 弹层
const handleClose = () => {
  dialogVisible.value = false
}
// 删除视频
const handleDelete = (value) => {
  itemData.value.map((obj) => {
    obj.sections.map((val) => {
      if (value.id === val.id) {
        val.mediaId = null
        val.mediaName = ""
        val.mediaDuration = 0
      }
    })
  })
}
// 视频观看
const handleSeeVideo = (id) => {
  if (!id || !preview.value) return
  mediaId.value = id
  dialogFormVisible.value = true
  // 等待视频弹层 DOM 挂载完成后再调用预览
  queueMicrotask(() => preview.value?.getId(id))
}
// 关闭弹层
const handlePreviewClose = () => {
  dialogFormVisible.value = false
};
// 向父组件暴露方法
defineExpose({
  handleSubmit,
});
</script>
<style lang="scss" scoped>
:deep(.videoBox .el-dialog){
  width: 1096px;
}
.courseList .el-collapse, .courseList .el-collapse-item__wrap{
  min-width: 1065px;
}
:deep(.videoBox .el-dialog){
  min-width: 1096px !important;
}
</style>
