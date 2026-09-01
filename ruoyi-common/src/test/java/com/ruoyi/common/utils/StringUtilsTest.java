package com.ruoyi.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * StringUtils 单元测试（纯静态，仅 ishttp 用 Constants、isMatch 用 AntPathMatcher，均可离线）
 */
public class StringUtilsTest
{
    @Test
    void nvl_shouldReturnValueOrDefault()
    {
        assertThat(StringUtils.nvl("a", "b")).isEqualTo("a");
        assertThat(StringUtils.nvl(null, "b")).isEqualTo("b");
    }

    @Test
    void isEmpty_collection()
    {
        assertThat(StringUtils.isEmpty((Collection<Object>) null)).isTrue();
        assertThat(StringUtils.isEmpty(java.util.Collections.emptyList())).isTrue();
        assertThat(StringUtils.isEmpty(Arrays.asList("x"))).isFalse();
    }

    @Test
    void isNotEmpty_collection()
    {
        assertThat(StringUtils.isNotEmpty(Arrays.asList("x"))).isTrue();
        assertThat(StringUtils.isNotEmpty(java.util.Collections.emptyList())).isFalse();
    }

    @Test
    void isEmpty_objectArray()
    {
        assertThat(StringUtils.isEmpty((Object[]) null)).isTrue();
        assertThat(StringUtils.isEmpty(new Object[]{})).isTrue();
        assertThat(StringUtils.isEmpty(new Object[]{1})).isFalse();
    }

    @Test
    void isEmpty_map()
    {
        assertThat(StringUtils.isEmpty((Map<Object, Object>) null)).isTrue();
        assertThat(StringUtils.isEmpty(new HashMap<>())).isTrue();
        Map<String, String> m = new HashMap<>();
        m.put("k", "v");
        assertThat(StringUtils.isEmpty(m)).isFalse();
    }

    @Test
    void isEmpty_string()
    {
        assertThat(StringUtils.isEmpty((String) null)).isTrue();
        assertThat(StringUtils.isEmpty("")).isTrue();
        assertThat(StringUtils.isEmpty(" ")).isTrue();
        assertThat(StringUtils.isEmpty("x")).isFalse();
    }

    @Test
    void isNull_isNotNull()
    {
        assertThat(StringUtils.isNull(null)).isTrue();
        assertThat(StringUtils.isNull("x")).isFalse();
        assertThat(StringUtils.isNotNull("x")).isTrue();
        assertThat(StringUtils.isNotNull(null)).isFalse();
    }

    @Test
    void isArray()
    {
        assertThat(StringUtils.isArray(new int[]{})).isTrue();
        assertThat(StringUtils.isArray("x")).isFalse();
        assertThat(StringUtils.isArray(null)).isFalse();
    }

    @Test
    void trim_shouldHandleNull()
    {
        assertThat(StringUtils.trim(null)).isEqualTo("");
        assertThat(StringUtils.trim(" a ")).isEqualTo("a");
    }

    @Test
    void hide_shouldMaskRange()
    {
        assertThat(StringUtils.hide(null, 0, 2)).isEqualTo("");
        assertThat(StringUtils.hide("abcdef", 1, 4)).isEqualTo("a***ef");
        assertThat(StringUtils.hide("ab", 2, 1)).isEqualTo("");
        assertThat(StringUtils.hide("a", 5, 2)).isEqualTo("");
    }

    @Test
    void substring_singleArg()
    {
        assertThat(StringUtils.substring("hello", 1)).isEqualTo("ello");
        assertThat(StringUtils.substring("hello", -1)).isEqualTo("o");
        assertThat(StringUtils.substring("hello", 10)).isEqualTo("");
        assertThat(StringUtils.substring(null, 1)).isEqualTo("");
    }

    @Test
    void substring_twoArgs()
    {
        assertThat(StringUtils.substring("hello", 1, 3)).isEqualTo("el");
        assertThat(StringUtils.substring("hello", -4, -2)).isEqualTo("el");
        assertThat(StringUtils.substring("hi", 2, 1)).isEqualTo("");
    }

    @Test
    void substringBetweenLast()
    {
        assertThat(StringUtils.substringBetweenLast("<a>x</a><b>y</b>", "<b>", "</b>")).isEqualTo("y");
        assertThat(StringUtils.substringBetweenLast(null, "a", "b")).isEqualTo("");
        assertThat(StringUtils.substringBetweenLast("nodata", "<b>", "</b>")).isEqualTo("");
    }

