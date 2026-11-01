# 前端问题与阅读器差距分析（2026-10-31）

> 审查范围：`frontend/`
> 审查方式：静态阅读路由、页面、组件、composable、API 封装与当前构建配置；`npm run typecheck` 当前通过。
> 原始结论：当前产品本质上是 **“Markdown 编辑工作台 + 管理后台”**，不是面向读者的阅读器。文件树和保存已经接后端，但阅读器的信息架构、阅读页和阅读体验基本缺失。
> 复核日期：2026-10-31（晚间）。本次复核逐条对照当前代码重新判定状态，已修复项给出代码位置证据，未修复项保留原结论。

---

## 1. 现状判断

### 1.1 已经具备

- Vue 3 + Vite + TypeScript，`npm run typecheck` 通过。
- 工作区三模式：所见即所得（TinyMCE）、源码、只读预览（`WorkspaceView.vue`）。
- 文件树、文件新建、文件夹新建、导入、导出、云端保存、IndexedDB 本地暂存已接入。
- 登录、注册、当前用户、资料修改、头像上传已接入。
- B 端登录、用户管理、文件管理（仅元数据）已接入。
- Markdown 预览已开启 `sanitize`，没有发现裸 `v-html`（`PreviewPane.vue`）。
- **（复核新增）公开阅读链路已打通**：`/` 为公开文章列表、`/read/:id` 为独立阅读页，后端公开接口支持匿名访问。

### 1.2 当前不再是「只有工作区」

- 路由已有首页 `/`（`ArticleListView.vue`）与阅读页 `/read/:id`（`ArticleReaderView.vue`），`/books/:id/chapters/:chapterId` 重定向到 `/read/:id`。
- 阅读页独立于工作区布局，含标题、元信息、目录、正文、阅读进度与操作区。
- 仍缺：文章上下篇/相关推荐、收藏/书签、阅读历史、分类/标签模型、打印导出 PDF、脚注/数学/图表、SSR/预渲染。

---

## 2. 阅读器能力矩阵

| 能力 | 当前状态 | 缺口 | 优先级 |
|---|---|---|---|
| 首页 / 文章列表 | 已实现 | `/` 为公开文章列表，支持搜索、分类筛选、分页 | — |
| 文章详情 / 阅读页 | 已实现 | `/read/:id` 独立页面 | — |
| 公开阅读 | 已实现 | 后端 `@OptionalAuth` 支持匿名读列表/详情/正文 | — |
| 文章标题 / 作者 / 时间 / 摘要 | 已实现 | 卡片与详情头部均有，后端提供 `category`/`summary`/`authorNickname` | — |
| 目录 TOC | 已实现 | `useReading.ts` 提取标题生成 slug 锚点，支持跳转与高亮 | — |
| 搜索 | 已实现（标题） | 列表页 `keyword` 后端模糊匹配；工作区为客户端文件名过滤 | — |
| 上下篇 / 相关推荐 | 没有 | 无相邻文章导航 | P1 |
| 阅读进度 | 已实现 | 滚动进度条 + localStorage 记录上次位置 | — |
| 收藏 / 书签 | 没有 | 无收藏模型和入口 | P1 |
| 阅读历史 | 没有 | 无历史模型和入口 | P2 |
| 阅读排版设置 | 已实现 | 字号、行高、内容宽度、代码字号，持久化到 localStorage | — |
| 代码块复制 | 已实现 | `x-markdown-vue` 默认提供，预览栏文案已统一提示 | — |
| 图片灯箱 / 懒加载 | 部分 | 已配置懒加载（`loading="lazy"`）；无灯箱放大 | P2 |
| 外链处理 | 已实现 | `customAttrs` 设置 `target="_blank"` + `rel="noopener noreferrer"` | — |
| 分类 / 标签 | 部分 | 后端有 `category` 字段与分类计数接口；无独立标签模型 | P2 |
| 分享 / 复制链接 | 已实现 | 阅读页有「复制链接」按钮 | — |
| 打印 / 导出 PDF | 没有 | 只有导出 Markdown | P2 |
| 脚注 / 数学 / 图表 | 未确认 | 未配置对应插件 | P2 |
| 移动端阅读 | 已实现 | 阅读页有移动端断点适配 | — |
| SEO / 社交分享卡片 | 部分 | 有 `document.title` 动态设置；无 description/OG/canonical，纯 SPA | P2 |
| 无障碍阅读 | 部分 | 阅读页有 skip link 与 `aria-live`；文件树仍缺方向键导航 | P2 |

