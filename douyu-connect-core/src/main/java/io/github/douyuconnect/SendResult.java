package io.github.douyuconnect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable result of one send attempt. Raw fields are exposed but excluded from toString. */
public record SendResult(Status status, String serverCode, Map<String,String> rawResponse) {
    public enum Status { ACKNOWLEDGED, REJECTED, NOT_READY, QUEUE_FULL, CANCELLED, UNKNOWN }

    public SendResult {
        Objects.requireNonNull(status, "status");
        rawResponse = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(rawResponse, "rawResponse")));
    }

    /** Source-compatible constructor for local results and existing callers. */
    public SendResult(Status status, String serverCode) { this(status,serverCode,Map.of()); }

    public SendFailureReason reason() {
        return switch (status) {
            case ACKNOWLEDGED -> SendFailureReason.NONE;
            case REJECTED -> SendFailureReason.fromServerCode(serverCode);
            case NOT_READY -> SendFailureReason.SENDER_NOT_READY;
            case QUEUE_FULL -> SendFailureReason.SEND_QUEUE_FULL;
            case CANCELLED -> SendFailureReason.REQUEST_CANCELLED;
            case UNKNOWN -> SendFailureReason.DELIVERY_UNKNOWN;
        };
    }

    public String message() { return reason().message(); }

    public static SendResult fromChatResponse(Map<String,String> response) {
        Objects.requireNonNull(response,"response");
        if (!"chatres".equals(response.get("type")) || response.get("res") == null)
            throw new IllegalArgumentException("Expected a chatres response containing res");
        String code = response.get("res");
        return new SendResult("0".equals(code) ? Status.ACKNOWLEDGED : Status.REJECTED,code,response);
    }

    @Override public String toString() {
        return "SendResult[status=" + status + ", serverCode=" + serverCode + ", reason=" + reason() + ", message=" + message() + "]";
    }
}
