package com.ruoyi.common.utils.sign;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * Md5Utils 单元测试（校验真实摘要值）
 */
public class Md5UtilsTest
{
    @Test
    void hash_knownVector()
    {
        // MD5("test") = 098f6bcd4621d373cade4e832627b4f6
        assertThat(Md5Utils.hash("test")).isEqualTo("098f6bcd4621d373cade4e832627b4f6");
    }

    @Test
    void hash_deterministic_andNonEmpty()
    {
        String a = Md5Utils.hash("abc");
        assertThat(a).hasSize(32).containsPattern("[0-9a-f]{32}");
        assertThat(a).isEqualTo(Md5Utils.hash("abc"));
        assertThat(Md5Utils.hash("")).hasSize(32);
    }
}
