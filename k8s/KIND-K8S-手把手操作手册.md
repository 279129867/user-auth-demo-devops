# Kind + Kubernetes 部署 user-auth-demo 手把手操作手册

> 目标：用标准 `kind` 自建 K8S 集群，配国内源（daocloud），把 user-auth-demo（Spring Boot + MariaDB + Redis）完整部署跑通，**全程不联网、免 VPN**。
> 前置：Docker Desktop 已装（内置 Docker Engine），本机已有 `kind.exe` / `kubectl.exe`。

---

## 〇、工具位置（你的机器）

```powershell
# Docker / kubectl / kind 都在同一目录
$env:PATH = "C:\Program Files\Docker\Docker\resources\bin;$env:PATH"
```

---

## 一、确认前置镜像都在本机（不用联网拉）

```powershell
# 这些镜像必须在本机存在（这次项目全部用本地镜像）
docker images
# 关键看：kindest/node、mariadb:10.11、redis:7-alpine、user-auth-demo-app
```

如果缺，先构建/拉取 app 镜像（本机构建）：
```powershell
cd C:\Users\Administrator\Doubao\chats\2026-10-13\new-chat\user-auth-demo
docker build -t user-auth-demo-app:latest .
```

---

## 二、创建 kind 集群（配置文件自带 daocloud 国内源）

**配置文件 `k8s\kind-config.yaml`**（已保存）：
```yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
name: user-auth-cluster
nodes:
  - role: control-plane
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."docker.io"]
      endpoint = ["https://docker.m.daocloud.io", "https://docker.1ms.run"]
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."registry.k8s.io"]
      endpoint = ["https://docker.m.daocloud.io"]
```

创建集群：
```powershell
kind create cluster --config "C:\...\user-auth-demo\k8s\kind-config.yaml"
# 成功标志：Set kubectl context to "kind-user-auth-cluster"
```

> ⚠️ kind 可能默认用 kindest/node:v1.34.0，没关系，只要能 Ready。

验证：
```powershell
kubectl get nodes        # 应显示 Ready
kubectl cluster-info
```

---

## 三、给节点 containerd 真正配 daocloud（关键一步）

> kind 节点的 containerd 是 v2.x，旧式 `mirrors` 配置**已废弃**，必须用 **`config_path`（certs.d）** 方式。且通过 `docker exec` 写文件容易失败，**要用 `docker cp`**。

### 1. 节点容器名
```powershell
docker ps --format "{{.Names}}"   # 找到 user-auth-cluster-control-plane
```

### 2. 修改节点 config.toml：把 registry 段改为 config_path

**用 printf 写入（避免 heredoc 编码问题）**：
```powershell
$node = "user-auth-cluster-control-plane"
docker exec $node sh -c 'printf "version = 2\n\n[plugins]\n  [plugins.\"io.containerd.grpc.v1.cri\"]\n    restrict_oom_score_adj = false\n    sandbox_image = \"registry.k8s.io/pause:3.10\"\n    tolerate_missing_hugepages_controller = true\n    [plugins.\"io.containerd.grpc.v1.cri\".containerd]\n      default_runtime_name = \"runc\"\n      discard_unpacked_layers = true\n      snapshotter = \"overlayfs\"\n      [plugins.\"io.containerd.grpc.v1.cri\".containerd.runtimes]\n        [plugins.\"io.containerd.grpc.v1.cri\".containerd.runtimes.runc]\n          base_runtime_spec = \"/etc/containerd/cri-base.json\"\n          runtime_type = \"io.containerd.runc.v2\"\n          [plugins.\"io.containerd.grpc.v1.cri\".containerd.runtimes.runc.options]\n            SystemdCgroup = true\n        [plugins.\"io.containerd.grpc.v1.cri\".containerd.runtimes.test-handler]\n          base_runtime_spec = \"/etc/containerd/cri-base.json\"\n          runtime_type = \"io.containerd.runc.v2\"\n          [plugins.\"io.containerd.grpc.v1.cri\".containerd.runtimes.test-handler.options]\n            SystemdCgroup = true\n    [plugins.\"io.containerd.grpc.v1.cri\".registry]\n      config_path = \"/etc/containerd/certs.d\"\n\n[proxy_plugins]\n  [proxy_plugins.fuse-overlayfs]\n    address = \"/run/containerd-fuse-overlayfs.sock\"\n    type = \"snapshot\"\n" > /etc/containerd/config.toml && echo WRITE_OK'
```

### 3. 写 certs.d/docker.io/hosts.toml（配 daocloud）

**在宿主机建文件，再 docker cp 进节点（最可靠）**：

宿主机建 `hosts.toml`（已保存在 k8s\hosts.toml）：
```toml
server = "https://registry-1.docker.io"

[host."https://docker.m.daocloud.io"]
  capabilities = ["pull", "resolve", "push"]
  skip_verify = true

[host."https://docker.1ms.run"]
  capabilities = ["pull", "resolve", "push"]
  skip_verify = true
```

