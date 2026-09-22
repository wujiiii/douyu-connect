package io.github.douyuconnect.message;
public record NobleMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.NOBLE; }
    public String userId() { return context.field("uid"); }
    public String nickname() { return context.field("unk"); }
    public String avatar() { return context.field("uic"); }
    public String recipientName() { return context.field("donk"); }
    public String level() { return context.field("nl"); }
}
