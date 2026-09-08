<!-- 个人设置 -->
<template>
  <div class="mySetWrapper content">
    <CardsTitle class="marg-bt-40" title="个人设置" />
    <TableSwitchBar :data="tabData" @changeTable="checkHandle"></TableSwitchBar>  
    <div v-if="act == 0" class="fx-sb pd-tp-30">
      <div>
        <div class="fx">
          <!-- 一期先不加  放到二期 -->
          <!-- <div class="item fx">
            <span class="lab">账号：</span><el-input v-model="input" placeholder="请输入内容"></el-input>
          </div> -->
          <div class="item fx">
            <span class="lab">昵称：</span> <el-input v-model="user.name" placeholder="请输入内容"></el-input>
          </div>
        </div>
        <div class="item fx">
          <span class="lab">性别：</span>
          <el-radio-group class="radioGroup" v-model="user.gender">
            <el-radio :label="0">男</el-radio>
            <el-radio :label="1">女</el-radio>
          </el-radio-group>
        </div>
        <div class="item fx">
          <div class="bt" @click="updateUserInfoHandle">更新信息</div>
        </div>
      </div>
      <div>
        <el-upload
          class="avatar-uploader"
          :action="actions"
          :show-file-list="false"
          :on-success="handleAvatarSuccess"
          :headers="uploadHeaders"
          >
          <img v-if="imageUrl" :src="imageUrl" class="avatar">
          <i v-else class="el-icon-plus avatar-uploader-icon"></i>
          <div class="uploadBut"><span>上传头像</span></div>
        </el-upload>
      </div>
    </div>
    <div v-else class="set pd-tp-30">
      <div class="line fx-sb"><div><span>密码</span> 已设置登录密码</div><span class="font-bt" @click="openPasswordDialog">修改</span></div>
      <div class="line fx-sb"><div><span>手机号</span> {{ maskPhone(userInfo && userInfo.cellPhone) }}</div><span class="font-bt" @click="unsupportedSecurityChange('手机号')">修改</span></div>
      <div class="line fx-sb"><div><span>邮箱</span> {{ userInfo && userInfo.email ? userInfo.email : '未绑定' }}</div><span class="font-bt" @click="unsupportedSecurityChange('邮箱')">修改</span></div>
    </div>
    <el-dialog v-model="passwordDialog" title="修改密码" width="420px" @closed="resetPasswordForm">
      <el-form :model="passwordForm" label-width="110px">
        <el-form-item label="原密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialog = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>

/** 数据导入 **/
import { onMounted, ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import { updateUserInfo, updateCurrentUserPassword, getUserInfo } from "@/api/user.js";
import { useUserStore } from "@/store"
import proxy from '@/config/proxy';


// 组件导入
import CardsTitle from "./components/CardsTitle.vue";
import TableSwitchBar from "@/components/TableSwitchBar.vue";

const store = useUserStore()
const userInfo = ref(store.getUserInfo || {})



const env = import.meta.env.MODE || "development"
const actions = proxy[env].host+'/ms/files'
const uploadHeaders = {authorization: store.getToken}
const tabData = [
  {id: 0, name: '基本信息'},
  {id: 1, name: '安全设置'}
]
// 切换基本信息和安全设置
const act = ref(0)
const checkHandle = val => {
  act.value = val
}
// 更新信息的参数
const user = reactive({
  name: userInfo.value.name || '',
  icon: userInfo.value.icon || '',
  gender: userInfo.value.gender ?? 0
})
// 图片上传
const imageUrl = ref(user.icon)
const applyUserInfo = data => {
  const info = data || {}
  userInfo.value = info
  user.name = info.name || ''
  user.icon = info.icon || ''
  user.gender = info.gender ?? 0
  imageUrl.value = user.icon
}
const loadUserInfo = async () => {
  if (userInfo.value.id || userInfo.value.name) return
  try {
    const res = await getUserInfo()
    if (res.code === 200 && res.data) {
      await store.setUserInfo(res.data)
      applyUserInfo(res.data)
    } else {
      ElMessage.error(res.msg || '用户信息获取失败，请重新登录')
    }
  } catch (error) {
    ElMessage.error('用户信息获取失败，请稍后重试')
  }
}
function handleAvatarSuccess(res, file) {
  if (res.code == 200) {
    imageUrl.value = URL.createObjectURL(file.raw);
    user.icon = res.data.path
  } else {
    ElMessage({
      message: '图片上传出错，请联系管理员',
      type: 'error'
    })
  }
}

// 更改密码 手机号
const passwordDialog = ref(false)
const passwordSubmitting = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  password: '',
  confirmPassword: ''
})
const openPasswordDialog = () => {
  passwordDialog.value = true
}
const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.password = ''
  passwordForm.confirmPassword = ''
}
const submitPasswordChange = async () => {
  if (!passwordForm.oldPassword || !passwordForm.password || !passwordForm.confirmPassword) {
    ElMessage.error('请完整填写密码信息')
    return
  }
  if (passwordForm.password !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  if (passwordForm.password.length < 6 || passwordForm.password.length > 32) {
    ElMessage.error('新密码长度必须为6到32个字符')
    return
  }
  passwordSubmitting.value = true
  try {
    const res = await updateCurrentUserPassword({
      oldPassword: passwordForm.oldPassword,
      password: passwordForm.password
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功')
      passwordDialog.value = false
    } else {
      ElMessage.error(res.msg || '密码修改失败')
    }
  } catch (e) {
    ElMessage.error('密码修改失败，请稍后重试')
  } finally {
    passwordSubmitting.value = false
  }
}
const maskPhone = phone => {
  if (!phone) return '未绑定'
  return phone.length >= 7 ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : phone
}
const unsupportedSecurityChange = type => {
  ElMessage.info(`暂不支持修改${type}，请联系管理员`)
}

const updateUserInfoHandle = async () => {
  await updateUserInfo(user)
    .then(async (res) => {
      if (res.code == 200) {
        // 从新获取当前登录用户的信息
        const data = await getUserInfo()
        if (data.code == 200) {
            // 记录到store
            await store.setUserInfo(data.data)
            applyUserInfo(data.data)
            ElMessage.success('个人信息更新成功')
        } 
      } else {
        ElMessage({
          message:res.msg || "更新失败",
          type: 'error'
        });
      }
    })
    .catch(() => {
      ElMessage({
        message: "请求出错！",
        type: 'error'
      });
    });
};

onMounted(loadUserInfo)
</script>
<style lang="scss" src="./index.scss"> </style>


