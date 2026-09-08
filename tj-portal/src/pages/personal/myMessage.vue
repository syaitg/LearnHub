<!-- 我的消息 -->
<template>
  <div class="message-wrapper">
    <div class="personalCards">
      <CardsTitle title="我的消息">
        <el-button v-if="unreadCount" :loading="markingAll" @click="markAllRead">
          全部标记为已读（{{ unreadCount }}）
        </el-button>
      </CardsTitle>
      <div class="toolbar">
        <el-radio-group v-model="readFilter" @change="changeFilter">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="unread">未读</el-radio-button>
          <el-radio-button label="read">已读</el-radio-button>
        </el-radio-group>
        <el-select v-model="typeFilter" class="type-select" @change="changeFilter">
          <el-option label="全部类型" value="all" />
          <el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>
      <div v-loading="loading" class="list-container">
        <div v-if="messages.length">
          <article
            v-for="item in messages"
            :key="item.id"
            class="message-item"
            :class="{ unread: !item.isRead }"
            tabindex="0"
            @click="readMessage(item)"
            @keydown.enter="readMessage(item)"
          >
            <span v-if="!item.isRead" class="unread-dot" />
            <div class="message-main">
              <div class="message-head">
                <div class="title-row">
                  <el-tag size="small" effect="plain" :type="typeMeta(item.type).tagType">
                    {{ typeMeta(item.type).label }}
                  </el-tag>
                  <h3>{{ item.title || (item.type === 4 ? "私信" : "未命名消息") }}</h3>
                </div>
                <time>{{ formatTime(item.pushTime) }}</time>
              </div>
              <p>{{ item.content || "暂无内容" }}</p>
            </div>
          </article>
        </div>
        <div v-else-if="!loading" class="empty"><Empty desc="暂无消息" /></div>
      </div>
      <div v-if="total" class="pageination">
        <el-pagination
          v-model:current-page="query.pageNo"
          v-model:page-size="query.pageSize"
          background
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @size-change="changePageSize"
          @current-change="loadMessages"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue"
import { ElMessage } from "element-plus"
import { getInboxPage, getUnreadInboxCount, markAllInboxRead, markInboxRead } from "@/api/message.js"
import Empty from "@/components/Empty.vue"
import CardsTitle from "./components/CardsTitle.vue"

const types = [
  { value: 0, label: "系统通知", tagType: "" },
  { value: 1, label: "笔记通知", tagType: "success" },
  { value: 2, label: "问答通知", tagType: "warning" },
  { value: 3, label: "其他通知", tagType: "info" },
  { value: 4, label: "私信", tagType: "danger" },
]
const query = reactive({ pageNo: 1, pageSize: 10 })
const readFilter = ref("all")
const typeFilter = ref("all")
const messages = ref([])
const total = ref(0)
const unreadCount = ref(0)
const loading = ref(false)
const markingAll = ref(false)

const typeMeta = (type) => types.find((item) => item.value === type) || { label: "未知消息", tagType: "info" }
const formatTime = (time) => time ? String(time).replace("T", " ").slice(0, 19) : ""
const errorMessage = (res, fallback) => ElMessage.error(res?.msg || fallback)

const requestParams = () => {
  const params = { ...query }
  if (readFilter.value !== "all") params.isRead = readFilter.value === "read"
  if (typeFilter.value !== "all") params.type = typeFilter.value
  return params
}

const loadUnreadCount = async () => {
  try {
    const res = await getUnreadInboxCount()
    if (res.code === 200) unreadCount.value = Number(res.data || 0)
    else errorMessage(res, "加载未读数量失败")
  } catch (error) {
    ElMessage.error("加载未读数量失败，请稍后重试")
  }
}

const loadMessages = async () => {
  loading.value = true
  try {
    const res = await getInboxPage(requestParams())
    if (res.code !== 200) {
      messages.value = []
      total.value = 0
      return errorMessage(res, "加载消息失败")
    }
    messages.value = res.data?.list || []
    total.value = Number(res.data?.total || 0)
    await loadUnreadCount()
  } catch (error) {
    messages.value = []
    total.value = 0
    ElMessage.error("获取消息失败，请稍后重试")
  } finally {
    loading.value = false
  }
}

const changeFilter = () => {
  query.pageNo = 1
  loadMessages()
}
const changePageSize = () => {
  query.pageNo = 1
  loadMessages()
}

const readMessage = async (item) => {
  if (item.isRead) return
  try {
    const res = await markInboxRead(item.id)
    if (res.code !== 200) return errorMessage(res, "标记已读失败")
    item.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    if (readFilter.value === "unread") await loadMessages()
  } catch (error) {
    ElMessage.error("标记已读失败，请稍后重试")
  }
}

const markAllRead = async () => {
  markingAll.value = true
  try {
    const res = await markAllInboxRead()
    if (res.code !== 200) return errorMessage(res, "全部已读操作失败")
    unreadCount.value = 0
    messages.value.forEach((item) => { item.isRead = true })
    ElMessage.success("已全部标记为已读")
    if (readFilter.value === "unread") await loadMessages()
  } catch (error) {
    ElMessage.error("全部已读操作失败，请稍后重试")
  } finally {
    markingAll.value = false
  }
}

onMounted(loadMessages)
</script>

<style lang="scss" scoped>
.personalCards { min-height: 560px; }
.toolbar { display: flex; justify-content: space-between; gap: 20px; padding: 24px 0 18px; border-bottom: 1px solid #eee; }
.type-select { width: 150px; }
.list-container { min-height: 390px; }
.message-item { position: relative; display: flex; padding: 20px 8px 20px 18px; border-bottom: 1px solid #eee; cursor: pointer; transition: background-color .2s; }
.message-item:hover, .message-item:focus-visible { background: #f8f9fb; outline: none; }
.message-item.unread { background: #fffaf5; }
.message-item.unread h3 { color: #19232b; font-weight: 600; }
.unread-dot { position: absolute; top: 27px; left: 2px; width: 7px; height: 7px; border-radius: 50%; background: #ff734c; }
.message-main { width: 100%; min-width: 0; }
.message-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.title-row { display: flex; align-items: center; min-width: 0; gap: 10px; }
h3 { overflow: hidden; margin: 0; color: #3c474f; font-size: 16px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
time { flex: none; color: #8c969f; font-size: 13px; }
p { margin: 12px 0 0; color: #5f6b75; font-size: 14px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.empty { height: 390px; }
@media (max-width: 768px) {
  .toolbar, .message-head { align-items: flex-start; flex-direction: column; }
  .type-select { width: 100%; }
}
</style>
