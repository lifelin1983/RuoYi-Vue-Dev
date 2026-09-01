# 当前迭代计划

> 迭代基线：2026-08-30 ｜ 项目版本：`3.9.2` ｜ 仓库：RuoYi-Vue
> 相关文档：[架构总览](../architecture/overview.md) ｜ [测试规范](../conventions/testing.md) ｜ [错误码](../reference/error-codes.md)

---

## 1. 迭代背景

`com.ruoyi.biz` 业务包于 **2026-08-07** 落地（代码生成器产出，作者 `life`），
已包含两个完整 CRUD 模块：**产品管理（树形）** 与 **学生管理（平表）**。

但落地时遗留了一批"能跑但不可维护"的问题：

| 问题 | 影响面 | 严重度 |
|------|--------|--------|
| 全仓库零自动化测试 | 回归全靠手点 | 🔴 高 |
| `sys_product` / `sys_student` 建表 SQL 未入库 | 新环境无法初始化 | 🔴 高 |
| 产品列表无分页 | 数据膨胀后接口劣化 | 🟡 中 |
| 树形删除未校验子节点 | 产生孤儿数据 | 🟡 中 |
| 无错误处理规范 | 客户端只显示"系统未知错误" | 🟡 中 |
| token 密钥 / Redis 密码为默认值 | 生产安全隐患 | 🟡 中 |

**本迭代目标**：把 `biz` 模块从"能跑"推进到"可交付、可维护"，并补齐工程化底座。

---

## 2. 迭代目标（Sprint Goal）

> **让 `com.ruoyi.biz` 达到可交付标准：SQL 可复现、逻辑有校验、改动有测试兜底。**

---

## 3. 任务清单

### P0 — 必须完成

| # | 任务 | 交付物 | 依赖 | 状态 |
|---|------|--------|------|------|
| P0-1 | **补齐业务表 SQL** | `sql/ry_2026mmdd_biz.sql`（`sys_product` + `sys_student` 建表 + 初始数据） | — | ⬜ 待开始 |
| P0-2 | **补菜单与权限 SQL** | 同上文件内追加 `sys_menu` 插入语句（`biz:product:*` / `biz:student:*`） | P0-1 | ⬜ 待开始 |
| P0-3 | **接入测试依赖** | 父 POM 增加 `spring-boot-starter-test` + ArchUnit `1.3.0`，并锁定 Surefire `2.22.2` | — | ✅ 已完成 |
| P0-7 | **架构约束结构化测试** | `ArchitectureRulesTest` + `MapperXmlRulesTest` + `.harness/enforcement.yml` | P0-3 | ✅ 已完成（15 用例全绿） |
| P0-4 | **补 Service 单测** | `SysProductServiceImplTest`(10)、`SysStudentServiceImplTest`(8) | P0-3 | ✅ 已完成 |
| P0-5 | **补 Mapper 测试** | `SysProductMapperTest`(11)、`SysStudentMapperTest`(12)，H2 覆盖全部 SQL | P0-3 | ✅ 已完成 |

### P1 — 应当完成

| # | 任务 | 交付物 | 依赖 | 状态 |
|---|------|--------|------|------|
| P1-1 | **产品列表加分页/懒加载** | `SysProductController.list()` 改造 | — | ⬜ 待开始 |
| P1-2 | **树形删除校验子节点** | `SysProductServiceImpl` 删除前检查 `parent_id` | — | ⬜ 待开始 |
| P1-3 | **统一错误码** | 后端新增业务错误码常量（统一返回可读 msg） | — | ⬜ 待开始 |
| P1-4 | **业务异常规范化** | Service 校验改用 `throw new ServiceException(...)` | P1-3 | ⬜ 待开始 |
| P1-5 | **补 Controller 接口测试** | `/biz/**` 主流程 + 鉴权用例 | P0-3 | ⬜ 待开始 |
| P1-6 | **编写 API 文档** | `docs/reference/api-spec.yaml` | — | ✅ 已完成 |

### P2 — 有余力再做

| # | 任务 | 交付物 | 状态 |
|---|------|--------|------|
| P2-1 | 生产配置加固 | token 密钥、Redis 密码外置到环境变量；`swagger.enabled=false` | ⬜ 待开始 |
| P2-2 | 接入 CI | `.github/workflows/ci.yml` 跑 `mvn test` | ⬜ 待开始 |
| P2-3 | 开启驼峰映射 | `mapUnderscoreToCamelCase=true`，逐步去掉冗余 `resultMap` | ⬜ 待开始 |
| P2-5 | 操作日志接入业务模块 | 确认 `biz` 的 `@Log` 已正确落库 | ⬜ 待开始 |

