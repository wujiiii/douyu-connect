package io.github.douyuconnect.message;
public record GenericMessage(MessageContext context) implements DouyuMessage {
    public Category category() { return Category.GENERIC; }
}
