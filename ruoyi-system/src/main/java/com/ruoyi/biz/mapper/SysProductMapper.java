package com.ruoyi.biz.mapper;

import java.util.List;
import com.ruoyi.biz.domain.SysProduct;

/**
 * 产品管理Mapper接口
 * 
 * @author life
 * @date 2026-08-07
 */
public interface SysProductMapper 
{
    /**
     * 查询产品管理
     * 
     * @param productId 产品管理主键
     * @return 产品管理
     */
    public SysProduct selectSysProductByProductId(Long productId);

    /**
     * 查询产品管理列表
     * 
     * @param sysProduct 产品管理
     * @return 产品管理集合
     */
    public List<SysProduct> selectSysProductList(SysProduct sysProduct);

    /**
     * 新增产品管理
     * 
     * @param sysProduct 产品管理
     * @return 结果
     */
    public int insertSysProduct(SysProduct sysProduct);

    /**
     * 修改产品管理
     * 
     * @param sysProduct 产品管理
     * @return 结果
     */
    public int updateSysProduct(SysProduct sysProduct);

    /**
     * 删除产品管理
     * 
     * @param productId 产品管理主键
     * @return 结果
     */
    public int deleteSysProductByProductId(Long productId);

    /**
     * 批量删除产品管理
     * 
     * @param productIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSysProductByProductIds(Long[] productIds);
}
