module.exports = {
  // 纯函数 + 未来组件测试均可用；jsdom 兼容 DOM 相关 utils（如 auth.js 的 js-cookie、permission.js 的 @/store）
  testEnvironment: 'jsdom',
  // 复用项目 babel.config.js（@vue/cli-plugin-babel/preset）转译 ES Module
  transform: {
    '^.+\\.js$': 'babel-jest'
  },
  // 收集被测逻辑文件覆盖率：仅纯函数/工具层（不含 request.js 等需重依赖的集成层）
  // 注：errorCode.js 为纯静态字典（数据），istanbul 不对其插桩，"覆盖率"恒为 0% 属假象；
  //     已单测覆盖查表正确性，但不计入覆盖率统计，避免误导。
  // P2-4 MVP: validate.js + auth.js(js-cookie 薄封装) + ruoyi.js(12 纯函数)
  //          + jsencrypt.js(加密包装) + permission.js(权限校验)
  collectCoverageFrom: [
    'src/utils/validate.js',
    'src/utils/auth.js',
    'src/utils/ruoyi.js',
    'src/utils/jsencrypt.js',
    'src/utils/permission.js'
  ],
  moduleFileExtensions: ['js', 'json'],
  // 解析 @ 别名（permission.js 等使用 import store from '@/store'）
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1'
  },
  testMatch: ['**/tests/**/*.spec.js']
}
