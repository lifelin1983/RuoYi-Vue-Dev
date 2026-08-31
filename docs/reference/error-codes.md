# 错误码参考

> 涵盖：HTTP/业务状态码、异常类体系、前端错误码映射、业务错误码规划。
> 相关文档：[API 规范](./api-spec.yaml) ｜ [编码规范](../conventions/README.md)

---

## 1. 状态码体系

本项目**没有独立的业务错误码段**，统一复用 HTTP 语义状态码。
常量定义在 `ruoyi-common/src/main/java/com/ruoyi/common/constant/HttpStatus.java`，
响应体为 `AjaxResult`（`{code, msg, data}`）或 `TableDataInfo`（`{total, rows, code, msg}`）。

### 1.1 完整对照表

来源：`HttpStatus.java`

| code | 常量名 | 含义 | 前端行为 |
|------|--------|------|---------|
| `200` | `SUCCESS` | 操作成功 | 直接返回 `res.data` |
| `201` | `CREATED` | 对象创建成功 | 非 200，走 `Notification.error` |
| `202` | `ACCEPTED` | 请求已接受（异步处理中） | 同上 |
| `204` | `NO_CONTENT` | 执行成功但无返回数据 | 同上 |
| `301` | `MOVED_PERM` | 资源已移除 | 同上 |
| `303` | `SEE_OTHER` | 重定向 | 同上 |
| `304` | `NOT_MODIFIED` | 资源未修改 | 同上 |
| `400` | `BAD_REQUEST` | 参数错误（缺失 / 格式不匹配） | 同上 |
| `401` | `UNAUTHORIZED` | 未认证，会话过期 | **弹窗提示重新登录**（全局只弹一次） |
| `403` | `FORBIDDEN` | 无权限，授权过期 | `Notification.error` |
| `404` | `NOT_FOUND` | 资源 / 服务未找到 | 同上 |
| `405` | `BAD_METHOD` | HTTP 方法不允许 | 同上 |
| `409` | `CONFLICT` | 资源冲突（如唯一约束冲突） | 同上 |
| `415` | `UNSUPPORTED_TYPE` | 不支持的媒体类型 | 同上 |
| `500` | `ERROR` | 系统内部错误 / 业务校验失败 | `Message.error(msg)` 并 reject |
| `501` | `NOT_IMPLEMENTED` | 接口未实现 | `Notification.error` |
| `601` | `WARN` | 系统警告（**业务警告专用**） | `Message.warning(msg)` 并 reject |

### 1.2 三个关键码

| code | 用在哪 | 约定 |
|------|--------|------|
| **200** | 所有成功的响应 | `AjaxResult.success()` 默认码 |
| **500** | 业务校验失败、系统异常 | `AjaxResult.error()` 默认码；`ServiceException` 未指定 code 时用它 |
| **601** | 业务**警告**（操作部分成功 / 需用户注意） | `AjaxResult.warn(msg)`；前端黄色提示 |

**使用建议**：

```java
// 成功
return success(data);
return toAjax(rows);            // rows > 0 ? success() : error()

// 业务校验失败 → 500
return error("产品名称已存在");
throw new ServiceException("存在下级产品，不允许删除");

// 业务警告 → 601
return warn("部分数据处理失败，请检查后重试");

// 带自定义码
throw new ServiceException("自定义错误", HttpStatus.WARN);
```

---

## 2. 异常类体系

来源：`ruoyi-common/src/main/java/com/ruoyi/common/exception/`

```
RuntimeException
└── BaseException（抽象基类，i18n 消息码）
    ├── DemoModeException          演示模式，禁止操作
    ├── GlobalException            全局异常
    ├── UtilException              工具类异常
    ├── ServiceException     ★    业务异常（最常用）
    ├── file.FileException
    │   ├── FileNameLengthLimitExceededException   文件名超长
    │   ├── FileSizeLimitExceededException         文件过大
    │   ├── FileUploadException                    文件上传失败
    │   └── InvalidExtensionException              非法文件扩展名
    ├── job.TaskException          定时任务异常
    └── user.UserException
        ├── BlackListException                     黑名单用户
        ├── CaptchaException                       验证码错误
        ├── CaptchaExpireException                 验证码过期
        ├── UserNotExistsException                 用户不存在
        ├── UserPasswordNotMatchException          密码不匹配
        └── UserPasswordRetryLimitExceedException  密码重试超限
```

