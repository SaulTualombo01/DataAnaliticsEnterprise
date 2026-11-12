import org.junit.jupiter.api.Test;
import org.dataanaliticsent.Calidad.Dominio.EvaluationDates;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationDatesTest {
    @Test void inclusiveEcuadorDayBecomesExclusiveUtcRange(){var range=EvaluationDates.range("2026-10-29","2026-10-29");assertEquals(Instant.parse("2026-10-29T05:00:00Z"),range.from());assertEquals(Instant.parse("2026-10-30T05:00:00Z"),range.until());}
    @Test void optionalLimitsRemainOpen(){assertNull(EvaluationDates.range(null,null).from());assertNull(EvaluationDates.range(null,null).until());assertEquals(Instant.parse("2026-03-01T05:00:00Z"),EvaluationDates.range(null,"2026-02-28").until());}
    @Test void invalidAndReversedDatesAreRejected(){assertThrows(IllegalArgumentException.class,() -> EvaluationDates.range("2026-10-30","2026-10-29"));assertThrows(IllegalArgumentException.class,() -> EvaluationDates.range("2026-02-30",null));assertThrows(IllegalArgumentException.class,() -> EvaluationDates.range("",null));}
    @Test void datesOutsideTheFourDigitCalendarAreRejected(){assertThrows(IllegalArgumentException.class,() -> EvaluationDates.range("+123456-01-01",null));assertThrows(IllegalArgumentException.class,() -> EvaluationDates.range(null,"0000-01-01"));}
}
