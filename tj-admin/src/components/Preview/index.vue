<!--视频预览-->
<template>
  <div class="preview" style="width: 60%">
    <el-dialog :model-value="props.dialogFormVisible" :title="props.title" @close="handleClose">
      <span class="close" @click="handleClose"></span>
      <div class="video">
        <video :key="videoElementKey" id="videoRef" ref="videoRef" preload="auto"></video>
      </div>
    </el-dialog>
  </div>
</template>
<script setup>
import {
  ref,
  onUnmounted,
  nextTick,
} from "vue";
import { ElMessage } from "element-plus";
// 接口api
import { getMediasSignature } from "@/api/media";
// 获取父组件值、方法
const props = defineProps({
  // 弹层隐藏显示
  dialogFormVisible: {
    type: Boolean,
    default: false,
  },
  // 媒资id
  mediaId: {
    type: [String, Number],
    default: "",
  },
  title: {
    type: String,
    default: "视频预览",
  },
});
// ------定义变量------
const emit = defineEmits(); //子组件获取父组件事件传值
const videoRef = ref(null);
const videoElementKey = ref(0);
// 初始化视频播放器并播放视频，支持官方 psign 和自有防盗链地址
const player = ref(null);
const fileId = ref("");
const signature = ref("");
const playAppId = ref("");
const playUrl = ref("");
let requestSequence = 0;

const getId = async (mediaId) => {
  const sequence = ++requestSequence
  disposePlayer()
  // 等待弹层 DOM 完成挂载后再初始化 video 播放器
  await nextTick()
  if (!videoRef.value || sequence !== requestSequence) return false
  return getMediasSignatureData(mediaId, sequence)
};
// ------定义方法------
const initPlay = (fileID, psign, url) => {
  disposePlayer()
  const options = {
    posterImage: true,
    autoplay: true,
    width: 100 + "%",
    preload: "auto",
    hlsConfig: {},
  };
  if (url) {
    const sourceType = /\.m3u8(?:$|\?)/i.test(url)
      ? 'application/x-mpegURL'
      : 'video/mp4'
    options.sources = [{ src: url, type: sourceType }];
  } else {
    options.appID = playAppId.value;
    options.fileID = fileID;
    options.psign = psign;
  }
  if (!videoRef.value) return
  const videoElement = videoRef.value
  if (!videoElement || !videoElement.isConnected) return
  const instance = new TCPlayer(videoElement, options);
  player.value = instance;
  instance.on('timeupdate', function() {
  });
  instance.on('pause', function() {
  });
  instance.on('play', function() {
  });
  instance.on('error', function() {
    ElMessage.error('视频播放失败，请刷新页面后重试');
  });
  instance.on('ended', function() {
  });
  instance.ready(() => {
    if (player.value !== instance) return
    try {
      instance.currentTime(0);
      const playResult = instance.play();
      if (playResult && typeof playResult.catch === 'function') {
        playResult.catch(() => {});
      }
    } catch (e) {
      if (player.value === instance) disposePlayer()
    }
  })
};
// 通过媒资 ID 获取视频播放信息
const getMediasSignatureData = async (mediaId, sequence) => {
  try {
    const res = await getMediasSignature({ mediaId });
    if (sequence !== requestSequence) return false
    if (res.code !== 200) {
      ElMessage.error(res.msg || "获取视频播放信息失败");
      return false;
    }
    fileId.value = res.data?.fileId || "";
    signature.value = res.data?.signature || "";
    playAppId.value = res.data?.appId || "";
    playUrl.value = res.data?.playUrl || "";
    if (!playUrl.value && (!fileId.value || !signature.value || !playAppId.value)) {
      ElMessage.error("视频播放信息不完整，无法播放");
      return false;
    }
    if (sequence !== requestSequence || !videoRef.value) return false
    // 官方视频使用 psign 播放，自有视频使用动态防盗链 URL
    initPlay(fileId.value, signature.value, playUrl.value);
    return true;
  } catch (e) {
    if (sequence === requestSequence) {
      ElMessage.error(e?.message || "获取视频播放信息失败");
    }
    return false;
  }
};
// 关闭弹层
const disposePlayer = () => {
  const instance = player.value
  player.value = null
  if (instance) {
    try {
      instance.pause()
      instance.dispose()
    } catch (e) {
      // 播放器销毁失败时忽略，继续进行预览切换。
    }
  }
  // 播放器销毁后强制让 Vue 创建新的 video 节点。
  videoElementKey.value += 1
}


const handleClose = () => {
  requestSequence++
  disposePlayer()
  emit("handleClose")
}

onUnmounted(() => {
  requestSequence++
  disposePlayer()
})
// 向父组件暴露方法
defineExpose({
  getId,
});
</script>
