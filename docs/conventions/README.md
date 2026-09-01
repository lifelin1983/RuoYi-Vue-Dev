# 编码规范

> 本规范从**现有代码实际写法**中提炼，不是凭空制定。所有示例均取自本仓库真实文件。
> 相关文档：[测试规范](./testing.md) ｜ [模块边界](../architecture/boundaries.md) ｜ [错误码](../reference/error-codes.md)

---

## 目录

1. [通用原则](#1-通用原则)
2. [Java 规范](#2-java-规范)
3. [MyBatis 规范](#3-mybatis-规范)
4. [REST 接口规范](#4-rest-接口规范)
5. [前端规范](#5-前端规范-vue2--element-ui)
6. [数据库规范](#6-数据库规范)
7. [Git 与提交规范](#7-git-与提交规范)
8. [代码生成器使用约定](#8-代码生成器使用约定)

---

## 1. 通用原则

| # | 原则 | 说明 |
|---|------|------|
| 1 | **照抄现有代码** | 不确定怎么写时，找一个同类型的现有类照抄，优先于翻规范 |
| 2 | **一致性 > 个人偏好** | RuoYi 的写法未必最优，但全仓库统一比局部优雅重要 |
| 3 | **新代码优先用生成器** | 能用 `ruoyi-generator` 生成的就不要手写，保证风格零偏差 |
| 4 | **不改框架源码** | `views/system/**`、`api/system/**`、`common/**` 等框架目录，需求通过扩展满足 |
| 5 | **注释写"为什么"** | 代码本身说明"做什么"，注释只解释业务背景与非直觉决策 |

---

## 2. Java 规范

### 2.1 文件格式

- 编码 **UTF-8**，换行 **LF**，文件末尾保留一个空行
- 缩进 **4 个空格**，**禁用 Tab**（历史文件里少量 Tab 属遗留，新代码不得再出现）
- 行宽建议 ≤ 120

### 2.2 大括号：Allman 风格（独占一行）

这是 RuoYi 最显著的风格特征，**必须遵守**：

```java
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
}
```

> `if / for / try / switch` 同样如此，`else` 另起一行：
> ```java
> if (StringUtils.isNotNull(code))
> {
>     return AjaxResult.error(code, e.getMessage());
> }
> else
> {
>     return AjaxResult.error(e.getMessage());
> }
> ```

### 2.3 命名

| 元素 | 规则 | 示例 |
|------|------|------|
| 包 | 全小写，单数 | `com.ruoyi.biz.controller` |
| 类 | 大驼峰 | `SysProductController`、`SysProductServiceImpl` |
| Service 接口 | **`I` + 大驼峰** | `ISysProductService` |
| Service 实现 | 大驼峰 + `Impl` | `SysProductServiceImpl` |
| Mapper 接口 | 大驼峰 + `Mapper` | `SysProductMapper` |
| 实体 | 大驼峰，与表名对应 | `SysProduct` ↔ `sys_product` |
| 方法 | 小驼峰，动词开头 | `selectSysProductList` |
| 变量/字段 | 小驼峰 | `productName`、`sysProductService` |
| 常量 | 全大写 + 下划线 | `CODE_TAG`、`SYS_USER` |
| 枚举值 | 全大写 | `INSERT`、`EXPORT` |
| 布尔字段 | 不加 `is` 前缀 | `enabled`（而非 `isEnabled`） |

### 2.4 Service 方法五件套（固定命名，禁止自创）

```
selectXxxByXxxId(Long xxxId)      查询单个
selectXxxList(Xxx xxx)            查询列表（入参即查询条件）
insertXxx(Xxx xxx)                新增
updateXxx(Xxx xxx)                修改
deleteXxxByXxxIds(Long[] ids)     批量删除
deleteXxxByXxxId(Long xxxId)      单个删除
```

### 2.5 Javadoc

**类头必须带 `@author` 与 `@date`**（本仓库业务代码使用 `@author life`）：

```java
/**
 * 产品管理Controller
 *
 * @author life
 * @date 2026-08-07
 */
```

**接口方法必须写完整 Javadoc**（`@param` + `@return`）：

```java
/**
 * 查询产品管理
 *
 * @param productId 产品管理主键
 * @return 产品管理
 */
public SysProduct selectSysProductByProductId(Long productId);
```

**实现类方法**：脚手架生成的实现类保留与接口一致的注释；手写方法至少写一行 `/** ... */`。

**字段注释**：用 `/** xxx */` 单行形式：

```java
/** 产品id */
private Long productId;

/** 产品状态（0正常 1停用） */
@Excel(name = "产品状态", readConverterExp = "0=正常,1=停用")
private String status;
```

### 2.6 实体类规范

```java
public class SysProduct extends TreeEntity
{
    private static final long serialVersionUID = 1L;

    /** 产品id */
    private Long productId;

    /** 产品名称 */
    @Excel(name = "产品名称")
    private String productName;

    public void setProductId(Long productId)
    {
        this.productId = productId;
    }

    public Long getProductId()
    {
        return productId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("productId", getProductId())
            .append("productName", getProductName())
            .append("status", getStatus())
            .toString();
    }
}
```

强制项：

1. 必须 `private static final long serialVersionUID = 1L;`
2. 继承 `BaseEntity`（普通实体）或 `TreeEntity`（树形实体）
3. 字段用**包装类型**（`Long` / `Integer`，不用 `long` / `int`）
4. getter/setter **手写成块**，不用 Lombok（本仓库未引入 Lombok）
5. `toString()` 用 `ToStringBuilder` + `ToStringStyle.MULTI_LINE_STYLE`
6. 需要导出的字段加 `@Excel(name="中文列名")`，字典值加 `readConverterExp`
7. 日期字段加 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`
8. 状态字段必须在注释里写明取值含义，如 `/** 产品状态（0正常 1停用） */`

### 2.7 依赖注入

统一用字段注入 `@Autowired`（全仓库一致，不要求构造器注入）：

```java
@Autowired
private ISysProductService sysProductService;
```

### 2.8 import 顺序

脚手架生成的顺序为：**JDK → 第三方 → `com.ruoyi.*`**，实际以"按包名分组"为准：

```java
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.biz.domain.SysProduct;
import com.ruoyi.biz.service.ISysProductService;
import com.ruoyi.common.utils.poi.ExcelUtil;
```

- 不使用通配符 `import x.y.*`（示例代码为节省篇幅才这么写）
- 不 import 未使用的类

### 2.9 异常处理

- 业务校验失败抛 **`ServiceException`**（会被 `GlobalExceptionHandler` 捕获并转成 `AjaxResult.error`）
- 禁止 `catch` 后吞掉异常、禁止 `e.printStackTrace()`
- 禁止在 Controller 里 `try-catch` 只为返回错误信息（交给全局处理器）
- 日志用 `protected final Logger logger = BaseController.logger`（继承即得）

```java
// ✅ 正确
if (UserConstants.NOT_UNIQUE.equals(userService.checkUserNameUnique(userName)))
{
    throw new ServiceException("新增用户'" + userName + "'失败，登录账号已存在");
}

// ❌ 错误
try { ... } catch (Exception e) { return AjaxResult.error("失败"); }
```

---

## 3. MyBatis 规范

### 3.1 文件落位

| 文件 | 路径 |
|------|------|
| Mapper 接口 | `ruoyi-system/src/main/java/com/ruoyi/biz/mapper/XxxMapper.java` |
| Mapper XML | `ruoyi-system/src/main/resources/mapper/biz/XxxMapper.xml` |

> ⚠️ `application.yml` 中 `mapperLocations: classpath*:mapper/**/*Mapper.xml`
> ——文件名**必须以 `Mapper.xml` 结尾**，否则扫描不到（表现为 `BindingException`）。

### 3.2 XML 固定结构

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
"http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.ruoyi.biz.mapper.SysProductMapper">

    <resultMap type="SysProduct" id="SysProductResult">
        <result property="productId"    column="product_id"    />
        <result property="parentId"     column="parent_id"     />
        <result property="productName"  column="product_name"  />
        <result property="orderNum"     column="order_num"     />
        <result property="status"       column="status"        />
    </resultMap>

    <sql id="selectSysProductVo">
        select product_id, parent_id, product_name, order_num, status from sys_product
    </sql>

    <select id="selectSysProductList" parameterType="SysProduct" resultMap="SysProductResult">
        <include refid="selectSysProductVo"/>
        <where>
            <if test="productName != null  and productName != ''"> and product_name like concat('%', #{productName}, '%')</if>
        </where>
    </select>

    <select id="selectSysProductByProductId" parameterType="Long" resultMap="SysProductResult">
        <include refid="selectSysProductVo"/>
        where product_id = #{productId}
    </select>

    <insert id="insertSysProduct" parameterType="SysProduct" useGeneratedKeys="true" keyProperty="productId">
        insert into sys_product
        <trim prefix="(" suffix=")" suffixOverrides=",">
            <if test="parentId != null">parent_id,</if>
            <if test="productName != null">product_name,</if>
        </trim>
        <trim prefix="values (" suffix=")" suffixOverrides=",">
            <if test="parentId != null">#{parentId},</if>
            <if test="productName != null">#{productName},</if>
        </trim>
    </insert>

    <update id="updateSysProduct" parameterType="SysProduct">
        update sys_product
        <trim prefix="SET" suffixOverrides=",">
            <if test="parentId != null">parent_id = #{parentId},</if>
            <if test="productName != null">product_name = #{productName},</if>
        </trim>
        where product_id = #{productId}
    </update>

    <delete id="deleteSysProductByProductIds" parameterType="String">
        delete from sys_product where product_id in
        <foreach item="productId" collection="array" open="(" separator="," close=")">
            #{productId}
        </foreach>
    </delete>
</mapper>
```

### 3.3 强制约定

| # | 约定 | 原因 |
|---|------|------|
| 1 | `resultMap` id 固定为 `<Xxx>Result` | 生成器与人工保持一致 |
| 2 | 查询列片段 `<sql id="selectXxxVo">` | 多处 include，避免列遗漏 |
| 3 | 字符串条件判空写 `!= null and != ''` | 与生成器输出一致 |
| 4 | 模糊查询用 `like concat('%', #{field}, '%')` | 防 SQL 注入 |
| 5 | `resultMap` 中用短类名（`SysProduct`） | `typeAliasesPackage: com.ruoyi.**.domain` 已配置 |
| 6 | **新增实体字段必须同步改 `resultMap` 和 `selectXxxVo`** | `mapUnderscoreToCamelCase` 已关闭，不加就查不出来 |
| 7 | 排序字段必须过 `SqlUtil.escapeOrderBySql()` | 防注入 |
| 8 | 不用 `select *` | 明确列清单 |
| 9 | 批量删除 `foreach collection="array"` | 入参是 `Long[]` |

---

## 4. REST 接口规范

### 4.1 URL 与方法映射

统一前缀 `/biz/{module}`（业务模块），方法语义固定：

| 操作 | 方法 | 路径 | 返回 |
|------|------|------|------|
| 列表 | `GET` | `/biz/product/list` | `TableDataInfo` 或 `AjaxResult` |
| 详情 | `GET` | `/biz/product/{productId}` | `AjaxResult`（data 为对象） |
| 导出 | `POST` | `/biz/product/export` | `void`（直接写 response） |
| 新增 | `POST` | `/biz/product` | `AjaxResult` |
| 修改 | `PUT` | `/biz/product` | `AjaxResult` |
| 删除 | `DELETE` | `/biz/product/{productIds}` | `AjaxResult` |

- 路径变量用**复数**命名表示可批量：`{productIds}`，Java 侧收 `Long[] productIds`
- 新增/修改用 `@RequestBody`，列表查询用对象直接接收（`SysProduct sysProduct`）

### 4.2 权限注解（强制）

```java
@PreAuthorize("@ss.hasPermi('biz:product:list')")
```

权限串格式：`模块:功能:操作`，操作固定为 `list` / `query` / `add` / `edit` / `remove` / `export`。
前后端权限串必须完全一致。

### 4.3 操作日志注解（写操作强制）

```java
@Log(title = "产品管理", businessType = BusinessType.INSERT)
```

`BusinessType` 取值：`OTHER` `INSERT` `UPDATE` `DELETE` `GRANT` `EXPORT` `IMPORT` `FORCE` `GENCODE` `CLEAN`

### 4.4 响应封装（禁止自建响应体）

```java
// 分页列表
startPage();
List<SysStudent> list = sysStudentService.selectSysStudentList(sysStudent);
return getDataTable(list);

// 树形/不分页列表
return success(list);

// 详情
return success(sysProductService.selectSysProductByProductId(productId));

// 增删改：用影响行数判定
return toAjax(sysProductService.insertSysProduct(sysProduct));

// 失败
return error("产品名称已存在");
return warn("部分数据处理失败");
```

### 4.5 完整 Controller 骨架

```java
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
```

---

## 5. 前端规范（Vue2 + Element UI）

> ⚠️ 项目是 **Vue 2.6 + Options API**。禁止使用 `<script setup>`、Composition API、Vue3 语法。

### 5.1 目录约定

| 类型 | 路径 | 命名 |
|------|------|------|
| 接口 | `src/api/biz/xxx.js` | 小驼峰，与后端模块同名 |
| 页面 | `src/views/biz/xxx/index.vue` | 小驼峰目录 + `index.vue` |
| 组件 | `src/components/XxxYyy/index.vue` | 大驼峰（全局复用组件） |

### 5.2 API 文件模板

```js
import request from '@/utils/request'

// 查询产品管理列表
export function listProduct(query) {
  return request({
    url: '/biz/product/list',
    method: 'get',
    params: query
  })
}

// 查询产品管理详细
export function getProduct(productId) {
  return request({
    url: '/biz/product/' + productId,
    method: 'get'
  })
}

// 新增产品管理
export function addProduct(data) {
  return request({
    url: '/biz/product',
    method: 'post',
    data: data
  })
}

// 修改产品管理
export function updateProduct(data) {
  return request({
    url: '/biz/product',
    method: 'put',
    data: data
  })
}

// 删除产品管理
export function delProduct(productId) {
  return request({
    url: '/biz/product/' + productId,
    method: 'delete'
  })
}
```

规范点：

- 引用路径用 `@/` 别名（已在 `vue.config.js` 配置）
- 函数名固定：`listXxx` / `getXxx` / `addXxx` / `updateXxx` / `delXxx`
- 查询用 `params`，提交用 `data`
- 单引号、**2 空格缩进**、结尾无分号（与现有文件一致）
- 每个函数前写 `//` 中文注释

### 5.3 页面 `.vue` 结构（顺序固定）

```
<template> → <script> → <style>
```

`<script>` 内顺序：

```js
export default {
  name: "Product",              // 1. 组件名（大驼峰）
  dicts: ['sys_status'],        // 2. 字典声明
  components: { Treeselect },   // 3. 局部组件
  data() { ... },               // 4. 数据
  created() { ... },            // 5. 生命周期
  methods: { ... }              // 6. 方法
}
```

### 5.4 `data()` 字段顺序（照抄脚手架）

```js
data() {
  return {
    // 遮罩层
    loading: true,
    // 显示搜索条件
    showSearch: true,
    // 产品管理表格数据
    productList: [],
    // 产品管理树选项
    productOptions: [],
    // 弹出层标题
    title: "",
    // 是否显示弹出层
    open: false,
    // 是否展开，默认全部展开
    isExpandAll: true,
    // 重新渲染表格状态
    refreshTable: true,
    // 查询参数
    queryParams: {
      productName: null,
    },
    // 表单参数
    form: {},
    // 表单校验
    rules: {
    }
  }
}
```

### 5.5 方法命名（固定套路）

| 方法 | 用途 | 注释写法 |
|------|------|---------|
| `getList()` | 加载列表 | `/** 查询产品管理列表 */` |
| `handleQuery()` | 搜索 | `/** 搜索按钮操作 */` |
| `resetQuery()` | 重置搜索 | `/** 重置按钮操作 */` |
| `handleAdd(row)` | 新增 | `/** 新增按钮操作 */` |
| `handleUpdate(row)` | 修改 | `/** 修改按钮操作 */` |
| `handleDelete(row)` | 删除 | `/** 删除按钮操作 */` |
| `submitForm()` | 提交 | `/** 提交按钮 */` |
| `cancel()` | 取消 | `// 取消按钮` |
| `reset()` | 表单重置 | `// 表单重置` |
| `getTreeselect()` | 加载树 | `/** 查询产品管理下拉树结构 */` |
| `normalizer(node)` | 树结构转换 | `/** 转换产品管理数据结构 */` |
| `toggleExpandAll()` | 展开/折叠 | `/** 展开/折叠操作 */` |

模板内事件绑定：`@click="handleQuery"`、`@keyup.enter.native="handleQuery"`。

### 5.6 权限与字典

```html
<!-- 按钮权限 -->
<el-button v-hasPermi="['biz:product:add']" @click="handleAdd">新增</el-button>

<!-- 字典回显 -->
<dict-tag :options="dict.type.sys_status" :value="scope.row.status"/>

<!-- 字典单选 -->
<el-radio-group v-model="form.status">
  <el-radio v-for="dict in dict.type.sys_status" :key="dict.value" :label="dict.value">
    {{dict.label}}
  </el-radio>
</el-radio-group>
```

### 5.7 提示与工具

- 成功：`this.$modal.msgSuccess("修改成功")`
- 错误：`this.$modal.msgError("操作失败")`
- 确认框：`this.$modal.confirm('是否确认删除？').then(...)`
- 表单重置：`this.resetForm("form")`
- 树形数据转换：`this.handleTree(response.data, "productId", "parentId")`
- 下载：`this.download('biz/product/export', {...}, \`product_${new Date().getTime()}.xlsx\`)`

### 5.8 其他前端约定

- 样式：`<style scoped>` 优先；全局样式放 `src/assets/styles/`
- 双引号用于 HTML 属性，单引号用于 JS 字符串（与现有代码一致）
- 组件名、prop 名用短横线（kebab-case）在模板中使用
- 不要直接修改 `src/views/system/**`、`src/api/system/**`

---

## 6. 数据库规范

### 6.1 命名

| 对象 | 规则 | 示例 |
|------|------|------|
| 表 | `模块_` 前缀 + 小写下划线 | `sys_product`、`sys_student`、`sys_user` |
| 字段 | 小写下划线 | `product_name`、`create_time` |
| 主键 | `<实体>_id`，`bigint` 自增 | `product_id` |
| 索引 | `idx_<表>_<字段>` | `idx_product_name` |
| 外键 | **不建物理外键**，靠应用约束 | — |

### 6.2 表必备字段（对齐 `BaseEntity`）

```sql
`create_by`   varchar(64)   DEFAULT '' COMMENT '创建者',
`create_time` datetime              COMMENT '创建时间',
`update_by`   varchar(64)   DEFAULT '' COMMENT '更新者',
`update_time` datetime              COMMENT '更新时间',
`remark`      varchar(500)  DEFAULT NULL COMMENT '备注'
```

树形表额外必备（对齐 `TreeEntity`）：

```sql
`parent_id`  bigint DEFAULT 0 COMMENT '父id',
`order_num`  int    DEFAULT 0 COMMENT '显示顺序',
```

### 6.3 状态字段

统一 `char(1)`，`0` 表示正常、`1` 表示停用（对齐 `UserConstants.NORMAL` / `EXCEPTION`），
并在字段注释里写明取值范围：

```sql
`status` char(1) DEFAULT '0' COMMENT '产品状态（0正常 1停用）'
```

### 6.4 变更管理

- **所有建表/改表 SQL 必须并入 `sql/` 目录**（当前 `sys_product` / `sys_student` 缺失，需补）
- SQL 脚本按日期命名：`ry_YYYYMMDD.sql`
- 破坏性变更（DROP / 改字段类型）必须单独评审

---

## 7. Git 与提交规范

### 7.1 提交信息

```
<type>(<scope>): <subject>
```

`type` 取值：

| type | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复缺陷 |
| `refactor` | 重构（不改变外部行为） |
| `perf` | 性能优化 |
| `docs` | 文档变更 |
| `style` | 格式调整（不影响逻辑） |
| `test` | 测试相关 |
| `chore` | 构建/依赖/工具 |

示例：

```
feat(biz/product): 新增产品树形管理模块
fix(biz/student): 修复生日字段导出格式错误
docs(architecture): 补充模块边界文档
```

### 7.2 分支

```
main        生产
develop     集成分支
feature/xxx 功能分支
hotfix/xxx  紧急修复
```

### 7.3 提交前自检

- [ ] 无 `System.out.println`（调试输出用 `logger`）
- [ ] 无注释掉的大段代码
- [ ] 无硬编码的密钥、密码、路径
- [ ] Controller 每个方法都有 `@PreAuthorize`
- [ ] 写操作都有 `@Log`
- [ ] 新增字段已同步 `resultMap` + `selectXxxVo` + 前端表格列
- [ ] 建表/改表 SQL 已并入 `sql/`
- [ ] 前端权限串与后端一致

### 7.4 .gitignore

不要提交：`target/`、`ruoyi-ui/node_modules/`、`ruoyi-ui/dist/`、`.idea/`、`*.log`

---

## 8. 代码生成器使用约定

`ruoyi-generator` 能一键产出符合本规范的全套代码，是**保证风格一致的第一选择**。

### 8.1 使用流程

1. 建表（务必写 `COMMENT`，表注释 + 字段注释都会进生成结果）
2. 系统工具 → 代码生成 → 导入表
3. 编辑生成配置：
   - **包路径**：`com.ruoyi.biz`
   - **模块名**：`biz`
   - **业务名**：`product`（决定 `/biz/product` 路径与 `biz:product:*` 权限）
   - **生成模板**：树形业务选"树表"模板，普通业务选"单表"模板
4. 预览 → 生成代码
5. **按边界规则落位**：

| 生成物 | 落位 |
|--------|------|
| `XxxController.java` | `ruoyi-admin/src/main/java/com/ruoyi/biz/controller/` |
| `Xxx.java` | `ruoyi-system/src/main/java/com/ruoyi/biz/domain/` |
| `XxxMapper.java` | `ruoyi-system/src/main/java/com/ruoyi/biz/mapper/` |
| `IXxxService.java` | `ruoyi-system/src/main/java/com/ruoyi/biz/service/` |
| `XxxServiceImpl.java` | `ruoyi-system/src/main/java/com/ruoyi/biz/service/impl/` |
| `XxxMapper.xml` | `ruoyi-system/src/main/resources/mapper/biz/` |
| `xxx.js` | `ruoyi-ui/src/api/biz/` |
| `index.vue` | `ruoyi-ui/src/views/biz/xxx/` |
| `.sql`（菜单） | 入库后并入 `sql/` |

### 8.2 生成后必改项

- [ ] 类头 `@author` 改为实际作者（生成器默认是 `ruoyi`）
- [ ] 补充业务校验逻辑（脚手架只有空壳）
- [ ] 前端 `rules` 补充必填/长度校验（脚手架生成的是空 `{}`）
- [ ] 确认 `resultMap` 覆盖了全部字段
