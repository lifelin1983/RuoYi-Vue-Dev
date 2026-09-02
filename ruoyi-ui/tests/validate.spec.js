import {
  isPathMatch,
  isEmpty,
  isHttp,
  isExternal,
  validUsername,
  validURL,
  validLowerCase,
  validUpperCase,
  validAlphabets,
  validEmail,
  isString,
  isArray
} from '../src/utils/validate'

describe('validate.js 纯函数', () => {
  describe('isPathMatch - 路径通配符', () => {
    it('精确匹配', () => {
      expect(isPathMatch('/dashboard', '/dashboard')).toBe(true)
    })
    it('单星 * 匹配单层非斜杠片段', () => {
      expect(isPathMatch('/user/*', '/user/list')).toBe(true)
      expect(isPathMatch('/user/*', '/user/list/detail')).toBe(false)
    })
    it('双星 ** 匹配任意层', () => {
      expect(isPathMatch('/user/**', '/user/list/detail')).toBe(true)
      expect(isPathMatch('/user/**', '/user')).toBe(false)
    })
    it('问号 ? 匹配单字符', () => {
      expect(isPathMatch('/a?c', '/abc')).toBe(true)
      expect(isPathMatch('/a?c', '/abbc')).toBe(false)
    })
    it('转义正则元字符', () => {
      expect(isPathMatch('/a.b', '/a.b')).toBe(true)
      expect(isPathMatch('/a.b', '/axb')).toBe(false)
    })
  })

  describe('isEmpty', () => {
    it('空值判定为 true', () => {
      expect(isEmpty(null)).toBe(true)
      expect(isEmpty(undefined)).toBe(true)
      expect(isEmpty('')).toBe(true)
      expect(isEmpty('undefined')).toBe(true)
    })
    it('字符串零 / 空格非空', () => {
      expect(isEmpty('0')).toBe(false)
      expect(isEmpty(' ')).toBe(false)
    })
    it('JS 宽松相等陷阱：数字 0 与布尔 false 被视空（0=="" 且 false==""）', () => {
      // 注意：这是 validate.js 的真实语义，依赖框架者需知晓，避免数字字段填 0 被误判为空
      expect(isEmpty(0)).toBe(true)
      expect(isEmpty(false)).toBe(true)
    })
  })

  describe('isHttp', () => {
    it('http/https 为 true', () => {
      expect(isHttp('http://a.com')).toBe(true)
      expect(isHttp('https://a.com')).toBe(true)
    })
    it('其他协议为 false', () => {
      expect(isHttp('ftp://a.com')).toBe(false)
      expect(isHttp('a.com')).toBe(false)
    })
  })

  describe('isExternal', () => {
    it('外链协议为 true', () => {
      expect(isExternal('https://a.com')).toBe(true)
      expect(isExternal('mailto:a@b.com')).toBe(true)
      expect(isExternal('tel:123')).toBe(true)
    })
    it('内部路径为 false', () => {
      expect(isExternal('/dashboard')).toBe(false)
      expect(isExternal('dashboard')).toBe(false)
    })
  })

  describe('validUsername', () => {
    it('白名单内且去空格', () => {
      expect(validUsername('admin')).toBe(true)
      expect(validUsername('  editor  ')).toBe(true)
    })
    it('白名单外为 false', () => {
      expect(validUsername('root')).toBe(false)
      expect(validUsername('')).toBe(false)
    })
  })

  describe('validURL', () => {
    it('合法 URL', () => {
      expect(validURL('https://www.example.com/path?q=1')).toBe(true)
      expect(validURL('ftp://192.168.1.1:8080')).toBe(true)
    })
    it('非法 URL', () => {
      expect(validURL('not a url')).toBe(false)
      expect(validURL('http://')).toBe(false)
    })
  })

  describe('validLowerCase / validUpperCase / validAlphabets', () => {
    it('validLowerCase', () => {
      expect(validLowerCase('abc')).toBe(true)
      expect(validLowerCase('abC')).toBe(false)
      expect(validLowerCase('123')).toBe(false)
    })
    it('validUpperCase', () => {
      expect(validUpperCase('ABC')).toBe(true)
      expect(validUpperCase('ABc')).toBe(false)
    })
    it('validAlphabets', () => {
      expect(validAlphabets('abc')).toBe(true)
      expect(validAlphabets('ABC')).toBe(true)
      expect(validAlphabets('ab1')).toBe(false)
    })
  })

  describe('validEmail', () => {
    it('合法邮箱', () => {
      expect(validEmail('user@example.com')).toBe(true)
      expect(validEmail('a.b+c@sub.domain.io')).toBe(true)
    })
    it('非法邮箱', () => {
      expect(validEmail('user@')).toBe(false)
      expect(validEmail('user.com')).toBe(false)
      expect(validEmail('user@domain')).toBe(false)
    })
  })

  describe('isString', () => {
    it('字符串（含 String 对象）为 true', () => {
      expect(isString('x')).toBe(true)
      expect(isString(new String('x'))).toBe(true)
    })
    it('非字符串为 false', () => {
      expect(isString(123)).toBe(false)
      expect(isString({})).toBe(false)
      expect(isString([])).toBe(false)
    })
  })

  describe('isArray', () => {
    it('数组为 true', () => {
      expect(isArray([])).toBe(true)
      expect(isArray([1, 2])).toBe(true)
    })
    it('非数组为 false', () => {
      expect(isArray({})).toBe(false)
      expect(isArray('[]')).toBe(false)
      expect(isArray(null)).toBe(false)
    })
  })
})
