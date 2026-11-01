# MD Reader 文档索引

> 更新日期：2026-11-01
> 目录约定：项目 Markdown 文档统一放在 `docs/`，根目录不再保留零散文档。
> 事实来源：接口与行为以 `docs/api/backend-api-v1.0.md` 和 `backend/`、`frontend/` 实际代码为准；`docs/design/` 是需求与设计基线，部分 TBD 已被后续实现覆盖。

## 1. 快速入口

| 文档 | 用途 | 状态 |
|---|---|---|
| [后端接口与数据库字段 v1.7](api/backend-api-v1.0.md) | 接口、字段、错误码、DDL、索引、种子数据 | 已实现，持续维护 |
| [前端接口需求与字段清单 v1.1](api/frontend-api-requirements-v1.0.md) | 前端已接入/待接入接口、字段映射 | 已同步至当前实现 |
| [接口契约：当前用户文件列表 v1.0](api/接口契约-当前用户文件列表-v1.0.md) | `GET /api/files/list` 的详细契约 | 已实现 |
| [MD Reader 需求文档 v1.0](design/MD-Reader需求文档-v1.0.md) | C 端工作区需求基线 | 设计基线 |
| [B 端管理后台需求文档 v1.0](design/B端管理后台需求文档-v1.0.md) | 管理后台需求基线 | 设计基线 |
| [前端实现规格说明书](design/frontend-implementation-spec.md) | 设计图解析、页面结构、交互规格 | 设计基线 |
| [后端代码审查 2026-10-31](backend/code-review-2026-10-31.md) | 后端上线前问题清单与修改建议 | 复核中：P0 全部修复，P1/P2 部分修复 |
| [前端问题与阅读器差距 2026-10-31](frontend/frontend-reader-gap-2026-10-31.md) | 阅读器能力差距、交互/工程质量问题与迭代顺序 | 复核中：阶段 A/B 主要项已修复 |
| [项目就绪度评估 2026-11-01](project-readiness-2026-11-01.md) | 可行/可使用/可上线/可交付判定与上线前清单 | 当前结论：可使用、可交付，生产上线有条件 |
| [主要缺口与改造方案 2026-11-01](project-gap-remediation-2026-11-01.md) | 可视化缺口、文件级落点、OSS 图片方案与排期 | 待执行 |
| [阅读器 MVP 部署说明 2026-10-31](frontend/reader-deploy-2026-10-31.md) | 生产 Nginx 配置与 SPA 回退示例 | 已实现 |
| [公开文章页面接口清单 2026-10-31](frontend/公开文章页面接口清单-2026-10-31.md) | 公开文章列表页的接口与字段映射 | 已实现 |
| [Spring Boot 经典包结构](backend/spring-package-structure.md) | 通用包结构参考，不是当前项目清单 | 参考 |
| [前端任务记录 2026-10](archive/frontend-task-notes-2026-10.md) | 已完成的零散前端任务记录 | 归档 |

## 2. 目录结构

```text
docs/
├── README.md                              # 本索引
├── project-readiness-2026-11-01.md         # 项目就绪度总评
├── project-gap-remediation-2026-11-01.md   # 主要缺口、OSS 图片与改造落点
├── api/                                   # 接口契约与字段文档
│   ├── backend-api-v1.0.md
│   ├── frontend-api-requirements-v1.0.md
│   └── 接口契约-当前用户文件列表-v1.0.md
├── backend/                               # 后端工程文档
│   ├── code-review-2026-10-31.md
│   └── spring-package-structure.md
├── frontend/                              # 前端工程文档
│   ├── frontend-reader-gap-2026-10-31.md
│   ├── reader-deploy-2026-10-31.md
│   └── 公开文章页面接口清单-2026-10-31.md
├── design/                                # 需求与设计基线
│   ├── MD-Reader需求文档-v1.0.md
│   ├── B端管理后台需求文档-v1.0.md
│   └── frontend-implementation-spec.md
└── archive/                               # 历史任务与一次性记录
    └── frontend-task-notes-2026-10.md
```

## 3. 维护约定

- 接口路径、参数、响应、错误码或数据库结构变更时，同步更新 `docs/api/backend-api-v1.0.md`。
- 前端字段或调用方式变化时，同步更新 `docs/api/frontend-api-requirements-v1.0.md`。
- 新增接口契约单独放在 `docs/api/`，文件名使用 `接口契约-<主题>-v<版本>.md`。
- 需求或设计决策放在 `docs/design/`；实现完成后回写“已实现/已废弃”状态，避免文档与代码不一致。
- 一次性任务记录、临时排查结论放在 `docs/archive/`，不要留在根目录。
- 后端代码审查、上线检查等阶段性文档放在 `docs/backend/`；前端问题、阅读器差距、交互审查放在 `docs/frontend/`，文件名带日期。
- 阶段性文档解决后，在对应条目上更新状态（待处理/进行中/已完成），不要直接删除历史结论。
