# mall 生产环境部署文档（Docker）

> 适用环境：CentOS 7.x / Ubuntu 20.04+（64 位，内存建议 ≥ 4G）
> 部署方式：Docker + Docker Compose，全部容器加入自定义网络 `cyh-net`，容器间通过 **容器名** 互访。

---

## 一、整体架构

```
┌────────────── 客户端（浏览器 / App） ──────────────┐
│                       │ 80                        │
┌──────────────▼──────────────┐                     │
│          Nginx              │                     │
│   静态前端 + 反向代理         │                     │
└──────┬─────────┬─────────┬──┘                     │
       │         │         │                        │
┌──────▼───┐ ┌───▼────┐ ┌──▼────────┐               │
│mall-admin│ │mall-search│ │mall-portal│             │
│   8080   │ │   8081   │ │    8085   │             │
└────┬─────┘ └────┬─────┘ └────┬──────┘             │
     │            │            │                    │
     └──────── cyh-net 网络（容器名互访）──────────┘
         mysql / redis / rabbitmq / es / mongo
```

| 服务 | 容器名 | 对外端口 | 说明 |
|------|--------|----------|------|
| MySQL | mysql | 3306 | 主数据库（mall 库） |
| Redis | redis | 6379 | 缓存 |
| RabbitMQ | mq | 5672 / 15672 | 消息队列（含管理台） |
| Elasticsearch | es | 9200 / 9300 | 商品搜索，含 IK 分词器 |
| MongoDB | mongo | 27017 | 会员 / 前台数据 |
| Nginx | nginx | 80 | 前端静态资源 + 反向代理 |
| mall-admin | mall-admin | 8080 | 后台管理接口 |
| mall-search | mall-search | 8081 | 搜索服务 |
| mall-portal | mall-portal | 8085 | 前台门户接口 |

---

## 二、环境准备

### 2.1 安装 Docker

```bash
# 使用国内镜像源安装（如已安装可跳过）
curl -fsSL https://get.docker.com | bash -s docker --mirror Aliyun

# 设置开机自启并启动
systemctl enable docker && systemctl start docker

# 验证
docker --version
```

### 2.2 安装 Docker Compose

```bash
curl -L "https://github.com/docker/compose/releases/download/v2.24.5/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
ln -s /usr/local/bin/docker-compose /usr/bin/docker-compose

# 验证
docker-compose --version
```

### 2.3 配置 Docker 镜像加速（可选，推荐）

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": ["https://docker.m.daocloud.io"]
}
EOF
systemctl daemon-reload && systemctl restart docker
```

### 2.4 创建 Docker 网络

> 所有容器必须加入同一网络，才能通过容器名互相访问。

```bash
docker network create cyh-net

# 验证
docker network ls | grep cyh-net
```

### 2.5 准备宿主目录

```bash
mkdir -p /home/mysql/data /home/mysql/log
mkdir -p /home/redis/data /home/redis/conf
mkdir -p /home/mongo/data
mkdir -p /home/nginx/html /home/nginx/conf.d /home/nginx/logs
mkdir -p /home/mall/{mall-admin,mall-search,mall-portal}
mkdir -p /mydata/app/{mall-admin,mall-search,mall-portal}/logs
```

---

## 三、基础组件部署

### 3.1 MySQL 8.0

```bash
docker pull mysql:8.0
docker run -d \
  --name mysql \
  --network cyh-net \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e TZ=Asia/Shanghai \
  -v /home/mysql/data:/var/lib/mysql \
  -v /home/mysql/log:/var/log/mysql \
  --restart unless-stopped \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

**导入初始数据库**（[mall.sql](../../sql/mall.sql)）：

```bash
# 方式一：进入容器导入
docker exec -i mysql mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS mall DEFAULT CHARACTER SET utf8mb4;"
docker exec -i mysql mysql -uroot -p123456 mall < mall.sql

# 方式二：直接管道导入
cat mall.sql | docker exec -i mysql mysql -uroot -p123456 --default-character-set=utf8mb4 mall
```

> 验证：`docker exec mysql mysql -uroot -p123456 -e "show databases;"` 应能看到 `mall` 库。

### 3.2 Redis 6.2.6

先准备配置文件：

