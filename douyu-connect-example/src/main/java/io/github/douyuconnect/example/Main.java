package io.github.douyuconnect.example;

import io.github.douyuconnect.*;
import io.github.douyuconnect.config.*;
import io.github.douyuconnect.message.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only unless the operator explicitly enables a sender and issues a send command. */
public final class Main {
    private Main() {}
    public static void main(String[] args) throws Exception {
        var arguments = new ArrayList<>(Arrays.asList(args));
        TlsMode tlsMode = arguments.remove("--douyu-tls") ? TlsMode.DOUYU_COMPATIBLE : TlsMode.SYSTEM_DEFAULT;
        args = arguments.toArray(String[]::new);
        if (args.length > 0 && args[0].equals("--help")) { help(); return; }
        AtomicLong received = new AtomicLong();
        try (DouyuClient client = new DouyuClient(ClientOptions.defaults(), tlsMode, event -> System.out.println("event " + event))) {
            System.out.println("TLS_MODE=" + tlsMode);
            client.subscribe(MessageFilter.all(), message -> {
                received.incrementAndGet();
                if (message.category() != Category.GENERIC) {
                    System.out.printf("room=%s sequence=%d category=%s%n", message.context().roomId(), message.context().sequence(), message.category());
                }
            });
            if (args.length == 3 && args[0].equals("--observe")) {
                int seconds = Integer.parseInt(args[2]);
                if (seconds < 1 || seconds > 3600) throw new IllegalArgumentException("Duration must be 1..3600 seconds");
                await(client.connect(args[1],ConnectionConfig.receiveOnly()));
                System.out.println("RECEIVE_READY " + args[1]);
                new CountDownLatch(1).await(seconds,TimeUnit.SECONDS);
                await(client.disconnect(args[1]));
                System.out.println("RECEIVED_PACKETS " + received.get());
                return;
            }
            if (args.length != 0) throw new IllegalArgumentException("Use --help or [--douyu-tls] --observe ROOM SECONDS");
            help();
            try (Scanner input = new Scanner(System.in)) {
                while (input.hasNextLine()) {
                    String[] command = input.nextLine().trim().split("\\s+",3);
                    if (command[0].equals("quit")) break;
                    try {
                        if (command.length < 2) { help(); continue; }
                        String room = command[1];
                        switch (command[0]) {
                            case "connect" -> await(client.connect(room,ConnectionConfig.receiveOnly()));
                            case "disconnect" -> await(client.disconnect(room));
                            case "status" -> System.out.println(client.status(room));
                            case "reconnect" -> await(client.reconnect(room,command.length == 3 ? ReconnectScope.valueOf(command[2].toUpperCase()) : ReconnectScope.ALL));
                            case "sender" -> await(client.updateSender(room,SenderConfig.enabled(credentialsFromEnvironment())));
                            case "sender-off" -> await(client.updateSender(room,SenderConfig.disabled()));
                            case "send" -> {
                                if (command.length != 3) throw new IllegalArgumentException("send ROOM TEXT");
                                System.out.println(await(client.sendChat(room,command[2])));
                            }
                            default -> help();
                        }
                    } catch (Exception error) {
                        // Do not print configuration or credentials from exception chains.
                        System.err.println("Command failed: " + error.getClass().getSimpleName() + "; inspect state events for details");
                    }
                }
            }
        }
    }
    private static <T> T await(CompletionStage<T> stage) throws Exception { return stage.toCompletableFuture().get(20,TimeUnit.SECONDS); }
    private static Credentials credentialsFromEnvironment() {
        return new Credentials(required("DOUYU_DEVICE_ID"),Long.parseLong(required("DOUYU_USER_ID")),required("DOUYU_USERNAME"),
            Long.parseLong(required("DOUYU_LOGIN_TICKET_ID")),required("DOUYU_SESSION_TOKEN"),Integer.parseInt(System.getenv().getOrDefault("DOUYU_BIZ","1")));
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }
    private static void help() {
        System.out.println("Read-only observation: [--douyu-tls] --observe ROOM SECONDS");
        System.out.println("--douyu-tls explicitly enables verified TLS 1.2/RSA compatibility for this JVM; certificate/hostname validation stays on.");
        System.out.println("Interactive commands: connect ROOM | disconnect ROOM | status ROOM | reconnect ROOM [RECEIVE|SEND|ALL]");
        System.out.println("Optional sending: sender ROOM (load env) | sender-off ROOM | send ROOM TEXT | quit");
        System.out.println("Sender env: DOUYU_DEVICE_ID, DOUYU_USER_ID, DOUYU_USERNAME, DOUYU_LOGIN_TICKET_ID, DOUYU_SESSION_TOKEN, optional DOUYU_BIZ");
    }
}
