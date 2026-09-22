package io.github.douyuconnect.message;

/**
 * 爵位消息（type=anbc）；保留等级原值，不推断金额或构造业务礼物编号。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record NobleMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.NOBLE; }

    /** @return 原字段 {@code uid}：发送者用户 ID；缺失为 null */
    public String getUid() { return context.field("uid"); }

    /** @return 原字段 {@code unk}：爵位消息发送者昵称；缺失为 null */
    public String getUnk() { return context.field("unk"); }

    /** @return 原字段 {@code uic}：爵位消息发送者头像；缺失为 null */
    public String getUic() { return context.field("uic"); }

    /** @return 原字段 {@code donk}：爵位接收者昵称；缺失为 null */
    public String getDonk() { return context.field("donk"); }

    /** @return 原字段 {@code nl}：贵族等级标识；缺失为 null */
    public String getNl() { return context.field("nl"); }

    /** @return {@code uid} 的兼容便捷访问：发送者用户 ID；缺失为 null */
    public String userId() { return getUid(); }

    /** @return userId() 的 JavaBean getter，协议字段为 {@code uid} */
    public String getUserId() { return userId(); }

    /** @return {@code unk} 的兼容便捷访问：爵位消息发送者昵称；缺失为 null */
    public String nickname() { return getUnk(); }

    /** @return nickname() 的 JavaBean getter，协议字段为 {@code unk} */
    public String getNickname() { return nickname(); }

    /** @return {@code uic} 的兼容便捷访问：爵位消息发送者头像；缺失为 null */
    public String avatar() { return getUic(); }

    /** @return avatar() 的 JavaBean getter，协议字段为 {@code uic} */
    public String getAvatar() { return avatar(); }

    /** @return {@code donk} 的兼容便捷访问：爵位接收者昵称；缺失为 null */
    public String recipientName() { return getDonk(); }

    /** @return recipientName() 的 JavaBean getter，协议字段为 {@code donk} */
    public String getRecipientName() { return recipientName(); }

    /** @return {@code nl} 的兼容便捷访问：贵族等级标识；缺失为 null */
    public String level() { return getNl(); }

    /** @return level() 的 JavaBean getter，协议字段为 {@code nl} */
    public String getLevel() { return level(); }
}
