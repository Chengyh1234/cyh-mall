# 消息通知系统完整设计文档

> 覆盖三类消息：**系统通知（type=1）/ 订单通知（type=2）/ 营销通知（type=3）**
> 贯穿：谁发送 → 什么时候发送（如何落表）→ 谁如何接收 → 前端展示什么 → 点击会怎么样

---

## 1. 消息体系总览

### 1.1 表结构（两份）

```
ums_message            消息主体（一条消息一份）
├── id                 主键
├── type               消息类型：1系统 / 2订单 / 3营销
├── send_type          发送方式：0定向（指定用户）/ 1广播（全站用户）
├── title              消息标题
├── content            消息内容
├── biz_id             业务ID（订单ID / 优惠券ID，前端点击跳转依据）
├── create_time        发送时间

ums_member_message     接收记录（每用户一份，承载已读/未读状态）
├── id                 主键
├── member_id          接收会员ID
├── message_id         消息ID
├── read_status        已读状态：0未读 / 1已读
├── deleted            逻辑删除：0正常 / 1删除
├── create_time        接收记录时间
```

### 1.2 核心设计：主体与接收分离

- **广播消息**（send_type=1）：发布时**只写消息主体**，不为每个用户生成接收记录（避免万级 insert）；用户首次查询时 `materializeBroadcast` **惰性物化**，把该用户缺失的广播消息补生成接收记录
- **定向消息**（send_type=0）：发布时**立即写主体 + 为指定用户写接收记录**
- 已读 / 未读状态**只存在接收记录**中，保证每个用户独立

### 1.3 数据流（统一链路）

```
发送方（管理员 / 业务系统）
      │
      ▼
① 写入 ums_message（主体，含 type / send_type / biz_id）
      │
      ├─ 定向：② 立即写 ums_member_message（每用户一条）
      │
      └─ 广播：② 暂不写，用户首次查询时惰性物化
      │
      ▼
③ 用户端：GET /member/message/list（物化→分页→组装详情）
        未读数：GET /member/message/unread-count（物化→统计）
      │
      ▼
④ 前端消息中心展示（Tab 分类 + 未读红点）
      │
      ▼
⑤ 点击消息：PUT /read/{id} 标记已读 + 按 bizId 跳转业务页
```

---

## 2. 三类消息详细设计

### 2.1 系统通知（type=1）

| 环节 | 说明 |
| --- | --- |
| **谁发送** | 系统管理员（mall-admin） |
| **发送接口** | `POST /message/create`（UmsMessageController） |
| **什么时候发送** | 管理员手动发布（如维护公告、平台规则变更） |
| **数据保存** | 入参 type=1 + sendType（0定向带 memberIds / 1广播）+ title/content → 写 `ums_message`；定向立即写 `ums_member_message`，广播惰性物化 |
| **谁接收** | 定向：指定会员；广播：全站用户（首查物化） |
| **前端展示** | 消息中心"系统"Tab；列表项显示：标题（加粗）、内容摘要、时间；未读消息左侧红点 |
| **点击行为** | 仅标记已读（`PUT /member/message/read/{id}`），**无业务跳转** |

### 2.2 订单通知（type=2）

| 环节 | 说明 |
| --- | --- |
| **谁发送** | 订单业务系统（下单用户本人专属通知） |
| **什么时候发送** | 3 个业务触发点（见下表） |
| **数据保存** | 定向（send_type=0），写 `ums_message`（type=2, biz_id=订单ID）+ 立即写接收会员的 `ums_member_message` |
| **谁接收** | 下单用户本人（memberId = 订单 memberId） |
| **前端展示** | 消息中心"订单"Tab；显示标题、内容、时间、未读红点 |
| **点击行为** | 标记已读 + 跳转订单详情 `/pages/order/orderDetail?orderId={bizId}` |

**三个触发点明细：**

| 触发点 | 代码位置 | 触发时机 | 标题 | 内容 |
| --- | --- | --- | --- | --- |
| 支付成功 | `OmsPortalOrderServiceImpl.paySuccess` | 用户支付成功时 | 订单支付成功 | 您的订单已支付成功，商家将尽快发货。 |
| 确认收货 | `OmsPortalOrderServiceImpl.confirmReceiveOrder` | 用户确认收货时 | 订单已完成 | 您已确认收货，感谢您的购买。 |
| 后台发货 | `OmsOrderServiceImpl.delivery`（mall-admin） | 管理员发货时 | 订单已发货 | 您的订单已发货，请注意查收。 |

### 2.3 营销通知（type=3）

