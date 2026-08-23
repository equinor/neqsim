package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import neqsim.process.equipment.capacity.CapacityConstraint;

/**
 * Immutable definition and engineering basis for one plant-wide optimization constraint.
 *
 * <p>
 * The definition contains identity and registration metadata only. Runtime values, residuals, feasibility, and
 * utilization belong to the complete snapshot layer so an open registration can never be mistaken for fresh solved
 * evidence. Missing unit, basis, or provenance is retained with an explicit registration status instead of being
 * silently discarded.
 * </p>
 */
public final class PlantConstraintDefinition implements Serializable, Comparable<PlantConstraintDefinition> {
  private static final long serialVersionUID = 1L;

  /** How participant values must be combined after any declared conversion. */
  public enum AggregationPolicy {
    /** One direct observable or pre-calculated value. */
    DIRECT,
    /** Sum of compatible participant values. */
    SUM,
    /** Maximum participant value. */
    MAXIMUM,
    /** Minimum participant value. */
    MINIMUM,
    /** Sum compared with a shared plant budget. */
    SHARED_BUDGET,
    /** Participant values must use one common setpoint. */
    COMMON_SETPOINT,
    /** Participants require explicit conversion to the registered unit and basis. */
    RATE_BASIS_CONVERSION
  }

  /** Direction of increasing violation. */
  public enum LimitDirection {
    /** Values above the upper limit are worse. */
    MAXIMUM,
    /** Values below the lower limit are worse. */
    MINIMUM,
    /** Values outside an inclusive range are worse. */
    RANGE,
    /** Values away from a target by more than the tolerance are worse. */
    EQUALITY
  }

  /** Engineering role of the restriction. */
  public enum Category {
    PHYSICAL, DESIGN, OPERATING, QUALITY, ENVIRONMENTAL, COMMERCIAL, SCREENING
  }

  /** Completeness and enablement of the registration metadata. */
  public enum RegistrationStatus {
    REGISTERED, DISABLED, INCOMPLETE_BASIS, DISABLED_INCOMPLETE_BASIS
  }

  private final String id;
  private final String qualifiedId;
  private final PlantConstraintScope scope;
  private final AggregationPolicy aggregationPolicy;
  private final LimitDirection limitDirection;
  private final Category category;
  private final CapacityConstraint.ConstraintSeverity severity;
  private final String unit;
  private final String basis;
  private final String provenance;
  private final String owner;
  private final String reference;
  private final String calculationMethod;
  private final String description;
  private final boolean enabled;
  private final boolean confidenceSet;
  private final double confidence;
  private final boolean validityRangeSet;
  private final double validityMinimum;
  private final double validityMaximum;
  private final List<PlantConstraintParticipant> participants;
  private final RegistrationStatus registrationStatus;

  private PlantConstraintDefinition(Builder builder) {
    id = PlantConstraintScope.requireText(builder.id, "Constraint id");
    if (builder.scope == null) {
      throw new IllegalArgumentException("Plant constraint scope is required");
    }
    scope = builder.scope;
    qualifiedId = scope.getStableId() + "#" + PlantConstraintScope.escape(id);
    aggregationPolicy = require(builder.aggregationPolicy, "Aggregation policy");
    limitDirection = require(builder.limitDirection, "Limit direction");
    category = require(builder.category, "Constraint category");
    severity = require(builder.severity, "Constraint severity");
    unit = PlantConstraintScope.safeText(builder.unit);
    basis = PlantConstraintScope.safeText(builder.basis);
    provenance = PlantConstraintScope.safeText(builder.provenance);
    owner = PlantConstraintScope.safeText(builder.owner);
    reference = PlantConstraintScope.safeText(builder.reference);
    calculationMethod = PlantConstraintScope.safeText(builder.calculationMethod);
    description = PlantConstraintScope.safeText(builder.description);
    enabled = builder.enabled;
    confidenceSet = builder.confidenceSet;
    confidence = validateConfidence(builder.confidenceSet, builder.confidence);
    validityRangeSet = builder.validityRangeSet;
    validityMinimum = builder.validityMinimum;
    validityMaximum = builder.validityMaximum;
    validateValidity();
    participants = validateParticipants(builder.participants);
    boolean incomplete = unit.isEmpty() || basis.isEmpty() || provenance.isEmpty();
    registrationStatus = enabled ? (incomplete ? RegistrationStatus.INCOMPLETE_BASIS : RegistrationStatus.REGISTERED)
        : (incomplete ? RegistrationStatus.DISABLED_INCOMPLETE_BASIS : RegistrationStatus.DISABLED);
  }

  /** Starts a JPype-friendly fluent builder. */
  public static Builder builder(String id, PlantConstraintScope scope) {
    return new Builder(id, scope);
  }