---

## 4. 任务详情

### P0-1 / P0-2 补齐业务表与菜单 SQL

**问题**：`sql/ry_20260417.sql` 中**没有** `sys_product` / `sys_student`，新环境初始化后业务模块直接报错。

**产出文件**：`sql/ry_20260830_biz.sql`

**表结构（依据现有实体与 Mapper XML 反推）**：

```sql
-- ----------------------------
-- 产品管理（树形）
-- ----------------------------
DROP TABLE IF EXISTS `sys_product`;
CREATE TABLE `sys_product`
(
    `product_id`   bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '产品id',
    `parent_id`    bigint(20)   DEFAULT 0                COMMENT '父产品id',
    `product_name` varchar(30)  DEFAULT ''               COMMENT '产品名称',
    `order_num`    int(4)       DEFAULT 0                COMMENT '显示顺序',
    `status`       char(1)      DEFAULT '0'              COMMENT '产品状态（0正常 1停用）',
    `create_by`    varchar(64)  DEFAULT ''               COMMENT '创建者',
    `create_time`  datetime                              COMMENT '创建时间',
    `update_by`    varchar(64)  DEFAULT ''               COMMENT '更新者',
    `update_time`  datetime                              COMMENT '更新时间',
    `remark`       varchar(500) DEFAULT NULL             COMMENT '备注',
    PRIMARY KEY (`product_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 COMMENT = '产品管理';

