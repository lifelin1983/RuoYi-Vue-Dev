module.exports = {
  // 纯函数 + 未来组件测试均可用；jsdom 兼容 DOM 相关 utils（如 auth.js 的 js-cookie）
  testEnvironment: 'jsdom',
  // 复用项目 babel.config.js（@vue/cli-plugin-babel/preset）转译 ES Module
  transform: {
    '^.+\\.js$': 'babel-jest'
  },
  // 仅收集被测文件自身覆盖率（MVP 只覆盖 validate.js，避免未测 utils 拉低总覆盖率造成误读）
  collectCoverageFrom: ['src/utils/validate.js'],
  moduleFileExtensions: ['js', 'json'],
  testMatch: ['**/tests/**/*.spec.js']
}