```bash
cat > /home/redis/conf/redis.conf <<EOF
appendonly yes
protected-mode no
daemonize no
EOF
```

启动容器：

```bash
docker pull redis:6.2.6
docker run -d \
  --name redis \
  --network cyh-net \
  -p 6379:6379 \
  --restart unless-stopped \
  -v /home/redis/data:/data \
  -v /home/redis/conf/redis.conf:/usr/local/bin/redisconfig/redis.conf \
  redis:6.2.6 \
  redis-server /usr/local/bin/redisconfig/redis.conf
```

> 验证：`docker exec redis redis-cli ping` 返回 `PONG`。

### 3.3 RabbitMQ（带管理台）

```bash
docker pull rabbitmq:4.2.5-management
docker run -d \
  --name mq \
  --network cyh-net \
  --hostname rabbitmq-host \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin \
  -v rabbitmq_data:/var/lib/rabbitmq \
  --restart unless-stopped \
  rabbitmq:4.2.5-management
```

**⚠️ 必须创建 `/mall` 虚拟主机**

> 应用 prod 配置中 `virtual-host: /mall`，而默认容器只有 `/` 虚拟主机，不创建则应用连不上 MQ。

```bash
docker exec mq rabbitmqctl add_vhost /mall
```

> 验证：浏览器访问 `http://服务器IP:15672`，账号 `admin/admin`，能看到 `/mall` 虚拟主机。

### 3.4 Elasticsearch 7.17.3（含 IK 分词器）

```bash
docker pull elasticsearch:7.17.3
docker run -d \
  --name es \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" \
  -e "discovery.type=single-node" \
  -v es-data:/usr/share/elasticsearch/data \
  -v es-plugins:/usr/share/elasticsearch/plugins \
  --privileged \
  --network cyh-net \
  -p 9200:9200 \
  -p 9300:9300 \
  --restart unless-stopped \
  elasticsearch:7.17.3
```

**安装 IK 分词器**：

```bash
# 等待 es 初始化完成后执行（约 30 秒，可先跳过下一步验证）
docker exec -it es bash -c "cd /usr/share/elasticsearch && ./bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/7.17.3"
# 安装完成后重启 es 使插件生效
docker restart es
```

> 验证：`curl http://服务器IP:9200` 返回 json；`curl http://服务器IP:9200/_cat/plugins` 能看到 `analysis-ik`。
> ⚠️ 若 9200 不通，先看日志：`docker logs es`（多半是内存不足或宿主 `vm.max_map_count` 太小，按 6.1 处理）。

### 3.5 MongoDB 7.0（副本集模式）

```bash
docker pull mongo:7.0
docker run -d \
  --name mongo \
  --network cyh-net \
  -p 27017:27017 \
  -e TZ=Asia/Shanghai \
  -v /home/mongo/data:/data/db \
  --restart unless-stopped \
  mongo:7.0 \
  --replSet rs0 \
  --bind_ip_all
```

**初始化副本集**（应用以副本集模式连接，必须初始化）：

```bash
docker exec -it mongo mongosh --eval 'rs.initiate({_id:"rs0",members:[{_id:0,host:"mongo:27017"}]})'
```

> 说明：副本集成员 host 使用容器名 `mongo:27017`，保证容器间通过 `cyh-net` 网络互访稳定。
> 验证：`docker exec mongo mongosh --eval 'rs.status().ok'` 返回 `1`。

### 3.6 文件存储方案（不使用 MinIO）

> 本项目**不使用 MinIO**，图片 / 文件上传改用 **阿里云 OSS**（应用 prod 配置中已内置 `aliyun.oss` 配置项），无需部署任何对象存储中间件。OSS 配置见 5.1。

---

## 四、Nginx 部署

### 4.1 启动 Nginx 容器

```bash
docker pull nginx:1.22
docker run -d \
  --name nginx \
  --network cyh-net \
  -p 80:80 \
  -v /home/nginx/html:/usr/share/nginx/html \
  -v /home/nginx/conf.d:/etc/nginx/conf.d \
  -v /home/nginx/logs:/var/log/nginx \
  --restart unless-stopped \
  nginx:1.22
```

> 只挂载 `conf.d` 子目录，保留镜像自带的 `nginx.conf` / `mime.types`，避免配置被整体覆盖的坑。

