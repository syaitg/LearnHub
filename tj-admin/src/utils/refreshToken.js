import axios from "axios";
import proxy from "../config/proxy";
import { TOKEN_NAME } from "../config/global";

const env = import.meta.env.MODE || "development";
const host = env === "mock" ? "https://mock.boxuegu.com/mock/3359" : proxy[env].host;
const sleep = (delay) => new Promise((resolve) => setTimeout(resolve, delay));
let refreshing = false;
let lastRefreshSucceeded = false;

/**
 * 刷新管理端令牌。
 * 并发的 401 请求共用同一次刷新结果，避免重复调用刷新接口。
 */
export async function tryRefreshToken() {
  if (refreshing) {
    while (refreshing) await sleep(10);
    return lastRefreshSucceeded;
  }

  refreshing = true;
  try {
    const response = await axios.get(`${host}/as/accounts/refresh`, {
      params: { clientType: "admin" },
      withCredentials: true,
      timeout: 5000,
    });
    lastRefreshSucceeded = response.status === 200 && response.data.code === 200;
    if (lastRefreshSucceeded) {
      sessionStorage.setItem(TOKEN_NAME, response.data.data);
    } else {
      sessionStorage.removeItem(TOKEN_NAME);
    }
    return lastRefreshSucceeded;
  } catch (error) {
    sessionStorage.removeItem(TOKEN_NAME);
    lastRefreshSucceeded = false;
    return false;
  } finally {
    refreshing = false;
  }
}
