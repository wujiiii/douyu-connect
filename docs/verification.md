# 验证记录

日期：2026-09-22。

环境：Windows、Temurin JDK 17.0.18.8、Maven 3.9.15。

## 自动验证

`mvn -B -ntp verify` 已通过 55 项测试：

- ProtocolTest：4 项，小端包、拆包/粘包的每个拆分位置、直接缓冲区、中文/Emoji、单层转义、空值、非法长度/重复长度/终止符。
- CommandsTest：2 项，普通接收 loginreq 的完整访客模板、字段顺序、随机数范围，以及发送连接的 Cookie 参数和独立版本字段。
- ClassificationTest：6 项，支持类型映射、未知类型、礼物字段不做业务计算、潘多拉嵌套消息、数量文案、空数字/错误数字、钻粉动作和原始房间字段；未知/空/null 类型不能误判为续费。
- MessageGetterTest：12 项，逐类验证原字段 JavaBean getter、原值/转义/前导零保留、缺失字段、旧便捷方法兼容、广播嵌套字段、原始房间号与连接元数据区分。
- ClientTest：18 项，跨类型顺序、订阅取消、断开排空、动态再连接、重复网络失败恢复、旧握手/旧关闭隔离、发送配置切换、回执超时、回调异常隔离、凭据脱敏、幂等连接、认证失败、房间过滤、队列溢出、连续配置更新、停用发送、错误类型诊断、资源关闭、带原因的拒绝回执/事件，以及启用发送时接收通道和接收重连仍使用访客登录（单个测试可能覆盖多种行为）。
- NettyIntegrationTest：2 项，本地 WebSocket 服务端实测握手、loginres、入组、WebSocket 分片重组、有序礼物/潘多拉接收、成功/拒绝回执及扩展字段传递、凭据更新只重连发送通道、主动断开。
- SendResultTest：4 项，已证实拒绝码映射、未知码保留、错误码命名空间隔离、原始回执不可变、输出不包含完整回执、本地结果及原两参数构造器兼容。
- TlsCompatibilityTest：7 项，每项使用独立 JVM。本地生成测试证书，验证系统默认模式不改策略、兼容模式固定协议/套件且幂等、过晚初始化明确失败、额外禁用规则不被移除、可信 WSS 握手成功、不可信证书与错误主机名被拒绝。

使用伪造的测试凭据，本地模拟服务不与斗鱼通信。测试不能证明平台当前协议或账号策略未发生变化。

普通接收 loginreq 已按用户给定格式补齐 dfl、随机 visitor 用户名、随机 uid、ver=20220825、aver=218101901、ct=0。使用 JDK ThreadLocalRandom，随机范围均为左闭右开；发送连接仍保留原有 Cookie 登录模板。本次模板更新仅进行自动和本地模拟验证，没有再连接真实房间或发送弹幕；下文真实收流记录发生在此更新之前。

## 原字段 getter 与注释验证

消息 getter 清单以原项目 BaseMessage 及各 Handler 为依据。开发时只读核对原 BaseMessage 的 52 个字段，全部包含于公共 getter 与 GenericMessage 的兼容集合中；专用消息按相应类型提供原字段 getter，Handler 中补充的 pid、receive_nn、txt4/5/6、chatmsg、crealPrice 等字段另行覆盖。字段列表见 message-fields.md。

新增原字段 getter 返回 String，保留原字段内容，不替代原有数值便捷方法；原方法继续兼容。钻粉明确检查 dfobc 为 OPEN、dfrbc 为 RENEW，其他类型返回 UNKNOWN。

对整个 message 包执行 `javadoc -quiet -encoding UTF-8 -docencoding UTF-8 -Xdoclint:all -d douyu-connect-core/target/message-javadoc -sourcepath douyu-connect-core/src/main/java io.github.douyuconnect.message`，退出码 0，无警告、无错误。本次字段变更未连接真实斗鱼房间，也未发送弹幕。

## 正式兼容模式的真实匿名接收

命令：`java -jar douyu-connect-example/target/douyu-connect-example-0.1.0-SNAPSHOT.jar --douyu-tls --observe 4489985 3`。

结果：进程退出码 0；TLS_MODE=DOUYU_COMPATIBLE；接收通道依次进入 CONNECTING、AUTHENTICATING、READY，随后按请求主动断开。3 秒内收到 11 个协议包，其中至少一条被分类为 CHAT（本地 sequence=9）。使用正式 NettyTransport 和配置 API，没有临时反射或测试凭据。

这次验证没有启用发送通道、没有发送弹幕。真实发送成功与账号安全验证问题仍不在此结果的保证范围内。

## 历史：默认策略的真实接收探测

以匿名接收方式尝试房间 4489985，端点 `wss://danmuproxy.douyu.com:8501/`。

结果：未通过真实收流验收。TLS 握手阶段服务端返回 `SSLHandshakeException: Received fatal alert: handshake_failure`，未进入 WebSocket/斗鱼登录阶段。单独使用 JDK TLSv1.2 socket 对该端点以及旧 Netty 模块的 `danmu.douyu.com:8501` 探测，也复现握手失败；仅指定 TLSv1.2 未解决问题。后续发送探测进一步确认了 TLS 套件兼容问题，见下文。

这轮匿名接收探测未禁用证书/主机名校验、未修改 JVM 安全策略、未回退到明文连接。需要在目标部署环境进一步确认真实收流。

## 用户授权的一次真实发送测试

用户明确指定向房间 4489985 发送一次文本 `1`。凭据仅通过测试子进程环境传入，不写入源码、文档或提交。

1. 默认库依次尝试发送端口 6671 至 6675，均在 TLS 阶段失败，弹幕写入次数为 0。
2. OpenSSL 握手探测协商到 TLSv1.2 / AES256-GCM-SHA384；该探测工具本地信任库校验失败，因此没有通过它发送凭据。
3. 本机 JDK 安全策略禁用了 `TLS_RSA_*`。在独立、临时的测试进程中仅去掉这一规则，并显式选择 `TLS_RSA_WITH_AES_256_GCM_SHA384`，其他安全规则保持不变；使用系统默认信任链及 HTTPS 主机名校验，TLS 握手成功。
4. 临时测试适配保留 SDK 的连接、登录、发送及回执处理流程，在 `wss://wsproxy.douyu.com:6671/` 登录成功，返回的账号 ID 与用户提供的账号匹配。
5. 对 `sendChat(roomId, "1")` 实际发起一次网络写入，接收到服务器 `chatres/res=391`。
6. 调用方异步结果收到 `SendResult(status=REJECTED, serverCode=391)`。实际弹幕写入次数为 1，没有重发。

后续查阅斗鱼官方前端后已确认：`391` 触发账号安全验证流程。正式代码已映射为 ACCOUNT_VERIFICATION_REQUIRED；`res=0` 才判为 ACKNOWLEDGED，因此本次不算成功发送/页面可见验收。

这次历史测试的回执通过 `sendChat` 的 CompletionStage 返回调用方，不额外进入普通消息订阅流。后续正式代码已经扩展 SendResult，包含 reason、message、完整 rawResponse，并通过 SEND_REJECTED 客户端事件记录拒绝原因。

这次历史测试使用忽略目录 target 中的临时程序，进程结束后环境凭据清除，没有更改系统 JDK 文件。后续正式库已提供显式的 DOUYU_COMPATIBLE 模式；默认 SYSTEM_DEFAULT 仍保持系统策略，使用方法及进程影响见 README。