### 4.2 编写反向代理配置

创建 `/home/nginx/conf.d/default.conf`：

```nginx
server {
    listen       80;
    server_name  _;

    client_max_body_size 20m;   # 上传图片/文件需要

    # 前端静态资源（前端 build 产物放到 /home/nginx/html 目录）
    location / {
        root   /usr/share/nginx/html;
        index  index.html index.htm;
        try_files $uri $uri/ /index.html;   # 支持前端 history 路由
    }

    # 后台管理接口
    location /admin/ {
        proxy_pass http://mall-admin:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 搜索服务
    location /search/ {
        proxy_pass http://mall-search:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 前台门户接口
    location /portal/ {
        proxy_pass http://mall-portal:8085;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

> 路径前缀（`/admin/` `/search/` `/portal/`）以你前端实际请求的 context-path 为准，不一致时改对应 `location` 即可。

### 4.3 校验并热重载

```bash
docker exec nginx nginx -t          # 语法校验
docker exec nginx nginx -s reload   # 热重载，无需重启容器
```

---

## 五、应用服务部署（三个 jar）

### 5.1 修改 prod 配置（关键！）

> 容器间通过容器名互访，因此必须把 prod 配置里的 IP 地址改为容器名。

**① mall-admin** — [application-prod.yml](../../../mall-admin/src/main/resources/application-prod.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
  data:
    redis:
      host: redis
  rabbitmq:
    host: mq
    virtual-host: /mall
aliyun:
  oss:
    endpoint: xxx            # 阿里云 OSS 对外访问域名，替换为自己的
    accessKeyId: xxx         # AccessKeyId
    accessKeySecret: xxx     # AccessKeySecret
    bucketName: xxx          # 存储空间名称
    callback:
      url: http://服务器公网IP或域名:8080/aliyun/oss/callback   # 上传回调地址（线上置 true 时生效）
      enabled: true
```

> 💡 不使用 MinIO 时，文件上传走阿里云 OSS：把上面 `aliyun.oss` 各项替换为你的真实值（endpoint / accessKeyId / accessKeySecret / bucketName）。若暂不需要上传功能，可先保持默认 `xxx`，待配置好 OSS 后再启用。

