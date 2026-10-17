# 营销通知（type=3）设计方案

> 基于现有消息通知体系（`ums_message` + `ums_member_message`）扩展，与已实现的系统通知（type=1）、订单通知（type=2）共用同一套接收、未读、已读机制。

---

## 1. 设计目标

- 支持**主动发布**与**业务自动触发**两种营销消息来源
- 支持**广播 / 定向**两种发送方式（定向含**按条件筛选人群**）
- 支持**定时发送**（预约时间自动广播，如秒杀开抢提醒）
- 复用现有消息中心：前端无需新增页面，Tab 内"营销"分类即可

---

## 2. 消息模型（现有，无需改动）

```
ums_message            消息主体（一条消息一份）
├── id / type / send_type / title / content / biz_id / create_time
ums_member_message     接收记录（每用户一份，承载已读/未读状态）
├── id / member_id / message_id / read_status / deleted / create_time
```

- type=3：营销通知
- send_type=0：定向（发布时立即为会员写入接收记录）
- send_type=1：广播（只写主体，用户首查时 `materializeBroadcast` 惰性物化）

---

## 3. 功能设计

### 3.1 主动发布（管理端）

复用 `mall-admin` 的 `POST /message/create`，入参：

```json
{
  "type": 3,
  "sendType": 0,
  "title": "双11大促开启",
  "content": "全场5折起，领券立减…",
  "bizId": null,
  "memberIds": [1, 2, 3]          // sendType=0 时必填
}
```

广播场景：`sendType=1`，不传 memberIds。

### 3.2 大范围优惠券的发放与领取

**现状**：系统领券是用户**主动领取**模式（`POST /member/coupon/add/{couponId}`），受总数 `count`、领取时间 `enableTime`、限领数 `perLimit` 控制。商品券/品类券（useType=1/2）在商品详情页/分类页领取即可，**不依赖通知**。

**大范围券（useType=0 全场券/平台券）统一采用「通知引导领取」模式：**

```
管理端发布营销通知（type=3, bizId=券ID）
        │
        ▼
用户消息中心"营销"Tab 看到通知
        │
        ▼
点击通知 → 前端读 bizId → 跳转领券页（/pages/coupon/couponDetail）
        │
        ▼
POST /member/coupon/add/{couponId}   ← 复用现有领券接口
（受 count / enableTime / perLimit 控制，先到先得）
```

- 前端改动点：消息中心点击跳转增加 type=3 分支（bizId 带券ID → 跳领券页）+ 新增领券页展示券详情
- 后端改动点：新增券详情接口 `GET /member/coupon/detail/{couponId}`（领券页展示）+ 营销通知发送方法（3.3）
- 领券成功后自动发送"券已到账"通知（bizId=券ID），提醒用户到"我的优惠券"使用

**结论**：不做全员直接入账，大范围券一律通过**营销通知引导领取**，领取仍受券本身的 count / enableTime / perLimit 限制。

### 3.3 业务自动触发（各业务点）

与订单通知相同的模式：业务节点调用"发送营销通知"方法。

**已接入/待接入触发点：**

| 场景 | 触发位置 | 消息内容示例 | 发送方式 |
| --- | --- | --- | --- |
| 优惠券领取成功 | `UmsMemberCouponService.add` | "满100减20券已到账" | 定向（当前用户） |
| 限时购/秒杀开售 | 秒杀场次开始（定时任务/MQ） | "10点整限量开抢" | 广播 |
| 会员等级升级 | 等级变更逻辑 | "恭喜升级为金卡会员" | 定向 |
| 节日/活动提醒 | 运营配置 | "会员日双倍积分" | 广播 |

通用方法（mall-portal）：

```java
// 定向营销通知
void sendMarketingNotice(Long memberId, String title, String content, String bizId);
// 定向营销通知（按条件批量）
void sendMarketingNoticeToMembers(List<Long> memberIds, String title, String content, String bizId);
// 广播营销通知
void sendMarketingBroadcast(String title, String content, String bizId);
```

实现均复用 `sendOrderNotice` 的写法（type 改为 3）。

### 3.4 定时发送（扩展点 B）

**方案：MQ 延迟消息 / 定时任务轮询**

- 管理端发布时带 `publishTime`，未到期时仅存 `ums_message`（新增 `status` 字段：0=草稿/1=已发布，或复用 `publish_time` 判断）
- 到期触发：
  - 简单方案：Spring `@Scheduled` 每分钟扫描到期消息 → 置为已发布 → 广播场景触发物化（定向场景发送时已写接收记录）
  - 现有基础设施：项目已有 RabbitMQ 延迟消息（订单超时取消用 `OrderCancelEventPublisher`），可仿照实现 `MessagePublishEventPublisher`

> 建议首期用定时扫描，简单可靠；量大再上延迟队列。

---

## 4. 接口设计汇总

**管理端（mall-admin）**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/message/create` | 发布营销/系统消息（定向/广播）已有 |
| POST | `/message/create/timed` | 定时发布（扩展 B，带 publishTime） |
| GET | `/message/list` | 已发布消息列表（已有） |

**用户端（mall-portal）**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/member/message/list?type=3` | 营销消息列表（已有） |
| GET | `/member/message/unread-count` | 未读数（已有，含营销） |
| PUT | `/member/message/read/{id}` | 单条已读（已有） |
| PUT | `/member/message/read-all?type=3` | 营销全部已读（已有） |

---

## 5. 前端影响

- 消息中心页已有"营销"Tab（`type=3`），基础展示**无需改动**
- 点击跳转需增加 type=3 分支：bizId 为优惠券ID → 跳转优惠券详情/领券页（当前只有订单跳转逻辑）
- 管理端若做营销发布界面：对接 `/message/create`，选择定向（memberIds）/广播即可，无需人群条件组件

---

## 6. 实施步骤（按优先级）

1. **P0 通知领券闭环**：后端券详情接口 + 营销发送方法；前端领券页 + 消息中心 type=3 点击跳转
2. **P1 领券成功通知**：领券成功自动发送"券已到账"通知
3. **P2 定时**：`publish_time` + 定时扫描发布（3.4）
4. **P3 丰富场景**：秒杀开售广播、等级升级定向通知

---

## 7. 风险与注意点

- **广播物化放大**：大促广播用户量大，首次查询物化可能批量 insert；建议物化批量插入（`saveBatch`）并加索引（已有 idx_member_id / idx_message_id）
- **事务一致性**：业务触发通知与主流程同事务，失败回滚不残留脏通知
- **重复发送**：定时扫描需保证幂等（status 状态位防止重复发布）
- **文案合规**：营销消息含强营销属性，管理端需留审核/预览环节
