#!/bin/bash
# 一键回滚脚本：回退到上一个 git 版本
echo "===== 开始回滚 $(date) ====="

# 查看当前版本
CURRENT=$(git log --oneline -1)
echo "当前版本: $CURRENT"

# 回退一个版本
echo "回退到上一个版本..."
git reset --hard HEAD~1

# 重新构建启动
echo "重新构建并启动..."
docker compose build app
docker compose up -d

sleep 15
APP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health)
if [ "$APP_STATUS" = "200" ]; then
    echo "===== 回滚成功! ====="
else
    echo "===== 回滚后服务未就绪，请检查日志 ====="
fi
