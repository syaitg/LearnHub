<!-- 登录页面 - 手机号 -->
<template>
  <div class="loginPhone">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="0px"
      class="demo-dynamic"
    >
      <el-form-item prop="cellPhone" label="">
        <el-input
          v-model="formData.cellPhone"
          maxlength="11"
          clearable
          placeholder="请输入手机号"
        />
      </el-form-item>
      <el-form-item prop="password" label="">
        <div class="verify-row">
          <el-input v-model="formData.password" maxlength="6" placeholder="请输入验证码" />
          <span
            class="bt bt-grey verify-button"
            :class="{ disabled: countdown > 0 }"
            @click="sendVerifyCode"
          >{{ countdown > 0 ? `${countdown}s后重发` : '发送验证码' }}</span>
        </div>
      </el-form-item>
      <el-form-item class="marg-b-10">
        <div class="fx-sb">
          <el-checkbox v-model="formData.rememberMe" label="7天免登录" size="large" />
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
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getUserInfo, phoneLogins, verifycode } from '@/api/user'
import { useUserStore } from '@/store'

const emit = defineEmits(['goHandle'])
const router = useRouter()
const store = useUserStore()
const formRef = ref()
const countdown = ref(0)
let countdownTimer

const formData = reactive({
  cellPhone: '',
  password: '',
  rememberMe: false
})

const phonePattern = /^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\d{8}$/
const validatePhone = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入手机号'))
  } else if (!phonePattern.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

const rules = reactive({
  cellPhone: [{ validator: validatePhone, trigger: 'blur' }],
  password: [{ required: true, message: '请输入短信验证码', trigger: 'blur' }]
})

const startCountdown = () => {
  countdown.value = 60
  countdownTimer = window.setInterval(() => {
    if (countdown.value <= 1) {
      window.clearInterval(countdownTimer)
      countdownTimer = undefined
      countdown.value = 0
      return
    }
    countdown.value -= 1
  }, 1000)
}

const sendVerifyCode = async () => {
  if (countdown.value > 0) return
  try {
    await formRef.value.validateField('cellPhone')
  } catch {
    return
  }

  try {
    const res = await verifycode({ cellPhone: formData.cellPhone })
    if (res.code === 200) {
      ElMessage.success('验证码发送成功')
      startCountdown()
    } else {
      ElMessage.error(res.msg || '验证码发送失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.msg || error?.message || '验证码发送失败')
  }
}

const submitForm = (formEl) => {
  if (!formEl) return
  formEl.validate(async (valid) => {
    if (!valid) return
    let tokenStored = false
    try {
      const res = await phoneLogins(formData)
      if (res.code !== 200) {
        ElMessage.error(res.msg || '登录失败')
        return
      }
      await store.setToken(res.data)
      tokenStored = true
      const userInfo = await getUserInfo()
      if (userInfo.code !== 200 || !userInfo.data) {
        await store.logout()
        ElMessage.error(userInfo.msg || '获取用户信息失败，请重新登录')
        return
      }
      await store.setUserInfo(userInfo.data)
      await router.push('/main/index')
    } catch (error) {
      if (tokenStored) await store.logout()
      ElMessage.error(error?.response?.data?.msg || error?.message || '登录失败，请稍后重试')
    }
  })
}

const goRegister = () => {
  emit('goHandle', 'register')
}

onBeforeUnmount(() => {
  if (countdownTimer) window.clearInterval(countdownTimer)
})
</script>

<style lang="scss" scoped>
.loginPhone {
  margin-top: 40px;

  .verify-row {
    position: relative;
    width: 100%;
  }

  .verify-button {
    position: absolute;
    right: 10px;
    top: 6px;
    width: 88px;
    height: 28px;
    line-height: 28px;
    font-size: 14px;
    text-align: center;
    cursor: pointer;

    &.disabled {
      cursor: not-allowed;
      opacity: 0.6;
    }
  }
}
</style>
