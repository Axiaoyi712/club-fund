# 社团活动经费管理系统

> 面向高校社团的活动经费全流程管理系统，覆盖「申请 → 审批 → 支出 → 归档」完整闭环。

**作者**：王奕翔　|　**GitHub**：[Axiaoyi712](https://github.com/Axiaoyi712)

---

## 一、项目简介

系统把高校社团活动经费的线下纸质流程搬到线上。申请人在线填报活动信息和经费预算，主席团、团委老师、学院领导依次在线审批，获批后登记实际支出，活动结束后归档形成经费执行台账。系统还提供统计报表、消息通知和 Excel 导出。

## 二、技术栈

| 层次 | 技术 | 版本 |
| --- | --- | --- |
| 后端框架 | Spring Boot | 3.3.5 |
| 运行环境 | JDK | 17 |
| 持久层框架 | MyBatis-Plus | 3.5.7 |
| 模板引擎 | Thymeleaf | 随 Spring Boot |
| 前端 | Bootstrap、原生 JavaScript（图表用 CSS/JS 手写） | — |
| 数据库 | MySQL | 8.x |
| 安全 | Spring Security（BCrypt 密码加密、CSRF 表单令牌） | 随 Spring Boot |
| 工具库 | Lombok | 随 Spring Boot |
| Excel 处理 | Apache POI | 5.2.5 |

## 三、功能模块

- **工作台**：登录后展示待办、金额和申请概览。
- **活动申请**：填写活动信息和经费预算明细，金额自动合计，提交后进入审批流程。
- **三级审批**：主席团 → 团委老师 → 学院领导逐级审批，可通过或驳回，每一步都留痕。
- **社团管理**：维护社团信息和社团成员名单。
- **支出登记**：活动获批后登记每笔实际支出，支持超支预警。
- **经费执行台账**：活动结束后归档，形成经费执行台账。
- **统计报表**：按社团、审批状态、金额分级统计，含柱状图和环形图。
- **消息通知**：提交、审批、驳回时自动生成待办和结果通知。
- **Excel 导出**：活动申请可导出为 Excel 文件。

## 四、角色说明

| 角色 | 标识 | 主要权限 |
| --- | --- | --- |
| 申请人 | `applicant` | 提交活动申请、登记支出、查看本人申请 |
| 主席团 | `chairman` | 一级审批、社团管理 |
| 团委老师 | `teacher` | 二级审批、查看经费执行台账 |
| 学院领导 | `leader` | 三级审批、查看经费执行台账 |
| 管理员 | `admin` | 全部权限，含统计报表 |

## 五、业务流程

```
申请人提交申请
      │
      ▼
主席团审批 ──驳回──► 申请人修改后重新提交
      │ 通过
      ▼
团委老师审批 ──驳回──► 申请人修改后重新提交
      │ 通过
      ▼
学院领导审批 ──驳回──► 申请人修改后重新提交
      │ 通过
      ▼
   已获批
      │
      ▼
登记实际支出 ──► 活动结束归档 ──► 经费执行台账
```

申请单状态依次为：`submitted`（待主席团审批）→ `chairman_passed`（待团委老师审批）→ `teacher_passed`（待学院领导审批）→ `leader_passed` → `approved`（已获批）；任一环节驳回则为 `rejected`。

## 六、快速开始

### 1. 环境准备

- JDK 17
- Maven 3.6+
- MySQL 8.x

### 2. 创建数据库

登录 MySQL，执行：

```sql
CREATE DATABASE club_fund DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 3. 修改数据库配置

打开 `src/main/resources/application.yml`，把用户名和密码改成你本机的 MySQL 账号：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/club_fund?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456   # 改成你自己的 MySQL 密码
```

### 4. 启动项目

方式一（推荐）：在 IDEA 中运行主类 `com.clubfund.ClubFundApplication`。

方式二：命令行启动。

```bash
mvn spring-boot:run
```

### 5. 访问系统

浏览器打开 <http://localhost:8080>，用下面的测试账号登录。

> 启动时系统会自动执行 `schema.sql` 建表；当用户表为空时，会自动插入 5 个测试账号。

## 七、测试账号

所有账号密码均为 **`123456`**。

| 角色 | 登录名 | 密码 |
| --- | --- | --- |
| 申请人 | `applicant1` | 123456 |
| 主席团 | `chairman1` | 123456 |
| 团委老师 | `teacher1` | 123456 |
| 学院领导 | `leader1` | 123456 |
| 管理员 | `admin1` | 123456 |

## 八、项目结构

```
club-fund
├── pom.xml                          # Maven 依赖与构建配置
└── src/main
    ├── java/com/clubfund
    │   ├── ClubFundApplication.java # 启动入口
    │   ├── config/                  # 配置类：登录拦截、CSRF、数据初始化等
    │   ├── controller/              # 控制层：接收请求、返回页面
    │   ├── dto/                     # 表单数据对象
    │   ├── entity/                  # 实体类：与数据库表对应
    │   ├── mapper/                  # MyBatis-Plus Mapper 接口
    │   └── service/                 # 业务逻辑层
    └── resources
        ├── application.yml          # 系统配置
        ├── schema.sql               # 建表脚本
        ├── static/                  # 静态资源：css、js
        └── templates/               # Thymeleaf 页面模板
```

## 九、常见问题

- **端口 8080 被占用**：在 `application.yml` 中修改 `server.port`，或关闭占用 8080 的程序。
- **启动报数据库连接失败**：确认 MySQL 已启动、数据库 `club_fund` 已创建、账号密码正确。
- **页面登录后没有数据**：用户表为空时才会自动插入测试账号；若你手动建过用户，系统不会重复插入。
