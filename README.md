# 用户认证系统 + DevOps 运维平台

Spring Boot 3 用户认证项目，配套完整的监控告警、CI/CD 流水线和自动化运维脚本。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Spring Boot 3 + Spring Security + JWT + MyBatis-Plus |
| 中间件 | MySQL 8 + Redis 7 |
| 监控 | Prometheus + Grafana + Alertmanager |
| CI/CD | Jenkins Pipeline + Gitee |
| 容器化 | Docker + Docker Compose |
| 镜像仓库 | 腾讯云容器镜像服务 TCR |

## 项目架构

```
┌─────────┐     ┌──────────┐     ┌─────────┐
│  MySQL  │◄────│  Spring  │────►│  Redis  │
└─────────┘     │   Boot   │     └─────────┘
                 │   :8080  │
                 └────┬─────┘
                      │
         ┌────────────┼────────────┐
         ▼            ▼            ▼
   ┌──────────┐ ┌──────────┐ ┌────────────┐
   │Prometheus│ │ Grafana  │ │Alertmanager│
   │  :9090   │ │  :3000   │ │  :9093     │
   └──────────┘ └──────────┘ └────────────┘
```

## 一键启动

```bash
docker compose up -d
```

| 服务 | 地址 | 账号密码 |
|---|---|---|
| 应用 | http://localhost:8080 | - |
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Alertmanager | http://localhost:9093 | - |
| Jenkins | http://localhost:8081 | 首次启动设置 |

## 核心功能

### 1. 用户认证
- 注册 / 登录（JWT Token）
- Spring Security 接口拦截
- Redis 缓存用户信息

### 2. 监控告警
- Prometheus 采集应用、MySQL、Redis、主机指标
- Grafana 可视化仪表盘（Node Exporter + JVM Micrometer）
- Alertmanager 邮件告警（QQ 邮箱 SMTP）
- 告警规则：服务宕机 / CPU 过高 / 磁盘不足

### 3. CI/CD 流水线
- Jenkins Pipeline 自动拉取 Gitee 代码
- Maven 构建 → Docker 镜像打包
- 推送至腾讯云 TCR 镜像仓库
- 触发方式：push 代码自动触发

### 4. 自动化运维脚本
| 脚本 | 功能 |
|---|---|
| `scripts/healthcheck.sh` | 检查所有服务健康状态 |
| `scripts/backup.sh` | MySQL 数据库备份，保留 7 天 |
| `scripts/deploy.sh` | 一键拉代码 + 构建 + 重启 |
| `scripts/rollback.sh` | 回退到上一个版本 |

## 告警验证

```bash
# 触发告警（停掉应用）
docker stop demo-app

# 等待 10 秒，Prometheus firing，收到告警邮件

# 恢复（启动应用）
docker start demo-app
# 收到恢复邮件
```

## 项目结构

```
user-auth-demo/
├── src/main/java/com/demo/        # Spring Boot 源码
├── prometheus/                     # Prometheus 配置
│   ├── prometheus.yml
│   └── alert_rules.yml            # 告警规则
├── alertmanager/                   # 邮件告警配置
├── scripts/                        # 自动化运维脚本
├── Dockerfile                      # 应用镜像
├── Dockerfile.jenkins              # Jenkins 镜像（含 Maven）
├── Jenkinsfile                     # CI/CD 流水线定义
└── docker-compose.yml              # 全部服务编排
```

## 效果展示

### Grafana 监控仪表盘
![Grafana](docs/screenshots/grafana-dashboard.png)

### Prometheus 告警规则
![Prometheus Alerts](docs/screenshots/prometheus-alerts.png)

### Jenkins 构建流水线
![Jenkins](docs/screenshots/jenkins-build.png)

### 邮件告警通知
![Email Alert](docs/screenshots/email-alert.png)

## 学习要点

1. **Docker Compose 多服务编排**：10 个容器一键启动
2. **Prometheus 指标采集**：应用 + 中间件 + 主机
3. **Grafana 仪表盘**：可视化监控
4. **Alertmanager 告警**：邮件通知 + 恢复通知
5. **Jenkins Pipeline**：代码 push 自动构建
6. **Shell 脚本自动化**：备份、部署、回滚、健康检查
