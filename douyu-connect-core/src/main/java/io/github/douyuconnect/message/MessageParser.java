package io.github.douyuconnect.message;
import java.time.Instant;
import java.util.*;
import io.github.douyuconnect.protocol.Stt;
public final class MessageParser {
    private MessageParser() {}
    public static DouyuMessage parse(String room, String instance, String connection, long sequence, Instant receivedAt, String raw) {
        Map<String,String> fields;
        List<String> issues = new ArrayList<>();
        try { fields = Stt.decode(raw); }
        catch (IllegalArgumentException e) { fields = Map.of(); issues.add("Malformed STT payload"); }
        for (String key : List.of("hits", "gfcnt", "mn", "price", "crealPrice")) {
            String value = fields.get(key);
            if (value != null && !value.isEmpty()) {
                try { Long.parseLong(value); } catch (NumberFormatException e) { issues.add("Invalid numeric field: " + key); }
            }
        }
        if (fields.containsKey("chatmsg")) {
            try { Stt.decode(fields.get("chatmsg")); } catch (IllegalArgumentException e) { issues.add("Malformed nested chatmsg"); }
        }
        String type = fields.getOrDefault("type", "");
        String btype = fields.getOrDefault("btype", "");
        MessageContext context = new MessageContext(room, instance, connection, sequence, receivedAt, type, btype, fields, raw, issues);
        // Direct message kinds are authoritative; nested chatmsg never becomes an extra event.
        return switch (type) {
            case "chatmsg" -> new ChatMessage(context);
            case "dgb" -> new GiftMessage(context);
            case "dfobc", "dfrbc" -> new FansBadgeMessage(context);
            case "anbc" -> new NobleMessage(context);
            case "rss" -> new RoomStatusMessage(context);
            default -> switch (btype) {
                case "pandora" -> new PandoraBroadcastMessage(context);
                case "voiceDanmu" -> new VoiceDanmuMessage(context);
                default -> new GenericMessage(context);
            };
        };
    }
}
