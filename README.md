# douyu-connect

Java 17 的独立斗鱼连接库：动态连接/断开房间、同房间有序消息分类订阅、可选账号发送、发送参数更新和重连。无需 Spring、数据库或 Redis。

只解析和转发协议消息。潘多拉/梦幻岛关联、礼物去重、连击增量、价格查询、金库记账和主房间规则由业务代码处理。

## 构建与运行

需要 JDK 17+ 和 Maven 3.9+：

```shell
mvn -B -ntp verify
mvn -B -ntp install
java -jar douyu-connect-example/target/douyu-connect-example-0.1.0-SNAPSHOT.jar --help
```

`core` 是可复用的普通 JAR；`example` 是包含依赖的可执行 JAR。业务项目安装本地构件后添加：

```xml
<dependency>
  <groupId>io.github.douyuconnect</groupId>
  <artifactId>douyu-connect-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

匿名观察指定房间 10 秒（不加载账号、不发送弹幕）：

```shell
java -jar douyu-connect-example/target/douyu-connect-example-0.1.0-SNAPSHOT.jar --douyu-tls --observe 4489985 10
```

也可以只带 `--douyu-tls` 启动交互示例，输入 `connect 房间号`、`disconnect 房间号`、`status 房间号`、`reconnect 房间号 SEND`、`quit`。不带该选项则保留系统默认 TLS 配置。请使用实际数字房间 ID；短号/链接解析不在模块范围内。

**TLS 兼容模式已正式提供：** 本机系统默认 TLS 策略与斗鱼端点不兼容，可显式选择 `TlsMode.DOUYU_COMPATIBLE`。它使用已验证的 TLS 1.2 套件，并保留证书及主机名校验；必须在进程首次 TLS 初始化前启用，影响范围见下文。正式程序已完成真实匿名接收验证，3 秒收到 11 个协议包（包含聊天消息）。历史真实发送测试得到 `391`，现已映射为 `ACCOUNT_VERIFICATION_REQUIRED`（需要账号安全验证），不能算成功发送。详情见 [验证记录](docs/verification.md)。

## 快速接入

```java
import io.github.douyuconnect.*;
import io.github.douyuconnect.config.*;
import io.github.douyuconnect.message.*;

// 在进程首次 TLS 初始化前选择；此模式会调整 JVM 内存中的 JSSE 安全属性。
DouyuClient client = new DouyuClient(ClientOptions.defaults(), TlsMode.DOUYU_COMPATIBLE, event -> {
    // 状态、认证失败、发送拒绝原因、回调异常、队列溢出；不要在这里阻塞等待 close()。
    System.out.println(event);
});

// 一个订阅同时接收 dgb 与潘多拉广播，保持同房间交付顺序。
Subscription gifts = client.subscribe(
    MessageFilter.categories(Category.GIFT, Category.PANDORA_BROADCAST),
    message -> {
        if (message instanceof GiftMessage gift) {
            // gift.giftId(), gift.propId(), gift.count(), gift.hits()
            // 此处调用你自己的礼物/连击业务。
        } else if (message instanceof PandoraBroadcastMessage pandora) {
            // pandora.quantityText(), pandora.propId(), pandora.chatFields()
            // 此处调用你自己的潘多拉关联业务。
        }
    }
);

// 订阅先注册，以覆盖刚连接时到达的消息。
client.connect("4489985", ConnectionConfig.receiveOnly())
    .toCompletableFuture().join();

// 动态断开：关闭该房间全部通道，停止自动重连，排空已接纳的回调。
client.disconnect("4489985").toCompletableFuture().join();

// 再连接时原来的订阅继续生效。
client.connect("4489985", ConnectionConfig.receiveOnly())
    .toCompletableFuture().join();

// 生命周期结束时：
gifts.cancel().toCompletableFuture().join();
client.close();
```

以上 `.join()` 只适合应用入口等外部线程。消息回调、客户端事件回调中可以发起异步操作，但不能阻塞等待自身的 `disconnect`、`cancel` 或 `close`。

## 订阅方式

```java
// 类型化订阅
Subscription chat = client.subscribe(ChatMessage.class, message -> {
    System.out.println(message.text());
});

