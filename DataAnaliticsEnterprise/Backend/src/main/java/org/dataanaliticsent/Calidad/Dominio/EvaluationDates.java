package org.dataanaliticsent.Calidad.Dominio;

import java.time.*;

/** Días de la interfaz en Ecuador, con límite superior exclusivo para no perder registros. */
public final class EvaluationDates {
    private EvaluationDates() {}
    public record Range(Instant from,Instant until) {}
    public static Range range(String from,String to) {
        try {
            for(String value:new String[]{from,to})if(value!=null&&(!value.matches("\\d{4}-\\d{2}-\\d{2}")||value.startsWith("0000")))throw new IllegalArgumentException("Utiliza fechas válidas con formato AAAA-MM-DD.");
            LocalDate first=from==null?null:LocalDate.parse(from),last=to==null?null:LocalDate.parse(to);
            if(first!=null&&last!=null&&first.isAfter(last))throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final.");
            var zone=ZoneId.of("America/Guayaquil");
            return new Range(first==null?null:first.atStartOfDay(zone).toInstant(),last==null?null:last.plusDays(1).atStartOfDay(zone).toInstant());
        }catch(DateTimeException ex){throw new IllegalArgumentException("Utiliza fechas válidas con formato AAAA-MM-DD.");}
    }
}
