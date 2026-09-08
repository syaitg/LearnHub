<template>
  <el-form
    :model="staffseditData"
    ref="ruleFormRef"
    :rules="rules"
    label-width="130px"
    class="demo-ruleForm"
  >
    <el-form-item label="头像：" prop="icon" class="el-form-icon">
      <div class="el-input-icon">
        <!-- <UploadImage
        @getFlag="getFlag"
        @getCoverUrl="getCoverUrl"
        @setUplad="setUplad"
        :upladImg="photoImg == '' ? photoImg : staffsData.icon"
        :isCourse="isCourse"
        ref="uploadImg"
      ></UploadImage> -->
        <!-- 当前版本暂不支持更换头像功能 -->
        <img
          :src="staffsData.icon"
          alt=""
          srcset=""
          class="icon"
        />
      </div>
    </el-form-item>
    <el-form-item label="用户名称：" prop="name">
      <div class="el-input">
        <span>{{ staffsData.name }}</span>
      </div>
    </el-form-item>
    <el-form-item label="用户手机号：" prop="cellPhone">
      <div class="el-input">
        <span>{{ staffsData.cellPhone }}</span>
      </div>
    </el-form-item>
    <el-form-item label="角色：">
      <div class="el-input">
        <span>{{ staffsData.roleName }}</span>
      </div>
    </el-form-item>
    <el-form-item label="密码：" class="passwordbody">
      <div class="password el-input">
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
    <el-form-item label="旧密码：" v-show="isCourse" prop="oldPassword">
      <div class="el-input">
        <el-input
          type="password"
          v-model="staffseditData.oldPassword"
          placeholder="请输入原密码"
          @input="editStaffs"
        ></el-input>
      </div>
    </el-form-item>
    <el-form-item label="新密码：" v-show="isCourse" prop="password">
      <div class="el-input">
        <el-input
          type="password"
          v-model="staffseditData.password"
          placeholder="请输入新密码"
          @input="editStaffs"
        ></el-input>
      </div>
    </el-form-item>
    <el-form-item label="确认新密码：" v-show="isCourse" prop="checkpassword">
      <div class="el-input">
        <el-input
          type="password"
          v-model="staffseditData.checkpassword"
          placeholder="请再次输入新密码"
          @input="editStaffs"
        ></el-input>
      </div>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { computed, reactive, ref } from "vue"

const emit = defineEmits(["editStaffs", "handleSaveUser"])
defineProps({
  staffsData: {
    type: Object,
    default: () => ({}),
  },
})

const ruleFormRef = ref()
const isCourse = ref(false)
const staffseditData = reactive({
  oldPassword: "",
  password: "",
  checkpassword: "",
})

const clearPasswords = () => {
  staffseditData.oldPassword = ""
  staffseditData.password = ""
  staffseditData.checkpassword = ""
  ruleFormRef.value?.clearValidate()
}

const editpassword = () => {
  isCourse.value = !isCourse.value
  if (!isCourse.value) clearPasswords()
}

const rules = computed(() => ({
  oldPassword: [
    { required: true, message: "原密码不能为空", trigger: "blur" },
  ],
  password: [
    { required: true, message: "新密码不能为空", trigger: "blur" },
    { min: 6, max: 16, message: "新密码长度必须为 6 到 16 个字符", trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value === staffseditData.oldPassword) {
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
        if (value !== staffseditData.password) {
          return callback(new Error("两次输入的新密码不一致"))
        }
        callback()
      },
      trigger: "blur",
    },
  ],
}))

const editStaffs = () => {
  emit("editStaffs", {
    oldPassword: staffseditData.oldPassword,
    password: staffseditData.password,
  })
}

const rulespassWord = async () => {
  if (!isCourse.value) {
    emit("handleSaveUser", null)
    return
  }
  const valid = await ruleFormRef.value.validate().catch(() => false)
  if (!valid) return
  emit("handleSaveUser", {
    oldPassword: staffseditData.oldPassword,
    password: staffseditData.password,
  })
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
.uploadBox {
  font-weight: 400;
  font-size: 12px;
  color: #000;
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
  :deep(.el-input__wrapper){
      padding-right: 0;
    }
}
</style>
<style lang="scss" scoped>
.el-input{
  width: 369px;
}
.el-form-item{
  margin-bottom: 10px;
}
.password{
  margin-top: 10px;
  }
</style>
<style lang="scss">
.passwordbody{
  label{
    margin-top: 10px;
  }
}
</style>