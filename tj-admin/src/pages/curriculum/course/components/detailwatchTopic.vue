<template>
  <div class="topicBox" v-if="dialogVisible">
    <el-dialog
      :model-value="dialogVisible"
      title="查看题目"
      :before-close="handleClose"
    >
      <div class="transHearder">
        <span class="transHearder-title">已选题目：</span><span class="transHearder-item">{{ tableName.length }}</span><span class="transHearder-title">&nbsp;&nbsp;&nbsp;&nbsp;  总分：</span><span class="transHearder-item">{{ score }}</span>分
      </div>
      <!-- 题目列表 -->
      <el-table
        :data="tableName"
        border
        :stripe="true"
        v-loading="loading"
        row-key="id"
        height="475"
      >
        <el-table-column type="index" align="center" width="60px" label="序号" />
        <el-table-column label="题目名称" min-width="250" class-name="textLeft">
          <template #default="scope">
            <!-- 添加el-Popover弹出框 -->
            <el-popover
              placement="right"
              width="300"
              trigger="hover"
              content=""
              @show="handleShow(scope)"
            >
            <template #reference>
              <div class="head">
                <div class="ellipsisHidden2" v-html="scope.row.name"></div>
              </div>
            </template>
            <div class="popicCon">
                <div class="tit" v-html="tipicData.name || scope.row.name"></div>
                <ul>
                  <li v-for="(item, index) in (tipicData.options || scope.row.options || [])" :key="index">
                    {{numLetter(index+1)}}.<span v-html="item"></span>
                  </li>
                </ul>
                <div class="answer">
                  <div class="item">正确答案：
                    <span
                      v-for="(val, index) in (tipicData.answers || scope.row.answers || [])"
                      :key="index"
                    >
                    {{numLetter(val)}}
                    </span>
                  </div>
                  <div class="item">答案解析：<div v-html="(tipicData.analysis ?? scope.row.analysis) || '无'"></div></div>
                </div>
                
              </div>
              <div class="topicFoot">
                <p>题目难度：{{(tipicData.difficult ?? scope.row.difficult)===1?'简单':(tipicData.difficult ?? scope.row.difficult)===2?'中等':'困难'}}</p>
                <p>题目分数：{{tipicData.score ?? scope.row.score}}</p>
              </div>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <span class="dialog-footer">
          <el-button class="button primary" @click="handleClose">返回</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, watch } from "vue"
import { numLetter } from "@/utils/index"
import { getSubjectsList } from "@/api/curriculum"
import { getDetails } from "@/api/title"

const props = defineProps({
  dialogVisible: {
    type: Boolean,
    default: false,
  },
  score: {
    type: Number,
    default: 0,
  },
  // 题目所属的课程目录 ID。
  bizId: {
    type: [Number, String],
    default: null,
  },
})

const emit = defineEmits(["handleClose"])
const loading = ref(false)
const score = ref(0)
const tableName = ref([])
const tipicData = ref({})

// 分数为 0 时也需要刷新，避免保留上一个小节的数据。
watch(
  () => props.score,
  (value) => {
    score.value = Number(value || 0)
  },
  { immediate: true }
)

// 弹窗打开或切换目录时重新加载题目列表。
watch(
  [() => props.dialogVisible, () => props.bizId],
  ([visible]) => {
    if (visible) {
      tipicData.value = {}
      getCheckList()
    }
  },
  { immediate: true }
)

const getCheckList = async () => {
  if (!props.bizId) {
    tableName.value = []
    return
  }
  loading.value = true
  try {
    const response = await getSubjectsList(props.bizId)
    tableName.value = response.code === 200 ? (response.data || []) : []
  } catch (error) {
    tableName.value = []
  } finally {
    loading.value = false
  }
}

// 只读弹窗关闭时不向父组件回写题目 ID。
const handleClose = (done) => {
  tableName.value = []
  tipicData.value = {}
  emit("handleClose")
  if (typeof done === "function") done()
}

// 鼠标经过时加载完整题目信息，用于展示选项、答案和解析。
const handleShow = async ({ row }) => {
  if (!row?.id) return
  tipicData.value = {}
  try {
    const response = await getDetails(row.id)
    if (response.code === 200) tipicData.value = response.data || {}
  } catch (error) {
    tipicData.value = {}
  }
}
</script>
<style lang="scss" scoped>
.popicCon {
  color: #332929;
}
.topicFoot {
  color: #332929;
}
.topicBox {
  .btn {
    display: flex;
    padding-top: 0;
  }
  :deep(.el-transfer-panel) {
    width: 46% !important;
  }
}

:deep(.el-dialog) {
  width: 788px !important;
  height: 700px;
  .el-form-item {
    display: flex;
  }
  .el-dialog__body {
    padding-left: 30px;
    padding-right: 30px;
    padding-top: 20px;
    border-top: 1px solid #e5e4e4;
    padding-bottom: 32px;
  }
  // .searchForm {
  //   padding-top: 0 !important;
  //   height: 40px !important;
  //   margin-bottom: 20px !important;
  // }
}
.transHearder {
    right: 14px;
    font-family: PingFangSC-Medium;
    font-weight: 500;
    font-size: 14px;
    margin-bottom: 21px;
    .transHearder-title{
      font-family: PingFangSC-Medium;
      font-weight: 500;
      font-size: 14px;
      color: #332929;
    }
    .transHearder-item{
      color: #FF734F;
      font-family: PingFangSC-Medium;
      font-weight: 500;
      font-size: 14px;
    }
  }
  :deep(.el-table){
    thead{
      height: 40px !important;
    }
    tr.el-table__row{
      height: 60px
    }
    td.el-table__cell{
      font-family: PingFangSC-Regular;
      font-weight: 400;
      font-size: 14px;
      color: #332929;
  }
  th.el-table__cell.is-leaf{
    font-family: PingFangSC-Medium;
    font-weight: 500;
    font-size: 14px;
    color: #332929;
    background: #FDFCFA;
  }

}
  
</style>
<style lang="scss">
  .el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell{
    background: #FDFCFA;
  }
</style>
