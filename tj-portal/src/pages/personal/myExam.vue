<!-- 我的考试 -->
<template>
  <div class="myExamWrapper">
    <div class="personalCards" v-if="myExamData != null">
      <CardsTitle class="marg-bt-20" title="我的考试" />
      <div v-if="count == 0" class="nodata">
        <Empty ></Empty>
      </div>
      <ExamTable v-if="count > 0" :data="myExamData"></ExamTable>
      <div class="pageination" v-if="count > 0">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="count"
          class="mt-4"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
  </div>
</template>
<script setup>

/** 数据导入 **/
import { onMounted, ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import { getExamList } from "@/api/class.js";
import { getClassDetails } from "@/api/classDetails.js";

// 组件导入
import CardsTitle from './components/CardsTitle.vue'
import ExamTable from './components/ExamTable.vue'
import Empty from "@/components/Empty.vue";

// mounted生命周期
onMounted(async () => {
  // 查询我的考试记录
  getExamListData()
});

/** 方法定义 **/

// 查询我的考试记录
const myExamData = ref(null)
const count = ref(0)
const params = reactive({
  pageNo: 1,
  pageSize: 10,
})
// 查询我的考试记录
const getExamListData = async () => {
  try {
    const res = await getExamList(params)
    if (res.code !== 200 || !res.data) {
      ElMessage.error(res.msg || '考试记录加载失败')
      return
    }

    const list = res.data.list || []
    const courseIds = [...new Set(list.map(item => item.courseId).filter(Boolean))]
    const courseResults = await Promise.allSettled(
      courseIds.map(id => getClassDetails(id))
    )
    const courseNameMap = new Map()
    courseResults.forEach((result, index) => {
      if (result.status === 'fulfilled' && result.value?.code === 200) {
        courseNameMap.set(String(courseIds[index]), result.value.data?.name)
      }
    })

    myExamData.value = list.map(item => ({
      ...item,
      courseName: courseNameMap.get(String(item.courseId)) || (item.courseId ? `课程 ${item.courseId}` : '--'),
    }))
    count.value = Number(res.data.total || 0)
  } catch (error) {
    ElMessage.error(error?.message || '考试记录加载失败')
  }
}

const handleSizeChange = (val) => {
  params.pageSize = val
  getExamListData()
}
const handleCurrentChange = (val) => {
  params.pageNo = val
  getExamListData()
}
</script>
<style lang="scss" src="./index.scss"> </style>
