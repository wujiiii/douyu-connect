package io.github.douyuconnect.message;
public record VoiceDanmuMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.VOICE_DANMU; }
    public String userId() { return context.field("uid"); }
    public Long rawPrice() { return context.number("crealPrice"); }
    public java.util.Map<String,String> chatFields() { return context.nested("chatmsg"); }
}
