<!-- 媒资列表-->
<template>
  <div class="contentBox">
    <!-- 搜索 -->
    <Search :searchData="searchData" @handleSearch="handleSearch"></Search>
    <!-- end -->
    <div class="bg-wt radius marg-tp-20">
      <div class="tableBox">
        <div class="conHead pad-30">
          <!-- 上传视频 -->
          <div class="addBox">
            <el-upload
              class="upload-demo"
              action="#"
              :multiple="true"
              :http-request="httpRequest"
              :before-upload="beforeVideoUpload"
              :accept="accept"
              :limit="100"
              :show-file-list="false"
              :on-remove="handleRemove"
              :on-change="handleChange"
              :on-progress="handleProgress"
              :file-list="fileList"
            >
              <el-button
                size="small"
                type="primary"
                style="
                  font-family: PingFangSC-Medium;
                  font-weight: 500;
                  font-size: 14px;
                "
                >上传视频</el-button
              >
            </el-upload>
            <!-- <el-button
              class="button buttonSub"
              v-if="activeName === 'second'"
              @click="handleBatchStop"
              >批量暂停</el-button
            >
            <el-button
              class="button buttonSub"
              v-if="activeName === 'second'"
              @click="handleBatchDelet"
              >批量删除</el-button
            > -->
          </div>
          <!-- end -->
          <!-- tab -->
          <div class="tab">
            <el-tabs
              v-model="activeName"
              class="demo-tabs"
              @tab-click="handleClick"
            >
              <el-tab-pane label="已上传" name="first"> </el-tab-pane>
              <el-tab-pane label="上传中" name="second"></el-tab-pane>
            </el-tabs>
          </div>
          <!-- end -->
        </div>
        <!-- 已上传列表 -->
        <TableList
          v-if="activeName === 'first'"
          :baseData="baseData"
          :total="total"
          :loading="loading"
          :pageSize="searchData.pageSize"
          :isSearch="isSearch"
          @getList="getList"
          @handleSizeChange="handleSizeChange"
          @handleCurrentChange="handleCurrentChange"
        ></TableList>
        <!-- end -->
        <!-- 上传中列表 -->
        <UploadCenter
          v-else
          ref="upload"
          :baseData="fileList"
          :total="total"
          :loading="loading"
          :getList="getList"
          :pageSize="searchData.pageSize"
          @continueUpload="continueUpload"
          @suspendUpload="suspendUpload"
          @handleSizeChange="handleSizeChange"
          @handleCurrentChange="handleCurrentChange"
          @deleteUpload="deleteUpload"
        ></UploadCenter>
        <!-- end -->
      </div>
    </div>
    <!-- 上传视频结束后弹层 -->
    <UploadVideo
      :dialogVisible="uploadVideoVisible"
      :videoNumber="videoNumber"
      @handleClose="handleClose"
    ></UploadVideo>
    <!-- end -->
  </div>
