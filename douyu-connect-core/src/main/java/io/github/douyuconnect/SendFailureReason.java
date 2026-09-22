package io.github.douyuconnect;

/** Stable SDK reasons; server mappings apply only to chatres.res. */
public enum SendFailureReason {
    NONE("服务器确认发送成功"),
    ROOM_MUTED("用户已被禁言"),
    GLOBAL_MUTED("用户被全站禁言"),
    FANS_ONLY("房间仅允许粉丝发言"),
    INVALID_OPERATION("非法操作"),
    TARGET_USER_NOT_FOUND("未找到目标用户"),
    DUPLICATE_MESSAGE("重复发言"),
    RATE_LIMITED("发言过于频繁"),
    ACCOUNT_VERIFICATION_REQUIRED("需要账号安全验证"),
    UNKNOWN_SERVER_CODE("未知服务端拒绝原因"),
    SENDER_NOT_READY("发送通道尚未就绪，本次未发送"),
    SEND_QUEUE_FULL("发送队列已满，本次未入队"),
    REQUEST_CANCELLED("尚未发出的请求已取消"),
    DELIVERY_UNKNOWN("已尝试发送但未收到可靠回执，是否送达未知，请勿自动重发");

    private final String message;

    SendFailureReason(String message) { this.message = message; }

    public String message() { return message; }

    /** Verified against Douyu's web client; see docs/send-response-codes.md. */
    static SendFailureReason fromServerCode(String code) {
        if (code == null) return UNKNOWN_SERVER_CODE;
        return switch (code) {
            case "2" -> ROOM_MUTED;
            case "5" -> GLOBAL_MUTED;
            case "6" -> FANS_ONLY;
            case "206" -> INVALID_OPERATION;
            case "208" -> TARGET_USER_NOT_FOUND;
            case "289" -> DUPLICATE_MESSAGE;
            case "290" -> RATE_LIMITED;
            case "391" -> ACCOUNT_VERIFICATION_REQUIRED;
            default -> UNKNOWN_SERVER_CODE;
        };
    }
}
