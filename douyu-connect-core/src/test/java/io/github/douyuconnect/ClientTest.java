package io.github.douyuconnect;

import io.github.douyuconnect.config.*;
import io.github.douyuconnect.message.*;
import io.github.douyuconnect.transport.Transport;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class ClientTest {
    static final ConnectionConfig RECEIVE = new ConnectionConfig(List.of(URI.create("ws://localhost:1234/")), SenderConfig.disabled());
    static ClientOptions options() { return new ClientOptions(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(30),
        Duration.ofMillis(15), Duration.ofMillis(30), Duration.ZERO, Duration.ofMillis(150), Duration.ofSeconds(2), 100, 10, 2, 4096); }
    static SenderConfig sender(String token) { return new SenderConfig(new Credentials("device", 123, "user", 456, token, 1), List.of(URI.create("ws://localhost:1235/"))); }
    static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(3, TimeUnit.SECONDS); }
    static FakeWire next(FakeTransport transport) throws Exception { FakeWire wire = transport.opened.poll(3, TimeUnit.SECONDS); assertNotNull(wire); return wire; }
    static void ready(FakeWire wire) { wire.listener.onOpen(); wire.listener.onMessage("type@=loginres/userid@=123/"); }

    @Test void routesMixedGiftMessagesInOrderAcrossDynamicConnections() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            BlockingQueue<DouyuMessage> messages = new LinkedBlockingQueue<>();
            client.subscribe(MessageFilter.categories(Category.GIFT, Category.PANDORA_BROADCAST), messages::add);
            var connected = client.connect("1", RECEIVE); FakeWire first = next(transport); ready(first); await(connected);
            for (int i = 0; i < 30; i++) first.listener.onMessage(i % 2 == 0 ? "type@=dgb/hits@=" + i + "/" : "type@=gbroadcast/btype@=pandora/");
            long last = 0;
            for (int i = 0; i < 30; i++) {
                DouyuMessage message = messages.poll(2, TimeUnit.SECONDS); assertNotNull(message);
                assertEquals(i % 2 == 0 ? Category.GIFT : Category.PANDORA_BROADCAST, message.category());
                assertTrue(message.context().sequence() > last); last = message.context().sequence();
            }
            await(client.disconnect("1")); assertTrue(first.closed);
            first.listener.onMessage("type@=dgb/"); assertNull(messages.poll());
            var again = client.connect("1", RECEIVE); FakeWire second = next(transport); ready(second); await(again);
            second.listener.onMessage("type@=dgb/"); assertNotNull(messages.poll(2, TimeUnit.SECONDS));
        }
        assertTrue(transport.closed);
    }

    @Test void disconnectDrainsCallbacksAndCancellationWaitsForRunningCallback() throws Exception {
        FakeTransport transport = new FakeTransport();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            Subscription subscription = client.subscribe(GiftMessage.class, message -> {
                entered.countDown();
                try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            var connecting = client.connect("1", RECEIVE); FakeWire wire = next(transport); ready(wire); await(connecting);
            wire.listener.onMessage("type@=dgb/"); assertTrue(entered.await(2, TimeUnit.SECONDS));
            var cancelling = subscription.cancel(); var disconnecting = client.disconnect("1");
            assertFalse(cancelling.toCompletableFuture().isDone()); assertFalse(disconnecting.toCompletableFuture().isDone());
            release.countDown(); await(cancelling); await(disconnecting);
        } finally { release.countDown(); }
    }

    @Test void reconnectsAfterRepeatedFailuresButNeverAfterManualDisconnect() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            client.connect("1", RECEIVE);
            next(transport).listener.onClosed(new IllegalStateException("offline"));
            next(transport).listener.onClosed(new IllegalStateException("offline again"));
            FakeWire recovered = next(transport); ready(recovered);
            assertEquals(ConnectionState.READY, client.status("1").receive());
            await(client.disconnect("1"));
            recovered.listener.onClosed(new IllegalStateException("late close"));
            assertNull(transport.opened.poll(120, TimeUnit.MILLISECONDS));
        }
    }

    @Test void lateHandshakeCannotReviveDisconnectedRoom() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var connecting = client.connect("1", RECEIVE); FakeWire old = next(transport);
            await(client.disconnect("1")); old.listener.onOpen(); old.listener.onMessage("type@=loginres/");
            assertTrue(old.closed); assertTrue(connecting.toCompletableFuture().isCompletedExceptionally());
            var again = client.connect("1", RECEIVE); FakeWire current = next(transport); ready(current); await(again);
            old.listener.onClosed(null); assertEquals(ConnectionState.READY, client.status("1").receive());
        }
    }

    @Test void changingCredentialsOnlyReplacesSendConnectionAndCancelsOldRequests() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var connecting = client.connect("1", new ConnectionConfig(RECEIVE.receiveEndpoints(), sender("old-token")));
            FakeWire receive = next(transport), send = next(transport); ready(receive); ready(send); await(connecting);
            var inFlight = client.sendChat("1", "hello/@S"); var queued = client.sendChat("1", "later");
            var updating = client.updateSender("1", sender("new-token"));
            assertEquals(SendResult.Status.UNKNOWN, await(inFlight).status());
            assertEquals(SendResult.Status.CANCELLED, await(queued).status());
            assertFalse(receive.closed); assertTrue(send.closed);
            FakeWire replacement = next(transport); ready(replacement); await(updating);
            assertTrue(replacement.sent.stream().anyMatch(s -> s.contains("stk@=new-token/")));
            var result = client.sendChat("1", "new message");
            send.listener.onMessage("type@=chatres/res@=0/"); assertFalse(result.toCompletableFuture().isDone());
            replacement.listener.onMessage("type@=chatres/res@=0/");
            assertEquals(SendResult.Status.ACKNOWLEDGED, await(result).status());
        }
    }

    @Test void receiptTimeoutReportsUnknownAndReplacesChannelWithoutResending() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var connecting = client.connect("1", new ConnectionConfig(RECEIVE.receiveEndpoints(), sender("token")));
            FakeWire receive = next(transport), send = next(transport); ready(receive); ready(send); await(connecting);
            var result = client.sendChat("1", "one");
            assertEquals(SendResult.Status.UNKNOWN, await(result).status());
            FakeWire replacement = next(transport); ready(replacement);
            assertTrue(replacement.sent.stream().noneMatch(s -> s.contains("chatmessage")));
        }
    }

    @Test void callbackFailureDoesNotPreventOtherSubscribersAndUnknownMessagesSurvive() throws Exception {
        FakeTransport transport = new FakeTransport(); BlockingQueue<ClientEvent> events = new LinkedBlockingQueue<>();
        try (DouyuClient client = new DouyuClient(options(), transport, events::add)) {
            client.subscribe(MessageFilter.all(), message -> { throw new IllegalArgumentException("consumer error"); });
            BlockingQueue<DouyuMessage> received = new LinkedBlockingQueue<>();
            client.subscribe(MessageFilter.protocol("newtype", null), received::add);
            var connecting = client.connect("1", RECEIVE); FakeWire wire = next(transport); ready(wire); await(connecting);
            wire.listener.onMessage("type@=newtype/x@=y/");
            assertInstanceOf(GenericMessage.class, received.poll(2, TimeUnit.SECONDS));
            assertTrue(events.stream().anyMatch(e -> e.kind() == ClientEvent.Kind.CALLBACK_ERROR));
        }
    }

    @Test void credentialsAreRedactedAndDuplicateConnectDoesNotReplaceConfiguration() throws Exception {
        FakeTransport transport = new FakeTransport();
        assertFalse(sender("secret").toString().contains("secret"));
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var first = client.connect("1", RECEIVE); var duplicate = client.connect("1", RECEIVE);
            FakeWire wire = next(transport); ready(wire); await(first); await(duplicate);
            assertNull(transport.opened.poll());
            assertThrows(ExecutionException.class, () -> await(client.connect("1", ConnectionConfig.withSender(sender("x").credentials()))));
        }
    }

    @Test void requestCompletionCannotSendThroughOldConnectionDuringCredentialChange() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var connecting = client.connect("1", new ConnectionConfig(RECEIVE.receiveEndpoints(), sender("old")));
            FakeWire receive = next(transport), send = next(transport); ready(receive); ready(send); await(connecting);
            var request = client.sendChat("1", "first");
            var attemptedFromCompletion = request.thenCompose(result -> client.sendChat("1", "must not use old connection"));
            client.updateSender("1", sender("new"));
            assertEquals(SendResult.Status.NOT_READY, await(attemptedFromCompletion).status());
            assertEquals(1, send.sent.stream().filter(s -> s.contains("type@=chatmessage/")).count());
        }
    }

    @Test void authFailureStopsRetriesUntilNewCredentialsArrive() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var connecting = client.connect("1", new ConnectionConfig(RECEIVE.receiveEndpoints(), sender("expired")));
            FakeWire receive = next(transport), send = next(transport); ready(receive); send.listener.onOpen();
            send.listener.onMessage("type@=error/code@=4202/");
            assertThrows(ExecutionException.class, () -> await(connecting));
            assertEquals(ConnectionState.AUTH_FAILED, client.status("1").send());
            assertNull(transport.opened.poll(100,TimeUnit.MILLISECONDS));
            var updated = client.updateSender("1", sender("valid")); ready(next(transport)); await(updated);
            assertEquals(ConnectionState.READY, client.status("1").send());
        }
    }

    @Test void roomFilterSurvivesReconnectAndDoesNotReceiveAnotherRoomsMessages() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            BlockingQueue<DouyuMessage> messages = new LinkedBlockingQueue<>();
            client.subscribe(MessageFilter.room("1").and(MessageFilter.categories(Category.GIFT)), messages::add);
            var first = client.connect("1", RECEIVE); FakeWire one = next(transport); ready(one); await(first);
            var second = client.connect("2", RECEIVE); FakeWire two = next(transport); ready(two); await(second);
            two.listener.onMessage("type@=dgb/"); one.listener.onMessage("type@=dgb/");
            DouyuMessage before = messages.poll(2,TimeUnit.SECONDS); assertNotNull(before); assertEquals("1",before.context().roomId());
            var reconnecting = client.reconnect("1", ReconnectScope.RECEIVE); FakeWire newOne = next(transport); ready(newOne); await(reconnecting);
            newOne.listener.onMessage("type@=dgb/");
            DouyuMessage after = messages.poll(2,TimeUnit.SECONDS); assertNotNull(after);
            assertEquals(before.context().roomInstanceId(), after.context().roomInstanceId());
            assertNotEquals(before.context().connectionId(), after.context().connectionId());
            assertTrue(after.context().sequence() > before.context().sequence()); assertNull(messages.poll());
        }
    }

    @Test void queueOverflowDisconnectsExplicitlyAndDrainsAcceptedMessages() throws Exception {
        ClientOptions d = options();
        ClientOptions small = new ClientOptions(d.connectionTimeout(),d.heartbeatInterval(),d.idleTimeout(),d.retryBase(),d.retryMax(),d.sendInterval(),d.receiptTimeout(),d.shutdownTimeout(),1,1,2,4096);
        FakeTransport transport = new FakeTransport(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        BlockingQueue<ClientEvent> events = new LinkedBlockingQueue<>();
        try (DouyuClient client = new DouyuClient(small, transport, events::add)) {
            BlockingQueue<DouyuMessage> messages = new LinkedBlockingQueue<>();
            client.subscribe(GiftMessage.class, message -> {
                entered.countDown();
                try { release.await(2,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                messages.add(message);
            });
            var connecting = client.connect("1", RECEIVE); FakeWire wire = next(transport); ready(wire); await(connecting);
            // Barrier: ensure the login response has left the tiny callback queue.
            CountDownLatch barrier = new CountDownLatch(1);
            Subscription barrierSubscription = client.subscribe(GenericMessage.class, message -> barrier.countDown());
            wire.listener.onMessage("type@=barrier/"); assertTrue(barrier.await(2,TimeUnit.SECONDS)); await(barrierSubscription.cancel());
            wire.listener.onMessage("type@=dgb/hits@=1/"); assertTrue(entered.await(2,TimeUnit.SECONDS));
            wire.listener.onMessage("type@=dgb/hits@=2/"); wire.listener.onMessage("type@=dgb/hits@=3/");
            assertTrue(wire.closed); release.countDown(); await(client.disconnect("1"));
            assertNotNull(messages.poll(2,TimeUnit.SECONDS)); assertNotNull(messages.poll(2,TimeUnit.SECONDS)); assertNull(messages.poll());
            assertTrue(events.stream().anyMatch(event -> event.kind() == ClientEvent.Kind.QUEUE_OVERFLOW));
        } finally { release.countDown(); }
    }

    @Test void repeatedUpdatesWaitForOldConnectionToCloseAndUseOnlyNewestCredentials() throws Exception {
        FakeTransport transport = new FakeTransport(); CompletableFuture<Void> gate = new CompletableFuture<>();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var connected = client.connect("1", new ConnectionConfig(RECEIVE.receiveEndpoints(),sender("initial")));
            FakeWire receive = next(transport), send = next(transport); ready(receive); ready(send); await(connected);
            send.closeGate = gate;
            var first = client.updateSender("1",sender("intermediate"));
            var second = client.updateSender("1",sender("latest"));
            try {
                assertNull(transport.opened.poll(), "Must wait for the old send connection to close");
            } finally { gate.complete(null); }
            FakeWire latest = next(transport); ready(latest); await(second);
            assertTrue(latest.sent.stream().anyMatch(text -> text.contains("stk@=latest/")));
            assertThrows(Exception.class, () -> await(first));
            assertNull(transport.opened.poll());
        } finally { gate.complete(null); }
    }

    @Test void disablingSendingKeepsReceiveAliveAndCancelsQueuedMessages() throws Exception {
        FakeTransport transport = new FakeTransport();
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {})) {
            var connected = client.connect("1",new ConnectionConfig(RECEIVE.receiveEndpoints(),sender("token")));
            FakeWire receive = next(transport), send = next(transport); ready(receive); ready(send); await(connected);
            var sent = client.sendChat("1","first");
            var queued = client.sendChat("1","second");
            await(client.updateSender("1",SenderConfig.disabled()));
            assertEquals(SendResult.Status.UNKNOWN,await(sent).status());
            assertEquals(SendResult.Status.CANCELLED,await(queued).status());
            assertEquals(SendResult.Status.NOT_READY,await(client.sendChat("1","third")).status());
            assertFalse(receive.closed); assertTrue(send.closed);
        }
    }

    @Test void connectionErrorsExposeSafeCauseTypesWithoutExceptionSecrets() throws Exception {
        FakeTransport transport = new FakeTransport(); BlockingQueue<ClientEvent> events = new LinkedBlockingQueue<>();
        try (DouyuClient client = new DouyuClient(options(), transport, events::add)) {
            client.connect("1",RECEIVE); FakeWire wire = next(transport);
            wire.listener.onClosed(new javax.net.ssl.SSLHandshakeException("private-token"));
            ClientEvent retry;
            do { retry = events.poll(2,TimeUnit.SECONDS); assertNotNull(retry); } while (retry.state() != ConnectionState.RETRY_WAIT);
            assertTrue(retry.detail().contains("SSLHandshakeException"));
            assertFalse(retry.detail().contains("private-token"));
        }
    }

    @Test void closeWaitsForClientEventListenerToFinish() throws Exception {
        FakeTransport transport = new FakeTransport(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (DouyuClient client = new DouyuClient(options(), transport, event -> {
            entered.countDown();
            try { release.await(2,TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        })) {
            client.connect("1",RECEIVE); assertTrue(entered.await(2,TimeUnit.SECONDS));
            var closing = client.closeAsync().toCompletableFuture();
            try { assertThrows(TimeoutException.class, () -> closing.get(100,TimeUnit.MILLISECONDS)); }
            finally { release.countDown(); }
            await(closing);
        } finally { release.countDown(); }
    }

    static class FakeTransport implements Transport {
        final BlockingQueue<FakeWire> opened = new LinkedBlockingQueue<>(); boolean closed;
        public Connection open(URI uri, Duration timeout, int max, Listener listener) {
            FakeWire wire = new FakeWire(listener); opened.add(wire); return wire;
        }
        public void close() { closed = true; }
    }
    static class FakeWire implements Transport.Connection {
        final Transport.Listener listener; final List<String> sent = new CopyOnWriteArrayList<>(); volatile boolean closed;
        CompletableFuture<Void> closeGate;
        FakeWire(Transport.Listener listener) { this.listener = listener; }
        public CompletionStage<Void> send(String text) {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("closed"));
            sent.add(text); return CompletableFuture.completedFuture(null);
        }
        public CompletionStage<Void> close() { closed = true; return closeGate == null ? CompletableFuture.completedFuture(null) : closeGate; }
    }
}