---

## 3. P0：做阅读器前必须补

### 3.1 缺少首页和文章列表

- 位置：`frontend/src/router/index.ts`
- 问题：`/` 直接重定向到 `/workspace`，用户没有“浏览文章”的入口。
- **状态：已修复。**
- 现状证据：
  - `/` 路由指向 `ArticleListView.vue`（懒加载），不再跳转工作区。
  - 列表页支持分页、排序（`created_at DESC, id DESC`）、空态、加载态与错误重试。
  - 数据源为 `GET /api/files`，后端固定返回 `PUBLIC + PUBLISHED`；另有 `GET /api/files/categories` 提供真实分类与计数。
  - URL 参数 `q`（标题搜索）与 `kind`（分类）刷新后保留。

### 3.2 缺少独立阅读页

- 位置：`WorkspaceView.vue`、`PreviewPane.vue`
- 问题：阅读只是工作区的一个模式，页面仍然围绕编辑和文件树组织；没有文章头部、目录、正文排版、上下篇等阅读结构。
- **状态：已修复（上下篇除外）。**
- 现状证据：
  - 新增 `/read/:id` 路由与 `ArticleReaderView.vue`（16 KB），独立于工作区布局。
  - 阅读页含文章头部（元信息、分类、作者、更新时间）、目录侧栏、正文区、阅读操作区（复制链接、回到顶部）。
  - 复用 `PreviewPane` 的 `bare` 模式渲染（同一套 `sanitize` 渲染链路），但不复用工作区布局。

### 3.3 公开阅读被登录态阻塞

- 位置：`backend/src/main/java/com/mdreader/config/WebMvcConfig.java`、`backend/src/main/java/com/mdreader/service/impl/FileServiceImpl.java`
- 问题：即使前端新增公开阅读页，匿名用户仍无法获取文件详情和正文；后端拦截器覆盖所有 `/api/**`，服务层也强制当前用户。
- **状态：已修复。**
- 现状证据：
  - 后端新增 `@OptionalAuth`，`GET /api/files`、`/api/files/{id}`、`/api/files/{id}/content`、`/api/files/categories` 匿名可读；服务层 `canRead` 放行匿名用户读 `PUBLIC + PUBLISHED`。
  - 前端新增 `api/public.ts`，以 `guest` 通道调用公开接口。
  - 两侧都做了产品决策：做公开阅读器，登录后进工作台发布。

### 3.4 文件管理有接口、没有 UI

- 位置：`frontend/src/composables/useFileExplorer.ts`、`frontend/src/components/FileTree.vue`、`frontend/src/components/MultiFunctionBar.vue`
- 问题：`renameFileNode`、`moveFileNode`、`deleteFileNode` 已实现并接入后端，但文件树只发出 `select/toggle`，多功能栏只有导入、导出、新建。用户无法重命名、移动或删除文件/文件夹。
- **状态：已修复。**
- 现状证据：
  - `FileTree.vue` 增加右键菜单（`@contextmenu`），提供「重命名 / 移动 / 删除」三个 `role="menuitem"` 操作。
  - 新增 `FileNodeActionDialog.vue`（7 KB）统一承载重命名、移动、删除三类交互，移动支持选择目标文件夹，删除有二次确认。
  - `MultiFunctionBar.vue` 与 `WorkspaceView.vue` 通过 `@rename` / `@move` / `@remove` 事件串联到该对话框。

