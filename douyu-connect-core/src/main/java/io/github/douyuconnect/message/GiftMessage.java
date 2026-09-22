package io.github.douyuconnect.message;
public record GiftMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.GIFT; }
    public String userId() { return context.field("uid"); }
    public String nickname() { return context.field("nn"); }
    public String avatar() { return context.field("ic"); }
    public String giftId() { return context.field("gfid"); }
    public String propId() { return context.field("pid"); }
    public String giftName() { return context.field("gfn"); }
    public String recipientName() { return context.field("receive_nn"); }
    public Long count() { return context.number("gfcnt"); }
    public Long hits() { return context.number("hits"); }
}
