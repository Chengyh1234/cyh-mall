# mall 生产环境部署文档（基础组件 Docker + 应用宿主机直跑）

> 适用环境：CentOS 7.x / Ubuntu 20.04+（64 位，内存建议 ≥ 4G）
> 部署方式：**基础组件**（MySQL/Redis/RabbitMQ/ES/MongoDB/Nginx）用 **Docker 容器**部署；**三个 Java 后端**（mall-admin/mall-search/mall-portal）**不打镜像**，直接以 jar 方式在宿主机 `java -jar` 运行。
> 应用通过 `localhost` 访问基础组件（Docker 已将端口映射到宿主机），Nginx 容器把 `/8080 /8081 /8085` 反代到宿主机上的后端进程。

---

## 一、整体架构

```
┌────────────── 客户端（浏览器 / App） ──────────────┐
│                       │ 80                        │
┌──────────────▼──────────────┐                     │
│          Nginx 容器          │                     │
│   静态前端 + 反向代理         │                     │
└──────┬───────┬───────┬──────┘                     │
   80  │  81   │8080/8081/8085                       │
   后台 │  前台  │ 反代到宿主机后端                    │
       │       │ 172.17.0.1（Docker 网关）          │
┌──────▼───────▼───────▼──────────────────────────┐  │
│              宿主机 · Java 后端进程               │  │
│  java -jar mall-admin  :8080   （直跑，非容器）   │  │
│  java -jar mall-search :8081                    │  │
│  java -jar mall-portal :8085                    │  │
└───────────────────────┬─────────────────────────┘  │
                        │ localhost 访问             │
                        │ (端口已映射到宿主机)       │
┌───────────────────────▼─────────────────────────┐  │
│       基础组件容器（cyh-net 网络，容器名互访）    │  │
│   mysql / redis / rabbitmq / es / mongo          │  │
└─────────────────────────────────────────────────┘  │
```

| 服务 | 部署方式 | 位置/进程 | 端口 | 说明 |
|------|----------|-----------|------|------|
| MySQL | Docker 容器 | mysql | 3306 | 主数据库（mall 库） |
| Redis | Docker 容器 | redis | 6379 | 缓存 |
| RabbitMQ | Docker 容器 | mq | 5672 / 15672 | 消息队列（含管理台） |
| Elasticsearch | Docker 容器 | es | 9200 / 9300 | 商品搜索，含 IK 分词器 |
| MongoDB | Docker 容器 | mongo | 27017 | 会员 / 前台数据 |
| Nginx | Docker 容器 | nginx | 80 / 81 / 8080 / 8081 / 8085 | 前端静态 + 反代到宿主机后端 |
| mall-admin | **宿主机 jar** | 宿主进程 | 8080 | 后台管理接口 |
| mall-search | **宿主机 jar** | 宿主进程 | 8081 | 搜索服务 |
| mall-portal | **宿主机 jar** | 宿主进程 | 8085 | 前台门户接口 |

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
mkdir -p /home/nginx/html/admin /home/nginx/html/app
mkdir -p /home/nginx/conf.d /home/nginx/logs
mkdir -p /home/mall/{mall-admin,mall-search,mall-portal}   # 存放三个后端 jar（宿主机直跑）
mkdir -p /mydata/app/{mall-admin,mall-search,mall-portal}/logs   # 应用日志目录（宿主机）
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

**创建连接账号**（应用配置使用 `admin/123456` 认证连接，必须创建；若 `show users` 为空，应用会报 `AuthenticationFailed (code 18)`）：

```bash
docker exec -it mongo mongosh --eval 'db.getSiblingDB("admin").createUser({user:"admin",pwd:"123456",roles:[{role:"root",db:"admin"}]})'
```

> 说明：副本集成员 host 使用容器名 `mongo:27017`，保证容器间通过 `cyh-net` 网络互访稳定。
> 验证：`docker exec mongo mongosh --eval 'rs.status().ok'` 返回 `1`；`docker exec mongo mongosh --eval 'db.getSiblingDB("admin").getUsers()'` 能看到 `admin` 账号。

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
  -p 81:81 \
  -v /home/nginx/html:/usr/share/nginx/html \
  -v /home/nginx/conf.d:/etc/nginx/conf.d \
  -v /home/nginx/logs:/var/log/nginx \
  --restart unless-stopped \
  nginx:1.22
