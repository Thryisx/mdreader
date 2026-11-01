# 阅读器 MVP 部署说明（2026-10-31）

本次已补齐阶段 A 阅读器 MVP 的前端链路：`/` 为公开文章列表，
`/read/:id` 为独立阅读页。公开接口无需登录即可访问。

## 生产 Nginx 示例

```nginx
server {
  listen 80;
  server_name example.com;
  root /var/www/md-reader;
  index index.html;

  location /api/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
  }

  location /uploads/ {
    proxy_pass http://127.0.0.1:8080;
  }

  location / {
    try_files $uri $uri/ /index.html;
  }
}
```

关键点：

- `/api` 与 `/uploads` 转发到后端，避免跨域。
- `try_files ... /index.html` 保证 `/read/:id` 直接刷新不 404。
- 前端已做路由懒加载，阅读页不会阻塞首屏。

## 验证

- `npm run typecheck` 通过。
- `npm run build` 通过。
