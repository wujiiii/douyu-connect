# 消息字段与 getter 对照

本清单依据原项目 `opensource-douyu-barrage/model/BaseMessage.java` 及 `ruoyi-danmu/handler` 已定义、读取的字段整理。协议可能继续扩展，未知字段仍保留在原字段 Map；不猜测其他版本的字段含义。

## 公共 getter

所有消息通过 DouyuMessage 继承以下 getter：

| Getter | 含义 |
|---|---|
| getType() / getBtype() | 原始 type / btype，缺失返回 null |
| getRid() | 报文原始房间字段；缺失返回 null，不用连接房间号补全 |
| getConnectionRoomId() | 接收该报文的连接所属房间 |
| getContext() | 消息信封，含房间实例、连接 ID、序号、接收时间和诊断 |
| getCategory() | SDK 分类 |
| getRawFields() | 包含未知字段的不可变 Map |
| getRawText() | 原始 STT 正文，不含二进制包头和终止 NUL |

MessageContext 的各个 record 分量也都有对应的 getXxx() 和字段注释。context.getRoomId() 是连接房间；context.getType()/getBtype() 保留原 record 方法的行为，分类器在缺失时传入空串，而消息 getType()/getBtype() 读取原字段 Map，缺失为 null。

## 按消息列出的原字段

下表中的每个字段都有 String getter，除 receive_nn 对应 getReceiveNn() 外，方法名为 get + 原字段名首字母大写，例如 uid → getUid()、userid → getUserid()、crealPrice → getCrealPrice()。

| 消息类型 | 已提供 getter 的原字段（另含公共 type/btype/rid） |
|---|---|
| ChatMessage | `cid`、`gid`、`uid`、`userid`、`nn`、`txt`、`level`、`gt`、`col`、`ct`、`rg`、`pg`、`cmt`、`ic`、`nl`、`nc`、`bnn`、`bl`、`brid`、`hc`、`ol`、`rev`、`hl`、`ifs`、`cst` |
| GiftMessage | `gid`、`uid`、`nn`、`ic`、`level`、`gt`、`ct`、`rg`、`pg`、`nl`、`nc`、`bnn`、`bl`、`brid`、`hc`、`ol`、`cst`、`gfid`、`pid`、`gfn`、`gs`、`bg`、`gfcnt`、`hits`、`receive_nn` |
| FansBadgeMessage | `uid`、`nick`、`icon`、`rnick`、`mn`、`price` |
| NobleMessage | `uid`、`unk`、`uic`、`donk`、`nl` |
| PandoraBroadcastMessage | `uid`、`txt4`、`txt5`、`txt6`、`chatmsg` |
| VoiceDanmuMessage | `uid`、`crealPrice`、`chatmsg` |
| RoomStatusMessage | `ss` |
| GenericMessage | `cid`、`gid`、`uid`、`userid`、`res`、`nn`、`txt`、`level`、`gt`、`col`、`ct`、`rg`、`pg`、`cmt`、`ic`、`nl`、`nc`、`bnn`、`bl`、`brid`、`hc`、`ol`、`rev`、`hl`、`ifs`、`cst`、`gfid`、`gs`、`bg`、`gfcnt`、`hits`、`sl`、`sid`、`did`、`snk`、`dnk`、`rpt`、`sn`、`dn`、`gn`、`gc`、`drid`、`gb`、`es`、`eid`、`sdid`、`trid`、`content`、`code`、`desc`、`pid`、`gfn`、`receive_nn`、`nick`、`icon`、`rnick`、`mn`、`price`、`unk`、`uic`、`donk`、`txt4`、`txt5`、`txt6`、`chatmsg`、`crealPrice`、`ss` |

GenericMessage 的集合覆盖原 BaseMessage 中未独立分类的礼包、礼物广播、超级弹幕和控制报文等字段，并补充现有 Handler 使用的字段。没有新增这些消息的分类或业务处理；字段解释取决于实际 type/btype，缺失为 null。

## 原值与便捷视图

- 字符串 getter 不把 ID 转成数字，不丢弃前导零，不补零，不将空串变成缺失。
- 原有 userId()/nickname()/giftId() 等保留，并提供 getUserId()/getNickname()/getGiftId() 等 JavaBean 别名。
- getGfcnt()/getHits() 返回原字符串；count()/getCount()、hits() 是可空 Long 视图。getHits() 不替代原 hits() 的返回类型。
- getMn()/getPrice()/getCrealPrice() 返回原字符串；months()/getMonths()、rawPrice()/getRawPrice() 保留原有可空数值视图，不折算金额或月份。
- 潘多拉 getTxt5()/quantityText()/getQuantityText() 保留完整数量文案，不从中拼接数字。
- getChatmsg() 是解码外层转义之后的嵌套 STT 字符串；getChatFields() 再解码一层得到不可变 Map，getChatNn()/getChatIc() 对应原 Handler 使用的 chatmsg.nn/chatmsg.ic。缺失、空串或非法嵌套字段时 Map 为空，相应便捷值为 null，不生成额外聊天事件。

## 钻粉动作

原 FansBadgeHandler 明确按如下方式处理：

| 原始 type | action()/getAction() |
|---|---|
| dfobc | OPEN（开通） |
| dfrbc | RENEW（续费） |
| 其他值、空串、null | UNKNOWN |

判断使用两个明确的相等比较，不忽略大小写、不 trim、不将“非 dfobc”判为续费。原分类器仍只将 dfobc/dfrbc 分发为 FansBadgeMessage；UNKNOWN 用于防御直接构造或未来扩展的消息对象。

## 维护与测试

字段 getter 的中文 Javadoc 是 IDE 悬浮说明的来源。MessageGetterTest 使用原协议字段集合检查 JavaBean 可读属性、解码原值、字段缺失、嵌套字段和房间元数据区分；ClassificationTest 覆盖已知与未知钻粉动作。新增字段应同步原字段清单和注释，避免把业务派生字段当成协议字段。
