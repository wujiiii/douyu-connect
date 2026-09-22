package io.github.douyuconnect;
import io.github.douyuconnect.message.DouyuMessage;
import java.util.concurrent.*;
import java.util.function.*;
final class ListenerSubscription implements Subscription {
    private final MessageFilter filter;
    private final Consumer<DouyuMessage> handler;
    private final Runnable remove;
    private boolean active = true;
    private int running;
    private final CompletableFuture<Void> cancelled = new CompletableFuture<>();
    ListenerSubscription(MessageFilter filter, Consumer<DouyuMessage> handler, Runnable remove) {
        this.filter = filter; this.handler = handler; this.remove = remove;
    }
    void deliver(DouyuMessage message, Consumer<Throwable> errors) {
        synchronized (this) { if (!active) return; running++; }
        try { if (filter.test(message)) handler.accept(message); }
        catch (Throwable error) { errors.accept(error); }
        finally {
            boolean finished;
            synchronized (this) { running--; finished = !active && running == 0; }
            if (finished) cancelled.complete(null);
        }
    }
    public CompletionStage<Void> cancel() {
        boolean finished;
        synchronized (this) { active = false; finished = running == 0; }
        remove.run();
        if (finished) cancelled.complete(null);
        return cancelled.minimalCompletionStage();
    }
}
