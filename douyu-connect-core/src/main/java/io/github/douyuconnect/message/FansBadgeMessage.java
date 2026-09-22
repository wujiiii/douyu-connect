package io.github.douyuconnect.message;
public record FansBadgeMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.FANS_BADGE; }
    public String userId() { return context.field("uid"); }
    public String nickname() { return context.field("nick"); }
    public String avatar() { return context.field("icon"); }
    public String recipientName() { return context.field("rnick"); }
    public Long months() { return context.number("mn"); }
    public Long rawPrice() { return context.number("price"); }
    public enum Action { OPEN, RENEW }
    public Action action() { return "dfobc".equals(context.type()) ? Action.OPEN : Action.RENEW; }
}
