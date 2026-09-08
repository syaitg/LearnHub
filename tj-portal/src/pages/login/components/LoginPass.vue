<!-- 登录页面 - 用户名密码登录 -->
<template>
  <div class="loginPass">
    <el-form
      ref="formRef"
      :model="fromData"
      :rules="rules"
      label-width="0px"
      class="demo-dynamic"
    >
      <el-form-item prop="username" label="">
        <el-input v-model="fromData.username" placeholder="请输入用户名或手机号" />
      </el-form-item>
      <el-form-item prop="password" label="">
        <el-input type="password" show-password v-model="fromData.password" placeholder="请输入密码" />
      </el-form-item>
      <el-form-item class="marg-b-10">
        <div class="fx-sb">
            <div>
                <el-checkbox v-model="fromData.rememberMe" label="7天免登录" size="large" />
            </div>
            <div>找回密码</div>
        </div>
      </el-form-item>
      <el-form-item class="marg-bt-15">
        <div class="bt" @click="submitForm(formRef)">登 录</div>
      </el-form-item>
    </el-form>
    <div class="font-bt text-center" @click="goRegister">
        去注册
    </div>
  </div>
</template>
<script setup>
// 数据导入
import { reactive, ref } from "vue";
import { useRouter } from 'vue-router'
import { userLogins, getUserInfo } from "@/api/user"
import { useUserStore } from '@/store'
import { ElMessage } from "element-plus";

const emit = defineEmits(['goHandle'])
const store = useUserStore();
const router = useRouter()

const formRef = ref();
// 登录参数效验
const fromData = reactive({
  username: '',
  password: '',
  rememberMe: false
});
// 效验规则
const rules = reactive({
  username: [
    { required: true, message: "请输入正确的用户名或手机号", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur"},
  ],
});
// 登录
const submitForm = async formEl => {
  if (!formEl) return
  const valid = await formEl.validate().catch(() => false)
  if (!valid) return

  let tokenStored = false
  try {
    const loginRes = await userLogins(fromData)
    if (loginRes.code !== 200) {
      ElMessage.error(loginRes.msg || '登录失败，请检查账号和密码')
      return
    }

    await store.setToken(loginRes.data)
    tokenStored = true
    const userRes = await getUserInfo()
    if (userRes.code !== 200 || !userRes.data) {
      await store.logout()
      ElMessage.error(userRes.msg || '用户信息获取失败，请重新登录')
      return
    }

    await store.setUserInfo(userRes.data)
    await router.push('/main/index')
  } catch (err) {
    if (tokenStored) await store.logout()
    ElMessage.error(err?.response?.data?.msg || err?.message || '登录失败，请稍后重试')
  }
};

const goRegister = () => {
  emit('goHandle', 'register')
}
</script>
<style lang="scss" scoped>
.loginPass {
    margin-top: 40px;
}
</style>
