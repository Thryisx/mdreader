# MD Reader

Markdown 阅读器 + 编辑工作台 + 管理后台。

- 公开首页 `/`：文章列表、标题搜索、分类筛选、分页。
- 阅读页 `/read/:id`：目录、阅读进度、阅读排版设置、图片懒加载、复制链接。
- 工作台 `/workspace`：文件树、导入导出、新建/重命名/移动/删除、Markdown 三模式编辑、本地暂存与云端保存。
- 管理后台 `/admin`：用户管理、文件元数据管理。

## 技术栈

- 前端：Vue 3 + Vite + TypeScript + vue-router + TinyMCE + x-markdown-vue
- 后端：Spring Boot 3.5.3 + MyBatis + Flyway + JWT
- 数据库：MySQL 8
- 存储：本地头像；OSS 图片方案见文档

## 目录

```text
backend/   Spring Boot 服务
frontend/  Vue 前端
docs/      接口、设计、审查、部署文档
```

完整文档入口：[`docs/README.md`](docs/README.md)

## 快速开始

### 1. 建库

```sql
CREATE DATABASE IF NOT EXISTS `mdreader`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

Flyway 会在后端启动时自动执行 `backend/src/main/resources/db/migration/` 下的迁移。

### 2. 配置后端

```powershell
cd backend
Copy-Item src/main/resources/application-local.example.yml src/main/resources/application-local.yml
```

设置环境变量：

```text
DB_HOST
DB_PORT
DB_NAME
DB_USERNAME
DB_PASSWORD
JWT_SECRET
```

`application-local.yml`、`.env`、OSS/阿里云凭据文件已被 `.gitignore` 忽略，不要提交真实密码或 AccessKey。

### 3. 启动后端

```powershell
mvn -DskipTests package
java -jar target/mdreader-0.0.1-SNAPSHOT.jar
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

开发地址：`http://localhost:5173`，Vite 会把 `/api` 代理到 `http://localhost:8080`。

## 测试与构建

```bash
cd frontend
npm run typecheck
npm run build
```

```powershell
cd backend
mvn test
```

后端集成测试使用独立测试库，数据库连接通过 `TEST_DB_*` 环境变量配置；不要在测试配置中提交真实密码。

## 生产部署

- Nginx / SPA 回退示例：[`docs/frontend/reader-deploy-2026-10-31.md`](docs/frontend/reader-deploy-2026-10-31.md)
- 生产环境必须显式设置 `SPRING_PROFILES_ACTIVE=prod`，并配置 `JWT_SECRET`、数据库和 CORS。
- 图片功能建议使用 OSS，方案见 [`docs/project-gap-remediation-2026-11-01.md`](docs/project-gap-remediation-2026-11-01.md)。

## 版本

- 分支：`thryisx`
- 版本：`v1.0`
