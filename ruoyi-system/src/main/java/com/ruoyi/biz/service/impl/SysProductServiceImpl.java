package com.ruoyi.biz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.biz.mapper.SysProductMapper;
import com.ruoyi.biz.domain.SysProduct;
import com.ruoyi.biz.service.ISysProductService;
import com.ruoyi.common.exception.ServiceException;

/**
 * 产品管理Service业务层处理
 * 
 * @author life
 * @date 2026-08-07
 */
@Service
public class SysProductServiceImpl implements ISysProductService 
{
    @Autowired
    private SysProductMapper sysProductMapper;

    /**
     * 查询产品管理
     * 
     * @param productId 产品管理主键
     * @return 产品管理
     */
    @Override
    public SysProduct selectSysProductByProductId(Long productId)
    {
        return sysProductMapper.selectSysProductByProductId(productId);
    }

    /**
     * 查询产品管理列表
     * 
     * @param sysProduct 产品管理
     * @return 产品管理
     */
    @Override
    public List<SysProduct> selectSysProductList(SysProduct sysProduct)
    {
        return sysProductMapper.selectSysProductList(sysProduct);
    }

    /**
     * 新增产品管理
     * 
     * @param sysProduct 产品管理
     * @return 结果
     */
    @Override
    public int insertSysProduct(SysProduct sysProduct)
    {
        return sysProductMapper.insertSysProduct(sysProduct);
    }

    /**
     * 修改产品管理
     * 
     * @param sysProduct 产品管理
     * @return 结果
     */
    @Override
    public int updateSysProduct(SysProduct sysProduct)
    {
        return sysProductMapper.updateSysProduct(sysProduct);
    }

    /**
     * 批量删除产品管理
     * 
     * @param productIds 需要删除的产品管理主键
     * @return 结果
     */
    @Override
    public int deleteSysProductByProductIds(Long[] productIds)
    {
        // P1-2 修复：删除前先校验是否存在子节点，避免产生孤儿数据
        for (Long productId : productIds)
        {
            SysProduct childQuery = new SysProduct();
            childQuery.setParentId(productId);
            List<SysProduct> children = sysProductMapper.selectSysProductList(childQuery);
            if (children != null && !children.isEmpty())
            {
                throw new ServiceException("存在下级产品，不允许删除");
            }
        }
        return sysProductMapper.deleteSysProductByProductIds(productIds);
    }

    /**
     * 删除产品管理信息
     * 
     * @param productId 产品管理主键
     * @return 结果
     */
    @Override
    public int deleteSysProductByProductId(Long productId)
    {
        return sysProductMapper.deleteSysProductByProductId(productId);
    }
}
