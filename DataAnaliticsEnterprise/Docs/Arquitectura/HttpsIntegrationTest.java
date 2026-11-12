import org.dataanaliticsent.DataAnaliticsEnterprise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** TLS real, certificado local efímero y secretos leídos mediante configtree fuera del repositorio. */
@SpringBootTest(classes=DataAnaliticsEnterprise.class, webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties="M2_SECRETS_DIRECTORY=${m2.test.tls.directory}")
@ActiveProfiles({"local","production"})
@EnabledIfEnvironmentVariable(named="M2_ROLE_TEST_JDBC_URL",matches=".+")
class HttpsIntegrationTest {
    private static final String PASSWORD = "Isolated-TLS-Test-Password-2026";
    private static final Path DIRECTORY = setupSecrets();
    @LocalServerPort int port;

    private static Path setupSecrets() {
        try {
            Path directory = Files.createTempDirectory("m2-tls-test-");
            Files.writeString(directory.resolve("m2.security.data-key-id"), "tls-test");
            Files.writeString(directory.resolve("m2.security.data-key-base64"), Base64.getEncoder().encodeToString(new byte[32]));
            byte[] hmac = new byte[32]; Arrays.fill(hmac,(byte)5);
            Files.writeString(directory.resolve("m2.security.email-lookup-key-base64"),Base64.getEncoder().encodeToString(hmac));
            Files.writeString(directory.resolve("server.ssl.key-store-password"),PASSWORD);
            String keytool = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool").toString();
            var process = new ProcessBuilder(keytool,"-genkeypair","-alias","server","-keyalg","RSA","-keysize","2048","-storetype","PKCS12",
                    "-keystore",directory.resolve("server.p12").toString(),"-storepass",PASSWORD,"-dname","CN=localhost",
                    "-ext","SAN=dns:localhost","-validity","2","-noprompt")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(30,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Tiempo de generación TLS agotado."); }
            if (process.exitValue()!=0) throw new IllegalStateException("No se generó el certificado TLS aislado.");
            System.setProperty("m2.test.tls.directory", directory.toString().replace('\\','/')+"/");
            return directory;
        } catch (Exception error) { throw new IllegalStateException("No se preparó TLS de pruebas.",error); }
    }

    @DynamicPropertySource static void settings(DynamicPropertyRegistry p) {
        String url=System.getenv("M2_ROLE_TEST_JDBC_URL");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/m2_role_test")) throw new IllegalArgumentException("Solo base aislada de pruebas.");
        p.add("spring.datasource.url",()->url);
        p.add("spring.datasource.username",()->"m2_app");
        p.add("spring.datasource.password",()->System.getenv("M2_ROLE_TEST_DB_PASSWORD"));
        p.add("server.ssl.key-store",()->DIRECTORY.resolve("server.p12").toUri().toString());
        p.add("m2.worker.user-id",()->"");
        p.add("m2.csv.directory",()->DIRECTORY.resolve("csv").toString());
    }

    @Test void httpsValidatesCertificateAndSetsSecureCookiesAndHsts() throws Exception {
        var store=KeyStore.getInstance("PKCS12");
        try(var stream=Files.newInputStream(DIRECTORY.resolve("server.p12"))) { store.load(stream,PASSWORD.toCharArray()); }
        var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(store);
        var ssl=SSLContext.getInstance("TLS"); ssl.init(null,trust.getTrustManagers(),null);
        var client=HttpClient.newBuilder().sslContext(ssl).connectTimeout(Duration.ofSeconds(5)).build();
        var request=HttpRequest.newBuilder(URI.create("https://localhost:"+port+"/api/v1/session/csrf")).timeout(Duration.ofSeconds(10)).build();
        var reply=client.send(request,HttpResponse.BodyHandlers.ofString());
        assertEquals(200,reply.statusCode());
        assertTrue(reply.headers().firstValue("Strict-Transport-Security").orElse("").contains("max-age="));
        String cookie=reply.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.startsWith("__Host-M2SESSION="));
        assertTrue(cookie.contains("Secure")); assertTrue(cookie.contains("HttpOnly"));
        assertTrue(Set.of("TLSv1.2","TLSv1.3").contains(reply.sslSession().orElseThrow().getProtocol()));
        assertThrows(SSLHandshakeException.class,()->HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()),"No se omite la validación de confianza del certificado");
        var plain=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/session/csrf")).timeout(Duration.ofSeconds(5)).header("X-Forwarded-Proto","https").build();
        var rejected=HttpClient.newHttpClient().send(plain,HttpResponse.BodyHandlers.ofString());
        assertNotEquals(200,rejected.statusCode(),"Una cabecera de proxy no convierte HTTP en TLS");
    }

    @AfterAll static void cleanup() throws Exception {
        System.clearProperty("m2.test.tls.directory");
        try(var files=Files.walk(DIRECTORY)) {
            for(var file:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
        }
    }
}
