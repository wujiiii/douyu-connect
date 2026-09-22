package io.github.douyuconnect.message;
import io.github.douyuconnect.protocol.Stt;
import java.time.Instant;
import java.util.*;
public record MessageContext(String roomId, String roomInstanceId, String connectionId, long sequence,
                             Instant receivedAt, String type, String btype, Map<String,String> rawFields,
                             String rawText, List<String> parseIssues) {
    public MessageContext {
        rawFields = Collections.unmodifiableMap(new LinkedHashMap<>(rawFields));
        parseIssues = List.copyOf(parseIssues);
    }
    public String field(String name) { return rawFields.get(name); }
    public Long number(String name) {
        String value = field(name);
        if (value == null || value.isEmpty()) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException e) { return null; }
    }
    public Map<String,String> nested(String name) {
        String value = field(name);
        if (value == null || value.isEmpty()) return Map.of();
        try { return Stt.decode(value); } catch (IllegalArgumentException e) { return Map.of(); }
    }
}
