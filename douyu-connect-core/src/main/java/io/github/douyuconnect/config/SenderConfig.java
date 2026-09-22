package io.github.douyuconnect.config;
import java.net.URI;
import java.util.List;
public record SenderConfig(Credentials credentials, List<URI> endpoints) {
    public SenderConfig { endpoints = Endpoints.validate(endpoints); }
    public static SenderConfig disabled() { return new SenderConfig(null, Endpoints.send()); }
    public static SenderConfig enabled(Credentials credentials) {
        return new SenderConfig(java.util.Objects.requireNonNull(credentials), Endpoints.send());
    }
    public boolean enabled() { return credentials != null; }
}
