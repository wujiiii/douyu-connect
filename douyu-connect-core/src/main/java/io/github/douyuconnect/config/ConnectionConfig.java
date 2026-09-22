package io.github.douyuconnect.config;
import java.net.URI;
import java.util.*;
public record ConnectionConfig(List<URI> receiveEndpoints, SenderConfig sender) {
    public ConnectionConfig { receiveEndpoints = Endpoints.validate(receiveEndpoints); Objects.requireNonNull(sender); }
    public static ConnectionConfig receiveOnly() { return new ConnectionConfig(Endpoints.receive(), SenderConfig.disabled()); }
    public static ConnectionConfig withSender(Credentials credentials) { return new ConnectionConfig(Endpoints.receive(), SenderConfig.enabled(credentials)); }
}
