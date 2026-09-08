<template>
  <div class="contentBox">
    <div class="bg-wt radius marg-tp-20">
      <div class="detailBox">
        <div class="tit">
          <span>个人信息</span>
          <span class="tooltipIcon">
            <span class="hover">用户名、手机号、角色请联系管理员进行修改</span>
          </span>
        </div>

        <!-- 用户类型：1-员工，2-学生，3-教师；学生不能登录管理端。 -->
        <Teacher
          v-if="userInfo.data.type === 3"
          ref="teacher"
          :teacher-data="teacherData.data"
          @edit-teacher="editTeacher"
          @handle-save-teacher="handleSaveTeacher"
        />
        <Staffs
          v-else-if="userInfo.data.type === 1"
          ref="user"
          :staffs-data="staffsData.data"
          @edit-staffs="editStaffs"
          @handle-save-user="handleSaveUser"
        />
      </div>
    </div>
    <div class="BoxBottom">
      <div class="btn">
        <el-button class="button buttonSub" @click="handleGetback">取消</el-button>
        <el-button
          v-if="userInfo.data.type === 3"
          class="button primary"
          @click="saveTeacher"
        >保存并返回</el-button>
        <el-button
          v-else-if="userInfo.data.type === 1"
          class="button primary"
          @click="saveUser"
        >保存并返回</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue"
import { useRouter } from "vue-router"
import { ElMessage } from "element-plus"
import Teacher from "./components/teacher.vue"
import Staffs from "./components/user.vue"
import {
  getUserInfo,
  updateCurrentUserProfile,
  updateCurrentUserPassword,
} from "@/api/user.js"

const router = useRouter()
const teacher = ref()
const user = ref()
const teacherData = reactive({ data: {} })
const staffsData = reactive({ data: {} })
const userInfo = reactive({ data: {} })
const teacherEditData = reactive({ data: {} })
const staffsEditData = reactive({ data: {} })

const showMessage = (message, type = "error") => {
  ElMessage({ message, type, showClose: false })
}

const responseMessage = (response, fallback) =>
  response?.msg || response?.message || fallback

const editTeacher = (value) => {
  teacherEditData.data = value || {}
}

const editStaffs = (value) => {
  staffsEditData.data = value || {}
}

const loadUserInfo = async () => {
  try {
    const response = await getUserInfo()
    if (response?.code !== 200 || !response.data) {
      showMessage(responseMessage(response, "获取个人信息失败"))
      return
    }
    userInfo.data = response.data
    if (response.data.type === 3) {
      teacherData.data = response.data
    } else if (response.data.type === 1) {
      staffsData.data = response.data
    } else {
      showMessage("当前账号不支持访问管理端个人中心")
    }
  } catch (error) {
    showMessage(error?.response?.data?.msg || "获取个人信息失败，请稍后重试")
  }
}

const saveTeacher = () => {
  // 子组件先完成资料与可选密码区域的统一校验，再回传保存数据。
  teacher.value?.rulespassWord()
}

const handleSaveTeacher = async (payload) => {
  const data = payload || teacherEditData.data || {}
  const current = teacherData.data || {}
  const profileChanged =
    data.job !== current.job ||
    data.intro !== current.intro ||
    data.photo !== current.photo
  const passwordChanged = Boolean(data.oldPassword || data.password)

  if (!profileChanged && !passwordChanged) {
    showMessage("未检测到需要保存的修改", "info")
    return
  }

  try {
    if (profileChanged) {
      const response = await updateCurrentUserProfile({
        name: current.name,
        gender: current.gender,
        icon: current.icon,
        job: data.job,
        intro: data.intro,
        photo: data.photo,
      })
      if (response?.code !== 200) {
        throw new Error(responseMessage(response, "个人资料保存失败"))
      }
    }

    if (passwordChanged) {
      const response = await updateCurrentUserPassword({
        oldPassword: data.oldPassword,
        password: data.password,
      })
      if (response?.code !== 200) {
        throw new Error(responseMessage(response, "密码修改失败"))
      }
    }

    showMessage("保存成功", "success")
    router.go(-1)
  } catch (error) {
    showMessage(error?.response?.data?.msg || error?.message || "保存失败，请稍后重试")
  }
}

const saveUser = () => {
  // 员工个人中心当前只开放密码修改。
  user.value?.rulespassWord()
}

const handleSaveUser = async (payload) => {
  const data = payload || staffsEditData.data
  if (!data?.oldPassword || !data?.password) {
    showMessage("请先点击“修改密码”并填写完整信息", "info")
    return
  }

  try {
    const response = await updateCurrentUserPassword({
      oldPassword: data.oldPassword,
      password: data.password,
    })
    if (response?.code !== 200) {
      throw new Error(responseMessage(response, "密码修改失败"))
    }
    showMessage("密码修改成功", "success")
    router.go(-1)
  } catch (error) {
    showMessage(error?.response?.data?.msg || error?.message || "密码修改失败，请稍后重试")
  }
}

const handleGetback = () => {
  router.push({ path: "/main" })
}

onMounted(loadUserInfo)
</script>
<style lang="scss" scoped>
.marg-tp-20 {
  border-bottom-left-radius: 0;
  border-bottom-right-radius: 0;
}
.detailBox {
  padding-left: 65px;
  padding-top: 41px;

  .tit {
    margin-bottom: 10px;
    margin-left: 45px;
  }
}
.BoxBottom {
  height: 100px;
  border-top: 1px solid #f5efee;
  margin-bottom: 20px;
  background: #ffffff;
  border-radius: 0 0 12px 12px;
  .btn {
    // 让按钮居中
    display: flex;
    justify-content: center;
    align-items: center;
  }
  .button {
    width: 138px;
    height: 40px;
  }
}
.tooltipIcon {
  background: url(@/assets/btn-xiangqing.png) no-repeat;
  background-size: contain;
  display: inline-block;
  width: 19px;
  height: 19px;
  position: absolute;
  left: 432px;
  top: 129px;
  .hover {
    position: absolute;
    display: none;
    z-index: 9;
    left: -20px;
    top: 30px;
    width: 280px;
    border-radius: 6px;
    line-height: 40px;
    padding: 0 10px;
    background: #ffffff;
    box-shadow: 0 0 8px 1px rgba(0, 0, 0, 0.05);
    font-weight: 400;
    font-size: 12px;
    color: #332929;
    &::after {
      position: absolute;
      background: #ffffff;
      top: -5px;
      left: 23px;
      z-index: -1;
      content: '';
      width: 10px;
      height: 10px;
      transform: rotate(45deg);
      box-shadow: 0 0 8px 1px rgba(0, 0, 0, 0.05);
    }
    &::before {
      position: absolute;
      content: '';
      background: #fff;
      width: 50px;
      height: 10px;
      top: 0px;
      left: 10px;
    }
  }
  &:hover .hover {
    display: block;
  }
}
</style>
<style lang="scss">
.el-popper.is-light {
  box-shadow: 0 0 8px 1px rgba(0, 0, 0, 0.05);
  .el-popper__arrow {
    box-shadow: 0 0 8px 1px rgba(0, 0, 0, 0.05);
  }
}
</style>

