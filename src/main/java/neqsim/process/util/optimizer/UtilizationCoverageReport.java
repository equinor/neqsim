package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.util.optimizer.EquipmentCapacityConstraintResolver.ResolvedConstraint;
import neqsim.process.util.optimizer.InstalledEquipmentCapacityEvidence.ConstraintOrigin;

/**
 * Immutable capacity evidence coverage for explicitly declared equipment and constraint identities.
 *
 * <p>
 * Expectations are independent of successful discovery: missing equipment and missing constraints remain visible.
 * Completion applies only to this declared scope, never to an undeclared whole plant. Every selected supplier is
 * sampled at most once, and this report retains no equipment, callbacks, or mutable registry references. Completion
 * describes evidence availability, not process convergence or operating feasibility. Unknown numeric evidence is NaN
 * through Java getters and null in JSON.
 * </p>
 */
public final class UtilizationCoverageReport implements Serializable {
  private static final long serialVersionUID = 1L;
  private final String schemaVersion = "1.0";
  private final String coverageScope = "DECLARED_EQUIPMENT_AND_CONSTRAINTS";
  private final String modelName;
  private final String registryIdentityDigest;
  private final List<String> expectedEquipmentIds;
  private final List<String> expectedConstraintIds;
  private final List<String> requiredConstraintIds;
  private final List<Row> rows;
  private final List<String> diagnostics;
  private final boolean complete;

  /** Evidence availability and scope diagnostics, independent of operating feasibility. */
  public enum Status {
    /** All required physical and metadata evidence is present. */
    AVAILABLE,
    /** Equipment or its constraint is intentionally disabled. */
    DISABLED,
    /** The expected equipment was not supplied. */
    MISSING_EQUIPMENT,
    /** The expected constraint was not discovered. */
    MISSING_CONSTRAINT,
    /** No constraints were discovered for expected equipment. */
    NO_CONSTRAINTS,
    /** Equipment constraint discovery failed. */
    DISCOVERY_FAILED,
    /** No explicit cached value or supplier exists. */
    MISSING_CURRENT_VALUE,
    /** A supplier returned a non-finite current value. */
    NON_FINITE_CURRENT_VALUE,
    /** The derived dimensionless utilization is not finite. */
    NON_FINITE_UTILIZATION,
    /** A supplier threw an exception. */
    SAMPLE_FAILED,
    /** No finite applicable rating has been supplied. */
    MISSING_LIMIT,
    /** The applicable limit is non-positive. */
    INVALID_LIMIT,
    /** The physical unit is missing. */
    MISSING_UNIT,
    /** The measurement or rating basis is missing. */
    MISSING_BASIS,
    /** The source of the rating is missing. */
    MISSING_PROVENANCE,
    /** A discovered or expected constraint is absent from the supplied registry. */
    MISSING_REGISTRATION,
    /** Registry and live definition metadata disagree. */
    METADATA_MISMATCH,
    /** Default or advisory evidence does not establish rated installed capacity. */
    SCREENING_ONLY,
    /** Current evidence lies outside the declared scalar validity range. */
    OUTSIDE_VALIDITY_RANGE
  }

