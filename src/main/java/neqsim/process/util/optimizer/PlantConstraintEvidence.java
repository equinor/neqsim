package neqsim.process.util.optimizer;

import java.io.Serializable;
import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Immutable assessed evidence row pairing one registry definition with one calculation sample.
 *
 * <p>
 * Coverage status is intentionally separate from operating status. Missing or invalid evidence is never converted into
 * zero utilization, and an enabled constraint with incomplete coverage fails closed independently of its nominal
 * severity.
 * </p>
 */
public final class PlantConstraintEvidence implements Serializable, Comparable<PlantConstraintEvidence> {
  private static final long serialVersionUID = 1L;
  private static final double CONSISTENCY_TOLERANCE = 1.0e-10;

  /** Completeness and trustworthiness of the post-solve evidence row. */
  public enum CoverageStatus {
    /** Finite, consistent evidence for the exact calculation is available. */
    AVAILABLE,
    /** The registered restriction is disabled and therefore requires no runtime sample. */
    DISABLED,
    /** An enabled registration lacks unit, basis, or provenance. */
    INCOMPLETE_REGISTRATION,
    /** No runtime sample was supplied for an enabled registration. */
    MISSING_SAMPLE,
    /** The sampler reported a missing value. */
    MISSING_VALUE,
    /** The sampler reported that the restriction could not be calculated. */
    NOT_CALCULABLE,
    /** Required numerical evidence is non-finite. */
    NON_FINITE_VALUE,
    /** The sampled point is outside the evidence validity envelope. */
    OUTSIDE_VALIDITY,
    /** The process calculation did not complete convergence. */
    INCOMPLETE_CONVERGENCE,
    /** The evidence belongs to an older process state. */
    STALE,
    /** The evidence calculation identity differs from the snapshot identity. */
    CALCULATION_ID_MISMATCH,
    /** Sample identity, unit, or basis differs from the registration. */
    METADATA_MISMATCH,
    /** Sampling raised an exception. */
    EXCEPTION,
    /** Finite values have internally inconsistent signs or normalized quantities. */
    INVALID_EVIDENCE
  }

  /** Engineering interpretation of an assessed row. */
  public enum OperatingStatus {
    /** The registered restriction is disabled. */
    DISABLED,
    /** Runtime evidence is unavailable. */
    UNAVAILABLE,
    /** Runtime evidence exists but is invalid or inapplicable. */
    INVALID,
    /** The operating point has ordinary headroom. */
    WITHIN_LIMIT,
    /** The operating point is at or above the configured near-limit threshold. */
    NEAR_LIMIT,
    /** The operating point is numerically on the limit. */
    ACTIVE_LIMIT,
    /** The registered limit is violated. */
    VIOLATED
  }

  private final PlantConstraintDefinition definition;
  private final PlantConstraintSample sample;
  private final CoverageStatus coverageStatus;
  private final OperatingStatus operatingStatus;
  private final boolean hardConstraint;
  private final boolean feasible;
  private final String diagnostic;

  PlantConstraintEvidence(PlantConstraintDefinition definition, PlantConstraintSample sample, String calculationId,
      double nearLimitThreshold, boolean convergenceComplete) {
    if (definition == null) {
      throw new IllegalArgumentException("Plant constraint definition is required");
    }
    this.definition = definition;
    this.sample = sample;
    hardConstraint = definition.getSeverity() == CapacityConstraint.ConstraintSeverity.CRITICAL
        || definition.getSeverity() == CapacityConstraint.ConstraintSeverity.HARD;
    coverageStatus = assessCoverage(definition, sample, calculationId, convergenceComplete);
    operatingStatus = assessOperatingStatus(coverageStatus, sample, nearLimitThreshold);
    feasible = assessFeasible(coverageStatus, operatingStatus, hardConstraint);
    diagnostic = buildDiagnostic(coverageStatus, sample);
  }

  private static CoverageStatus assessCoverage(PlantConstraintDefinition definition, PlantConstraintSample sample,
      String calculationId, boolean convergenceComplete) {
    if (!definition.isEnabled()) {
      return CoverageStatus.DISABLED;
    }
    if (definition.getRegistrationStatus() == PlantConstraintDefinition.RegistrationStatus.INCOMPLETE_BASIS) {
      return CoverageStatus.INCOMPLETE_REGISTRATION;
    }
    if (!convergenceComplete) {
      return CoverageStatus.INCOMPLETE_CONVERGENCE;
    }
    if (sample == null) {
      return CoverageStatus.MISSING_SAMPLE;
    }
    if (!definition.getQualifiedId().equals(sample.getQualifiedConstraintId())) {
      return CoverageStatus.METADATA_MISMATCH;
    }
    if (!calculationId.equals(sample.getCalculationId())) {
      return CoverageStatus.CALCULATION_ID_MISMATCH;
    }
    if (!definition.getUnit().equals(sample.getUnit()) || !definition.getBasis().equals(sample.getBasis())) {
      return CoverageStatus.METADATA_MISMATCH;
    }
    switch (sample.getStatus()) {
    case MISSING_VALUE:
      return CoverageStatus.MISSING_VALUE;
    case NOT_CALCULABLE:
      return CoverageStatus.NOT_CALCULABLE;
    case NON_FINITE_VALUE:
      return CoverageStatus.NON_FINITE_VALUE;
    case OUTSIDE_VALIDITY:
      return CoverageStatus.OUTSIDE_VALIDITY;
    case INCOMPLETE_CONVERGENCE:
      return CoverageStatus.INCOMPLETE_CONVERGENCE;
    case STALE:
      return CoverageStatus.STALE;
    case METADATA_MISMATCH:
      return CoverageStatus.METADATA_MISMATCH;
    case EXCEPTION:
      return CoverageStatus.EXCEPTION;
    case AVAILABLE:
    default:
      return evidenceIsConsistent(sample) ? CoverageStatus.AVAILABLE : CoverageStatus.INVALID_EVIDENCE;
    }
  }

