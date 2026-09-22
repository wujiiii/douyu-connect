# 开发指南

本文件适用于整个仓库。先遵循当前用户任务；以下内容记录项目已经确认的边界和行为，避免后续开发无意改变接口语义。沟通及项目说明默认使用中文。

## 项目定位与阅读顺序

这是 Java 17 的独立斗鱼连接库，采用 Maven 多模块与 Netty，不依赖 Spring、数据库、Redis 或若依运行环境。

模块负责动态建立/断开房间连接、协议解析、消息分类、有序订阅、可选弹幕发送及发送凭据更新。业务负责潘多拉/梦幻岛关联、去重、连击数量计算、价格查询、统计、金库记账及主房间规则。没有新的明确需求时，不将这些业务或管理界面迁入本库。

开始任务时先检查 `git status --short`，再按需要阅读：

1. [README.md](README.md)：公开 API、配置、线程和生命周期语义。
2. [docs/implementation-plan.md](docs/implementation-plan.md)：已确认的模块边界。
3. [docs/send-response-codes.md](docs/send-response-codes.md)：拒绝码映射及来源。
4. [docs/verification.md](docs/verification.md)：历史测试与真实链路验证，注意区分当前结果和历史结果。
5. [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)：协议模板来源与许可。

原始参考项目位于 `D:/Development/RuoYi-Vue`，仅供本机只读参考，不是构建依赖。该项目可能存在用户未提交的改动；除非任务明确要求，不修改它，也不要求其他开发环境存在该路径。

## 代码入口

核心包为 `io.github.douyuconnect`，源码根目录是 `douyu-connect-core/src/main/java/io/github/douyuconnect/`。

| 文件/目录 | 职责 |
|---|---|
| `DouyuClient.java` | 公开入口，连接注册、订阅、操作结果和整体关闭 |
| `RoomSession.java` | 包内房间会话，收发通道状态、重连、心跳、发送队列与回执 |
| `SerialMailbox.java` | 有界的房间串行回调队列 |
| `ListenerSubscription.java`、`MessageFilter.java` | 订阅筛选、异常隔离、取消订阅 |
| `message/` | 分类器、不可变消息对象、原始字段和解析诊断 |
| `protocol/` | 小端数据包、STT 编解码、登录/入组/心跳/发送报文 |
| `transport/Transport.java` | 可注入的异步传输接口，便于测试与扩展 |
| `transport/NettyTransport.java`、`TlsSupport.java` | WebSocket 传输、资源释放和 TLS 模式 |
| `config/` | 不可变凭据、连接参数、端点、客户端选项、TLS 模式 |
| `SendResult.java`、`SendFailureReason.java`、`ClientEvent.java` | 下游发送结果、拒绝原因与客户端事件 |

`douyu-connect-example` 提供可执行命令行示例；入口为 `io.github.douyuconnect.example.Main`。依赖版本以根 `pom.xml` 为准，Netty 通过 BOM 统一管理，不在子模块单独混入另一套版本。

## 必须保持的行为

### 消息解析与顺序

