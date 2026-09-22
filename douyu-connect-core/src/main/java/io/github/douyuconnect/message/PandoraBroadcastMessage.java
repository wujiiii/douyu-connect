package io.github.douyuconnect.message;

/**
 * 潘多拉广播（btype=pandora）；不关联开箱记录，不与 dgb 合并。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record PandoraBroadcastMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.PANDORA_BROADCAST; }

    /** @return 原字段 {@code uid}：发送者用户 ID；缺失为 null */
    public String getUid() { return context.field("uid"); }

    /** @return 原字段 {@code txt4}：潘多拉广播中的奖励礼物名称；缺失为 null */
    public String getTxt4() { return context.field("txt4"); }

    /** @return 原字段 {@code txt5}：潘多拉广播中的数量文案，不提取或拼接数字；缺失为 null */
    public String getTxt5() { return context.field("txt5"); }

    /** @return 原字段 {@code txt6}：潘多拉广播中的奖励道具 ID，不添加 pid_ 前缀；缺失为 null */
    public String getTxt6() { return context.field("txt6"); }

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

    /** @return {@code txt4} 的兼容便捷访问：潘多拉广播中的奖励礼物名称；缺失为 null */
    public String giftName() { return getTxt4(); }

    /** @return giftName() 的 JavaBean getter，协议字段为 {@code txt4} */
    public String getGiftName() { return giftName(); }

    /** @return {@code txt5} 的兼容便捷访问：潘多拉广播中的数量文案，不提取或拼接数字；缺失为 null */
    public String quantityText() { return getTxt5(); }

    /** @return quantityText() 的 JavaBean getter，协议字段为 {@code txt5} */
    public String getQuantityText() { return quantityText(); }

    /** @return {@code txt6} 的兼容便捷访问：潘多拉广播中的奖励道具 ID，不添加 pid_ 前缀；缺失为 null */
    public String propId() { return getTxt6(); }

    /** @return propId() 的 JavaBean getter，协议字段为 {@code txt6} */
    public String getPropId() { return propId(); }

    /** @return getChatFields() 的兼容访问，不生成新的聊天事件 */
    public java.util.Map<String,String> chatFields() { return getChatFields(); }
}
