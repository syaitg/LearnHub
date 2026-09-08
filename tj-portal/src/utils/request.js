import axios from 'axios';
import proxy from '../config/proxy';
import { ElMessageBox } from 'element-plus';
import router from '../router';
import { tryRefreshToken } from './refreshToken';

const env = import.meta.env.MODE || 'development';
const host =
  env === 'mock'
    ? 'https://mock.boxuegu.com/mock/3359'
    : proxy[env].host;

const CODE = {
  REQUEST_SUCCESS: 200,
};

// 并发请求同时失效时，只显示一个登录超时提示框。
let loginAlertVisible = false;

// 将媒资服务返回的相对地址转换为当前接口地址，避免图片标签请求到前端页面地址。
function normalizeMediaPaths(value) {
  if (!value || typeof value !== 'object') return value;
  if (Array.isArray(value)) {
    value.forEach(normalizeMediaPaths);
    return value;
  }
  Object.keys(value).forEach((key) => {
    const item = value[key];
    if (typeof item === 'string' && /^\/ms\//.test(item)) {
      value[key] = `${host}${item}`;
    } else if (item && typeof item === 'object') {
      normalizeMediaPaths(item);
    }
  });
  return value;
}

const instance = axios.create({
  baseURL: host,
  timeout: 60000,
  withCredentials: false,
});

instance.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('token');
  config.headers = config.headers || {};

  // FormData 需要由浏览器/Axios 自动生成 multipart boundary，不能手动固定 Content-Type。
  const isFormData =
    typeof FormData !== 'undefined' && config.data instanceof FormData;
  if (isFormData) {
    delete config.headers['Content-Type'];
    delete config.headers['content-type'];
  } else if (
    !config.headers['Content-Type'] &&
    !config.headers['content-type']
  ) {
    config.headers['Content-Type'] = 'application/json';
  }

  if (token) {
    config.headers.authorization = token;
  } else {
    delete config.headers.authorization;
    delete config.headers.Authorization;
  }

  return config;
});

/**
 * 刷新访问令牌后重放原请求，每个请求最多重试一次，避免刷新失败时无限循环。
 */
async function retryAfterRefresh(config) {
  if (!config || config._retry) return null;

  config._retry = true;
  const success = await tryRefreshToken();
  return success ? instance(config) : null;
}

/**
 * 清理本地登录状态并提示用户重新登录。
 * 刷新令牌失败后统一走这里，避免多个请求重复弹窗。
 */
function alertLoginMessage() {
  if (loginAlertVisible) return;

  loginAlertVisible = true;
  sessionStorage.removeItem('userInfo');
  sessionStorage.removeItem('token');

  ElMessageBox.confirm(
    '您的账号登录超时或已在其他机器登录，请重新登录或更换账号登录！',
    '登录超时',
    {
      confirmButtonText: '重新登录',
      cancelButtonText: '继续浏览',
      type: 'warning',
    }
  )
    .then(() => router.push('/login'))
    .catch(() => router.go(0))
    .finally(() => {
      loginAlertVisible = false;
    });
}

instance.interceptors.response.use(
  async (response) => {
    const code = response.data?.code;

    // 接口正常返回时，保持原有调用方约定，只返回业务数据而不是完整 Axios 响应。
    if (code === CODE.REQUEST_SUCCESS) {
      return normalizeMediaPaths(response.data);
    }

    // 部分网关会以 HTTP 200 携带业务码 401，和 HTTP 401 一样尝试刷新令牌。
    if (code === 401) {
      const retried = await retryAfterRefresh(response.config);
      if (retried) return retried;

      alertLoginMessage();
    }

    return normalizeMediaPaths(response.data);
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
