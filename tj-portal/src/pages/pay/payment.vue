<!-- 支付 - 支付页面 -->
<template>
  <div class="paymenttWrapper container bg-wt">
    <div class="title">支付订单</div>
    <div class="successCont fx-sb">
      <div class="fx">
        <img src="@/assets/icon_success.png" width="72" height="72" alt="">
        <div class="">
          <p class="tit">订单提交成功！请尽快完成支付。</p>
          <p class="fx">支付还剩 <span class="ft-cl-err"><Countdown @timeOver="timeOver" :endTime="orderInfo && orderInfo.payOutTime"></Countdown></span> , 超时后将取消订单</p>
        </div>
      </div>
      <div class="price">
        <span>应付金额：</span>
        <span class="ft-cl-err">¥ {{orderInfo &&  amountConversion(orderInfo.payAmount)}}</span>
      </div>
    </div>
    <div class="pay">
      <div class="tit">选择一下支付方式付款</div>
      <div class="fx">

        <div v-for="item in payMethodList" :key="item.id" @click="payMethodCheck(item)" class="cont marg-rt-20" :class="{act: payMethod.id === item.id}">
          <img :src="item.channelIcon" width="44" height="44" alt=""> {{ item.name }}
        </div>
      </div>
    </div>
    <!-- 支付二维码弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      width="440px"
      :before-close="handleClose"
    >
      <template #header class="dialog-title">
        <span>{{payMethod.name}}支付</span>
      </template>
      <div style="padding: 0 40px" v-if="qrCodeUrl != ''">
        <qrcode-vue :key="qrCodeUrl" :value="qrCodeUrl" :size="320" level="H" />
      </div>
      <template #footer>
        <div class="dialog-footer">
          <p>请使用<em> {{payMethod.name}} </em>扫一扫</p> <p>二维码完成支付</p>
        </div>
      </template>
    </el-dialog>
  </div>
</template>
<script setup>
/** 数据导入 **/
import { onMounted,reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { getPayMethod, getPayUrl, getPayState } from "@/api/order.js";
import {amountConversion} from "@/utils/tool.js"
import QrcodeVue from 'qrcode.vue'
import Countdown from './components/Countdown.vue'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  // 获取支付渠道列表
  getPayMethodList()
  // 获取订单的信息及时效
  getPayStateData()
})
// 支付二维码弹窗数据
const dialogVisible = ref(false)
const dialogCont = reactive({
  title: '',
  desc: ''
})

const title = ref('')
// 选择支付方式
const payMethod = ref({})
const payMethodCheck = async (item) => {
  // 微信支付在测试环境为确认支付，确认后由后端直接完成支付；真实支付模式仍会继续展示二维码。
  if (item.channelCode === 'wxPay') {
    try {
      await ElMessageBox.confirm('当前为测试支付，确认后订单将直接支付完成，不会发起真实扣款。是否继续？', '确认支付', {
        confirmButtonText: '确认支付',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
  }
  payMethod.value = item
  // 获取二维码或确认支付
  getPayUrlData(item)
} 

// 选择小节的数据
const value = ref([])

// 提问数据
const ruleForm = reactive({
  courseId: '', // 课程id,
  chapterId: '',  // 章Id
  sectionId: '', // 小节Id
  title: '', 
  anonymity: false, // 是否匿名
  description: '',
})

// 获取支付渠道列表
const payMethodList = ref([]) // 支付渠道信息
const getPayMethodList = async () => {
  await getPayMethod()
    .then((res) => {
      if (res.code == 200) {
        payMethodList.value = res.data
      } else {
        ElMessage({
          message:res.msg,
          type: 'error'
        });
      }
    })
    .catch(() => {
      ElMessage({
        message: "获取支付渠道列表出错！",
        type: 'error'
      });
    });
} 

// 获取支付二维码链接
const timer = ref(null) // 定时获取支付状态
const qrCodeUrl = ref('')
const isCreatingPayOrder = ref(false)
// 开始轮询业务订单状态。测试微信确认支付和重复点击场景都复用这条状态刷新链路。
function startPayStatePolling(interval = 1000) {
  clearInterval(timer.value)
  getPayStateData()
  timer.value = setInterval(() => {
    getPayStateData()
  }, interval)
}
const getPayUrlData = async val => {
  // 定时获取支付状态
  if (isCreatingPayOrder.value) return

  const orderId = route.query.orderId
  const payChannelCode = val && val.channelCode
  if (!orderId || !payChannelCode) {
    ElMessage({
      message: '支付订单信息不完整，请返回订单页面重试',
      type: 'error'
    })
    return
  }

  isCreatingPayOrder.value = true
  try {
    const res = await getPayUrl({ orderId, payChannelCode })
    if (res.code == 200) {
      if (typeof res.data === 'string' && res.data.startsWith('mock://')) {
        qrCodeUrl.value = ''
        dialogVisible.value = false
        ElMessage.success('支付已确认，正在完成订单')
        startPayStatePolling()
        return
      }
      qrCodeUrl.value = res.data
      dialogVisible.value = true
      // 定时获取支付状态
      clearInterval(timer.value)
      timer.value = setInterval(() => {
        getPayStateData()
      }, 5000)
    } else {
      ElMessage({
        message: res.msg,
        type: 'error'
      })
    }
  } catch (error) {
    // 兼容旧版本后端已经完成支付、但重复请求仍返回“支付单已经支付”的情况。
    // 此时不再提示服务器内部错误，而是直接查询订单状态并等待页面跳转。
    const errorMessage = error?.response?.data?.msg || error?.response?.data?.message || ''
    if (payChannelCode === 'wxPay' && errorMessage.includes('支付单已经支付')) {
      ElMessage.info('订单已经支付，正在刷新订单状态')
      startPayStatePolling()
    } else {
      ElMessage({
        message: '获取支付二维码出错！',
        type: 'error'
      })
    }
  } finally {
    isCreatingPayOrder.value = false
  }
}
// 获取支付信息,包含支付状态和支付超时时间 payStaus 1:待支付，2：已支付，3：已关闭，4：已完成，5：已报名，6：已退款
const orderInfo = ref()
const isFirstGet = ref(true)
const getPayStateData = async () => {
  await getPayState({orderId: route.query.orderId})
    .then((res) => {
      if (res.code === 200) {
        if (res.data.status === 1 && isFirstGet.value){
          isFirstGet.value = false
          orderInfo.value = res.data
        } else if (res.data.status === 2 || res.data.status === 5){
          clearInterval(timer.value)
          router.push({path:'/pay/success', query:{order: res.data.id}})
        } else {
          // ElMessage('状态是非待支付或已支付成')
        }
      } else {
        ElMessage({
          message:res.msg,
          type: 'error'
        });
      }
    })
    .catch(() => {});
} 
const handleClose = (done) => {
  clearInterval(timer.value)
  done()
}
// 订单超时回首页去吧
const timeOver = () => {
  router.push('/main/index')
}
</script>
<style lang="scss" src="./index.scss"> </style>