### 3.5 搜索和目录缺失

- 位置：`MultiFunctionBar.vue`、`FileTree.vue`
- 问题：当前只有文件树，没有文件搜索，也没有文章目录。
- **状态：已修复。**
- 现状证据：
  - 工作区多功能栏顶部新增 `type="search"` 输入框，绑定 `filter` 传给 `FileTree`，`FileTree` 在 `visibleRows` 中做客户端文件名过滤（匹配不到时展开全部祖先节点）。
  - 阅读页通过 `useReading.ts` 的 `extractHeadings` 从 Markdown 提取 1–4 级标题并生成去重 slug，配合 `customAttrs` 回填 `id`，支持锚点跳转与当前章节高亮。

---

## 4. P1：影响阅读体验和工作区可用性

### 4.1 阅读排版设置只有背景

- 位置：`SettingsView.vue`
- 问题：设置页只有浅色、深色、跟随系统和自定义背景；没有字号、行高、内容宽度、字体、段落间距等阅读设置。
- **状态：已修复。**
- 现状证据：新增 `composables/useReading.ts`，提供 `fontSize`、`lineHeight`、`maxWidth`、`codeFontSize` 四项偏好，带 `MIN_VALUES`/`MAX_VALUES` 边界钳制与 `normalize` 容错，持久化到 `localStorage`（key：`md-reader:reading-preferences:v1`）。`SettingsView.vue` 提供滑杆与重置按钮，阅读页通过 `styleVars` CSS 变量实时生效。

### 4.2 预览缺少阅读增强

- 位置：`PreviewPane.vue`
- 问题：目前只有 Markdown 渲染和 Shiki 高亮；图片灯箱、图片懒加载、外链新窗口、标题锚点都没有配置。代码块复制按钮是否可见取决于 `x-markdown-vue` 默认行为。
- **状态：大部分已修复。**
- 已完成：
  - `customAttrs` 已配置外链 `target="_blank"` + `rel="noopener noreferrer"`，图片 `loading="lazy"` + `decoding="async"`。
  - 标题锚点在渲染后由调用方按顺序回填 `id`（`PreviewPane` 的 `heading` 回调返回空对象，避免 `{#id}` 原样显示）。
  - 代码块复制由 `x-markdown-vue` 提供，预览栏文案已统一为「sanitize 已开启 · 代码块支持复制 · 图片懒加载」。
- 仍待处理：**图片灯箱放大未实现**（阅读页与预览均无点击放大交互）。

### 4.3 编辑器与预览使用不同渲染链路

- 位置：`frontend/src/utils/markdown.ts`、`PreviewPane.vue`
- 问题：所见即所得用 `marked` 生成 HTML，预览用 `x-markdown-vue`；再经过 TinyMCE → turndown 回转，表格、任务列表、代码块等格式可能不一致。
- **状态：待处理。**
- 现状：`utils/markdown.ts` 仍保留 `marked`（TinyMCE 正文渲染）与 `turndown`（HTML → Markdown）双向转换，`PreviewPane` 仍走 `x-markdown-vue`，两条链路未合并，也没有“TinyMCE → Markdown → 预览”的往返测试。

### 4.4 图片以 data URL 写入正文，容易超限

- 位置：`WysiwygEditor.vue`
- 问题：粘贴图片和“导入导图”都会把 base64 直接写进 Markdown；单张限制 4 MB，但多张图片叠加后很容易超过后端 10 MB 正文限制，导致保存失败。
- **状态：待处理。**
- 现状：`WysiwygEditor.vue` 仍是 `MAX_IMAGE_SIZE = 4 * 1024 * 1024` 的 data URL 插入（`insertImage`）；后端仍无通用图片上传接口（只有头像 `POST /api/users/me/avatar`）；保存失败时前端也没有「图片使正文超过 10 MB」的明确提示。

### 4.5 保存没有版本/冲突检测

