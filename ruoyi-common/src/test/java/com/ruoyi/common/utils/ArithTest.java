package com.ruoyi.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Arith 精确浮点运算的单元测试（纯静态、无上下文依赖）
 */
public class ArithTest
{
    @Test
    void add_shouldSum()
    {
        assertThat(Arith.add(0.1, 0.2)).isCloseTo(0.3, Offset.offset(1e-9));
        assertThat(Arith.add(1.0, 2.0)).isCloseTo(3.0, Offset.offset(1e-12));
    }

    @Test
    void sub_shouldSubtract()
    {
        assertThat(Arith.sub(1.0, 0.9)).isCloseTo(0.1, Offset.offset(1e-9));
        assertThat(Arith.sub(5.0, 2.0)).isCloseTo(3.0, Offset.offset(1e-12));
    }

    @Test
    void mul_shouldMultiply()
    {
        assertThat(Arith.mul(2.0, 3.0)).isCloseTo(6.0, Offset.offset(1e-12));
    }

    @Test
    void div_shouldDivideWithDefaultScale()
    {
        assertThat(Arith.div(1.0, 3.0)).isCloseTo(1.0 / 3.0, Offset.offset(1e-9));
    }

    @Test
    void div_shouldReturnZeroWhenDividendIsZero()
    {
        assertThat(Arith.div(0.0, 5.0)).isEqualTo(0.0);
    }

    @Test
    void div_withScale_shouldRoundHalfUp()
    {
        assertThat(Arith.div(2.0, 3.0, 2)).isCloseTo(0.67, Offset.offset(1e-9));
        assertThat(Arith.div(10.0, 4.0, 1)).isCloseTo(2.5, Offset.offset(1e-12));
    }

    @Test
    void div_withNegativeScale_shouldThrow()
    {
        assertThatThrownBy(() -> Arith.div(1.0, 1.0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void round_shouldRoundHalfUp()
    {
        assertThat(Arith.round(2.345, 2)).isCloseTo(2.35, Offset.offset(1e-12));
        assertThat(Arith.round(2.5, 0)).isCloseTo(3.0, Offset.offset(1e-12));
        assertThat(Arith.round(2.4, 0)).isCloseTo(2.0, Offset.offset(1e-12));
    }

    @Test
    void round_withNegativeScale_shouldThrow()
    {
        assertThatThrownBy(() -> Arith.round(1.0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
