# 超话数据日报

零成本方案：GitHub Pages 提供公开网页，GitHub Actions 每天北京时间 23:30 自动采集并发布。

首次启用只需在仓库 `Settings → Secrets and variables → Actions` 新建 `WEIBO_COOKIE`，并在 `Settings → Pages` 将 Source 设为 `GitHub Actions`。之后无需人工更新。

如果某次微博返回字段不完整，任务会失败并保留上一份有效数据，避免页面显示错误或空值。
