import org.junit.jupiter.api.Test;
import org.dataanaliticsent.Calidad.Dominio.*;
import org.dataanaliticsent.Calidad.Infraestructura.SyntheticInputProvider;
import org.dataanaliticsent.Calidad.Aplicacion.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.dataanaliticsent.Calidad.Dominio.QualityEngine.*;

class QualityEngineTest {
    private final QualityEngine engine=new QualityEngine();
    private final Instant now=Instant.parse("2026-10-28T16:00:00Z");
    private final UUID id=UUID.randomUUID();
    private Rule rule(Dimension dimension,Kind kind,List<String> fields){return new Rule(id,dimension,kind,fields,new BigDecimal("98"),new BigDecimal("100"),false,true,new BigDecimal("0"),new BigDecimal("100"),Set.of("activo"),"reference","1","id",kind==Kind.FOREIGN_KEY?"id":"value",BigDecimal.ZERO,86400L);}
    private Report run(Input input,Rule rule){return engine.evaluate(input,List.of(rule),now,Duration.ofSeconds(5));}
    private Input input(List<Map<String,Object>> rows,Set<String> fields){return new Input(rows,fields,true,Map.of());}
    @Test void completenessCountsNullsWithoutCleaning(){
        var fixture=new SyntheticInputProvider(new ObjectMapper()).acquire(id,id,SyntheticInputProvider.CUSTOMERS,SyntheticInputProvider.METADATA,null);
        Rule rule=new Rule(id,Dimension.COMPLETENESS,Kind.REQUIRED,List.of("correo"),new BigDecimal("98"),new BigDecimal("100"),false,true,null,null,null,null,null,null,null,null,null);
        Result r=run(fixture.input(),rule).results().getFirst();assertEquals(95,r.numerator());assertEquals(100,r.denominator());assertEquals(new BigDecimal("95.00"),r.value());assertEquals(new BigDecimal("100.00"),r.coverage());assertEquals(5,r.findings().size());assertEquals("FAIL",r.complianceState());assertNull(fixture.input().rows().get(99).get("correo"));
    }
    @Test void emptyIsNotApproval(){var r=run(input(List.of(),Set.of("x")),new Rule(id,Dimension.COMPLETENESS,Kind.REQUIRED,List.of("x"),BigDecimal.ZERO,BigDecimal.valueOf(100),false,true,null,null,null,null,null,null,null,null,null));assertEquals("NO_DATA",r.qualityOutcome());assertNull(r.results().getFirst().value());assertEquals("NOT_EVALUABLE",r.results().getFirst().measurementState());}
    @Test void partialInputCannotProduceResults(){assertThrows(IllegalArgumentException.class,() -> run(new Input(List.of(),Set.of("id","x"),false,Map.of()),rule(Dimension.VALIDITY,Kind.EMAIL,List.of("x"))));}
    @Test void duplicateFormulaCountsAdditionalCopies(){var r=run(input(List.of(Map.of("id","a"),Map.of("id","a"),Map.of("id","b")),Set.of("id")),rule(Dimension.UNIQUENESS,Kind.UNIQUE_KEY,List.of("id"))).results().getFirst();assertEquals(2,r.numerator());assertEquals(3,r.denominator());assertEquals("DUPLICATE_KEY",r.findings().getFirst().code());}
    @Test void missingKeyLowersCoverage(){Map<String,Object> missing=new HashMap<>();missing.put("id",null);var r=run(input(List.of(Map.of("id","a"),missing),Set.of("id")),rule(Dimension.UNIQUENESS,Kind.UNIQUE_KEY,List.of("id"))).results().getFirst();assertEquals(1,r.denominator());assertEquals(new BigDecimal("50.00"),r.coverage());assertEquals("PARTIAL_COVERAGE",r.complianceState());}
    @Test void numericRepresentationsShareUniqueKey(){var r=run(input(List.of(Map.of("id",1),Map.of("id",new BigDecimal("1.00"))),Set.of("id")),rule(Dimension.UNIQUENESS,Kind.UNIQUE_KEY,List.of("id"))).results().getFirst();assertEquals(1,r.numerator());}
    @Test void accuracyNeedsMatchingReferenceVersion(){var r=run(input(List.of(Map.of("id","a","x","yes")),Set.of("id","x")),rule(Dimension.ACCURACY,Kind.REFERENCE_EQUALS,List.of("x"))).results().getFirst();assertNull(r.value());assertEquals("NOT_EVALUABLE",r.complianceState());}
    @Test void accuracyUsesEvidenceRatherThanEmailSyntax(){var data=new Input(List.of(Map.of("id","a","x","different@example.test")),Set.of("id","x"),true,Map.of("reference",new Reference("1",Map.of("a","trusted@example.test"))));var r=run(data,rule(Dimension.ACCURACY,Kind.REFERENCE_EQUALS,List.of("x"))).results().getFirst();assertEquals(0,r.numerator());assertEquals(1,r.denominator());}
    @Test void foreignKeysFindOrphans(){var data=new Input(List.of(Map.of("id","known","x","known"),Map.of("id","orphan","x","orphan")),Set.of("id","x"),true,Map.of("reference",new Reference("1",Map.of("known","value"))));assertEquals(1,run(data,rule(Dimension.CONSISTENCY,Kind.FOREIGN_KEY,List.of("x"))).results().getFirst().numerator());}
    @Test void invalidTimestampIsNotEvaluable(){var r=run(input(List.of(Map.of("id",1,"x","not-a-date")),Set.of("id","x")),rule(Dimension.TIMELINESS,Kind.WITHIN_AGE,List.of("x"))).results().getFirst();assertNull(r.value());assertEquals(0,r.denominator());}
    @Test void futureDateIsNotCurrent(){var r=run(input(List.of(Map.of("id",1,"x","2026-10-28T16:00:00.001Z")),Set.of("id","x")),rule(Dimension.TIMELINESS,Kind.WITHIN_AGE,List.of("x"))).results().getFirst();assertEquals(0,r.numerator());}
    @Test void wrongNumericBusinessTypeIsViolationNotContractFailure(){var r=run(input(List.of(Map.of("id",1,"x","25")),Set.of("id","x")),rule(Dimension.VALIDITY,Kind.NUMBER_RANGE,List.of("x"))).results().getFirst();assertEquals(1,r.denominator());assertEquals(0,r.numerator());}
    @Test void missingMetadataColumnIsContractFailure(){assertThrows(IllegalArgumentException.class,() -> run(input(List.of(Map.of("x","a")),Set.of("x")),rule(Dimension.VALIDITY,Kind.EMAIL,List.of("missing"))));}
    @Test void exactThresholdDoesNotApproveRoundedValue(){assertEquals(new BigDecimal("98.00"),MetricMath.computePercentageOrNull(97996,100000));assertFalse(MetricMath.meetsThresholdExact(97996,100000,new BigDecimal("98")));}
    @Test void sameInputVersionsAndInstantAreDeterministic(){var data=input(List.of(Map.of("id",1,"x","a@b.test")),Set.of("id","x"));var rule=rule(Dimension.VALIDITY,Kind.EMAIL,List.of("x"));assertEquals(run(data,rule),run(data,rule));}
    @Test void technicalAdminHasNoQualityPrivilege(){assertFalse(AccessService.permits("SYSTEM_ADMIN",null,id,AccessService.Action.READ_RESULTS));assertFalse(AccessService.permits("BUSINESS_ANALYST",null,id,AccessService.Action.MANAGE_QUALITY));assertTrue(AccessService.permits("BUSINESS_ANALYST",null,id,AccessService.Action.READ_RESULTS));}
    @Test void assetScopeIsRequired(){assertFalse(AccessService.permits("QUALITY_MANAGER",id,UUID.randomUUID(),AccessService.Action.MANAGE_QUALITY));assertTrue(AccessService.permits("QUALITY_MANAGER",id,id,AccessService.Action.MANAGE_QUALITY));}
    @Test void idempotencyFingerprintIgnoresRuleOrderButNotSnapshot(){UUID second=UUID.randomUUID();var a=new QualityContracts.CreateEvaluation(id,id,List.of(id,second),null);var b=new QualityContracts.CreateEvaluation(id,id,List.of(second,id),null);var c=new QualityContracts.CreateEvaluation(id,id,List.of(second,id),id);assertEquals(EvaluationService.fingerprint(a),EvaluationService.fingerprint(b));assertNotEquals(EvaluationService.fingerprint(a),EvaluationService.fingerprint(c));}
    @Test void incidentWorkflowPreservesReview(){assertFalse(Workflow.incidentTransition("OPEN","RESOLVED"));assertTrue(Workflow.incidentTransition("OPEN","IN_PROGRESS"));assertTrue(Workflow.incidentTransition("IN_PROGRESS","RESOLVED"));assertFalse(Workflow.incidentTransition("RESOLVED","RESOLVED"));}
}
