package neqsim.process.safety.cfd;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.dispersion.GasDispersionResult;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator.ConsequenceBranch;
import neqsim.process.safety.scenario.ReleaseDispersionScenarioGenerator.ReleaseDispersionScenario;

/**
 * Versioned CFD source-term handoff case for detailed safety dispersion simulations.
 *
 * <p>
 * The case is a neutral JSON payload that carries NeqSim process context, source-term time series,
 * fluid composition, weather, inventory basis, consequence branches and quality metadata. It is
 * intended as the common handoff layer before writing simulator-specific files for OpenFOAM, FLACS,
 * KFX, PHAST or other detailed consequence tools.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class CfdSourceTermCase implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Schema identifier used in generated JSON. */
  public static final String SCHEMA_VERSION = "neqsim-cfd-source-term-case-1.0";

  private final String caseId;
  private final String scenarioName;
  private final Map<String, Object> context;
  private final Map<String, Object> fluid;
  private final Map<String, Object> release;
  private final Map<String, Object> sourceTerm;
  private final Map<String, Object> ambient;
  private final Map<String, Object> dispersionScreening;
  private final Map<String, Object> inventory;
  private final List<Map<String, Object>> consequenceBranches;
  private final Map<String, Object> cfdHints;
  private final Map<String, Object> provenance;
  private final List<String> qualityWarnings;

  /**
   * Creates a CFD source-term case from a builder.
   *
   * @param builder populated builder
   */
  private CfdSourceTermCase(Builder builder) {
    this.caseId = builder.caseId;
    this.scenarioName = builder.scenarioName;
    this.context = immutableCopy(builder.context);
    this.fluid = immutableCopy(builder.fluid);
    this.release = immutableCopy(builder.release);
    this.sourceTerm = immutableCopy(builder.sourceTerm);
    this.ambient = immutableCopy(builder.ambient);
    this.dispersionScreening = immutableCopy(builder.dispersionScreening);
    this.inventory = immutableCopy(builder.inventory);
    this.consequenceBranches = immutableListCopy(builder.consequenceBranches);
    this.cfdHints = immutableCopy(builder.cfdHints);
    this.provenance = immutableCopy(builder.provenance);
    this.qualityWarnings = Collections.unmodifiableList(new ArrayList<String>(builder.warnings));
  }

  /**
   * Creates a builder.
   *
   * @return empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a CFD handoff case from a release-dispersion scenario.
   *
   * @param scenario release-dispersion scenario produced by the scenario generator
   * @return CFD source-term handoff case
   * @throws IllegalArgumentException if the scenario is null
   */
  public static CfdSourceTermCase fromScenario(ReleaseDispersionScenario scenario) {
    if (scenario == null) {
      throw new IllegalArgumentException("scenario must not be null");
    }
    return builder().caseId(normalizeCaseId(scenario.getScenarioName()))
        .scenarioName(scenario.getScenarioName()).context(contextMap(scenario))
        .fluid(fluidMap(scenario)).release(releaseMap(scenario))
        .sourceTerm(sourceTermMap(scenario.getSourceTerm())).ambient(ambientMap(scenario))
        .dispersionScreening(dispersionMap(scenario.getDispersionResult()))
        .inventory(
            inventoryMap(scenario.getTrappedInventoryResult(), scenario.getInventoryVolumeM3()))
        .consequenceBranches(branchMaps(scenario.getConsequenceBranches()))
        .cfdHints(defaultCfdHints(scenario)).provenance(provenanceMap(scenario))
        .warnings(qualityWarnings(scenario)).build();
  }

  /**
   * Gets the schema version.
   *
   * @return schema version identifier
   */
  public String getSchemaVersion() {
    return SCHEMA_VERSION;
  }

  /**
   * Gets the case identifier.
   *
   * @return case identifier
   */
  public String getCaseId() {
    return caseId;
  }

  /**
   * Gets the scenario name.
   *
   * @return scenario name
   */
  public String getScenarioName() {
    return scenarioName;
  }

  /**
   * Gets the release map.
   *
   * @return immutable release map
   */
  public Map<String, Object> getRelease() {
    return release;
  }

  /**
   * Gets the source-term map.
   *
   * @return immutable source-term map
   */
  public Map<String, Object> getSourceTerm() {
    return sourceTerm;
  }

  /**
   * Gets quality warnings.
   *
   * @return immutable warning list
   */
  public List<String> getQualityWarnings() {
    return qualityWarnings;
  }

  /**
   * Converts this case to an ordered JSON-friendly map.
   *
   * @return ordered map representation
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("schemaVersion", SCHEMA_VERSION);
    map.put("caseId", caseId);
    map.put("scenarioName", scenarioName);
    map.put("context", context);
    map.put("fluid", fluid);
    map.put("release", release);
    map.put("sourceTerm", sourceTerm);
    map.put("ambient", ambient);
    map.put("dispersionScreening", dispersionScreening);
    map.put("inventory", inventory);
    map.put("consequenceBranches", consequenceBranches);
    map.put("cfdHints", cfdHints);
    map.put("provenance", provenance);
    map.put("qualityWarnings", qualityWarnings);
    return map;
  }

  /**
   * Serializes this case to pretty JSON.
   *
   * @return JSON representation of this case
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(toMap());
  }

  /**
   * Validates the case against the built-in required-field schema.
   *
   * @return validation result with errors and warnings
   */
  public ValidationResult validate() {
    List<String> errors = new ArrayList<String>();
    List<String> warnings = new ArrayList<String>();
    requireText(caseId, "caseId", errors);
    requireText(scenarioName, "scenarioName", errors);
    requireMap(context, "context", errors);
    requireMap(fluid, "fluid", errors);
    requireMap(release, "release", errors);
    requireMap(sourceTerm, "sourceTerm", errors);
    requireMap(ambient, "ambient", errors);
    requireMap(provenance, "provenance", errors);
    requirePositiveNumber(release.get("holeDiameterM"), "release.holeDiameterM", errors);
    requirePositiveNumber(sourceTerm.get("peakMassFlowRateKgPerS"),
        "sourceTerm.peakMassFlowRateKgPerS", errors);
    if (!Boolean.TRUE.equals(provenance.get("notForFinalLayoutWithoutValidation"))) {
      warnings
          .add("provenance.notForFinalLayoutWithoutValidation should be true for screening cases");
    }
    if (qualityWarnings.isEmpty()) {
      warnings.add("qualityWarnings is empty; industrial handoff should state assumptions");
    }
    return new ValidationResult(errors, warnings);
  }

  /**
   * Creates the process context map.
   *
   * @param scenario source scenario
   * @return ordered context map
   */
  private static Map<String, Object> contextMap(ReleaseDispersionScenario scenario) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("processName", scenario.getProcessName());
    map.put("equipmentName", scenario.getEquipmentName());
    map.put("equipmentType", scenario.getEquipmentType());
    map.put("streamName", scenario.getStreamName());
    map.put("releaseCase", scenario.getReleaseCaseName());
    map.put("releaseCategory", scenario.getReleaseCaseCategory());
    map.put("weatherCase", scenario.getWeatherCaseName());
    map.put("streamPressureBara", finiteOrNull(scenario.getStreamPressureBara()));
    map.put("streamTemperatureK", finiteOrNull(scenario.getStreamTemperatureK()));
    map.put("streamMassFlowRateKgPerS", finiteOrNull(scenario.getStreamMassFlowRateKgPerS()));
    return map;
  }

  /**
   * Creates the fluid composition map.
   *
   * @param scenario source scenario
   * @return ordered fluid map
   */
  private static Map<String, Object> fluidMap(ReleaseDispersionScenario scenario) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("componentMoleFractions", scenario.getComponentMoleFractions());
    map.put("sourceDensityKgPerM3",
        finiteOrNull(scenario.getDispersionResult().getSourceDensityKgPerM3()));
    map.put("fuelMoleFraction", finiteOrNull(scenario.getDispersionResult().getFuelMoleFraction()));
    map.put("fuelMassFraction", finiteOrNull(scenario.getDispersionResult().getFuelMassFraction()));
    map.put("lowerFlammableLimitVolumeFraction",
        finiteOrNull(scenario.getDispersionResult().getLowerFlammableLimitVolumeFraction()));
    return map;
  }

  /**
   * Creates the release geometry map.
   *
   * @param scenario source scenario
   * @return ordered release map
   */
  private static Map<String, Object> releaseMap(ReleaseDispersionScenario scenario) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    double diameter = scenario.getHoleDiameterM();
    map.put("holeDiameterM", finiteOrNull(diameter));
    map.put("holeAreaM2", finiteOrNull(Math.PI * diameter * diameter / 4.0));
    map.put("orientation", scenario.getReleaseOrientation().name());
    map.put("orientationAngleDeg", finiteOrNull(scenario.getReleaseOrientation().getAngle()));
    map.put("releaseHeightM", finiteOrNull(scenario.getReleaseHeightM()));
    map.put("releaseLocation", defaultReleaseLocation(scenario.getReleaseHeightM()));
    map.put("releaseFrequencyPerYear", finiteOrNull(scenario.getReleaseFrequencyPerYear()));
    return map;
  }

  /**
   * Creates a default release location map.
   *
   * @param releaseHeightM release height in m
   * @return ordered location map
   */
  private static Map<String, Object> defaultReleaseLocation(double releaseHeightM) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("xM", 0.0);
    map.put("yM", 0.0);
    map.put("zM", finiteOrNull(releaseHeightM));
    map.put("basis", "default local coordinate origin; replace with site layout coordinates");
    return map;
  }

  /**
   * Creates the source-term map from source-term arrays.
   *
   * @param sourceTerm source-term result
   * @return ordered source-term map
   */
  private static Map<String, Object> sourceTermMap(SourceTermResult sourceTerm) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("scenarioName", sourceTerm.getScenarioName());
    map.put("peakMassFlowRateKgPerS", finiteOrNull(sourceTerm.getPeakMassFlowRate()));
    map.put("totalMassReleasedKg", finiteOrNull(sourceTerm.getTotalMassReleased()));
    map.put("timeToEmptyS", finiteOrNull(sourceTerm.getTimeToEmpty()));
    map.put("timeSeries", sourceTermSeries(sourceTerm));
    return map;
  }

  /**
   * Creates source-term time-series rows.
   *
   * @param sourceTerm source-term result
   * @return list of ordered source-term rows
   */
  private static List<Map<String, Object>> sourceTermSeries(SourceTermResult sourceTerm) {
    double[] time = sourceTerm.getTime();
    double[] massFlow = sourceTerm.getMassFlowRate();
    double[] temperature = sourceTerm.getTemperature();
    double[] pressure = sourceTerm.getPressure();
    double[] vaporFraction = sourceTerm.getVaporMassFraction();
    double[] velocity = sourceTerm.getJetVelocity();
    double[] momentum = sourceTerm.getJetMomentum();
    double[] dropletSmd = sourceTerm.getLiquidDropletSMD();
    List<Map<String, Object>> series = new ArrayList<Map<String, Object>>();
    for (int index = 0; index < time.length; index++) {
      Map<String, Object> row = new LinkedHashMap<String, Object>();
      row.put("timeS", finiteOrNull(time[index]));
      row.put("massFlowRateKgPerS", finiteOrNull(massFlow[index]));
      row.put("temperatureK", finiteOrNull(temperature[index]));
      row.put("pressurePa", finiteOrNull(pressure[index]));
      row.put("vaporMassFraction", finiteOrNull(vaporFraction[index]));
      row.put("jetVelocityMPerS", finiteOrNull(velocity[index]));
      row.put("jetMomentumN", finiteOrNull(momentum[index]));
      row.put("liquidDropletSmdM", finiteOrNull(dropletSmd[index]));
      series.add(row);
    }
    return series;
  }

  /**
   * Creates the ambient/weather map.
   *
   * @param scenario source scenario
   * @return ordered ambient map
   */
  private static Map<String, Object> ambientMap(ReleaseDispersionScenario scenario) {
    BoundaryConditions weather = scenario.getBoundaryConditions();
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("weatherCaseName", scenario.getWeatherCaseName());
    map.put("ambientTemperatureK", finiteOrNull(weather.getAmbientTemperature()));
    map.put("windSpeedMPerS", finiteOrNull(weather.getWindSpeed()));
    map.put("windDirectionDeg", finiteOrNull(weather.getWindDirection()));
    map.put("relativeHumidity", finiteOrNull(weather.getRelativeHumidity()));
    map.put("atmosphericPressurePa", finiteOrNull(weather.getAtmosphericPressure()));
    map.put("pasquillStabilityClass", Character.toString(weather.getPasquillStabilityClass()));
    map.put("surfaceRoughnessM", finiteOrNull(weather.getSurfaceRoughness()));
    map.put("offshore", Boolean.valueOf(weather.isOffshore()));
    return map;
  }

  /**
   * Creates a dispersion screening map.
   *
   * @param result gas dispersion result
   * @return ordered dispersion map
   */
  private static Map<String, Object> dispersionMap(GasDispersionResult result) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("selectedModel", result.getSelectedModel());
    map.put("distanceToLflM", finiteOrNull(result.getDistanceToLflM()));
    map.put("distanceToHalfLflM", finiteOrNull(result.getDistanceToHalfLflM()));
    map.put("flammableCloudVolumeM3", finiteOrNull(result.getFlammableCloudVolumeM3()));
    map.put("toxicComponentName", result.getToxicComponentName());
    map.put("toxicThresholdPpm", finiteOrNull(result.getToxicThresholdPpm()));
    map.put("toxicDistanceM", finiteOrNull(result.getToxicDistanceM()));
    map.put("screeningBasis", result.getScreeningBasis());
    return map;
  }

  /**
   * Creates an inventory map.
   *
   * @param inventoryResult optional trapped inventory result
   * @param representativeVolumeM3 representative fallback volume in m3
   * @return ordered inventory map
   */
  private static Map<String, Object> inventoryMap(InventoryResult inventoryResult,
      double representativeVolumeM3) {
    if (inventoryResult != null) {
      Map<String, Object> map = new LinkedHashMap<String, Object>(inventoryResult.toMap());
      map.put("basis", "TrappedInventoryCalculator evidence-linked isolated inventory");
      return map;
    }
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("basis", "Representative volume configured on ReleaseDispersionScenarioGenerator");
    map.put("totalVolumeM3", finiteOrNull(representativeVolumeM3));
    map.put("warnings", Collections.singletonList(
        "No TrappedInventoryCalculator result attached; volume is a screening assumption."));
    return map;
  }

  /**
   * Converts consequence branches to maps.
   *
   * @param branches consequence branches
   * @return list of branch maps
   */
  private static List<Map<String, Object>> branchMaps(List<ConsequenceBranch> branches) {
    List<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
    for (ConsequenceBranch branch : branches) {
      maps.add(branch.toMap());
    }
    return maps;
  }

  /**
   * Creates default CFD domain and solver hints.
   *
   * @param scenario source scenario
   * @return ordered CFD hint map
   */
  private static Map<String, Object> defaultCfdHints(ReleaseDispersionScenario scenario) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    double endpoint = maxFinite(scenario.getDispersionResult().getDistanceToHalfLflM(),
        scenario.getDispersionResult().getToxicDistanceM());
    if (!Double.isFinite(endpoint) || endpoint <= 0.0) {
      endpoint = 100.0;
    }
    map.put("recommendedDomainLengthM", finiteOrNull(Math.max(200.0, 2.0 * endpoint)));
    map.put("recommendedDomainWidthM", finiteOrNull(Math.max(80.0, endpoint)));
    map.put("recommendedDomainHeightM", finiteOrNull(Math.max(30.0, 0.25 * endpoint)));
    map.put("sourceRefinementRadiusM",
        finiteOrNull(Math.max(2.0, 20.0 * scenario.getHoleDiameterM())));
    map.put("solverFamily", "transient buoyant species transport / detailed CFD");
    map.put("openFoamHint", "map sourceTerm.timeSeries to a coded or tabulated mass source");
    return map;
  }

  /**
   * Returns the maximum finite value from two endpoint distances.
   *
   * @param first first endpoint value
   * @param second second endpoint value
   * @return maximum finite value, or NaN if neither value is finite
   */
  private static double maxFinite(double first, double second) {
    boolean firstFinite = Double.isFinite(first);
    boolean secondFinite = Double.isFinite(second);
    if (firstFinite && secondFinite) {
      return Math.max(first, second);
    }
    if (firstFinite) {
      return first;
    }
    if (secondFinite) {
      return second;
    }
    return Double.NaN;
  }

  /**
   * Creates a provenance and assumption map.
   *
   * @param scenario source scenario
   * @return ordered provenance map
   */
  private static Map<String, Object> provenanceMap(ReleaseDispersionScenario scenario) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("generatedBy", "NeqSim ReleaseDispersionScenarioGenerator");
    map.put("sourceModel", "LeakModel plus GasDispersionAnalyzer screening result");
    map.put("standardsBasis", standardsBasis());
    map.put("confidenceLevel", "screening");
    map.put("notForFinalLayoutWithoutValidation", Boolean.TRUE);
    map.put("limitations", limitations(scenario));
    return map;
  }

  /**
   * Gets standards basis list.
   *
   * @return standards basis list
   */
  private static List<String> standardsBasis() {
    List<String> standards = new ArrayList<String>();
    standards.add("NORSOK Z-013 risk and emergency preparedness assessment");
    standards.add("API 752/753 facility siting consequence screening");
    standards.add("CCPS Guidelines for Chemical Process Quantitative Risk Analysis");
    standards.add("TNO Yellow Book / Pasquill-Gifford screening basis");
    return standards;
  }

  /**
   * Creates limitations for the case.
   *
   * @param scenario source scenario
   * @return limitation list
   */
  private static List<String> limitations(ReleaseDispersionScenario scenario) {
    List<String> limitations = new ArrayList<String>();
    limitations
        .add("Screening source term and dispersion; validate before final layout decisions.");
    limitations.add("Release coordinates default to local origin unless site layout is added.");
    limitations.add("CFD mesh, obstacle geometry and congestion are not generated by this schema.");
    if (Double.isNaN(scenario.getReleaseFrequencyPerYear())) {
      limitations.add("Release frequency is not supplied; add site-specific QRA frequencies.");
    } else {
      limitations.add("Release frequency is generic screening guidance, not a QRA claim.");
    }
    return limitations;
  }

  /**
   * Creates quality warnings.
   *
   * @param scenario source scenario
   * @return warning list
   */
  private static List<String> qualityWarnings(ReleaseDispersionScenario scenario) {
    List<String> warnings = new ArrayList<String>();
    warnings.add("CFD handoff case is a source-term package, not a completed CFD model.");
    warnings.add("Replace default release coordinates with site layout before detailed CFD.");
    if (scenario.getTrappedInventoryResult() == null) {
      warnings
          .add("Inventory volume is representative; attach TrappedInventoryCalculator for audit.");
    } else {
      warnings.addAll(scenario.getTrappedInventoryResult().getWarnings());
    }
    return warnings;
  }

  /**
   * Normalizes a scenario name to a case identifier.
   *
   * @param scenarioName scenario name
   * @return normalized case id
   */
  private static String normalizeCaseId(String scenarioName) {
    if (scenarioName == null || scenarioName.trim().isEmpty()) {
      return "cfd-source-term-case";
    }
    return scenarioName.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "")
        .replaceAll("-+$", "");
  }

  /**
   * Converts a finite double to a JSON-friendly object.
   *
   * @param value numeric value
   * @return double value or null when not finite
   */
  private static Object finiteOrNull(double value) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return null;
    }
    return Double.valueOf(value);
  }

  /**
   * Requires a text value.
   *
   * @param value text value
   * @param field field name
   * @param errors error list
   */
  private static void requireText(String value, String field, List<String> errors) {
    if (value == null || value.trim().isEmpty()) {
      errors.add(field + " is required");
    }
  }

  /**
   * Requires a map value.
   *
   * @param map map to check
   * @param field field name
   * @param errors error list
   */
  private static void requireMap(Map<String, Object> map, String field, List<String> errors) {
    if (map == null || map.isEmpty()) {
      errors.add(field + " is required");
    }
  }

  /**
   * Requires a positive numeric value.
   *
   * @param value value to check
   * @param field field name
   * @param errors error list
   */
  private static void requirePositiveNumber(Object value, String field, List<String> errors) {
    if (!(value instanceof Number) || ((Number) value).doubleValue() <= 0.0) {
      errors.add(field + " must be positive");
    }
  }

  /**
   * Creates an immutable copy of a map.
   *
   * @param map source map
   * @return immutable copy
   */
  private static Map<String, Object> immutableCopy(Map<String, Object> map) {
    return Collections.unmodifiableMap(new LinkedHashMap<String, Object>(map));
  }

  /**
   * Creates an immutable copy of a list of maps.
   *
   * @param maps source map list
   * @return immutable copied list
   */
  private static List<Map<String, Object>> immutableListCopy(List<Map<String, Object>> maps) {
    List<Map<String, Object>> copied = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> map : maps) {
      copied.add(immutableCopy(map));
    }
    return Collections.unmodifiableList(copied);
  }

  /** Builder for CFD source-term cases. */
  public static final class Builder {
    private String caseId = "cfd-source-term-case";
    private String scenarioName = "CFD source-term case";
    private Map<String, Object> context = new LinkedHashMap<String, Object>();
    private Map<String, Object> fluid = new LinkedHashMap<String, Object>();
    private Map<String, Object> release = new LinkedHashMap<String, Object>();
    private Map<String, Object> sourceTerm = new LinkedHashMap<String, Object>();
    private Map<String, Object> ambient = new LinkedHashMap<String, Object>();
    private Map<String, Object> dispersionScreening = new LinkedHashMap<String, Object>();
    private Map<String, Object> inventory = new LinkedHashMap<String, Object>();
    private List<Map<String, Object>> consequenceBranches = new ArrayList<Map<String, Object>>();
    private Map<String, Object> cfdHints = new LinkedHashMap<String, Object>();
    private Map<String, Object> provenance = new LinkedHashMap<String, Object>();
    private List<String> warnings = new ArrayList<String>();

    /**
     * Sets the case id.
     *
     * @param caseId stable case identifier
     * @return this builder
     */
    public Builder caseId(String caseId) {
      if (caseId != null && !caseId.trim().isEmpty()) {
        this.caseId = caseId;
      }
      return this;
    }

    /**
     * Sets the scenario name.
     *
     * @param scenarioName readable scenario name
     * @return this builder
     */
    public Builder scenarioName(String scenarioName) {
      if (scenarioName != null && !scenarioName.trim().isEmpty()) {
        this.scenarioName = scenarioName;
      }
      return this;
    }

    /**
     * Sets context map.
     *
     * @param context context map
     * @return this builder
     */
    public Builder context(Map<String, Object> context) {
      this.context = copyOrEmpty(context);
      return this;
    }

    /**
     * Sets fluid map.
     *
     * @param fluid fluid map
     * @return this builder
     */
    public Builder fluid(Map<String, Object> fluid) {
      this.fluid = copyOrEmpty(fluid);
      return this;
    }

    /**
     * Sets release map.
     *
     * @param release release map
     * @return this builder
     */
    public Builder release(Map<String, Object> release) {
      this.release = copyOrEmpty(release);
      return this;
    }

    /**
     * Sets source-term map.
     *
     * @param sourceTerm source-term map
     * @return this builder
     */
    public Builder sourceTerm(Map<String, Object> sourceTerm) {
      this.sourceTerm = copyOrEmpty(sourceTerm);
      return this;
    }

    /**
     * Sets ambient map.
     *
     * @param ambient ambient map
     * @return this builder
     */
    public Builder ambient(Map<String, Object> ambient) {
      this.ambient = copyOrEmpty(ambient);
      return this;
    }

    /**
     * Sets dispersion screening map.
     *
     * @param dispersionScreening dispersion screening map
     * @return this builder
     */
    public Builder dispersionScreening(Map<String, Object> dispersionScreening) {
      this.dispersionScreening = copyOrEmpty(dispersionScreening);
      return this;
    }

    /**
     * Sets inventory map.
     *
     * @param inventory inventory map
     * @return this builder
     */
    public Builder inventory(Map<String, Object> inventory) {
      this.inventory = copyOrEmpty(inventory);
      return this;
    }

    /**
     * Sets consequence branch maps.
     *
     * @param consequenceBranches consequence branch maps
     * @return this builder
     */
    public Builder consequenceBranches(List<Map<String, Object>> consequenceBranches) {
      this.consequenceBranches = new ArrayList<Map<String, Object>>(consequenceBranches);
      return this;
    }

    /**
     * Sets CFD hints.
     *
     * @param cfdHints CFD hint map
     * @return this builder
     */
    public Builder cfdHints(Map<String, Object> cfdHints) {
      this.cfdHints = copyOrEmpty(cfdHints);
      return this;
    }

    /**
     * Sets provenance map.
     *
     * @param provenance provenance map
     * @return this builder
     */
    public Builder provenance(Map<String, Object> provenance) {
      this.provenance = copyOrEmpty(provenance);
      return this;
    }

    /**
     * Sets warnings.
     *
     * @param warnings warning list
     * @return this builder
     */
    public Builder warnings(List<String> warnings) {
      this.warnings = new ArrayList<String>(warnings);
      return this;
    }

    /**
     * Builds the case.
     *
     * @return CFD source-term case
     */
    public CfdSourceTermCase build() {
      return new CfdSourceTermCase(this);
    }

    /**
     * Copies a map or returns an empty map.
     *
     * @param map map to copy
     * @return copied map
     */
    private static Map<String, Object> copyOrEmpty(Map<String, Object> map) {
      if (map == null) {
        return new LinkedHashMap<String, Object>();
      }
      return new LinkedHashMap<String, Object>(map);
    }
  }

  /** Validation result for a CFD source-term case. */
  public static class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final List<String> errors;
    private final List<String> warnings;

    /**
     * Creates a validation result.
     *
     * @param errors validation errors
     * @param warnings validation warnings
     */
    private ValidationResult(List<String> errors, List<String> warnings) {
      this.errors = new ArrayList<String>(errors);
      this.warnings = new ArrayList<String>(warnings);
    }

    /**
     * Checks whether validation passed.
     *
     * @return true when there are no errors
     */
    public boolean isValid() {
      return errors.isEmpty();
    }

    /**
     * Gets validation errors.
     *
     * @return immutable error list
     */
    public List<String> getErrors() {
      return Collections.unmodifiableList(errors);
    }

    /**
     * Gets validation warnings.
     *
     * @return immutable warning list
     */
    public List<String> getWarnings() {
      return Collections.unmodifiableList(warnings);
    }

    /**
     * Serializes validation result as JSON.
     *
     * @return validation result JSON
     */
    public String toJson() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("valid", Boolean.valueOf(isValid()));
      map.put("errors", errors);
      map.put("warnings", warnings);
      return new GsonBuilder().setPrettyPrinting().create().toJson(map);
    }
  }
}