- 位置：`frontend/src/api/files.ts`、`useFileExplorer.ts`、`WorkspaceView.vue`
- 问题：保存是整篇覆盖，没有 `updatedAt`/版本号校验；多标签页或两台设备同时编辑会最后写入者覆盖。云端正文只在首次打开时 hydrate 一次。
- **状态：待处理。**
- 现状：`saveFile()` 仍直接调用 `PUT /api/files/{id}` 覆盖，只处理 404；后端 `UpdateFileDTO` 也没有版本号/`updatedAt` 字段，冲突检测需要前后端一起补。

### 4.6 登录态失效后文件树状态清理不彻底

- 位置：`frontend/src/composables/useUserAuth.ts`、`WorkspaceView.vue`、`useFileExplorer.ts`
- 问题：`useUserAuth.reset()` 只清 Token 和 IndexedDB，不清 `useFileExplorer` 的内存树；`WorkspaceView` 的 watch 只在组件挂载时生效。若在 `/settings`、`/profile` 等页面登录过期，再回到 `/workspace`，`init` 因 `initialized=true` 不再 refresh，可能继续显示上一个账号的文件树。
- **状态：已修复。**
- 现状证据：`useUserAuth.reset()` 现在统一调用 `useFileExplorer().reset()` 清空内存树；`WorkspaceView.vue` 中 `watch` 触发时按 `authenticated` 决定 `explorer.reset()`，登录态失效会立即清空。

### 4.7 Token 存在 localStorage，缺少刷新与跨标签同步

- 位置：`frontend/src/api/http.ts`
- 问题：用户和管理员 Token 都存在 localStorage，XSS 可直接读取；没有 refresh token、没有跨标签页同步、没有主动过期提示。
- **状态：部分修复。**
- 已完成：`useUserAuth.ts` 监听 `storage` 事件，跨标签页登录/退出可同步；Token 失效时清除本地状态并跳转登录页。
- 仍待处理：Token 仍存 localStorage（XSS 可读），没有 refresh token 机制，也没有 httpOnly Cookie 方案。

### 4.8 管理端正文查看与“B 端不读正文”的约定冲突

- 位置：`frontend/src/admin/views/AdminFilesView.vue`
- 问题：文件管理页可以打开正文对话框并读取任意文件内容。
- **状态：已修复（选择“不允许”，按产品约定收敛）。**
- 现状证据：后端 `AdminFileController` 只保留元数据分页，`AdminFileVO` 不含 `mdUrl` 与正文；前端 `AdminFilesView.vue` 已删除正文对话框与 `fetchAdminFileContent` 调用，页面明确提示「B 端不读取、不预览、不下载用户文件正文」。

### 4.9 用户缺少修改密码入口

- 位置：`frontend/src/views/ProfileView.vue`、`frontend/src/api/user.ts`
- 问题：个人信息页只能改昵称、邮箱、头像；没有修改密码。后端也缺接口。
- **状态：已修复。**
- 现状证据：后端新增 `PUT /api/auth/me/password`；前端新增 `ChangePasswordDialog.vue`（资料页内）与 `ChangePasswordView.vue`（独立页 `/change-password`，用于首次强制改密），校验旧密码、新密码、确认密码，成功后清除 Token 跳转登录。

### 4.10 注册密码规则可用性差

- 位置：`frontend/src/views/RegisterView.vue`、`frontend/src/utils/password.ts`
- 问题：注册密码被限制为恰好 10 位且 8 数字 + 2 字母，和管理员重置密码规则不统一。
- **状态：部分修复。**
- 已完成：后端抽出统一 `PasswordPolicy`，注册、改密、管理员重置走同一规则；前端 `utils/password.ts` 同步为同一正则。
- 仍待处理：规则本身仍是最难用的那一版（恰好 10 位、8 数字 + 2 字母），与后端一致但可用性未改善。建议放宽为「长度 + 复杂度」。

### 4.11 无障碍不足

