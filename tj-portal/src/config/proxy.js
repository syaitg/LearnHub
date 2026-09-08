export default {
  development: {
    // 开发环境接口请求
    host: 'http://api.tianji.com',
    // host: '/api',
    // 开发环境 cdn 路径
    cdn: '',
  },
  test: {
    // 测试环境接口地址
    host: 'http://api.tianji.com',
    // 测试环境 cdn 路径
    cdn: '',
  },
  product: {
    // 正式环境通过 Nginx 的同源 /api 反向代理访问网关，避免浏览器跨域。
    host: '/api',
    // 正式环境 cdn 路径
    cdn: '',
  },
  pro: {
    // 发布构建使用 pro 模式，与 Nginx 的同源 /api 反向代理保持一致。
    host: '/api',
    // 正式环境 cdn 路径
    cdn: '',
  },
};
