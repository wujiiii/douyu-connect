package io.github.douyuconnect.message;
public record RoomStatusMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.ROOM_STATUS; }
    public String status() { return context.field("ss"); }
}
