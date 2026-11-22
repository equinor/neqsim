package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.separator.ThreePhaseSeparator;
import neqsim.process.mechanicaldesign.separator.SeparatorMechanicalDesign;
import neqsim.thermo.system.SystemInterface;

/**
 * Immutable, fail-closed separator constraint evidence for one completed plant calculation.
 *
 * <p>
 * This adapter samples the existing separator rating calculations without retaining the mutable separator or its
 * callbacks. It requires explicit vessel geometry and mechanical-design elevations so that the convenience fallbacks in
 * {@link Separator} cannot be mistaken for installed design evidence. Missing phases, ratings, geometry, calculation
 * identity, or convergence are unavailable evidence rather than zero utilization.
 * </p>
 *
 * <p>
 * The supported profiles cover gas load, K-value at HLL, droplet cut size, inlet momentum, liquid residence,
 * liquid-level inventory, and (for three-phase vessels) oil-water interface settling. Carry-over/carry-under and
 * slug-volume qualification require a separately validated provider or measured constraint and are deliberately outside
 * this adapter.
 * </p>
 */
public final class PlantSeparatorEvidence implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_VERSION = "1.0";

  /** Supported separator service profile. */
  public enum Profile {
    /** Gas scrubber with gas-capacity and inlet-momentum evidence. */
    GAS_SCRUBBER,
    /** Two-phase gas/oil separator. */
    TWO_PHASE_OIL,
    /** Two-phase gas/water separator. */
    TWO_PHASE_WATER,
    /** Three-phase gas/oil/water separator with interface-settling evidence. */
    THREE_PHASE
  }

  /** Overall evidence status. */
  public enum Status {
    /** Every profile-required observation is complete for the exact calculation. */
    AVAILABLE,
    /** At least one required observation is missing, stale, invalid, or unconverged. */
    INCOMPLETE
  }

  private enum Metric {
    GAS_LOAD("gas-load-factor", "gasLoadFactor", "m/s", "Souders-Brown gas load at design gas area",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    K_VALUE("k-value-at-hll", "kValue", "m/s", "Souders-Brown K-value at explicit HLL",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    DROPLET_CUT("droplet-cut-size-at-hll", "dropletCutSize", "µm",
        "Stokes droplet cut size at explicit HLL and effective gas length",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    INLET_MOMENTUM("inlet-momentum", "inletMomentum", "Pa", "mixture momentum flux at explicit inlet-nozzle diameter",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    OIL_RETENTION("oil-retention-time", "oilRetentionTime", "min", "oil residence between explicit NIL and NLL",
        PlantConstraintDefinition.LimitDirection.MINIMUM),
    WATER_RETENTION("water-retention-time", "waterRetentionTime", "min", "water residence below explicit NIL",
        PlantConstraintDefinition.LimitDirection.MINIMUM),
    LIQUID_LEVEL("liquid-level-inventory", null, "fraction", "live liquid height divided by vessel diameter",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    INTERFACE_SETTLING("interface-settling-time", null, "min",
        "150 micrometre water-droplet settling time through the explicit oil layer",
        PlantConstraintDefinition.LimitDirection.MINIMUM);

    private final String id;
    private final String equipmentConstraintName;
    private final String unit;
    private final String basis;
    private final PlantConstraintDefinition.LimitDirection direction;

    Metric(String id, String equipmentConstraintName, String unit, String basis,
        PlantConstraintDefinition.LimitDirection direction) {
      this.id = id;
      this.equipmentConstraintName = equipmentConstraintName;
      this.unit = unit;
      this.basis = basis;
      this.direction = direction;
    }
  }

  private final String calculationId;
  private final String separatorName;
  private final String areaName;
  private final Profile profile;
  private final String provenance;
  private final boolean convergenceComplete;
  private final Status status;
  private final List<PlantConstraintDefinition> definitions;
  private final List<PlantConstraintSample> samples;
  private final List<String> diagnostics;

  private PlantSeparatorEvidence(Builder builder) {
    calculationId = PlantConstraintScope.requireText(builder.calculationId, "Calculation id");
    provenance = PlantConstraintScope.requireText(builder.provenance, "Separator evidence provenance");
    areaName = PlantConstraintScope.requireText(builder.areaName, "Area name");
    separatorName = PlantConstraintScope.requireText(builder.separator.getName(), "Separator name");
    profile = builder.profile;
    convergenceComplete = builder.convergenceComplete;

    List<String> findings = validateState(builder);
    List<Metric> metrics = metricsFor(profile);
    List<PlantConstraintDefinition> capturedDefinitions = new ArrayList<PlantConstraintDefinition>();
    List<PlantConstraintSample> capturedSamples = new ArrayList<PlantConstraintSample>();
    PlantConstraintScope scope = PlantConstraintScope.equipment(builder.modelName, areaName, separatorName);
    Map<String, CapacityConstraint> registered = builder.separator.getCapacityConstraints();
    boolean stateUsable = findings.isEmpty();

    for (Metric metric : metrics) {
      CapacityConstraint source = metric.equipmentConstraintName == null ? null
          : registered.get(metric.equipmentConstraintName);
      PlantConstraintDefinition definition = definition(metric, scope, source, provenance);
      capturedDefinitions.add(definition);
      PlantConstraintSample sample = capture(metric, definition, source, builder, stateUsable);
      capturedSamples.add(sample);
      if (sample.getStatus() != PlantConstraintSample.SampleStatus.AVAILABLE) {
        findings.add(metric.id + "=" + sample.getStatus().name() + diagnosticSuffix(sample.getDiagnostic()));
      }
    }
    Collections.sort(capturedDefinitions);
    Collections.sort(capturedSamples,
        (first, second) -> first.getQualifiedConstraintId().compareTo(second.getQualifiedConstraintId()));
    definitions = Collections.unmodifiableList(capturedDefinitions);
    samples = Collections.unmodifiableList(capturedSamples);
    diagnostics = Collections.unmodifiableList(new ArrayList<String>(findings));
    status = findings.isEmpty() ? Status.AVAILABLE : Status.INCOMPLETE;
  }

  /**
   * Starts a callback-free separator evidence builder.
   *
   * @param modelName stable process-model name
   * @param areaName stable process-area name
   * @param calculationId exact completed process calculation UUID string
   * @param separator solved separator to sample immediately
   * @param profile explicit separator service profile
   * @param provenance source of the installed limits and completed process state
   * @return new separator evidence builder
   */
  public static Builder builder(String modelName, String areaName, String calculationId, Separator separator,
      Profile profile, String provenance) {
    return new Builder(modelName, areaName, calculationId, separator, profile, provenance);
  }

  private static List<String> validateState(Builder builder) {
    List<String> findings = new ArrayList<String>();
    Separator separator = builder.separator;
    if (!builder.convergenceComplete) {
      findings.add("INCOMPLETE_CONVERGENCE");
    }
    if (!separator.solved()) {
      findings.add("SEPARATOR_NOT_SOLVED");
    }
    if (separator.getCalculationIdentifier() == null
        || !builder.calculationId.equals(separator.getCalculationIdentifier().toString())) {
      findings.add("STALE_CALCULATION_IDENTITY");
    }
    SystemInterface fluid = separator.getThermoSystem();
    if (fluid == null || !fluid.hasPhaseType("gas")) {
      findings.add("GAS_PHASE_MISSING");
    }
    if (!finitePositive(separator.getInternalDiameter()) || !finitePositive(separator.getSeparatorLength())) {
      findings.add("EXPLICIT_VESSEL_GEOMETRY_MISSING");
    }
    if (!("horizontal".equals(separator.getOrientation()) || "vertical".equals(separator.getOrientation()))) {
      findings.add("INVALID_SEPARATOR_ORIENTATION");
    }
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    if (design == null || !finitePositive(design.getInletNozzleID())) {
      findings.add("EXPLICIT_INLET_NOZZLE_MISSING");
    }
    if (profileNeedsHll(builder.profile)
        && (design == null || !finitePositive(design.getHLL()) || !finitePositive(design.getEffectiveLengthGas()))) {
      findings.add("EXPLICIT_HLL_OR_GAS_LENGTH_MISSING");
    }
    if (profileNeedsLiquidGeometry(builder.profile)
        && (design == null || !finitePositive(design.getNLL()) || !finitePositive(design.getNIL())
            || design.getNLL() <= design.getNIL() || !finitePositive(design.getEffectiveLengthLiquid()))) {
      findings.add("EXPLICIT_LIQUID_LEVEL_GEOMETRY_MISSING");
    }
    if (profileNeedsOil(builder.profile) && (fluid == null || !fluid.hasPhaseType("oil"))) {
      findings.add("OIL_PHASE_MISSING");
    }
    if (profileNeedsWater(builder.profile) && (fluid == null || !fluid.hasPhaseType("aqueous"))) {
      findings.add("WATER_PHASE_MISSING");
    }
    if (builder.profile == Profile.THREE_PHASE && !(separator instanceof ThreePhaseSeparator)) {
      findings.add("THREE_PHASE_SEPARATOR_TYPE_REQUIRED");
    }
    if (!Double.isFinite(builder.maximumLiquidLevelFraction) || builder.maximumLiquidLevelFraction <= 0.0
        || builder.maximumLiquidLevelFraction > 1.0) {
      findings.add("LIQUID_LEVEL_LIMIT_MISSING");
    }
    if (builder.profile == Profile.THREE_PHASE && !finitePositive(builder.minimumInterfaceSettlingMinutes)) {
      findings.add("INTERFACE_SETTLING_LIMIT_MISSING");
    }
    return findings;
  }

  private static List<Metric> metricsFor(Profile profile) {
    List<Metric> result = new ArrayList<Metric>();
    result.add(Metric.GAS_LOAD);
    result.add(Metric.INLET_MOMENTUM);
    result.add(Metric.LIQUID_LEVEL);
    if (profile != Profile.GAS_SCRUBBER) {
      result.add(Metric.K_VALUE);
      result.add(Metric.DROPLET_CUT);
    }
    if (profileNeedsOil(profile)) {
      result.add(Metric.OIL_RETENTION);
    }
    if (profileNeedsWater(profile)) {
      result.add(Metric.WATER_RETENTION);
    }
    if (profile == Profile.THREE_PHASE) {
      result.add(Metric.INTERFACE_SETTLING);
    }
    return result;
  }

  private static boolean profileNeedsHll(Profile profile) {
    return profile != Profile.GAS_SCRUBBER;
  }

  private static boolean profileNeedsLiquidGeometry(Profile profile) {
    return profile == Profile.TWO_PHASE_OIL || profile == Profile.TWO_PHASE_WATER || profile == Profile.THREE_PHASE;
  }

  private static boolean profileNeedsOil(Profile profile) {
    return profile == Profile.TWO_PHASE_OIL || profile == Profile.THREE_PHASE;
  }

  private static boolean profileNeedsWater(Profile profile) {
    return profile == Profile.TWO_PHASE_WATER || profile == Profile.THREE_PHASE;
  }

  private static PlantConstraintDefinition definition(Metric metric, PlantConstraintScope scope,
      CapacityConstraint source, String provenance) {
    ConstraintSeverity severity = source == null ? ConstraintSeverity.HARD : source.getSeverity();
    return PlantConstraintDefinition.builder(metric.id, scope).limitDirection(metric.direction)
        .category(PlantConstraintDefinition.Category.DESIGN).severity(severity).unit(metric.unit).basis(metric.basis)
        .provenance(provenance).owner("separator process and mechanical design")
        .reference(source == null ? "caller-declared installed limit" : safeText(source.getSourceReference()))
        .calculationMethod("immutable strict post-solve separator adapter").build();
  }

  private static PlantConstraintSample capture(Metric metric, PlantConstraintDefinition definition,
      CapacityConstraint source, Builder builder, boolean stateUsable) {
    if (!stateUsable) {
      PlantConstraintSample.SampleStatus status = builder.convergenceComplete ? PlantConstraintSample.SampleStatus.STALE
          : PlantConstraintSample.SampleStatus.INCOMPLETE_CONVERGENCE;
      return unavailable(definition, builder.calculationId, status, builder.provenance,
          "Separator state is not usable for strict evidence");
    }
    double limit = limit(metric, source, builder);
    if (!finitePositive(limit)) {
      return unavailable(definition, builder.calculationId, PlantConstraintSample.SampleStatus.NOT_CALCULABLE,
          builder.provenance, "Finite positive installed limit is required");
    }
    try {
      double value = value(metric, builder.separator);
      if (!Double.isFinite(value) || value < 0.0
          || metric.direction == PlantConstraintDefinition.LimitDirection.MINIMUM && value <= 0.0) {
        return unavailable(definition, builder.calculationId, PlantConstraintSample.SampleStatus.NON_FINITE_VALUE,
            builder.provenance, "Separator observation is unavailable or non-finite");
      }
      return sample(definition, builder.calculationId, value, limit, builder.provenance);
    } catch (RuntimeException exception) {
      return unavailable(definition, builder.calculationId, PlantConstraintSample.SampleStatus.EXCEPTION,
          builder.provenance, exception.getClass().getSimpleName() + ": " + safeText(exception.getMessage()));
    }
  }

  private static double limit(Metric metric, CapacityConstraint source, Builder builder) {
    if (metric == Metric.LIQUID_LEVEL) {
      return builder.maximumLiquidLevelFraction;
    }
    if (metric == Metric.INTERFACE_SETTLING) {
      return builder.minimumInterfaceSettlingMinutes;
    }
    if (source == null) {
      return Double.NaN;
    }
    return source.getDisplayDesignValue();
  }

  private static double value(Metric metric, Separator separator) {
    SeparatorMechanicalDesign design = separator.getMechanicalDesign();
    switch (metric) {
    case GAS_LOAD:
      return separator.getGasLoadFactor();
    case K_VALUE:
      return separator.calcKValue(design.getHLL());
    case DROPLET_CUT:
      return separator.calcDropletCutSize(design.getEffectiveLengthGas(),
          separator.getInternalDiameter() - design.getHLL()) * 1.0e6;
    case INLET_MOMENTUM:
      return separator.calcInletMomentumFlux(design.getInletNozzleID());
    case OIL_RETENTION:
      return separator.calcOilRetentionTime();
    case WATER_RETENTION:
      return separator.calcWaterRetentionTime();
    case LIQUID_LEVEL:
      return separator.getLiquidLevel();
    case INTERFACE_SETTLING:
      return ((ThreePhaseSeparator) separator).calcInterfaceSettlingTime();
    default:
      return Double.NaN;
    }
  }

  private static PlantConstraintSample sample(PlantConstraintDefinition definition, String calculationId, double value,
      double limit, String provenance) {
    boolean minimum = definition.getLimitDirection() == PlantConstraintDefinition.LimitDirection.MINIMUM;
    double utilization = minimum ? limit / value : value / limit;
    double margin = minimum ? value - limit : limit - value;
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId).values(value, limit)
        .normalized(utilization, utilization - 1.0).physical(margin, Math.max(0.0, -margin)).unit(definition.getUnit())
        .basis(definition.getBasis()).provenance(provenance).build();
  }

  private static PlantConstraintSample unavailable(PlantConstraintDefinition definition, String calculationId,
      PlantConstraintSample.SampleStatus status, String provenance, String diagnostic) {
    return PlantConstraintSample.builder(definition.getQualifiedId(), calculationId).status(status)
        .unit(definition.getUnit()).basis(definition.getBasis()).provenance(provenance).diagnostic(diagnostic).build();
  }

  private static boolean finitePositive(double value) {
    return Double.isFinite(value) && value > 0.0;
  }

  private static String safeText(String value) {
    return value == null ? "" : value;
  }

  private static String diagnosticSuffix(String diagnostic) {
    return diagnostic == null || diagnostic.isEmpty() ? "" : ":" + diagnostic;
  }

  /** @return exact completed process calculation identity */
  public String getCalculationId() {
    return calculationId;
  }

  /** @return stable separator equipment name */
  public String getSeparatorName() {
    return separatorName;
  }

  /** @return stable process area name */
  public String getAreaName() {
    return areaName;
  }

  /** @return declared separator service profile */
  public Profile getProfile() {
    return profile;
  }

  /** @return evidence provenance supplied by the caller */
  public String getProvenance() {
    return provenance;
  }

  /** @return overall evidence status */
  public Status getStatus() {
    return status;
  }

  /** @return true only when every profile-required observation is available */
  public boolean isComplete() {
    return status == Status.AVAILABLE;
  }

  /** @return true only when evidence is complete and no hard limit is violated */
  public boolean isFeasible() {
    return toPlantUtilizationSnapshot().isFeasible();
  }

  /** @return immutable deterministic constraint definitions */
  public List<PlantConstraintDefinition> getDefinitions() {
    return Collections.unmodifiableList(new ArrayList<PlantConstraintDefinition>(definitions));
  }

  /** @return immutable deterministic exact-calculation samples */
  public List<PlantConstraintSample> getSamples() {
    return Collections.unmodifiableList(new ArrayList<PlantConstraintSample>(samples));
  }

  /** @return immutable fail-closed diagnostics */
  public List<String> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
  }

  /**
   * Builds a complete plant utilization snapshot from the frozen separator evidence.
   *
   * @return immutable utilization snapshot
   */
  public PlantUtilizationSnapshot toPlantUtilizationSnapshot() {
    PlantConstraintRegistry registry = new PlantConstraintRegistry();
    for (PlantConstraintDefinition definition : definitions) {
      registry.register(definition);
    }
    PlantUtilizationSnapshot.Builder snapshot = PlantUtilizationSnapshot.builder(registry, calculationId)
        .convergenceComplete(convergenceComplete);
    for (PlantConstraintSample sample : samples) {
      snapshot.sample(sample);
    }
    return snapshot.build();
  }

  /** @return standards-compliant JSON with null for unavailable numbers */
  public String toJson() {
    JsonObject root = new JsonObject();
    root.addProperty("schemaVersion", SCHEMA_VERSION);
    root.addProperty("calculationId", calculationId);
    root.addProperty("areaName", areaName);
    root.addProperty("separatorName", separatorName);
    root.addProperty("profile", profile.name());
    root.addProperty("provenance", provenance);
    root.addProperty("convergenceComplete", convergenceComplete);
    root.addProperty("status", status.name());
    JsonArray findingRows = new JsonArray();
    for (String diagnostic : diagnostics) {
      findingRows.add(diagnostic);
    }
    root.add("diagnostics", findingRows);
    JsonArray evidenceRows = new JsonArray();
    for (PlantConstraintEvidence evidence : toPlantUtilizationSnapshot().getEvidence()) {
      JsonObject row = new JsonObject();
      row.addProperty("qualifiedConstraintId", evidence.getQualifiedConstraintId());
      row.addProperty("coverageStatus", evidence.getCoverageStatus().name());
      row.addProperty("operatingStatus", evidence.getOperatingStatus().name());
      row.addProperty("unit", evidence.getDefinition().getUnit());
      row.addProperty("basis", evidence.getDefinition().getBasis());
      addNumber(row, "sampledValue",
          evidence.getSample() == null ? Double.NaN : evidence.getSample().getSampledValue());
      addNumber(row, "applicableLimit",
          evidence.getSample() == null ? Double.NaN : evidence.getSample().getApplicableLimit());
      addNumber(row, "normalizedUtilization", evidence.getNormalizedUtilization());
      addNumber(row, "physicalMargin",
          evidence.getSample() == null ? Double.NaN : evidence.getSample().getPhysicalMargin());
      row.addProperty("diagnostic", evidence.getDiagnostic());
      evidenceRows.add(row);
    }
    root.add("evidence", evidenceRows);
    return root.toString();
  }

  private static void addNumber(JsonObject target, String name, double value) {
    if (Double.isFinite(value)) {
      target.addProperty(name, value);
    } else {
      target.add(name, JsonNull.INSTANCE);
    }
  }

  /** Callback-free JPype-friendly separator evidence builder. */
  public static final class Builder {
    private final String modelName;
    private final String areaName;
    private final String calculationId;
    private final Separator separator;
    private final Profile profile;
    private final String provenance;
    private boolean convergenceComplete;
    private double maximumLiquidLevelFraction = Double.NaN;
    private double minimumInterfaceSettlingMinutes = Double.NaN;

    private Builder(String modelName, String areaName, String calculationId, Separator separator, Profile profile,
        String provenance) {
      this.modelName = PlantConstraintScope.requireText(modelName, "Model name");
      this.areaName = PlantConstraintScope.requireText(areaName, "Area name");
      this.calculationId = PlantConstraintScope.requireText(calculationId, "Calculation id");
      if (separator == null) {
        throw new IllegalArgumentException("Separator is required");
      }
      if (profile == null) {
        throw new IllegalArgumentException("Separator profile is required");
      }
      this.separator = separator;
      this.profile = profile;
      this.provenance = PlantConstraintScope.requireText(provenance, "Separator evidence provenance");
    }

    /**
     * Declares the maximum acceptable live liquid-level fraction.
     *
     * @param value finite fraction in (0, 1]
     * @return this builder
     */
    public Builder maximumLiquidLevelFraction(double value) {
      maximumLiquidLevelFraction = value;
      return this;
    }

    /**
     * Declares the minimum acceptable oil-water interface settling time.
     *
     * @param value positive time in minutes
     * @return this builder
     */
    public Builder minimumInterfaceSettlingMinutes(double value) {
      minimumInterfaceSettlingMinutes = value;
      return this;
    }

    /**
     * Declares whether the complete isolated process candidate converged.
     *
     * @param value true only after all applicable process convergence checks pass
     * @return this builder
     */
    public Builder convergenceComplete(boolean value) {
      convergenceComplete = value;
      return this;
    }

    /** @return immutable separator evidence */
    public PlantSeparatorEvidence build() {
      return new PlantSeparatorEvidence(this);
    }
  }
}