- 同一房间的接收消息，包括 `dgb` 与潘多拉广播，经过同一条串行分发路径。不要按类别拆成并行执行器。
- 顺序指本地接收/解码/同步回调顺序，不保证服务端业务发生顺序、跨房间顺序或业务自行异步提交后的顺序。
- 接收通道是业务消息的来源；不要将发送通道附带的聊天/礼物重复分发。
- `GiftMessage` 表示 `type=dgb`，不表示已经确认为普通礼物。保留 `gfid`、`pid`、`gfcnt`、`hits`，不重写 ID、不求差、不相乘。
- `btype=pandora` 分类为潘多拉广播，不进行开箱关联；梦幻岛礼物仍通过 `dgb` 转发。未知类型通过 `GenericMessage` 保留。
- STT 每次只解码一层；嵌套 `chatmsg` 不额外生成聊天事件。保留空值、原文、未知字段和解析诊断，不将缺失数字默认为 0 或 1。
- 已知原协议字段提供 `getUid()`、`getRid()` 等 JavaBean getter 和中文 Javadoc；原字段 getter 保留字符串原值，原有便捷方法继续兼容。字段清单见 `docs/message-fields.md`，新增已知字段应同步 getter、注释和验证。
- 钻粉动作仅明确匹配 `dfobc -> OPEN`、`dfrbc -> RENEW`；未知、空或 null 类型为 UNKNOWN，不允许将“不是开通”默认视为续费。
- 本地 `sequence` 不是平台唯一 ID，不能承诺去重或断线补发。重连保持房间实例并更换连接 ID；断开完成后再次连接产生新房间实例。
- 队列有上限。当前溢出策略是报告 `QUEUE_OVERFLOW`、主动断开该房间、排空已接纳消息，由业务显式恢复；不能静默丢包后仍声称消息连续。

### 连接、订阅与并发

- 公开操作是 `connect`/`disconnect`，不涉及业务房间表的增删。同房间相同配置重复连接幂等，不同配置明确失败。
- 主动断开必须取消心跳、重连和旧连接任务，完成前排空已接纳回调；意外断线才按策略自动重连。
- 订阅独立于底层连接，断开和重连都不自动取消订阅。取消完成后该订阅不能再运行回调。
- 保留连接代次检查：迟到握手、旧回执、旧关闭通知、旧重连任务不能改变新连接状态。
- 更新发送配置只重建发送通道，不打断接收；连续更新只启用最新配置，并等待旧通道关闭。
- 网络与心跳线程不执行用户消息回调。异步操作的对外完成回调不得在内部房间锁中直接执行，防止重入产生错误发送。
- 不在业务消息/事件回调中阻塞等待自身的 `disconnect`、`cancel` 或 `close`。
- `connect` 等操作的 Future 反映本次尝试，网络失败后的后台恢复通过事件或状态查询观察。`READY` 表示登录响应成功且初始化请求已写入，不虚构入组确认报文。
- 默认接收端点为 `danmuproxy.douyu.com:8501~8504`，发送端点为 `wsproxy.douyu.com:6671~6675`。每房间、每通道独立顺序轮换，不是随机端口；地址可配置。

### 发送与回执

- 每个发送通道最多一个在途请求，等待队列有界。切换凭据/断开时取消未发请求，已尝试写入但无可靠回执的结果为 `UNKNOWN`，禁止自动重发。
- 回执超时后替换发送连接，避免迟到回执匹配下一条请求。
- `SendResult` 保留 `status`、`serverCode`，并提供 `reason()`、`message()` 和不可变 `rawResponse()`。保持原两参数构造器的兼容性。
- 原因映射只适用于 `chatres.res`。`391` 是需要账号安全验证；未查证的码为 `UNKNOWN_SERVER_CODE`，不能混用 `error.code` 或其他 HTTP/喇叭接口的解释。
- 拒绝同时通过 `SEND_REJECTED` 事件通知下游；发送 Future 是该请求的直接结果，回执不混入普通消息订阅流。
- `ACKNOWLEDGED` 只表示成功回执，不等于已验证直播间页面可见。

## TLS 兼容模式

- `SYSTEM_DEFAULT` 是默认模式，不主动修改 JVM 安全属性。
- `DOUYU_COMPATIBLE` 是显式选择的模式，使用 JDK provider、TLSv1.2 和 `TLS_RSA_WITH_AES_256_GCM_SHA384`；必须在首次 JSSE/TLS 初始化前启用。
- 兼容模式会在 JVM 内存中移除 `jdk.tls.disabledAlgorithms` 中的 `TLS_RSA_*`/旧格式 `TLS_RSA_` 规则，保留其他规则。它是进程级影响，不是单连接配置；关闭客户端也不会撤销缓存或恢复隔离。
- 保留默认信任链与 HTTPS 主机名校验，不引入 trust-all、不禁用主机名校验、不修改安装目录中的 JDK 配置文件，不自动回退到明文。
- 若策略已缓存或另有明确限制，构造客户端应明确失败。TLS 策略测试放在独立 JVM，避免污染其他测试；需要验证可信连接成功、不可信证书和错误主机名失败。
- 临时 `target` 目录中的反射测试脚本不是正式实现，不将其复制回生产代码。

