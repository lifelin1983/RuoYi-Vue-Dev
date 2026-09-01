# 系统架构总览

> 适用版本：RuoYi-Vue `3.9.2` ｜ 文档基线日期：2026-08-30
> 相关文档：[模块边界与依赖规则](./boundaries.md) ｜ [编码规范](../conventions/README.md)

---

## 1. 整体形态

前后端分离的后台管理系统。后端以 Spring Boot 内嵌 Tomcat 单体形态部署，前端为 Vue2 SPA 静态资源，
两者通过 HTTP + JWT 通信，开发期由 webpack devServer 代理转发。

```
┌──────────────────────────────────────────────────────────────┐
│  浏览器 (SPA)                                                 │
│  Vue 2.6.12 + Element UI 2.15.14 + Vuex + Vue Router          │
│  dev: :80            prod: Nginx 托管 dist/                   │
└───────────────────────────┬──────────────────────────────────┘
                            │  HTTP / JSON
                            │  Authorization: Bearer <jwt>
                            │  （开发期 devServer proxy → :8080）
┌───────────────────────────▼──────────────────────────────────┐
│  Spring Boot 2.5.15  (:8080, context-path /)                  │
│                                                               │
│  ┌───────────── 过滤器 / 拦截器链 ─────────────┐               │
│  │ XssFilter → JwtAuthenticationTokenFilter    │               │
│  │ → RepeatSubmitInterceptor                   │               │
│  └────────────────────┬───────────────────────┘               │
│  ┌────────────────────▼───────────────────────┐               │
│  │ Spring Security 鉴权 + @PreAuthorize(@ss)   │               │
│  └────────────────────┬───────────────────────┘               │
│  ┌────────────────────▼───────────────────────┐               │
│  │ AOP 切面：Log / DataScope / DataSource /    │               │
│  │          RateLimiter                        │               │
│  └────────────────────┬───────────────────────┘               │
│  ┌────────────────────▼───────────────────────┐               │
│  │ Controller  →  Service  →  Mapper(MyBatis)  │               │
│  └────────────────────┬───────────────────────┘               │
│  ┌────────────────────▼───────────────────────┐               │
│  │ 全局异常处理 GlobalExceptionHandler          │               │
│  └────────────────────────────────────────────┘               │
└──────────┬─────────────────────────────┬─────────────────────┘
           │                             │
   ┌───────▼────────┐           ┌────────▼─────────┐
   │  MySQL (Druid) │           │  Redis           │
   │  业务库 + 定时任务库 │        │  会话 / 缓存 /   │
   │                │           │  字典 / 限流计数  │
   └────────────────┘           └──────────────────┘
```

---

## 2. 技术栈矩阵

### 2.1 后端

| 领域 | 选型 | 版本 | 备注 |
|------|------|------|------|
| 基础框架 | Spring Boot | `2.5.15` | 父 POM 用 `spring-boot-dependencies` BOM 仲裁 |
| Spring | Spring Framework | `5.3.39` | 显式覆盖（`spring-framework-bom`） |
| 安全 | Spring Security | `5.7.14` | 显式覆盖（`spring-security-bom`） |
| JDK | Java | `1.8` | `maven-compiler-plugin` 3.1，UTF-8 |
| 构建 | Maven 多模块 | — | 阿里云镜像仓库 |
| ORM | MyBatis | 随 Boot | XML 在 `resources/mapper/**` |
| 连接池 | Druid | `1.2.28` | 支持动态数据源切换 |
| 分页 | PageHelper | `1.4.7` | `helperDialect: mysql` |
| 缓存/会话 | Spring Data Redis (Lettuce) | 随 Boot | 序列化器 `FastJson2JsonRedisSerializer` |
| Token | JJWT | `0.9.1` | `token.header=Authorization`、`expireTime=30`（分钟） |
| 接口文档 | SpringFox (Swagger3) | `3.0.0` | 排除原生 `swagger-models` |
| JSON | fastjson2 | `2.0.62` | |
| Excel | Apache POI | `4.1.2` | 由 `ExcelUtil` 封装 |
| 模板 | Velocity | `2.3` | 代码生成 |
| 验证码 | Kaptcha | `2.3.3` | `captchaType: math`（默认算术） |
| 系统监控 | OSHI | `7.3.0` | 服务器监控页数据来源 |
| UA 解析 | Yauaa | `7.32.0` | 登录日志设备解析 |
| 内嵌容器 | Tomcat | `9.0.112` | 显式覆盖 |
| 日志 | Logback | `1.2.13` | 显式覆盖 |

