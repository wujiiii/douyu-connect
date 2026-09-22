package io.github.douyuconnect.message;

/**
 * 礼物原始消息（type=dgb）；不判断普通/潘多拉/梦幻岛来源，也不计算数量或价格。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record GiftMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.GIFT; }

    /** @return 原字段 {@code gid}：弹幕分组 ID；缺失为 null */
    public String getGid() { return context.field("gid"); }

    /** @return 原字段 {@code uid}：发送者用户 ID；缺失为 null */
    public String getUid() { return context.field("uid"); }

    /** @return 原字段 {@code nn}：发送者昵称；缺失为 null */
    public String getNn() { return context.field("nn"); }

    /** @return 原字段 {@code ic}：用户头像标识；缺失为 null */
    public String getIc() { return context.field("ic"); }

    /** @return 原字段 {@code level}：用户等级；缺失为 null */
    public String getLevel() { return context.field("level"); }

    /** @return 原字段 {@code gt}：礼物头衔标识；缺失为 null */
    public String getGt() { return context.field("gt"); }

    /** @return 原字段 {@code ct}：客户端类型；缺失为 null */
    public String getCt() { return context.field("ct"); }

    /** @return 原字段 {@code rg}：房间权限组；缺失为 null */
    public String getRg() { return context.field("rg"); }

    /** @return 原字段 {@code pg}：平台权限组；缺失为 null */
    public String getPg() { return context.field("pg"); }

    /** @return 原字段 {@code nl}：贵族等级标识；缺失为 null */
    public String getNl() { return context.field("nl"); }

    /** @return 原字段 {@code nc}：贵族弹幕标识；缺失为 null */
    public String getNc() { return context.field("nc"); }

    /** @return 原字段 {@code bnn}：粉丝牌名称；缺失为 null */
    public String getBnn() { return context.field("bnn"); }

    /** @return 原字段 {@code bl}：粉丝牌等级；缺失为 null */
    public String getBl() { return context.field("bl"); }

    /** @return 原字段 {@code brid}：粉丝牌关联房间号，不是接收连接房间号；缺失为 null */
    public String getBrid() { return context.field("brid"); }

    /** @return 原字段 {@code hc}：徽章校验信息；缺失为 null */
    public String getHc() { return context.field("hc"); }

    /** @return 原字段 {@code ol}：主播等级；缺失为 null */
    public String getOl() { return context.field("ol"); }

    /** @return 原字段 {@code cst}：协议中的弹幕发送时间，不转换时间单位；缺失为 null */
    public String getCst() { return context.field("cst"); }

    /** @return 原字段 {@code gfid}：礼物 ID，保留 0 等原值，不改写为 pid_xxx；缺失为 null */
    public String getGfid() { return context.field("gfid"); }

    /** @return 原字段 {@code pid}：道具 ID，与 gfid 分别保留；缺失为 null */
    public String getPid() { return context.field("pid"); }

    /** @return 原字段 {@code gfn}：礼物名称；缺失为 null */
    public String getGfn() { return context.field("gfn"); }

    /** @return 原字段 {@code gs}：礼物显示样式；缺失为 null */
    public String getGs() { return context.field("gs"); }

    /** @return 原字段 {@code bg}：大礼物标识；缺失为 null */
    public String getBg() { return context.field("bg"); }

    /** @return 原字段 {@code gfcnt}：协议礼物数量，不与 hits 相乘；缺失为 null */
    public String getGfcnt() { return context.field("gfcnt"); }

    /** @return 原字段 {@code hits}：协议礼物连击计数，不计算相邻消息的增量；缺失为 null */
    public String getHits() { return context.field("hits"); }

    /** @return 原字段 {@code receive_nn}：接收者昵称；缺失为 null */
    public String getReceiveNn() { return context.field("receive_nn"); }

    /** @return {@code uid} 的兼容便捷访问：发送者用户 ID；缺失为 null */
    public String userId() { return getUid(); }

    /** @return userId() 的 JavaBean getter，协议字段为 {@code uid} */
    public String getUserId() { return userId(); }

    /** @return {@code nn} 的兼容便捷访问：发送者昵称；缺失为 null */
    public String nickname() { return getNn(); }

    /** @return nickname() 的 JavaBean getter，协议字段为 {@code nn} */
    public String getNickname() { return nickname(); }

    /** @return {@code ic} 的兼容便捷访问：用户头像标识；缺失为 null */
    public String avatar() { return getIc(); }

    /** @return avatar() 的 JavaBean getter，协议字段为 {@code ic} */
    public String getAvatar() { return avatar(); }

    /** @return {@code gfid} 的兼容便捷访问：礼物 ID，保留 0 等原值，不改写为 pid_xxx；缺失为 null */
    public String giftId() { return getGfid(); }

    /** @return giftId() 的 JavaBean getter，协议字段为 {@code gfid} */
    public String getGiftId() { return giftId(); }

    /** @return {@code pid} 的兼容便捷访问：道具 ID，与 gfid 分别保留；缺失为 null */
    public String propId() { return getPid(); }

    /** @return propId() 的 JavaBean getter，协议字段为 {@code pid} */
    public String getPropId() { return propId(); }

    /** @return {@code gfn} 的兼容便捷访问：礼物名称；缺失为 null */
    public String giftName() { return getGfn(); }

    /** @return giftName() 的 JavaBean getter，协议字段为 {@code gfn} */
    public String getGiftName() { return giftName(); }

    /** @return {@code receive_nn} 的兼容便捷访问：接收者昵称；缺失为 null */
    public String recipientName() { return getReceiveNn(); }

    /** @return recipientName() 的 JavaBean getter，协议字段为 {@code receive_nn} */
    public String getRecipientName() { return recipientName(); }

    /** @return 原字段 {@code gfcnt} 的可空整数视图；缺失、空串或非法整数为 null，不改变原值 */
    public Long count() { return context.number("gfcnt"); }

    /** @return count() 的 JavaBean getter；不执行业务数量或金额换算 */
    public Long getCount() { return count(); }

    /** @return 原字段 {@code hits} 的可空整数视图；缺失、空串或非法整数为 null，不改变原值 */
    public Long hits() { return context.number("hits"); }
}