- 位置：`FileTree.vue`
- 问题：文件树声明了 `role="tree"`，但没有方向键导航、Home/End、类型筛选等键盘行为；正文弹窗没有焦点陷阱和关闭后的焦点恢复。
- **状态：部分修复。**
- 已完成：`FileTree.vue` 补齐 `role="tree"`、`aria-expanded`、`aria-selected`、`aria-label`；阅读页提供 skip link 与 `aria-live` 保存状态。
- 仍待处理：文件树仍无方向键/Home/End 导航；`FileNodeActionDialog.vue` 等弹窗仍无焦点陷阱与关闭后焦点恢复。

---

## 5. P2：交付、性能与工程化

### 5.1 没有测试和 lint

- 位置：`frontend/package.json`
- 问题：只有 `dev`、`build`、`preview`、`typecheck`；没有单元测试、组件测试、E2E、ESLint。
- **状态：待处理。**
- 现状：`package.json` 脚本未变，仍无 `vitest`、`@vue/test-utils`、`playwright`、`eslint`。后端已建立集成测试基线（见后端审查 4.1），前端尚无对应工程化。

### 5.2 首屏体积和加载策略

- 现状：`WorkspaceView` 直接引入 TinyMCE、Shiki、marked、turndown；TinyMCE 资源自托管。
- **状态：部分修复。**
- 已完成：`ArticleListView`、`ArticleReaderView`、`RegisterView`、`ChangePasswordView` 等路由已改为懒加载；`App.vue` 只做背景层 + RouterView。
- 仍待处理：`WorkspaceView` 仍为静态引入（未懒加载），TinyMCE、Shiki 仍在工作区入口同步加载。

### 5.3 部署与 SPA 回退

- 位置：`vite.config.ts`
- 问题：Vite 代理只在开发环境生效；生产需要 Nginx 转发 `/api`、`/uploads` 并为 History 路由配置 `try_files ... /index.html`。
- **状态：部分修复。**
- 已完成：`docs/frontend/reader-deploy-2026-10-31.md` 给出生产 Nginx 示例，包含 `/api`、`/uploads` 转发与 `try_files ... /index.html` 回退；前端已做路由懒加载。
- 仍待处理：没有 Dockerfile、CI 构建配置与自动化部署说明；仓库中亦无 Nginx 配置文件实体。

### 5.4 SEO 与分享

- 问题：纯 SPA，没有文章级 `<title>`、description、Open Graph、canonical、sitemap。
- **状态：部分修复。**
- 已完成：阅读页与各页面已动态设置 `document.title`（`ArticleReaderView.vue` 等）。
- 仍待处理：无 `<meta name="description">`、Open Graph、canonical、sitemap；无预渲染/SSR，分享链接无社交摘要。

### 5.5 错误边界

- 位置：`frontend/src/main.ts`
- 问题：没有 `app.config.errorHandler`，单个组件渲染异常可能导致整页空白。
- **状态：待处理。**
- 现状：`main.ts` 仍只有 `createApp(App).use(router).mount('#app')`，无全局错误处理与错误页。

### 5.6 文档与实现不一致

- 位置：`frontend/README.md`
- 问题：README 仍写“C 端文件树、内容、用户信息和保存仍为 Mock/占位”，但当前代码已经接入后端文件树、保存、登录和 IndexedDB。
- **状态：已修复。**
- 现状证据：`frontend/README.md` 已按当前实现重写：路由表更新为 `/`（公开文章列表）、`/read/:id`（阅读页）、`/workspace`、`/login`、`/register`、`/change-password` 等真实路由；删除「占位接口」「Mock 文件树」「保存为 420ms 模拟」等失效描述；补充阅读排版设置、公开阅读链路、生产 Nginx 与已知限制。

---

## 6. 建议迭代顺序

### 阶段 A：阅读器 MVP（已完成）