  private UtilizationCoverageReport(Builder builder) {
    modelName = builder.modelName;
    registryIdentityDigest = builder.registry == null ? "" : builder.registry.getIdentityDigest();
    expectedEquipmentIds = immutableList(builder.expectedEquipment.keySet());
    expectedConstraintIds = immutableList(builder.expectedConstraints);
    Set<String> required = new TreeSet<String>(builder.expectedConstraints);
    List<Row> assessed = new ArrayList<Row>();
    for (Map.Entry<String, PlantConstraintScope> expected : builder.expectedEquipment.entrySet()) {
      PlantConstraintScope scope = expected.getValue();
      ProcessEquipmentInterface equipment = builder.equipment.get(expected.getKey());
      Set<String> names = builder.expectedNames(scope);
      if (equipment == null) {
        assessed.add(Row.unavailable(scope, "", Status.MISSING_EQUIPMENT));
        addMissingRows(assessed, scope, names, Status.MISSING_CONSTRAINT);
        continue;
      }
      Map<String, ResolvedConstraint> resolved;
      try {
        resolved = EquipmentCapacityConstraintResolver.resolve(equipment);
      } catch (RuntimeException exception) {
        assessed.add(Row.unavailable(scope, "", Status.DISCOVERY_FAILED));
        addMissingRows(assessed, scope, names, Status.MISSING_CONSTRAINT);
        continue;
      }
      names.addAll(resolved.keySet());
      if (names.isEmpty()) {
        assessed.add(Row.unavailable(scope, "", isEnabled(equipment) ? Status.NO_CONSTRAINTS : Status.DISABLED));
      }
      for (String name : names) {
        String id = constraintId(scope, name);
        required.add(id);
        ResolvedConstraint selected = resolved.get(name);
        if (selected == null) {
          assessed.add(Row.unavailable(scope, name, Status.MISSING_CONSTRAINT));
        } else {
          PlantConstraintDefinition definition = builder.registry == null ? null : builder.registry.get(id);
          assessed.add(new Row(scope, name, selected, isEnabled(equipment), builder.bases.get(id),
              builder.registry != null, definition));
        }
      }
    }
    requiredConstraintIds = immutableList(required);
    rows = Collections.unmodifiableList(assessed);
    List<String> gaps = new ArrayList<String>();
    if (expectedEquipmentIds.isEmpty()) {
      gaps.add("EXPECTATIONS_NOT_DECLARED");
    }
    for (Row row : rows) {
      for (Status status : row.statuses) {
        if (status != Status.AVAILABLE && status != Status.DISABLED) {
          gaps.add(row.getIdentity() + "=" + status.name());
        }
      }
    }
    diagnostics = Collections.unmodifiableList(gaps);
    complete = gaps.isEmpty();
  }

  private static void addMissingRows(List<Row> rows, PlantConstraintScope scope, Set<String> names, Status status) {
    for (String name : names) {
      rows.add(Row.unavailable(scope, name, status));
    }
  }

  private static boolean isEnabled(ProcessEquipmentInterface equipment) {
    if (equipment instanceof ProcessEquipmentBaseClass) {
      return ((ProcessEquipmentBaseClass) equipment).isCapacityAnalysisEnabled();
    }
    return !(equipment instanceof CapacityConstrainedEquipment)
        || ((CapacityConstrainedEquipment) equipment).isCapacityAnalysisEnabled();
  }

  private static String constraintId(PlantConstraintScope scope, String name) {
    return scope.getStableId() + "#" + PlantConstraintScope.escape(name);
  }

