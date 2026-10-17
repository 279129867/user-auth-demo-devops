# user-auth-demo 部署成功报告（腾讯云 TCR 方案）

> **本次成果**：用「标准 kind 自建集群 + 腾讯云 TCR 免费私有仓库」把 user-auth-demo（Spring Boot + MariaDB + Redis + JWT）完整部署跑通，注册/登录/JWT 鉴权/Redis 缓存全链路验证成功。
> **相比公共源（daocloud/1ms.run）**：腾讯云 TCR 是国内云厂商自营，稳定、免运维，彻底摆脱公共源抽风问题。

---

## 一、最终结果（链路验证）

| 验证项 | 结果 | 说明 |
|--------|------|------|
| 健康检查 `/actuator/health` | ✅ 200 | status:UP，db(MySQL):UP，redis(7.4.11):UP |
| 注册 `/api/auth/register` | ✅ code:0 | 新用户 userId=2 写入 MariaDB |
| 登录 `/api/auth/login` | ✅ code:0 | 返回 JWT token（长度 125） |
| 鉴权 `/api/users/me` | ✅ code:0 | 带 Bearer token 访问成功，返回用户信息 |
| 密码不泄露 | ✅ | 用户信息里无 password 字段（@JsonIgnore 生效） |

三个 Pod 全部 `1/1 Running`：app、mysql(MariaDB)、redis。

---

## 二、为什么这次稳定（关键结论）

**核心矛盾**：公共镜像源（daocloud/1ms.run）免费但抽风，首次部署拉全新镜像时容易卡死/超时。

**腾讯云 TCR 方案解决方式**：
1. **镜像推送到腾讯云私有仓库**（`ccr.ccs.tencentyun.com/你的账号/镜像`）
2. **本机 docker 登录腾讯云** → 能稳定推拉（国内网络，快）
3. **镜像先拉本机，再 `kind load` 进集群**（绕开集群 containerd 直接拉腾讯云时可能挂起的问题）
4. **部署清单镜像地址指向腾讯云 + imagePullPolicy: IfNotPresent**（集群直接用节点内镜像，不触发网络拉取）

**结果**：Pod 启动快、稳定，不再受网络波动影响。

---

## 三、本次遇到的关键问题与解决（踩坑记录）

### 问题 1：腾讯云私有镜像拉取 401 Unauthorized
- **现象**：Pod 事件显示 `Pulling ccr...`，containerd 日志 `401 Unauthorized (anonymous token)`
- **根因**：本机 docker 用了 `credsStore: desktop`（Docker Desktop 安全存储），**凭据不在 config.json 明文里**。用 `--from-file=.dockerconfigjson` 导入的 secret 里 `auth` 字段是空的 → 集群拿不到凭据
- **解决**：用 `docker-credential-desktop get` 读取明文凭据，重建带正确用户名/密码的 secret：
  ```powershell
  kubectl create secret docker-registry tencent-cr -n user-auth-demo \
    --docker-server=ccr.ccs.tencentyun.com \
    --docker-username=<账号> --docker-password=<密码>
  ```

### 问题 2：containerd 直接拉腾讯云会「挂起」（既不成功也不报错）
- **现象**：Pod 事件一直 `Pulling`（几分钟），但 containerd 日志无 Pull/Pulled 记录
- **根因**：集群节点 containerd 到腾讯云的连接不稳定（网络问题），拉取请求挂起
- **解决**：**改为「本机 docker 拉 + kind load 导入集群」**，Pod 用节点内镜像（`imagePullPolicy: IfNotPresent`），绕开集群直接拉腾讯云

### 问题 3：kind load 多镜像时输出看似报错
- **现象**：`kind load docker-image` 显示 `not yet present on node, loading...` 且 exit 1
- **真相**：实际加载成功（exit 0 是后续单独跑），`loading...` 只是 stderr 提示
- **解决**：加载后用 `ctr -n k8s.io images list` 确认镜像已进节点

### 问题 4：旧 Pod 卡 Terminating
- **现象**：多次部署残留的旧 Pod 长时间 `Terminating`
- **解决**：不影响新 Pod，最终会被清理；关注新 Pod 的 Running/Ready 即可

---