  private static boolean evidenceIsConsistent(PlantConstraintSample sample) {
    if (!Double.isFinite(sample.getSampledValue()) || !Double.isFinite(sample.getApplicableLimit())
        || !Double.isFinite(sample.getNormalizedUtilization()) || !Double.isFinite(sample.getNormalizedResidual())
        || !Double.isFinite(sample.getPhysicalMargin()) || !Double.isFinite(sample.getRequiredRelief())
        || sample.getRequiredRelief() < 0.0) {
      return false;
    }
    if (!approximatelyEqual(sample.getNormalizedResidual(), sample.getNormalizedUtilization() - 1.0)) {
      return false;
    }
    if (!approximatelyEqual(sample.getRequiredRelief(), Math.max(0.0, -sample.getPhysicalMargin()))) {
      return false;
    }
    if (sample.getNormalizedResidual() > CONSISTENCY_TOLERANCE
        && sample.getPhysicalMargin() >= -CONSISTENCY_TOLERANCE) {
      return false;
    }
    return sample.getNormalizedResidual() >= -CONSISTENCY_TOLERANCE
        || sample.getPhysicalMargin() > -CONSISTENCY_TOLERANCE;
  }

  private static boolean approximatelyEqual(double first, double second) {
    double scale = Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
    return Math.abs(first - second) <= CONSISTENCY_TOLERANCE * scale;
  }

  private static OperatingStatus assessOperatingStatus(CoverageStatus coverage, PlantConstraintSample sample,
      double nearLimitThreshold) {
    if (coverage == CoverageStatus.DISABLED) {
      return OperatingStatus.DISABLED;
    }
    if (coverage != CoverageStatus.AVAILABLE) {
      switch (coverage) {
      case MISSING_SAMPLE:
      case MISSING_VALUE:
      case NOT_CALCULABLE:
        return OperatingStatus.UNAVAILABLE;
      default:
        return OperatingStatus.INVALID;
      }
    }
    if (sample.getNormalizedResidual() > CONSISTENCY_TOLERANCE) {
      return OperatingStatus.VIOLATED;
    }
    if (Math.abs(sample.getNormalizedResidual()) <= CONSISTENCY_TOLERANCE) {
      return OperatingStatus.ACTIVE_LIMIT;
    }
    return sample.getNormalizedUtilization() >= nearLimitThreshold ? OperatingStatus.NEAR_LIMIT
        : OperatingStatus.WITHIN_LIMIT;
  }

  private static boolean assessFeasible(CoverageStatus coverage, OperatingStatus operating, boolean hard) {
    if (coverage == CoverageStatus.DISABLED) {
      return true;
    }
    if (coverage != CoverageStatus.AVAILABLE) {
      return false;
    }
    return !hard || operating != OperatingStatus.VIOLATED;
  }

  private static String buildDiagnostic(CoverageStatus coverage, PlantConstraintSample sample) {
    if (sample != null && !sample.getDiagnostic().isEmpty()) {
      return coverage.name() + ": " + sample.getDiagnostic();
    }
    return coverage.name();
  }

  /** @return immutable registry definition */
  public PlantConstraintDefinition getDefinition() {
    return definition;
  }

  /** @return exact registry-qualified identity */
  public String getQualifiedConstraintId() {
    return definition.getQualifiedId();
  }

  /** @return immutable runtime sample, or null for an unsampled row */
  public PlantConstraintSample getSample() {
    return sample;
  }

  /** @return completeness and trustworthiness of this row */
  public CoverageStatus getCoverageStatus() {
    return coverageStatus;
  }

  /** @return engineering operating interpretation */
  public OperatingStatus getOperatingStatus() {
    return operatingStatus;
  }

  /** @return true for CRITICAL and HARD registered severity */
  public boolean isHardConstraint() {
    return hardConstraint;
  }

  /** @return true when coverage is complete and no hard limit is violated */
  public boolean isFeasible() {
    return feasible;
  }

  /** @return true when finite exact-calculation evidence is available */
  public boolean hasAvailableEvidence() {
    return coverageStatus == CoverageStatus.AVAILABLE;
  }

  /** @return concise coverage or sampler diagnostic */
  public String getDiagnostic() {
    return diagnostic;
  }

  /**
   * Returns normalized utilization without hiding unavailable evidence.
   *
   * @return dimensionless utilization, or NaN when unavailable
   */
  public double getNormalizedUtilization() {
    return hasAvailableEvidence() ? sample.getNormalizedUtilization() : Double.NaN;
  }

  /** @return normalized signed residual, or NaN when unavailable */
  public double getNormalizedResidual() {
    return hasAvailableEvidence() ? sample.getNormalizedResidual() : Double.NaN;
  }

  @Override
  public int compareTo(PlantConstraintEvidence other) {
    return getQualifiedConstraintId().compareTo(other.getQualifiedConstraintId());
  }
}
