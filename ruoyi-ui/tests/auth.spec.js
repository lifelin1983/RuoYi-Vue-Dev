// auth.js 测试：js-cookie 薄封装，mock 整个模块后断言调用透传
import { getToken, setToken, removeToken } from '../src/utils/auth'

// 自动 mock 'js-cookie' 默认导出（Cookies）
jest.mock('js-cookie')

// 取 mock 后的 Cookies 实例
const Cookies = require('js-cookie')

describe('auth.js token 封装', () => {
  beforeEach(() => {
    jest.clearAllMocks()
  })

  it('getToken 读取 Admin-Token', () => {
    Cookies.get.mockReturnValue('tok-abc')
    expect(getToken()).toBe('tok-abc')
    expect(Cookies.get).toHaveBeenCalledWith('Admin-Token')
  })

  it('getToken 无 cookie 时返回 undefined', () => {
    Cookies.get.mockReturnValue(undefined)
    expect(getToken()).toBeUndefined()
    expect(Cookies.get).toHaveBeenCalledWith('Admin-Token')
  })

  it('setToken 写入 Admin-Token 并透传返回值', () => {
    Cookies.set.mockReturnValue('ok')
    expect(setToken('tok-xyz')).toBe('ok')
    expect(Cookies.set).toHaveBeenCalledWith('Admin-Token', 'tok-xyz')
  })

  it('removeToken 删除 Admin-Token 并透传返回值', () => {
    Cookies.remove.mockReturnValue('gone')
    expect(removeToken()).toBe('gone')
    expect(Cookies.remove).toHaveBeenCalledWith('Admin-Token')
  })
})
