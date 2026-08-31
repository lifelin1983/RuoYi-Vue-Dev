package com.ruoyi.biz.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.biz.domain.SysProduct;
import com.ruoyi.biz.service.ISysProductService;
import com.ruoyi.common.utils.poi.ExcelUtil;

/**
 * 产品管理Controller
 * 
 * @author life
 * @date 2026-08-07
 */
@RestController
@RequestMapping("/biz/product")
public class SysProductController extends BaseController
{
    @Autowired
    private ISysProductService sysProductService;

    /**
     * 查询产品管理列表
     */
    @PreAuthorize("@ss.hasPermi('biz:product:list')")
    @GetMapping("/list")
    public AjaxResult list(SysProduct sysProduct)
    {
        List<SysProduct> list = sysProductService.selectSysProductList(sysProduct);
        return success(list);
    }

    /**
     * 导出产品管理列表
     */
    @PreAuthorize("@ss.hasPermi('biz:product:export')")
    @Log(title = "产品管理", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysProduct sysProduct)
    {
        List<SysProduct> list = sysProductService.selectSysProductList(sysProduct);
        ExcelUtil<SysProduct> util = new ExcelUtil<SysProduct>(SysProduct.class);
        util.exportExcel(response, list, "产品管理数据");
    }

    /**
     * 获取产品管理详细信息
     */
    @PreAuthorize("@ss.hasPermi('biz:product:query')")
    @GetMapping(value = "/{productId}")
    public AjaxResult getInfo(@PathVariable("productId") Long productId)
    {
        return success(sysProductService.selectSysProductByProductId(productId));
    }

    /**
     * 新增产品管理
     */
    @PreAuthorize("@ss.hasPermi('biz:product:add')")
    @Log(title = "产品管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody SysProduct sysProduct)
    {
        return toAjax(sysProductService.insertSysProduct(sysProduct));
    }

    /**
     * 修改产品管理
     */
    @PreAuthorize("@ss.hasPermi('biz:product:edit')")
    @Log(title = "产品管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody SysProduct sysProduct)
    {
        return toAjax(sysProductService.updateSysProduct(sysProduct));
    }

    /**
     * 删除产品管理
     */
    @PreAuthorize("@ss.hasPermi('biz:product:remove')")
    @Log(title = "产品管理", businessType = BusinessType.DELETE)
	@DeleteMapping("/{productIds}")
    public AjaxResult remove(@PathVariable Long[] productIds)
    {
        return toAjax(sysProductService.deleteSysProductByProductIds(productIds));
    }
}