### 2.2 前端

| 领域 | 选型 | 版本 |
|------|------|------|
| 框架 | Vue | `2.6.12` |
| UI | Element UI | `2.15.14` |
| 路由 | vue-router | `3.4.9` |
| 状态 | Vuex | `3.6.0` |
| 请求 | axios | `0.30.3` |
| 构建 | @vue/cli-service | `4.4.6` |
| 图表 | ECharts | `5.4.0` |
| 树选择 | @riophae/vue-treeselect | `0.4.0` |
| 富文本 | quill | `2.0.2` |
| 其他 | js-cookie / jsencrypt / nprogress / file-saver / vuedraggable | 见 `package.json` |

> **注意**：项目是 **Vue2 Options API**，不是 Vue3 / Composition API。不要用 `<script setup>`。

---

## 3. 分层与调用链

### 3.1 标准四层

```
Controller（ruoyi-admin）
    │  入参校验、鉴权注解、操作日志注解、响应封装
    │  extends BaseController
    ▼
Service 接口 I Xxx Service（ruoyi-system）
    │  业务规则、事务边界、数据权限
    ▼
Service 实现 XxxServiceImpl（ruoyi-system）  ← @Service
    │
    ▼
Mapper 接口 XxxMapper（ruoyi-system）  ← @Mapper（由 MyBatisConfig 扫描）
    │
    ▼
Mapper XML（ruoyi-system/src/main/resources/mapper/**）  ← SQL 落点
```

### 3.2 一次分页查询的完整链路

以 `GET /biz/student/list?pageNum=1&pageSize=10` 为例：

```
① 前端  src/api/biz/student.js  listStudent(query)
     └─ request({ url:'/biz/student/list', method:'get', params })
② utils/request.js  请求拦截器
     ├─ 注入 Authorization: Bearer <token>
     ├─ GET 参数 tansParams 拼到 URL
     └─ POST/PUT 做 1s 内防重复提交校验（sessionStorage）
③ devServer proxy → http://localhost:8080
④ XssFilter → JwtAuthenticationTokenFilter
     └─ 解析 token → LoginUser → 存入 AuthenticationContextHolder
     └─ 刷新 token 有效期（Redis）
⑤ RepeatSubmitInterceptor（仅 POST/PUT 且未显式关闭时）
⑥ SecurityConfig 授权决策；方法级 @PreAuthorize("@ss.hasPermi('biz:student:list')")
     └─ PermissionService.hasPermi → SysPermissionService 查权限集合
⑦ SysStudentController.list()
     ├─ startPage()   ← PageHelper.startPage，读取 pageNum/pageSize/orderBy
     ├─ sysStudentService.selectSysStudentList(sysStudent)
     │     └─ SysStudentMapper.selectSysStudentList → XML SQL
     └─ getDataTable(list) → TableDataInfo{total, rows, code, msg}
⑧ 响应拦截器：code===200 → 直接返回 res.data
⑨ 前端把 rows 灌进 el-table，total 灌进 pagination 组件
```

### 3.3 横切能力

| 能力 | 实现位置 | 触发方式 |
|------|---------|---------|
| 操作日志 | `framework/aspectj/LogAspect.java` | `@Log(title=, businessType=)`；异步落库 `AsyncManager` + `AsyncFactory` |
| 数据权限 | `framework/aspectj/DataScopeAspect.java` | `@DataScope(deptAlias=, userAlias=)` 拼 SQL 片段 |
| 多数据源 | `framework/aspectj/DataSourceAspect.java` + `DynamicDataSource` | `@DataSource(DataSourceType.MASTER/SLAVE)` |
| 接口限流 | `framework/aspectj/RateLimiterAspect.java` | `@RateLimiter(count=, time=)`，Redis 计数 |
| 防重复提交 | `framework/interceptor/RepeatSubmitInterceptor.java` | 自动（POST/PUT） |
| XSS 过滤 | `common/filter` + `common/xss` | `xss.enabled=true`，`urlPatterns=/system/*,/monitor/*,/tool/*` |
| 全局异常 | `framework/web/exception/GlobalExceptionHandler.java` | `@RestControllerAdvice` |
| 国际化 | `framework/config/I18nConfig.java` + `i18n/messages.properties` | `spring.messages.basename=i18n/messages` |
| 异步任务 | `framework/manager/AsyncManager.java` | 延时任务线程池，用于日志落库等 |