</template>
<script setup>
import { ref, reactive, onMounted, onUnmounted } from "vue"
import TcVod from "vod-js-sdk-v6"
import { ElMessage } from "element-plus"
// 公用数据
import { statusData } from "@/utils/commonData"
// 导入接口
import { getMedia, getMediaUpload, mediaUpload } from "@/api/media"
// 导入组件
// 搜索
import Search from "./components/Search.vue"
// 已上传
import TableList from "./components/TableList.vue"
// 已上传
import UploadCenter from "./components/UploadCenter.vue"
// 上传视频结束后弹层
import UploadVideo from "@/components/Video/index.vue"
// ------定义变量------
const loading = ref(false)
let total = ref(null) //数据总条数
let upload = ref()
const activeName = ref("first") //当前选中的tab值
let searchData = reactive({
  pageSize: 10,
  pageNo: 1,
  sortBy: "id",
  isAsc: false,
}) //搜索对象
let baseData = ref([]) //表格数据
let fileList = ref([]) //上传列表
let uploaderG = ref([])
const uploadGenerations = new Map()
let isSearch = ref(false) //是否触发了搜索按钮,用来控制没有搜索出数据和正常列表无数据的区分，显示的图片和提示语不一样
let tcVod = null
let videoNumber = ref(0) //共上传视频数量
let videoSucceedNumber = ref(0) //上传视频成功数量
let videoFailNumber = ref(0) //上传视频失败数量
let uploadVideoVisible = ref(false) //上传视频结束后提示弹层
const accept = ".mp4" //当前统一支持上传MP4格式视频
// 获取统一响应或异常中的错误提示
const getErrorMessage = (error, defaultMessage) => {
  return error?.response?.data?.msg || error?.message || defaultMessage
}
// 获取当前上传文件对应的列表项
const uploadKey = (uid) => String(uid)
const getUploadItem = (uid) => {
  const key = uploadKey(uid)
  return fileList.value.find((item) => uploadKey(item.uid) === key)
}
const nextUploadGeneration = (uid) => {
  const key = uploadKey(uid)
  const generation = (uploadGenerations.get(key) || 0) + 1
  uploadGenerations.set(key, generation)
  return generation
}
const invalidateUpload = (uid) => nextUploadGeneration(uid)
const isCurrentUpload = (uid, generation, item) =>
  uploadGenerations.get(uploadKey(uid)) === generation && getUploadItem(uid) === item && !item.isStop
const refreshVideoNumber = () => {
  videoNumber.value = videoSucceedNumber.value + videoFailNumber.value
}
const markUploadFailure = (item) => {
  if (item.failureCounted) return
  item.failureCounted = true
  videoFailNumber.value += 1
  refreshVideoNumber()
}
const clearUploadFailure = (item) => {
  if (!item.failureCounted) return
  item.failureCounted = false
  videoFailNumber.value = Math.max(0, videoFailNumber.value - 1)
  refreshVideoNumber()
}
// 移除指定的上传任务和列表项
const removeUploadItem = (uid) => {
  invalidateUpload(uid)
  uploaderG.value = uploaderG.value.filter((item) => uploadKey(item.uid) !== uploadKey(uid))
  const index = fileList.value.findIndex((item) => uploadKey(item.uid) === uploadKey(uid))
  if (index >= 0) {
    const removedItem = fileList.value[index]
    clearUploadFailure(removedItem)
    fileList.value.splice(index, 1)
  }
}
// 判断是否还有正在上传或正在保存的任务
const hasActiveUpload = () => {
  return fileList.value.some((item) =>
    ["uploading", "saving"].includes(item.uploadStatus)
  )
}
// ------生命周期------
onMounted(() => {
  init()
})

