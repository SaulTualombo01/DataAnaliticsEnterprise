package org.dataanaliticsent.Calidad.Dominio;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Motor determinista: no modifica filas, no interpreta SQL/scripts ni obtiene datos por red.
 */
public final class QualityEngine {
    public enum Dimension {COMPLETENESS, ACCURACY, CONSISTENCY, UNIQUENESS, VALIDITY, TIMELINESS}

    public enum Kind {REQUIRED, UNIQUE_KEY, EMAIL, NUMBER_RANGE, ALLOWED_VALUES, FIELD_EQUALS, REFERENCE_EQUALS, FOREIGN_KEY, WITHIN_AGE}

    public record Rule(UUID versionId, Dimension dimension, Kind kind, List<String> fields,
                       BigDecimal threshold, BigDecimal minimumCoverage, boolean critical,
                       boolean blankIsMissing, BigDecimal minimum, BigDecimal maximum,
                       Set<String> allowedValues, String referenceId, String referenceVersion,
                       String referenceKey, String referenceField, BigDecimal tolerance, Long maxAgeSeconds) {
        public Rule {
            Objects.requireNonNull(versionId);
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(kind);
            if (fields == null || fields.isEmpty() || fields.size() > 20 || new HashSet<>(fields).size() != fields.size()
                    || fields.stream().anyMatch(f -> f == null || f.isBlank() || f.length() > 128 || f.codePoints().anyMatch(Character::isISOControl)))
                throw new IllegalArgumentException("Selecciona campos únicos y válidos.");
            fields = List.copyOf(fields);
            allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
            checkPercentage(threshold);
            checkPercentage(minimumCoverage);
            if (minimumCoverage == null) minimumCoverage = BigDecimal.valueOf(100);
            if (tolerance == null) tolerance = BigDecimal.ZERO;
            if (tolerance.signum() < 0) throw new IllegalArgumentException("La tolerancia no puede ser negativa.");
            Dimension expected = switch (kind) {
                case REQUIRED -> Dimension.COMPLETENESS;
                case UNIQUE_KEY -> Dimension.UNIQUENESS;
                case EMAIL, NUMBER_RANGE, ALLOWED_VALUES -> Dimension.VALIDITY;
                case FIELD_EQUALS, FOREIGN_KEY -> Dimension.CONSISTENCY;
                case REFERENCE_EQUALS -> Dimension.ACCURACY;
                case WITHIN_AGE -> Dimension.TIMELINESS;
            };
            if (dimension != expected)
                throw new IllegalArgumentException("Tipo de regla incompatible con la dimensión.");
            if (kind == Kind.FIELD_EQUALS && fields.size() != 2)
                throw new IllegalArgumentException("La comparación requiere dos campos.");
            if (kind != Kind.REQUIRED && kind != Kind.UNIQUE_KEY && kind != Kind.FIELD_EQUALS && fields.size() != 1)
                throw new IllegalArgumentException("Este tipo requiere un campo.");
            if (kind == Kind.NUMBER_RANGE && (minimum == null || maximum == null || minimum.compareTo(maximum) > 0))
                throw new IllegalArgumentException("Define un rango numérico válido.");
            if (kind == Kind.ALLOWED_VALUES && (allowedValues.isEmpty() || allowedValues.size() > 200))
                throw new IllegalArgumentException("Define entre 1 y 200 valores permitidos.");
            if (kind == Kind.WITHIN_AGE && (maxAgeSeconds == null || maxAgeSeconds < 1 || maxAgeSeconds > 315360000L))
                throw new IllegalArgumentException("Define una vigencia entre 1 segundo y 10 años.");
            if ((kind == Kind.FOREIGN_KEY || kind == Kind.REFERENCE_EQUALS)
                    && (referenceId == null || referenceVersion == null || referenceKey == null || referenceField == null))
                throw new IllegalArgumentException("La regla requiere una referencia identificada y versionada.");
        }
    }

    public record Reference(String version, String keyField, String valueField, Map<String, Object> values) {
        public Reference(String version, Map<String, Object> values) {
            this(version, "id", "value", values);
        }

