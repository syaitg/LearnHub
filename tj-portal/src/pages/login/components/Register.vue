<!-- 注册页面 -->
<template>
  <div class="loginPass">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="0px"
      class="demo-dynamic"
    >
      <el-form-item prop="cellPhone" label="">
        <el-input v-model="formData.cellPhone" maxlength="11" clearable placeholder="请输入手机号" />
      </el-form-item>
      <el-form-item prop="password" label="">
        <el-input v-model="formData.password" type="password" show-password placeholder="请输入密码" />
      </el-form-item>
      <el-form-item prop="code" label="">
        <div class="verify-row">
          <el-input v-model="formData.code" maxlength="6" placeholder="请输入短信验证码" />
          <span
            class="bt bt-grey verify-button"
            :class="{ disabled: countdown > 0 }"
            @click="sendVerifyCode"
          >{{ countdown > 0 ? `${countdown}s后重发` : '发送验证码' }}</span>
        </div>
      </el-form-item>
      <el-form-item class="marg-bt-15">
        <div class="bt" @click="submitForm(formRef)">注册</div>
      </el-form-item>
    </el-form>
    <div class="font-bt text-center" @click="goLogin">去登录</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userRegist, verifycode } from '@/api/user'

const emit = defineEmits(['goHandle'])
const formRef = ref()
const countdown = ref(0)
let countdownTimer

const formData = reactive({
  cellPhone: '',
  password: '',
  code: ''
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
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入短信验证码', trigger: 'blur' }]
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
    try {
      const res = await userRegist(formData)
      if (res.code === 200) {
        ElMessage.success('注册成功！请登录')
        window.setTimeout(() => emit('goHandle', 'pass'), 500)
      } else {
        ElMessage.error(res.msg || '注册失败')
      }
    } catch (error) {
      ElMessage.error(error?.response?.data?.msg || error?.message || '注册失败，请稍后重试')
    }
  })
}

const goLogin = () => emit('goHandle', 'pass')

onBeforeUnmount(() => {
  if (countdownTimer) window.clearInterval(countdownTimer)
})
</script>

<style lang="scss" scoped>
.loginPass {
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
