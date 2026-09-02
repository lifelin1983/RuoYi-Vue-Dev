// errorCode.js 测试（纯字典：验证常见错误码查表与 default 兜底，request.js 响应拦截器依赖此查表）
import errorCode from '../src/utils/errorCode'

describe('errorCode.js 错误码字典', () => {
  it('常见错误码查表正确', () => {
    expect(errorCode['401']).toBe('认证失败，无法访问系统资源')
    expect(errorCode['403']).toBe('当前操作没有权限')
    expect(errorCode['404']).toBe('访问资源不存在')
  })
  it('default 兜底且未定义码为 undefined', () => {
    expect(errorCode['default']).toBe('系统未知错误，请反馈给管理员')
    expect(errorCode['999']).toBeUndefined()
  })
})