1. ~~新增文章列表页和阅读详情页。~~ 已完成
2. ~~阅读页接入标题、元信息、正文、目录和上下篇。~~ 标题/元信息/正文/目录已完成，**上下篇未做**
3. ~~增加阅读排版设置（字号、行高、宽度）。~~ 已完成
4. ~~增加加载、空态、错误、移动端适配。~~ 已完成
5. ~~与后端确认公开阅读接口。~~ 已完成（匿名可读公开列表/详情/正文/分类）

### 阶段 B：工作区可用性

1. ~~文件树右键/行内操作：重命名、移动、删除。~~ 已完成
2. ~~文件名搜索、文章搜索。~~ 已完成（工作区客户端过滤 + 列表页后端搜索）
3. 保存冲突检测和“重新加载云端版本”。**待处理**
4. ~~修正登录过期后的文件树清理。~~ 已完成

### 阶段 C：阅读体验增强

1. ~~阅读进度、上次阅读位置。~~ 已完成
2. 收藏/书签、阅读历史。**待处理**
3. ~~代码复制、图片懒加载、外链处理。~~ 已完成（图片灯箱放大未做）
4. 标签/分类、分享、SEO。**部分完成**（分类已有，标签模型与 SEO 元信息待补）

### 阶段 D：工程质量

1. 单元测试 + E2E + ESLint。**待处理**
2. 路由懒加载和体积优化。**部分完成**
3. Nginx/Docker/CI/部署文档。**部分完成**（Nginx 示例已有，Docker/CI 待补）
4. 无障碍检查。**部分完成**

## 7. 验收清单

- [x] `/` 展示文章列表，不再是工作区重定向。
- [x] `/read/:id` 可直接打开并刷新，不 404。
- [x] 阅读页只呈现标题、元信息、正文、目录和阅读操作。
- [x] 目录锚点可跳转，当前章节高亮。
- [x] 搜索能按标题过滤文章。
- [x] 字号、行高、内容宽度设置可持久化。
- [x] 代码块可复制，外链安全打开，图片懒加载。
- [ ] 图片点击放大（灯箱）未实现。
- [x] 移动端 390px 无横向溢出，正文可读。
- [x] 加载态、空态、接口失败重试完整。
- [x] 文件树可重命名、移动、删除。
- [ ] 多标签页同时保存时能发现冲突（缺版本号/`updatedAt` 条件更新）。
- [x] 登录过期或退出后不会残留上一个账号的文件树/正文。
- [x] Markdown XSS 样例在阅读页不会执行（`sanitize` 开启）。
- [ ] 图片较多的文档不会无声超过正文大小限制（仍为 base64 内联）。
- [x] 管理端正文访问策略与产品约定一致（管理端只读元数据）。
- [ ] `npm run typecheck`、`npm run build`、单元测试和 E2E 全部通过（typecheck/build 通过，无前端测试）。
- [ ] 生产环境 `/api`、`/uploads` 代理和 SPA 回退配置完成（Nginx 示例已给出，Docker/CI 未做）。
- [ ] 全局错误边界与错误页。
- [ ] 文章上下篇 / 相关推荐。
- [ ] 收藏、阅读历史。

## 8. 需要后端配合的事项

- ~~公开阅读：匿名可读的公开文件列表/详情/正文接口。~~ 已提供（`@OptionalAuth` + `/api/files/categories`）
- ~~搜索：标题搜索。~~ 已支持 `GET /api/files?keyword=`；工作区为客户端过滤
- 阅读进度、收藏、历史：需要新增数据表和接口（当前阅读进度仅存浏览器 localStorage）。
- 标签/分类：分类字段与计数接口已具备；标签模型仍需新增。
- 保存冲突：`PUT /api/files/{id}` 增加版本号/`updatedAt` 条件更新，冲突返回 409。**待后端与前端一起做。**
- ~~自助改密：`PUT /api/auth/me/password`。~~ 已提供
- ~~管理端正文策略。~~ 已确认不允许，改为元数据 VO
- 图片上传接口（正文插图）：后端需提供通用图片上传接口，正文只存 URL。**待补。**
