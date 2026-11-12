import org.junit.jupiter.api.Test;
import org.dataanaliticsent.Calidad.Infraestructura.PersistenceModels;
import org.dataanaliticsent.Calidad.Infraestructura.Seguridad.SensitiveDataCipher;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import jakarta.persistence.Entity;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CipherAndMappingTest {
    private SensitiveDataCipher cipher(){byte[] encryption=new byte[32],lookup=new byte[32];Arrays.fill(encryption,(byte)1);Arrays.fill(lookup,(byte)2);return new SensitiveDataCipher("test",Map.of("test",encryption),lookup);}
    @Test void contextBoundCipherCannotBeMovedToAnotherProject(){var c=cipher();var v=c.encrypt("evidencia","incident","project-a","row-1");assertEquals("evidencia",c.decrypt(v,"incident","project-a","row-1"));assertThrows(SecurityException.class,() -> c.decrypt(v,"incident","project-b","row-1"));}
    @Test void ciphertextIsRandomizedAndEmailLookupNormalized(){var c=cipher();assertNotEquals(c.encrypt("email","email",null,"id"),c.encrypt("email","email",null,"id"));assertEquals(c.computeEmailLookupHmac(" Example@Example.Test "),c.computeEmailLookupHmac("example@example.test"));}
    @Test void fourteenEntitiesBuildHibernateMetadata(){var registry=new StandardServiceRegistryBuilder().applySetting("hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect").applySetting("hibernate.boot.allow_jdbc_metadata_access",false).build();try{var sources=new MetadataSources(registry);int count=0;for(Class<?> type:PersistenceModels.class.getDeclaredClasses())if(type.isAnnotationPresent(Entity.class)){sources.addAnnotatedClass(type);count++;}assertEquals(14,count);assertEquals(14,sources.buildMetadata().getEntityBindings().size());}finally{StandardServiceRegistryBuilder.destroy(registry);}}
}
