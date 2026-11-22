package neqsim.process.util.optimizer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import neqsim.process.equipment.capacity.CapacityConstraint.ConstraintSeverity;
import neqsim.process.equipment.pipeline.PipeBeggsAndBrills;

/**
 * Immutable, fail-closed piping constraint evidence for one completed plant calculation.
 *
 * <p>
 * This adapter snapshots the existing steady-state {@link PipeBeggsAndBrills} pressure, temperature, and
 * mixture-velocity profiles without retaining the mutable pipe. Installed limits are always supplied explicitly by the
 * caller. The class does not promote the pipe's convenience defaults, API RP 14E, FIV, AIV, hydrate, wax, or other
 * screening correlations to verified design capacity.
 * </p>
 *
 * <p>
 * A complete snapshot requires exact calculation identity, a converged process candidate, caller-confirmed geometry
 * provenance, finite profile values and all six hydraulic/thermal limits. Missing or stale observations remain
 * unavailable rather than becoming zero utilization.
 * </p>
 */
public final class PlantPipelineEvidence implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final String SCHEMA_VERSION = "1.0";

  /** Overall strict evidence status. */
  public enum Status {
    /** Every required hydraulic and thermal observation is available. */
    AVAILABLE,
    /** At least one observation, limit, identity, or validity requirement is incomplete. */
    INCOMPLETE
  }

  /** Required piping restriction families. */
  public enum Metric {
    /** Highest absolute pressure in the solved profile. */
    MAXIMUM_PRESSURE("maximum-pressure", "bara", "maximum absolute pressure along solved pipe profile",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    /** Total inlet-to-outlet pressure loss. */
    PRESSURE_DROP("pressure-drop", "bar", "solved inlet-to-outlet pressure difference",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    /** Pressure delivered at the declared receiving boundary. */
    RECEIVING_PRESSURE("receiving-pressure", "bara", "absolute pressure at solved receiving boundary",
        PlantConstraintDefinition.LimitDirection.MINIMUM),
    /** Highest mixture superficial velocity in the solved profile. */
    MIXTURE_VELOCITY("mixture-velocity", "m/s", "maximum mixture superficial velocity along solved profile",
        PlantConstraintDefinition.LimitDirection.MAXIMUM),
    /** Lowest bulk-fluid temperature in the solved profile. */
    MINIMUM_TEMPERATURE("minimum-temperature", "°C", "minimum bulk-fluid temperature along solved profile",
        PlantConstraintDefinition.LimitDirection.MINIMUM),
    /** Highest bulk-fluid temperature in the solved profile. */
    MAXIMUM_TEMPERATURE("maximum-temperature", "°C", "maximum bulk-fluid temperature along solved profile",
        PlantConstraintDefinition.LimitDirection.MAXIMUM);

    private final String id;
    private final String unit;
    private final String basis;
    private final PlantConstraintDefinition.LimitDirection direction;

    Metric(String id, String unit, String basis, PlantConstraintDefinition.LimitDirection direction) {
      this.id = id;
      this.unit = unit;
      this.basis = basis;
      this.direction = direction;
    }
  }

  /**
   * Immutable location of the observation used for one constraint sample.
   */
  public static final class ExtremumLocation implements Serializable, Comparable<ExtremumLocation> {
    private static final long serialVersionUID = 1L;
    private final Metric metric;
    private final int nodeIndex;
    private final double distanceMetres;

    private ExtremumLocation(Metric metric, int nodeIndex, double distanceMetres) {
      this.metric = metric;
      this.nodeIndex = nodeIndex;
      this.distanceMetres = distanceMetres;
    }

    /** @return restriction family */
    public Metric getMetric() {
      return metric;
    }

    /** @return zero-based solved profile node */
    public int getNodeIndex() {
      return nodeIndex;
    }

    /** @return distance from the pipe inlet in metres */
    public double getDistanceMetres() {
      return distanceMetres;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(ExtremumLocation other) {
      return metric.name().compareTo(other.metric.name());
    }
  }

  private static final class Observation {
    private final double value;
    private final int nodeIndex;
    private final double distanceMetres;

    private Observation(double value, int nodeIndex, double distanceMetres) {
      this.value = value;
      this.nodeIndex = nodeIndex;
      this.distanceMetres = distanceMetres;
    }
  }

  private final String calculationId;
  private final String pipelineName;
  private final String areaName;
  private final String provenance;
  private final boolean convergenceComplete;
  private final Status status;
  private final List<PlantConstraintDefinition> definitions;
  private final List<PlantConstraintSample> samples;
  private final List<ExtremumLocation> locations;
  private final List<String> diagnostics;

  private PlantPipelineEvidence(Builder builder) {
    calculationId = PlantConstraintScope.requireText(builder.calculationId, "Calculation id");
    provenance = PlantConstraintScope.requireText(builder.provenance, "Pipeline evidence provenance");
    areaName = PlantConstraintScope.requireText(builder.areaName, "Area name");
    pipelineName = PlantConstraintScope.requireText(builder.pipeline.getName(), "Pipeline name");
    convergenceComplete = builder.convergenceComplete;

    List<String> findings = validateState(builder);
    PlantConstraintScope scope = PlantConstraintScope.equipment(builder.modelName, areaName, pipelineName);
    List<PlantConstraintDefinition> capturedDefinitions = new ArrayList<PlantConstraintDefinition>();
    List<PlantConstraintSample> capturedSamples = new ArrayList<PlantConstraintSample>();
    List<ExtremumLocation> capturedLocations = new ArrayList<ExtremumLocation>();
    boolean stateUsable = findings.isEmpty();

    for (Metric metric : Metric.values()) {
      PlantConstraintDefinition definition = definition(metric, scope, provenance);
      capturedDefinitions.add(definition);
      PlantConstraintSample sample = capture(metric, definition, builder, stateUsable, findings, capturedLocations);
      capturedSamples.add(sample);
      if (sample.getStatus() != PlantConstraintSample.SampleStatus.AVAILABLE) {
        findings.add(metric.id + "=" + sample.getStatus().name() + diagnosticSuffix(sample.getDiagnostic()));
      }
    }
    Collections.sort(capturedDefinitions);
    Collections.sort(capturedSamples,
        (first, second) -> first.getQualifiedConstraintId().compareTo(second.getQualifiedConstraintId()));
    Collections.sort(capturedLocations);
    definitions = Collections.unmodifiableList(capturedDefinitions);
    samples = Collections.unmodifiableList(capturedSamples);
    locations = Collections.unmodifiableList(capturedLocations);
    diagnostics = Collections.unmodifiableList(new ArrayList<String>(findings));
    status = findings.isEmpty() ? Status.AVAILABLE : Status.INCOMPLETE;
  }

  /**
   * Starts a callback-free piping evidence builder.
   *
   * @param modelName stable process-model name
   * @param areaName stable process-area name
   * @param calculationId exact completed process calculation UUID string
   * @param pipeline solved Beggs-Brill pipeline to sample immediately
   * @param provenance source of installed limits, geometry, and process state
   * @return new piping evidence builder
   */
  public static Builder builder(String modelName, String areaName, String calculationId, PipeBeggsAndBrills pipeline,
      String provenance) {
    return new Builder(modelName, areaName, calculationId, pipeline, provenance);
  }

  private static List<String> validateState(Builder builder) {
    List<String> findings = new ArrayList<String>();
    PipeBeggsAndBrills pipeline = builder.pipeline;
    if (!builder.convergenceComplete) {
      findings.add("INCOMPLETE_CONVERGENCE");
    }
    if (!pipeline.solved()) {
      findings.add("PIPELINE_NOT_SOLVED");
    }
    if (pipeline.getCalculationIdentifier() == null
        || !builder.calculationId.equals(pipeline.getCalculationIdentifier().toString())) {
      findings.add("STALE_CALCULATION_IDENTITY");
    }
    if (!builder.geometryVerified) {
      findings.add("GEOMETRY_PROVENANCE_NOT_VERIFIED");
    }
    if (!finitePositive(pipeline.getLength()) || !finitePositive(pipeline.getDiameter())
        || !Double.isFinite(pipeline.getPipeWallRoughness()) || pipeline.getPipeWallRoughness() < 0.0) {
      findings.add("EXPLICIT_PIPE_GEOMETRY_MISSING");
    }
    if (!limitsValid(builder)) {
      findings.add("INSTALLED_LIMIT_SET_INCOMPLETE");
    }
    String profileDiagnostic = profileDiagnostic(pipeline);
    if (!profileDiagnostic.isEmpty()) {
      findings.add("SOLVED_PROFILE_INCOMPLETE:" + profileDiagnostic);
    }
    return findings;
  }

  private static boolean limitsValid(Builder builder) {
    return finitePositive(builder.maximumPressureBara) && finitePositive(builder.maximumPressureDropBar)
        && finitePositive(builder.minimumReceivingPressureBara)
        && finitePositive(builder.maximumMixtureVelocityMetresPerSecond)
        && Double.isFinite(builder.minimumTemperatureCelsius) && Double.isFinite(builder.maximumTemperatureCelsius)
        && builder.maximumTemperatureCelsius > builder.minimumTemperatureCelsius;
  }

  private static String profileDiagnostic(PipeBeggsAndBrills pipeline) {
    try {
      double[] pressure = pipeline.getPressureProfile();
      double[] temperature = pipeline.getTemperatureProfile();
      List<Double> velocity = pipeline.getMixtureSuperficialVelocityProfile();
      List<Double> distance = pipeline.getLengthProfile();
      if (pressure.length < 2 || temperature.length != 1 && temperature.length != pressure.length
          || velocity.size() != pressure.length || distance.size() != pressure.length) {
        return "PROFILE_SIZE_MISMATCH pressure=" + pressure.length + ",temperature=" + temperature.length + ",velocity="
            + velocity.size() + ",distance=" + distance.size();
      }
      double previousDistance = -1.0;
      for (int index = 0; index < pressure.length; index++) {
        double currentDistance = distance.get(index);
        if (!finitePositiveOrZero(pressure[index]) || pressure[index] <= 0.0
            || !finitePositiveOrZero(velocity.get(index)) || !finitePositiveOrZero(currentDistance)
            || currentDistance < previousDistance) {
          return "NON_FINITE_OR_NON_MONOTONIC_PROFILE_AT_NODE=" + index;
        }
        previousDistance = currentDistance;
      }
      for (int index = 0; index < temperature.length; index++) {
        if (!finitePositiveOrZero(temperature[index]) || temperature[index] <= 0.0) {
          return "NON_FINITE_TEMPERATURE_PROFILE_AT_NODE=" + index;
        }
      }
      if (Math.abs(distance.get(distance.size() - 1) - pipeline.getLength()) > Math.max(1.0e-9,
          pipeline.getLength() * 1.0e-9)) {
        return "PROFILE_END_DISTANCE_MISMATCH";
      }
      return "";
    } catch (RuntimeException exception) {
      return exception.getClass().getSimpleName() + ":" + safeText(exception.getMessage());
    }
  }

  private static PlantConstraintDefinition definition(Metric metric, PlantConstraintScope scope, String provenance) {
    PlantConstraintDefinition.Category category = metric == Metric.RECEIVING_PRESSURE
        ? PlantConstraintDefinition.Category.OPERATING
        : PlantConstraintDefinition.Category.DESIGN;
    return PlantConstraintDefinition.builder(metric.id, scope).limitDirection(metric.direction).category(category)
        .severity(ConstraintSeverity.HARD).unit(metric.unit).basis(metric.basis).provenance(provenance)
        .owner("piping process and mechanical design").reference("caller-declared installed piping limit")
        .calculationMethod("immutable strict post-solve Beggs-Brill profile adapter").build();
  }

  private static PlantConstraintSample capture(Metric metric, PlantConstraintDefinition definition, Builder builder,
      boolean stateUsable, List<String> findings, List<ExtremumLocation> capturedLocations) {
    if (!stateUsable) {
      PlantConstraintSample.SampleStatus sampleStatus = unavailableStatus(findings);
      return unavailable(definition, builder.calculationId, sampleStatus, builder.provenance,
          "Pipeline state is not usable for strict evidence");
    }
    try {
      Observation observation = observe(metric, builder.pipeline);
      double limit = limit(metric, builder);
      if (!Double.isFinite(observation.value) || !Double.isFinite(limit)) {
        return unavailable(definition, builder.calculationId, PlantConstraintSample.SampleStatus.NON_FINITE_VALUE,
            builder.provenance, "Pipeline observation or installed limit is non-finite");
      }
      capturedLocations.add(new ExtremumLocation(metric, observation.nodeIndex, observation.distanceMetres));
      return sample(definition, builder.calculationId, observation.value, limit, builder.provenance);
    } catch (RuntimeException exception) {
      return unavailable(definition, builder.calculationId, PlantConstraintSample.SampleStatus.EXCEPTION,
          builder.provenance, exception.getClass().getSimpleName() + ": " + safeText(exception.getMessage()));
    }
  }

  private static PlantConstraintSample.SampleStatus unavailableStatus(List<String> findings) {
    if (findings.contains("INCOMPLETE_CONVERGENCE")) {
      return PlantConstraintSample.SampleStatus.INCOMPLETE_CONVERGENCE;
    }
    if (findings.contains("STALE_CALCULATION_IDENTITY")) {
      return PlantConstraintSample.SampleStatus.STALE;
    }
    return PlantConstraintSample.SampleStatus.NOT_CALCULABLE;
  }

  private static Observation observe(Metric metric, PipeBeggsAndBrills pipeline) {
    double[] pressure = pipeline.getPressureProfile();
    double[] temperature = pipeline.getTemperatureProfile();
    List<Double> velocity = pipeline.getMixtureSuperficialVelocityProfile();
    List<Double> distance = pipeline.getLengthProfile();
    switch (metric) {
    case MAXIMUM_PRESSURE:
      return extremum(pressure, distance, true, 0.0);
    case PRESSURE_DROP:
      return new Observation(pipeline.getPressureDrop(), pressure.length - 1, distance.get(distance.size() - 1));
    case RECEIVING_PRESSURE:
      return new Observation(pressure[pressure.length - 1], pressure.length - 1, distance.get(distance.size() - 1));
    case MIXTURE_VELOCITY:
      return extremum(toArray(velocity), distance, true, 0.0);
    case MINIMUM_TEMPERATURE:
      return extremum(temperature, distance, false, -273.15);
    case MAXIMUM_TEMPERATURE:
      return extremum(temperature, distance, true, -273.15);
    default:
      throw new IllegalArgumentException("Unsupported pipeline evidence metric " + metric);
    }
  }

  private static Observation extremum(double[] values, List<Double> distance, boolean maximum, double offset) {
    int selected = 0;
    for (int index = 1; index < values.length; index++) {
      if (maximum ? values[index] > values[selected] : values[index] < values[selected]) {
        selected = index;
      }
    }
    return new Observation(values[selected] + offset, selected, distance.get(selected));
  }

  private static double[] toArray(List<Double> values) {
    double[] result = new double[values.size()];
    for (int index = 0; index < values.size(); index++) {
      result[index] = values.get(index);
    }
    return result;
  }

  private static double limit(Metric metric, Builder builder) {
    switch (metric) {
    case MAXIMUM_PRESSURE:
      return builder.maximumPressureBara;
    case PRESSURE_DROP:
      return builder.maximumPressureDropBar;
    case RECEIVING_PRESSURE:
      return builder.minimumReceivingPressureBara;
    case MIXTURE_VELOCITY:
      return builder.maximumMixtureVelocityMetresPerSecond;
    case MINIMUM_TEMPERATURE:
      return builder.minimumTemperatureCelsius;
    case MAXIMUM_TEMPERATURE:
      return builder.maximumTemperatureCelsius;
    default:
      return Double.NaN;
    }
  }

  private static PlantConstraintSample sample(PlantConstraintDefinition definition, String calculationId, double value,
      double limit, String provenance) {
    boolean minimum = definition.getLimitDirection() == PlantConstraintDefinition.LimitDirection.MINIMUM;
    double utilization;
    if (minimum) {
      double scale = Math.max(1.0, Math.abs(limit));
      utilization = 1.0 + (limit - value) / scale;
    } else {
      utilization = value / limit;
    }
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

  private static boolean finitePositiveOrZero(double value) {
    return Double.isFinite(value) && value >= 0.0;
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

  /** @return stable pipeline equipment name */
  public String getPipelineName() {
    return pipelineName;
  }

  /** @return stable process area name */
  public String getAreaName() {
    return areaName;
  }

  /** @return evidence provenance supplied by the caller */
  public String getProvenance() {
    return provenance;
  }

  /** @return overall evidence status */
  public Status getStatus() {
    return status;
  }

  /** @return true only when every required observation is available */
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

  /** @return immutable deterministic extrema locations */
  public List<ExtremumLocation> getLocations() {
    return Collections.unmodifiableList(new ArrayList<ExtremumLocation>(locations));
  }

  /** @return immutable fail-closed diagnostics */
  public List<String> getDiagnostics() {
    return Collections.unmodifiableList(new ArrayList<String>(diagnostics));
  }

  /**
   * Builds a complete plant utilization snapshot from the frozen piping evidence.
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
    root.addProperty("pipelineName", pipelineName);
    root.addProperty("provenance", provenance);
    root.addProperty("convergenceComplete", convergenceComplete);
    root.addProperty("status", status.name());
    JsonArray findingRows = new JsonArray();
    for (String diagnostic : diagnostics) {
      findingRows.add(diagnostic);
    }
    root.add("diagnostics", findingRows);
    JsonArray evidenceRows = new JsonArray();
    PlantUtilizationSnapshot snapshot = toPlantUtilizationSnapshot();
    for (PlantConstraintEvidence evidence : snapshot.getEvidence()) {
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
      ExtremumLocation location = locationFor(evidence.getDefinition().getId());
      if (location == null) {
        row.add("nodeIndex", JsonNull.INSTANCE);
        row.add("distanceMetres", JsonNull.INSTANCE);
      } else {
        row.addProperty("nodeIndex", location.getNodeIndex());
        row.addProperty("distanceMetres", location.getDistanceMetres());
      }
      row.addProperty("diagnostic", evidence.getDiagnostic());
      evidenceRows.add(row);
    }
    root.add("evidence", evidenceRows);
    return root.toString();
  }

  private ExtremumLocation locationFor(String constraintId) {
    for (ExtremumLocation location : locations) {
      if (location.getMetric().id.equals(constraintId)) {
        return location;
      }
    }
    return null;
  }

  private static void addNumber(JsonObject target, String name, double value) {
    if (Double.isFinite(value)) {
      target.addProperty(name, value);
    } else {
      target.add(name, JsonNull.INSTANCE);
    }
  }

  /** Callback-free JPype-friendly piping evidence builder. */
  public static final class Builder {
    private final String modelName;
    private final String areaName;
    private final String calculationId;
    private final PipeBeggsAndBrills pipeline;
    private final String provenance;
    private boolean convergenceComplete;
    private boolean geometryVerified;
    private double maximumPressureBara = Double.NaN;
    private double maximumPressureDropBar = Double.NaN;
    private double minimumReceivingPressureBara = Double.NaN;
    private double maximumMixtureVelocityMetresPerSecond = Double.NaN;
    private double minimumTemperatureCelsius = Double.NaN;
    private double maximumTemperatureCelsius = Double.NaN;

    private Builder(String modelName, String areaName, String calculationId, PipeBeggsAndBrills pipeline,
        String provenance) {
      this.modelName = PlantConstraintScope.requireText(modelName, "Model name");
      this.areaName = PlantConstraintScope.requireText(areaName, "Area name");
      this.calculationId = PlantConstraintScope.requireText(calculationId, "Calculation id");
      if (pipeline == null) {
        throw new IllegalArgumentException("Pipeline is required");
      }
      this.pipeline = pipeline;
      this.provenance = PlantConstraintScope.requireText(provenance, "Pipeline evidence provenance");
    }

    /**
     * Confirms that length, diameter, and roughness came from the declared design source.
     *
     * @param value true only after caller verifies geometry provenance
     * @return this builder
     */
    public Builder geometryVerified(boolean value) {
      geometryVerified = value;
      return this;
    }

    /**
     * Declares the maximum absolute pressure.
     *
     * @param value positive pressure in bara
     * @return this builder
     */
    public Builder maximumPressureBara(double value) {
      maximumPressureBara = value;
      return this;
    }

    /**
     * Declares the maximum inlet-to-outlet pressure loss.
     *
     * @param value positive pressure difference in bar
     * @return this builder
     */
    public Builder maximumPressureDropBar(double value) {
      maximumPressureDropBar = value;
      return this;
    }

    /**
     * Declares the minimum receiving-boundary pressure.
     *
     * @param value positive absolute pressure in bara
     * @return this builder
     */
    public Builder minimumReceivingPressureBara(double value) {
      minimumReceivingPressureBara = value;
      return this;
    }

    /**
     * Declares the verified maximum mixture superficial velocity.
     *
     * @param value positive velocity in metres per second
     * @return this builder
     */
    public Builder maximumMixtureVelocityMetresPerSecond(double value) {
      maximumMixtureVelocityMetresPerSecond = value;
      return this;
    }

    /**
     * Declares the minimum bulk-fluid temperature.
     *
     * @param value temperature in degrees Celsius
     * @return this builder
     */
    public Builder minimumTemperatureCelsius(double value) {
      minimumTemperatureCelsius = value;
      return this;
    }

    /**
     * Declares the maximum bulk-fluid temperature.
     *
     * @param value temperature in degrees Celsius
     * @return this builder
     */
    public Builder maximumTemperatureCelsius(double value) {
      maximumTemperatureCelsius = value;
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

    /** @return immutable piping evidence */
    public PlantPipelineEvidence build() {
      return new PlantPipelineEvidence(this);
    }
  }
}
