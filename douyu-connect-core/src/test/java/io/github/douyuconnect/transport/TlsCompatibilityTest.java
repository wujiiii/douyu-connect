package io.github.douyuconnect.transport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TlsCompatibilityTest {
    @TempDir static Path temporary;
    static Path keyStore;

    @BeforeAll static void createLocalTestCertificate() throws Exception {
        keyStore = temporary.resolve("localhost.p12");
        run(List.of(executable("keytool"),"-genkeypair","-alias","localhost","-keyalg","RSA","-keysize","2048",
            "-dname","CN=localhost","-ext","SAN=dns:localhost","-validity","30","-storetype","PKCS12",
            "-keystore",keyStore.toString(),"-storepass","test-password","-keypass","test-password","-noprompt"));
    }

    // Separate JVMs prevent the compatibility test from altering the rest of the test suite.
    @ParameterizedTest
    @ValueSource(strings={"system-default","compatible","late","extra-rule","trusted","untrusted","wrong-host"})
    void checksPolicyAndActualTlsHandshakeInIsolatedProcess(String mode) throws Exception {
        List<String> command = new ArrayList<>(List.of(executable("java"),"-Dfile.encoding=UTF-8"));
        if (!mode.equals("untrusted")) command.addAll(List.of("-Djavax.net.ssl.trustStore=" + keyStore,
            "-Djavax.net.ssl.trustStorePassword=test-password","-Djavax.net.ssl.trustStoreType=PKCS12"));
        command.addAll(List.of("-cp",System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),
            TlsProcessProbe.class.getName(),mode,keyStore.toString()));
        assertTrue(run(command).contains("OK " + mode));
    }

    private static String executable(String name) {
        return Path.of(System.getProperty("java.home"),"bin",name + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "")).toString();
    }
    private static String run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(25,TimeUnit.SECONDS),"TLS test process timed out");
            String output = new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(0,process.exitValue(),output);
            return output;
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
