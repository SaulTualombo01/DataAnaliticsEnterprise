import org.junit.jupiter.api.Test;
import org.dataanaliticsent.Calidad.Infraestructura.Configuracion.IntegrationConfiguration;
import org.dataanaliticsent.Calidad.Presentacion.Errores.ApiException;
import org.springframework.http.HttpStatus;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class UploadsOnlyTest {
    @Test void everyNormalProfileUsesUploadedFilesWithoutSyntheticSeeding() {
        for(String resource:java.util.List.of("application.yml","application-local.yml","application-docker.yml")){
            var yaml=new org.springframework.beans.factory.config.YamlPropertiesFactoryBean();
            yaml.setResources(new org.springframework.core.io.ClassPathResource(resource));
            var properties=yaml.getObject();assertNotNull(properties);
            assertEquals("uploads",properties.getProperty("m2.integrations.mode"),resource);
            assertNotEquals("true",properties.getProperty("m2.bootstrap.fixture-rules"),resource);
        }
    }
    @Test void externalCatalogIsEmptyWhenOnlyUploadsAreEnabled() {
        var provider=new IntegrationConfiguration().unavailableInputProvider("uploads");
        assertTrue(provider.assets(UUID.randomUUID(),UUID.randomUUID()).isEmpty());
    }
    @Test void unknownAndFormerSyntheticAssetsCannotBeAcquiredOrRead() {
        var provider=new IntegrationConfiguration().unavailableInputProvider("uploads");
        var actor=UUID.randomUUID();var project=UUID.randomUUID();
        var oldSynthetic=UUID.fromString("f7c83e25-073f-4ddb-b72b-e4669d242d37");var metadata=UUID.randomUUID();
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ApiException.class,() -> provider.acquire(actor,project,oldSynthetic,metadata,null)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ApiException.class,() -> provider.read(actor,project,oldSynthetic,metadata,UUID.randomUUID(),null)).getStatus());
    }
    @Test void anUnconfiguredExternalIntegrationStillReportsUnavailable() {
        var provider=new IntegrationConfiguration().unavailableInputProvider("external");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,assertThrows(ApiException.class,() -> provider.assets(UUID.randomUUID(),UUID.randomUUID())).getStatus());
    }
}