**② mall-search** — [application-prod.yml](../../../mall-search/src/main/resources/application-prod.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
  elasticsearch:
    uris: es:9200
  rabbitmq:
    host: mq
    virtual-host: /mall
```

**③ mall-portal** — [application-prod.yml](../../../mall-portal/src/main/resources/application-prod.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
  data:
    mongodb:
      uri: mongodb://mongo:27017/mall-port    # 未开认证时去掉账号密码
    redis:
      host: redis
  rabbitmq:
    host: mq
    virtual-host: /mall
```

> ⚠️ MongoDB 认证说明：本文档的 mongo 容器**未开启认证**，而原 prod 配置带 `admin:123456` 账号，连接会失败。两种方案任选：
> - **方案 A（推荐，简单）**：如上，把 uri 改成 `mongodb://mongo:27017/mall-port`（去掉账号密码）；
> - **方案 B**：给 mongo 创建用户后保留原 uri（需同步调整容器初始化脚本）。

### 5.2 打包 jar

在**项目根目录**（本机开发环境，JDK 17 + Maven）：

```bash
mvn clean package -DskipTests
```

产出 jar：

| 模块 | 产物 |
|------|------|
| mall-admin | `mall-admin/target/mall-admin-1.0-SNAPSHOT.jar` |
| mall-search | `mall-search/target/mall-search-1.0-SNAPSHOT.jar` |
| mall-portal | `mall-portal/target/mall-portal-1.0-SNAPSHOT.jar` |

### 5.3 构建镜像

项目已提供 [Dockerfile](../../sh/Dockerfile)（openjdk:17 基础镜像）。将 jar 与 Dockerfile 放到同一目录构建：

```bash
# 以 mall-admin 为例，其余两个模块同理
cp mall-admin-1.0-SNAPSHOT.jar /home/mall/mall-admin/
cp /path/to/Dockerfile /home/mall/mall-admin/

cd /home/mall/mall-admin
docker build -t mall/mall-admin:1.0-SNAPSHOT .
```

> Dockerfile 中 `ADD mall-admin-1.0-SNAPSHOT.jar` 与 jar 文件名必须一致。

### 5.4 启动应用容器（docker run 方式）

**mall-admin（8080）**：

```bash
docker run -d \
  --name mall-admin \
  --network cyh-net \
  -p 8080:8080 \
  -e "spring.profiles.active=prod" \
  -e TZ=Asia/Shanghai \
  -v /etc/localtime:/etc/localtime \
  -v /mydata/app/mall-admin/logs:/var/logs \
  --restart unless-stopped \
  mall/mall-admin:1.0-SNAPSHOT
```

**mall-search（8081）**：

```bash
docker run -d \
  --name mall-search \
  --network cyh-net \
  -p 8081:8081 \
  -e "spring.profiles.active=prod" \
  -e TZ=Asia/Shanghai \
  -v /etc/localtime:/etc/localtime \
  -v /mydata/app/mall-search/logs:/var/logs \
  --restart unless-stopped \
  mall/mall-search:1.0-SNAPSHOT
```

**mall-portal（8085）**：

```bash
docker run -d \
  --name mall-portal \
  --network cyh-net \
  -p 8085:8085 \
  -e "spring.profiles.active=prod" \
  -e TZ=Asia/Shanghai \
  -v /etc/localtime:/etc/localtime \
  -v /mydata/app/mall-portal/logs:/var/logs \
  --restart unless-stopped \
  mall/mall-portal:1.0-SNAPSHOT
```

> 项目自带的 [mall-admin.sh](../../sh/mall-admin.sh) 脚本用的是 `--link` 旧方式；本方案统一用 `--network cyh-net` + 容器名访问，更简洁可靠。

---

## 六、Docker Compose 一键编排（可选，推荐）

> 若不想一条条 `docker run`，可以把全部服务写进一份 compose 文件，`up -d` 一键启动。
> 注意：`cyh-net` 网络已存在，compose 里必须用 `external: true` 复用，而不是新建。

创建 `/home/mall/docker-compose-production.yml`：

```yaml
version: '3'

networks:
  cyh-net:
    external: true          # 复用已创建的 cyh-net

volumes:
  rabbitmq_data:
  es-data:
  es-plugins:

services:
  # ---------- 基础组件 ----------
  mysql:
    image: mysql:8.0
    container_name: mysql
    command: mysqld --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    environment:
      MYSQL_ROOT_PASSWORD: "123456"
      TZ: Asia/Shanghai
    ports: ["3306:3306"]
    volumes:
      - /home/mysql/data:/var/lib/mysql
      - /home/mysql/log:/var/log/mysql
    networks: [cyh-net]
    restart: unless-stopped

  redis:
    image: redis:6.2.6
    container_name: redis
    command: redis-server /usr/local/bin/redisconfig/redis.conf
    ports: ["6379:6379"]
    volumes:
      - /home/redis/data:/data
      - /home/redis/conf/redis.conf:/usr/local/bin/redisconfig/redis.conf
    networks: [cyh-net]
    restart: unless-stopped

  mq:
    image: rabbitmq:4.2.5-management
    container_name: mq
    hostname: rabbitmq-host
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin
    ports: ["5672:5672", "15672:15672"]
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    networks: [cyh-net]
    restart: unless-stopped

  es:
    image: elasticsearch:7.17.3
    container_name: es
    environment:
      ES_JAVA_OPTS: -Xms512m -Xmx512m
      discovery.type: single-node
    ports: ["9200:9200", "9300:9300"]
    volumes:
      - es-data:/usr/share/elasticsearch/data
      - es-plugins:/usr/share/elasticsearch/plugins
    privileged: true
    networks: [cyh-net]
    restart: unless-stopped

  mongo:
    image: mongo:7.0
    container_name: mongo
    command: --replSet rs0 --bind_ip_all
    environment:
      TZ: Asia/Shanghai
    ports: ["27017:27017"]
    volumes:
      - /home/mongo/data:/data/db
    networks: [cyh-net]
    restart: unless-stopped

  nginx:
    image: nginx:1.22
    container_name: nginx
    ports: ["80:80"]
    volumes:
      - /home/nginx/html:/usr/share/nginx/html
      - /home/nginx/conf.d:/etc/nginx/conf.d
      - /home/nginx/logs:/var/log/nginx
    networks: [cyh-net]
    restart: unless-stopped

  # ---------- 应用 ----------
  mall-admin:
    image: mall/mall-admin:1.0-SNAPSHOT
    container_name: mall-admin
    environment:
      - spring.profiles.active=prod
      - TZ=Asia/Shanghai
    ports: ["8080:8080"]
    volumes:
      - /mydata/app/mall-admin/logs:/var/logs
    networks: [cyh-net]
    depends_on: [mysql, redis, es]
    restart: unless-stopped

  mall-search:
    image: mall/mall-search:1.0-SNAPSHOT
    container_name: mall-search
    environment:
      - spring.profiles.active=prod
      - TZ=Asia/Shanghai
    ports: ["8081:8081"]
    volumes:
      - /mydata/app/mall-search/logs:/var/logs
    networks: [cyh-net]
    depends_on: [mysql, es]
    restart: unless-stopped

  mall-portal:
    image: mall/mall-portal:1.0-SNAPSHOT
    container_name: mall-portal
    environment:
      - spring.profiles.active=prod
      - TZ=Asia/Shanghai
    ports: ["8085:8085"]
    volumes:
      - /mydata/app/mall-portal/logs:/var/logs
    networks: [cyh-net]
    depends_on: [mysql, redis, mongo, mq]
    restart: unless-stopped
```

**Compose 常用命令**：

```bash
# 一键启动全部
docker-compose -f /home/mall/docker-compose-production.yml up -d

# 只启动基础组件 / 只启动应用（可按需拆分，如注释掉应用段）
docker-compose -f /home/mall/docker-compose-production.yml up -d mysql redis mq es mongo nginx
docker-compose -f /home/mall/docker-compose-production.yml up -d mall-admin mall-search mall-portal

# 查看状态 / 日志
docker-compose -f /home/mall/docker-compose-production.yml ps
docker-compose -f /home/mall/docker-compose-production.yml logs -f es

# 停止（不删容器）
docker-compose -f /home/mall/docker-compose-production.yml stop

# 彻底下线（删除容器；不加 -v 保留数据卷）
docker-compose -f /home/mall/docker-compose-production.yml down
```

> 说明：使用 compose 方式启动后，仍需手动执行本章之外的初始化操作：导入 mall.sql、创建 `/mall` vhost、安装 IK 分词器、初始化 mongo 副本集。

---

## 七、部署验收

```bash
# 1. 所有容器都在运行
docker ps

# 2. 基础组件
curl http://localhost:9200          # es，返回 json
docker exec redis redis-cli ping    # redis，返回 PONG
docker exec mysql mysql -uroot -p123456 -e "show databases;"  # mysql，看到 mall 库

# 3. 应用
curl http://localhost:8080/druid/login.html   # mall-admin 可达（Druid 监控）
curl http://localhost:8081/                   # mall-search 可达
curl http://localhost:8085/swagger-ui/        # mall-portal 可达（如启用 swagger）

# 4. 前端 + 反向代理
curl http://localhost/                        # nginx 返回前端页面
curl http://localhost/admin/                  # 经 nginx 转发到 mall-admin

# 5. 应用日志（排错首选）
docker logs -f mall-portal
docker logs -f es
```

---

## 八、常见问题排查

### 8.1 ES 启动失败 / 9200 不通

多半是内存不足或系统参数问题：

```bash
# 查看报错
docker logs es

# 调大内存映射数（永久生效）
echo "vm.max_map_count=262144" >> /etc/sysctl.conf && sysctl -p

# 内存不足时降低 ES 堆内存（512m 已是最低，必要时关掉其他容器）
```

### 8.2 应用连不上 MySQL / Redis

```bash
# 确认容器在同一网络且容器名正确
docker network inspect cyh-net

# 确认 prod 配置已把 IP 改为容器名（mysql / redis / es / mongo / mq）
docker exec -it mall-admin sh   # 进入容器内 ping 一下
```

### 8.3 MongoDB 连不上

- 检查副本集是否初始化：`docker exec mongo mongosh --eval 'rs.status().ok'`（需为 1）
- 确认 prod 配置 uri 已去掉账号密码（未开认证场景）

### 8.4 RabbitMQ 连接报错

- 确认已创建 `/mall` vhost：`docker exec mq rabbitmqctl list_vhosts`
- 确认账号密码为 `admin/admin`

### 8.5 图片上传失败

- 本项目不使用 MinIO，文件上传走阿里云 OSS
- 确认 [application-prod.yml](../../../mall-admin/src/main/resources/application-prod.yml) 中 `aliyun.oss` 的 `endpoint / accessKeyId / accessKeySecret / bucketName` 已配置正确
- 确认 OSS 回调地址（`aliyun.oss.callback.url`）在线上为可被公网访问的地址，且 `callback.enabled: true`
- 若未配置 OSS 且暂不需要上传功能，可先忽略此报错

### 8.6 前端刷新 404

- 检查 nginx `location /` 中是否配置了 `try_files $uri $uri/ /index.html;`

### 8.7 容器时间不对

```bash
# 所有容器启动时已加 -e TZ=Asia/Shanghai 和 -v /etc/localtime:/etc/localtime
# 如仍不准，可补执行
docker cp /etc/localtime <容器名>:/etc/localtime && docker restart <容器名>
```

---

## 九、一键部署脚本（参考）

可将如下脚本存为 `/home/mall/deploy-env.sh`，一次性拉起基础组件 + nginx：

```bash
#!/usr/bin/env bash
set -e

# 创建网络（已存在则跳过）
docker network inspect cyh-net >/dev/null 2>&1 || docker network create cyh-net

# MySQL
docker run -d --name mysql --network cyh-net -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 -e TZ=Asia/Shanghai \
  -v /home/mysql/data:/var/lib/mysql --restart unless-stopped \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci || true

# Redis
docker run -d --name redis --network cyh-net -p 6379:6379 --restart unless-stopped \
  -v /home/redis/data:/data \
  -v /home/redis/conf/redis.conf:/usr/local/bin/redisconfig/redis.conf \
  redis:6.2.6 redis-server /usr/local/bin/redisconfig/redis.conf || true

# RabbitMQ
docker run -d --name mq --network cyh-net --hostname rabbitmq-host \
  -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=admin -e RABBITMQ_DEFAULT_PASS=admin \
  -v rabbitmq_data:/var/lib/rabbitmq --restart unless-stopped \
  rabbitmq:4.2.5-management || true

# ES
docker run -d --name es --network cyh-net --privileged \
  -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" -e "discovery.type=single-node" \
  -v es-data:/usr/share/elasticsearch/data \
  -v es-plugins:/usr/share/elasticsearch/plugins \
  -p 9200:9200 -p 9300:9300 --restart unless-stopped \
  elasticsearch:7.17.3 || true

# MongoDB
docker run -d --name mongo --network cyh-net -p 27017:27017 -e TZ=Asia/Shanghai \
  -v /home/mongo/data:/data/db --restart unless-stopped \
  mongo:7.0 --replSet rs0 --bind_ip_all || true

# Nginx
docker run -d --name nginx --network cyh-net -p 80:80 --restart unless-stopped \
  -v /home/nginx/html:/usr/share/nginx/html \
  -v /home/nginx/conf.d:/etc/nginx/conf.d \
  -v /home/nginx/logs:/var/log/nginx \
  nginx:1.22 || true

echo "=== 启动完成，容器状态 ==="
docker ps
```

---

## 十、附：项目相关文件索引

| 文件 | 用途 |
|------|------|
| [deploy-production.md](deploy-production.md) | 本文档 |
| [nginx.conf](nginx.conf) | 项目自带 nginx 配置（仅静态，需按第四章改造） |
| [docker-compose-env.yml](docker-compose-env.yml) | 项目自带基础组件编排（旧版本，参考） |
| [docker-compose-app.yml](docker-compose-app.yml) | 项目自带应用编排（旧版 `external_links` 方式，参考） |
| [Dockerfile](../../sh/Dockerfile) | 应用镜像构建文件 |
| [run.sh](../../sh/run.sh) / [mall-admin.sh](../../sh/mall-admin.sh) | 项目自带部署脚本（`--link` 旧方式，参考） |
| [mall.sql](../../sql/mall.sql) | 数据库初始化脚本 |
