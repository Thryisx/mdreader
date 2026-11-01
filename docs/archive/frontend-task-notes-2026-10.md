# 前端任务记录（2026-10）

> 归档说明：本文原为 `docs/C端需要的接口.md`，是一次性前端任务清单，不是接口契约。接口定义请以 `docs/api/` 下文档为准。

## 任务一
- 我们以这个标签：<link rel="icon" type="image/svg+xml" href="/favicon.svg">
- 作为我们的logo，作用在class=entry__logo中

## 任务二
- C端也需要实现登录页面，页面可以复用B端已有的，我们添加角色校验就行。
- 并且可以在本地编辑 Markdown 并自动暂存到 localStorage，点击保存时拦截并弹出登录框。登录成功后自动将本地暂存数据同步到云端。请给出前端的核心拦截逻辑代码和后端的接口鉴权策略简述。

## 任务三
- workspace__eyebrow这个类中要么支持点击文件名跳转，要么就不需要这一层<p>标签
- 因为这层和底下的标题标签重复了。

## 任务四
- tox-tbtn__select-label类的功能是保存文件，而workspace__save类也是提供一样的功能。
- 删掉其他tox-tbtn__select-label类吧

## 任务五
- user-bar类同时绑定登录按钮，首次登录都弹出框显示请登录（但是允许被关掉），保存功能必须登录才能使用。