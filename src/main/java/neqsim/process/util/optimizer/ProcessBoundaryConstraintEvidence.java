package neqsim.process.util.optimizer;

import java.io.Serializable;
import neqsim.process.equipment.network.NetworkDecisionVariable;
import neqsim.process.equipment.network.NetworkQualityResult;

/**
 * Immutable, unit-safe evidence for one process-boundary constraint at one completed operating point.
 *
 * <p>
 * Physical values and margins retain their engineering unit. {@link #getScaledViolation()} is the only
 * dimensionless quantity and is formed with the explicit positive {@link #getResidualScale()}.
 * </p>
 */
public final class ProcessBoundaryConstraintEvidence implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Boundary role. */
  public enum Kind {
    INJECTION,
    RECEIVING_CAPACITY,
    EXPORT_CAPACITY,
    PRODUCT_QUALITY,
    NOMINATION
  }

  /** Positive-flow direction relative to the modeled process. */
  public enum FlowDirection {
    INTO_PROCESS,
    OUT_OF_PROCESS,
    BIDIRECTIONAL,
    NOT_APPLICABLE
  }

  /** Applicability of the registered validity period to this evaluation. */
  public enum ApplicabilityStatus {
    APPLICABLE,
    OUTSIDE_VALIDITY,
    NOT_ASSESSED
  }

  /** Availability of the runtime sample used to calculate the residual. */
  public enum CalculationStatus {
    AVAILABLE,
    MISSING_VALUE,
    NON_FINITE_VALUE,
    NOT_CALCULABLE,
    METADATA_MISMATCH,
    OUTSIDE_VALIDITY
  }

  /** Immutable registration metadata, safe to retain independently of evaluator callbacks. */
  public static final class Metadata implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String areaName;
    private final String pointName;
    private final Kind kind;
    private final FlowDirection flowDirection;
    private final NetworkDecisionVariable.RateBasis rateBasis;
    private final String provenance;
    private final double confidence;
    private final String effectiveFrom;
    private final String effectiveTo;
    private final ApplicabilityStatus applicabilityStatus;
    private final String observableName;
    private final String method;
    private final String referenceJson;
    private final int periodIndex;

    /**
     * Creates complete immutable boundary metadata.
     *
     * @param id stable caller-owned identifier
     * @param areaName process-model area name
     * @param pointName named boundary point
     * @param kind boundary role
     * @param flowDirection positive-flow direction
     * @param rateBasis rate basis, or {@code NONE} for non-flow constraints
     * @param provenance source of the limit or nomination
     * @param confidence confidence in [0, 1], or NaN when not quantified
     * @param effectiveFrom inclusive effective-period label, or null
     * @param effectiveTo inclusive effective-period label, or null
     * @param applicabilityStatus validity assessment for this evaluation context
     * @param observableName measured attribute or metric key, or null
     * @param method calculation, test, or standard name, or null
     * @param referenceJson serialized reference conditions, or null
     * @param periodIndex zero-based nomination period, or -1 when not applicable
     */
    public Metadata(String id, String areaName, String pointName, Kind kind, FlowDirection flowDirection,
        NetworkDecisionVariable.RateBasis rateBasis, String provenance, double confidence, String effectiveFrom,
        String effectiveTo, ApplicabilityStatus applicabilityStatus, String observableName, String method,
        String referenceJson, int periodIndex) {
      this.id = requireText(id, "Boundary id");
      this.areaName = requireText(areaName, "Area name");
      this.pointName = requireText(pointName, "Point name");
      if (kind == null || flowDirection == null || rateBasis == null || applicabilityStatus == null) {
        throw new IllegalArgumentException("Boundary kind, direction, rate basis, and applicability are required");
      }
      if (Double.isFinite(confidence) && (confidence < 0.0 || confidence > 1.0)) {
        throw new IllegalArgumentException("Confidence must be in [0, 1] or NaN");
      }
      if (periodIndex < -1) {
        throw new IllegalArgumentException("Period index must be -1 or non-negative");
      }
      this.kind = kind;
      this.flowDirection = flowDirection;
      this.rateBasis = rateBasis;
      this.provenance = provenance;
      this.confidence = confidence;
      this.effectiveFrom = effectiveFrom;
      this.effectiveTo = effectiveTo;
      this.applicabilityStatus = applicabilityStatus;
      this.observableName = observableName;
      this.method = method;
      this.referenceJson = referenceJson;
      this.periodIndex = periodIndex;
    }

    /** @return stable caller-owned identifier */
    public String getId() {
      return id;
    }

    /** @return process-model area name */
    public String getAreaName() {
      return areaName;
    }

    /** @return named boundary point */
    public String getPointName() {
      return pointName;
    }

    /** @return boundary role */
    public Kind getKind() {
      return kind;
    }

    /** @return positive-flow direction */
    public FlowDirection getFlowDirection() {
      return flowDirection;
    }

    /** @return explicit rate basis */
    public NetworkDecisionVariable.RateBasis getRateBasis() {
      return rateBasis;
    }

    /** @return source of the registered limit or nomination */
    public String getProvenance() {
      return provenance;
    }

    /** @return confidence in [0, 1], or NaN */
    public double getConfidence() {
      return confidence;
    }

    /** @return inclusive effective-period start label, or null */
    public String getEffectiveFrom() {
      return effectiveFrom;
    }

    /** @return inclusive effective-period end label, or null */
    public String getEffectiveTo() {
      return effectiveTo;
    }

    /** @return validity assessment for this evaluation context */
    public ApplicabilityStatus getApplicabilityStatus() {
      return applicabilityStatus;
    }

    /** @return measured attribute or metric key, or null */
    public String getObservableName() {
      return observableName;
    }

    /** @return calculation, test, or standard name, or null */
    public String getMethod() {
      return method;
    }

    /** @return serialized reference conditions, or null */
    public String getReferenceJson() {
      return referenceJson;
    }

    /** @return zero-based nomination period, or -1 */
    public int getPeriodIndex() {
      return periodIndex;
    }
  }

  /** Immutable result of sampling one boundary observable exactly once. */
  public static final class Sample implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Double value;
    private final CalculationStatus status;
    private final Double sourceMargin;
    private final String provenance;
    private final String diagnostic;

    /** Creates one immutable sample. */
    public Sample(Double value, CalculationStatus status, Double sourceMargin, String provenance,
        String diagnostic) {
      this.value = value;
      this.status = status == null ? CalculationStatus.NOT_CALCULABLE : status;
      this.sourceMargin = sourceMargin;
      this.provenance = provenance;
      this.diagnostic = diagnostic;
    }

    /** Creates an available scalar sample. */
    public static Sample available(double value) {
      return new Sample(Double.valueOf(value), CalculationStatus.AVAILABLE, null, null, null);
    }

    /** Adapts a network-quality result without retaining its mutable reference object. */
    public static Sample fromNetworkQualityResult(NetworkQualityResult result) {
      if (result == null) {
        return new Sample(null, CalculationStatus.NOT_CALCULABLE, null, null,
            "Network quality result was null");
      }
      CalculationStatus calculationStatus = result.getStatus() == NetworkQualityResult.Status.NOT_CALCULABLE
          ? CalculationStatus.NOT_CALCULABLE : CalculationStatus.AVAILABLE;
      return new Sample(result.getValue(), calculationStatus, result.getMargin(), result.getProvenance(),
          result.getMessage());
    }

    /** @return sampled value, or null */
    public Double getValue() {
      return value;
    }

    /** @return calculation status reported by the sampler */
    public CalculationStatus getStatus() {
      return status;
    }

    /** @return source-reported signed margin, or null */
    public Double getSourceMargin() {
      return sourceMargin;
    }

    /** @return sample-specific provenance, or null */
    public String getProvenance() {
      return provenance;
    }

    /** @return sample diagnostic, or null */
    public String getDiagnostic() {
      return diagnostic;
    }
  }

  private final Metadata metadata;
  private final String qualifiedConstraintName;
  private final ProcessModelSimulationEvaluator.ConstraintDefinition.Type constraintType;
  private final boolean hard;
  private final String unit;
  private final double lowerBound;
  private final double upperBound;
  private final double equalityTolerance;
  private final double sampledValue;
  private final double signedMargin;
  private final double violation;
  private final double residualScale;
  private final double scaledViolation;
  private final CalculationStatus calculationStatus;
  private final Double sourceMargin;
  private final String sampleProvenance;
  private final String diagnostic;

  /** Creates one evaluated evidence row. */
  ProcessBoundaryConstraintEvidence(Metadata metadata,
      ProcessModelSimulationEvaluator.ConstraintDefinition definition, Sample sample, double residualScale) {
    if (metadata == null || definition == null) {
      throw new IllegalArgumentException("Boundary metadata and constraint definition are required");
    }
    if (!Double.isFinite(residualScale) || residualScale <= 0.0) {
      throw new IllegalArgumentException("Residual scale must be finite and positive");
    }
    this.metadata = metadata;
    this.qualifiedConstraintName = metadata.getAreaName() + "::" + metadata.getPointName() + "/"
        + definition.getName();
    this.constraintType = definition.getType();
    this.hard = definition.isHard();
    this.unit = definition.getUnit();
    this.lowerBound = definition.getLowerBound();
    this.upperBound = definition.getUpperBound();
    this.equalityTolerance = definition.getEqualityTolerance();
    this.residualScale = residualScale;
    Sample safeSample = sample == null
        ? new Sample(null, CalculationStatus.MISSING_VALUE, null, null, "Boundary sampler returned null") : sample;
    CalculationStatus status = safeSample.getStatus();
    Double value = safeSample.getValue();
    if (metadata.getApplicabilityStatus() == ApplicabilityStatus.OUTSIDE_VALIDITY) {
      status = CalculationStatus.OUTSIDE_VALIDITY;
    } else if (status == CalculationStatus.AVAILABLE && value == null) {
      status = CalculationStatus.MISSING_VALUE;
    } else if (status == CalculationStatus.AVAILABLE && !Double.isFinite(value.doubleValue())) {
      status = CalculationStatus.NON_FINITE_VALUE;
    }
    this.calculationStatus = status;
    this.sampledValue = status == CalculationStatus.AVAILABLE ? value.doubleValue() : Double.NaN;
    this.signedMargin = status == CalculationStatus.AVAILABLE ? definition.marginFromValue(sampledValue) : Double.NaN;
    this.violation = Double.isFinite(signedMargin) ? Math.max(0.0, -signedMargin) : Double.NaN;
    this.scaledViolation = Double.isFinite(violation) ? violation / residualScale : Double.NaN;
    this.sourceMargin = safeSample.getSourceMargin();
    this.sampleProvenance = safeSample.getProvenance();
    this.diagnostic = safeSample.getDiagnostic();
  }

  /** @return immutable registration metadata */
  public Metadata getMetadata() {
    return metadata;
  }

  /** @return stable {@code area::point/constraint} identity */
  public String getQualifiedConstraintName() {
    return qualifiedConstraintName;
  }

  /** @return lower, upper, range, or equality type */
  public ProcessModelSimulationEvaluator.ConstraintDefinition.Type getConstraintType() {
    return constraintType;
  }

  /** @return true when unavailable or violated evidence is infeasible */
  public boolean isHard() {
    return hard;
  }

  /** @return physical engineering unit */
  public String getUnit() {
    return unit;
  }

  /** @return physical lower bound or equality target */
  public double getLowerBound() {
    return lowerBound;
  }

  /** @return physical upper bound */
  public double getUpperBound() {
    return upperBound;
  }

  /** @return absolute equality tolerance */
  public double getEqualityTolerance() {
    return equalityTolerance;
  }

  /** @return sampled physical value, or NaN when unavailable */
  public double getSampledValue() {
    return sampledValue;
  }

  /** @return positive satisfied margin, negative violation, or NaN */
  public double getSignedMargin() {
    return signedMargin;
  }

  /** @return non-negative physical violation, or NaN */
  public double getViolation() {
    return violation;
  }

  /** @return positive physical scale used only for normalization */
  public double getResidualScale() {
    return residualScale;
  }

  /** @return non-negative dimensionless violation, or NaN */
  public double getScaledViolation() {
    return scaledViolation;
  }

  /** @return resolved evidence calculation status */
  public CalculationStatus getCalculationStatus() {
    return calculationStatus;
  }

  /** @return source-reported signed margin, or null */
  public Double getSourceMargin() {
    return sourceMargin;
  }

  /** @return runtime-sample provenance, or null */
  public String getSampleProvenance() {
    return sampleProvenance;
  }

  /** @return runtime diagnostic, or null */
  public String getDiagnostic() {
    return diagnostic;
  }

  /** @return true when a finite, applicable value was available */
  public boolean isCalculable() {
    return calculationStatus == CalculationStatus.AVAILABLE;
  }

  /** @return true when evidence is calculable and the physical margin is non-negative */
  public boolean isFeasible() {
    return isCalculable() && signedMargin >= 0.0;
  }

  private static String requireText(String value, String label) {
    if (value == null || value.trim().length() == 0) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }
}
