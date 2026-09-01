package com.ruoyi.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Date;
import org.junit.jupiter.api.Test;

/**
 * DateUtils 单元测试（纯静态，无容器依赖）
 */
public class DateUtilsTest
{
    @Test
    void getNowDate()
    {
        assertThat(DateUtils.getNowDate()).isInstanceOf(Date.class);
    }

    @Test
    void getDate_getTime_dateTimeNow()
    {
        assertThat(DateUtils.getDate()).hasSize(10);
        assertThat(DateUtils.getTime()).hasSize(19);
        assertThat(DateUtils.dateTimeNow()).hasSize(14);
        assertThat(DateUtils.dateTimeNow("yyyy")).hasSize(4);
    }

    @Test
    void dateTime_fromDate()
    {
        assertThat(DateUtils.dateTime(new Date())).hasSize(10);
    }

    @Test
    void parseDateToStr_and_dateTime_parse()
    {
        Date d = new Date();
        String s = DateUtils.parseDateToStr("yyyy-MM-dd", d);
        assertThat(s).hasSize(10);
        Date parsed = DateUtils.dateTime("yyyy-MM-dd", s);
        assertThat(parsed).isNotNull();
    }

    @Test
    void datePath_and_dateTime()
    {
        assertThat(DateUtils.datePath()).matches("\\d{4}/\\d{2}/\\d{2}");
        assertThat(DateUtils.dateTime()).hasSize(8);
    }

    @Test
    void parseDate()
    {
        assertThat(DateUtils.parseDate("2020-01-01")).isNotNull();
        assertThat(DateUtils.parseDate("not-a-date")).isNull();
        assertThat(DateUtils.parseDate(null)).isNull();
    }

    @Test
    void getServerStartDate()
    {
        assertThat(DateUtils.getServerStartDate()).isInstanceOf(Date.class);
    }

    @Test
    void differentDaysByMillisecond()
    {
        Date d1 = DateUtils.dateTime("yyyy-MM-dd", "2020-01-01");
        Date d2 = DateUtils.dateTime("yyyy-MM-dd", "2020-01-02");
        assertThat(DateUtils.differentDaysByMillisecond(d1, d2)).isEqualTo(1);
    }

    @Test
    void timeDistance()
    {
        Date d1 = DateUtils.dateTime("yyyy-MM-dd HH:mm:ss", "2020-01-01 00:00:00");
        Date d2 = DateUtils.dateTime("yyyy-MM-dd HH:mm:ss", "2020-01-02 01:02:03");
        assertThat(DateUtils.timeDistance(d2, d1)).contains("天");
    }

    @Test
    void toDate_fromLocalDateTime_and_LocalDate()
    {
        assertThat(DateUtils.toDate(java.time.LocalDateTime.now())).isInstanceOf(Date.class);
        assertThat(DateUtils.toDate(java.time.LocalDate.now())).isInstanceOf(Date.class);
    }
}
