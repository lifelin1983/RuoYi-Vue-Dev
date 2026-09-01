package com.ruoyi.common.utils.uuid;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Seq 序列生成器单元测试
 */
public class SeqTest
{
    @Test
    void getId_default()
    {
        String id = Seq.getId();
        assertThat(id).matches("\\d{14}A\\d{3}");
    }

    @Test
    void getId_byType()
    {
        assertThat(Seq.getId(Seq.commSeqType)).matches("\\d{14}A\\d{3}");
        assertThat(Seq.getId(Seq.uploadSeqType)).matches("\\d{14}A\\d{3}");
    }

    @Test
    void getId_withAtomicInt()
    {
        AtomicInteger ai = new AtomicInteger(5);
        String id = Seq.getId(ai, 3);
        assertThat(id).endsWith("A005");
        assertThat(id).hasSize(18);
    }
}
