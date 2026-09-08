<template>
  <el-form
    :model="teachereditData"
    ref="ruleFormRef"
    :rules="rules"
    label-width="130px"
    class="demo-ruleForm"
  >
    <el-form-item label="教师名称：" prop="name">
      <div class="el-input">
        <span>{{ teacherData.name }}</span>
      </div>
    </el-form-item>
    <el-form-item label="头像：" class="el-form-icon">
      <div class="el-input-icon">
        <!-- <UploadImage
          @getFlag="getFlag"
          @getCoverUrl="getCoverUrl"
          @setUplad="setUplad"
          :upladImg="photoImg == '' ? photoImg : teacherData.icon"
          :isCourse="isCourse"
          ref="uploadImg"
        ></UploadImage> -->
        <img :src="teacherData.icon" alt="" srcset="" class="icon" />
      </div>
    </el-form-item>
    <el-form-item label="教师手机号：" prop="cellPhone">
      <div class="el-input">
        <span>{{ teacherData.cellPhone }}</span>
      </div>
    </el-form-item>
    <el-form-item label="岗位：" prop="job">
      <div class="el-input">
        <el-input
          v-model="teachereditData.job"
          @input="jobTextInput"
          show-word-limit
          class="cursour"
          width="369"
          placeholder="请输入"
        ></el-input>
        <span class="numText" :class="jobNumVal === 0 ? 'tip' : ''"
          >{{ jobNumVal }}/20</span
        >
      </div>
    </el-form-item>
    <el-form-item label="教师介绍：" prop="intro">
      <div class="el-input">
        <el-input
          v-model="teachereditData.intro"
          type="textarea"
          placeholder="请输入"
          resize="none"
          @input="introTextInput"
          show-word-limit
        ></el-input>
        <span class="numText" :class="introNumVal === 0 ? 'tip' : ''"
          >{{ introNumVal }}/200</span
        >
      </div>
    </el-form-item>
    <el-form-item label="教师形象照：" prop="photo">
      <div class="tearchUpload">
        <div class="el-input">
          <UploadImage
            @getFlag="getFlag"
            @getCoverUrl="getCoverUrl"
            @setUplad="setUplad"
            :upladImg="photoImg !== '' ? photoImg : teachereditData.photo"
            ref="uploadImg"
          ></UploadImage>
        </div>
      </div>
    </el-form-item>
    <el-form-item label="密码：" class="passwordbody">
      <div class="passwordbody el-input">
        <el-input
          type="password"
          model-value="******"
          placeholder="请输入"
          maxlength="20"
          disabled
        ></el-input>
      </div>

      <a
        href="###"
        onclick="return false;"
        style="color: #2080f7; margin-left: 20px"
        @click="editpassword"
        >修改密码</a
      >
    </el-form-item>
    <el-form-item label="旧密码：" v-show="ispassword" prop="oldPassword">
      <div class="el-input">
        <el-input
          type="password"
          v-model="teachereditData.oldPassword"
          placeholder="请输入原密码"
          @input="editTeacher"
        ></el-input>
      </div>
    </el-form-item>
    <el-form-item label="新密码：" v-show="ispassword" prop="password">
      <div class="el-input">
        <el-input
          type="password"
          v-model="teachereditData.password"
          placeholder="请输入新密码"
          @input="editTeacher"
        ></el-input>
      </div>
    </el-form-item>
    <el-form-item label="确认新密码：" v-show="ispassword" prop="checkpassword">
      <div class="el-input">
        <el-input
          type="password"
          v-model="teachereditData.checkpassword"
          placeholder="请再次输入新密码"
          @input="editTeacher"
        ></el-input>
      </div>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from "vue"
import { validateTextLength } from "@/utils/index.js"
import UploadImage from "@/components/UploadImage/index.vue"

const emit = defineEmits(["editTeacher", "handleSaveTeacher"])
const props = defineProps({
  teacherData: {
    type: Object,
    default: () => ({}),
  },
})

const ruleFormRef = ref()
const ispassword = ref(false)
const photoImg = ref("")
const introNumVal = ref(0)
const jobNumVal = ref(0)
const teachereditData = reactive({
  job: "",
  intro: "",
  photo: "",
  oldPassword: "",
  password: "",
  checkpassword: "",
})

const payload = () => ({
  job: teachereditData.job,
  intro: teachereditData.intro,
  photo: teachereditData.photo,
  oldPassword: teachereditData.oldPassword,
  password: teachereditData.password,
})

watch(
  () => props.teacherData,
  (data) => {
    teachereditData.job = data?.job || ""
    teachereditData.intro = data?.intro || ""
    teachereditData.photo = data?.photo || ""
    introNumVal.value = validateTextLength(teachereditData.intro, 200).numVal
    jobNumVal.value = validateTextLength(teachereditData.job, 20).numVal
  },
  { immediate: true, deep: true }
)

watch(photoImg, value => {
  if (value) teachereditData.photo = value
})

