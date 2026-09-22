package io.github.douyuconnect.message;
import io.github.douyuconnect.protocol.Stt;
import java.time.Instant;
import java.util.*;
/**
 * 消息信封；连接元数据与协议字段独立保存。
 * @param roomId 接收连接所属房间，不覆盖 rawFields 中的 rid
 * @param roomInstanceId 逻辑房间实例 ID，断开完成后重新连接会更换
 * @param connectionId 当前底层连接 ID，重连会更换
 * @param sequence 房间实例内的本地递增序号，不是服务端消息 ID
 * @param receivedAt 本机接收时间，不是服务端发送时间
 * @param type 分类器使用的协议类型，缺失时解析器传入空串
 * @param btype 分类器使用的广播子类型，缺失时解析器传入空串
 * @param rawFields 解码一层 STT 后的所有顶层字段，包括未知字段
 * @param rawText 解码协议字段前的 STT 正文，不含二进制包头和末尾 NUL
 * @param parseIssues 解析诊断，不包含凭据或报文正文
 */
public record MessageContext(String roomId, String roomInstanceId, String connectionId, long sequence,
                             Instant receivedAt, String type, String btype, Map<String,String> rawFields,
                             String rawText, List<String> parseIssues) {
    /**
     * 创建信封并防御性复制原字段和诊断集合，保持对外不可变。
     * @param roomId 接收连接所属房间
     * @param roomInstanceId 逻辑房间实例 ID
     * @param connectionId 底层连接 ID
     * @param sequence 本地接收序号
     * @param receivedAt 本机接收时间
     * @param type 分类器使用的协议类型
     * @param btype 分类器使用的广播子类型
     * @param rawFields 全部顶层原字段
     * @param rawText 原始 STT 正文
     * @param parseIssues 解析诊断
     */
    public MessageContext {
        rawFields = Collections.unmodifiableMap(new LinkedHashMap<>(rawFields));
        parseIssues = List.copyOf(parseIssues);
    }
    /** @return 连接所属房间；报文 rid 请通过消息 getRid() 获取 */
    public String getRoomId() { return roomId; }
    /** @return 逻辑房间实例 ID */
    public String getRoomInstanceId() { return roomInstanceId; }
    /** @return 底层连接 ID */
    public String getConnectionId() { return connectionId; }
    /** @return 房间实例内的本地接收序号 */
    public long getSequence() { return sequence; }
    /** @return 本机接收时间 */
    public Instant getReceivedAt() { return receivedAt; }
    /** @return 分类器使用的 type，与 type() 相同；不同于消息 getType() 的缺失字段 null 语义 */
    public String getType() { return type; }
    /** @return 分类器使用的 btype，与 btype() 相同 */
    public String getBtype() { return btype; }
    /** @return 包含未知字段的不可变顶层字段 Map */
    public Map<String,String> getRawFields() { return rawFields; }
    /** @return 原始 STT 正文 */
    public String getRawText() { return rawText; }
    /** @return 不可变的解析诊断列表 */
    public List<String> getParseIssues() { return parseIssues; }

    /**
     * @param name 协议字段名
     * @return 原值；缺失为 null，空串仍为空串
     */
    public String field(String name) { return rawFields.get(name); }
    /**
     * @param name 协议字段名
     * @return 整数值；缺失、空串或非法整数为 null，不补零
     */
    public Long number(String name) {
        String value = field(name);
        if (value == null || value.isEmpty()) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException e) { return null; }
    }
    /**
     * @param name 嵌套 STT 字段名
     * @return 再解码一层的不可变 Map；缺失、空串或格式错误为空 Map
     */
    public Map<String,String> nested(String name) {
        String value = field(name);
        if (value == null || value.isEmpty()) return Map.of();
        try { return Stt.decode(value); } catch (IllegalArgumentException e) { return Map.of(); }
    }
}
