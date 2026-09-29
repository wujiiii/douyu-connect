package io.github.douyuconnect;

import io.github.douyuconnect.config.*;
import io.github.douyuconnect.message.*;
import io.github.douyuconnect.protocol.*;
import io.github.douyuconnect.transport.Transport;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** All room state transitions are serialized by this monitor; user callbacks run elsewhere. */
final class RoomSession {
    private final DouyuClient owner;
    private final String roomId;
    private final String instanceId = UUID.randomUUID().toString();
    private final SerialMailbox mailbox;
    private final Lane receive = new Lane(ChannelKind.RECEIVE);
    private final Lane send = new Lane(ChannelKind.SEND);
    private final ArrayDeque<PendingSend> pending = new ArrayDeque<>();
    private ConnectionConfig config;
    private long version = 1;
    private long sequence;
    private boolean desired = true;
    private CompletableFuture<Void> disconnected;
    private PendingSend inFlight;
    private ScheduledFuture<?> receiptTimer;
    private ScheduledFuture<?> sendTimer;
    private long lastSentNanos;

    RoomSession(DouyuClient owner, String roomId, ConnectionConfig config) {
        this.owner = owner; this.roomId = roomId; this.config = config;
        mailbox = new SerialMailbox(owner.callbacks, owner.options.messageQueueCapacity());
    }
    synchronized CompletionStage<Void> start() {
        open(receive);
        if (config.sender().enabled()) open(send);
        return readiness();
    }
    synchronized CompletionStage<Void> connectExisting(ConnectionConfig requested) {
        if (!desired) return DouyuClient.failed("Room is disconnecting");
        if (!config.equals(requested)) return DouyuClient.failed("Configuration differs; use updateSender or disconnect/connect");
        return readiness();
    }
    private CompletionStage<Void> readiness() {
        return (config.sender().enabled() ? CompletableFuture.allOf(receive.ready, send.ready) : receive.ready.copy()).minimalCompletionStage();
    }
    synchronized RoomStatus status() { return new RoomStatus(roomId, instanceId, version, receive.state, send.state); }

    synchronized CompletionStage<Void> disconnect() {
        if (disconnected != null) return disconnected.minimalCompletionStage();
        desired = false;
        disconnected = new CompletableFuture<>();
        cancelRequests();
        CompletableFuture<Void> receiveClosed = invalidate(receive);
        CompletableFuture<Void> sendClosed = invalidate(send);
        state(receive, ConnectionState.DISCONNECTED, "Disconnected by caller");
        state(send, ConnectionState.DISCONNECTED, "Disconnected by caller");
        CompletableFuture.allOf(receiveClosed, sendClosed, mailbox.drained()).whenComplete((unused, error) -> {
            if (error == null) disconnected.complete(null); else disconnected.completeExceptionally(error);
        });
        return disconnected.minimalCompletionStage();
    }

    synchronized CompletionStage<Void> updateSender(SenderConfig replacement) {
        if (!desired) return DouyuClient.failed("Room is disconnecting");
        if (config.sender().equals(replacement)) return send.ready.copy().minimalCompletionStage();
        config = new ConnectionConfig(config.receiveEndpoints(), replacement); version++;
        send.state = ConnectionState.DISCONNECTED;
        cancelRequests();
        return replace(send, replacement.enabled());
    }

