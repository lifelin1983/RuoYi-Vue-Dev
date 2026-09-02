module.exports = {
  // 纯函数 + 未来组件测试均可用；jsdom 兼容 DOM 相关 utils（如 auth.js 的 js-cookie）
  testEnvironment: 'jsdom',
  // 复用项目 babel.config.js（@vue/cli-plugin-babel/preset）转译 ES Module
  transform: {
    '^.+\\.js$': 'babel-jest'
  },
  // 仅收集纯函数文件覆盖率（不收 request.js/scroll-to.js 等 DOM/网络文件，避免总覆盖率误读）
  // P2-4 MVP: validate.js(已落地) + auth.js(js-cookie 薄封装) + ruoyi.js(12 纯函数)
  collectCoverageFrom: [
    'src/utils/validate.js',
    'src/utils/auth.js',
    'src/utils/ruoyi.js'
  ],
  moduleFileExtensions: ['js', 'json'],
  testMatch: ['**/tests/**/*.spec.js']
}