// 单房间＋多类型筛选；无需先连接房间
Subscription roomGifts = client.subscribe(
    MessageFilter.room("4489985")
        .and(MessageFilter.categories(Category.GIFT, Category.PANDORA_BROADCAST)),
    message -> { /* 业务处理 */ }
);

// 原始协议类型/子类型筛选。null 表示不限制该字段。
Subscription broadcast = client.subscribe(
    MessageFilter.protocol(null, "pandora"), message -> { /* 原始字段可用 */ }
);

// 所有接收通道消息，包括控制响应、未知类型和带诊断信息的解析异常消息
Subscription all = client.subscribe(MessageFilter.all(), message -> {
    var fields = message.context().rawFields();
    var original = message.context().rawText();
});
```

- 订阅属于客户端，与底层连接独立。全局及按房间筛选的订阅都覆盖未来连接的房间，断开/重连不会取消订阅。
- 一个订阅的多类型条件为“任一匹配”，每个接收报文在该订阅最多交付一次。不同订阅各自接收；上游重复报文仍正常交付。
- 同一房间同步执行各订阅回调。不同房间可以并发调用同一个处理器，处理器应按房间隔离状态或保证线程安全。
- `cancel()` 立即停止接纳该订阅的新回调，完成时该订阅已运行的回调也已退出。已取消订阅不再接收队列中的待处理消息。
- 监听器异常被隔离并上报 `CALLBACK_ERROR`，不会中止其他监听器。

## 消息分类及字段

| Category | Java 对象 | 协议依据 | 便捷字段 |
|---|---|---|---|
| CHAT | ChatMessage | type=chatmsg | userId、nickname、avatar、text、messageId |
| GIFT | GiftMessage | type=dgb | userId、nickname、avatar、giftId、propId、giftName、count、hits、recipientName |
| FANS_BADGE | FansBadgeMessage | type=dfobc/dfrbc | action=OPEN/RENEW；手动构造且类型不匹配时为 UNKNOWN，另有 userId、nickname、avatar、recipientName、months、rawPrice |
| NOBLE | NobleMessage | type=anbc | userId、nickname、avatar、recipientName、level |
| PANDORA_BROADCAST | PandoraBroadcastMessage | 广播子类型 btype=pandora | userId、giftName、quantityText、propId、chatFields |
| VOICE_DANMU | VoiceDanmuMessage | 广播子类型 btype=voiceDanmu | userId、rawPrice、chatFields |
| ROOM_STATUS | RoomStatusMessage | type=rss | status（原始 ss） |
| GENERIC | GenericMessage | 其他类型 | context 中的全部原始字段 |

已知直接类型（例如 dgb）优先匹配；其余消息再按已知 btype 匹配。嵌套 `chatmsg` 只作为嵌套数据，不另外生成聊天消息。`ssd` 不等同于 `voiceDanmu`，首版作为通用消息。

`GiftMessage` 不代表“已确认的普通礼物”。gfid、pid 分别保留，不将 gfid=0 改成 pid_xxx；gfcnt 和 hits 分别提供，不求差、不相乘。潘多拉 txt5 保留原始文案，不提取数字凑出数量。价格保留协议数值，不折算金额或贡献值。免费礼物、未知消息均不会按业务规则过滤。

数字便捷字段为可空 `Long`，缺失/空字段返回 null，格式错误还会加入 `context.parseIssues()`；原始值仍在 `rawFields`。完整字段以不可变 `Map<String,String>` 保存，嵌套 STT 字段按需使用 `context.nested("chatmsg")` 解码。未知字段不会在模型映射时丢失；原文也保留供诊断。

消息信封包含 `roomId`（连接房间）、`roomInstanceId`、`connectionId`、`sequence`、`receivedAt`、`type`、`btype`、`rawFields`、`rawText`、`parseIssues`。广播原始 rid 不被连接房间号覆盖。

所有消息均提供标准 JavaBean getter。原协议字段按原名访问，如 `getUid()`、`getRid()`、`getGfid()`、`getGfcnt()`、`getHits()`；字段含义、缺失值与是否换算均有中文 Javadoc。原字段 getter 返回原始字符串，因此 `getGfcnt()` 可保留 `"0002"`，已有 `count()`/`getCount()` 则提供可空 Long 视图。旧的 `userId()`、`giftId()` 等调用方式继续可用。

```java
client.subscribe(GiftMessage.class, gift -> {
    String senderUid = gift.getUid();
    String protocolRoomId = gift.getRid();             // 原始 rid，缺失时为 null
    String connectionRoomId = gift.getConnectionRoomId(); // 从哪个房间连接收到
    String countText = gift.getGfcnt();                // 不计算连击增量
    String recipientName = gift.getReceiveNn();
});
```

钻粉动作只显式匹配两个类型：`dfobc -> OPEN`、`dfrbc -> RENEW`。未知/空/null 类型不能推断为续费；手动构造 FansBadgeMessage 时返回 UNKNOWN，分类器本身仍只将 dfobc/dfrbc 分类为钻粉。潘多拉和语音消息的 `getChatmsg()` 保留嵌套 STT 原值，`getChatFields()` 返回内层 Map，`getChatNn()`/`getChatIc()` 访问原 Handler 读取的内层昵称/头像。

字段清单以原项目 BaseMessage 和各 Handler 已定义/读取的字段为依据，见 [消息字段与 getter 对照](docs/message-fields.md)。未来上游新增的未知字段仍通过 getRawFields() 保留，不声称静态模型已穷尽所有版本的协议字段。

## 顺序和连续性

每个房间独立串行分发，礼物与潘多拉等类别不拆成并行执行器。顺序指连接接收/解码顺序，不等于服务端事件发生顺序，也不保证不同房间之间的全局顺序。业务回调自行异步提交的任务由业务保证顺序。

同一逻辑房间重连时，本地 sequence 继续递增，connectionId 改变；主动断开完成后再次 connect 会创建新的 roomInstanceId。sequence 不是服务端唯一 ID，不能用于跨断线去重。断线没有历史补发承诺。

发送连接只用于控制和发送回执，不将其附带的聊天/礼物广播重复推入接收消息流。

回调在共享工作池执行，不占用 Netty I/O 或心跳线程；同房间慢回调会延迟后续消息。队列达到上限时会报告 `QUEUE_OVERFLOW`，停止该房间全部连接、排空已接纳消息，不静默跳过后继续伪装连续。业务收到异常后应等待 disconnect 完成，再显式 connect；被拒绝的报文无法恢复。

## 连接与重连

| 方法 | 语义 |
|---|---|
| connect(roomId, config) | 创建并连接房间；相同配置重复调用幂等；不同配置明确失败 |
| disconnect(roomId) | 取消心跳与重试、关闭收发通道、排空已接纳回调；不取消订阅；不存在时成功 |
| reconnect(roomId, RECEIVE/SEND/ALL) | 保留逻辑房间和订阅，按当前配置替换指定通道 |
| updateSender(roomId, sender) | 替换发送配置并重建发送通道；接收连接继续运行 |
| status(roomId) | 接收/发送各自状态、房间实例、配置版本；不存在返回 DISCONNECTED |
| roomIds() | 返回管理中的房间集合，包含连接中、重试中和断开排空中的房间 |
| closeAsync()/close() | 断开全部房间、取消订阅、限时等待线程池与传输资源关闭 |

`connect`/`reconnect`/`updateSender` 的异步结果代表本次连接尝试；失败会返回异常，网络类失败仍在后台指数退避重试，后续恢复通过 `ClientEvent` 或 `status` 观察。就绪意味着收到成功 loginres 并完成入组/初始化请求的网络写入，不声称已得到协议不存在的“入组确认”。

状态为 DISCONNECTED、CONNECTING、AUTHENTICATING、READY、RETRY_WAIT、AUTH_FAILED。接收/发送分别维护。明确认证失败不无限重试，需要业务更新凭据或显式重连。主动 disconnect 后不会自动恢复。

disconnect 完成后不会再交付旧连接消息；新连接使用新代次，迟到握手、旧定时器和旧关闭通知不能复活或污染新连接。断开正在排空时，connect 会明确拒绝，请先等待断开完成。回调长期阻塞会使断开超时报错；超时不表示该回调已被强制终止。

## 发送和动态凭据

```java
Credentials credentials = new Credentials(
    deviceId,       // acf_devid；旧对象字段名 acf_did
    userId,         // acf_uid
    username,       // acf_username
    loginTicketId,  // acf_ltkid
    sessionToken,   // acf_stk
    1               // acf_biz；沿用原项目默认值
);

