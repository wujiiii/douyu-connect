package io.github.douyuconnect;
public record RoomStatus(String roomId, String roomInstanceId, long configVersion, ConnectionState receive, ConnectionState send) {}
