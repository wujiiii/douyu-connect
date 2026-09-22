package io.github.douyuconnect.transport;

import io.github.douyuconnect.config.TlsMode;
import io.netty.buffer.UnpooledByteBufAllocator;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Executed only by TlsCompatibilityTest in a disposable JVM, with a generated local certificate. */
public final class TlsProcessProbe {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String original = Security.getProperty("jdk.tls.disabledAlgorithms");
        if (!original.contains("TLS_RSA_*")) original += ", TLS_RSA_*";
        Security.setProperty("jdk.tls.disabledAlgorithms",original);
        if (mode.equals("system-default")) {
            TlsSupport.createContext(TlsMode.SYSTEM_DEFAULT);
            check(original.equals(Security.getProperty("jdk.tls.disabledAlgorithms")),"Default mode changed process policy");
        } else if (mode.equals("late")) {
            SSLContext.getDefault();
            expectConfigurationFailure();
        } else if (mode.equals("extra-rule")) {
            Security.setProperty("jdk.tls.disabledAlgorithms",original + ", " + TlsSupport.DOUYU_CIPHER);
            expectConfigurationFailure();
            check(Security.getProperty("jdk.tls.disabledAlgorithms").contains(TlsSupport.DOUYU_CIPHER),"Explicit algorithm restriction was removed");
        } else {
            var context = TlsSupport.createContext(TlsMode.DOUYU_COMPATIBLE);
            SSLEngine engine = context.newEngine(UnpooledByteBufAllocator.DEFAULT,"localhost",443);
            check(Arrays.equals(new String[]{"TLSv1.2"},engine.getEnabledProtocols()),"Expected TLS 1.2 only");
            check(Arrays.equals(new String[]{TlsSupport.DOUYU_CIPHER},engine.getEnabledCipherSuites()),"Expected precisely the verified cipher");
            String expected = String.join(", ",Arrays.stream(original.split(",")).map(String::trim)
                .filter(rule -> !rule.equals("TLS_RSA_*") && !rule.equals("TLS_RSA_")).toList());
            check(expected.equals(Security.getProperty("jdk.tls.disabledAlgorithms")),"Other algorithm restrictions changed");
            TlsSupport.createContext(TlsMode.DOUYU_COMPATIBLE);
            check(expected.equals(Security.getProperty("jdk.tls.disabledAlgorithms")),"Repeated initialization is not idempotent");
            engine.closeOutbound();
            if (!mode.equals("compatible")) verifyHandshake(mode,Path.of(args[1]));
        }
        System.out.println("OK " + mode);
    }

    private static void expectConfigurationFailure() throws Exception {
        try { TlsSupport.createContext(TlsMode.DOUYU_COMPATIBLE); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("Incompatible cached/explicit TLS policy must fail fast");
    }

    private static void verifyHandshake(String mode,Path keyStoreFile) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keyStoreFile)) { keyStore.load(input,"test-password".toCharArray()); }
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(keyStore,"test-password".toCharArray());
        SSLContext serverContext = SSLContext.getInstance("TLSv1.2"); serverContext.init(keys.getKeyManagers(),null,null);
        try (SSLServerSocket server = (SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,10,InetAddress.getByName("127.0.0.1"))) {
            server.setEnabledProtocols(new String[]{"TLSv1.2"});
            server.setEnabledCipherSuites(new String[]{TlsSupport.DOUYU_CIPHER}); server.setSoTimeout(5000);
            Thread responder = new Thread(() -> respond(server),"local-tls-test"); responder.setDaemon(true); responder.start();
            try (NettyTransport transport = new NettyTransport(1,TlsMode.DOUYU_COMPATIBLE)) {
                CompletableFuture<Void> opened = new CompletableFuture<>();
                String host = mode.equals("wrong-host") ? "127.0.0.1" : "localhost";
                Transport.Connection connection = transport.open(URI.create("wss://" + host + ":" + server.getLocalPort() + "/"),Duration.ofSeconds(3),4096,new Transport.Listener() {
                    public void onOpen() { opened.complete(null); }
                    public void onMessage(String text) { }
                    public void onClosed(Throwable cause) { opened.completeExceptionally(cause == null ? new IOException("Connection closed") : cause); }
                });
                try {
                    opened.get(5,TimeUnit.SECONDS);
                    check(mode.equals("trusted"),"TLS accepted an untrusted certificate or wrong hostname");
                } catch (ExecutionException error) {
                    check(!mode.equals("trusted"),"Trusted TLS failed: " + error.getCause());
                    boolean certificateFailure = false;
                    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                        if (cause instanceof java.security.cert.CertificateException) certificateFailure = true;
                    }
                    check(certificateFailure,"Negative test must fail certificate validation, not an unrelated socket error");
                } finally { connection.close().toCompletableFuture().get(3,TimeUnit.SECONDS); }
            }
            responder.join(5000); check(!responder.isAlive(),"Local TLS server did not terminate");
        }
    }

    private static void respond(SSLServerSocket server) {
        try (SSLSocket socket = (SSLSocket)server.accept()) {
            socket.setSoTimeout(5000); socket.startHandshake();
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));
            String key = null;
            for (String line; (line = input.readLine()) != null && !line.isEmpty();) {
                if (line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) key = line.substring(line.indexOf(':')+1).trim();
            }
            if (key == null) return;
            String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            socket.getInputStream().read();
        } catch (Exception expectedInNegativeCases) { /* Client verifies the precise failure cause. */ }
    }
    private static void check(boolean condition,String message) { if (!condition) throw new AssertionError(message); }
}
