package io.github.douyuconnect;
import java.util.*;
import java.util.concurrent.*;
/** Bounded serial callback queue using a shared pool, never the I/O event loop. */
final class SerialMailbox {
    private final Executor executor;
    private final int capacity;
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
    private final List<CompletableFuture<Void>> waiters = new ArrayList<>();
    private boolean running;
    SerialMailbox(Executor executor, int capacity) { this.executor = executor; this.capacity = capacity; }
    synchronized boolean offer(Runnable action) {
        if (queue.size() >= capacity) return false;
        queue.add(action);
        if (!running) {
            running = true;
            try { executor.execute(this::run); }
            catch (RejectedExecutionException e) { running = false; queue.removeLast(); return false; }
        }
        return true;
    }
    synchronized CompletableFuture<Void> drained() {
        if (!running && queue.isEmpty()) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> future = new CompletableFuture<>(); waiters.add(future); return future;
    }
    private void run() {
        while (true) {
            Runnable action;
            List<CompletableFuture<Void>> complete = null;
            synchronized (this) {
                action = queue.poll();
                if (action == null) {
                    running = false; complete = new ArrayList<>(waiters); waiters.clear();
                }
            }
            if (complete != null) { complete.forEach(f -> f.complete(null)); return; }
            try { action.run(); }
            catch (Throwable e) { System.getLogger(SerialMailbox.class.getName()).log(System.Logger.Level.ERROR, "Callback task failed: " + e.getClass().getSimpleName()); }
        }
    }
}
