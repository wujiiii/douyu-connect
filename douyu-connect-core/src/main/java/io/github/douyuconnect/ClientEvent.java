package io.github.douyuconnect;
public record ClientEvent(String roomId, ChannelKind channel, String connectionId,
    ConnectionState state, Kind kind, String detail) {
    public enum Kind { STATE, CALLBACK_ERROR, QUEUE_OVERFLOW, PROTOCOL_ERROR, SEND_REJECTED }
}
