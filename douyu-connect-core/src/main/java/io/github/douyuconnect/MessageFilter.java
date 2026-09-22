package io.github.douyuconnect;
import io.github.douyuconnect.message.*;
import java.util.*;
import java.util.function.Predicate;
@FunctionalInterface
public interface MessageFilter extends Predicate<DouyuMessage> {
    static MessageFilter all() { return message -> true; }
    static MessageFilter categories(Category... categories) {
        Set<Category> values = Set.copyOf(Arrays.asList(categories));
        return message -> values.contains(message.category());
    }
    static MessageFilter room(String roomId) { return message -> roomId.equals(message.context().roomId()); }
    static MessageFilter protocol(String type, String btype) {
        return message -> (type == null || type.equals(message.context().type())) && (btype == null || btype.equals(message.context().btype()));
    }
    default MessageFilter and(MessageFilter other) { return message -> test(message) && other.test(message); }
}
