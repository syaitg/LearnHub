export default {
  development: {
    // 开发环境接口请求
    // host: 'https://tjxt-dev.itheima.net/api',
    // 开发环境直接访问网关服务，避免域名入口的反向代理异常导致上传失败
    host: 'http://192.168.0.128:10010',
    // 开发环境 cdn 路径
    cdn: '',
  },
  test: {
    // 测试环境接口地址
    host: 'https://tjxt-admin-t.itheima.net/api',
    // 测试环境 cdn 路径
    cdn: '',
  },
  product: {
    // 正式环境接口地址
    host: 'https://service-bv448zsw-1257786608.gz.apigw.tencentcs.com',
    // 正式环境 cdn 路径
    cdn: '',
  },
  pro: {
    // 正式环境接口地址
    host: '/api',
    // 正式环境 cdn 路径
    cdn: '',
  },
};