  private static <T> T require(T value, String label) {
    if (value == null) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  private static double validateConfidence(boolean set, double value) {
    if (!set) {
      return Double.NaN;
    }
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException("Confidence must be finite and in [0, 1]");
    }
    return value;
  }

  private void validateValidity() {
    if (validityRangeSet && (!Double.isFinite(validityMinimum) || !Double.isFinite(validityMaximum)
        || validityMinimum > validityMaximum)) {
      throw new IllegalArgumentException("Validity range must be finite and ordered");
    }
  }

  private List<PlantConstraintParticipant> validateParticipants(List<PlantConstraintParticipant> source) {
    List<PlantConstraintParticipant> copy = new ArrayList<PlantConstraintParticipant>(source);
    Collections.sort(copy);
    if (aggregationPolicy != AggregationPolicy.DIRECT && copy.isEmpty()) {
      throw new IllegalArgumentException(aggregationPolicy + " requires at least one participant");
    }
    if (aggregationPolicy != AggregationPolicy.DIRECT && (unit.isEmpty() || basis.isEmpty())) {
      throw new IllegalArgumentException(aggregationPolicy + " requires an explicit target unit and basis");
    }
    Set<String> identities = new HashSet<String>();
    boolean explicitConversionFound = false;
    for (PlantConstraintParticipant participant : copy) {
      if (!identities.add(participant.getSourceId())) {
        throw new IllegalArgumentException("Duplicate participant identity " + participant.getSourceId());
      }
      if (participant.isConversionExplicit()) {
        explicitConversionFound = true;
      } else if (!unit.equals(participant.getUnit()) || !basis.equals(participant.getBasis())) {
        throw new IllegalArgumentException(
            "Participant " + participant.getSourceId() + " has unlike unit or basis without an explicit conversion");
      }
    }
    if (aggregationPolicy == AggregationPolicy.RATE_BASIS_CONVERSION && !explicitConversionFound) {
      throw new IllegalArgumentException("RATE_BASIS_CONVERSION requires an explicit participant conversion");
    }
    return Collections.unmodifiableList(copy);
  }

  /** @return caller-owned constraint id within its scope */
  public String getId() {
    return id;
  }

  /** @return escaped plant-stable scope and constraint identity */
  public String getQualifiedId() {
    return qualifiedId;
  }

  /** @return immutable plant subject scope */
  public PlantConstraintScope getScope() {
    return scope;
  }

  /** @return declared participant aggregation policy */
  public AggregationPolicy getAggregationPolicy() {
    return aggregationPolicy;
  }

  /** @return direction of increasing violation */
  public LimitDirection getLimitDirection() {
    return limitDirection;
  }

  /** @return engineering restriction category */
  public Category getCategory() {
    return category;
  }

  /** @return optimization severity */
  public CapacityConstraint.ConstraintSeverity getSeverity() {
    return severity;
  }

  /** @return physical engineering unit, or empty when not supplied */
  public String getUnit() {
    return unit;
  }

  /** @return measurement, rate, reference, or rating basis, or empty */
  public String getBasis() {
    return basis;
  }

  /** @return source of the registered restriction, or empty */
  public String getProvenance() {
    return provenance;
  }

  /** @return accountable constraint owner, or empty */
  public String getOwner() {
    return owner;
  }

  /** @return source document, tag, or external reference, or empty */
  public String getReference() {
    return reference;
  }

  /** @return named calculation or measurement method, or empty */
  public String getCalculationMethod() {
    return calculationMethod;
  }

  /** @return engineering description */
  public String getDescription() {
    return description;
  }

  /** @return whether the restriction participates in feasibility */
  public boolean isEnabled() {
    return enabled;
  }

  /** @return whether evidence-quality confidence was explicitly supplied */
  public boolean hasConfidence() {
    return confidenceSet;
  }

  /** @return evidence-quality confidence, or NaN when unquantified */
  public double getConfidence() {
    return confidence;
  }

  /** @return whether a scalar validity range was supplied */
  public boolean hasValidityRange() {
    return validityRangeSet;
  }

  /** @return inclusive lower validity bound in the registered unit, or NaN */
  public double getValidityMinimum() {
    return validityRangeSet ? validityMinimum : Double.NaN;
  }

  /** @return inclusive upper validity bound in the registered unit, or NaN */
  public double getValidityMaximum() {
    return validityRangeSet ? validityMaximum : Double.NaN;
  }

  /** @return deterministic participant list sorted by source identity */
  public List<PlantConstraintParticipant> getParticipants() {
    return participants;
  }

  /** @return registration completeness and enablement status */
  public RegistrationStatus getRegistrationStatus() {
    return registrationStatus;
  }

