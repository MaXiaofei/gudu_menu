# 咕嘟小食单 · 环境层更名运维手册（yanhuo → gudu）

> 代码/文档层已全量改为 gudu。本手册处理**运行环境层**（数据库/容器/卷/服务器目录）——这些改代码不会自动同步，必须在服务器手动操作。

## 执行前必做
1. 备份数据库：
   docker exec yanhuo-mysql-prod mysqldump -uroot -p<密码> --all-databases > /root/backup_pre_rename.sql
2. ssh 核对当前实际（代码已 gudu，环境可能仍 yanhuo 或半迁）：
   - ssh root@49.232.3.201 'docker ps --format "{{.Names}}" | grep -iE "yanhuo|gudu"'
   - ssh root@49.232.3.201 'docker exec yanhuo-mysql-prod mysql -uroot -p<密码> -e "SHOW DATABASES"'
   - ssh root@49.232.3.201 'ls -d /root/yanhuo /root/gudu 2>/dev/null'

## 涉及项
| 层 | 旧 | 新 |
|---|---|---|
| MySQL 库 | yanhuo / yanhuo_test | gudu / gudu_test |
| Docker 容器 | yanhuo-mysql/redis/minio(-prod) | gudu-* |
| Docker 卷 | yanhuo_mysql-data | gudu_mysql-data |
| 服务器目录 | /root/yanhuo/ | /root/gudu/ |
| 配置文件 | docker-compose*.yml / application-test.yml / application-prod.yml / docs/deploy.md | 改 gudu |

## 迁移步骤（生产·低峰期·先备份）
1. 数据库：CREATE DATABASE gudu; → mysqldump yanhuo | mysql gudu，验证后保留 yanhuo 作回滚
2. 配置文件（本仓库）：docker-compose.prod.yml 容器名、application-prod/test.yml 的 jdbc 库名、docs/deploy.md 路径，全改 gudu
3. 重建容器：docker compose -f docker-compose.prod.yml down && up -d（卷改名需先迁移数据）
4. 目录：停服后 mv /root/yanhuo /root/gudu
5. .env：连接串/库名改 gudu
6. 验证：api 启动 + 接口连通 + 后台/小程序能登录

## 风险
- 重命名库/卷有数据丢失风险，必须先备份，建议本地先试
- docker-compose.yml（本地）注释说 yanhuo-deps 已删，先确认是否还用
- deploy.md 提示生产 service name 已 gudu-mysql（可能部分已迁），核对清楚再动