```

> 只挂载 `conf.d` 子目录，保留镜像自带的 `nginx.conf` / `mime.types`，避免配置被整体覆盖的坑。
> **必须映射 5 个端口**：80/81 托管前后端前端页面，8080/8085/8081 反代三个后端接口。后端接口统一由 nginx 入口对外，宿主机上的后端进程无需再映射端口公网暴露。

### 4.2 编写 Nginx 配置（无域名 · nginx 统一反代）

> ⚠️ **方案说明（无域名 · nginx 统一反代）**：本部署**不使用域名**。后端接口没有统一路径前缀（如 `/role/list`、`/home/content`、`/esProduct/search`），因此 nginx 采用**多端口监听**：80/81 托管前端静态页面，8080/8085/8081 各自反代到宿主机上**直接 `java -jar` 运行**的三个后端进程。前端 base 指向 nginx 的端口（`http://IP:8080` 等），后端应用不直接暴露公网。

创建 `/home/nginx/conf.d/default.conf`（完整配置，可直接使用）：

```nginx
# ============================================================
#  mall Nginx 配置（仅开放 80/81，路径前缀反代宿主机后端）
#  80  → 后台管理前端 + /api-admin/ 反代 mall-admin:8080
#  81  → 前台 H5 + /api-portal/ 反代 mall-portal:8085
#                      + /api-search/ 反代 mall-search:8081
# ============================================================

# ---------- 后台管理前端（http://IP ） ----------
server {
    listen       80;
    server_name  _;

    root   /usr/share/nginx/html/admin;
    index  index.html;

    client_max_body_size 20m;

    # 后台接口：/api-admin/xxx → mall-admin 的 /xxx
    location ^~ /api-admin/ {
        proxy_pass http://172.17.0.1:8080/;   # 尾部斜杠：剥离 /api-admin 前缀
        proxy_set_header  Host              $host;
        proxy_set_header  X-Real-IP         $remote_addr;
        proxy_set_header  X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header  X-Forwarded-Proto $scheme;
    }

    # 静态资源长缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?|ttf)$ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~ /\. {
        deny all;
    }
}

# ---------- 前台 H5 + 接口（http://IP:81 ） ----------
server {
    listen       81;
    server_name  _;

    root   /usr/share/nginx/html/app;
    index  index.html;

    client_max_body_size 20m;

    # 前台接口：/api-portal/xxx → mall-portal 的 /xxx
    location ^~ /api-portal/ {
        proxy_pass http://172.17.0.1:8085/;   # 尾部斜杠：剥离 /api-portal 前缀
        proxy_set_header  Host              $host;
        proxy_set_header  X-Real-IP         $remote_addr;
        proxy_set_header  X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header  X-Forwarded-Proto $scheme;
    }

    # 搜索接口：/api-search/xxx → mall-search 的 /xxx
    location ^~ /api-search/ {
        proxy_pass http://172.17.0.1:8081/;   # 尾部斜杠：剥离 /api-search 前缀
        proxy_set_header  Host              $host;
        proxy_set_header  X-Real-IP         $remote_addr;
        proxy_set_header  X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header  X-Forwarded-Proto $scheme;
    }

    # 静态资源长缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|svg|ico|woff2?|ttf)$ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000";
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    location ~ /\. {
        deny all;
    }
}
```

> 说明：
> - 前端访问入口：后台 `http://IP`，前台 H5 `http://IP:81`，接口为 `http://IP:8080/8081/8085`。
> - 反代目标为宿主机 `172.17.0.1`（Docker bridge 访问宿主机的网关地址），因为三个后端在宿主机 `java -jar` 直接运行，不在容器内。
> - 需要对外放行的端口：80、81、8080、8081、8085。
> - 前端 base 需指向 nginx 端口，见 5.0 前端部署。

### 4.3 校验并热重载

```bash
docker exec nginx nginx -t          # 语法校验
docker exec nginx nginx -s reload   # 热重载，无需重启容器
```

