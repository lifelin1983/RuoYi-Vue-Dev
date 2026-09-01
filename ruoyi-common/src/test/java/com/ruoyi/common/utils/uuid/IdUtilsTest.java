package com.ruoyi.common.utils.uuid;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * IdUtils 单元测试
 */
public class IdUtilsTest
{
    @Test
    void randomUUID_shouldBeStandardFormat()
    {
        String u = IdUtils.randomUUID();
        assertThat(u).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void simpleUUID_shouldBeNoDash()
    {
        assertThat(IdUtils.simpleUUID()).hasSize(32).doesNotContain("-");
    }

    @Test
    void fastUUID_shouldBeStandardFormat()
    {
        String u = IdUtils.fastUUID();
        assertThat(u).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void fastSimpleUUID_shouldBeNoDash()
    {
        assertThat(IdUtils.fastSimpleUUID()).hasSize(32).doesNotContain("-");
    }

    @Test
    void shouldBeUnique()
    {
        assertThat(IdUtils.randomUUID()).isNotEqualTo(IdUtils.randomUUID());
    }
}