onUnmounted(() => {
  // 页面卸载时使当前上传 generation 失效，避免 SDK 的异步 Promise 回调继续修改页面状态。
  // 同时取消底层上传任务并清理任务集合。
  for (const task of uploaderG.value) {
    const item = getUploadItem(task.uid)
    if (item) item.isStop = true
    invalidateUpload(task.uid)
    try {
      task.uploader.cancel()
    } catch (e) {
      // 上传器已经结束或已取消时，忽略取消异常。
    }
  }
  uploaderG.value = []
  uploadGenerations.clear()
})
// ------定义方法------
// 获取初始值
const init = () => {
  getList()
  // 获取视频签名
  const getSignature = async function () {
    const res = await getMediaUpload({
      //这里就是发axios请求
    })
    if (res?.code !== 200) {
      throw new Error(res?.msg || "获取视频上传授权失败，请检查媒资服务配置！")
    }
    if (typeof res.data !== "string" || !res.data.trim()) {
      throw new Error("媒资服务未返回有效的视频上传授权，请联系管理员检查VOD配置！")
    }
    return res.data
  }
  // 前文中所述的获取上传签名的函数
  tcVod = new TcVod({
    getSignature: getSignature,
  })
}
// 获取列表值
const getList = async (showError = true) => {
  loading.value = true
  try {
    const res = await getMedia(searchData)
    if (res?.code !== 200) {
      throw new Error(res?.msg || "媒资列表加载失败，请稍后重试！")
    }
    baseData.value = Array.isArray(res.data?.list) ? res.data.list : []
    total.value = Number(res.data?.total || 0)
  } catch (err) {
    if (showError) {
      ElMessage({
        message: getErrorMessage(
          err,
          "媒资列表加载失败，请检查网络或媒资服务后重试！"
        ),
        type: "error",
        showClose: true,
        duration: 5000,
      })
    }
  } finally {
    loading.value = false
  }
}
// 触发tab切换
const handleClick = () => {
  if (activeName.value === "first") {
    getList()
  }
}
// 获取签名 这里的签名请求是由后端提供的，只需要拿到后端给的签名请求即可
// const getVodSignature = async () => {
//   await getMediaUpload()
//     .then((res) => {})
//     .catch((err) => {});
// };
// 文件列表改变时 将文件列表保存到本地
const handleChange = (file, fileItem) => {
  activeName.value = "second"
  fileItem.forEach((item) => {
    if (uploadKey(item.uid) === uploadKey(file.uid) && !item.uploadStatus) {
      item.videoFlag = true
      item.videoUploadPercent = 0
      item.uploadStatus = "waiting"
      item.statusText = "等待上传"
      item.isStop = false
    }
  })
  fileList.value = [...fileItem]
}
// 文件从上传组件移除时同步清理任务
const handleRemove = (file) => {
  const task = uploaderG.value.find((item) => uploadKey(item.uid) === uploadKey(file.uid))
  if (task) {
    const item = getUploadItem(file.uid)
    if (item) { item.isStop = true; invalidateUpload(file.uid) }
    try {
      task.uploader.cancel()
    } catch (e) {
      // 上传器已经结束或已取消时，忽略取消异常。
    }
  }
  removeUploadItem(file.uid)
}
// 上传前统一校验视频格式和大小
const beforeVideoUpload = (rawFile) => {
  const extension = rawFile.name.split(".").pop()?.toLowerCase()
  if (extension !== "mp4") {
    ElMessage({
      message: "当前仅支持上传MP4格式的视频，请重新选择文件！",
      type: "warning",
      showClose: true,
      duration: 5000,
    })
    return false
  }
  const isLt2G = rawFile.size / 1024 / 1024 < 2048
  if (!isLt2G) {
    ElMessage({
      message: "视频大小不能超过2GB，请压缩后重新上传！",
      type: "warning",
      showClose: true,
      duration: 5000,
    })
    return false
  }
  return true
}
// 保存云端上传结果到媒资服务
const saveUploadedMedia = async (doneResult, uploadItem, filename) => {
  uploadItem.uploadStatus = "saving"
  uploadItem.statusText = "云端上传完成，正在保存媒资信息"
  uploadItem.videoUploadPercent = 100
  // 这里发请求给后端进行转码操作
  let val = {
    fileId: doneResult.fileId, // 腾讯云file_id
    source: "OWN_TENCENT", // 新上传视频使用自己的腾讯云 VOD 账号
    // video_type: "operating_activity", // 视频类型
    filename: filename, // 视频名称
    mediaUrl:
      doneResult.video && doneResult.video.url
        ? doneResult.video.url
        : "", // 视频地址
  }
  const res = await mediaUpload(val)
  if (res?.code !== 200) {
    throw new Error(res?.msg || "视频已上传到云端，但媒资信息保存失败！")
  }
}
// 执行腾讯云视频上传，并同步更新当前文件的状态
const startVideoUpload = (rawFile, uploadItem, startPercent = 0) => {
  if (!rawFile || !uploadItem || !getUploadItem(uploadItem.uid)) return Promise.resolve()
  if (!tcVod) {
    uploadItem.uploadStatus = "failed"
    uploadItem.statusText = "上传组件初始化失败"
    markUploadFailure(uploadItem)
    ElMessage({ message: "视频上传组件尚未初始化，请刷新页面后重试！", type: "error", showClose: true, duration: 5000 })
    return Promise.resolve()
  }
  clearUploadFailure(uploadItem)
  const uid = uploadItem.uid
  const generation = nextUploadGeneration(uid)
  uploadItem.raw = rawFile
  uploadItem.videoFlag = true
  const normalizedStart = Math.max(0, Math.min(1, Number(startPercent) || 0))
  uploadItem.videoUploadPercent = Math.floor(normalizedStart * 100)
  uploadItem.uploadStatus = "uploading"
  uploadItem.statusText = "正在上传"
  uploadItem.isStop = false

  let currentUploader
  try {
    currentUploader = tcVod.upload({ mediaFile: rawFile, fileParallelLimit: 1, chunkParallelLimit: 1 })
  } catch (error) {
    if (!isCurrentUpload(uid, generation, uploadItem)) return Promise.resolve()
    uploadItem.uploadStatus = "failed"
    uploadItem.statusText = "创建上传任务失败"
    uploadItem.videoFlag = true
    markUploadFailure(uploadItem)
    ElMessage({ message: getErrorMessage(error, "创建视频上传任务失败，请稍后重试！"), type: "error", showClose: true, duration: 5000 })
    return Promise.resolve()
  }

  uploaderG.value = uploaderG.value.filter((item) => uploadKey(item.uid) !== uploadKey(uid))
  uploaderG.value.push({ uid, generation, uploader: currentUploader, videoFile: rawFile })
  currentUploader.on("media_upload", () => {
    if (isCurrentUpload(uid, generation, uploadItem)) uploadItem.statusText = "视频文件上传完成，正在等待云端确认"
  })
  currentUploader.on("media_progress", (info) => {
    if (!isCurrentUpload(uid, generation, uploadItem)) return
    const percent = normalizedStart + (1 - normalizedStart) * (Number(info?.percent) || 0)
    uploadItem.videoUploadPercent = Math.min(100, Math.max(0, parseInt(percent * 100)))
  })

  return currentUploader.done()
    .then(async (doneResult) => {
      if (!isCurrentUpload(uid, generation, uploadItem)) return
      if (!doneResult?.fileId) throw new Error("腾讯云未返回视频文件编号，无法保存媒资信息！")
      await saveUploadedMedia(doneResult, uploadItem, rawFile.name)
      if (!isCurrentUpload(uid, generation, uploadItem)) return
      uploadItem.uploadStatus = "success"
      uploadItem.statusText = "正在上传"
      uploadItem.failureCounted = false
      videoSucceedNumber.value += 1
      refreshVideoNumber()
      uploaderG.value = uploaderG.value.filter((item) =>
        !(uploadKey(item.uid) === uploadKey(uid) && item.generation === generation))
      await getList(false)
      if (!isCurrentUpload(uid, generation, uploadItem)) return
      ElMessage({ message: `视频“${rawFile.name}”上传成功，已加入媒资列表！`, type: "success", showClose: true, duration: 4000 })
      window.setTimeout(() => {
        if (uploadGenerations.get(uploadKey(uid)) !== generation || getUploadItem(uid) !== uploadItem) return
        removeUploadItem(uid)
        if (!hasActiveUpload() && videoFailNumber.value === 0) {
          uploadVideoVisible.value = true
          activeName.value = "first"
        }
      }, 500)
    })
    .catch((error) => {
      if (!isCurrentUpload(uid, generation, uploadItem)) return
      uploadItem.uploadStatus = "failed"
      uploadItem.statusText = "上传失败，可点击重新上传"
      uploadItem.videoFlag = true
      markUploadFailure(uploadItem)
      uploaderG.value = uploaderG.value.filter((item) =>
        !(uploadKey(item.uid) === uploadKey(uid) && item.generation === generation))
      ElMessage({ message: `视频“${rawFile.name}”上传失败：${getErrorMessage(error, "请检查腾讯云 VOD 权限、网络或媒资服务配置后重试")}`, type: "error", showClose: true, duration: 7000 })
    })
}
const httpRequest = (file) => {
  const uploadItem = getUploadItem(file.file.uid)
  if (!uploadItem) {
    ElMessage({
      message: "未找到待上传的视频，请重新选择文件！",
      type: "error",
      showClose: true,
    })
    return Promise.resolve()
  }
  ElMessage({
    message: `视频“${file.file.name}”已开始上传，可在“上传中”查看进度。`,
    type: "info",
    showClose: true,
    duration: 3000,
  })
  return startVideoUpload(file.file, uploadItem)
}
// 恢复上传
const continueUpload = (row) => {
  if (!row.raw) {
    ElMessage({
      message: "本地视频文件已失效，请删除该任务后重新选择文件上传！",
      type: "warning",
      showClose: true,
      duration: 5000,
    })
    return
  }
  startVideoUpload(row.raw, row, row.videoUploadPercent / 100)
}
// 暂停上传
const suspendUpload = (row) => {
  const task = uploaderG.value.find((item) => uploadKey(item.uid) === uploadKey(row.uid))
  if (!task) {
    ElMessage({
      message: "当前上传任务不存在，可能已经结束，请刷新后查看！",
      type: "warning",
      showClose: true,
    })
    return
  }
// 取消上传的方法
  row.isStop = true
  invalidateUpload(row.uid)
  row.uploadStatus = "paused"
  row.statusText = "已暂停，可继续上传"
  try {
    task.uploader.cancel()
  } catch (e) {
    // 上传器已经结束或已取消时，忽略取消异常。
  }
  uploaderG.value = uploaderG.value.filter(
    (item) => uploadKey(item.uid) !== uploadKey(row.uid)
  )
  ElMessage({
    message: `视频“${row.name}”已暂停上传。`,
    type: "info",
    showClose: true,
  })
}
//取消上传
const uploadVideoFileCancel = () => {
  for (const task of uploaderG.value) {
    const item = getUploadItem(task.uid)
    if (item) {
      item.isStop = true
      invalidateUpload(task.uid)
    }
    try {
      task.uploader.cancel()
    } catch (e) {
      // ignore cancellation errors from an already completed uploader
    }
  }
  uploaderG.value = []
}
const handleProgress = (event, file, fileList) => { }
// 搜索
const handleSearch = () => {
  isSearch.value = true //是否触发了搜索按钮
  getList()
}
// 设置每页条数
const handleSizeChange = (val) => {
  searchData.pageSize = val
  // 刷新列表
  getList()
}
// 当前页
const handleCurrentChange = (val) => {
  searchData.pageNo = val
  // 刷新列表
  getList()
}
// 删除正在上传的视频
const deleteUpload = (row) => {
  const task = uploaderG.value.find((item) => uploadKey(item.uid) === uploadKey(row.uid))
  // 先取消上传视频
  if (task) {
    row.isStop = true
    invalidateUpload(row.uid)
    try {
      task.uploader.cancel()
    } catch (e) {
      // 上传器已经结束或已取消时，忽略取消异常。
    }
  }
// 再把次视频从列表里删除
  removeUploadItem(row.uid)
  if (upload.value) {
    upload.value.dialogDeleteVisible = false
  }
  ElMessage({
    message: `已取消视频“${row.name}”的上传任务。`,
    type: "info",
    showClose: true,
  })
}
// 关闭上传结束后弹层
const handleClose = () => {
  uploadVideoVisible.value = false //关闭上传视频结束后的弹层
  activeName.value = "first"
};
</script>
<style src="./../index.scss" lang="scss" scoped></style>
<style lang="scss" scoped>
.contentBox{
  margin-bottom: 20px;
}
</style>
