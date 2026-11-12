import org.apache.catalina.util.ServerInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TomcatPatchTest {
    @Test void effectiveTomcatDependencyMatchesTheReviewedPatchedVersion() {
        assertEquals("Apache Tomcat/10.1.61",ServerInfo.getServerInfo());
    }
}
