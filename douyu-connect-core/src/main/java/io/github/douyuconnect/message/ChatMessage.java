package io.github.douyuconnect.message;

/**
 * 聊天消息（type=chatmsg）。
 * <p>原字段 getXxx() 返回解码后的字符串；缺失为 null，空串保持为空串，不补默认值。
 * 公共 type/btype/rid 及连接元数据 getter 见 {@link DouyuMessage}。
 * @param context 消息信封，保存原字段、原文和连接元数据
 */
public record ChatMessage(MessageContext context) implements DouyuMessage {
    /** @return 此消息的 SDK 分类 */
    @Override public Category category() { return Category.CHAT; }

    /** @return 原字段 {@code cid}：弹幕消息 ID；缺失为 null */
    public String getCid() { return context.field("cid"); }

    /** @return 原字段 {@code gid}：弹幕分组 ID；缺失为 null */
    public String getGid() { return context.field("gid"); }

    /** @return 原字段 {@code uid}：发送者用户 ID；缺失为 null */
    public String getUid() { return context.field("uid"); }

    /** @return 原字段 {@code userid}：推送/登录协议中的 userid，与 uid 分别保留；缺失为 null */
    public String getUserid() { return context.field("userid"); }

    /** @return 原字段 {@code nn}：发送者昵称；缺失为 null */
    public String getNn() { return context.field("nn"); }

    /** @return 原字段 {@code txt}：消息正文；缺失为 null */
    public String getTxt() { return context.field("txt"); }

    /** @return 原字段 {@code level}：用户等级；缺失为 null */
    public String getLevel() { return context.field("level"); }

    /** @return 原字段 {@code gt}：礼物头衔标识；缺失为 null */
    public String getGt() { return context.field("gt"); }

    /** @return 原字段 {@code col}：弹幕颜色标识；缺失为 null */
    public String getCol() { return context.field("col"); }

    /** @return 原字段 {@code ct}：客户端类型；缺失为 null */
    public String getCt() { return context.field("ct"); }

    /** @return 原字段 {@code rg}：房间权限组；缺失为 null */
    public String getRg() { return context.field("rg"); }

    /** @return 原字段 {@code pg}：平台权限组；缺失为 null */
    public String getPg() { return context.field("pg"); }

    /** @return 原字段 {@code cmt}：弹幕具体类型；缺失为 null */
    public String getCmt() { return context.field("cmt"); }

    /** @return 原字段 {@code ic}：用户头像标识；缺失为 null */
    public String getIc() { return context.field("ic"); }

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

    /** @return 原字段 {@code rev}：反向弹幕标识；缺失为 null */
    public String getRev() { return context.field("rev"); }

    /** @return 原字段 {@code hl}：高亮弹幕标识；缺失为 null */
    public String getHl() { return context.field("hl"); }

    /** @return 原字段 {@code ifs}：粉丝弹幕标识；缺失为 null */
    public String getIfs() { return context.field("ifs"); }

    /** @return 原字段 {@code cst}：协议中的弹幕发送时间，不转换时间单位；缺失为 null */
    public String getCst() { return context.field("cst"); }

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

    /** @return {@code txt} 的兼容便捷访问：消息正文；缺失为 null */
    public String text() { return getTxt(); }

    /** @return text() 的 JavaBean getter，协议字段为 {@code txt} */
    public String getText() { return text(); }

    /** @return {@code cid} 的兼容便捷访问：弹幕消息 ID；缺失为 null */
    public String messageId() { return getCid(); }

    /** @return messageId() 的 JavaBean getter，协议字段为 {@code cid} */
    public String getMessageId() { return messageId(); }
}
