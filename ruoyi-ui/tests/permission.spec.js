// permission.js 测试（mock @/store，验证 checkPermi/checkRole 的权限/角色匹配逻辑）
jest.mock('@/store', () => ({
  __esModule: true,
  default: { getters: { permissions: [], roles: [] } }
}))

import store from '@/store'
import { checkPermi, checkRole } from '../src/utils/permission'

describe('permission.js 权限校验', () => {
  afterEach(() => {
    store.getters.permissions = []
    store.getters.roles = []
  })

  describe('checkPermi - 字符权限', () => {
    it('非数组 / 空数组返回 false 并报错', () => {
      const spy = jest.spyOn(console, 'error').mockImplementation(() => {})
      expect(checkPermi('')).toBe(false)
      expect(checkPermi([])).toBe(false)
      expect(spy).toHaveBeenCalled()
      spy.mockRestore()
    })
    it('含超管权限 *:*:* 放行任意校验', () => {
      store.getters.permissions = ['*:*:*']
      expect(checkPermi(['system:user:add'])).toBe(true)
    })
    it('含匹配权限放行', () => {
      store.getters.permissions = ['system:user:add', 'system:user:edit']
      expect(checkPermi(['system:user:add'])).toBe(true)
    })
    it('不含匹配权限拦截', () => {
      store.getters.permissions = ['system:user:edit']
      expect(checkPermi(['system:user:add'])).toBe(false)
    })
  })

  describe('checkRole - 角色权限', () => {
    it('非数组 / 空数组返回 false 并报错', () => {
      const spy = jest.spyOn(console, 'error').mockImplementation(() => {})
      expect(checkRole(null)).toBe(false)
      spy.mockRestore()
    })
    it('含超管角色 admin 放行任意校验', () => {
      store.getters.roles = ['admin']
      expect(checkRole(['editor'])).toBe(true)
    })
    it('含匹配角色放行', () => {
      store.getters.roles = ['editor']
      expect(checkRole(['editor'])).toBe(true)
    })
    it('不含匹配角色拦截', () => {
      store.getters.roles = ['editor']
      expect(checkRole(['admin'])).toBe(false)
    })
  })
})
