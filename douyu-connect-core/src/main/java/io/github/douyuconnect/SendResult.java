package io.github.douyuconnect;
public record SendResult(Status status, String serverCode) {
    public enum Status { ACKNOWLEDGED, REJECTED, NOT_READY, QUEUE_FULL, CANCELLED, UNKNOWN }
}
