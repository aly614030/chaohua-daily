# 超话数据日报

零成本方案：GitHub Pages 提供公开网页，GitHub Actions 每天北京时间 23:30 自动采集并发布。

首次启用只需在仓库 `Settings → Secrets and variables → Actions` 新建 `WEIBO_COOKIE`，并在 `Settings → Pages` 将 Source 设为 `GitHub Actions`。之后无需人工更新。

如果某次微博返回字段不完整，任务会失败并保留上一份有效数据，避免页面显示错误或空值。

## 自行增删超话

在 GitHub 打开 `config/topics.json`，点击铅笔按钮编辑：

- 暂停某个超话：把对应的 `"enabled": true` 改成 `false`。
- 删除某个超话：删除对应的整行对象。
- 增加超话：复制一行，修改 `name` 和微博超话链接中以 `100808` 开头的 `id`。

提交后 GitHub 会自动重新发布；次日 23:30 按新名单采集。五项指标名称集中列在同一文件的 `metrics` 中；指标结构变化涉及采集与排名页面时，可以直接告诉我修改。