        public Reference {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    public record Input(List<Map<String, Object>> rows, Set<String> fields, boolean complete,
                        Map<String, Reference> references, List<Long> sourceRowNumbers) {
        public Input(List<Map<String, Object>> rows, Set<String> fields, boolean complete, Map<String, Reference> references) {
            this(rows, fields, complete, references, java.util.stream.LongStream.rangeClosed(1, rows.size()).boxed().toList());
        }

        public Input {
            rows = rows.stream().map(r -> Collections.unmodifiableMap(new LinkedHashMap<>(r))).toList();
            fields = Set.copyOf(fields);
            references = Map.copyOf(references);
            sourceRowNumbers = List.copyOf(sourceRowNumbers);
            if (sourceRowNumbers.size() != rows.size() || sourceRowNumbers.stream().anyMatch(n -> n < 1))
                throw new IllegalArgumentException("Referencias de fila no válidas.");
        }
    }

    public record Finding(long rowNumber, String field, String code) {
    }

    public record Result(UUID ruleVersionId, long candidateUnits, long applicableUnits, long numerator,
                         long denominator, String measurementState, BigDecimal value, BigDecimal coverage,
                         String complianceState, List<Finding> findings) {
    }

    public record Report(List<Result> results, String qualityOutcome, Instant measuredAt) {
    }

    private record Context(Rule rule, Input input, Instant at) {
    }

    private interface Strategy extends BiFunction<Context, Long, Boolean> {
    }

    private final Map<Kind, Strategy> strategies = Map.of(
            Kind.EMAIL, (c, i) -> {
                Object v = value(c, i);
                return v instanceof String s && s.length() <= 254 && s.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+");
            },
            Kind.NUMBER_RANGE, (c, i) -> {
                BigDecimal n = number(value(c, i));
                return n != null && n.compareTo(c.rule.minimum) >= 0 && n.compareTo(c.rule.maximum) <= 0;
            },
            Kind.ALLOWED_VALUES, (c, i) -> {
                Object v = value(c, i);
                return v instanceof String s && c.rule.allowedValues.contains(s);
            },
            Kind.FIELD_EQUALS, (c, i) -> {
                var row = c.input.rows.get(i.intValue());
                Object a = row.get(c.rule.fields.get(0)), b = row.get(c.rule.fields.get(1));
                return missing(a, true) || missing(b, true) ? null : equivalent(a, b, BigDecimal.ZERO);
            },
            Kind.REFERENCE_EQUALS, (c, i) -> reference(c, i, false),
            Kind.FOREIGN_KEY, (c, i) -> reference(c, i, true),
            Kind.WITHIN_AGE, (c, i) -> {
                try {
                    Instant date = Instant.parse(String.valueOf(value(c, i)));
                    long age = Duration.between(date, c.at).getSeconds();
                    return !date.isAfter(c.at) && age <= c.rule.maxAgeSeconds;
                } catch (RuntimeException ex) {
                    return null;
                }
            }
    );

    public Report evaluate(Input input, List<Rule> rules, Instant measuredAt, Duration timeout) {
        return evaluate(input, rules, measuredAt, timeout, 500000, 20000);
    }

    public static void validateWorkload(Input input, List<Rule> rules, long maxWorkUnits) {
        long units = 0;
        for (Rule rule : rules) {
            units = Math.addExact(units, Math.multiplyExact((long) input.rows.size(), rule.fields.size()));
            if (units > maxWorkUnits) throw new IllegalStateException("EVALUATION_WORK_LIMIT");
        }
    }

    public Report evaluate(Input input, List<Rule> rules, Instant measuredAt, Duration timeout, long maxWorkUnits, int maxFindings) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(measuredAt);
        if (!input.complete) throw new IllegalArgumentException("INPUT_PARTIAL");
        if (rules.isEmpty() || rules.size() > 20 || rules.stream().map(Rule::versionId).distinct().count() != rules.size())
            throw new IllegalArgumentException("Selecciona entre 1 y 20 versiones únicas.");
        if (timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Tiempo límite inválido.");
        if (maxWorkUnits < 1 || maxFindings < 1) throw new IllegalArgumentException("Presupuesto de evaluación inválido.");
        validateWorkload(input, rules, maxWorkUnits);
        int totalFindings = 0;
        long started = System.nanoTime();
        List<Result> results = new ArrayList<>();
        for (Rule rule : rules) {
            if (!input.fields.containsAll(rule.fields) || (rule.referenceKey != null && !input.fields.contains(rule.referenceKey)))
                throw new IllegalArgumentException("INPUT_CONTRACT_MISMATCH");
            long candidate = Math.multiplyExact(input.rows.size(), rule.kind == Kind.REQUIRED ? rule.fields.size() : 1);
            long evaluated = 0, passed = 0;
            List<Finding> findings = new ArrayList<>();
            Set<List<Object>> keys = new HashSet<>();
            Context context = new Context(rule, input, measuredAt);
            for (int i = 0; i < input.rows.size(); i++) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() - started > timeout.toNanos())
                    throw new IllegalStateException("EVALUATION_TIMEOUT");
                var row = input.rows.get(i);
                if (rule.kind == Kind.REQUIRED) {
                    for (String field : rule.fields) {
                        evaluated++;
                        if (!missing(row.get(field), rule.blankIsMissing)) passed++;
                        else {
                            if (++totalFindings > maxFindings) throw new IllegalStateException("EVALUATION_FINDING_LIMIT");
                            findings.add(new Finding(input.sourceRowNumbers.get(i), field, "MISSING_VALUE"));
                        }
                    }
                } else {
                    Boolean ok;
                    if (rule.kind == Kind.UNIQUE_KEY) {
                        List<Object> key = rule.fields.stream().map(row::get).map(QualityEngine::canonical).toList();
                        ok = key.stream().anyMatch(v -> missing(v, true)) ? null : keys.add(key);
                    } else ok = strategies.get(rule.kind).apply(context, (long) i);
                    if (ok != null) {
                        evaluated++;
                        if (ok) passed++;
                    }
                    if (!Boolean.TRUE.equals(ok)) {
                        if (++totalFindings > maxFindings) throw new IllegalStateException("EVALUATION_FINDING_LIMIT");
                        findings.add(new Finding(input.sourceRowNumbers.get(i), String.join(",", rule.fields),
                                ok == null ? "NOT_EVALUABLE" : rule.kind == Kind.UNIQUE_KEY ? "DUPLICATE_KEY" : "RULE_VIOLATION"));
                    }
                }
            }
            BigDecimal metric = MetricMath.computePercentageOrNull(passed, evaluated);
            BigDecimal coverage = MetricMath.computePercentageOrNull(evaluated, candidate);
            String state = evaluated == 0 ? "NOT_EVALUABLE" : "MEASURED";
            String compliance = candidate == 0 || evaluated == 0 || rule.threshold == null ? "NOT_EVALUABLE"
                    : !MetricMath.meetsThresholdExact(evaluated, candidate, rule.minimumCoverage) ? "PARTIAL_COVERAGE"
                    : MetricMath.meetsThresholdExact(passed, evaluated, rule.threshold) ? "PASS" : "FAIL";
            results.add(new Result(rule.versionId, candidate, candidate, passed, evaluated, state, metric, coverage, compliance, List.copyOf(findings)));
        }
        String outcome = input.rows.isEmpty() ? "NO_DATA" : results.stream().allMatch(r -> r.complianceState.equals("PASS")) ? "PASS"
                : results.stream().anyMatch(r -> r.complianceState.equals("FAIL")) ? "FAIL" : "INCOMPLETE";
        // El piloto requiere cobertura completa incluso si una regla propone un mínimo menor.
        if (outcome.equals("PASS") && results.stream().anyMatch(r -> r.denominator != r.applicableUnits))
            outcome = "INCOMPLETE";
        return new Report(List.copyOf(results), outcome, measuredAt);
    }

