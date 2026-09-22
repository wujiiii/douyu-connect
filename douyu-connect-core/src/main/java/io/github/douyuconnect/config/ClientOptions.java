package io.github.douyuconnect.config;
import java.time.Duration;
import java.util.Objects;
public record ClientOptions(Duration connectionTimeout, Duration heartbeatInterval, Duration idleTimeout,
    Duration retryBase, Duration retryMax, Duration sendInterval, Duration receiptTimeout,
    Duration shutdownTimeout, int messageQueueCapacity, int sendQueueCapacity, int callbackThreads, int maxPacketLength) {
    public ClientOptions {
        for (Duration duration : new Duration[]{connectionTimeout, heartbeatInterval, idleTimeout, retryBase, retryMax, receiptTimeout, shutdownTimeout}) {
            if (Objects.requireNonNull(duration).toMillis() < 1) throw new IllegalArgumentException("Timeout must be positive");
        }
        if (Objects.requireNonNull(sendInterval).isNegative() || retryMax.compareTo(retryBase) < 0
            || messageQueueCapacity < 1 || sendQueueCapacity < 1 || callbackThreads < 1
            || maxPacketLength < 9 || maxPacketLength > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Invalid client options");
    }
    public static ClientOptions defaults() {
        return new ClientOptions(Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(90),
            Duration.ofSeconds(1), Duration.ofSeconds(60), Duration.ofMillis(1500), Duration.ofSeconds(10),
            Duration.ofSeconds(5), 4096, 100, 4, 128 * 1024);
    }
}
