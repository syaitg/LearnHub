<!--上传中视频列表-->
<template>
  <div>
    <!-- 表格 -->
    <el-table
      :data="itemData"
      border
      stripe
      v-loading="loading"
      :row-class-name="tableRowClassName"
    >
      <el-table-column
        type="selection"
        width="55"
        @selection-change="handleSelectionChange"
      >
      </el-table-column>
      <el-table-column type="index" align="center" width="100" label="序号" />
      <el-table-column prop="name" label="视频名称" min-width="160" />
      <el-table-column prop="size" label="大小（MB）" min-width="160">
        <template #default="scope">
          {{ (scope.row.size / 1024 / 1024).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="260">
        <template #default="scope">
          <div class="upload-status">
            <!-- {{scope.row.status}} -->
            <span class="status-text">{{ scope.row.statusText || "等待上传" }}</span>
            <el-progress
              v-if='["uploading", "saving", "paused", "failed"].includes(scope.row.uploadStatus)'
              :percentage="scope.row.videoUploadPercent || 0"
              :status='scope.row.uploadStatus === "failed" ? "exception" : undefined'
            ></el-progress>
          </div>
        </template>
      </el-table-column>
      <el-table-column
        fixed="right"
        label="操作"
        align="center"
        min-width="220"
      >
        <template #default="scope">
          <div class="operate">
            <span
              v-if='scope.row.uploadStatus === "uploading"'
              class="textDefault"
              @click.stop="suspendUpload(scope.row)"
              >暂停</span
            >
            <span
              v-else-if='["waiting", "paused", "failed"].includes(scope.row.uploadStatus)'
              class="textDefault"
              @click.stop="continueUpload(scope.row)"
              >{{ scope.row.uploadStatus === "failed" ? "重新上传" : "上传" }}</span
            >
            <span v-else class="textForbidden">处理中</span>
            <span
              class="textWarning"
              @click="handleOpenDelete(scope.row)"
              >删除</span
            >
          </div>
        </template>
      </el-table-column>
      <!-- 空页面 -->
      <template #empty>
        <EmptyPage :isSearch="false" :baseData="itemData"></EmptyPage>
      </template>
      <!-- end -->
    </el-table>
    <!-- end -->
    <!-- 分页 -->
    <el-pagination
      v-if="total > searchData.pageSize"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
      :page-sizes="[10, 20, 30, 40]"
      :page-size="searchData.pageSize"
      layout="total, sizes, prev, pager, next, jumper"
      :total="total"
      class="paginationBox"
    >
    </el-pagination>
    <!-- end -->
    <!-- 删除 -->
    <Delete
      :dialogDeleteVisible="dialogDeleteVisible"
      :deleteText="deleteText"
      @handleDelete="handleDelete"
      @handleClose="handleClose"
    ></Delete>
    <!-- end -->
  </div>
</template>
<script setup>
import { ref, watch } from "vue";
// 接口api
import { deleteType } from "@/api/curriculum";
// 导入组件
// 删除弹出层
import Delete from "@/components/Delete/index.vue";
// 空页面
import EmptyPage from "@/components/EmptyPage/index.vue";
// 获取父组件值、方法
const props = defineProps({
  // 上传中的视频
  baseData: {
    type: Array,
    default: () => [],
  },
  // // 总条数
  // total: {
  //   type: Number,
  //   default: 0,
  // },
  // loading
  loading: {
    type: Boolean,
    default: false,
  },
  //
  videoUploadPercent: {
    type: String,
    default: 0,
  },
  videoFlag: {
    type: Boolean,
    default: false,
  },
});
// ------定义变量------
const emit = defineEmits(); //子组件获取父组件事件传值
const deleteText = ref("此操作将取消上传视频，是否继续？"); //需要删除的提示内容
let dialogDeleteVisible = ref(false); //控制删除弹层
let videoObj = ref({});
let multipleSelection = ref([]);
let total = ref(null); //数据总条数
let itemData = ref([]); //上传中数据
let searchData = ref({
  pageSize: 10,
  pageNo: 1,
}); //搜索对象
// let baseData = ref([]);
// ------定义方法------

//当上传中删除正在上传的视频时，同步刷新当前分页数据
const refreshData = () => {
  total.value = props.baseData.length;
  const maxPage = Math.max(1, Math.ceil(total.value / searchData.value.pageSize));
  if (searchData.value.pageNo > maxPage) {
    searchData.value.pageNo = maxPage;
  }
  const begin = (searchData.value.pageNo - 1) * searchData.value.pageSize;
  const end = begin + searchData.value.pageSize;
  itemData.value = props.baseData.slice(begin, end);
};
//监听上传任务变化，确保进度、状态和删除操作即时显示
watch(() => props.baseData, refreshData, { deep: true, immediate: true });
// 确定删除
const handleDelete = async () => {
  emit("deleteUpload", videoObj.value);
};
// 设置每页条数
const handleSizeChange = (val) => {
  searchData.value.pageSize = val;
  searchData.value.pageNo = 1;
  refreshData();
};
// 当前页
const handleCurrentChange = (val) => {
  // 前端处理分页
  searchData.value.pageNo = val;
  refreshData();
};
// 打开删除弹层
const handleOpenDelete = (row) => {
  dialogDeleteVisible.value = true;
  videoObj.value = row;
};
// 关闭删除弹层
const handleClose = () => {
  dialogDeleteVisible.value = false;
};
// 全选
const handleSelectionChange = (val) => {
  multipleSelection.value = val;
};
const tableRowClassName = ({ row, rowIndex }) => {
  row.index = rowIndex;
};
// 暂停上传
const suspendUpload = (row) => {
  emit('suspendUpload',row)
};
// 恢复上传
const continueUpload = (row) => {
  emit("continueUpload", row);
};
// 向父组件暴露方法
defineExpose({
  itemData,
  dialogDeleteVisible,
});
</script>
