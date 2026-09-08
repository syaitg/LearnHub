import axios from 'axios';
import proxy from "../config/proxy";
const env = import.meta.env.MODE || 'development';
const host = env === 'mock' ? 'https://mock.boxuegu.com/mock/3359' : proxy[env].host;

const sleep = (delay) => new Promise((resolve) => setTimeout(resolve, delay))
let isRefresh = false;
let success = false;
export async function tryRefreshToken(){
  if(isRefresh){
    while (isRefresh){
      await sleep(10)
    }
    return success;
  }
  isRefresh = true;
  success = false;
  try {
    // 尝试刷新 token
    const resp = await axios.get(host + "/as/accounts/refresh", {
      params: { clientType: "student" },
      withCredentials: true,
      timeout: 5000,
    });
    if (resp.status === 200 && resp.data.code === 200) {
      sessionStorage.setItem("token", resp.data.data)
      success = true;
    } else {
      sessionStorage.removeItem("token");
    }
    return success;
  } catch (error) {
    // 刷新请求的网络异常或服务异常也必须解除刷新锁，避免后续请求永久等待。
    sessionStorage.removeItem("token");
    return false;
  } finally {
    isRefresh = false;
  }
}
