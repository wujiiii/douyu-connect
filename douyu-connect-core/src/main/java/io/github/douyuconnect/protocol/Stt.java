package io.github.douyuconnect.protocol;
import java.util.*;
public final class Stt {
    private Stt() {}
    /** Decodes exactly one STT layer. Nested STT values must be decoded explicitly. */
    public static Map<String, String> decode(String text) {
        Objects.requireNonNull(text, "text");
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : text.split("/", -1)) {
            if (pair.isEmpty()) continue;
            int separator = pair.indexOf("@=");
            if (separator < 0) throw new IllegalArgumentException("Malformed STT field");
            result.put(unescape(pair.substring(0, separator)), unescape(pair.substring(separator + 2)));
        }
        return Collections.unmodifiableMap(result);
    }
    public static String encode(Map<String, String> fields) {
        StringBuilder result = new StringBuilder();
        fields.forEach((key, value) -> result.append(escape(key)).append("@=").append(escape(value)).append('/'));
        return result.toString();
    }
    public static String escape(String value) {
        Objects.requireNonNull(value, "STT value");
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL is not allowed in STT");
        return value.replace("@", "@A").replace("/", "@S");
    }
    private static String unescape(String value) {
        return value.replace("@S", "/").replace("@A", "@");
    }
}
