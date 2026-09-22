package io.github.douyuconnect;
import io.github.douyuconnect.config.*;
import io.github.douyuconnect.message.*;
import io.github.douyuconnect.transport.Transport;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class DouyuClient implements AutoCloseable {
    final ClientOptions options;
    final Transport transport;
    final ScheduledThreadPoolExecutor scheduler;
    final ExecutorService callbacks;
    private final ExecutorService notifications;
    private final Consumer<ClientEvent> events;
    private final CopyOnWriteArrayList<ListenerSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, RoomSession> rooms = new ConcurrentHashMap<>();
    private final Object lifecycle = new Object();
    private volatile boolean closed;
    private CompletableFuture<Void> closeFuture;

    public DouyuClient() { this(ClientOptions.defaults()); }
    public DouyuClient(ClientOptions options) {
        this(options, new io.github.douyuconnect.transport.NettyTransport(), event ->
            System.getLogger(DouyuClient.class.getName()).log(System.Logger.Level.DEBUG, event.toString()));
    }
    public DouyuClient(ClientOptions options, Consumer<ClientEvent> events) {
        this(options, new io.github.douyuconnect.transport.NettyTransport(), events);
    }
    /** Explicit TLS policy selection; compatibility mode has a process-wide JSSE effect. */
    public DouyuClient(ClientOptions options, TlsMode tlsMode, Consumer<ClientEvent> events) {
        this(options, new io.github.douyuconnect.transport.NettyTransport(tlsMode), events);
    }

    /** Owns and closes the supplied transport. Listeners must not block I/O or lifecycle futures. */
    public DouyuClient(ClientOptions options, Transport transport, Consumer<ClientEvent> events) {
        this.options = Objects.requireNonNull(options); this.transport = Objects.requireNonNull(transport);
        this.events = Objects.requireNonNull(events);
        scheduler = new ScheduledThreadPoolExecutor(2, threads("douyu-timer-"));
        scheduler.setRemoveOnCancelPolicy(true);
        callbacks = Executors.newFixedThreadPool(options.callbackThreads(), threads("douyu-callback-"));
        notifications = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1024),threads("douyu-event-"),new ThreadPoolExecutor.AbortPolicy());
    }
    static ThreadFactory threads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return task -> { Thread thread = new Thread(task, prefix + counter.incrementAndGet()); thread.setDaemon(true); return thread; };
    }
    public CompletionStage<Void> connect(String roomId, ConnectionConfig config) {
        validateRoom(roomId); Objects.requireNonNull(config);
        synchronized (lifecycle) {
            if (closed) return failed("Client closed");
            RoomSession room = rooms.get(roomId);
            if (room != null) return expose(room.connectExisting(config));
            room = new RoomSession(this, roomId, config); rooms.put(roomId, room);
            return expose(room.start());
        }
    }
    public CompletionStage<Void> disconnect(String roomId) {
        RoomSession room = rooms.get(roomId);
        if (room == null) return CompletableFuture.completedFuture(null);
        // Registry removal precedes completion visible to callers; subscriptions are intentionally retained.
        CompletableFuture<Void> done = room.disconnect().thenRun(() -> rooms.remove(roomId, room)).toCompletableFuture();
        return expose(done.copy().orTimeout(options.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS));
    }
    public CompletionStage<Void> updateSender(String roomId, SenderConfig config) {
        Objects.requireNonNull(config);
        RoomSession room = rooms.get(roomId);
        return room == null ? failed("Room is not connected") : expose(room.updateSender(config));
    }
    public CompletionStage<Void> reconnect(String roomId, ReconnectScope scope) {
        Objects.requireNonNull(scope);
        RoomSession room = rooms.get(roomId);
        return room == null ? failed("Room is not connected") : expose(room.reconnect(scope));
    }
    public CompletionStage<SendResult> sendChat(String roomId, String text) {
        Objects.requireNonNull(text);
        if (text.isBlank() || text.indexOf('\0') >= 0) throw new IllegalArgumentException("Message must be nonblank and contain no NUL");
        RoomSession room = rooms.get(roomId);
        return room == null ? CompletableFuture.completedFuture(new SendResult(SendResult.Status.NOT_READY, null)) : expose(room.sendChat(text));
    }
    public Subscription subscribe(MessageFilter filter, Consumer<DouyuMessage> handler) {
        Objects.requireNonNull(filter); Objects.requireNonNull(handler);
        synchronized (lifecycle) {
            if (closed) throw new IllegalStateException("Client closed");
            ListenerSubscription[] holder = new ListenerSubscription[1];
            holder[0] = new ListenerSubscription(filter, handler, () -> subscriptions.remove(holder[0]));
            subscriptions.add(holder[0]); return holder[0];
        }
    }
    public <T extends DouyuMessage> Subscription subscribe(Class<T> type, Consumer<T> handler) {
        Objects.requireNonNull(type); Objects.requireNonNull(handler);
        return subscribe(type::isInstance, message -> handler.accept(type.cast(message)));
    }
    Runnable delivery(DouyuMessage message) {
        List<ListenerSubscription> snapshot = List.copyOf(subscriptions);
        return () -> snapshot.forEach(subscription -> subscription.deliver(message, error -> emit(new ClientEvent(
            message.context().roomId(), ChannelKind.RECEIVE, message.context().connectionId(), null,
            ClientEvent.Kind.CALLBACK_ERROR, error.getClass().getSimpleName()))));
    }
    void emit(ClientEvent event) {
        try { notifications.execute(() -> {
            try { events.accept(event); }
            catch (Throwable error) { System.getLogger(DouyuClient.class.getName()).log(System.Logger.Level.WARNING, "Client event listener failed"); }
        }); }
        catch (RejectedExecutionException e) {
            if (!closed) System.getLogger(DouyuClient.class.getName()).log(System.Logger.Level.WARNING, "Client event queue full");
        }
    }
    public RoomStatus status(String roomId) {
        RoomSession room = rooms.get(roomId);
        return room == null ? new RoomStatus(roomId, "", 0, ConnectionState.DISCONNECTED, ConnectionState.DISCONNECTED) : room.status();
    }
    /** Includes connecting, retrying and draining rooms; consult status for readiness. */
    public Set<String> roomIds() { return Set.copyOf(rooms.keySet()); }
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycle) {
            if (closeFuture != null) return closeFuture.minimalCompletionStage();
            closed = true; closeFuture = new CompletableFuture<>();
            CompletableFuture<?>[] pending = rooms.keySet().stream().map(id -> disconnect(id).toCompletableFuture()).toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(pending).whenComplete((unused, error) -> {
                subscriptions.forEach(ListenerSubscription::cancel);
                scheduler.shutdownNow(); callbacks.shutdown(); notifications.shutdown();
                CompletableFuture.runAsync(() -> {
                    Throwable failure = error;
                    try { transport.close(); }
                    catch (Throwable transportError) { if (failure == null) failure = transportError; }
                    long deadline = System.nanoTime() + options.shutdownTimeout().toNanos();
                    for (ExecutorService executor : List.of(scheduler, callbacks, notifications)) {
                        try {
                            if (!executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                                executor.shutdownNow();
                                if (failure == null) failure = new TimeoutException("A client listener did not finish during shutdown");
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt(); executor.shutdownNow();
                            if (failure == null) failure = interrupted;
                        }
                    }
                    if (failure == null) closeFuture.complete(null); else closeFuture.completeExceptionally(failure);
                });
            });
            return closeFuture.minimalCompletionStage();
        }
    }
    @Override public void close() {
        try { closeAsync().toCompletableFuture().get(options.shutdownTimeout().toMillis() * 2 + 1000, TimeUnit.MILLISECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("Interrupted closing client",e); }
        catch (ExecutionException | TimeoutException e) { throw new IllegalStateException("Client did not close cleanly",e); }
    }
    private static void validateRoom(String roomId) {
        if (roomId == null || !roomId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Use a positive numeric canonical room ID");
    }
    static <T> CompletionStage<T> failed(String reason) { return CompletableFuture.failedFuture(new IllegalStateException(reason)); }
    /** Never run application future continuations inside a room lock or an I/O callback. */
    private static <T> CompletionStage<T> expose(CompletionStage<T> internal) {
        CompletableFuture<T> external = new CompletableFuture<>();
        internal.whenCompleteAsync((value, error) -> {
            if (error == null) external.complete(value); else external.completeExceptionally(error);
        });
        return external.minimalCompletionStage();
    }
}
