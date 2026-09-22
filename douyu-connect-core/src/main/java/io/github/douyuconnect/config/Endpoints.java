package io.github.douyuconnect.config;
import java.net.URI;
import java.util.*;
public final class Endpoints {
    private Endpoints() {}
    public static List<URI> receive() { return ports("danmuproxy.douyu.com", 8501, 8504); }
    public static List<URI> send() { return ports("wsproxy.douyu.com", 6671, 6675); }
    private static List<URI> ports(String host, int first, int last) {
        List<URI> uris = new ArrayList<>();
        for (int port = first; port <= last; port++) uris.add(URI.create("wss://" + host + ":" + port + "/"));
        return List.copyOf(uris);
    }
    public static List<URI> validate(List<URI> uris) {
        List<URI> copy = List.copyOf(uris);
        if (copy.isEmpty()) throw new IllegalArgumentException("At least one endpoint required");
        for (URI uri : copy) {
            if (!Set.of("ws","wss").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException("Invalid WebSocket endpoint");
        }
        return copy;
    }
}
