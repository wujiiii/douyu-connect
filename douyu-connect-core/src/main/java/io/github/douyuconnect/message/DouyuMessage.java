package io.github.douyuconnect.message;

import java.util.Map;

/** 分类消息的公共读取接口；原字段 getter 不补默认值，不覆盖服务器报文。 */
public interface DouyuMessage {
    /** @return 消息信封，包含连接元数据、原始字段、原文与解析诊断 */
    MessageContext context();

    /** @return SDK 消息分类，不等于原始协议 type/btype */
    Category category();

    /** @return 消息信封，与 {@link #context()} 相同 */
    default MessageContext getContext() { return context(); }

    /** @return SDK 消息分类，与 {@link #category()} 相同 */
    default Category getCategory() { return category(); }

    /** @return 原始 type（如 dgb、dfobc、dfrbc）；字段缺失时为 null */
    default String getType() { return context().field("type"); }

    /** @return 原始广播子类型 btype（如 pandora、voiceDanmu）；字段缺失时为 null */
    default String getBtype() { return context().field("btype"); }

    /** @return 报文原始 rid；字段缺失时为 null，不使用连接房间号补全；广播中的含义由协议决定 */
    default String getRid() { return context().field("rid"); }

    /** @return 接收该消息的连接所属房间，与报文原始 rid 分开保存 */
    default String getConnectionRoomId() { return context().roomId(); }

    /** @return 所有解码后的顶层协议字段，包括未知字段；Map 不可修改 */
    default Map<String,String> getRawFields() { return context().rawFields(); }

    /** @return 去掉二进制包头和末尾 NUL 后的原始 STT 正文；未执行字段转义解码 */
    default String getRawText() { return context().rawText(); }
}