-- ----------------------------
-- 学生管理（平表）
-- ----------------------------
DROP TABLE IF EXISTS `sys_student`;
CREATE TABLE `sys_student`
(
    `student_id`     bigint(20)  NOT NULL AUTO_INCREMENT COMMENT '编号',
    `student_name`   varchar(30) DEFAULT ''              COMMENT '学生名称',
    `student_age`    int(3)      DEFAULT NULL            COMMENT '年龄',
    `student_hobby`  char(1)     DEFAULT NULL            COMMENT '爱好（0代码 1音乐 2电影）',
    `student_sex`    char(1)     DEFAULT NULL            COMMENT '性别（0男 1女 2未知）',
    `student_status` char(1)     DEFAULT '0'             COMMENT '状态（0正常 1停用）',
    `student_birthday` datetime  DEFAULT NULL            COMMENT '生日',
    `create_by`      varchar(64) DEFAULT ''              COMMENT '创建者',
    `create_time`    datetime                            COMMENT '创建时间',
    `update_by`      varchar(64) DEFAULT ''              COMMENT '更新者',
    `update_time`    datetime                            COMMENT '更新时间',
    `remark`         varchar(500) DEFAULT NULL           COMMENT '备注',
    PRIMARY KEY (`student_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 COMMENT = '学生管理';
```

> ⚠️ **动手前必须核对**：`student_birthday` 字段名来自 `SysStudent` 实体，需与
> `SysStudentMapper.xml` 的 `resultMap` 逐列比对，以 **XML 实际列名**为准。

**菜单 SQL（示例，`parent_id` 需按实际目录调整）**：

```sql
-- 业务管理目录
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES ('业务管理', 0, 5, '/biz', NULL, 1, 0, 'M', '0', '0', '', 'build', 'admin', sysdate(), '业务管理目录');

-- 产品管理菜单（parent_id 取上一步生成的 menu_id）
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES ('产品管理', (SELECT menu_id FROM (SELECT menu_id FROM sys_menu WHERE menu_name='业务管理' AND parent_id=0) t), 1, 'product', 'biz/product/index', 1, 0, 'C', '0', '0', 'biz:product:list', 'tree-table', 'admin', sysdate(), '产品管理菜单');

-- 按钮权限：查询/新增/修改/删除/导出
INSERT INTO `sys_menu` (`menu_name`, `parent_id`, `order_num`, `path`, `component`, `is_frame`, `is_cache`, `menu_type`, `visible`, `status`, `perms`, `icon`, `create_by`, `create_time`, `remark`)
VALUES
('产品查询', @productMenuId, 1, '', NULL, 1, 0, 'F', '0', '0', 'biz:product:query', '#', 'admin', sysdate(), ''),
('产品新增', @productMenuId, 2, '', NULL, 1, 0, 'F', '0', '0', 'biz:product:add',    '#', 'admin', sysdate(), ''),
('产品修改', @productMenuId, 3, '', NULL, 1, 0, 'F', '0', '0', 'biz:product:edit',   '#', 'admin', sysdate(), ''),
('产品删除', @productMenuId, 4, '', NULL, 1, 0, 'F', '0', '0', 'biz:product:remove', '#', 'admin', sysdate(), ''),
('产品导出', @productMenuId, 5, '', NULL, 1, 0, 'F', '0', '0', 'biz:product:export', '#', 'admin', sysdate(), '');

-- 学生管理同理（biz:student:*）
```

> 权限串必须与 `SysProductController` 里的 `@PreAuthorize("@ss.hasPermi('biz:product:xxx')")`
> 以及 `v-hasPermi="['biz:product:xxx']"` **完全一致**。

**验收标准**：

- [ ] 全新 MySQL 执行 `quartz.sql` → `ry_20260417.sql` → `ry_20260830_biz.sql` 后系统可正常启动
- [ ] 用 `admin` 登录能看到"业务管理"菜单，产品/学生页面可正常增删改查
- [ ] 非授权账号看不到菜单，直接调接口返回 403

---

### P0-3 接入测试依赖

在根 `pom.xml` 增加（详见 [测试规范 §3](../conventions/testing.md#3-依赖接入第一步)）：

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**验收**：`mvn -q test-compile` 无报错。

---

### P0-3 / P0-7 接入测试依赖与架构约束测试（✅ 已完成）

已落地内容：

| 改动 | 说明 |
|------|------|
| 父 POM `<dependencies>` | 新增 `spring-boot-starter-test` + `archunit-junit5:1.3.0`（均 test scope） |
| 父 POM `<build><plugins>` | 锁定 `maven-surefire-plugin:2.22.2` |
| `ArchitectureRulesTest` | 11 条架构规则 + 1 条自检 |
| `MapperXmlRulesTest` | 3 条 Mapper XML 规则 |
| `.harness/enforcement.yml` | 机器可读的规则声明 |

**两个踩坑记录**

1. **Surefire 必须显式锁定版本**。本项目父 POM 不是 `spring-boot-starter-parent`，
   Maven 默认用 Surefire `2.12.4`，**不识别 JUnit 5**，会导致 `mvn test` 静默跑 0 个用例。
2. **规则作用域必须收窄到 `com.ruoyi.biz`**。首版规则全量应用时，
   `ruoyi-generator` 的 `GenController`、`ruoyi-framework` 的 `UserDetailsServiceImpl` 等
   框架原生代码触发告警。这些代码我们不改，因此改为只约束自有业务包。

**反向验证**：临时注入一个无 `@PreAuthorize` 的 Controller，规则成功拦截并精确定位到
`TempUnsecuredController.leak()` 第 21 行，错误含完整修复指引。验证后已清理。

### P0-4 / P0-5 补测试（✅ 已完成）

按 [测试规范 §12.3](../conventions/testing.md#123-存量补齐顺序) 的批次推进，第 2、3 批已完成。

**交付**

| 文件 | 用例 | 覆盖内容 |
|------|------|---------|
| `ruoyi-system/src/test/.../biz/service/impl/SysProductServiceImplTest` | 10 | 查询/新增/修改/删除的返回值语义与调用契约 |
| `.../SysStudentServiceImplTest` | 8 | 同上 |
| `ruoyi-system/src/test/.../biz/mapper/SysProductMapperTest` | 11 | 全部 6 条 SQL + 动态列 trim + 主键回填 |
| `.../SysStudentMapperTest` | 12 | 全部 6 条 SQL + 4 个动态条件分支（含 params 年龄区间） |
| `ruoyi-system/src/test/resources/sql/schema.sql` / `data.sql` | — | H2 测试库表与数据 |
| `ruoyi-system/src/test/resources/application-test.yml` | — | 切片测试配置 |
| `ruoyi-system/src/test/java/com/ruoyi/TestMybatisApplication` | — | 供 `@MybatisTest` 查找的测试启动配置 |

**验收**：

- [x] 每个 `Mapper.xml` 中的 SQL 语句至少被执行一次（12 条全部覆盖）
- [x] `mvn clean test` 全绿（全仓库 56 个用例）
- [ ] `com.ruoyi.biz.service.impl` 行覆盖率 ≥ 70% ——**待 JaCoCo 接入后量化**，
      当前 Service 是透传层，方法级覆盖已达 100%

**两个用 `@Disabled` 固化的已知缺陷**（修复后移除注解即转为通过）

| 用例 | 缺陷 |
|------|------|
| `SysProductServiceImplTest.deleteSysProductByProductIds_hasChildren_...` | P1-2：删父节点未校验子节点 → 孤儿数据 |
| `SysProductMapperTest.selectSysProductList_byParentId_shouldFilter` | P1-2 前置：XML 的 `where` 不支持 `parent_id` |

**踩坑记录**

1. `@MybatisTest` 在 `ruoyi-system` 报 `Unable to find a @SpringBootConfiguration`
   —— `RuoYiApplication` 在 `ruoyi-admin` 模块，本模块测试 classpath 上没有。
   解决：新增 `TestMybatisApplication`（用 `@SpringBootConfiguration` 而非 `@SpringBootApplication`）。
2. Mapper 接口无 `@Mapper` 注解，切片测试不会自动扫到 —— 在配置类上补 `@MapperScan`。
3. `ruoyi-system` 编译期没有 MyBatis 依赖，需显式加 `mybatis-spring-boot-starter-test:2.3.1`。

详见 [测试规范 §6.2](../conventions/testing.md#62-在本项目跑通-mybatistest-的两个前提实测踩坑)。

---

### P1-1 产品列表分页

**现状**：`SysProductController.list()` 直接 `return success(list)`，无分页。

**两个方案**：

| 方案 | 做法 | 优点 | 缺点 |
|------|------|------|------|
| **A. 树形懒加载（推荐）** | `list` 改为按 `parentId` 查一层（树形懒加载） | 数据量大时性能好 | 改造量中等 |
| B. 加分页 | `startPage()` + `getDataTable()` | 改动最小 | 树形结构被分页截断，体验差 |

**推荐 A**，与 `TreeEntity` 的树形语义匹配。若产品数量确定在百级以内，可暂用 B 过渡。

---

### P1-2 树形删除校验子节点

**现状**：`deleteSysProductByProductIds` 直接 `delete ... where product_id in (...)`，
删除父节点后其子节点变成孤儿数据（树形视图中再也找不到）。

**改法**：在 `SysProductServiceImpl.deleteSysProductByProductIds` 中先校验：

```java
@Override
public int deleteSysProductByProductIds(Long[] productIds)
{
    for (Long productId : productIds)
    {
        SysProduct child = new SysProduct();
        child.setParentId(productId);
        if (StringUtils.isNotEmpty(sysProductMapper.selectSysProductList(child)))
        {
            throw new ServiceException("存在下级产品，不允许删除");
        }
    }
    return sysProductMapper.deleteSysProductByProductIds(productIds);
}
```

> 注意要先在 `SysProductMapper.xml` 的 `<where>` 里补 `parent_id` 条件（当前只有 `product_name`）。

---

### P1-3 / P1-4 统一错误码与异常

1. 在 `ruoyi-common/src/main/java/com/ruoyi/common/constant/` 新增业务错误码常量
2. Service 层校验统一 `throw new ServiceException(...)`

详见 [错误码参考](../reference/error-codes.md)。

---

## 5. 排期建议

| 周次 | 重点 |
|------|------|
| W1 | P0-1 / P0-2 补齐 SQL + 菜单；P0-3 接入测试依赖 |
| W2 | P0-4 / P0-5 补 Service + Mapper 测试 |
| W3 | P1-1 产品分页；P1-2 树删除校验；P1-3/P1-4 错误码 |
| W4 | P1-5 接口测试；P2 有余力再推进 |

---

## 6. 风险与阻塞

| 风险 | 影响 | 应对 |
|------|------|------|
| `sys_student` 字段与 XML 不一致 | SQL 建错，启动报错 | 建表前逐列比对 `SysStudentMapper.xml` |
| H2 与 MySQL 方言差异 | Mapper 测试跑不通 | 个别用例改用 Testcontainers 或跳过 |
| Redis 不可用 | Controller 测试无法鉴权 | CI 起 Redis service；本地用 `database: 5` 隔离 |
| 补测试与业务需求抢时间 | 迭代延期 | P0 优先，P1 顺延，P2 直接砍 |
| 生产密钥未替换 | 安全事故 | 即便本迭代不做，也要在部署前单独处理 |

---

## 7. 本迭代不做的事

- ❌ 不引入 Lombok（与现有手写 getter/setter 风格冲突）
- ❌ 不做 `com.ruoyi.biz` 独立成 Maven 模块（条件已具备，但当前收益不大）

---

## 8. 变更记录

| 日期 | 变更 |
|------|------|
| 2026-08-30 | 建立迭代计划，梳理 P0/P1/P2 任务；完成 API 文档（P1-6） |
| 2026-08-30 | 完成 P0-3 测试依赖接入 + P0-7 架构约束测试（15 用例） |
| 2026-08-31 | 完成 P0-4 Service 单测（18）+ P0-5 Mapper 测试（23），全仓库 56 用例全绿 |
| 2026-08-31 | 落地 P0-2 文档新鲜度门禁 `scripts/check-doc-links.sh`（6 类检查，`--strict` 模式）；修正 4 处"零测试"过期表述（CLAUDE.md 已知欠账、testing.md 开头与附录、overview.md 待改进表），strict 校验全绿 |
