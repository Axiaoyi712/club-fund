# 社团活动经费管理系统

> 面向高校社团的活动经费全流程管理系统，覆盖「申请 → 三级审批 → 支出执行 → 归档台账」完整闭环，并提供统计报表、消息通知与 Excel 导出。

**作者**：王奕翔　|　**GitHub**：[Axiaoyi712](https://github.com/Axiaoyi712)

---

## 一、项目简介

系统把高校社团活动经费的线下纸质审批流程搬到线上。社团负责人在线填报活动信息和经费预算，主席团、团委老师、学院领导依次在线审批，全部通过后活动进入执行阶段；执行过程中登记每笔实际支出，系统自动做超支预警；活动结束后由老师或领导归档，形成经费执行台账。整个流程的每一步都有审批记录和消息通知，数据可追溯。

## 二、技术栈

| 层次 | 技术 | 版本 |
| --- | --- | --- |
| 后端框架 | Spring Boot | 3.3.5 |
| 运行环境 | JDK | 17 |
| 持久层 | MyBatis-Plus（`mybatis-plus-spring-boot3-starter`） | 3.5.7 |
| 模板引擎 | Thymeleaf | 随 Spring Boot |
| 前端 | Bootstrap、原生 JavaScript（图表用 CSS/JS 手写，无额外图表库） | — |
| 数据库 | MySQL | 8.x |
| 安全 | Spring Security（仅引入 `spring-security-crypto` 与 `spring-security-web`） | 随 Spring Boot |
| 工具库 | Lombok | 随 Spring Boot |
| Excel 处理 | Apache POI（`poi-ooxml`） | 5.2.5 |

项目采用经典的 **Controller → Service → Mapper** 三层结构，页面由服务端 Thymeleaf 渲染，少量交互通过原生 JS + 表单提交完成，没有引入前后端分离框架，整体依赖轻、易部署。

## 三、系统架构

### 3.1 分层结构

- **Controller**：接收请求、参数绑定、调用 Service、返回 Thymeleaf 页面或重定向。
- **Service**：承载全部业务规则，包括状态流转、金额计算、权限校验、通知派发；关键写操作均在事务中执行。
- **Mapper**：继承 MyBatis-Plus 的 `BaseMapper`，单表 CRUD 无需手写 SQL，复杂查询用 `LambdaQueryWrapper` 构造。
- **Entity / DTO**：实体与数据库表一一对应；DTO 用于接收表单数据。

### 3.2 请求处理链路

```
HTTP 请求
   │
   ▼
CsrfFilter（校验非安全方法的 CSRF 令牌）
   │
   ▼
LoginInterceptor（Session 无登录用户则重定向 /login）
   │
   ▼
Controller（参数绑定 + 调用 Service）
   │
   ▼
Service（@Transactional 事务 + 业务规则 + 行级锁）
   │
   ▼
Mapper（MyBatis-Plus → MySQL）
   │
   ▼
Thymeleaf 渲染页面返回
```

### 3.3 关键设计点

- **状态机驱动审批**：申请单状态字段决定当前停留在哪个审批级，角色与状态严格匹配才能操作。
- **页面级与方法级双重鉴权**：菜单按角色渲染，Service 内部再次校验，避免绕过页面前端直接发请求。
- **金额全程服务端计算**：预算合计、申请合计、支出合计均由后端计算并落库，前端只负责展示，不信任前端传入的合计值。
- **幂等提交**：表单携带一次性 `submission_token`，配合数据库唯一键，防止重复提交和刷新重放。

## 四、功能模块

| 模块 | 说明 |
| --- | --- |
| 工作台 | 展示待办数量、金额概览、申请与归档趋势 |
| 活动申请 | 选择主办社团、填写活动信息、动态增删预算明细，提交后进入审批 |
| 经费审批 | 按角色展示当前停留在本审批级的申请，支持通过 / 驳回并填写意见 |
| 社团管理 | 维护社团信息、指定负责人、管理社团成员名单 |
| 支出登记 | 获批活动登记实际支出，可关联预算明细行，自动超支预警 |
| 经费执行台账 | 已归档活动的获批金额、实际支出、结余/超支一览 |
| 统计报表 | 审批状态分布、社团申请金额、金额分级、环比趋势（管理员） |
| 消息通知 | 待办与结果通知，支持已读 / 待处理状态管理 |
| Excel 导出 | 将申请数据导出为 `.xlsx` 文件 |

## 五、数据库设计

系统共 **8 张表**，由 `schema.sql` 在启动时通过 `CREATE TABLE IF NOT EXISTS` 自动创建；后续迭代新增的字段通过一系列 `*SchemaInitializer` 做幂等补列，保证旧库可平滑升级。

### 5.1 表清单

| 表 | 作用 |
| --- | --- |
| `user` | 用户账号与角色 |
| `club` | 社团（主办单位），含负责人 |
| `club_member` | 社团成员名单 |
| `activity` | 活动经费申请主表，含状态与归档信息 |
| `budget_item` | 预算明细行 |
| `approve_log` | 审批操作留痕 |
| `expense` | 实际支出登记 |
| `notification` | 消息通知 |

### 5.2 表关系

```
user ──< club（leader_id，一个负责人可管多个社团）
club ──< club_member
club ──< activity（club_id）
user ──< activity（apply_user，申请人）
activity ──< budget_item
activity ──< approve_log
activity ──< expense
budget_item ──< expense（budget_item_id，可空，用于明细级执行对比）
user ──< notification（user_id）
```

### 5.3 核心表说明

- **activity**：核心主表。`status` 记录审批状态，`total_budget` / `total_apply` 为预算与申请合计，`archived`、`archive_time`、`archive_operator_*` 记录归档信息，`submission_token` 与申请人构成唯一键。
- **budget_item**：每行记录开支项目、数量、单价、预算金额（数量×单价）和申请金额。
- **approve_log**：按 `stage`（chairman / teacher / leader）、`action`（pass / reject）记录审批人、意见和时间。
- **expense**：记录支出项目、金额、实际支出日期、关联预算行，并带提交凭证唯一键。

## 六、核心业务设计

### 6.1 审批状态机

```
                ┌────────── reject（任一审批级）──────────┐
                ▼                                         │
submitted ──pass──► chairman_passed ──pass──► teacher_passed ──pass──► approved
   ▲                                                                  │
   └──────────────────── resubmit（申请人修改后重提）◄── rejected ◄────┘
```

- `submitted`：主席团审批中；`chairman_passed`：团委老师审批中；`teacher_passed`：学院领导审批中；`approved`：已获批；`rejected`：已驳回。
- 学院领导终审通过即置为 `approved`，三级通过后申请人收到获批结果。
- 驳回为终态，申请人修改后可重提，**保留原申请 ID 与历史审批记录**，重提时不能更换主办社团。

### 6.2 申请规则

- 提交人必须是所选社团的**负责人**（`club.leader_id` 与当前用户一致）。
- 活动开始时间必须至少在申请当天的 **7 天之后**（对齐学院提前一周填报的要求）。
- 结束时间不得早于开始时间；至少包含一条预算明细。

### 6.3 预算与金额

- 行预算金额 = 数量 × 单价，由服务端计算。
- 行申请金额不得超过该行预算金额。
- 预算合计、申请合计由明细行自动汇总，金额统一使用 `BigDecimal`（DECIMAL(10,2)），避免浮点误差。

### 6.4 支出与超支预警

- 仅 **已获批、未归档、申请人本人** 可登记支出。
- 支出可关联预算明细行，系统按明细行聚合已支出金额，做**明细级执行对比**。
- 两级预警：某行累计支出**首次超过该行获批金额**、**首次超过该行预算金额**时分别给出提示及超出数额（仅在跨越临界点时提示，避免重复告警）。
- 活动级判定：支出合计超过申请合计即标记为超支。

### 6.5 归档与台账

- 归档条件：活动已获批、未归档、活动结束时间已过，操作人为团委老师、学院领导或管理员。
- 台账行计算：结余 = 获批合计 − 实际支出合计（正数为结余，负数为超支）。
- 归档后该活动经费执行闭环，不再允许登记支出。

### 6.6 通知触发规则

| 触发事件 | 通知对象 | 类型 |
| --- | --- | --- |
| 提交 / 重提申请 | 全部主席团成员 | 待办 |
| 主席团通过 | 全部团委老师 | 待办 |
| 团委老师通过 | 全部学院领导 | 待办 |
| 终审通过 | 申请人 | 结果 |
| 申请被驳回 | 申请人 | 结果（含驳回原因） |

每次推进前会先把该申请上一阶段的未处理待办置为已处理，再生成下一阶段待办。

## 七、安全与并发

- **认证**：基于 Session 的登录态，`LoginInterceptor` 拦截所有未放行路径，未登录重定向到 `/login`。
- **鉴权**：基于角色（RBAC），菜单按角色渲染，Service 内部对操作人角色和数据归属做二次校验。
- **密码存储**：使用 BCrypt 加密，数据库不存明文密码。
- **CSRF 防护**：注册 Spring Security 的 `CsrfFilter`，令牌存于 Session（`HttpSessionCsrfTokenRepository`），Thymeleaf 自动为 POST 表单注入隐藏令牌；校验失败跳转 `/csrf-error`。
- **并发一致性**：审批、支出、归档等写操作标注 `@Transactional`，并通过 `SELECT ... FOR UPDATE` 对活动行加排他锁，串行化同一申请的并发操作。
- **幂等控制**：表单携带 UUID 凭证，数据库以 `(apply_user, submission_token)`、`(activity_id, submission_token)` 建唯一键，重复提交只生效一次。
- **输入校验**：对金额范围、小数位、文本长度、日期合法性、驳回原因等做服务端校验。

## 八、快速开始

### 1. 环境准备

- JDK 17
- Maven 3.6+
- MySQL 8.x

### 2. 创建数据库

```sql
CREATE DATABASE club_fund DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 3. 修改数据库配置

编辑 `src/main/resources/application.yml`，将用户名、密码改为本机 MySQL 账号：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/club_fund?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456   # 改为你自己的 MySQL 密码
```

### 4. 启动

- IDE 中运行主类 `com.clubfund.ClubFundApplication`；
- 或命令行：`mvn spring-boot:run`。

### 5. 访问

浏览器打开 <http://localhost:8080>，使用下方测试账号登录。

> 启动时自动建表；当用户表为空时，`DataInitializer` 会自动插入 5 个测试账号。

## 九、测试账号

所有账号密码均为 **`123456`**。

| 角色 | 登录名 | 密码 |
| --- | --- | --- |
| 申请人 | `applicant1` | 123456 |
| 主席团 | `chairman1` | 123456 |
| 团委老师 | `teacher1` | 123456 |
| 学院领导 | `leader1` | 123456 |
| 管理员 | `admin1` | 123456 |

## 十、打包与部署

```bash
# 打包（产物在 target/ 下）
mvn clean package

# 运行 jar
java -jar target/club-fund-0.0.1.jar
```

部署时可通过命令行参数或环境变量覆盖内置配置，无需改包，例如：

```bash
java -jar club-fund-0.0.1.jar --server.port=8080 --spring.datasource.password=你的密码
```

## 十一、项目结构

```
club-fund
├── pom.xml                          # Maven 依赖与构建配置
└── src/main
    ├── java/com/clubfund
    │   ├── ClubFundApplication.java # 启动入口
    │   ├── config/                  # 登录拦截、CSRF、MyBatis-Plus、数据与表结构初始化
    │   ├── controller/              # 控制层
    │   ├── dto/                     # 表单数据对象
    │   ├── entity/                  # 实体类
    │   ├── mapper/                  # Mapper 接口
    │   └── service/                 # 业务逻辑层
    └── resources
        ├── application.yml          # 系统配置
        ├── schema.sql               # 建表脚本
        ├── static/                  # css、js 静态资源
        └── templates/               # Thymeleaf 页面模板
```

## 十二、常见问题

- **端口 8080 被占用**：修改 `application.yml` 的 `server.port`，或释放占用端口的进程。
- **启动报数据库连接失败**：确认 MySQL 已启动、数据库 `club_fund` 已创建、账号密码正确。
- **提交后提示表单失效**：提交凭证为一次性，重复提交或刷新旧页面会被拦截，重新进入表单填写即可。
- **登录后没有测试数据**：仅当用户表为空时才会自动插入测试账号；手动建过用户则不会重复插入。