---

## 4. 关键运行时配置

来源：`ruoyi-admin/src/main/resources/application.yml`

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.port` | `8080` | |
| `server.servlet.context-path` | `/` | |
| `spring.profiles.active` | `druid` | 数据源走 `application-druid.yml` |
| `spring.redis` | `localhost:6379/db0`，无密码 | 未配置密码，生产必须加 |
| `mybatis.typeAliasesPackage` | `com.ruoyi.**.domain` | 实体必须在 `**.domain` 包下 |
| `mybatis.mapperLocations` | `classpath*:mapper/**/*Mapper.xml` | 文件名必须以 `Mapper.xml` 结尾 |
| `mybatis.configLocation` | `classpath:mybatis/mybatis-config.xml` | |
| `mybatis.configuration.mapUnderscoreToCamelCase` | **未开启**（已注释） | 依赖 `resultMap` 手工映射 |
| `pagehelper.helperDialect` | `mysql` | |
| `token.header` / `secret` / `expireTime` | `Authorization` / `abcdefghijklmnopqrstuvwxyz` / `30` | 密钥为默认值，生产必须替换 |
| `ruoyi.profile` | `D:/ruoyi/uploadPath` | 上传根目录（Windows 路径，部署需改） |
| `ruoyi.captchaType` | `math` | 可选 `char` |
| `ruoyi.addressEnabled` | `false` | IP 归属地查询开关 |
| `swagger.enabled` / `pathMapping` | `true` / `/dev-api` | 生产建议关闭 |
| `xss.enabled` / `excludes` | `true` / `/system/notice` | |
| `referer.enabled` | `false` | 防盗链默认关闭 |
| `user.password.maxRetryCount` / `lockTime` | `5` / `10` | 密码错误锁定策略 |
| 文件上传限制 | 单文件 `10MB`，总请求 `20MB` | |

### 4.1 配置文件位置

| 配置 | 路径 |
|------|------|
| 主配置 | `ruoyi-admin/src/main/resources/application.yml` |
| 数据源 | `ruoyi-admin/src/main/resources/application-druid.yml` |
| 日志 | `ruoyi-admin/src/main/resources/logback.xml` |
| MyBatis 全局配置 | `ruoyi-admin/src/main/resources/mybatis/mybatis-config.xml` |
| 国际化 | `ruoyi-admin/src/main/resources/i18n/messages.properties` |
| 前端环境 | `ruoyi-ui/.env.*` → `VUE_APP_BASE_API` |
| 前端构建 | `ruoyi-ui/vue.config.js` |

---

## 5. 数据层

### 5.1 库表组织

| 脚本 | 内容 |
|------|------|
| `sql/ry_20260417.sql` | 主库：系统管理（`sys_*`）、定时任务以外的基础表 |
| `sql/quartz.sql` | Quartz 调度相关表 |

**业务表现状**

| 表 | 对应实体 | 对应 XML | 是否在 SQL 脚本中 |
|----|---------|---------|------------------|
| `sys_product` | `SysProduct`（`TreeEntity`） | `mapper/biz/SysProductMapper.xml` | ❌ 未纳入 |
| `sys_student` | `SysStudent`（`BaseEntity`） | `mapper/biz/SysStudentMapper.xml` | ❌ 未纳入 |

> 新环境初始化时需手工补建，否则业务模块启动即报错。

### 5.2 MyBatis 约定

- `resultMap` 命名固定为 `<Xxx>Result`，查询列片段固定为 `<sql id="selectXxxVo">`
- 列表查询用 `<where>` + `<if>` 动态条件；字符串条件判空写成 `!= null and != ''`
- 新增用 `<trim prefix="(" suffix=")" suffixOverrides=",">`，配合 `useGeneratedKeys="true" keyProperty="主键"`
- 修改用 `<trim prefix="SET" suffixOverrides=",">`
- 批量删除用 `<foreach collection="array" ...>`（`parameterType="String"` 是脚手架习惯写法）

### 5.3 动态数据源

`DynamicDataSource` + `DynamicDataSourceContextHolder` 支持主从切换，默认走 Bean 上的 `@DataSource`，
未标注时走默认数据源。当前配置仅启用主库。

---

## 6. 前端架构

```
ruoyi-ui/src/
├── api/            按模块划分：biz / monitor / system / tool
├── assets/         样式、图标（svg-sprite）、图片
├── components/     全局组件（Pagination / RightToolbar / DictTag / FileUpload / ImageUpload …）
├── directive/      自定义指令：v-hasPermi / v-hasRole / dialog 拖拽
├── layout/         整体布局（Sidebar / Navbar / TagsView / AppMain）
├── plugins/        cache（session/local 缓存）、modal、download、tab 等
├── router/         静态路由 + 动态路由（后端菜单驱动）
├── store/          Vuex：modules（app/settings/user/permission/dict）+ getters
├── utils/          request / auth / errorCode / ruoyi / dict / generator
└── views/          页面：biz / dashboard / monitor / system / tool
```

### 6.1 请求与响应契约

- 统一出口：`utils/request.js`（axios 实例）
- 成功判定：`res.data.code === 200` → 直接返回 `res.data`
- `code === 401` → 弹窗提示重新登录（全局只弹一次，`isRelogin.show` 去重）
- `code === 500` → `Message.error(msg)` 并 reject
- `code === 601` → `Message.warning(msg)` 并 reject
- 其他非 200 → `Notification.error`
- 文件下载走 `download()`（`responseType: blob` + `file-saver`）

### 6.2 权限模型

| 层 | 机制 |
|----|------|
| 菜单路由 | 后端 `sys_menu` 动态下发 → 前端 `router` 拼接 |
| 按钮 | `v-hasPermi="['biz:product:add']"` / `v-hasRole` |
| 接口 | `@PreAuthorize("@ss.hasPermi('biz:product:add')")` |
| 数据 | `@DataScope` 拼 SQL 片段 |

两侧权限字符串必须完全一致，前端漏配只是按钮不显示，后端漏配则是**真实越权漏洞**。

### 6.3 字典机制

`dicts: ['sys_status']` 声明 → `store/modules/dict.js` 加载 → 模板里 `<dict-tag :options="dict.type.sys_status" :value="scope.row.status"/>`。

---

## 7. 部署形态

| 环境 | 后端 | 前端 |
|------|------|------|
| 开发 | `mvn spring-boot:run`（:8080） | `npm run dev`（:80，代理转发） |
| 预发 | `npm run build:stage` → `dist/` | `mvn package` → `ruoyi-admin/target/*.jar` |
| 生产 | `java -jar` 运行 jar；`swagger.enabled` 建议置 `false` | `dist/` 交由 Nginx 托管，`VUE_APP_BASE_API` 指向后端域名 |

`vue.config.js` 已开启 gzip 预压缩（`CompressionPlugin`）与 chunk 拆分，Nginx 需配合开启 `gzip_static`。

---

## 8. 架构现状评估

**优势**

- 分层清晰，`common` 无业务污染，模块边界明确
- 代码生成器能一键产出符合规范的 CRUD 全套代码，一致性高
- 横切能力（日志/权限/限流/XSS/防重）齐全且可插拔

**待改进**

| 问题 | 影响 |
|------|------|
| CI 质量门禁未接入 | 架构约束仅本地 `mvn test` 生效，无法阻止违规代码合入 |
| Controller 接口测试与前端测试缺失 | 越权回归与前端纯函数无自动化兜底 |
| 业务表 SQL 未纳入版本管理 | 新环境无法一键初始化 |
| `SysProductController.list()` 无分页 | 树形数据膨胀后接口性能劣化 |
| `mapUnderscoreToCamelCase` 未开启 | 每加字段都要改两处（实体 + resultMap），易漏 |
| token 密钥、Redis 密码为默认值 | 生产部署前必须替换 |
| 树形删除未校验子节点 | 可能产生孤儿数据 |

**高风险改动区域**

改动以下位置前必须确认影响面，否则容易引发全站故障或大范围回归。

| 区域 | 风险 |
|------|------|
| `framework/config/SecurityConfig.java` | 改错直接全站 401/403 |
| `framework/security/filter/JwtAuthenticationTokenFilter.java` | 影响全量鉴权链路 |
| `framework/interceptor/RepeatSubmitInterceptor.java` | 前端已有防重提交逻辑，双端易冲突 |
| `ruoyi-generator/resources/vm/**` | 改模板会影响后续所有生成代码 |
| `ruoyi-common/**` 下的工具类 | 影响面覆盖全部模块 |
| 树形实体删除逻辑 | 当前未校验子节点，误删产生孤儿数据 |
