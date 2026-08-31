package com.ruoyi.biz.service.impl;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.biz.domain.SysProduct;
import com.ruoyi.biz.mapper.SysProductMapper;
import com.ruoyi.common.exception.ServiceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * SysProductServiceImpl 单元测试
 *
 * 纯 Mockito，不启动 Spring 容器。当前 Service 是 Mapper 的透传层，
 * 因此本测试主要锁定「调用契约」与「返回值语义」，并为已知缺陷预留验收用例。
 *
 * 运行：mvn test -pl ruoyi-system -am -Dtest=SysProductServiceImplTest
 *
 * @author life
 * @date 2026-08-30
 */
@ExtendWith(MockitoExtension.class)
class SysProductServiceImplTest
{
    @Mock
    private SysProductMapper sysProductMapper;

    @InjectMocks
    private SysProductServiceImpl sysProductService;

    private SysProduct buildProduct(Long productId, String name)
    {
        SysProduct product = new SysProduct();
        product.setProductId(productId);
        product.setParentId(0L);
        product.setProductName(name);
        product.setOrderNum(1);
        product.setStatus("0");
        return product;
    }

    @Test
    @DisplayName("按主键查询 - 存在时返回产品")
    void selectSysProductByProductId_exist_returnsProduct()
    {
        // given
        SysProduct expected = buildProduct(100L, "模具管理系统");
        given(sysProductMapper.selectSysProductByProductId(100L)).willReturn(expected);

        // when
        SysProduct actual = sysProductService.selectSysProductByProductId(100L);

        // then
        assertThat(actual).isNotNull();
        assertThat(actual.getProductId()).isEqualTo(100L);
        assertThat(actual.getProductName()).isEqualTo("模具管理系统");
        verify(sysProductMapper).selectSysProductByProductId(100L);
    }

    @Test
    @DisplayName("按主键查询 - 不存在时返回 null")
    void selectSysProductByProductId_notExist_returnsNull()
    {
        // given
        given(sysProductMapper.selectSysProductByProductId(999L)).willReturn(null);

        // when
        SysProduct actual = sysProductService.selectSysProductByProductId(999L);

        // then
        assertThat(actual).isNull();
    }

    @Test
    @DisplayName("条件查询 - 原样透传给 Mapper 并返回结果")
    void selectSysProductList_withCondition_delegatesToMapper()
    {
        // given
        SysProduct query = new SysProduct();
        query.setProductName("模具");
        List<SysProduct> expected = Collections.singletonList(buildProduct(100L, "模具管理系统"));
        given(sysProductMapper.selectSysProductList(query)).willReturn(expected);

        // when
        List<SysProduct> actual = sysProductService.selectSysProductList(query);

        // then
        assertThat(actual).hasSize(1);
        assertThat(actual.get(0).getProductName()).contains("模具");
        verify(sysProductMapper).selectSysProductList(query);
    }

    @Test
    @DisplayName("条件查询 - 无命中时返回空列表而非 null")
    void selectSysProductList_noMatch_returnsEmptyList()
    {
        // given
        given(sysProductMapper.selectSysProductList(any(SysProduct.class)))
            .willReturn(Collections.emptyList());

        // when
        List<SysProduct> actual = sysProductService.selectSysProductList(new SysProduct());

        // then
        assertThat(actual).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("新增 - 返回影响行数 1")
    void insertSysProduct_success_returnsOne()
    {
        // given
        SysProduct product = buildProduct(null, "新产品");
        given(sysProductMapper.insertSysProduct(product)).willReturn(1);

        // when
        int rows = sysProductService.insertSysProduct(product);

        // then
        assertThat(rows).isEqualTo(1);
        verify(sysProductMapper).insertSysProduct(product);
    }

    @Test
    @DisplayName("修改 - 影响行数 0 时返回 0（调用方 toAjax 会判为操作失败）")
    void updateSysProduct_zeroRows_returnsZero()
    {
        // given
        SysProduct product = buildProduct(999L, "不存在的产品");
        given(sysProductMapper.updateSysProduct(product)).willReturn(0);

        // when
        int rows = sysProductService.updateSysProduct(product);

        // then
        assertThat(rows).isZero();
    }

    @Test
    @DisplayName("批量删除 - 返回影响行数")
    void deleteSysProductByProductIds_returnsAffectedRows()
    {
        // given
        Long[] ids = new Long[] { 100L, 101L };
        given(sysProductMapper.deleteSysProductByProductIds(ids)).willReturn(2);

        // when
        int rows = sysProductService.deleteSysProductByProductIds(ids);

        // then
        assertThat(rows).isEqualTo(2);
        verify(sysProductMapper).deleteSysProductByProductIds(ids);
    }

    @Test
    @DisplayName("单个删除 - 返回影响行数")
    void deleteSysProductByProductId_returnsAffectedRows()
    {
        // given
        given(sysProductMapper.deleteSysProductByProductId(100L)).willReturn(1);

        // when
        int rows = sysProductService.deleteSysProductByProductId(100L);

        // then
        assertThat(rows).isEqualTo(1);
        verify(sysProductMapper).deleteSysProductByProductId(100L);
    }

    /**
     * 已知缺陷（P1-2）：删除父节点时未校验子节点，会产生孤儿数据。
     *
     * 当前实现直接透传给 Mapper 的 delete ... where product_id in (...)，
     * 删除父节点后其子节点在前端树里将永远不可见。
     *
     * 修复步骤：
     *   1. 在 SysProductMapper.xml 的 where 中补充 parent_id 条件（当前只支持 productName）
     *   2. 在 SysProductServiceImpl.deleteSysProductByProductIds 中先查子节点，
     *      存在则 throw new ServiceException("存在下级产品，不允许删除")
     *   3. 移除本用例的 @Disabled，测试应转为通过
     *
     * 参考：docs/plans/current-sprint.md 的 P1-2
     */
    @Test
    @Disabled("P1-2 待修复：树形删除未校验子节点。修复后移除本注解，用例应转为通过")
    @DisplayName("删除有子节点的父节点 - 应抛 ServiceException（当前实现未校验，缺陷）")
    void deleteSysProductByProductIds_hasChildren_shouldThrowServiceException()
    {
        // given：父节点 100 下存在子节点 101
        SysProduct child = buildProduct(101L, "冲压模");
        child.setParentId(100L);
        SysProduct childQuery = new SysProduct();
        childQuery.setParentId(100L);
        given(sysProductMapper.selectSysProductList(childQuery))
            .willReturn(Collections.singletonList(child));

        // when & then
        assertThatThrownBy(() -> sysProductService.deleteSysProductByProductIds(new Long[] { 100L }))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("下级");
    }

    /**
     * 边界说明（非缺陷）：空数组会原样透传给 Mapper。
     *
     * MyBatis 的 foreach 对空数组会生成 `in ()`，属非法 SQL。
     * 但前端删除按钮不会传空数组，且 Controller 层可拦截，暂不处理。
     * 这里用用例把「会透传」这个当前行为记录下来，避免有人误以为已有防护。
     */
    @Test
    @DisplayName("批量删除 - 空数组会原样透传给 Mapper（记录当前行为）")
    void deleteSysProductByProductIds_emptyArray_stillDelegates()
    {
        // given
        Long[] empty = new Long[0];
        given(sysProductMapper.deleteSysProductByProductIds(empty)).willReturn(0);

        // when
        int rows = sysProductService.deleteSysProductByProductIds(empty);

        // then
        assertThat(rows).isZero();
        verify(sysProductMapper).deleteSysProductByProductIds(empty);
    }
}
