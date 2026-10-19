# Kafka 订单异步处理 —— 部署成功报告

> 日期：2026-10-19 ｜ 集群：kind `user-auth-cluster`（k8s v1.34.0） ｜ 镜像源：腾讯云 TCR + daocloud 加速

## 一、项目目标

在现有 `user-auth-demo`（Spring Boot 3 + MariaDB + Redis + JWT，已部署在 kind K8S）之上，补齐 **MQ / Kafka 缺口**：
用 **订单异步处理** 场景演示消息队列的「异步解耦 + 削峰」——下单后主流程立即返回，支付/状态更新由 Kafka 消费者异步完成。

## 二、最终架构

```
客户端
  │  POST /api/orders/create（带 JWT）
  ▼
app(Spring Boot) ──OrderController──► OrderService（落库 status=PENDING）
  │                                      │
  │ OrderProducer.sendOrderCreated       │ 立即返回 orderId
  ▼                                      │
Kafka topic: order-events (KRaft 单节点) ◄┘
  │
  ▼
OrderConsumer（@KafkaListener）
  │  模拟处理 1s
  ▼
OrderService.markPaid → 数据库 status: PENDING → PAID
```

## 三、K8S 部署现状（全部 Running）

| 组件 | Pod | READY | 说明 |
|------|-----|-------|------|
| app | app-6c94b88fd4 | 1/1 Running | 含 Kafka 代码的新镜像 |
| mysql(MariaDB) | mysql-… | 1/1 Running | 复用，新增 t_order 表 |
| redis | redis-… | 1/1 Running | 复用 |
| kafka | kafka-6f6886bdb | 1/1 Running | apache/kafka 3.7 KRaft 单节点 |

- `kubectl get endpoints kafka` → `10.244.0.10:9092`（已就绪路由）
- 健康检查 `/actuator/health` → **UP**（db=MySQL UP、redis 7.4.11 UP）

## 四、验证结果（订单异步链路打通）

| 步骤 | 结果 |
|------|------|
| 登录（learn_user2） | ✅ 拿到有效 JWT |
| POST /api/orders/create（userId=1, amount=99.5） | ✅ 返回 orderId=2, status=PENDING |
| 立即 GET /api/orders/2 | ✅ status=PENDING（主流程先落库） |
| 等待 2.5 秒后 GET /api/orders/2 | ✅ **status=PAID**（Kafka 消费者异步更新） |
| kafka topic order-events offset | ✅ =2（消息已被生产并被消费者消费） |

> 关键证据：订单状态由 **PENDING → PAID** 只有 Kafka 消费者会触发（`OrderService.markPaid`），且 topic offset 前进，证明生产者-主题-消费者全链路真实工作，不是假验证。

## 五、新增代码（com.demo.user.kafka + order 模块）

| 文件 | 职责 |
|------|------|
| `kafka/KafkaConfig.java` | 定义 topic `order-events`（1 分区 1 副本） |
| `kafka/OrderProducer.java` | 生产者：发订单创建消息（key=orderId） |
| `kafka/OrderConsumer.java` | 消费者：收到消息→`markPaid` 异步改状态 |
| `entity/Order.java` | t_order 实体 |
| `repository/OrderRepository.java` | JPA 仓库 |
| `service/OrderService.java` | createOrder / markPaid / getOrder |
| `controller/OrderController.java` | POST /api/orders/create、GET /api/orders/{id} |
| `application.yml` | `spring.kafka.bootstrap-servers=${KAFKA_HOST:kafka}:9092` |
| `k8s/kafka.yaml` | Kafka Deployment+Service（KRaft） |

## 六、镜像方案（关键决策）

1. **bitnami/zookeeper + bitnami/kafka 拉不到**：daocloud/1ms.run 对 bitnami 命名空间返回 `denied`/`not found`。
   → **改用 `apache/kafka:3.7.0`**（daocloud 有缓存，拉取成功）。
2. **apache/kafka 3.7 官方镜像只支持 KRaft**（日志：`Formatting is only supported for clusters in KRaft mode`），不再支持 Zookeeper 模式。
   → **采用 KRaft 单节点**（controller+broker 合一），这是 Kafka 3.x 新标准，去掉了 zookeeper，反而更简洁、简历更显技术新。
3. 镜像全部 tag 到腾讯云 `ccr.ccs.tencentyun.com/279129867/`，`imagePullPolicy: IfNotPresent`。

## 七、踩坑与解决

| 问题 | 现象 | 解决 |
|------|------|------|
| kind load 多架构镜像失败 | `ctr: content digest ... not found` | app/kafka 均带 multi-arch index（含 attestation），kind `--all-platforms` 缺 blob。改为 `docker save` → 节点内 `ctr images import`（stdin 重定向，不用 `--all-platforms`） |
| docker cp 静默失败 | 传 tar 到节点后文件不存在 | 放弃 docker cp，改用 `docker exec -i ... ctr import - < tar` |
| kafka readiness 探测失败 | Pod Running 但 0/1 | exec probe 命令在镜像里退出码不可靠，改为最稳的 `tcpSocket: 9092` |
| Docker Desktop 重启后 context 漂移 | kubectl 指向 `docker-desktop`（废弃内置集群） | `kubectl config use-context kind-user-auth-cluster` 切回 |
| 创建订单 403 | Spring Security 拦截 `/api/orders/**` | 登录后带 `Authorization: Bearer <token>` 调用 |

## 八、核心命令清单（复现用）

```powershell
$env:PATH = "C:\Program Files\Docker\Docker\resources\bin;$env:PATH"
kubectl config use-context kind-user-auth-cluster

# 构建含 Kafka 的新 app 镜像
docker build -t ccr.ccs.tencentyun.com/279129867/user-auth-demo:latest .

# 拉取 kafka（走 daocloud mirror），打成 ccr tag
docker pull apache/kafka:3.7.0
docker tag apache/kafka:3.7.0 ccr.ccs.tencentyun.com/279129867/kafka:3.7

# 导入多架构镜像（kind load 会失败，用 ctr import）
docker save ccr.ccs.tencentyun.com/279129867/kafka:3.7 -o kafka.tar
docker exec -i user-auth-cluster-control-plane ctr -n k8s.io images import - < kafka.tar
docker save ccr.ccs.tencentyun.com/279129867/user-auth-demo:latest -o app.tar
docker exec -i user-auth-cluster-control-plane ctr -n k8s.io images import - < app.tar

# 部署
kubectl apply -f k8s/kafka.yaml
kubectl rollout restart deployment/app -n user-auth-demo

# 验证
kubectl get pods -n user-auth-demo
kubectl port-forward -n user-auth-demo svc/app 18080:8080   # 单独窗口
# 登录 → 创建订单 → 查状态 PENDING→PAID
```

## 九、小结

- Kafka（KRaft）已成功跑在 kind K8S 上，与 MariaDB、Redis、app 组成完整微服务链路。
- 订单异步处理真实工作：主流程立即返回，消费者异步把 PENDING 更新为 PAID，topic offset 佐证消息收发。
- 补齐了简历上「MQ / Kafka」缺口，且用上了 Kafka 3.7 的 KRaft 新特性。
- 镜像源完全走国内（腾讯云 TCR + daocloud），无需 VPN，稳定可复现。
