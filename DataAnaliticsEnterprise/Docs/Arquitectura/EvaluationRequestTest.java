import org.junit.jupiter.api.Test;
import org.dataanaliticsent.Calidad.Aplicacion.QualityContracts.CreateEvaluation;
import org.dataanaliticsent.Calidad.Aplicacion.EvaluationService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationRequestTest {
    @Test void reviewedVersionsArePartOfIdempotencyAndRemainOrderIndependent() {
        UUID asset=UUID.randomUUID(),metadata=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID(),av=UUID.randomUUID(),bv=UUID.randomUUID();
        var first=new CreateEvaluation(asset,metadata,List.of(a,b),null,Map.of(a,av,b,bv));
        var reordered=new CreateEvaluation(asset,metadata,List.of(b,a),null,Map.of(b,bv,a,av));
        var changed=new CreateEvaluation(asset,metadata,List.of(a,b),null,Map.of(a,UUID.randomUUID(),b,bv));
        assertEquals(EvaluationService.fingerprint(first),EvaluationService.fingerprint(reordered));
        assertNotEquals(EvaluationService.fingerprint(first),EvaluationService.fingerprint(changed));
        assertEquals(EvaluationService.fingerprint(new CreateEvaluation(asset,metadata,List.of(a,b),null)),EvaluationService.fingerprint(new CreateEvaluation(asset,metadata,List.of(a,b),null,null)));
    }
}