---

## 五、应用与前端部署

### 5.0 前端构建与部署

> 两个前端均为静态资源，构建后放到 nginx 挂载的 `/home/nginx/html` 下，由 nginx 直接托管（见第四章）。

**① 后台前端 mall-admin-web**（[C:\Users\33383\Desktop\code\mycode\github\mall-admin-web](file:///C:/Users/33383/Desktop/code/mycode/github/mall-admin-web)）

先修改 [.env.production](file:///C:/Users/33383/Desktop/code/mycode/github/mall-admin-web/.env.production)，API 指向 nginx 反代入口：

```bash
VITE_BASE_SERVER_URL = http://服务器IP:8080
```

> 注意：`VITE_USE_OSS`、`VITE_OSS_UPLOAD_URL` 在 [.env](file:///C:/Users/33383/Desktop/code/mycode/github/mall-admin-web/.env) 中已配置为阿里云 OSS 直传，无需改动；若使用本地上传方式（`VITE_USE_OSS=false`），则上传走 `VITE_MINIO_UPLOAD_URL = /minio/upload`（本项目不使用 MinIO，保持 OSS 直传即可）。

构建：

```bash
cd mall-admin-web
npm install
npm run build            # 产物在 dist/ 目录
```

部署：

```bash
# 将构建产物同步到服务器，放入 nginx 后台前端目录（对应 80 端口）
scp -r dist/* root@服务器IP:/home/nginx/html/admin/
```

**② 前台前端 mall-app-web（H5）**（[C:\Users\33383\Desktop\code\mycode\github\mall-app-web](file:///C:/Users/33383/Desktop/code/mycode/github/mall-app-web)）

先修改 [.env.production](file:///C:/Users/33383/Desktop/code/mycode/github/mall-app-web/.env.production)，API / ES 指向 nginx 反代入口：

```bash
VITE_API_BASE_URL = http://服务器IP:8085
VITE_ES_BASE_URL = http://服务器IP:8081
```

构建：

```bash
cd mall-app-web
npm install
npm run build:h5          # 产物在 dist/build/h5 目录
```

部署：

```bash
scp -r dist/build/h5/* root@服务器IP:/home/nginx/html/app/
```

> 前台 H5 通过 `http://服务器IP:81` 访问页面，其 API/ES 请求经 nginx 反代到后端（`http://服务器IP:8085`、`http://服务器IP:8081`），见 4.2。

### 5.1 修改后端 prod 配置（关键！）

> 应用在 **宿主机直接 `java -jar` 运行**，因此 prod 配置里访问基础组件的地址统一使用 **`localhost`**（基础组件 Docker 已把端口映射到宿主机，无需再用容器名）。

**① mall-admin** — [application-prod.yml](../../../mall-admin/src/main/resources/application-prod.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
  data:
    redis:
      host: localhost
  rabbitmq:
    host: localhost
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
    url: jdbc:mysql://localhost:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
  elasticsearch:
    uris: localhost:9200
  rabbitmq:
    host: localhost
    virtual-host: /mall
```

**③ mall-portal** — [application-prod.yml](../../../mall-portal/src/main/resources/application-prod.yml)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mall?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false
  data:
    mongodb:
      uri: mongodb://admin:123456@localhost:27017/mall-port?authSource=admin   # 使用 3.5 创建的 admin 账号认证连接
    redis:
      host: localhost
  rabbitmq:
    host: localhost
    virtual-host: /mall
```

> ⚠️ MongoDB 认证说明：mongo 容器已按 3.5 创建 `admin/123456` 账号，应用使用该账号认证连接（`authSource=admin`）。若启动后报 `Authentication failed`，说明账号未创建或密码不一致，按 3.5 重新执行创建命令即可，无需修改 uri。

### 5.2 打包 jar

在**项目根目录**（本机开发环境，JDK 17 + Maven）：

```bash
# 无论注释掉 docker 插件，还是用 -Ddocker.skip=true 跳过，务必避免打包时触发 docker build
mvn clean package -DskipTests -pl '!mall-demo' --% -Ddocker.skip=true
```

> 💡 项目自带的 `docker-maven-plugin` 在 `package` 阶段默认会尝试连 docker 构建镜像。本方案应用不打镜像，因此打包时加 `-Ddocker.skip=true` 跳过（或用 -pl 指定模块：`-pl mall-admin,mall-search,mall-portal -am`）。若需彻底屏蔽，可把根 [pom.xml](../../pom.xml) 中该插件绑定的 `<executions>` 注释掉。

产出 jar：

| 模块 | 产物 |
|------|------|
| mall-admin | `mall-admin/target/mall-admin-1.0-SNAPSHOT.jar` |
| mall-search | `mall-search/target/mall-search-1.0-SNAPSHOT.jar` |
| mall-portal | `mall-portal/target/mall-portal-1.0-SNAPSHOT.jar` |

### 5.3 上传 jar 到服务器

将三个 jar 分别上传到对应目录（无需构建镜像）：

```bash
scp mall-admin/target/mall-admin-1.0-SNAPSHOT.jar root@服务器IP:/home/mall/mall-admin/
scp mall-search/target/mall-search-1.0-SNAPSHOT.jar root@服务器IP:/home/mall/mall-search/
scp mall-portal/target/mall-portal-1.0-SNAPSHOT.jar root@服务器IP:/home/mall/mall-portal/
```

### 5.4 在宿主机启动应用（java -jar 直跑）

> 应用启动方式（logs 目录已在 2.5 创建）：用 `nohup` 后台运行，日志写到 `/mydata/app/<模块>/logs/`。下面以 mall-admin 为例，其余两个模块改名字即可。

**先一次性确认 host 解析**：三个应用在同宿主上，不冲突，但 `nacos`/hostname 无关，只需确保端口空闲。

**mall-admin（8080）**：

```bash
cd /home/mall/mall-admin
nohup java -Dspring.profiles.active=prod -jar mall-admin-1.0-SNAPSHOT.jar \
  >> /mydata/app/mall-admin/logs/app.log 2>&1 &
```

**mall-search（8081）**：

```bash
cd /home/mall/mall-search
nohup java -Dspring.profiles.active=prod -jar mall-search-1.0-SNAPSHOT.jar \
  >> /mydata/app/mall-search/logs/app.log 2>&1 &
```

**mall-portal（8085）**：

```bash
cd /home/mall/mall-portal
nohup java -Dspring.profiles.active=prod -jar mall-portal-1.0-SNAPSHOT.jar \
  >> /mydata/app/mall-portal/logs/app.log 2>&1 &
```

**查看 / 停止 / 重启**（以 mall-portal 为例）：

```bash
# 查看进程（确认已在运行）
ps -ef | grep mall-portal | grep -v grep

# 查看日志
tail -f /mydata/app/mall-portal/logs/app.log

# 停止：kill 对应进程（-9 无法优雅关闭，慎用）
kill <PID>

# 重启：kill 后重新执行上面的 nohup java -jar 命令即可
```

> ⚠️ 若要让应用开机自启，建议将上面 nohup 命令封装为 systemd service（见 5.4.1）；若只是手动拉起，nohup 方式已足够。

#### 5.4.1 可选：配置 systemd 开机自启（推荐）

以 mall-portal 为例，创建 `/etc/systemd/system/mall-portal.service`：

```ini
[Unit]
Description=mall-portal
After=network.target docker.service

[Service]
User=root
WorkingDirectory=/home/mall/mall-portal
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=prod /home/mall/mall-portal/mall-portal-1.0-SNAPSHOT.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable mall-portal
systemctl restart mall-portal
```

---

> 本项目应用不再打包 docker 镜像，[mall-admin.sh](../../sh/mall-admin.sh)（`--link` 旧脚本）与 [Dockerfile](../../sh/Dockerfile) 仅作参考，本方案不直接使用。

## 六、Docker Compose 一键编排基础组件（可选，推荐）

> 应用已改为宿主机直跑，无需放进 compose。本 compose 仅编排**基础组件 + nginx**（共 6 个容器），`up -d` 一键拉起，java 后端仍按第五章在宿主机运行。
> 注意：`cyh-net` 网络已存在，compose 里必须用 `external: true` 复用，而不是新建。

创建 `/home/mall/docker-compose-production.yml(在docker下docker-compose-env.yml为准)`：

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
  # ---------- 基础组件（应用在宿主机直跑，不进 compose） ----------
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
    ports: ["80:80", "81:81", "8080:8080", "8081:8081", "8085:8085"]
    volumes:
      - /home/nginx/html:/usr/share/nginx/html
      - /home/nginx/conf.d:/etc/nginx/conf.d
      - /home/nginx/logs:/var/log/nginx
    networks: [cyh-net]
    restart: unless-stopped
```

**Compose 常用命令**：

```bash
# 一键启动基础组件（6 个容器：mysql redis mq es mongo nginx）
docker-compose -f /home/mall/docker-compose-production.yml up -d

# 查看状态 / 日志
docker-compose -f /home/mall/docker-compose-production.yml ps
docker-compose -f /home/mall/docker-compose-production.yml logs -f es

# 停止（不删容器）
docker-compose -f /home/mall/docker-compose-production.yml stop

# 彻底下线（删除容器；不加 -v 保留数据卷）
docker-compose -f /home/mall/docker-compose-production.yml down
```

> 说明：使用 compose 方式启动后，仍需手动执行本章之外的初始化操作：导入 mall.sql、创建 `/mall` vhost、安装 IK 分词器、初始化 mongo 副本集并创建 `admin/123456` 认证账号（见 3.5）。三个 java 后端按第五章内容在宿主机直跑，不受本 compose 管辖。

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

# 4. 前端 + 后端（nginx 统一反代）
curl http://服务器IP/                        # 后台前端页面（80 端口）
curl http://服务器IP:81/                     # 前台 H5 页面（81 端口）
curl http://服务器IP:8080/druid/login.html   # 经 nginx 反代 → mall-admin
curl http://服务器IP:8081/esProduct/search   # 经 nginx 反代 → mall-search
curl http://服务器IP:8085/home/content       # 经 nginx 反代 → mall-portal

# 5. 基础组件日志（排错首选）
docker logs -f es

# 6. 应用日志（宿主机 java 进程）
tail -f /mydata/app/mall-portal/logs/app.log
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

应用在宿主机直跑，用 `localhost` 连基础组件（Docker 已把端口映射到宿主机）：

```bash
# 确认基础组件容器在运行、端口映射正常
docker ps

# 从宿主机直接验证连通性
mysql -uroot -p123456 -h127.0.0.1 -e "show databases;"    # mysql
redis-cli -h127.0.0.1 ping                                  # redis，应返回 PONG

# 确认 prod 配置里连接地址是 localhost（而非容器名 mysql / redis / es / mongo / mq）
```

### 8.3 MongoDB 连不上

- 检查副本集是否初始化：`docker exec mongo mongosh --eval 'rs.status().ok'`（需为 1）
- 检查认证账号是否存在：`docker exec mongo mongosh --eval 'db.getSiblingDB("admin").getUsers()'`，若为空则按 3.5 创建 `admin/123456`
- 确认 prod 配置 uri 中的账号密码与 3.5 创建的一致

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
# 针对于基础组件容器：启动时已加 -e TZ=Asia/Shanghai 和 -v /etc/localtime:/etc/localtime
# 如仍不准，可补执行
docker cp /etc/localtime <容器名>:/etc/localtime && docker restart <容器名>
```

### 8.8 Java 应用启动失败

```bash
# 查看应用日志（宿主机 java 进程）
tail -f /mydata/app/<模块>/logs/app.log

# 确认端口未被占用
ss -lntp | grep -E '8080|8081|8085'

# 若报驱动/认证错，检查 prod 配置是否已改为 localhost（见 5.1）
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
# 初始化副本集 + 创建认证账号（重复执行会报错，属正常，可忽略）
docker exec mongo mongosh --eval 'rs.initiate({_id:"rs0",members:[{_id:0,host:"mongo:27017"}]})' || true
docker exec mongo mongosh --eval 'db.getSiblingDB("admin").createUser({user:"admin",pwd:"123456",roles:[{role:"root",db:"admin"}]})' || true

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
