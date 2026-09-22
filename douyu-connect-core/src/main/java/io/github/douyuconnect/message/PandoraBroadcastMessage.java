package io.github.douyuconnect.message;
public record PandoraBroadcastMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.PANDORA_BROADCAST; }
    public String userId() { return context.field("uid"); }
    public String giftName() { return context.field("txt4"); }
    public String quantityText() { return context.field("txt5"); }
    public String propId() { return context.field("txt6"); }
    public java.util.Map<String,String> chatFields() { return context.nested("chatmsg"); }
}
