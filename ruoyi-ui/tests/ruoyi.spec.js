// ruoyi.js 纯函数测试（无 DOM / 无外部依赖，仅 resetForm 依赖 this.$refs）
import {
  parseTime,
  resetForm,
  addDateRange,
  selectDictLabel,
  selectDictLabels,
  sprintf,
  parseStrEmpty,
  mergeRecursive,
  handleTree,
  tansParams,
  getNormalPath,
  blobValidate
} from '../src/utils/ruoyi'

describe('ruoyi.js 纯函数', () => {
  describe('parseTime - 日期格式化', () => {
    it('无参数 / 假值返回 null', () => {
      expect(parseTime()).toBeNull()
      expect(parseTime(0)).toBeNull()
      expect(parseTime('')).toBeNull()
    })
    it('默认格式', () => {
      const d = new Date(2026, 0, 2, 8, 5, 9) // 2026-01-02 08:05:09
      expect(parseTime(d)).toBe('2026-01-02 08:05:09')
    })
    it('自定义格式 + 补零 + 星期', () => {
      const d = new Date(2026, 2, 15, 9, 3, 0) // 周日? 2026-03-15 是周日
      expect(parseTime(d, '{y}/{m}/{d} {h}:{i} 周{a}')).toBe('2026/03/15 09:03 周日')
    })
    it('字符串时间戳(秒)自动 x1000', () => {
      // 2026-01-02 00:00:00 -> 秒级 1767283200
      expect(parseTime('1767283200')).toBe('2026-01-02 00:00:00')
    })
    it('ISO 字符串 T 与 .mmm 被规整', () => {
      expect(parseTime('2026-01-02T03:04:05.123')).toBe('2026-01-02 03:04:05')
    })
  })

  describe('resetForm - 依赖 this.$refs', () => {
    it('存在 ref 时调用 resetFields', () => {
      const resetFields = jest.fn()
      const ctx = { $refs: { formRef: { resetFields } } }
      resetForm.call(ctx, 'formRef')
      expect(resetFields).toHaveBeenCalled()
    })
    it('ref 不存在时不抛错', () => {
      const ctx = { $refs: {} }
      expect(() => resetForm.call(ctx, 'noRef')).not.toThrow()
    })
  })

  describe('addDateRange', () => {
    it('默认写入 beginTime/endTime', () => {
      const params = {}
      const out = addDateRange(params, ['2026-01-01', '2026-01-31'])
      expect(out.params.beginTime).toBe('2026-01-01')
      expect(out.params.endTime).toBe('2026-01-31')
    })
    it('自定义 propName', () => {
      const out = addDateRange({}, ['a', 'b'], 'Create')
      expect(out.params.beginCreate).toBe('a')
      expect(out.params.endCreate).toBe('b')
    })
    it('无 dateRange 时 params 仍初始化', () => {
      const out = addDateRange({})
      expect(out.params).toEqual({})
    })
  })

  describe('selectDictLabel / selectDictLabels', () => {
    const dict = [
      { value: '0', label: '男' },
      { value: '1', label: '女' }
    ]
    it('selectDictLabel 命中', () => {
      expect(selectDictLabel(dict, '1')).toBe('女')
    })
    it('selectDictLabel 未命中回显原值', () => {
      expect(selectDictLabel(dict, '9')).toBe('9')
    })
    it('selectDictLabel value 为 undefined 返回空串', () => {
      expect(selectDictLabel(dict, undefined)).toBe('')
    })
    it('selectDictLabels 多值', () => {
      expect(selectDictLabels(dict, '0,1')).toBe('男,女')
    })
    it('selectDictLabels 数组入参', () => {
      expect(selectDictLabels(dict, ['0', '1'])).toBe('男,女')
    })
    it('selectDictLabels 自定义分隔符', () => {
      expect(selectDictLabels(dict, '0|1', '|')).toBe('男|女')
    })
    it('selectDictLabels 空串返回空', () => {
      expect(selectDictLabels(dict, '')).toBe('')
    })
    it('selectDictLabels 含未匹配值回显原值', () => {
      expect(selectDictLabels(dict, '0,9')).toBe('男,9')
    })
  })

  describe('sprintf', () => {
    it('按顺序替换 %s', () => {
      expect(sprintf('%s/%s', 'a', 'b')).toBe('a/b')
    })
    it('占位多于参数时返回空串', () => {
      expect(sprintf('%s-%s', 'only')).toBe('')
    })
  })

  describe('parseStrEmpty', () => {
    it('空/undefined/null 字符串转空', () => {
      expect(parseStrEmpty('')).toBe('')
      expect(parseStrEmpty('undefined')).toBe('')
      expect(parseStrEmpty('null')).toBe('')
      expect(parseStrEmpty(null)).toBe('')
    })
    it('正常字符串原样返回', () => {
      expect(parseStrEmpty('hello')).toBe('hello')
    })
  })

  describe('mergeRecursive', () => {
    it('浅层覆盖', () => {
      expect(mergeRecursive({ a: 1, b: 2 }, { b: 3 })).toEqual({ a: 1, b: 3 })
    })
    it('深层递归合并对象', () => {
      const src = { a: { x: 1, y: 2 }, b: 5 }
      const tgt = { a: { y: 20, z: 30 } }
      expect(mergeRecursive(src, tgt)).toEqual({ a: { x: 1, y: 20, z: 30 }, b: 5 })
    })
  })

  describe('handleTree', () => {
    const flat = [
      { id: 1, parentId: 0, name: 'root' },
      { id: 2, parentId: 1, name: 'child1' },
      { id: 3, parentId: 1, name: 'child2' }
    ]
    it('构造树结构', () => {
      const tree = handleTree(flat, 'id', 'parentId', 'children')
      expect(tree).toHaveLength(1)
      expect(tree[0].name).toBe('root')
      expect(tree[0].children).toHaveLength(2)
      expect(tree[0].children[0].name).toBe('child1')
    })
    it('自定义字段名', () => {
      const data = [
        { key: 'a', pid: '0' },
        { key: 'b', pid: 'a' }
      ]
      const tree = handleTree(data, 'key', 'pid', 'kids')
      expect(tree[0].kids[0].key).toBe('b')
    })
  })

  describe('tansParams - URL 参数序列化', () => {
    it('基本键值', () => {
      expect(tansParams({ a: 1, b: 'x y' })).toBe('a=1&b=x%20y&')
    })
    it('嵌套对象展开', () => {
      expect(tansParams({ q: { k: 'v' } })).toBe('q%5Bk%5D=v&')
    })
    it('忽略空值', () => {
      expect(tansParams({ a: '', b: null, c: undefined, d: 1 })).toBe('d=1&')
    })
  })

  describe('getNormalPath', () => {
    it('空串返回自身', () => {
      expect(getNormalPath('')).toBe('')
    })
    it('双斜杠合并', () => {
      expect(getNormalPath('a//b')).toBe('a/b')
    })
    it('去除末尾斜杠', () => {
      expect(getNormalPath('/abc/')).toBe('/abc')
    })
  })

  describe('blobValidate', () => {
    it('非 json 类型视为 blob', () => {
      expect(blobValidate({ type: 'application/pdf' })).toBe(true)
      expect(blobValidate({ type: 'text/csv' })).toBe(true)
    })
    it('json 类型返回 false', () => {
      expect(blobValidate({ type: 'application/json' })).toBe(false)
    })
  })
})
