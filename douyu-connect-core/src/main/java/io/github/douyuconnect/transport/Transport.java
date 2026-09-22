package io.github.douyuconnect.transport;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
/** Asynchronous connection adapter. Callbacks for one connection must be ordered.
 * open returns before any callback; failed attempts must eventually call onClosed. */
public interface Transport extends AutoCloseable {
    Connection open(URI endpoint, Duration timeout, int maxPacketLength, Listener listener);
    interface Connection {
        CompletionStage<Void> send(String stt);
        CompletionStage<Void> close();
    }
    interface Listener {
        void onOpen();
        void onMessage(String stt);
        void onClosed(Throwable cause);
    }
    @Override void close();
}