### 2.1 两个基类的区别（易混淆）

| 类 | `getCode()` 类型 | 语义 | 典型用法 |
|----|-----------------|------|---------|
| `BaseException` | `String` | **i18n 消息键**，经 `MessageUtils.message(code, args)` 查 `i18n/messages.properties` | 需要国际化的固定文案 |
| `ServiceException` | `Integer` | **HTTP 状态码**，直接写进 `AjaxResult.code` | 业务校验失败（绝大多数场景） |

```java
// ServiceException（推荐，90% 场景）
throw new ServiceException("产品名称已存在");
throw new ServiceException("存在下级产品，不允许删除", HttpStatus.WARN);

// BaseException（需要 i18n 时用）
throw new BaseException("user.password.not.match");
```

### 2.2 全局异常处理器

`ruoyi-framework/src/main/java/com/ruoyi/framework/web/exception/GlobalExceptionHandler.java`
（`@RestControllerAdvice`）捕获并转为 `AjaxResult`：

| 异常 | 处理逻辑 | 返回 code |
|------|---------|----------|
| `AccessDeniedException` | 记录 URI + 权限校验失败 | `403` + "没有权限，请联系管理员授权" |
| `HttpRequestMethodNotSupportedException` | 记录 URI + 方法 | `500` + 原始 message |
| **`ServiceException`** | `log.error`；`code != null` 时用自定义码 | 自定义码，否则 `500` |
| `MissingPathVariableException` | 记录缺失的路径变量 | `500` |
| `MethodArgumentTypeMismatchException` | 参数类型转换失败 | `500` |
| `BindException` / `MethodArgumentNotValidException` | 参数绑定校验失败 | `500` + 首个字段错误 |
| `DemoModeException` | 演示模式拦截 | `500` |
| `Exception` | 兜底 | `500` |

> **注意**：XSS 防护会对错误信息做 `EscapeUtil.clean()` 转义，避免把用户输入原样回显。

---

## 3. 前端错误码映射

来源：`ruoyi-ui/src/utils/errorCode.js`

**当前映射表（只有 4 条）**：

```js
export default {
  '401': '认证失败，无法访问系统资源',
  '403': '当前操作没有权限',
  '404': '访问资源不存在',
  'default': '系统未知错误，请反馈给管理员'
}
```

### 3.1 响应拦截逻辑

`ruoyi-ui/src/utils/request.js`：

| 分支 | 行为 |
|------|------|
| `code === 200` | 返回 `res.data` |
| `code === 401` | `MessageBox.confirm` 提示重新登录（`isRelogin.show` 全局去重），reject |
| `code === 500` | `Message.error(msg)`，reject（**业务失败主要走这里**） |
| `code === 601` | `Message.warning(msg)`，reject |
| 其他非 200 | `Notification.error({title: msg})`，reject |
| 网络层异常 | `Network Error` → "后端接口连接异常"；`timeout` → "系统接口请求超时" |

### 3.2 取值优先级

```js
const msg = errorCode[code] || res.data.msg || errorCode['default']
```

即：**前端映射表 → 后端返回的 msg → default**。
所以即便前端不配映射，后端的中文 `msg` 也能正常显示。

### 3.3 待改进（P1-3）

`601`（业务警告）当前未配置映射，实际会走 `res.data.msg`。建议补齐：

```js
export default {
  '401': '认证失败，无法访问系统资源',
  '403': '当前操作没有权限',
  '404': '访问资源不存在',
  '500': '操作失败',
  '601': '操作警告',
  'default': '系统未知错误，请反馈给管理员'
}
```

---

## 4. 业务错误码规划（P1-3 待落地）

> ⚠️ **以下为规划，尚未实现。** 落地时需：
> 1. 在 `ruoyi-common/src/main/java/com/ruoyi/common/constant/` 新增 `BizErrorCode.java`
> 2. Service 层 `throw new ServiceException(msg, BizErrorCode.XXX)`
> 3. 前端 `errorCode.js` 同步映射

### 4.1 规划原则

- 业务错误码放在 **10000+** 段，避开 HTTP 状态码语义
- 格式：`1` + `2位模块号` + `2位序号`
- 前端必须能显示**可读中文**（靠后端 `msg` 兜底，映射表只做兜底补充）

