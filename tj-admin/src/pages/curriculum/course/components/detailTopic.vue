<!--课程题目-->
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
              <div class="textR">
                <span class="textForbidden">添加阶段考试</span>
              </div>
            </div>
          </template>
          <div class="itemCon" v-if="item.sections.length > 0">
            <div class="headTitle">
              <span>序号</span>
              <span>小节名称</span>
              <span>题目</span>
              <span>题目数目</span>
              <span>题目分数</span>
              <span>操作</span>
            </div>
            <div class="item">
              <ul>
                <li v-for="(val, i) in item.sections" :key="i">
                  <div class="leftLine"></div>
                  <div class="con">
                    <!-- 序号 -->
                    <div>
                      <div v-if="val.type !== 3">
                        <span v-if="i + 1 > 9">{{ i + 1 }}</span
                        ><span v-else>{{ "0" + (i + 1) }}</span>
                      </div>
                    </div>
                    <div>
                      <span>{{ val.name }}</span>
                    </div>
                    <div>
                      <span
                        @click="handleWatch(val)"
                        :class="
                          val.subjectNum > 0 ? 'textDefault' : 'textForbidden'
                        "
                      >
                        查看题目</span
                      >
                    </div>
                    <div>
                      {{ val.subjectNum }}
                    </div>
                    <div>
                      {{ val.totalScore }}
                    </div>
                    <div>
                      <span class="textForbidden" v-if="val.type === 3"
                        >删除阶段考试</span
                      >
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
    <!-- 查看题目弹层 -->
    <detailwatchTopic
      :dialogVisible="dialogVisible"
      :bizId="bizId"
      :score="score"
      @handleClose="handleWatchClose"
    ></detailwatchTopic>
    <!-- end -->
    <!-- 删除弹层 -->
    <!-- end -->
  </div>
</template>
<script setup>
import { ref, watch } from "vue";
import detailwatchTopic from "./detailwatchTopic.vue";

const props = defineProps({
  courseTopicData: {
    type: Array,
    default: () => [],
  },
});

const dialogVisible = ref(false);
const itemData = ref([]);
const activeNames = ref(["1"]);
const bizId = ref(null);
const score = ref(0);

// 详情页只展示父组件传入的课程目录，不在此处保存或修改课程数据。
watch(
  () => props.courseTopicData,
  (catalogue) => {
    itemData.value = Array.isArray(catalogue) ? catalogue : [];
  },
  { immediate: true, deep: true }
);

// 有关联题目时打开只读弹窗，目录 ID 直接作为题目业务关联 ID。
const handleWatch = (section) => {
  if (!section?.id || Number(section.subjectNum || 0) <= 0) return;
  bizId.value = section.id;
  score.value = Number(section.totalScore || 0);
  dialogVisible.value = true;
};

const handleWatchClose = () => {
  dialogVisible.value = false;
};
</script>
<style lang="scss" scoped>
.courseList .titText .textL span {
  font-size: 16px;
  color: #332929;
}
.headTitle {
  color: #332929;
}
.textR {
  font-size: 14px;
  font-family: PingFangSC-Regular;
  font-weight: 400;
}
</style>
