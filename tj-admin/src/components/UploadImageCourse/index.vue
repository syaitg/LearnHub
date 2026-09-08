<template>
  <div class="uploadBox" :class="imageUrl!==''||upladImg!==''?'solidLine':''">
    <el-upload
      ref="uploadfiles"
      class="avatar-uploader"
      :class="isCourse ? 'courseBox' : ''"
      v-model:file-list="fileList"
      :action="actions"
      name="file"
      :headers="uploadHeaders"
      :http-request="uploadImageRequest"
      :before-upload="beforeAvatarUpload"
      :show-file-list="false"
    >
      <img
        v-if="imageUrl || upladImg"
        :src="resolveMediaUrl(imageUrl || upladImg)"
        class="avatar"
      />

      <div v-else class="avatar-uploader-icon"><span></span>上传<Plus /></div>
      <div v-if="imageUrl || upladImg">
        <span class="el-delect" @click.stop="handleRemove"></span>
        <div class="el-upload-list__item-actions">
          <div class="avatar-uploader-icon"><span></span>重新上传<Plus /></div>
        </div>
      </div>
    </el-upload>
    <div>
      图片大小不超过2M<br /><span v-if="isCourse"
        >建议图片尺寸472*264<br /></span
      >仅能上传PNG JPG JPEG类型图片
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from "vue"
import { ElMessage } from "element-plus"
import { Plus } from "@element-plus/icons-vue"
import proxy from '../../config/proxy'
import {TOKEN_NAME} from '../../config/global'
import { uploadFile } from "@/api/media.js"
const env = import.meta.env.MODE || "development"
const proxyConfig = proxy[env] || proxy.development || {}
const apiHost = (proxyConfig.host || "").replace(/\/$/, "")
// 获取父组件值、方法
const props = defineProps({
  // 搜索对象
  isCourse: {
    type: Boolean,
    default: false,
  },
  upladImg: {
    type: String,
    default: "",
  },
})
// 定义变量
const fileList = ref([]) //上传图片列表
let imageUrl = ref("") //上传的图片路径
const emit = defineEmits(["getCoverUrl", "getFlag", "setUplad", "uploadStatus"]) //子组件获取父组件事件传值
const actions = `${apiHost}/ms/files`
// 每次上传前读取最新令牌，避免令牌刷新后仍使用旧令牌
// 解析媒资服务返回的文件地址，兼容完整地址和相对地址。
const resolveMediaUrl = (path) => {
  if (typeof path !== "string" || !path.trim()) return ""
  const value = path.trim()
  if (/^(?:https?:|data:|blob:)/i.test(value)) return value
  // 兼容历史图片地址格式。
  if (/^\/img-(?:tx|ali|qn)\//i.test(value)) return value
  if (!apiHost) return value
  return `${apiHost}${value.startsWith("/") ? value : `/${value}`}`
}

// 递归解析 Axios 和网关可能增加的 data、result、payload 包装。
const getUploadResult = (response) => {
  const nodes = []
  const visited = new Set()

  const visit = (value, depth = 0) => {
    if (!value || depth > 5) return
    if (typeof value === "string") {
      const text = value.trim()
      if ((text.startsWith("{") && text.endsWith("}"))
        || (text.startsWith("[") && text.endsWith("]"))) {
        try {
          visit(JSON.parse(text), depth + 1)
        } catch (e) {
          // JSON 文本解析失败时忽略该节点，继续检查其他响应层级。
        }
      }
      return
    }
    if (Array.isArray(value)) {
      value.forEach(item => visit(item, depth + 1))
      return
    }
    if (typeof value !== "object" || visited.has(value)) return
    visited.add(value)
    nodes.push(value)
    visit(value.data, depth + 1)
    visit(value.result, depth + 1)
    visit(value.payload, depth + 1)
  }
  visit(response)

  let code
  let id
  let path = ""
  let message = ""
  for (const node of nodes) {
    if (code === undefined && node.code !== undefined && node.code !== null) {
      const parsedCode = Number(node.code)
      if (!Number.isNaN(parsedCode)) code = parsedCode
    }
    if (id === undefined) {
      for (const key of ["id", "fileId", "fileID"]) {
        if (node[key] !== undefined && node[key] !== null && String(node[key]).trim()) {
          id = String(node[key]).trim()
          break
        }
      }
    }
    if (!path) {
      for (const key of ["path", "url", "filePath", "fileUrl", "coverUrl", "location"]) {
        if (typeof node[key] === "string" && node[key].trim()) {
          path = node[key].trim()
          break
        }
      }
    }
    if (!message && (node.msg || node.message)) {
      message = node.msg || node.message
    }
  }
  if (!path && id && /^\d+$/.test(id)) {
    path = `/ms/files/${id}/content`
  }
  return { code, path, message }
}

// 每次上传前读取最新令牌，避免令牌刷新后仍使用旧令牌。
const uploadHeaders = computed(() => {
  const token = sessionStorage.getItem(TOKEN_NAME)
  return token ? { authorization: token } : {}
})

// 上传成功后的处理。
const handleAvatarSuccess = (response) => {
  const result = getUploadResult(response)
  const success = (result.code === undefined || result.code === 200) && Boolean(result.path)
  if (!success) {
    const message = result.code === 200
      ? "媒资服务返回数据格式错误，未返回图片地址"
      : result.message || "图片上传失败，请检查媒资服务返回结果"
    const uploadError = new Error(message)
    if (result.code !== undefined && result.code !== 200) {
      uploadError.status = result.code
    }
    uploadError.response = { data: response }
    handleAvatarError(uploadError)
    return uploadError
  }
  revokePreviewUrl()
  imageUrl.value = result.path
  emit("getCoverUrl", result.path)
  emit("getFlag", true)
  emit("uploadStatus", false)
}

// 使用项目统一请求封装上传文件，确保令牌刷新和后端错误信息能正常处理。
const uploadImageRequest = (options) => {
  const formData = new FormData()
  formData.append("file", options.file)
  uploadFile(formData)
    .then(response => {
      const uploadError = handleAvatarSuccess(response)
      if (uploadError) {
        options.onError(uploadError)
        return
      }
      options.onSuccess(response)
    })
    .catch(error => {
      handleAvatarError(error)
      options.onError(error)
    })
  return { abort() {} }
}

const handleAvatarError = (error) => {
  revokePreviewUrl()
  imageUrl.value = ""
  fileList.value = []
  emit("getFlag", Boolean(props.upladImg))
  emit("uploadStatus", false)

  const status = error?.status ?? error?.response?.status
  const responseData = error?.response?.data
  // 网关转发微服务异常时，响应体可能是纯文本，也可能是统一的 JSON 错误对象
  const serverMessage = typeof responseData === "string"
    ? responseData
    : responseData?.msg || responseData?.message
  let message = "图片上传失败，请检查网络或媒资服务后重试！"
  console.error("图片上传失败", {
    error,
    uploadAddress: actions,
  })
  if (status === 401) {
    message = "登录状态已失效，请重新登录后再上传图片！"
  } else if (status === 413) {
    message = "图片文件过大，请压缩后再上传！"
  } else if (status >= 500) {
    message = serverMessage
      ? `媒资服务异常（HTTP ${status}）：${serverMessage}`
      : `媒资服务异常（HTTP ${status}），请检查媒资服务后重试！`
  } else if (status) {
    message = serverMessage
      ? `图片上传失败（HTTP ${status}）：${serverMessage}`
      : `图片上传失败（HTTP ${status}），请检查登录状态或上传参数！`
  } else if (error?.message === "Network Error" || error?.code === "ERR_NETWORK") {
    message = `无法连接图片上传接口：${actions}，请检查网关服务、网页协议或代理设置！`
  } else if (error?.message) {
    message = `图片上传失败：${error.message}`
  }
  ElMessage({
    message,
    type: "error",
    showClose: false,
  })
}
// 释放本地预览地址，避免重复上传时占用浏览器内存
const revokePreviewUrl = () => {
  if (imageUrl.value.startsWith("blob:")) {
    URL.revokeObjectURL(imageUrl.value)
  }
}
// 上传前判断图片是否符合上传要求
const beforeAvatarUpload = (file) => {
  let types = ["image/jpeg", "image/jpg", "image/png"]
  let isImage = types.includes(file.type)
  let isLt2M = file.size / 1024 / 1024 < 2
  // 判断图片格式
  if (!isImage) {
    ElMessage({

      message: "仅能是上传PNG、JPG、JPEG类型图片!",
      type: "error",
      showClose:false,
    })
    return false
  }
  // 判断图片大小
  if (!isLt2M) {
    ElMessage({

      message: "图片大小不超过 2MB!",
      type: "error",
      showClose:false,
    })
    return false
  }
  revokePreviewUrl()
  imageUrl.value = URL.createObjectURL(file)
  emit("getFlag", false)
  emit("uploadStatus", true)
  return true
}
// 删除图片
const handleRemove = () => {
  revokePreviewUrl()
  imageUrl.value = ""
  fileList.value = []
  emit('setUplad', '')
  emit("getFlag", false)
  emit("uploadStatus", false)
}
// 向父组件暴露方法
defineExpose({
  imageUrl,
});
</script>

<style lang="scss" scoped>
.avatar-uploader .el-icon-plus:after {
  position: absolute;
  display: inline-block;
  content: " " !important;
  left: calc(50% - 20px);
  top: calc(50% - 40px);
  width: 40px;
  height: 40px;
  background: url("./../../assets/icons/icon_upload.png") center center
    no-repeat;
  background-size: 20px;
}

.el-upload-list__item-actions:hover .upload-icon {
  display: inline-block;
}
.el-icon-zoom-in:before {
  content: "\E626";
}
.el-icon-delete:before {
  content: "\E612";
}
.el-upload-list__item-actions:hover {
  opacity: 1;
}
.upload-item {
  display: flex;
  align-items: center;
  .el-form-item__content {
    width: 500px !important;
  }
}
.upload-tips {
  font-size: 12px;
  color: #666666;
  display: inline-block;
  line-height: 17px;
  margin-left: 36px;
}
.el-upload-list__item-actions {
  position: absolute;
  width: 100%;
  height: 100%;
  left: 0;
  top: 0;
  cursor: default;
  text-align: center;
  color: #fff;
  opacity: 0;
  font-size: 20px;
  background-color: rgba(0, 0, 0, 0.5);
  transition: opacity 0.3s;
  display: flex;
  justify-content: center;
  align-items: center;
  flex-direction: column;
}
.avatar {
  width: 157px;
  height: 86px;
  display: block;
}
</style>