    synchronized CompletionStage<Void> reconnect(ReconnectScope scope) {
        if (!desired) return DouyuClient.failed("Room is disconnecting");
        List<CompletableFuture<Void>> results = new ArrayList<>();
        if (scope != ReconnectScope.SEND) results.add(replace(receive, true).toCompletableFuture());
        if (scope != ReconnectScope.RECEIVE) {
            send.state = ConnectionState.DISCONNECTED;
            cancelRequests(); results.add(replace(send, config.sender().enabled()).toCompletableFuture());
        }
        return CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).minimalCompletionStage();
    }

    private CompletionStage<Void> replace(Lane lane, boolean enabled) {
        CompletableFuture<Void> closed = invalidate(lane);
        long generation = lane.generation;
        lane.ready = new CompletableFuture<>();
        CompletableFuture<Void> result = lane.ready;
        state(lane, ConnectionState.DISCONNECTED, "Replacing connection");
        closed.whenComplete((unused, error) -> {
            synchronized (RoomSession.this) {
                if (!desired || generation != lane.generation) return;
                if (error != null) { result.completeExceptionally(error); return; }
                if (enabled) open(lane); else result.complete(null);
            }
        });
        return result.minimalCompletionStage();
    }

    private void open(Lane lane) {
        if (!desired || (lane.kind == ChannelKind.SEND && !config.sender().enabled())) return;
        lane.retry = null;
        if (lane.ready.isDone()) lane.ready = new CompletableFuture<>();
        long generation = ++lane.generation;
        lane.connectionId = UUID.randomUUID().toString();
        lane.lastActiveNanos = System.nanoTime();
        lane.loginAccepted = false;
        state(lane, ConnectionState.CONNECTING, "Connecting");
        List<URI> endpoints = lane.kind == ChannelKind.RECEIVE ? config.receiveEndpoints() : config.sender().endpoints();
        URI endpoint = endpoints.get(Math.floorMod(lane.endpointIndex++, endpoints.size()));
        try {
            lane.wire = owner.transport.open(endpoint, owner.options.connectionTimeout(), owner.options.maxPacketLength(), new Transport.Listener() {
                public void onOpen() { synchronized (RoomSession.this) { opened(lane, generation); } }
                public void onMessage(String text) { synchronized (RoomSession.this) { message(lane, generation, text); } }
                public void onClosed(Throwable cause) { synchronized (RoomSession.this) { failed(lane, generation, false, closeDetail("Connection closed", cause)); } }
            });
            lane.deadline = owner.scheduler.schedule(() -> {
                synchronized (RoomSession.this) { failed(lane, generation, false, "Connection/login timed out"); }
            }, owner.options.connectionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) { failed(lane, generation, false, closeDetail("Connection attempt failed", e)); }
    }

    private boolean current(Lane lane, long generation) { return desired && lane.generation == generation; }
    private void opened(Lane lane, long generation) {
        if (!current(lane, generation)) return;
        state(lane, ConnectionState.AUTHENTICATING, "WebSocket established; logging in");
        Credentials credentials = lane.kind == ChannelKind.SEND ? config.sender().credentials() : null;
        write(lane, generation, Commands.login(roomId, credentials));
    }

    private void message(Lane lane, long generation, String raw) {
        if (!current(lane, generation)) return;
        lane.lastActiveNanos = System.nanoTime();
        Map<String,String> fields;
        try { fields = Stt.decode(raw); }
        catch (IllegalArgumentException e) {
            event(lane, ClientEvent.Kind.PROTOCOL_ERROR, "Malformed STT message"); fields = Map.of();
        }
        String type = fields.getOrDefault("type", "");
        if (lane.kind == ChannelKind.RECEIVE) {
            DouyuMessage message = MessageParser.parse(roomId, instanceId, lane.connectionId, ++sequence, Instant.now(), raw);
            if (!mailbox.offer(owner.delivery(message))) {
                event(lane, ClientEvent.Kind.QUEUE_OVERFLOW, "Receive queue exhausted; continuity lost; disconnecting room");
                owner.disconnect(roomId); return;
            }
        }
        if ("error".equals(type)) {
            String code = fields.getOrDefault("code", "unknown");
            boolean auth = lane.kind == ChannelKind.SEND && (lane.state == ConnectionState.AUTHENTICATING || Set.of("4202","4207").contains(code));
            failed(lane, generation, auth, "Server error code=" + safeCode(code)); return;
        }
        if ("loginres".equals(type) && !lane.loginAccepted) {
            String result = fields.get("res");
            if (result != null && !"0".equals(result)) { failed(lane, generation, lane.kind == ChannelKind.SEND, "Login rejected"); return; }
            lane.loginAccepted = true;
            lane.wire.send(Commands.join(roomId, lane.kind == ChannelKind.SEND)).whenComplete((unused, error) -> {
                synchronized (RoomSession.this) {
                    if (!current(lane, generation)) return;
                    if (error != null) { failed(lane, generation, false, "Room initialization write failed"); return; }
                    cancel(lane.deadline); lane.deadline = null; lane.attempts = 0;
                    state(lane, ConnectionState.READY, "Protocol login accepted; room initialization sent");
                    lane.heartbeat = owner.scheduler.scheduleAtFixedRate(() -> {
                        synchronized (RoomSession.this) { heartbeat(lane, generation); }
                    }, owner.options.heartbeatInterval().toMillis(), owner.options.heartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
                    lane.ready.complete(null);
                }
            });
        }
        if (("pingreq".equals(type) || "msgrepeaterproxylist".equals(type)) && lane.kind == ChannelKind.SEND) {
            write(lane, generation, Commands.heartbeat(true));
        }
        if (lane.kind == ChannelKind.SEND && "chatres".equals(type) && inFlight != null && fields.containsKey("res")) {
            PendingSend completed = inFlight; inFlight = null; cancel(receiptTimer); receiptTimer = null;
            SendResult result = SendResult.fromChatResponse(fields);
            if (result.status() == SendResult.Status.REJECTED) {
                event(lane,ClientEvent.Kind.SEND_REJECTED,"chatres res=" + safeCode(result.serverCode())
                    + " reason=" + result.reason() + " message=" + result.message());
            }
            completed.result.complete(result);
            pump();
        }
    }

    private void heartbeat(Lane lane, long generation) {
        if (!current(lane, generation)) return;
        if (System.nanoTime() - lane.lastActiveNanos > owner.options.idleTimeout().toNanos()) {
            failed(lane, generation, false, "No inbound messages before idle timeout"); return;
        }
        write(lane, generation, Commands.heartbeat(lane.kind == ChannelKind.SEND));
    }
    private void write(Lane lane, long generation, String text) {
        lane.wire.send(text).whenComplete((unused, error) -> {
            if (error != null) synchronized (RoomSession.this) { failed(lane, generation, false, "Network write failed"); }
        });
    }

    private void failed(Lane lane, long generation, boolean authentication, String detail) {
        if (!current(lane, generation)) return;
        lane.ready.completeExceptionally(new IllegalStateException(detail));
        lane.state = authentication ? ConnectionState.AUTH_FAILED : ConnectionState.RETRY_WAIT;
        if (lane.kind == ChannelKind.SEND) cancelRequests();
        invalidate(lane);
        if (authentication) {
            state(lane, ConnectionState.AUTH_FAILED, detail + "; update credentials or reconnect explicitly"); return;
        }
        state(lane, ConnectionState.RETRY_WAIT, detail);
        long base = owner.options.retryBase().toMillis(), maximum = owner.options.retryMax().toMillis();
        long delay = base;
        for (int i = 0; i < Math.min(lane.attempts++, 30) && delay < maximum; i++) delay = delay > maximum / 2 ? maximum : delay * 2;
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, delay / 5));
        delay = Math.min(maximum, delay + Math.min(jitter, maximum - delay));
        long expected = lane.generation;
        lane.retry = owner.scheduler.schedule(() -> {
            synchronized (RoomSession.this) {
                if (!current(lane, expected)) return;
                lane.closing.whenComplete((unused, error) -> {
                    synchronized (RoomSession.this) {
                        if (!current(lane, expected)) return;
                        if (error == null) open(lane);
                        else state(lane,ConnectionState.DISCONNECTED,"Previous connection failed to close");
                    }
                });
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private CompletableFuture<Void> invalidate(Lane lane) {
        lane.generation++;
        cancel(lane.retry); cancel(lane.deadline); cancel(lane.heartbeat);
        lane.retry = null; lane.deadline = null; lane.heartbeat = null;
        lane.ready.completeExceptionally(new CancellationException("Connection replaced or disconnected"));
        Transport.Connection wire = lane.wire; lane.wire = null;
        if (wire == null) return lane.closing;
        CompletableFuture<Void> closing;
        try { closing = wire.close().toCompletableFuture(); }
        catch (RuntimeException e) { closing = CompletableFuture.failedFuture(e); }
        lane.closing = CompletableFuture.allOf(lane.closing, closing);
        return lane.closing;
    }

    synchronized CompletionStage<SendResult> sendChat(String text) {
        if (!desired || send.state != ConnectionState.READY) return result(SendResult.Status.NOT_READY);
        if (Commands.chat(text, config.sender().credentials()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 9 > owner.options.maxPacketLength())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message exceeds packet limit"));
        if (pending.size() >= owner.options.sendQueueCapacity()) return result(SendResult.Status.QUEUE_FULL);
        PendingSend request = new PendingSend(text); pending.add(request); pump();
        return request.result.minimalCompletionStage();
    }
    private void pump() {
        if (!desired || send.state != ConnectionState.READY || inFlight != null || pending.isEmpty() || sendTimer != null) return;
        long delay = owner.options.sendInterval().toNanos() - (System.nanoTime() - lastSentNanos);
        if (lastSentNanos != 0 && delay > 0) {
            sendTimer = owner.scheduler.schedule(() -> {
                synchronized (RoomSession.this) { sendTimer = null; pump(); }
            }, delay, TimeUnit.NANOSECONDS); return;
        }
        PendingSend request = pending.remove(); inFlight = request; lastSentNanos = System.nanoTime();
        long generation = send.generation;
        receiptTimer = owner.scheduler.schedule(() -> {
            synchronized (RoomSession.this) {
                if (current(send, generation) && inFlight == request) failed(send, generation, false, "Receipt timed out; delivery unknown");
            }
        }, owner.options.receiptTimeout().toMillis(), TimeUnit.MILLISECONDS);
        write(send, generation, Commands.chat(request.text, config.sender().credentials()));
    }
    private void cancelRequests() {
        cancel(receiptTimer); cancel(sendTimer); receiptTimer = null; sendTimer = null;
        PendingSend previous = inFlight; inFlight = null;
        List<PendingSend> queued = new ArrayList<>(pending); pending.clear();
        if (previous != null) previous.result.complete(new SendResult(SendResult.Status.UNKNOWN,null));
        queued.forEach(request -> request.result.complete(new SendResult(SendResult.Status.CANCELLED,null)));
    }
    private static CompletionStage<SendResult> result(SendResult.Status status) { return CompletableFuture.completedFuture(new SendResult(status,null)); }
    private void state(Lane lane, ConnectionState state, String detail) { lane.state = state; event(lane,ClientEvent.Kind.STATE,detail); }
    private void event(Lane lane, ClientEvent.Kind kind, String detail) { owner.emit(new ClientEvent(roomId,lane.kind,lane.connectionId,lane.state,kind,detail)); }
    private static String safeCode(String value) { return value.matches("[0-9]{1,10}") ? value : "unknown"; }
    private static String closeDetail(String prefix, Throwable error) { return prefix + causeTypes(error) + packetDiagnostic(error); }
    private static String packetDiagnostic(Throwable error) {
        for (int i = 0; error != null && i < 8; i++, error = error.getCause()) if (error instanceof io.github.douyuconnect.protocol.PacketFormatException packet) return packet.diagnostic();
        return "";
    }
    private static String causeTypes(Throwable error) {
        StringBuilder types = new StringBuilder();
        for (int i = 0; error != null && i < 5; i++, error = error.getCause()) types.append(" / ").append(error.getClass().getSimpleName());
        return types.toString();
    }
    private static void cancel(Future<?> future) { if (future != null) future.cancel(false); }
    private static final class PendingSend {
        final String text; final CompletableFuture<SendResult> result = new CompletableFuture<>();
        PendingSend(String text) { this.text = text; }
    }
    private static final class Lane {
        final ChannelKind kind;
        ConnectionState state = ConnectionState.DISCONNECTED;
        CompletableFuture<Void> ready = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> closing = CompletableFuture.completedFuture(null);
        long generation, lastActiveNanos;
        String connectionId = "";
        int endpointIndex, attempts;
        boolean loginAccepted;
        Transport.Connection wire;
        ScheduledFuture<?> retry, deadline, heartbeat;
        Lane(ChannelKind kind) { this.kind = kind; }
    }
}
