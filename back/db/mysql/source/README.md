# MySQL 构建片段

此目录存放 `tools/build-mysql-sql.mjs` 使用的 MySQL 方言数据片段。

这些文件用于重新生成 `../umm_oa.sql`，需要提交到 Git 仓库长期保存。
部署脚本会自动排除本目录，服务器只接收最终导入文件 `../umm_oa.sql`。