### 4.2 规划表

| code | 模块 | 含义 | 建议 msg |
|------|------|------|---------|
| `10101` | 产品管理 | 产品名称已存在 | `产品名称已存在，请重新输入` |
| `10102` | 产品管理 | 存在下级产品，不允许删除 | `存在下级产品，不允许删除` |
| `10103` | 产品管理 | 上级产品不能是自己 | `上级产品不能是自己` |
| `10104` | 产品管理 | 产品不存在或已被删除 | `产品不存在或已被删除` |
| `10201` | 学生管理 | 学生名称已存在 | `学生名称已存在，请重新输入` |
| `10202` | 学生管理 | 年龄超出合法范围 | `年龄需在 0 到 120 之间` |
| `10203` | 学生管理 | 学生不存在或已被删除 | `学生不存在或已被删除` |
| `10901` | 通用业务 | 数据已被他人修改，请刷新重试 | `数据已被他人修改，请刷新后重试` |
| `10902` | 通用业务 | 导出数据量超限 | `导出数据量超出上限，请缩小查询范围` |
| `10903` | 通用业务 | 导入文件格式错误 | `导入文件格式错误，请使用模板文件` |

### 4.3 常量类示例

```java
package com.ruoyi.common.constant;

/**
 * 业务错误码
 *
 * 规划：1(业务段) + 2位模块号 + 2位序号
 *
 * @author life
 * @date 2026-08-30
 */
public class BizErrorCode
{
    /** 产品管理模块 */
    public static final Integer PRODUCT_NAME_DUPLICATE = 10101;
    public static final Integer PRODUCT_HAS_CHILDREN   = 10102;
    public static final Integer PRODUCT_SELF_PARENT    = 10103;
    public static final Integer PRODUCT_NOT_FOUND      = 10104;

    /** 学生管理模块 */
    public static final Integer STUDENT_NAME_DUPLICATE = 10201;
    public static final Integer STUDENT_AGE_INVALID    = 10202;
    public static final Integer STUDENT_NOT_FOUND      = 10203;

    /** 通用业务 */
    public static final Integer DATA_CONFLICT          = 10901;
    public static final Integer EXPORT_SIZE_EXCEEDED   = 10902;
    public static final Integer IMPORT_FORMAT_INVALID   = 10903;
}
```

使用：

```java
throw new ServiceException("存在下级产品，不允许删除", BizErrorCode.PRODUCT_HAS_CHILDREN);
```

---

## 5. 常见错误排查

| 现象 | 可能原因 | 排查方向 |
|------|---------|---------|
| 接口返回 401 | token 过期 / Redis 会话丢失 | 检查 Redis 是否可用、`token.expireTime` |
| 接口返回 403 | 缺 `@PreAuthorize` 或权限串写错 | 对比 `sys_menu.perms` 与注解中的权限串 |
| 接口返回 500 但 `msg` 是"操作失败" | Service 返回 0 行影响，被 `toAjax` 判为失败 | 检查 SQL 是否命中；业务校验应改抛 `ServiceException` |
| 新增字段查出来是 null | `resultMap` / `selectXxxVo` 未同步 | `mapUnderscoreToCamelCase` 已关闭，需手工加映射 |
| Mapper 报 `BindingException` | XML 文件名不以 `Mapper.xml` 结尾，或不在 `mapper/**` 下 | 检查 `mapperLocations` 配置 |
| 前端提示"系统未知错误" | 后端抛了未捕获异常，且前端无对应映射 | 看后端日志；补齐 `errorCode.js` |
| 文件上传失败 | 超过 `max-file-size: 10MB` / 扩展名白名单 | 检查 `application.yml` 与 `FileUploadException` |
| Excel 导出乱码/空文件 | 前端未用 `download()`（未设 `responseType: blob`） | 改用 `utils/request.js` 的 `download()` |
| 验证码接口报错 | `captchaType` 配置与前端不一致 | `application.yml` 的 `ruoyi.captchaType`（`math` / `char`） |

---

## 6. 变更记录

| 日期 | 变更 |
|------|------|
| 2026-08-30 | 建立错误码参考；梳理 `HttpStatus` 对照表与异常体系；规划业务错误码段（待落地） |
