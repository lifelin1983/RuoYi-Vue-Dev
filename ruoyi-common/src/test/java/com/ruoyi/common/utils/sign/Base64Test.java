package com.ruoyi.common.utils.sign;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * 自实现 Base64 编解码单元测试（含已知向量与边界）
 */
public class Base64Test
{
    @Test
    void encode_decode_roundtrip()
    {
        byte[] data = "Hello World".getBytes();
        String enc = Base64.encode(data);
        byte[] dec = Base64.decode(enc);
        assertThat(dec).isEqualTo(data);
    }

    @Test
    void encode_edgeCases()
    {
        assertThat(Base64.encode(null)).isNull();
        assertThat(Base64.encode(new byte[]{})).isEqualTo("");
        String one = Base64.encode(new byte[]{1});
        assertThat(one).endsWith("==");
        assertThat(Base64.decode(one)).isEqualTo(new byte[]{1});
        String two = Base64.encode(new byte[]{1, 2});
        assertThat(two).endsWith("=");
        assertThat(Base64.decode(two)).isEqualTo(new byte[]{1, 2});
    }

    @Test
    void decode_invalid()
    {
        assertThat(Base64.decode(null)).isNull();
        assertThat(Base64.decode("notbase64!!")).isNull();
        assertThat(Base64.decode("====")).isNull();
    }

    @Test
    void knownVector()
    {
        assertThat(Base64.encode("Man".getBytes())).isEqualTo("TWFu");
        assertThat(Base64.decode("TWFu")).isEqualTo("Man".getBytes());
    }
}
