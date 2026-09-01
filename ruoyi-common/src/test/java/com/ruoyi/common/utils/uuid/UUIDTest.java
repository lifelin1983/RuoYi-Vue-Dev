package com.ruoyi.common.utils.uuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

/**
 * 自定义 UUID 实现单元测试（非 java.util.UUID）
 */
public class UUIDTest
{
    @Test
    void randomUUID_versionAndVariant()
    {
        UUID u = UUID.randomUUID();
        assertThat(u.version()).isEqualTo(4);
        assertThat(u.variant()).isEqualTo(2);
    }

    @Test
    void fromString_roundtrip()
    {
        UUID u = UUID.randomUUID();
        String s = u.toString();
        UUID back = UUID.fromString(s);
        assertThat(back).isEqualTo(u);
        assertThat(back.getMostSignificantBits()).isEqualTo(u.getMostSignificantBits());
        assertThat(back.getLeastSignificantBits()).isEqualTo(u.getLeastSignificantBits());
    }

    @Test
    void toString_simple()
    {
        UUID u = UUID.randomUUID();
        assertThat(u.toString(false)).hasSize(36).contains("-");
        assertThat(u.toString(true)).hasSize(32).doesNotContain("-");
    }

    @Test
    void fromString_invalid_throws()
    {
        assertThatThrownBy(() -> UUID.fromString("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nameUUIDFromBytes_isVersion3()
    {
        UUID u = UUID.nameUUIDFromBytes("test".getBytes());
        assertThat(u.version()).isEqualTo(3);
    }

    @Test
    void equals_and_hashCode()
    {
        UUID u1 = UUID.randomUUID();
        assertThat(u1.equals(u1)).isTrue();
        assertThat(u1.equals(null)).isFalse();
        assertThat(u1.equals("x")).isFalse();
        UUID u2 = UUID.fromString(u1.toString());
        assertThat(u1.hashCode()).isEqualTo(u2.hashCode());
    }

    @Test
    void compareTo_ordering()
    {
        // 用低位 UUID，避免 all-ones 被解析成带符号 long(-1) 触发符号位边界
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertThat(a.compareTo(b)).isLessThan(0);
        assertThat(b.compareTo(a)).isGreaterThan(0);
        assertThat(a.compareTo(a)).isEqualTo(0);
    }

    @Test
    void timeBasedAccessors_throwForNonTimeUuid()
    {
        UUID u = UUID.randomUUID();
        assertThatThrownBy(u::timestamp).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(u::clockSequence).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(u::node).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void randomFactories_notNull()
    {
        assertThat(UUID.fastUUID()).isNotNull();
        assertThat(UUID.getSecureRandom()).isInstanceOf(SecureRandom.class);
        assertThat(UUID.getRandom()).isInstanceOf(ThreadLocalRandom.class);
    }
}
