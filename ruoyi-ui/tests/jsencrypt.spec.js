// jsencrypt.js 测试（真实库往返：验证 encrypt 公钥加密 + decrypt 私钥解密 链路真实可用）
import { encrypt, decrypt } from '../src/utils/jsencrypt'

describe('jsencrypt.js 加解密包装（真实库往返）', () => {
  it('encrypt 返回非空字符串密文', () => {
    const c = encrypt('hello-ruoyi-2026')
    expect(typeof c).toBe('string')
    expect(c.length).toBeGreaterThan(10) // RSA 2048 密文较长
  })

  it('decrypt 能还原 encrypt 结果（ASCII 明文）', () => {
    const plain = 'hello-ruoyi-2026'
    const round = decrypt(encrypt(plain))
    expect(round).toBe(plain)
  })
})
