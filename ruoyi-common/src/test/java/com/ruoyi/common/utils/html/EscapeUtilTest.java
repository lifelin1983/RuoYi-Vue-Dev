package com.ruoyi.common.utils.html;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * EscapeUtil 单元测试（HTML/XSS 转义与还原）
 */
public class EscapeUtilTest
{
    @Test
    void escape_unescape_roundtrip()
    {
        String html = "<script>alert(1)</script>";
        String escaped = EscapeUtil.escape(html);
        assertThat(escaped).contains("%");
        assertThat(EscapeUtil.unescape(escaped)).isEqualTo(html);
    }

    @Test
    void escape_empty()
    {
        assertThat(EscapeUtil.escape("")).isEqualTo("");
        assertThat(EscapeUtil.escape(null)).isEqualTo("");
    }

    @Test
    void unescape_inputs()
    {
        assertThat(EscapeUtil.unescape("%3c")).isEqualTo("<");
        assertThat(EscapeUtil.unescape("")).isEqualTo("");
        assertThat(EscapeUtil.unescape(null)).isNull();
    }

    @Test
    void clean_stripsDisallowedTags()
    {
        String cleaned = EscapeUtil.clean("<script>alert(1)</script>");
        assertThat(cleaned).doesNotContain("script").contains("alert(1)");
        String b = EscapeUtil.clean("<b>hi</b>");
        assertThat(b).isEqualTo("<b>hi</b>");
    }
}
