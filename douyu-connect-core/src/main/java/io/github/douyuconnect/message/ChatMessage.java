package io.github.douyuconnect.message;
public record ChatMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.CHAT; }
    public String userId() { return context.field("uid"); }
    public String nickname() { return context.field("nn"); }
    public String avatar() { return context.field("ic"); }
    public String text() { return context.field("txt"); }
    public String messageId() { return context.field("cid"); }
}