拷进节点：
```powershell
$node = "user-auth-cluster-control-plane"
docker exec $node mkdir -p /etc/containerd/certs.d/docker.io
docker cp "C:\...\user-auth-demo\k8s\hosts.toml" "${node}:/etc/containerd/certs.d/docker.io/hosts.toml"
# 验证
docker exec $node cat /etc/containerd/certs.d/docker.io/hosts.toml
```

### 4. 重启 containerd 让配置生效
```powershell
$node = "user-auth-cluster-control-plane"
docker exec $node systemctl restart containerd
```

---

## 四、把本机 app 镜像加载进集群（免联网）

```powershell
kind load docker-image user-auth-demo-app:latest --name user-auth-cluster
# 验证（应能看到 docker.io/library/user-auth-demo-app:latest）
docker exec user-auth-cluster-control-plane ctr -n k8s.io images list
```

> ⚠️ 若 kind load 报 "content digest not found"，是因为镜像有多架构 manifest。改用单一架构镜像，或直接用本机构建的 `user-auth-demo-app:latest`。

---

## 五、部署项目

```powershell
# 部署 MariaDB + Redis（mysql-redis.yaml 用的是 mariadb:10.11 / redis:7-alpine，走 daocloud 国内源）
kubectl apply -f "C:\...\user-auth-demo\k8s\mysql-redis.yaml"

# 部署 App（app.yaml 镜像已改为 user-auth-demo-app:latest）
kubectl apply -f "C:\...\user-auth-demo\k8s\app.yaml"

# 查看 Pod
kubectl get pods -n user-auth-demo
```

等全部 `1/1 Running`（可能需要 1-3 分钟，MariaDB 首次初始化较慢）。

> 若 mariadb/redis 镜像在 daocloud 拉取慢，可观察：
> `docker exec user-auth-cluster-control-plane journalctl -u containerd --no-pager | grep -i pull`

---

## 六、验证整条链路（注册 → 登录 → JWT → Redis 缓存）

### 1. 启动 port-forward（本机 18080 → app 8080）
```powershell
kubectl port-forward -n user-auth-demo svc/app 18080:8080
```

### 2. 健康检查（db / redis 应 UP）
```powershell
curl http://127.0.0.1:18080/actuator/health
```

### 3. 注册 → 登录 → 带 token 访问
```powershell
# 注册
curl -X POST http://127.0.0.1:18080/api/auth/register -H "Content-Type: application/json" -d "{\"username\":\"k8s_demo_01\",\"password\":\"pass123\",\"nickname\":\"K8sDemo\"}"

# 登录拿 JWT
curl -X POST http://127.0.0.1:18080/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"k8s_demo_01\",\"password\":\"pass123\"}"

# 带 token 访问用户信息（token 用上面返回值）
curl http://127.0.0.1:18080/api/users/me -H "Authorization: Bearer <token>"
```

### 4. 确认 Redis 缓存
```powershell
kubectl exec -n user-auth-demo deploy/redis -- redis-cli keys '*'
# 应看到 user::2（用户缓存键）
```

---

## 七、清理 / 重启速查

```powershell
# 删除集群
kind delete cluster --name user-auth-cluster

# 重启集群（节点配置会丢，需重配 daocloud）
kind delete cluster --name user-auth-cluster
kind create cluster --config "C:\...\user-auth-demo\k8s\kind-config.yaml"
# 然后重复第三、四、五步
```

---

## 关键教训（这次踩过的坑）

1. **Docker Desktop 内置 K8S 无法配国内源**——它的 registry-mirror 私有代理连境外会 500，且 containerd 配置被强制接管，改不了。
2. **标准 kind 自建集群 containerd 可自由配置**——这是免 VPN 的正解。
3. **containerd v2 的 `mirrors` 配置已废弃**——必须用 `config_path`（certs.d）方式。
4. **`docker exec` 写文件易失败（编码/heredoc）**——用 `docker cp` 从宿主机拷贝最可靠。
5. **私有仓库镜像（腾讯云 401）**——用本机已有镜像 `kind load` 喂进集群。
6. **`ctr` 命令拉取不读 CRI 的 certs.d 配置**——别用 ctr 验证 daocloud，要看 Pod 实际拉取。
7. **本机 kind load 的镜像，Pod 仍可能去 daocloud 拉（403）**——因为 daocloud 配置让 docker.io 走国内源，而 `user-auth-demo-app` 不存在于 daocloud。**解决办法：给 app 加 `imagePullPolicy: IfNotPresent`**（镜像存在就用本地，不去 registry 拉）。app.yaml 已配好。
8. **私有/本地镜像必须 `kind load` 进集群**，否则 kubelet 无法用（会去 registry 拉而失败）。
