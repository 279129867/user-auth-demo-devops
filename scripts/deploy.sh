#!/bin/bash
# 一键部署脚本：拉最新代码 → 构建 → 启动
echo "===== 开始部署 $(date) ====="

# 拉取最新代码
echo "[1/4] 拉取最新代码..."
git pull origin main

# 构建镜像
echo "[2/4] 构建 Docker 镜像..."
docker compose build app

# 重启服务
echo "[3/4] 重启服务..."
docker compose up -d

# 等待健康检查
echo "[4/4] 等待服务启动..."
sleep 15

# 验证
APP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health)
if [ "$APP_STATUS" = "200" ]; then
    echo "===== 部署成功! ====="
else
    echo "===== 部署后服务未就绪，请检查日志 ====="
    docker logs demo-app --tail 20
fi