| 环节 | 说明 |
| --- | --- |
| **谁发送** | ① 运营人员（mall-admin 发布）② 业务系统（领券成功自动触发） |
| **发送接口/方法** | ① `POST /message/create` ② `UmsMemberMessageService.sendMarketingNotice`（`UmsMemberCouponServiceImpl.add` 领券成功后调用） |
| **什么时候发送** | ① 运营手动发布促销/活动 ② 用户领取优惠券成功时 |
| **数据保存** | type=3 + biz_id=优惠券ID；广播（send_type=1）只写主体惰性物化；定向（send_type=0）立即写接收记录 |
| **谁接收** | 广播：全站用户；定向：运营指定用户 / 领券用户本人 |
| **前端展示** | 消息中心"营销"Tab；标题、内容、时间、未读红点 |
| **点击行为** | 标记已读 + 跳转领券页 `/pages/coupon/couponDetail?couponId={bizId}` → 用户可"立即领取"（`POST /member/coupon/add/{couponId}`，受 count/领取时间/限领数控制） |

**营销通知触达链路（通知引导领取）：**

```
运营发布营销通知（type=3, bizId=优惠券ID）
      ▼
用户消息中心"营销"Tab 看到（未读红点）
      ▼
点击 → 领券页（展示券详情：金额/门槛/有效期/剩余量）
      ▼
立即领取 → 限时限量先到先得
      ▼
领券成功 → 自动再发"券已到账"通知（可跳"我的优惠券"）
```

---

## 3. 接收与读取细节

### 3.1 定向 vs 广播对比

| 维度 | 定向（send_type=0） | 广播（send_type=1） |
| --- | --- | --- |
| 发布时写主体 | ✅ | ✅ |
| 发布时写接收记录 | ✅ 立即为指定用户写 | ❌ 不写 |
| 接收记录生成 | 发送时 | 用户首查时惰性物化（`materializeBroadcast`） |
| 接收记录时间 | 发送时间 | 取消息 createTime（保证排序一致） |
| 适用场景 | 订单通知、指定用户营销 | 系统公告、全员促销 |

### 3.2 用户端接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/member/message/list?type=&pageNum=&pageSize=` | 消息分页列表（type 为空查全部；先物化再查询） |
| GET | `/member/message/unread-count` | 未读消息数量（先物化再统计 read_status=0） |
| PUT | `/member/message/read/{id}` | 单条标记已读 |
| PUT | `/member/message/read-all?type=` | 全部已读（可指定类型） |

---

## 4. 前端展示与交互

### 4.1 消息中心页（pages/message/message）

| 元素 | 说明 |
| --- | --- |
| Tab | 全部 / 系统 / 订单 / 营销（对应 type 空/1/2/3） |
| 列表项 | 标题 + 内容摘要 + 时间 + 未读红点 |
| 全部已读 | 右上角按钮（`PUT /read-all`） |
| 分页 | onReachBottom 触底加载更多 |
| 下拉刷新 | enablePullDownRefresh |
| 空态 | "暂无消息"占位 |

### 4.2 未读红点（全站）

- `stores/message.ts`：30s 轮询 `unread-count`（App 启动/登录后开启，退出停止）
- 页面 `onShow` 刷新未读数
- 进入消息中心 / 点击消息 → 本地递减 + 调已读接口

### 4.3 点击行为映射

| 消息类型 | bizId 含义 | 点击跳转 | 目标页面 |
| --- | --- | --- | --- |
| type=2 订单 | 订单ID | 跳订单详情 | `/pages/order/orderDetail?orderId={bizId}` |
| type=3 营销 | 优惠券ID | 跳领券页 | `/pages/coupon/couponDetail?couponId={bizId}` |
| type=1 系统 | 无业务含义 | 仅标记已读 | 不跳转 |

---

## 5. 接口/文件索引

**后端（mall）**
- 实体：`UmsMessage` / `UmsMemberMessage`（mall-mbg）
- 用户端：`UmsMemberMessageController`（/member/message）→ `UmsMemberMessageServiceImpl`
- 管理端：`UmsMessageController`（/message）→ `UmsMessageServiceImpl`（POST /message/create）
- 订单触发：`OmsPortalOrderServiceImpl`（支付成功/确认收货）、`OmsOrderServiceImpl`（发货）

**前端（mall-app-web）**
- 页面：`pages/message/message.vue`、`pages/coupon/couponDetail.vue`
- 接口封装：`apis/message.ts`、`apis/coupon.ts`
- 状态：`stores/message.ts`（未读数 + 轮询）
- 类型：`types/message.d.ts`

---

## 6. 待办/扩展

- **定时发送**：`publish_time` + 定时扫描（管理端预约发布，如秒杀开售提醒）
- **已读回执/推送**：如需秒级实时，可引入 WebSocket 信号（当前 30s 轮询够用）
- **消息删除**：接收记录 deleted 逻辑删除（接口预留）
