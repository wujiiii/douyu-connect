package io.github.douyuconnect.message;

/**
 * 直播状态消息（type=rss），不修改数据库或补全开播/下播时间。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record RoomStatusMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.ROOM_STATUS; }

    /** @return 原字段 {@code ss}：直播状态原值；原项目将 1 视为直播中，不推断其他值；缺失为 null */
    public String getSs() { return context.field("ss"); }

    /** @return {@code ss} 的兼容便捷访问：直播状态原值；原项目将 1 视为直播中，不推断其他值；缺失为 null */
    public String status() { return getSs(); }

    /** @return status() 的 JavaBean getter，协议字段为 {@code ss} */
    public String getStatus() { return status(); }
}
