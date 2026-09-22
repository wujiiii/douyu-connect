package io.github.douyuconnect.message;

/**
 * 高能/语音弹幕广播（btype=voiceDanmu），与 ssd 消息分开。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record VoiceDanmuMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.VOICE_DANMU; }

    /** @return 原字段 {@code uid}：发送者用户 ID；缺失为 null */
    public String getUid() { return context.field("uid"); }

    /** @return 原字段 {@code crealPrice}：高能/语音弹幕协议金额，保留原始单位，不折算；缺失为 null */
    public String getCrealPrice() { return context.field("crealPrice"); }

    /** @return 原字段 {@code chatmsg}：嵌套 chatmsg 的 STT 字符串，仅解码外层转义；使用 getChatFields() 读取内层字段；缺失为 null */
    public String getChatmsg() { return context.field("chatmsg"); }

    /** @return 嵌套 chatmsg 解码一层后的不可变字段 Map；缺失、空串或格式错误为空 Map */
    public java.util.Map<String,String> getChatFields() { return context.nested("chatmsg"); }

    /** @return 原嵌套字段 chatmsg.nn：广播附带的用户昵称；不覆盖外层 uid，缺失为 null */
    public String getChatNn() { return getChatFields().get("nn"); }

    /** @return 原嵌套字段 chatmsg.ic：广播附带的用户头像；缺失为 null */
    public String getChatIc() { return getChatFields().get("ic"); }

    /** @return {@code uid} 的兼容便捷访问：发送者用户 ID；缺失为 null */
    public String userId() { return getUid(); }

    /** @return userId() 的 JavaBean getter，协议字段为 {@code uid} */
    public String getUserId() { return userId(); }

    /** @return 原字段 {@code crealPrice} 的可空整数视图；缺失、空串或非法整数为 null，不改变原值 */
    public Long rawPrice() { return context.number("crealPrice"); }

    /** @return rawPrice() 的 JavaBean getter；不执行业务数量或金额换算 */
    public Long getRawPrice() { return rawPrice(); }

    /** @return getChatFields() 的兼容访问，不生成新的聊天事件 */
    public java.util.Map<String,String> chatFields() { return getChatFields(); }
}