  String canonicalForm() {
    StringBuilder value = new StringBuilder();
    appendCanonical(value, qualifiedId);
    appendCanonical(value, aggregationPolicy.name());
    appendCanonical(value, limitDirection.name());
    appendCanonical(value, category.name());
    appendCanonical(value, severity.name());
    appendCanonical(value, unit);
    appendCanonical(value, basis);
    appendCanonical(value, provenance);
    appendCanonical(value, owner);
    appendCanonical(value, reference);
    appendCanonical(value, calculationMethod);
    appendCanonical(value, description);
    appendCanonical(value, Boolean.toString(enabled));
    appendCanonical(value, Boolean.toString(confidenceSet));
    appendCanonical(value, Double.toHexString(confidence));
    appendCanonical(value, Boolean.toString(validityRangeSet));
    appendCanonical(value, Double.toHexString(validityMinimum));
    appendCanonical(value, Double.toHexString(validityMaximum));
    for (PlantConstraintParticipant participant : participants) {
      appendCanonical(value, participant.canonicalForm());
    }
    return value.toString();
  }

  private static void appendCanonical(StringBuilder target, String value) {
    target.append(value.length()).append(':').append(value);
  }

  @Override
  public int compareTo(PlantConstraintDefinition other) {
    return qualifiedId.compareTo(other.qualifiedId);
  }

  /** Fluent, callback-free construction surface suitable for Java and JPype callers. */
  public static final class Builder {
    private final String id;
    private final PlantConstraintScope scope;
    private AggregationPolicy aggregationPolicy = AggregationPolicy.DIRECT;
    private LimitDirection limitDirection = LimitDirection.MAXIMUM;
    private Category category = Category.OPERATING;
    private CapacityConstraint.ConstraintSeverity severity = CapacityConstraint.ConstraintSeverity.HARD;
    private String unit = "";
    private String basis = "";
    private String provenance = "";
    private String owner = "";
    private String reference = "";
    private String calculationMethod = "";
    private String description = "";
    private boolean enabled = true;
    private boolean confidenceSet;
    private double confidence = Double.NaN;
    private boolean validityRangeSet;
    private double validityMinimum = Double.NaN;
    private double validityMaximum = Double.NaN;
    private final List<PlantConstraintParticipant> participants = new ArrayList<PlantConstraintParticipant>();

    private Builder(String id, PlantConstraintScope scope) {
      this.id = id;
      this.scope = scope;
    }

    /** Sets how participant values are combined. */
    public Builder aggregationPolicy(AggregationPolicy value) {
      aggregationPolicy = value;
      return this;
    }

    /** Sets the direction in which the registered limit is violated. */
    public Builder limitDirection(LimitDirection value) {
      limitDirection = value;
      return this;
    }

    /** Sets the engineering category. */
    public Builder category(Category value) {
      category = value;
      return this;
    }

    /** Sets the established equipment-capacity severity. */
    public Builder severity(CapacityConstraint.ConstraintSeverity value) {
      severity = value;
      return this;
    }

    /** Sets the target engineering unit. */
    public Builder unit(String value) {
      unit = value;
      return this;
    }

    /** Sets the target measurement, rate, reference, or rating basis. */
    public Builder basis(String value) {
      basis = value;
      return this;
    }

    /** Sets the source of the registered restriction. */
    public Builder provenance(String value) {
      provenance = value;
      return this;
    }

    /** Sets the accountable owner or discipline. */
    public Builder owner(String value) {
      owner = value;
      return this;
    }

    /** Sets the source document, tag, or external reference. */
    public Builder reference(String value) {
      reference = value;
      return this;
    }

    /** Sets the named calculation or measurement method. */
    public Builder calculationMethod(String value) {
      calculationMethod = value;
      return this;
    }

    /** Sets the engineering description. */
    public Builder description(String value) {
      description = value;
      return this;
    }

    /** Sets whether the restriction participates in feasibility. */
    public Builder enabled(boolean value) {
      enabled = value;
      return this;
    }

    /** Sets evidence confidence in the inclusive range {@code [0, 1]}. */
    public Builder confidence(double value) {
      confidence = value;
      confidenceSet = true;
      return this;
    }

    /** Sets an inclusive scalar validity interval in the target unit. */
    public Builder validityRange(double minimum, double maximum) {
      validityMinimum = minimum;
      validityMaximum = maximum;
      validityRangeSet = true;
      return this;
    }

    /** Adds one participant; definitions sort participants by stable source identity. */
    public Builder participant(PlantConstraintParticipant value) {
      if (value == null) {
        throw new IllegalArgumentException("Participant is required");
      }
      participants.add(value);
      return this;
    }

    /** Builds and validates the immutable registration. */
    public PlantConstraintDefinition build() {
      return new PlantConstraintDefinition(this);
    }
  }
}
