package com.ruoyi.common.utils.html;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

/**
 * HTMLFilter 单元测试（XSS 白名单过滤）
 */
public class HTMLFilterTest
{
    private final HTMLFilter filter = new HTMLFilter();

    @Test
    void filter_stripsScript()
    {
        String out = filter.filter("<script>alert(1)</script>");
        assertThat(out).doesNotContain("<script>").contains("alert(1)");
    }

    @Test
    void filter_keepsAllowedTag()
    {
        assertThat(filter.filter("<b>bold</b>")).isEqualTo("<b>bold</b>");
    }

    @Test
    void filter_anchorWithHref()
    {
        String out = filter.filter("<a href=\"http://example.com\">link</a>");
        assertThat(out).contains("<a").contains("href").contains("link");
    }

    @Test
    void filter_plainText()
    {
        assertThat(filter.filter("plain text")).isEqualTo("plain text");
    }

    @Test
    void filter_image()
    {
        String out = filter.filter("<img src=\"x.jpg\" />");
        assertThat(out).contains("<img").contains("src");
    }

    @Test
    void htmlSpecialChars()
    {
        String out = HTMLFilter.htmlSpecialChars("<a>&\"");
        assertThat(out).contains("&lt;").contains("&amp;").contains("&quot;");
    }

    @Test
    void chr()
    {
        assertThat(HTMLFilter.chr(65)).isEqualTo("A");
    }

    @Test
    void flags()
    {
        assertThat(filter.isStripComments()).isTrue();
        assertThat(filter.isAlwaysMakeTags()).isFalse();
    }
}
