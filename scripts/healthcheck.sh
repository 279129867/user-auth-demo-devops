#!/bin/bash
# 健康检查脚本：检查所有服务是否正常
echo "===== 健康检查 $(date) ====="

# 检查 app
APP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health)
if [ "$APP_STATUS" = "200" ]; then
    echo "[OK]    应用服务 (8080) 正常"
else
    echo "[FAIL]  应用服务 (8080) 异常，HTTP $APP_STATUS"
fi

# 检查 MySQL
docker exec demo-mysql mysqladmin ping -uroot -proot > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "[OK]    MySQL (3307) 正常"
else
    echo "[FAIL]  MySQL (3307) 异常"
fi

# 检查 Redis
docker exec demo-redis redis-cli ping | grep -q PONG
if [ $? -eq 0 ]; then
    echo "[OK]    Redis (6379) 正常"
else
    echo "[FAIL]  Redis (6379) 异常"
fi

# 检查 Prometheus
PROM_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:9090/-/healthy)
if [ "$PROM_STATUS" = "200" ]; then
    echo "[OK]    Prometheus (9090) 正常"
else
    echo "[FAIL]  Prometheus (9090) 异常"
fi

# 检查 Grafana
GRAFANA_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/api/health)
if [ "$GRAFANA_STATUS" = "200" ]; then
    echo "[OK]    Grafana (3000) 正常"
else
    echo "[FAIL]  Grafana (3000) 异常"
fi

echo "===== 检查完成 ====="
