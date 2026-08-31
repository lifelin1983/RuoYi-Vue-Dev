package com.ruoyi.biz.mapper;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.biz.domain.SysProduct;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SysProductMapper 数据访问测试
 *
 * 用 @MybatisTest 做切片测试（只加载 MyBatis + 数据源，不启动 Web 层），
 * 数据在 H2 内存库（MODE=MySQL）中执行，每个用例结束后由 @Transactional 回滚。
 *
 * 目的：验证 SysProductMapper.xml 中的每条 SQL 都能正确执行，
 * 并暴露 resultMap 遗漏字段这类问题（本项目 mapUnderscoreToCamelCase 已关闭）。
 *
 * 运行：mvn test -pl ruoyi-system -am -Dtest=SysProductMapperTest
 *
 * @author life
 * @date 2026-08-30
 */
@MybatisTest
@ActiveProfiles("test")
@Sql(scripts = { "/sql/schema.sql", "/sql/data.sql" })
@Transactional
class SysProductMapperTest
{
    @Autowired
    private SysProductMapper sysProductMapper;

    private SysProduct buildProduct(String name, Long parentId)
    {
        SysProduct product = new SysProduct();
        product.setParentId(parentId);
        product.setProductName(name);
        product.setOrderNum(1);
        product.setStatus("0");
        return product;
    }

    @Test
    @DisplayName("selectSysProductList - 无条件返回全部 3 条")
    void selectSysProductList_noCondition_returnsAll()
    {
        List<SysProduct> list = sysProductMapper.selectSysProductList(new SysProduct());

        assertThat(list).hasSize(3);
    }

    @Test
    @DisplayName("selectSysProductList - 按名称模糊匹配")
    void selectSysProductList_byNameLike_returnsMatched()
    {
        SysProduct query = new SysProduct();
        query.setProductName("模具");

        List<SysProduct> list = sysProductMapper.selectSysProductList(query);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getProductName()).isEqualTo("模具管理系统");
    }

    @Test
    @DisplayName("selectSysProductByProductId - 存在时返回且字段映射正确")
    void selectSysProductByProductId_exist_mapsAllFields()
    {
        SysProduct product = sysProductMapper.selectSysProductByProductId(100L);

        assertThat(product).isNotNull();
        assertThat(product.getProductId()).isEqualTo(100L);
        assertThat(product.getParentId()).isEqualTo(0L);
        assertThat(product.getProductName()).isEqualTo("模具管理系统");
        assertThat(product.getOrderNum()).isEqualTo(1);
        assertThat(product.getStatus()).isEqualTo("0");
    }

    @Test
    @DisplayName("selectSysProductByProductId - 不存在时返回 null")
    void selectSysProductByProductId_notExist_returnsNull()
    {
        assertThat(sysProductMapper.selectSysProductByProductId(999L)).isNull();
    }

    @Test
    @DisplayName("insertSysProduct - 主键回填且可查回")
    void insertSysProduct_generatesKeyAndPersists()
    {
        SysProduct product = buildProduct("新产品", 0L);

        int rows = sysProductMapper.insertSysProduct(product);

        assertThat(rows).isEqualTo(1);
        assertThat(product.getProductId()).isNotNull();

        SysProduct saved = sysProductMapper.selectSysProductByProductId(product.getProductId());
        assertThat(saved).isNotNull();
        assertThat(saved.getProductName()).isEqualTo("新产品");
    }

    @Test
    @DisplayName("insertSysProduct - 只插入非 null 字段（trim 动态列生效）")
    void insertSysProduct_skipsNullColumns()
    {
        SysProduct product = new SysProduct();
        product.setProductName("仅名称");

        int rows = sysProductMapper.insertSysProduct(product);

        assertThat(rows).isEqualTo(1);
        SysProduct saved = sysProductMapper.selectSysProductByProductId(product.getProductId());
        assertThat(saved.getProductName()).isEqualTo("仅名称");
        assertThat(saved.getStatus()).isEqualTo("0");
    }

    @Test
    @DisplayName("updateSysProduct - 更新后字段生效")
    void updateSysProduct_changesField()
    {
        SysProduct product = sysProductMapper.selectSysProductByProductId(100L);
        product.setProductName("改名后的产品");
        product.setStatus("1");

        int rows = sysProductMapper.updateSysProduct(product);

        assertThat(rows).isEqualTo(1);
        SysProduct updated = sysProductMapper.selectSysProductByProductId(100L);
        assertThat(updated.getProductName()).isEqualTo("改名后的产品");
        assertThat(updated.getStatus()).isEqualTo("1");
    }

    @Test
    @DisplayName("updateSysProduct - 主键不存在时影响 0 行")
    void updateSysProduct_notExist_affectsZeroRows()
    {
        SysProduct product = buildProduct("不存在", 0L);
        product.setProductId(999L);

        assertThat(sysProductMapper.updateSysProduct(product)).isZero();
    }

    @Test
    @DisplayName("deleteSysProductByProductId - 删除后查不到")
    void deleteSysProductByProductId_removesRow()
    {
        int rows = sysProductMapper.deleteSysProductByProductId(100L);

        assertThat(rows).isEqualTo(1);
        assertThat(sysProductMapper.selectSysProductByProductId(100L)).isNull();
    }

    @Test
    @DisplayName("deleteSysProductByProductIds - 批量删除")
    void deleteSysProductByProductIds_removesMultipleRows()
    {
        int rows = sysProductMapper.deleteSysProductByProductIds(new Long[] { 100L, 101L });

        assertThat(rows).isEqualTo(2);
        assertThat(sysProductMapper.selectSysProductList(new SysProduct())).hasSize(1);
    }

    /**
     * 验收用例（P1-2 前置已修复）：XML 的 where 已支持 parentId 过滤。
     *
     * 查询 parentId=100 应返回其下 2 个子节点（101/102）。
     */
    @Test
    @DisplayName("selectSysProductList - 按 parentId 过滤返回子节点")
    void selectSysProductList_byParentId_shouldFilter()
    {
        SysProduct query = new SysProduct();
        query.setParentId(100L);

        List<SysProduct> list = sysProductMapper.selectSysProductList(query);

        assertThat(list).hasSize(2);
    }
}