const clearPasswords = () => {
  teachereditData.oldPassword = ""
  teachereditData.password = ""
  teachereditData.checkpassword = ""
  ruleFormRef.value?.clearValidate(["oldPassword", "password", "checkpassword"])
}

const editpassword = () => {
  ispassword.value = !ispassword.value
  if (!ispassword.value) clearPasswords()
}

const baseRules = {
  job: [
    { required: true, message: "岗位不能为空", trigger: "blur" },
    { min: 2, max: 20, message: "岗位长度必须为 2 到 20 个字符", trigger: "blur" },
    { pattern: /^[\u4e00-\u9fa5_a-zA-Z0-9]+$/, message: "岗位只能包含中英文、数字和下划线", trigger: "blur" },
  ],
  intro: [
    { required: true, message: "教师介绍不能为空", trigger: "blur" },
    { min: 10, max: 200, message: "教师介绍长度必须为 10 到 200 个字符", trigger: "blur" },
  ],
  photo: [
    { required: true, message: "请上传教师形象照", trigger: "change" },
  ],
}
const passwordRules = {
  oldPassword: [
    { required: true, message: "原密码不能为空", trigger: "blur" },
  ],
  password: [
    { required: true, message: "新密码不能为空", trigger: "blur" },
    { min: 6, max: 16, message: "新密码长度必须为 6 到 16 个字符", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value === teachereditData.oldPassword) {
          return callback(new Error("新密码不能与原密码相同"))
        }
        callback()
      },
      trigger: "blur",
    },
  ],
  checkpassword: [
    { required: true, message: "请再次输入新密码", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value !== teachereditData.password) {
          return callback(new Error("两次输入的新密码不一致"))
        }
        callback()
      },
      trigger: "blur",
    },
  ],
}
const profileChanged = computed(() => {
  const current = props.teacherData || {}
  return teachereditData.job !== (current.job || "")
    || teachereditData.intro !== (current.intro || "")
    || teachereditData.photo !== (current.photo || "")
})
const rules = computed(() => {
  if (!ispassword.value) return baseRules
  // 仅修改密码时不强制校验历史资料；资料和密码同时修改时仍完整校验资料。
  return profileChanged.value ? { ...baseRules, ...passwordRules } : passwordRules
})

const notifyChange = () => emit("editTeacher", payload())
const getFlag = () => {}
const getCoverUrl = (value) => {
  photoImg.value = value
  teachereditData.photo = value
  notifyChange()
}
const setUplad = () => {
  photoImg.value = ""
  teachereditData.photo = ""
  notifyChange()
}
const editTeacher = notifyChange
const introTextInput = () => {
  nextTick(() => {
    const value = validateTextLength(teachereditData.intro, 200)
    teachereditData.intro = value.val
    introNumVal.value = value.numVal
    notifyChange()
  })
}
const jobTextInput = () => {
  nextTick(() => {
    const value = validateTextLength(teachereditData.job, 20)
    teachereditData.job = value.val
    jobNumVal.value = value.numVal
    notifyChange()
  })
}

const rulespassWord = async () => {
  const valid = await ruleFormRef.value.validate().catch(() => false)
  if (!valid) return
  emit("handleSaveTeacher", payload())
}

defineExpose({ rulespassWord })
</script>
<style lang="scss" scoped>
:deep(.el-input__count) {
  height: 36%;
  font-weight: 400;
  font-size: 12px;
  color: #b5abab;
}
.el-form-icon {
  position: absolute;
  right: 235px;
  top: 178px;
  .el-input-icon {
    font-weight: 400;
    font-size: 12px;
    color: #000;
    .icon {
      width: 89px !important;
      height: 89px !important;
      border-radius: 50%;
      overflow: hidden;
    }
    :deep(.avatar-uploader) {
      width: 89px !important;
      height: 89px !important;
      border-radius: 50%;
      overflow: hidden;

      .el-upload.el-upload {
        width: 89px !important;
        height: 89px !important;
        // 将图片的边框设置为圆形
        border-radius: 50%;
        overflow: hidden;
      }
    }
    :deep(.Prompttext) {
      display: none;
    }
  }
  :deep(.el-input__wrapper) {
    padding-right: 0;
  }
}
</style>
<style lang="scss">
.cursour {
  .el-input__wrapper {
    padding-right: 11px !important;
  }
}
</style>
<style lang="scss" scoped>
.el-input {
  width: 369px;
}
.el-form-item {
  // margin-bottom: 10px;
}
.password {
  margin-top: 10px;
}
:deep(.tearchUpload){
 .uploadBox{
    font-weight: 400;
    font-size: 12px;
    color: #887E7E;
    .avatar-uploader,.avatar-uploader .el-upload{
      width: 160px;
      height: 160px;
      .el-upload{
        .avatar{
          width: 158px;
          height: 158px;
        }
      }
    }
  } 
}
</style>
<style lang="scss">
.passwordbody {
  label {
    margin-top: 10px;
  }
}
</style>