  private static List<String> immutableList(Iterable<String> values) {
    List<String> result = new ArrayList<String>();
    for (String value : values) {
      result.add(value);
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * Starts an explicit-scope Java and JPype-friendly coverage builder.
   *
   * @param modelName stable process-model name
   * @return mutable builder; building captures an immutable report
   */
  public static Builder builder(String modelName) {
    return new Builder(modelName);
  }

  /** @return schema version */
  public String getSchemaVersion() {
    return schemaVersion;
  }

  /** @return explicit scope label; no whole-plant completeness is implied */
  public String getCoverageScope() {
    return coverageScope;
  }

  /** @return stable model name */
  public String getModelName() {
    return modelName;
  }

  /** @return captured registry digest, or empty when no registry was supplied */
  public String getRegistryIdentityDigest() {
    return registryIdentityDigest;
  }

  /** @return immutable sorted equipment scope identities */
  public List<String> getExpectedEquipmentIds() {
    return expectedEquipmentIds;
  }

  /** @return immutable sorted explicitly expected canonical constraint identities */
  public List<String> getExpectedConstraintIds() {
    return expectedConstraintIds;
  }

  /** @return immutable union of explicitly expected and discovered canonical constraint identities */
  public List<String> getRequiredConstraintIds() {
    return requiredConstraintIds;
  }

  /** @return immutable sampled rows in stable equipment and constraint order */
  public List<Row> getRows() {
    return Collections.unmodifiableList(new ArrayList<Row>(rows));
  }

  /** @return immutable list of evidence gaps in the declared scope */
  public List<String> getDiagnostics() {
    return diagnostics;
  }

  /** @return true only when the non-empty declared scope has complete rated evidence */
  public boolean isComplete() {
    return complete;
  }

  /** @return JSON with explicit nulls for unavailable numeric evidence */
  public String toJson() {
    return new GsonBuilder().serializeNulls().create().toJson(this);
  }

  /** Immutable evidence row with no mutable constraint or equipment reference. */
  public static final class Row implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String equipmentId;
    private final String constraintName;
    private final String qualifiedConstraintId;
    private final ConstraintOrigin origin;
    private final boolean enabled;
    private final String unit;
    private final String basis;
    private final String provenance;
    private final boolean minimumConstraint;
    private final Double currentValue;
    private final Double applicableLimit;
    private final Double normalizedUtilization;
    private final List<Status> statuses;

    private Row(PlantConstraintScope scope, String name, Status status) {
      equipmentId = scope.getStableId();
      constraintName = name;
      qualifiedConstraintId = name.isEmpty() ? "" : constraintId(scope, name);
      origin = ConstraintOrigin.UNKNOWN;
      enabled = status != Status.DISABLED;
      unit = "";
      basis = "";
      provenance = "";
      minimumConstraint = false;
      currentValue = null;
      applicableLimit = null;
      normalizedUtilization = null;
      statuses = Collections.singletonList(status);
    }

    private static Row unavailable(PlantConstraintScope scope, String name, Status status) {
      return new Row(scope, name, status);
    }

    private Row(PlantConstraintScope scope, String name, ResolvedConstraint resolved, boolean equipmentEnabled,
        String declaredBasis, boolean registrySupplied, PlantConstraintDefinition definition) {
      CapacityConstraint constraint = resolved.getConstraint();
      equipmentId = scope.getStableId();
      constraintName = name;
      qualifiedConstraintId = constraintId(scope, name);
      origin = resolved.getOrigin();
      enabled = equipmentEnabled && constraint.isEnabled();
      unit = PlantConstraintScope.safeText(constraint.getUnit());
      basis = definition == null ? PlantConstraintScope.safeText(declaredBasis) : definition.getBasis();
      String dataSource = PlantConstraintScope.safeText(constraint.getDataSource());
      String sourceReference = PlantConstraintScope.safeText(constraint.getSourceReference());
      String liveProvenance = isMissingSource(dataSource) ? sourceReference : dataSource;
      provenance = definition != null ? definition.getProvenance() : liveProvenance;
      minimumConstraint = constraint.isMinimumConstraint();
      List<Status> gaps = new ArrayList<Status>();
      if (registrySupplied && definition == null) {
        gaps.add(Status.MISSING_REGISTRATION);
      }
      if (definition != null && (!unit.equals(definition.getUnit()) || constraint.isEnabled() != definition.isEnabled()
          || constraint.getSeverity() != definition.getSeverity()
          || (!isMissingSource(liveProvenance) && !liveProvenance.equals(definition.getProvenance()))
          || definition.getLimitDirection() != (minimumConstraint ? PlantConstraintDefinition.LimitDirection.MINIMUM
              : PlantConstraintDefinition.LimitDirection.MAXIMUM)
          || (!PlantConstraintScope.safeText(declaredBasis).isEmpty() && !basis.equals(declaredBasis)))) {
        gaps.add(Status.METADATA_MISMATCH);
      }
      Double sampled = null;
      Double limit = finite(constraint.getDisplayDesignValue());
      if (limit != null && limit.doubleValue() == Double.MAX_VALUE) {
        limit = null;
      }
      if (enabled) {
        if (!constraint.hasCurrentValue()) {
          gaps.add(Status.MISSING_CURRENT_VALUE);
        } else {
          try {
            sampled = finite(constraint.getCurrentValue());
            if (sampled == null) {
              gaps.add(Status.NON_FINITE_CURRENT_VALUE);
            }
          } catch (RuntimeException exception) {
            gaps.add(Status.SAMPLE_FAILED);
          }
        }
        if (limit == null) {
          gaps.add(Status.MISSING_LIMIT);
        } else if (limit.doubleValue() <= 0.0) {
          gaps.add(Status.INVALID_LIMIT);
        }
        if (unit.isEmpty()) {
          gaps.add(Status.MISSING_UNIT);
        }
        if (basis.isEmpty()) {
          gaps.add(Status.MISSING_BASIS);
        }
        if (isMissingSource(provenance)) {
          gaps.add(Status.MISSING_PROVENANCE);
        }
        if (constraint.getSeverity() == CapacityConstraint.ConstraintSeverity.ADVISORY
            || constraint.getType() == CapacityConstraint.ConstraintType.DESIGN
            || (definition != null && definition.getCategory() == PlantConstraintDefinition.Category.SCREENING)
            || (constraint.getSource() == CapacityConstraint.ConstraintSource.DEFAULT && isMissingSource(dataSource))) {
          gaps.add(Status.SCREENING_ONLY);
        }
        if (sampled != null
            && ((constraint.hasValidityRange() && (sampled.doubleValue() < constraint.getValidityMinimum()
                || sampled.doubleValue() > constraint.getValidityMaximum()))
                || (definition != null && definition.hasValidityRange()
                    && (sampled.doubleValue() < definition.getValidityMinimum()
                        || sampled.doubleValue() > definition.getValidityMaximum())))) {
          gaps.add(Status.OUTSIDE_VALIDITY_RANGE);
        }
      } else {
        gaps.add(Status.DISABLED);
      }
      currentValue = sampled;
      applicableLimit = limit;
      normalizedUtilization = sampled != null && limit != null && limit.doubleValue() > 0.0
          ? finite(constraint.getUtilization(sampled.doubleValue()))
          : null;
      if (sampled != null && limit != null && limit.doubleValue() > 0.0 && normalizedUtilization == null) {
        gaps.add(Status.NON_FINITE_UTILIZATION);
      }
      if (gaps.isEmpty()) {
        gaps.add(Status.AVAILABLE);
      }
      statuses = Collections.unmodifiableList(gaps);
    }

    private static boolean isMissingSource(String source) {
      return source.isEmpty() || "not_set".equalsIgnoreCase(source) || "default".equalsIgnoreCase(source);
    }

    private static Double finite(double value) {
      return Double.isFinite(value) ? Double.valueOf(value) : null;
    }

    private static double valueOrNaN(Double value) {
      return value == null ? Double.NaN : value.doubleValue();
    }

    /** @return canonical equipment scope identity */
    public String getEquipmentId() {
      return equipmentId;
    }

    /** @return equipment-local constraint name, or empty for an equipment-level gap */
    public String getConstraintName() {
      return constraintName;
    }

    /** @return canonical constraint identity, or empty for an equipment-level gap */
    public String getQualifiedConstraintId() {
      return qualifiedConstraintId;
    }

    /** @return canonical row identity used in diagnostics */
    public String getIdentity() {
      return qualifiedConstraintId.isEmpty() ? equipmentId : qualifiedConstraintId;
    }

    /** @return direct or strategy origin */
    public ConstraintOrigin getOrigin() {
      return origin;
    }

    /** @return whether equipment and constraint are enabled */
    public boolean isEnabled() {
      return enabled;
    }

    /** @return physical unit, or empty when unset */
    public String getUnit() {
      return unit;
    }

    /** @return explicit measurement or rating basis, or empty when unset */
    public String getBasis() {
      return basis;
    }

    /** @return rating provenance, or empty/unset marker when missing */
    public String getProvenance() {
      return provenance;
    }

    /** @return true when values below the applicable limit are worse */
    public boolean isMinimumConstraint() {
      return minimumConstraint;
    }

    /** @return sampled current value, or NaN when unavailable */
    public double getCurrentValue() {
      return valueOrNaN(currentValue);
    }

    /** @return applicable installed limit, or NaN when unavailable */
    public double getApplicableLimit() {
      return valueOrNaN(applicableLimit);
    }

    /** @return dimensionless utilization, or NaN when unavailable */
    public double getNormalizedUtilization() {
      return valueOrNaN(normalizedUtilization);
    }

    /** @return immutable evidence diagnostics, or AVAILABLE for a complete row */
    public List<Status> getStatuses() {
      return statuses;
    }
  }

  /** Builder retaining live inputs only until capture; report instances retain none. */
  public static final class Builder {
    private final String modelName;
    private final Map<String, PlantConstraintScope> expectedEquipment = new TreeMap<String, PlantConstraintScope>();
    private final Set<String> expectedConstraints = new TreeSet<String>();
    private final Map<String, Set<String>> namesByEquipment = new TreeMap<String, Set<String>>();
    private final Map<String, ProcessEquipmentInterface> equipment = new TreeMap<String, ProcessEquipmentInterface>();
    private final Map<String, String> bases = new TreeMap<String, String>();
    private PlantConstraintRegistry registry;

    private Builder(String modelName) {
      this.modelName = PlantConstraintScope.requireText(modelName, "Model name");
    }

    /**
     * Declares an equipment identity even if the equipment object is unavailable.
     * 
     * @param areaName process area name
     * @param equipmentName equipment name
     * @return this builder
     */
    public Builder expectEquipment(String areaName, String equipmentName) {
      PlantConstraintScope scope = PlantConstraintScope.equipment(modelName, areaName, equipmentName);
      expectedEquipment.put(scope.getStableId(), scope);
      return this;
    }

    /**
     * Declares a required constraint independently of live constraint discovery.
     * 
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param constraintName equipment-local constraint name
     * @return this builder
     */
    public Builder expectConstraint(String areaName, String equipmentName, String constraintName) {
      expectEquipment(areaName, equipmentName);
      PlantConstraintScope scope = PlantConstraintScope.equipment(modelName, areaName, equipmentName);
      String name = PlantConstraintScope.requireText(constraintName, "Constraint name");
      expectedConstraints.add(constraintId(scope, name));
      if (!namesByEquipment.containsKey(scope.getStableId())) {
        namesByEquipment.put(scope.getStableId(), new TreeSet<String>());
      }
      namesByEquipment.get(scope.getStableId()).add(name);
      return this;
    }

    /**
     * Declares and supplies equipment whose constraints will be sampled during build.
     * 
     * @param areaName process area name
     * @param value equipment instance
     * @return this builder
     * @throws IllegalArgumentException when an identity has conflicting equipment instances
     */
    public Builder equipment(String areaName, ProcessEquipmentInterface value) {
      if (value == null) {
        throw new IllegalArgumentException("Equipment is required");
      }
      expectEquipment(areaName, value.getName());
      String id = PlantConstraintScope.equipment(modelName, areaName, value.getName()).getStableId();
      if (equipment.containsKey(id) && equipment.get(id) != value) {
        throw new IllegalArgumentException("Duplicate equipment identity " + id);
      }
      equipment.put(id, value);
      return this;
    }

    /**
     * Declares a required constraint and its explicit measurement or rating basis.
     * 
     * @param areaName process area name
     * @param equipmentName equipment name
     * @param constraintName equipment-local constraint name
     * @param value non-empty measurement or rating basis
     * @return this builder
     */
    public Builder basis(String areaName, String equipmentName, String constraintName, String value) {
      expectConstraint(areaName, equipmentName, constraintName);
      bases.put(
          constraintId(PlantConstraintScope.equipment(modelName, areaName, equipmentName),
              PlantConstraintScope.requireText(constraintName, "Constraint name")),
          PlantConstraintScope.requireText(value, "Constraint basis"));
      return this;
    }

    /**
     * Requires matching registrations for every constraint in the declared equipment scope.
     * 
     * @param value registry providing basis and provenance metadata
     * @return this builder
     */
    public Builder registry(PlantConstraintRegistry value) {
      if (value == null) {
        throw new IllegalArgumentException("Plant constraint registry is required");
      }
      registry = value;
      return this;
    }

    private Set<String> expectedNames(PlantConstraintScope scope) {
      Set<String> names = namesByEquipment.get(scope.getStableId());
      return names == null ? new TreeSet<String>() : new TreeSet<String>(names);
    }

    /** @return immutable report after sampling every selected enabled constraint at most once */
    public UtilizationCoverageReport build() {
      return new UtilizationCoverageReport(this);
    }
  }
}
