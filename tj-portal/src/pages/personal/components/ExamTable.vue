<!-- 我的考试 - 列表表格  -->
<template>
  <div class="classCards fx-1">
    <el-table class="table" :data="data" style="">
      <el-table-column prop="courseName" center label="课程">
        <template #default="scope">
          <div>{{scope.row.courseName}}</div>
        </template>
      </el-table-column>
      <el-table-column center prop="targetName" label="练习/测试" align="center" min-width="160" />
      <el-table-column prop="submittedTime" align="center" label="提交时间" width="180">
        <template #default="scope">
          <span>{{ formatDateTime(scope.row.submittedTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" align="center" label="状态" width="120">
        <template #default="scope">
          <span>{{ statusText(scope.row.status, 'practice') }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="finalScore" align="center" label="分数" width="100">
        <template #default="scope">
          <span>{{ scope.row.status === 'IN_PROGRESS' ? '--' : `${scope.row.finalScore || 0}/${scope.row.totalScore || 0}` }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" width="100">
        <template #default="scope">
          <div
            class="font-bt1"
            @click="() => $router.push({
              name: 'myExamDetails',
              query: {
                id: scope.row.id,
                courseId: scope.row.courseId,
                courseName: scope.row.courseName
              }
            })"
          >查看</div>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
<script setup>
import { formatDateTime, statusText } from '@/pages/aiEducation/dictionaries'

// 介绍父组件传来的标题
defineProps({
  data:{
    type: Array,
    default: []
  }
})  
</script>
<style lang="scss" scoped>
.classCards{
  margin-top: 20px;
  line-height: 30px;
  
  .table{
    border: solid 1px #ebeef5;
    border-bottom: none;
  }
  img{
    width: 236px;
    height: 132px;
    border-radius: 4px;
  }
  .info{
    line-height: 30px;
    font-size: 14px;
    .tit{
      font-size: 20px;
      font-weight: 500;
      line-height: 40px;
    }
  }
}
</style>
