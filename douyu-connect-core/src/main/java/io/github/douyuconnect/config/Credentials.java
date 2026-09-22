package io.github.douyuconnect.config;
import java.util.Objects;
/** Caller supplied authentication snapshot. Never logs tokens. */
public record Credentials(String deviceId, long userId, String username, long loginTicketId, String sessionToken, int biz) {
    public Credentials {
        requireText(deviceId); requireText(username); requireText(sessionToken);
        if (userId <= 0 || loginTicketId <= 0 || biz < 0) throw new IllegalArgumentException("Invalid credential identifiers");
    }
    private static void requireText(String value) {
        if (Objects.requireNonNull(value, "Credential field").isBlank() || value.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Empty or invalid credential field");
    }
    @Override public String toString() { return "Credentials[REDACTED]"; }
}
