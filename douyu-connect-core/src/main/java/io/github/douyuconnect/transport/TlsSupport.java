package io.github.douyuconnect.transport;

import io.github.douyuconnect.config.TlsMode;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

final class TlsSupport {
    static final String DOUYU_CIPHER = "TLS_RSA_WITH_AES_256_GCM_SHA384";
    private TlsSupport() {}
    static synchronized SslContext createContext(TlsMode mode) throws SSLException {
        Objects.requireNonNull(mode,"mode");
        if (mode == TlsMode.SYSTEM_DEFAULT) return SslContextBuilder.forClient().build();

        // JSSE policy is process-wide and cached on first use. This is an explicit opt-in,
        // not a connection-local setting. Keep every other rule and never change disk files.
        String original = Security.getProperty("jdk.tls.disabledAlgorithms");
        if (original != null) {
            String compatible = String.join(", ",Arrays.stream(original.split(","))
                .map(String::trim).filter(rule -> !rule.equals("TLS_RSA_*") && !rule.equals("TLS_RSA_")).toList());
            if (!compatible.equals(original)) Security.setProperty("jdk.tls.disabledAlgorithms",compatible);
        }
        try {
            SSLContext probe = SSLContext.getInstance("TLSv1.2");
            probe.init(null,null,null);
            if (!Arrays.asList(probe.getDefaultSSLParameters().getCipherSuites()).contains(DOUYU_CIPHER)) {
                throw new IllegalStateException("DOUYU_COMPATIBLE must be selected before the first JSSE/TLS initialization; "
                    + "a cached or explicit security policy still disables " + DOUYU_CIPHER);
            }
        } catch (GeneralSecurityException error) {
            SSLException failure = new SSLException("Cannot initialize Douyu TLS compatibility");
            failure.initCause(error); throw failure;
        }
        return SslContextBuilder.forClient().sslProvider(SslProvider.JDK)
            .protocols("TLSv1.2").ciphers(List.of(DOUYU_CIPHER)).build();
    }
}
