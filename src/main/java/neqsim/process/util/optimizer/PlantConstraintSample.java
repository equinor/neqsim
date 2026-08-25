package neqsim.process.util.optimizer;

import java.io.Serializable;

/**
 * Immutable callback-free sample for one registered plant constraint and one completed calculation.
 *
 * <p>
 * Normalized utilization and residual are dimensionless. The residual convention is {@code utilization - 1}: positive
 * values violate the limit, zero is active, and negative values have headroom. Sampled value, applicable limit, signed
 * margin, and required relief retain the registered physical unit and basis.
 * </p>
 */
public final class PlantConstraintSample implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Availability and validity reported by the sampler. */
  public enum SampleStatus {
    /** A finite sample tied to the declared calculation is available. */
    AVAILABLE,
    /** The sampler did not return a value. */
    MISSING_VALUE,
    /** The restriction could not be calculated at this operating point. */
    NOT_CALCULABLE,
    /** One or more required numerical values are non-finite. */
    NON_FINITE_VALUE,
    /** The operating point is outside the evidence validity envelope. */
    OUTSIDE_VALIDITY,
    /** The process calculation did not complete convergence. */
    INCOMPLETE_CONVERGENCE,
    /** The evidence was produced for an older process state. */
    STALE,
    /** The source metadata does not match the registered contract. */
    METADATA_MISMATCH,
    /** Sampling raised an exception. */
    EXCEPTION
  }

  private final String qualifiedConstraintId;
  private final String calculationId;
  private final SampleStatus status;
  private final double sampledValue;
  private final double applicableLimit;
  private final double normalizedUtilization;
  private final double normalizedResidual;
  private final double physicalMargin;
  private final double requiredRelief;
  private final String unit;
  private final String basis;
  private final String provenance;
  private final String diagnostic;

  private PlantConstraintSample(Builder builder) {
    qualifiedConstraintId = PlantConstraintScope.requireText(builder.qualifiedConstraintId, "Qualified constraint id");
    calculationId = PlantConstraintScope.requireText(builder.calculationId, "Calculation id");
    status = builder.status == null ? SampleStatus.NOT_CALCULABLE : builder.status;
    sampledValue = builder.sampledValue;
    applicableLimit = builder.applicableLimit;
    normalizedUtilization = builder.normalizedUtilization;
    normalizedResidual = builder.normalizedResidual;
    physicalMargin = builder.physicalMargin;
    requiredRelief = builder.requiredRelief;
    unit = PlantConstraintScope.safeText(builder.unit);
    basis = PlantConstraintScope.safeText(builder.basis);
    provenance = PlantConstraintScope.safeText(builder.provenance);
    diagnostic = PlantConstraintScope.safeText(builder.diagnostic);
  }

  /**
   * Starts a JPype-friendly sample builder.
   *
   * @param qualifiedConstraintId exact registry-qualified identity
   * @param calculationId exact completed process calculation identity
   * @return new builder
   */
  public static Builder builder(String qualifiedConstraintId, String calculationId) {
    return new Builder(qualifiedConstraintId, calculationId);
  }

  /**
   * Adapts immutable installed-equipment evidence without retaining live equipment or suppliers.
   *
   * @param definition matching plant registry definition
   * @param calculationId completed calculation identity
   * @param evidence immutable evidence produced from the established capacity framework
   * @return callback-free plant sample
   */
  public static PlantConstraintSample fromInstalledEquipmentEvidence(PlantConstraintDefinition definition,
      String calculationId, InstalledEquipmentCapacityEvidence evidence) {
    if (definition == null || evidence == null) {
      throw new IllegalArgumentException("Plant definition and installed-equipment evidence are required");
    }
    SampleStatus resolvedStatus = mapInstalledStatus(evidence);
    if (!installedMetadataMatches(definition, evidence)) {
      resolvedStatus = SampleStatus.METADATA_MISMATCH;
    }
    return builder(definition.getQualifiedId(), calculationId).status(resolvedStatus)
        .values(evidence.getCurrentValue(), evidence.getApplicableLimit())
        .normalized(evidence.getNormalizedUtilization(), evidence.getNormalizedUtilization() - 1.0)
        .physical(evidence.getPhysicalMargin(), evidence.getRequiredRelief()).unit(definition.getUnit())
        .basis(definition.getBasis()).provenance(evidence.getDataSource())
        .diagnostic(resolvedStatus == SampleStatus.AVAILABLE ? "" : evidence.getEvidenceStatus().name()).build();
  }

  /**
   * Adapts immutable process-boundary evidence without retaining evaluator callbacks or process state.
   *
   * @param definition matching stream-scope plant registry definition
   * @param calculationId completed calculation identity
   * @param evidence immutable boundary evidence
   * @return callback-free plant sample
   */
  public static PlantConstraintSample fromProcessBoundaryEvidence(PlantConstraintDefinition definition,
      String calculationId, ProcessBoundaryConstraintEvidence evidence) {
    if (definition == null || evidence == null) {
      throw new IllegalArgumentException("Plant definition and process-boundary evidence are required");
    }
    SampleStatus resolvedStatus = mapBoundaryStatus(evidence.getCalculationStatus());
    if (!boundaryMetadataMatches(definition, evidence)) {
      resolvedStatus = SampleStatus.METADATA_MISMATCH;
    }
    double residual = Double.isFinite(evidence.getSignedMargin())
        ? -evidence.getSignedMargin() / evidence.getResidualScale()
        : Double.NaN;
    double utilization = Double.isFinite(residual) ? 1.0 + residual : Double.NaN;
    String provenance = PlantConstraintScope.safeText(evidence.getSampleProvenance());
    if (provenance.isEmpty()) {
      provenance = PlantConstraintScope.safeText(evidence.getMetadata().getProvenance());
    }
    return builder(definition.getQualifiedId(), calculationId).status(resolvedStatus)
        .values(evidence.getSampledValue(), applicableBoundaryLimit(evidence)).normalized(utilization, residual)
        .physical(evidence.getSignedMargin(), evidence.getViolation()).unit(definition.getUnit())
        .basis(definition.getBasis()).provenance(provenance).diagnostic(evidence.getDiagnostic()).build();
  }

  private static SampleStatus mapInstalledStatus(InstalledEquipmentCapacityEvidence evidence) {
    if (evidence
        .getEvidenceApplicability() == InstalledEquipmentCapacityEvidence.EvidenceApplicability.OUTSIDE_VALIDITY_RANGE) {
      return SampleStatus.OUTSIDE_VALIDITY;
    }
    switch (evidence.getEvidenceStatus()) {
    case AVAILABLE:
      return SampleStatus.AVAILABLE;
    case NON_FINITE_CURRENT_VALUE:
    case NON_FINITE_APPLICABLE_LIMIT:
    case NON_FINITE_UTILIZATION:
      return SampleStatus.NON_FINITE_VALUE;
    case INVALID_APPLICABLE_LIMIT:
    default:
      return SampleStatus.NOT_CALCULABLE;
    }
  }

  private static boolean installedMetadataMatches(PlantConstraintDefinition definition,
      InstalledEquipmentCapacityEvidence evidence) {
    PlantConstraintScope scope = definition.getScope();
    if (scope.getType() != PlantConstraintScope.Type.EQUIPMENT) {
      return false;
    }
    if (!definition.getId().equals(evidence.getConstraintName()) || !scope.getAreaName().equals(evidence.getAreaName())
        || !scope.getSubjectName().equals(evidence.getEquipmentName())
        || !definition.getUnit().equals(evidence.getPhysicalUnit())) {
      return false;
    }
    PlantConstraintDefinition.LimitDirection expected = evidence.isMinimumConstraint()
        ? PlantConstraintDefinition.LimitDirection.MINIMUM
        : PlantConstraintDefinition.LimitDirection.MAXIMUM;
    return definition.getLimitDirection() == expected;
  }

  private static SampleStatus mapBoundaryStatus(ProcessBoundaryConstraintEvidence.CalculationStatus status) {
    switch (status) {
    case AVAILABLE:
      return SampleStatus.AVAILABLE;
    case MISSING_VALUE:
      return SampleStatus.MISSING_VALUE;
    case NON_FINITE_VALUE:
      return SampleStatus.NON_FINITE_VALUE;
    case OUTSIDE_VALIDITY:
      return SampleStatus.OUTSIDE_VALIDITY;
    case METADATA_MISMATCH:
      return SampleStatus.METADATA_MISMATCH;
    case NOT_CALCULABLE:
    default:
      return SampleStatus.NOT_CALCULABLE;
    }
  }

  private static boolean boundaryMetadataMatches(PlantConstraintDefinition definition,
      ProcessBoundaryConstraintEvidence evidence) {
    PlantConstraintScope scope = definition.getScope();
    if (scope.getType() != PlantConstraintScope.Type.STREAM) {
      return false;
    }
    if (!definition.getId().equals(boundaryConstraintName(evidence))
        || !scope.getAreaName().equals(evidence.getMetadata().getAreaName())
        || !scope.getSubjectName().equals(evidence.getMetadata().getPointName())
        || !definition.getUnit().equals(PlantConstraintScope.safeText(evidence.getUnit()))) {
      return false;
    }
    return definition.getLimitDirection() == boundaryDirection(evidence.getConstraintType());
  }

  private static String boundaryConstraintName(ProcessBoundaryConstraintEvidence evidence) {
    String qualifiedName = evidence.getQualifiedConstraintName();
    int separator = qualifiedName.lastIndexOf('/');
    return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
  }

  private static PlantConstraintDefinition.LimitDirection boundaryDirection(
      ProcessModelSimulationEvaluator.ConstraintDefinition.Type type) {
    switch (type) {
    case LOWER_BOUND:
      return PlantConstraintDefinition.LimitDirection.MINIMUM;
    case UPPER_BOUND:
      return PlantConstraintDefinition.LimitDirection.MAXIMUM;
    case RANGE:
      return PlantConstraintDefinition.LimitDirection.RANGE;
    case EQUALITY:
    default:
      return PlantConstraintDefinition.LimitDirection.EQUALITY;
    }
  }

  private static double applicableBoundaryLimit(ProcessBoundaryConstraintEvidence evidence) {
    switch (evidence.getConstraintType()) {
    case LOWER_BOUND:
    case EQUALITY:
      return evidence.getLowerBound();
    case UPPER_BOUND:
      return evidence.getUpperBound();
    case RANGE:
      if (!Double.isFinite(evidence.getSampledValue())) {
        return Double.NaN;
      }
      return Math.abs(evidence.getSampledValue() - evidence.getLowerBound()) <= Math
          .abs(evidence.getUpperBound() - evidence.getSampledValue()) ? evidence.getLowerBound()
              : evidence.getUpperBound();
    default:
      return Double.NaN;
    }
  }

  /** @return exact registry-qualified identity */
  public String getQualifiedConstraintId() {
    return qualifiedConstraintId;
  }

  /** @return exact completed calculation identity */
  public String getCalculationId() {
    return calculationId;
  }

  /** @return sample availability and validity status */
  public SampleStatus getStatus() {
    return status;
  }

  /** @return sampled physical value, or NaN when unavailable */
  public double getSampledValue() {
    return sampledValue;
  }

  /** @return applicable physical limit, or NaN when unavailable */
  public double getApplicableLimit() {
    return applicableLimit;
  }

  /** @return dimensionless utilization where 1.0 is active */
  public double getNormalizedUtilization() {
    return normalizedUtilization;
  }

  /** @return dimensionless signed residual {@code utilization - 1} */
  public double getNormalizedResidual() {
    return normalizedResidual;
  }

  /** @return signed physical headroom; negative values violate */
  public double getPhysicalMargin() {
    return physicalMargin;
  }

  /** @return non-negative physical relief required to reach the limit */
  public double getRequiredRelief() {
    return requiredRelief;
  }

  /** @return physical engineering unit */
  public String getUnit() {
    return unit;
  }

  /** @return measurement, rating, rate, or reference basis */
  public String getBasis() {
    return basis;
  }

  /** @return runtime evidence provenance */
  public String getProvenance() {
    return provenance;
  }

  /** @return sampler diagnostic, or empty */
  public String getDiagnostic() {
    return diagnostic;
  }

  /** Callback-free builder suitable for Java and JPype callers. */
  public static final class Builder {
    private final String qualifiedConstraintId;
    private final String calculationId;
    private SampleStatus status = SampleStatus.AVAILABLE;
    private double sampledValue = Double.NaN;
    private double applicableLimit = Double.NaN;
    private double normalizedUtilization = Double.NaN;
    private double normalizedResidual = Double.NaN;
    private double physicalMargin = Double.NaN;
    private double requiredRelief = Double.NaN;
    private String unit = "";
    private String basis = "";
    private String provenance = "";
    private String diagnostic = "";

    private Builder(String qualifiedConstraintId, String calculationId) {
      this.qualifiedConstraintId = qualifiedConstraintId;
      this.calculationId = calculationId;
    }

    /** Sets the sampler-reported status. */
    public Builder status(SampleStatus value) {
      status = value;
      return this;
    }

    /** Sets the physical sampled value and applicable limit. */
    public Builder values(double value, double limit) {
      sampledValue = value;
      applicableLimit = limit;
      return this;
    }

    /** Sets dimensionless utilization and signed residual. */
    public Builder normalized(double utilization, double residual) {
      normalizedUtilization = utilization;
      normalizedResidual = residual;
      return this;
    }

    /** Sets signed physical margin and non-negative required relief. */
    public Builder physical(double margin, double relief) {
      physicalMargin = margin;
      requiredRelief = relief;
      return this;
    }

    /** Sets the physical engineering unit. */
    public Builder unit(String value) {
      unit = value;
      return this;
    }

    /** Sets the measurement, rating, rate, or reference basis. */
    public Builder basis(String value) {
      basis = value;
      return this;
    }

    /** Sets runtime evidence provenance. */
    public Builder provenance(String value) {
      provenance = value;
      return this;
    }

    /** Sets an explanatory sampler diagnostic. */
    public Builder diagnostic(String value) {
      diagnostic = value;
      return this;
    }

    /** @return validated immutable sample */
    public PlantConstraintSample build() {
      return new PlantConstraintSample(this);
    }
  }
}
