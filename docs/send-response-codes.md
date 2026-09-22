# 弹幕发送回执与拒绝原因

映射核对日期：2026-09-22。本表只适用于 `type=chatres` 的 `res` 字段，不适用于连接错误 `type=error/code`、HTTP error 或其他发送功能的返回值；不是完整官方协议码表。

| res | SendFailureReason | 中文说明 |
|---|---|---|
| 0 | NONE | 服务器确认发送成功（不保证页面可见） |
| 2 | ROOM_MUTED | 用户已被禁言 |
| 5 | GLOBAL_MUTED | 用户被全站禁言 |
| 6 | FANS_ONLY | 房间仅允许粉丝发言 |
| 206 | INVALID_OPERATION | 非法操作 |
| 208 | TARGET_USER_NOT_FOUND | 未找到目标用户 |
| 289 | DUPLICATE_MESSAGE | 重复发言 |
| 290 | RATE_LIMITED | 发言过于频繁 |
| 391 | ACCOUNT_VERIFICATION_REQUIRED | 需要账号安全验证 |
| 其余非零值 | UNKNOWN_SERVER_CODE | 未知服务端拒绝原因 |

证据来自斗鱼自身发布的前端脚本：

- [弹幕区脚本](https://shark2.douyucdn.cn/front-publish/live-next-player-aside-master/js/live-next-player-aside_90964cf.js)：`BARRAGE_ERROR` 映射包含 2、5、6、206、208、289、290；chatres 收到 391 时加载 accountSecurity 并触发 securityData。
- [账号安全模块](https://shark2.douyucdn.cn/front-publish/live-next-master/js/room/accountSecurity_2bad9cc.js)：根据服务端给出的 validate_type 展示极验、手机或邮箱验证。391 只映射为需要安全验证，不推断具体风控原因或验证方式。

308 仅找到前端设置错误状态；288、356、363 在普通提示分支被跳过。未确认其 chatres.res 业务语义，因此均保留 UNKNOWN_SERVER_CODE。不能将其他“喇叭发送”接口对 356 的解释直接套用，也不能将 error/code=4202 的 Cookie 过期解释套到 chatres/res=4202。

## 下游接口

`sendChat()` 的异步返回值包含：

- `status()`：原有 ACKNOWLEDGED、REJECTED、NOT_READY、QUEUE_FULL、CANCELLED、UNKNOWN。
- `serverCode()`：原始 res，未知码不丢失。
- `reason()`：稳定的原因枚举。
- `message()`：中文原因。
- `rawResponse()`：不可变的完整回执字段，包括 res、cd、len、未知扩展字段；不包含其他报文或登录凭据。

本地未就绪/队列满/取消/未知送达结果，也有独立原因，不伪装成服务端拒绝码。`rawResponse` 在这些本地结果上为空。

每个服务端拒绝回执会通过现有 ClientEvent 回调产生 SEND_REJECTED 事件，带房间、发送通道、连接 ID、码与原因，供业务记录日志；不打印弹幕正文或完整原始回执。SendResult.toString 也排除 rawResponse。未来取消或断开不会因此重发消息。

回执结果仍通过发送 Future 交付，不混入普通消息订阅。业务需要更细的限频信息时可自行读取 rawResponse 中的 cd 等字段，模块不自动改变业务策略。
