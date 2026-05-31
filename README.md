# 黑马点评 (hm-dianping)

基于 Spring Boot 的本地生活服务点评平台，实现了商户查询、优惠券秒杀、用户签到、关注推送、探店笔记等核心功能。

## 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot 2.3.12 | 基础框架 |
| MyBatis-Plus 3.4.3 | ORM 框架 |
| MySQL 8.0 | 关系型数据库 |
| Redis + Lettuce | 缓存 / 分布式锁 / 消息队列 |
| Redisson 3.13.6 | 分布式锁（可重入锁） |
| Hutool 5.7.17 | Java 工具库 |
| Lombok | 简化代码 |

## 项目结构

```
src/main/java/com/hmdp/
├── controller/          # 接口层
│   ├── UserController       # 用户：登录、签到、信息
│   ├── ShopController       # 商户：增删改查、附近搜索
│   ├── BlogController       # 笔记：发布、点赞、关注推送
│   ├── VoucherController    # 优惠券：普通券、秒杀券
│   ├── VoucherOrderController # 秒杀下单
│   ├── FollowController     # 关注：关注/取关、共同关注
│   ├── BlogCommentsController # 评论
│   ├── ShopTypeController   # 商户类型
│   └── UploadController     # 文件上传
├── service/impl/        # 业务实现
├── mapper/              # MyBatis Mapper
├── entity/              # 实体类
├── dto/                 # 数据传输对象
├── config/              # 配置类
└── utils/               # 工具类
    ├── CacheClient          # 缓存工具（解决穿透、击穿）
    ├── SimpleRedisLock      # Redis 分布式锁（Lua 脚本释放）
    ├── RedisIdWork          # Redis 分布式 ID 生成器（雪花算法）
    ├── RedisConstants       # Redis Key 常量
    ├── UserHolder           # ThreadLocal 用户上下文
    └── RegexUtils           # 正则校验工具
```

## 核心功能

### 1. 用户登录
- 手机验证码登录（Redis 存储验证码，2 分钟过期）
- Token 基于 Redis 实现无状态登录，支持分布式会话

### 2. 商户查询与缓存
- 商户 CRUD 操作
- **缓存穿透**：空值写入 Redis（TTL 2 分钟）
- **缓存击穿**：Redis 分布式锁 + 逻辑过期
- **缓存雪崩**：TTL 添加随机值，避免同时失效
- **附近商户**：Redis GEO 地理位置查询，按距离排序

### 3. 优惠券秒杀
- **原子操作**：Lua 脚本实现库存校验、一人一单判断、扣减库存
- **异步下单**：Redis Stream 消息队列，消费者异步创建订单
- **分布式锁**：Redisson 可重入锁，防止同一用户重复下单
- **Pending List**：消息消费失败时重试处理

### 4. 探店笔记
- 发布探店笔记（图文）
- 点赞功能（Set 去重，一人一赞）
- 关注推送（Redis Feed Stream，推送到粉丝收件箱）
- 滚动分页查询（基于游标分页）

### 5. 用户签到
- Redis Bitmap 实现签到打卡
- 统计当月累计签到天数（BITCOUNT）
- 签到详情（BITFIELD 位图查询）

### 6. 关注功能
- 关注 / 取消关注
- 共同关注（Set 交集运算）
- 关注推送 Feed 流

## 环境要求

- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

## 快速启动

1. **初始化数据库**

```sql
-- 执行 SQL 脚本创建数据库和表
source src/main/resources/db/hmdp.sql
```

2. **配置应用**

编辑 `src/main/resources/application.yaml`，修改数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/hmdp
    username: root
    password: your_password
  redis:
    host: 127.0.0.1
    port: 6379
    password: your_password
```

3. **启动项目**

```bash
mvn spring-boot:run
```

服务默认运行在 `http://localhost:8081`。

## API 接口概览

| 模块 | 接口 | 说明 |
|------|------|------|
| 用户 | `POST /user/code` | 发送验证码 |
| 用户 | `POST /user/login` | 登录 |
| 用户 | `POST /user/sign` | 每日签到 |
| 商户 | `GET /shop/{id}` | 查询商户（带缓存） |
| 商户 | `GET /shop/of/type?typeId=&x=&y=` | 附近商户 |
| 笔记 | `POST /blog` | 发布笔记 |
| 笔记 | `PUT /blog/like/{id}` | 点赞 |
| 笔记 | `GET /blog/of/follow` | 关注的人的笔记 |
| 优惠券 | `POST /voucher/seckill` | 创建秒杀券 |
| 优惠券 | `POST /voucher-order/seckill/{id}` | 秒杀下单 |
| 关注 | `PUT /follow/{id}/{isFollow}` | 关注/取关 |
| 关注 | `GET /follow/common/{id}` | 共同关注 |
