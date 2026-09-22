package io.github.douyuconnect.config;

/** DOUYU_COMPATIBLE changes the process-wide JSSE algorithm policy; select before any TLS use. */
public enum TlsMode {
    SYSTEM_DEFAULT,
    DOUYU_COMPATIBLE
}