## 构建与测试

从仓库根目录运行，需要完整 JDK 17+（包含 `keytool`）和 Maven 3.9+。支持 PowerShell；不要依赖特定机器的绝对路径。

```shell
# 全量验证，包含本地 WebSocket 以及独立 JVM 的 TLS 测试
mvn -B -ntp verify

# 核心模块测试
mvn -B -ntp -pl douyu-connect-core test

# 定向测试；PowerShell 中将 -Dtest 参数整体加引号
mvn -B -ntp -pl douyu-connect-core '-Dtest=ClientTest,SendResultTest' test
mvn -B -ntp -pl douyu-connect-core '-Dtest=TlsCompatibilityTest' test

# 安装本地 Maven 构件，供其他项目引用
mvn -B -ntp install
```

测试职责：协议改动看 `ProtocolTest`，分类改动看 `ClassificationTest`，原字段 getter 看 `MessageGetterTest`，连接/并发看 `ClientTest`，真实本地 WebSocket 收发看 `NettyIntegrationTest`，失败原因看 `SendResultTest`，TLS 看 `TlsCompatibilityTest`/`TlsProcessProbe`。

新增行为或修复缺陷，先添加能复现问题的测试，再实现并执行相应验证；网络/并发场景优先使用伪传输、本地服务器、锁存器和有超时的 Future，不依赖真实账号或任意 sleep。改动代码后完成 `mvn verify`；纯文档改动检查内容、路径及 diff 即可。历史 39 项通过是基线，不是新改动已验证的证据，也不是固定测试数量要求。

## 真实测试与凭据

离线测试和本地模拟测试是默认验证方式。真实弹幕发送是外部写操作：仅在当前任务明确授权的房间、文本和次数范围内执行；历史一次发送授权不代表后续可反复试发。已有明确授权时不重复询问，结果未知时不自行补发。

- 不在源码、文档、测试夹具、Git 提交或日志中保存真实 Cookie、sessionToken、设备标识或登录票据。通过调用方配置或临时子进程环境传入。
- 不打印登录报文、凭据对象内部值、完整回执或弹幕正文。测试使用明显的虚拟数据，保留 `Credentials` 的脱敏输出。
- `target/`、`.env`、日志和临时探测文件不应提交，也不将其当作权威配置。
- `docs/verification.md` 已记录真实匿名接收成功；历史一次真实发送返回 391，被服务器拒绝。不要将其描述为真实发送成功。

需要匿名收流验证时，先构建，再使用有时间上限的观察模式；该命令不启用发送：

```shell
java -jar douyu-connect-example/target/douyu-connect-example-0.1.0-SNAPSHOT.jar --douyu-tls --observe ROOM_ID 3
```

将 `ROOM_ID` 替换为任务涉及的实际数字房间号。记录端点、就绪状态、观察时长、收包结果和退出状态；不要把自动测试、本地模拟、真实收流和真实发送验收混为一谈。

## 修改与交付习惯

- 保持 Java 17 兼容，优先不可变配置/消息，避免静态可变连接状态。保留公共 API 语义，必要变更同步示例与 README。
- 变更拒绝码映射时更新来源记录；依赖统一在父 POM 管理；复用上游代码时保留许可和来源说明。
- 不覆盖已有用户改动，不做无关重构。完成任务后说明实际修改、已执行验证和剩余限制。
- 提交前检查 `git diff --check`、暂存文件列表及测试结果，确保不带构建产物和凭据。用户请求提交时直接完成本地提交，不把提交理解为推送授权。
