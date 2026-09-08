import axios from "axios";
import { ElMessageBox } from "element-plus";
import { tryRefreshToken } from "./refreshToken";
import { USER_KEY, TOKEN_NAME } from "../config/global";
import router from "../router";
import proxy from "../config/proxy";

const env = import.meta.env.MODE || "development";
const proxyConfig = proxy[env] || proxy.development || {}
const host = env === "mock" ? "https://tjxt-dev.itheima.net/api" : proxyConfig.host
const REQUEST_SUCCESS = 200;
let loginAlertVisible = false;

const instance = axios.create({
  baseURL: host,
  timeout: 5000,
  withCredentials: false,
});

instance.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_NAME);
  config.headers = {
    ...config.headers,
    ...(token ? { authorization: token } : {}),
  };
  return config;
});

/**
 * 统一处理登录失效。
 * 并发请求只展示一个弹窗，弹窗关闭后允许后续再次提示。
 */
function alertLoginMessage() {
  if (loginAlertVisible) return;
  loginAlertVisible = true;
  sessionStorage.removeItem(USER_KEY);
  sessionStorage.removeItem(TOKEN_NAME);
  ElMessageBox.confirm(
    "您的账号登录超时或在其他机器登录，请重新登录或更换账号登录！",
    "登录超时",
    {
      confirmButtonText: "重新登录",
      cancelButtonText: "继续浏览",
      type: "warning",
    }
  )
    .then(() => router.push("/login"))
    .catch(() => router.go(0))
    .finally(() => {
      loginAlertVisible = false;
    });
}

/** 尝试刷新令牌并重放原请求。 */
async function retryAfterRefresh(config) {
  if (!config || config._retry) return null;
  config._retry = true;
  const success = await tryRefreshToken();
  return success ? instance(config) : null;
}

instance.interceptors.response.use(
  async (response) => {
    const body = response?.data
    const code = body?.code
    if (code === REQUEST_SUCCESS) return body

    if (code === 401) {
      const retried = await retryAfterRefresh(response.config);
      if (retried) return retried;
      alertLoginMessage();
    }
    return body;
  },
  async (error) => {
    if (error.response?.status === 401) {
      const retried = await retryAfterRefresh(error.config);
      if (retried) return retried;
      alertLoginMessage();
    }
    return Promise.reject(error);
  }
);

export default instance;
