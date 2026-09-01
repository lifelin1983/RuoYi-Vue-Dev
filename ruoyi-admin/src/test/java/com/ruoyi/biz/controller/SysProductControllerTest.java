package com.ruoyi.biz.controller;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.biz.domain.SysProduct;
import com.ruoyi.biz.service.ISysProductService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SysProductController 接口测试（Harness P1-5）
 *
 * 用 standalone MockMvc（不启动 Spring 容器，故不需要 Redis / Spring Security 上下文，
 * @PreAuthorize 在 standalone 下不生效，仅测试 Controller 本身的请求映射与响应结构）。
 * 鉴权正确性由 ArchUnit 规则 bizControllerMethodsMustBeSecured 在 CI 强制，
 * 不在本测试重复验证。
 *
 * 运行：mvn test -pl ruoyi-admin -am -Dtest=SysProductControllerTest -DfailIfNoTests=false
 *
 * @author life
 * @date 2026-09-01
 */
@ExtendWith(MockitoExtension.class)
class SysProductControllerTest
{
    @Mock
    private ISysProductService sysProductService;

    @InjectMocks
    private SysProductController sysProductController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        mockMvc = MockMvcBuilders.standaloneSetup(sysProductController).build();
    }

    private SysProduct buildProduct(Long id, String name)
    {
        SysProduct p = new SysProduct();
        p.setProductId(id);
        p.setProductName(name);
        return p;
    }

    @Test
    @DisplayName("GET /list - 返回产品列表（code=200, data 为数组）")
    void list_returnsProducts() throws Exception
    {
        List<SysProduct> list = Collections.singletonList(buildProduct(100L, "模具管理系统"));
        given(sysProductService.selectSysProductList(any(SysProduct.class))).willReturn(list);

        mockMvc.perform(get("/biz/product/list"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].productName").value("模具管理系统"));
    }

    @Test
    @DisplayName("GET /{id} - 返回单条详情")
    void getInfo_returnsProduct() throws Exception
    {
        given(sysProductService.selectSysProductByProductId(100L)).willReturn(buildProduct(100L, "模具管理系统"));

        mockMvc.perform(get("/biz/product/100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.productId").value(100));
    }

    @Test
    @DisplayName("POST / - 新增返回成功")
    void add_returnsSuccess() throws Exception
    {
        given(sysProductService.insertSysProduct(any(SysProduct.class))).willReturn(1);

        mockMvc.perform(post("/biz/product")
                .contentType("application/json")
                .content("{\"productName\":\"新模具\",\"parentId\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT / - 修改返回成功")
    void edit_returnsSuccess() throws Exception
    {
        given(sysProductService.updateSysProduct(any(SysProduct.class))).willReturn(1);

        mockMvc.perform(put("/biz/product")
                .contentType("application/json")
                .content("{\"productId\":100,\"productName\":\"改模\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("DELETE /{ids} - 批量删除返回成功")
    void remove_returnsSuccess() throws Exception
    {
        given(sysProductService.deleteSysProductByProductIds(any(Long[].class))).willReturn(2);

        mockMvc.perform(delete("/biz/product/100,101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /export - 导出写入工作簿且不报错")
    void export_writesWorkbook() throws Exception
    {
        given(sysProductService.selectSysProductList(any(SysProduct.class)))
            .willReturn(Collections.singletonList(buildProduct(100L, "模具管理系统")));

        mockMvc.perform(post("/biz/product/export"))
            .andExpect(status().isOk());
    }
}
