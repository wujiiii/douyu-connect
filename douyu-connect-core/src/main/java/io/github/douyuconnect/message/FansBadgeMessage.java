package io.github.douyuconnect.message;

/**
 * 钻粉消息：dfobc 明确表示开通，dfrbc 明确表示续费。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record FansBadgeMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.FANS_BADGE; }

    /** @return 原字段 {@code uid}：发送者用户 ID；缺失为 null */
    public String getUid() { return context.field("uid"); }

    /** @return 原字段 {@code nick}：钻粉用户昵称；缺失为 null */
    public String getNick() { return context.field("nick"); }

    /** @return 原字段 {@code icon}：钻粉用户头像；缺失为 null */
    public String getIcon() { return context.field("icon"); }

    /** @return 原字段 {@code rnick}：钻粉接收者昵称；缺失为 null */
    public String getRnick() { return context.field("rnick"); }

    /** @return 原字段 {@code mn}：钻粉开通或续费月数；缺失为 null */
    public String getMn() { return context.field("mn"); }

    /** @return 原字段 {@code price}：钻粉协议金额，保留原始单位，不折算；缺失为 null */
    public String getPrice() { return context.field("price"); }

    /** 钻粉动作，只有精确匹配的协议类型才定义为开通或续费。 */
    public enum Action {
        /** 原始 type=dfobc，开通钻粉。 */
        OPEN,
        /** 原始 type=dfrbc，续费钻粉。 */
        RENEW,
        /** 未知、缺失或不匹配的类型，不推断为开通或续费。 */
        UNKNOWN
    }

    /** @return dfobc 为 OPEN，dfrbc 为 RENEW，其他类型（含 null）为 UNKNOWN */
    public Action action() {
        if ("dfobc".equals(context.type())) return Action.OPEN;
        if ("dfrbc".equals(context.type())) return Action.RENEW;
        return Action.UNKNOWN;
    }

    /** @return 钻粉动作，与 action() 相同 */
    public Action getAction() { return action(); }

    /** @return {@code uid} 的兼容便捷访问：发送者用户 ID；缺失为 null */
    public String userId() { return getUid(); }

    /** @return userId() 的 JavaBean getter，协议字段为 {@code uid} */
    public String getUserId() { return userId(); }

    /** @return {@code nick} 的兼容便捷访问：钻粉用户昵称；缺失为 null */
    public String nickname() { return getNick(); }

    /** @return nickname() 的 JavaBean getter，协议字段为 {@code nick} */
    public String getNickname() { return nickname(); }

    /** @return {@code icon} 的兼容便捷访问：钻粉用户头像；缺失为 null */
    public String avatar() { return getIcon(); }

    /** @return avatar() 的 JavaBean getter，协议字段为 {@code icon} */
    public String getAvatar() { return avatar(); }

    /** @return {@code rnick} 的兼容便捷访问：钻粉接收者昵称；缺失为 null */
    public String recipientName() { return getRnick(); }

    /** @return recipientName() 的 JavaBean getter，协议字段为 {@code rnick} */
    public String getRecipientName() { return recipientName(); }

    /** @return 原字段 {@code mn} 的可空整数视图；缺失、空串或非法整数为 null，不改变原值 */
    public Long months() { return context.number("mn"); }

    /** @return months() 的 JavaBean getter；不执行业务数量或金额换算 */
    public Long getMonths() { return months(); }

    /** @return 原字段 {@code price} 的可空整数视图；缺失、空串或非法整数为 null，不改变原值 */
    public Long rawPrice() { return context.number("price"); }

    /** @return rawPrice() 的 JavaBean getter；不执行业务数量或金额换算 */
    public Long getRawPrice() { return rawPrice(); }
}