## 四、可复现的命令步骤（你跟着学）

### 前置：环境变量
```powershell
$env:PATH = "C:\Program Files\Docker\Docker\resources\bin;$env:PATH"
```

### 1. 本机登录腾讯云（凭据存 Docker Desktop 安全存储）
```powershell
docker login ccr.ccs.tencentyun.com
```

### 2. 给镜像打腾讯云 tag 并推送
```powershell
docker tag mariadb:10.11 ccr.ccs.tencentyun.com/279129867/mariadb:10.11
docker tag redis:7-alpine ccr.ccs.tencentyun.com/279129867/redis:7-alpine
docker tag user-auth-demo-app:latest ccr.ccs.tencentyun.com/279129867/user-auth-demo:latest

docker push ccr.ccs.tencentyun.com/279129867/mariadb:10.11
docker push ccr.ccs.tencentyun.com/279129867/redis:7-alpine
docker push ccr.ccs.tencentyun.com/279129867/user-auth-demo:latest
```

### 3. 创建集群（kind）
```powershell
kind create cluster --name user-auth-cluster --config k8s\kind-config.yaml
```

### 4. 创建腾讯云拉取凭据（imagePullSecret）
```powershell
# 方法 A：从 Docker Desktop 凭据读取明文（凭据不在 config.json 时用这个）
# 先读取：docker-credential-desktop get

# 方法 B：直接创建（知道密码时）
kubectl create secret docker-registry tencent-cr -n user-auth-demo `
  --docker-server=ccr.ccs.tencentyun.com `
  --docker-username=你的腾讯云账号 --docker-password=镜像仓库密码
```

### 5. 本机拉腾讯云镜像 + kind load 导入集群（关键，绕开集群直拉腾讯云）
```powershell
docker pull ccr.ccs.tencentyun.com/279129867/mariadb:10.11
docker pull ccr.ccs.tencentyun.com/279129867/redis:7-alpine
docker pull ccr.ccs.tencentyun.com/279129867/user-auth-demo:latest

kind load docker-image `
  ccr.ccs.tencentyun.com/279129867/mariadb:10.11 `
  ccr.ccs.tencentyun.com/279129867/redis:7-alpine `
  ccr.ccs.tencentyun.com/279129867/user-auth-demo:latest `
  --name user-auth-cluster
```

### 6. 部署（镜像地址指向腾讯云 + IfNotPresent）
```powershell
kubectl apply -f k8s\mysql-redis.yaml
kubectl apply -f k8s\app.yaml
kubectl get pods -n user-auth-demo   # 等 3 个都 1/1 Running
```

### 7. 验证链路
```powershell
# 端口转发
kubectl port-forward -n user-auth-demo svc/app 18080:8080

# 健康检查
Invoke-WebRequest http://localhost:18080/actuator/health

# 注册 / 登录 / 鉴权
# (见验证脚本，或浏览器/Postman 访问对应接口)
```

---

## 五、关键文件

| 文件 | 作用 |
|------|------|
| `k8s\mysql-redis.yaml` | MariaDB + Redis 部署（镜像已改腾讯云 + secret） |
| `k8s\app.yaml` | App 部署（镜像腾讯云 + secret + IfNotPresent） |
| `k8s\kind-config.yaml` | kind 集群配置 |
| `k8s\containerd-config.toml` | 节点 containerd 配置（参考） |
| `k8s\hosts.toml` | certs.d 国内源配置（参考） |

---

## 六、经验总结

1. **云厂商免费私有仓库（腾讯云 TCR / 阿里云 ACR）是个人开发最稳的镜像源**，比公共加速源规范、稳定。
2. **凭据坑**：Docker Desktop 的 `credsStore` 会把凭据存安全存储，`config.json` 里 `auth` 为空——用 `--from-file` 导入 secret 是空的。要用 `docker-credential-desktop get` 读明文重建 secret。
3. **集群直拉外网镜像不稳定**时，改用「本机拉 + kind load 导入」，Pod 用 `IfNotPresent` 直接用节点内镜像——最稳。
4. **kind load 的 `loading...` 提示只是 stderr**，不代表失败；用 `ctr images list` 确认实际状态。

---

*报告生成时间：2026-10-17*
