# 墨舟 · 微信文章管理助手

一个可运行的 Spring Boot + Vue 3 微信公众号内容工作台。系统覆盖公众号、文章、素材、公众号用户、后台用户和定时创作任务；文章编辑器支持人工富文本编辑与 AI 对话协同编辑。

## 已实现能力

- 公众号管理：AppID/AppSecret 加密保存、连接检测、Access Token 缓存。
- 文章管理：草稿/发布状态、搜索筛选、乐观锁、版本快照与回滚、软删除。
- 智能编辑器：Tiptap 富文本编辑、自动保存、移动端预览；Agent4j Tool Calling 实时读取浏览器编辑区，通过读取、删除、流式插入、替换和元数据工具直接修改文章，聊天区同步展示工具过程，整轮完成后只生成一个文章版本。
- 微信发布：封面素材上传、创建/更新微信草稿、提交发布、查询发布结果。
- 定时创作 Agent：Quartz JDBC 持久化调度；每次按自然语言任务要求自主搜索、浏览和核实资料，调用素材库、网络图片、图片生成与编辑工具完成一篇新文章，并按配置保存本地草稿、同步微信草稿或自动发布；运行历史记录文章与工具调用过程。
- 用户体系：后台账号、五级角色、Token 登录、密码重置；公众号粉丝同步和查询。
- 系统设置：后台维护 LLM Base URL、模型和 API Key，API Key 加密入库；登录用户可修改自己的密码并使全部旧 Token 失效。
- 运维能力：Smart MyBatis 表结构同步与数据访问、仪表盘、图片素材库、操作审计、网页访问 SSRF 防护。

## 本地启动

环境要求：JDK 17+、MySQL 8+、Node.js 20+。

先创建数据库：

```sql
CREATE DATABASE `wechat-article` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

配置环境变量：

```bash
cp .env.example .env
# 修改 .env 中的数据库密码、管理员密码和 APP_SECRET_KEY
```

应用会直接导入项目根目录的 `.env`，不需要在 Shell 中 `source`。其中需要同时配置业务库 `ENV.MYSQL_URL` 和专用测试库 `ENV.MYSQL_TEST_URL`。测试库名称固定为 `wechat-article-test`，测试启动前会校验库名并清空该库；任何非测试库都会被安全检查拒绝。

启动后端（Smart MyBatis 会根据实体自动同步业务表；Quartz 会初始化自己的 JDBC 表）：

```bash
./mvnw spring-boot:run
```

另开终端启动前端：

```bash
cd webui
npm install
npm run dev
```

访问 `http://localhost:5173`。默认开发账号为 `admin / Admin@123`；如果设置了 `ADMIN_PASSWORD`，请使用环境变量中的密码。首次登录后应立即进入“系统设置”修改密码。

## AI 与微信配置

登录后进入“系统设置”，配置 Base URL、模型与 API Key，并选择 Chat Completions、Responses / Codex 或 Anthropic Messages 协议。Codex 专用模型选择 Responses / Codex，Claude 原生接口选择 Anthropic Messages。配置持久化在数据库中，API Key 使用 `APP_SECRET_KEY` 加密，YAML 和环境变量中不保存 LLM 参数。未配置或未启用时，普通手工编辑仍可使用，AI 编辑和定时创作任务会明确提示需要先配置 LLM。

微信功能需要在“公众号管理”中录入有效的 AppID/AppSecret，并在微信公众平台配置服务器出口 IP 白名单。同步微信草稿或自动发布的任务必须选择公众号；封面可以预先指定，也可以由 Agent 在执行时从素材库选择、从网页导入或生成。

## 验证命令

```bash
./mvnw test
cd webui && npm run build
```

后端测试只使用真实 MySQL，不包含 H2 或 MySQL 兼容模式。Smart MyBatis 会在空测试库中从实体同步业务表，以此验证实体元数据和实际 MySQL DDL。

后端接口统一位于 `/api`，开发环境由 Vite 代理到 `http://localhost:8081`。上传图片保存在 `STORAGE_PATH`，并通过 `/uploads/**` 访问。

业务 Mapper 全部继承 `SmartMapper`，组合查询由 Mapper 的 `default` 方法和 Smart MyBatis `Where` DSL 实现，业务表结构也由 Smart MyBatis 自动同步。Quartz 使用同一数据源中的 `QRTZ_*` 表保存 Job、Trigger 和集群状态，应用重启后任务仍可恢复。
