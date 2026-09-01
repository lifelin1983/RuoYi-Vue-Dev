package com.ruoyi.common.utils.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.ruoyi.common.exception.UtilException;
import org.junit.jupiter.api.Test;

/**
 * SqlUtil 单元测试（SQL 注入/排序注入防护，安全相关）
 */
public class SqlUtilTest
{
    @Test
    void escapeOrderBySql_valid()
    {
        assertThat(SqlUtil.escapeOrderBySql("id")).isEqualTo("id");
        assertThat(SqlUtil.escapeOrderBySql("id desc")).isEqualTo("id desc");
        assertThat(SqlUtil.escapeOrderBySql("id, name asc")).isEqualTo("id, name asc");
    }

    @Test
    void escapeOrderBySql_nullReturnsNull()
    {
        assertThat(SqlUtil.escapeOrderBySql(null)).isNull();
    }

    @Test
    void escapeOrderBySql_invalidThrows()
    {
        assertThatThrownBy(() -> SqlUtil.escapeOrderBySql("id; DROP TABLE"))
                .isInstanceOf(UtilException.class);
        assertThatThrownBy(() -> SqlUtil.escapeOrderBySql("1=1"))
                .isInstanceOf(UtilException.class);
    }

    @Test
    void escapeOrderBySql_tooLongThrows()
    {
        String longValue = String.join("", java.util.Collections.nCopies(600, "a"));
        assertThat(longValue.length()).isGreaterThan(500);
        assertThatThrownBy(() -> SqlUtil.escapeOrderBySql(longValue))
                .isInstanceOf(UtilException.class);
    }

    @Test
    void isValidOrderBySql()
    {
        assertThat(SqlUtil.isValidOrderBySql("id")).isTrue();
        assertThat(SqlUtil.isValidOrderBySql("id;drop")).isFalse();
    }

    @Test
    void filterKeyword()
    {
        // SQL_REGEX 中关键字多带尾随空格，normalize 会先剥离空白；用无空格的禁用词验证防护生效
        assertThatThrownBy(() -> SqlUtil.filterKeyword("sleep"))
                .isInstanceOf(UtilException.class);
        // 安全文本/空值 -> 不抛异常（提前返回）
        SqlUtil.filterKeyword("hello world");
        SqlUtil.filterKeyword(null);
        SqlUtil.filterKeyword("");
    }
}