// 初次连接时启用发送
client.connect(roomId, ConnectionConfig.withSender(credentials));

// 已有接收连接时启用发送，或用完整新凭据快照替换
client.updateSender(roomId, SenderConfig.enabled(newCredentials));

// 如需先彻底断开，也可断开完成后使用新配置 connect
client.disconnect(roomId).thenCompose(ignored ->
    client.connect(roomId, ConnectionConfig.withSender(newCredentials)));

client.sendChat(roomId, "弹幕内容").thenAccept(result -> {
    System.out.println(result.status());
    System.out.println(result.serverCode()); // 例如 391
    System.out.println(result.reason());     // ACCOUNT_VERIFICATION_REQUIRED
    System.out.println(result.message());    // 需要账号安全验证
    var fields = result.rawResponse();       // 完整且不可变的 chatres 字段，如 cd、len
});

// 只关闭发送能力
client.updateSender(roomId, SenderConfig.disabled());
```

凭据是不可变快照，字符串输出脱敏；不读取若依配置服务、不自动密码登录、不打印登录报文。每房间最多一个发送账号。不同房间可以使用不同账号；同一账号跨多个房间的全局限频由业务协调，模块的发送间隔按房间控制。

格式非法的配置在创建时拒绝，原连接不受影响。有效配置接纳后增加 configVersion，停止旧通道并等待关闭，再用新配置登录。连续更新只启用最新配置，旧操作以取消异常结束。登录失败不会自动回滚旧账号。相同配置重复 updateSender 不触发重连；需要强制重试时调用 reconnect(SEND)。已断开的房间请用新配置 connect。

发送未就绪返回 NOT_READY；有界队列已满返回 QUEUE_FULL。每条请求只发送一次、最多一个在途请求：

| SendResult.Status | 意义 |
|---|---|
| ACKNOWLEDGED | 收到 chatres/res=0；不等于已验证页面可见 |
| REJECTED | 收到非零发送回执，serverCode 保留返回值，reason/message 提供已核实的原因 |
| NOT_READY | 发送通道不可用，本次未入队 |
| QUEUE_FULL | 等待队列已满，本次未入队 |
| CANCELLED | 断开/更新/失败时取消了尚未发出的请求 |
| UNKNOWN | 已尝试写入但未得到可靠回执，不能确定是否送达 |

更新凭据或重连会取消旧等待队列，旧在途请求返回 UNKNOWN。回执超时会替换发送连接，避免迟到回执匹配下一条；不自动重发结果未知的消息。调用方不能直接把 UNKNOWN 当作未送达后重发。

发送回执新增 `reason()`、`message()`、`rawResponse()`；原有 status/serverCode 和两参数构造器继续可用。已验证的码为 2、5、6、206、208、289、290、391，未知码保留原值并返回 UNKNOWN_SERVER_CODE。连接错误码不混入这份映射，完整说明和依据见 [发送回执原因表](docs/send-response-codes.md)。

服务端拒绝还会通过 ClientEvent 回调记录 SEND_REJECTED 事件，包含房间、连接、返回码和原因，不含弹幕正文或完整原始回执。下游可将这些事件写入业务日志。发送 Future 是每次请求的直接结果，回执不另外进入普通消息订阅流。

交互示例中的 `sender 房间号` 从环境变量加载凭据，只有再执行 `send 房间号 内容` 才发送文本。变量为 DOUYU_DEVICE_ID、DOUYU_USER_ID、DOUYU_USERNAME、DOUYU_LOGIN_TICKET_ID、DOUYU_SESSION_TOKEN，可选 DOUYU_BIZ。运行中真正的动态凭据更新由业务调用 API 传入新对象完成。

## 配置、线程与扩展

### TLS 选择

默认构造器保持 `TlsMode.SYSTEM_DEFAULT`，不改变 JVM 安全属性。斗鱼兼容模式显式启用：

```java
var client = new DouyuClient(
    ClientOptions.defaults(), TlsMode.DOUYU_COMPATIBLE, event -> System.out.println(event));