    @Test
    void hasText()
    {
        assertThat(StringUtils.hasText(null)).isFalse();
        assertThat(StringUtils.hasText(" ")).isFalse();
        assertThat(StringUtils.hasText("a")).isTrue();
    }

    @Test
    void format_shouldReplacePlaceholders()
    {
        assertThat(StringUtils.format("this is {} for {}", "a", "b")).isEqualTo("this is a for b");
        assertThat(StringUtils.format(null, "a")).isNull();
        assertThat(StringUtils.format("{}", (Object[]) null)).isEqualTo("{}");
        assertThat(StringUtils.format("", "a")).isEqualTo("");
    }

    @Test
    void ishttp()
    {
        assertThat(StringUtils.ishttp("http://x")).isTrue();
        assertThat(StringUtils.ishttp("https://x")).isTrue();
        assertThat(StringUtils.ishttp("ftp://x")).isFalse();
        assertThat(StringUtils.ishttp(null)).isFalse();
    }

    @Test
    void str2List_and_str2Set()
    {
        List<String> list = StringUtils.str2List("a,b,c", ",");
        assertThat(list).containsExactly("a", "b", "c");
        List<String> filtered = StringUtils.str2List("a,,b", ",", true, false);
        assertThat(filtered).containsExactly("a", "b");
        assertThat(StringUtils.str2List(null, ",")).isEmpty();
        assertThat(StringUtils.str2Set("a,b", ",")).containsExactly("a", "b");
    }

    @Test
    void containsAny()
    {
        assertThat(StringUtils.containsAny(Arrays.asList("a", "b"), "a", "c")).isTrue();
        assertThat(StringUtils.containsAny(java.util.Collections.emptyList(), "a")).isFalse();
        assertThat(StringUtils.containsAny(null, "a")).isFalse();
    }

    @Test
    void containsAnyIgnoreCase()
    {
        assertThat(StringUtils.containsAnyIgnoreCase("Hello", "ELL")).isTrue();
        assertThat(StringUtils.containsAnyIgnoreCase("hello", "XYZ")).isFalse();
    }

    @Test
    void toUnderScoreCase()
    {
        assertThat(StringUtils.toUnderScoreCase("helloWorld")).isEqualTo("hello_world");
        assertThat(StringUtils.toUnderScoreCase("userId")).isEqualTo("user_id");
        assertThat(StringUtils.toUnderScoreCase("HELLO")).isEqualTo("hello");
        assertThat(StringUtils.toUnderScoreCase("user_name")).isEqualTo("user_name");
    }

    @Test
    void convertToCamelCase()
    {
        assertThat(StringUtils.convertToCamelCase("hello_world")).isEqualTo("HelloWorld");
        assertThat(StringUtils.convertToCamelCase("HELLO_WORLD")).isEqualTo("HelloWorld");
        assertThat(StringUtils.convertToCamelCase(null)).isEqualTo("");
        assertThat(StringUtils.convertToCamelCase("hello")).isEqualTo("Hello");
    }

    @Test
    void toCamelCase()
    {
        assertThat(StringUtils.toCamelCase("user_name")).isEqualTo("userName");
        assertThat(StringUtils.toCamelCase(null)).isNull();
        assertThat(StringUtils.toCamelCase("username")).isEqualTo("username");
    }

    @Test
    void inStringIgnoreCase()
    {
        assertThat(StringUtils.inStringIgnoreCase("a", "A", "b")).isTrue();
        assertThat(StringUtils.inStringIgnoreCase("a", "B")).isFalse();
        assertThat(StringUtils.inStringIgnoreCase(null, "A")).isFalse();
    }

    @Test
    void matches_and_isMatch()
    {
        assertThat(StringUtils.matches("abc", Arrays.asList("a*"))).isTrue();
        assertThat(StringUtils.matches("", Arrays.asList("a*"))).isFalse();
        assertThat(StringUtils.isMatch("/**", "/api/x")).isTrue();
        assertThat(StringUtils.isMatch("/api", "/other")).isFalse();
    }

    @Test
    void cast()
    {
        String s = StringUtils.cast("x");
        assertThat(s).isEqualTo("x");
    }

    @Test
    void padl()
    {
        assertThat(StringUtils.padl(7, 3)).isEqualTo("007");
        assertThat(StringUtils.padl("7", 3, '0')).isEqualTo("007");
        assertThat(StringUtils.padl("12345", 3, '0')).isEqualTo("345");
        assertThat(StringUtils.padl((String) null, 3, '0')).isEqualTo("000");
        assertThat(StringUtils.padl("ab", 3, '0')).isEqualTo("0ab");
    }
}
