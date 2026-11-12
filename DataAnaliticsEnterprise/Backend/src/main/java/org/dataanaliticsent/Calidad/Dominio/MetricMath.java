package org.dataanaliticsent.Calidad.Dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class MetricMath {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private MetricMath() {
    }

    public enum ComplianceState {
        COMPLIANT,
        NON_COMPLIANT,
        NOT_EVALUABLE,
        NOT_APPLICABLE,
        PARTIAL_COVERAGE
    }

    public record MetricEvaluation(
            long numerator,
            long denominator,
            long applicableUnits,
            BigDecimal displayPercentage,
            BigDecimal coveragePercentage,
            ComplianceState complianceState
    ) {
    }

    public static BigDecimal computePercentageOrNull(long numerator, long denominator) {
        if (numerator < 0 || denominator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("Conteos inválidos: numerador=" + numerator + ", denominador=" + denominator);
        }
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    public static boolean meetsThresholdExact(long numerator, long denominator, BigDecimal thresholdPercentage) {
        Objects.requireNonNull(thresholdPercentage, "thresholdPercentage no puede ser nulo");
        if (denominator <= 0 || numerator < 0 || numerator > denominator) {
            return false;
        }
        BigDecimal leftSide = BigDecimal.valueOf(numerator).multiply(ONE_HUNDRED);
        BigDecimal rightSide = thresholdPercentage.multiply(BigDecimal.valueOf(denominator));
        return leftSide.compareTo(rightSide) >= 0;
    }

    public static MetricEvaluation evaluate(
            long numerator,
            long evaluatedUnits,
            long applicableUnits,
            BigDecimal thresholdPercentage,
            BigDecimal requiredCoveragePercentage
    ) {
        if (applicableUnits == 0) {
            return new MetricEvaluation(0, 0, 0, null, null, ComplianceState.NOT_APPLICABLE);
        }
        if (evaluatedUnits == 0) {
            return new MetricEvaluation(0, 0, applicableUnits, null, BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY), ComplianceState.NOT_EVALUABLE);
        }

        BigDecimal displayValue = computePercentageOrNull(numerator, evaluatedUnits);
        BigDecimal coverageValue = computePercentageOrNull(evaluatedUnits, applicableUnits);

        BigDecimal minCoverage = requiredCoveragePercentage != null ? requiredCoveragePercentage : ONE_HUNDRED;
        boolean coverageMet = meetsThresholdExact(evaluatedUnits, applicableUnits, minCoverage);

        if (!coverageMet) {
            return new MetricEvaluation(numerator, evaluatedUnits, applicableUnits, displayValue, coverageValue, ComplianceState.PARTIAL_COVERAGE);
        }

        if (thresholdPercentage == null) {
            return new MetricEvaluation(numerator, evaluatedUnits, applicableUnits, displayValue, coverageValue, ComplianceState.NOT_EVALUABLE);
        }

        ComplianceState state = meetsThresholdExact(numerator, evaluatedUnits, thresholdPercentage)
                ? ComplianceState.COMPLIANT
                : ComplianceState.NON_COMPLIANT;

        return new MetricEvaluation(numerator, evaluatedUnits, applicableUnits, displayValue, coverageValue, state);
    }
}