// 也可以直接构造 new NettyTransport(2, TlsMode.DOUYU_COMPATIBLE)。
```

`DOUYU_COMPATIBLE` 固定使用 JDK TLS provider、TLSv1.2 和 `TLS_RSA_WITH_AES_256_GCM_SHA384`，系统信任链和 HTTPS 主机名校验仍然开启。不会信任所有证书，不会自动降级为明文，也不依赖测试程序中的反射。

该方案与已验证的临时方案一致：在 **JVM 进程内存中** 从 `jdk.tls.disabledAlgorithms` 移除 `TLS_RSA_*`/旧格式 `TLS_RSA_` 这一禁用规则，保留其余规则，不改磁盘上的 JDK 文件。这个属性不是单个客户端私有的，可能影响同进程其他 JSSE 使用者；关闭客户端不会恢复它或清空 JSSE 缓存。需要隔离时，将斗鱼连接库放在独立 JVM 中运行。

必须在首次 JSSE/TLS 初始化之前创建兼容模式客户端，例如在启动 Spring 或其他 HTTPS SDK 之前。若旧策略已缓存，或运行环境另有规则禁止这个套件，会在构造客户端时明确失败，不再默默进入握手重试。`SYSTEM_DEFAULT` 也不能撤销同进程中已经发生的兼容模式启用。

该 RSA 密钥交换套件不具备前向保密；本选项用于服务端兼容。可参阅 [Oracle JDK 安全更新说明](https://www.oracle.com/java/technologies/javase/17all-relnotes.html)。

`ClientOptions` 默认连接/登录超时 10s，心跳 45s，静默阈值 90s，重试基础 1s、上限 60s（含抖动），发送间隔 1500ms，回执超时 10s，关闭等待 5s，消息队列 4096，发送等待队列 100，回调线程 4，协议包长度上限 128KiB。这些是模块初始参数，不代表平台承诺的限制。静默检测在心跳检查点执行。

`ConnectionConfig.receiveEndpoints` 与 `SenderConfig.endpoints` 支持自定义 ws/wss 地址，默认使用原项目的斗鱼端点。生产默认 wss，验证系统信任链与主机名。本地测试使用明确传入的 ws 地址，不会把生产连接自动降级为 ws。

`Transport` 是异步传输扩展接口，可用于业务适配或测试注入；同一连接的回调必须有序。传入的 Transport 由客户端拥有并在关闭时释放。公共异步结果与内部锁隔离，后续任务通过 CompletableFuture 默认异步设施交付；推荐业务显式指定自己的执行器处理耗时的 Future 回调。

状态通知有独立的有界队列。状态监听器应快速返回；通知队列耗尽会记录警告，不阻塞连接控制。业务也可以通过 status 查询当前状态。

## 验证及来源

执行 `mvn -B -ntp verify` 运行离线测试和本地真实 WebSocket 模拟服务测试，不需要账号和外网。

原项目保持只读。协议模板来源、改动和许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，本仓库许可见 [LICENSE](LICENSE)。