    private static Object value(Context c, Long i) {
        return c.input.rows.get(i.intValue()).get(c.rule.fields.get(0));
    }

    private static Boolean reference(Context c, Long i, boolean foreignKey) {
        Reference reference = c.input.references.get(c.rule.referenceId);
        if (reference == null || !Objects.equals(reference.version, c.rule.referenceVersion)
                || !Objects.equals(c.rule.referenceField, foreignKey ? reference.keyField : reference.valueField))
            return null;
        Object key = c.input.rows.get(i.intValue()).get(c.rule.referenceKey);
        if (missing(key, true)) return null;
        if (foreignKey) return reference.values.containsKey(String.valueOf(key));
        Object expected = reference.values.get(String.valueOf(key));
        Object actual = value(c, i);
        return expected == null || missing(actual, true) ? null : equivalent(actual, expected, c.rule.tolerance);
    }

    private static Object canonical(Object value) {
        return value instanceof Number ? number(value).stripTrailingZeros() : value;
    }

    private static boolean equivalent(Object a, Object b, BigDecimal tolerance) {
        if (a instanceof Number && b instanceof Number)
            return number(a).subtract(number(b)).abs().compareTo(tolerance) <= 0;
        return Objects.equals(a, b);
    }

    private static BigDecimal number(Object value) {
        if (!(value instanceof Number)) return null;
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean missing(Object value, boolean blanks) {
        return value == null || blanks && value instanceof String s && s.isBlank();
    }

    private static void checkPercentage(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0))
            throw new IllegalArgumentException("El porcentaje debe estar entre 0 y 100.");
    }
}

