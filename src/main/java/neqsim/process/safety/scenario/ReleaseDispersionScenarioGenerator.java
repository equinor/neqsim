package neqsim.process.safety.scenario;

import com.google.gson.GsonBuilder;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.process.safety.BoundaryConditions;
import neqsim.process.safety.cfd.CfdSourceTermCase;
import neqsim.process.safety.dispersion.GasDispersionAnalyzer;
import neqsim.process.safety.dispersion.GasDispersionResult;
import neqsim.process.safety.inventory.TrappedInventoryCalculator;
import neqsim.process.safety.inventory.TrappedInventoryCalculator.InventoryResult;
import neqsim.process.safety.release.LeakModel;
import neqsim.process.safety.release.ReleaseOrientation;
import neqsim.process.safety.release.SourceTermResult;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Generates leak source terms and gas dispersion screening scenarios from a process flowsheet.
 *
 * <p>
 * The generator walks {@link ProcessSystem} unit operations, discovers standalone streams and
 * equipment outlet streams, builds a {@link LeakModel} for each pressurized stream candidate, and
 * sends the resulting {@link SourceTermResult} to {@link GasDispersionAnalyzer}. Optional scenario
 * taxonomy, weather envelopes, trapped-inventory results and consequence branches make the output
 * suitable for structured QRA and CFD source-term handoffs.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ReleaseDispersionScenarioGenerator implements Serializable {
  private static final long serialVersionUID = 1000L;

  private static final double DEFAULT_HOLE_DIAMETER_M = 0.01;
  private static final double DEFAULT_FULL_BORE_DIAMETER_M = 0.10;
  private static final double DEFAULT_INVENTORY_VOLUME_M3 = 1.0;
  private static final double DEFAULT_RELEASE_DURATION_SECONDS = 300.0;
  private static final double DEFAULT_TIME_STEP_SECONDS = 5.0;

  private final ProcessSystem processSystem;
  private BoundaryConditions boundaryConditions = BoundaryConditions.defaultConditions();
  private double holeDiameterM = DEFAULT_HOLE_DIAMETER_M;
  private double fullBoreDiameterM = DEFAULT_FULL_BORE_DIAMETER_M;
  private double inventoryVolumeM3 = DEFAULT_INVENTORY_VOLUME_M3;
  private double releaseDurationSeconds = DEFAULT_RELEASE_DURATION_SECONDS;
  private double timeStepSeconds = DEFAULT_TIME_STEP_SECONDS;
  private double releaseHeightM = 1.0;
  private double minimumMassFlowRateKgPerS = 1.0e-9;
  private double minimumPressureBara = 1.2;
  private double backPressureBara =
      BoundaryConditions.defaultConditions().getAtmosphericPressureBar();
  private boolean backPressureManuallySet;
  private ReleaseOrientation releaseOrientation = ReleaseOrientation.HORIZONTAL;
  private GasDispersionAnalyzer.ModelSelection modelSelection =
      GasDispersionAnalyzer.ModelSelection.AUTO;
  private String toxicComponentName;
  private double toxicThresholdPpm = Double.NaN;
  private boolean scenarioTaxonomyEnabled;
  private final List<ReleaseCase> releaseCases = new ArrayList<ReleaseCase>();
  private final List<WeatherCase> weatherCases = new ArrayList<WeatherCase>();
  private InventoryResult trappedInventoryResult;
  private final List<ConsequenceBranch> consequenceBranches = defaultConsequenceBranches();

  /**
   * Creates a release-dispersion scenario generator.
   *
   * @param processSystem process system to scan for stream release candidates
   */
  public ReleaseDispersionScenarioGenerator(ProcessSystem processSystem) {
    if (processSystem == null) {
      throw new IllegalArgumentException("processSystem cannot be null");
    }
    this.processSystem = processSystem;
  }

  /**
   * Sets the boundary conditions for generated dispersion calculations.
   *
   * @param boundaryConditions weather and ambient conditions
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator boundaryConditions(
      BoundaryConditions boundaryConditions) {
    if (boundaryConditions != null) {
      this.boundaryConditions = boundaryConditions;
      if (!backPressureManuallySet) {
        this.backPressureBara = boundaryConditions.getAtmosphericPressureBar();
      }
    }
    return this;
  }

  /**
   * Sets the representative leak hole diameter.
   *
   * @param holeDiameterM hole diameter in m, must be positive
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator holeDiameter(double holeDiameterM) {
    if (holeDiameterM <= 0.0) {
      throw new IllegalArgumentException("holeDiameterM must be positive");
    }
    this.holeDiameterM = holeDiameterM;
    return this;
  }

  /**
   * Sets the representative leak hole diameter with unit conversion.
   *
   * @param holeDiameter hole diameter value, must be positive
   * @param unit diameter unit, one of m, mm, or in
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator holeDiameter(double holeDiameter, String unit) {
    return holeDiameter(toMeters(holeDiameter, unit));
  }

  /**
   * Sets the full-bore rupture diameter used by the taxonomy.
   *
   * @param diameter full-bore diameter value, must be positive
   * @param unit diameter unit, one of m, mm, or in
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator fullBoreDiameter(double diameter, String unit) {
    double meters = toMeters(diameter, unit);
    if (meters <= 0.0) {
      throw new IllegalArgumentException("full bore diameter must be positive");
    }
    this.fullBoreDiameterM = meters;
    return this;
  }

  /**
   * Sets the representative isolated inventory volume used by the source-term model.
   *
   * @param inventoryVolumeM3 inventory volume in m3, must be positive
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator inventoryVolume(double inventoryVolumeM3) {
    if (inventoryVolumeM3 <= 0.0) {
      throw new IllegalArgumentException("inventoryVolumeM3 must be positive");
    }
    this.inventoryVolumeM3 = inventoryVolumeM3;
    this.trappedInventoryResult = null;
    return this;
  }

  /**
   * Uses an evidence-linked trapped inventory result as the isolated source volume.
   *
   * @param inventoryResult trapped inventory result from {@link TrappedInventoryCalculator}
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator trappedInventory(InventoryResult inventoryResult) {
    if (inventoryResult == null) {
      throw new IllegalArgumentException("inventoryResult must not be null");
    }
    this.trappedInventoryResult = inventoryResult;
    this.inventoryVolumeM3 = effectiveVolume(inventoryResult);
    return this;
  }

  /**
   * Calculates and uses an evidence-linked trapped inventory result.
   *
   * @param inventoryCalculator configured trapped inventory calculator
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator trappedInventory(
      TrappedInventoryCalculator inventoryCalculator) {
    if (inventoryCalculator == null) {
      throw new IllegalArgumentException("inventoryCalculator must not be null");
    }
    return trappedInventory(inventoryCalculator.calculate());
  }

  /**
   * Sets release duration and time step for generated source terms.
   *
   * @param releaseDurationSeconds release duration in seconds, must be positive
   * @param timeStepSeconds time step in seconds, must be positive
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator releaseDuration(double releaseDurationSeconds,
      double timeStepSeconds) {
    if (releaseDurationSeconds <= 0.0) {
      throw new IllegalArgumentException("releaseDurationSeconds must be positive");
    }
    if (timeStepSeconds <= 0.0) {
      throw new IllegalArgumentException("timeStepSeconds must be positive");
    }
    this.releaseDurationSeconds = releaseDurationSeconds;
    this.timeStepSeconds = timeStepSeconds;
    return this;
  }

  /**
   * Sets the effective release height used in dispersion screening.
   *
   * @param releaseHeightM release height in m, must be non-negative
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator releaseHeight(double releaseHeightM) {
    if (releaseHeightM < 0.0) {
      throw new IllegalArgumentException("releaseHeightM cannot be negative");
    }
    this.releaseHeightM = releaseHeightM;
    return this;
  }

  /**
   * Sets the minimum stream pressure required before a stream is screened.
   *
   * @param minimumPressureBara minimum absolute pressure in bara
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator minimumPressure(double minimumPressureBara) {
    this.minimumPressureBara = minimumPressureBara;
    return this;
  }

  /**
   * Sets the minimum stream mass flow required before a stream is screened.
   *
   * @param minimumMassFlowRateKgPerS minimum stream mass flow rate in kg/s, must be non-negative
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator minimumMassFlowRate(double minimumMassFlowRateKgPerS) {
    if (minimumMassFlowRateKgPerS < 0.0) {
      throw new IllegalArgumentException("minimumMassFlowRateKgPerS cannot be negative");
    }
    this.minimumMassFlowRateKgPerS = minimumMassFlowRateKgPerS;
    return this;
  }

  /**
   * Sets the downstream back pressure for the leak model.
   *
   * @param backPressureBara back pressure in bara, normally atmospheric pressure
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator backPressure(double backPressureBara) {
    if (backPressureBara <= 0.0) {
      throw new IllegalArgumentException("backPressureBara must be positive");
    }
    this.backPressureBara = backPressureBara;
    this.backPressureManuallySet = true;
    return this;
  }

  /**
   * Sets release orientation for generated source terms.
   *
   * @param releaseOrientation release direction, defaults to horizontal when null
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator releaseOrientation(
      ReleaseOrientation releaseOrientation) {
    if (releaseOrientation != null) {
      this.releaseOrientation = releaseOrientation;
    }
    return this;
  }

  /**
   * Sets the dispersion model selection strategy.
   *
   * @param modelSelection dispersion model selection strategy, defaults to auto when null
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator modelSelection(
      GasDispersionAnalyzer.ModelSelection modelSelection) {
    if (modelSelection != null) {
      this.modelSelection = modelSelection;
    }
    return this;
  }

  /**
   * Adds a toxic endpoint calculation to every generated dispersion scenario.
   *
   * @param componentName component name in the NeqSim fluid
   * @param thresholdPpm toxic threshold in ppm, must be positive
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator toxicEndpoint(String componentName,
      double thresholdPpm) {
    if (thresholdPpm <= 0.0) {
      throw new IllegalArgumentException("thresholdPpm must be positive");
    }
    this.toxicComponentName = componentName;
    this.toxicThresholdPpm = thresholdPpm;
    return this;
  }

  /**
   * Enables the built-in release taxonomy for industrial screening matrices.
   *
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator useDefaultScenarioTaxonomy() {
    this.scenarioTaxonomyEnabled = true;
    this.releaseCases.clear();
    this.releaseCases.addAll(defaultReleaseTaxonomy());
    return this;
  }

  /**
   * Sets explicit release cases for scenario matrix generation.
   *
   * @param releaseCases release cases to generate
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator releaseCases(ReleaseCase... releaseCases) {
    this.scenarioTaxonomyEnabled = true;
    this.releaseCases.clear();
    if (releaseCases != null) {
      for (ReleaseCase releaseCase : releaseCases) {
        if (releaseCase != null) {
          this.releaseCases.add(releaseCase);
        }
      }
    }
    return this;
  }

  /**
   * Adds a named weather case to the batch weather envelope.
   *
   * @param name weather case name
   * @param conditions boundary conditions
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator addWeatherCase(String name,
      BoundaryConditions conditions) {
    this.weatherCases.add(new WeatherCase(name, conditions));
    return this;
  }

  /**
   * Replaces the batch weather envelope with explicit weather cases.
   *
   * @param weatherCases weather cases to use
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator weatherCases(List<WeatherCase> weatherCases) {
    this.weatherCases.clear();
    if (weatherCases != null) {
      this.weatherCases.addAll(weatherCases);
    }
    return this;
  }

  /**
   * Enables a default industrial weather envelope.
   *
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator useDefaultWeatherEnvelope() {
    this.weatherCases.clear();
    this.weatherCases.addAll(defaultWeatherEnvelope());
    return this;
  }

  /**
   * Sets consequence branches for ignition and non-ignition outcomes.
   *
   * @param branches consequence branches
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator consequenceBranches(List<ConsequenceBranch> branches) {
    this.consequenceBranches.clear();
    if (branches != null) {
      this.consequenceBranches.addAll(branches);
    }
    return this;
  }

  /**
   * Restores the built-in consequence branch set.
   *
   * @return this generator
   */
  public ReleaseDispersionScenarioGenerator useDefaultConsequenceBranches() {
    this.consequenceBranches.clear();
    this.consequenceBranches.addAll(defaultConsequenceBranches());
    return this;
  }

  /**
   * Generates release source terms and gas dispersion results for all eligible stream candidates.
   *
   * @return immutable list of generated release-dispersion scenarios
   */
  public List<ReleaseDispersionScenario> generateScenarios() {
    List<StreamCandidate> candidates = discoverStreamCandidates();
    List<ReleaseCase> cases = activeReleaseCases();
    List<WeatherCase> weatherEnvelope = activeWeatherCases();
    List<ReleaseDispersionScenario> scenarios = new ArrayList<ReleaseDispersionScenario>();
    for (StreamCandidate candidate : candidates) {
      for (ReleaseCase releaseCase : cases) {
        for (WeatherCase weatherCase : weatherEnvelope) {
          ReleaseDispersionScenario scenario =
              generateScenario(candidate, releaseCase, weatherCase);
          if (scenario != null) {
            scenarios.add(scenario);
          }
        }
      }
    }
    return Collections.unmodifiableList(scenarios);
  }

  /**
   * Generates CFD source-term handoff cases for all generated scenarios.
   *
   * @return immutable list of CFD source-term cases
   */
  public List<CfdSourceTermCase> generateCfdSourceTermCases() {
    List<ReleaseDispersionScenario> scenarios = generateScenarios();
    List<CfdSourceTermCase> cases = new ArrayList<CfdSourceTermCase>();
    for (ReleaseDispersionScenario scenario : scenarios) {
      cases.add(scenario.toCfdSourceTermCase());
    }
    return Collections.unmodifiableList(cases);
  }

  /**
   * Gets the default industrial release taxonomy.
   *
   * @return release taxonomy list
   */
  public static List<ReleaseCase> defaultReleaseTaxonomy() {
    List<ReleaseCase> cases = new ArrayList<ReleaseCase>();
    cases.add(ReleaseCase.FIVE_MM_HOLE);
    cases.add(ReleaseCase.TEN_MM_HOLE);
    cases.add(ReleaseCase.TWENTY_FIVE_MM_HOLE);
    cases.add(ReleaseCase.FIFTY_MM_HOLE);
    cases.add(ReleaseCase.FULL_BORE_RUPTURE);
    cases.add(ReleaseCase.FLANGE_LEAK);
    cases.add(ReleaseCase.INSTRUMENT_LEAK);
    cases.add(ReleaseCase.DROPPED_OBJECT_DAMAGE);
    return Collections.unmodifiableList(cases);
  }

  /**
   * Gets a default weather envelope for safety screening batches.
   *
   * @return weather case list
   */
  public static List<WeatherCase> defaultWeatherEnvelope() {
    List<WeatherCase> cases = new ArrayList<WeatherCase>();
    cases.add(new WeatherCase("winter-D-low-wind-prevailing",
        BoundaryConditions.builder().ambientTemperature(5.0, "C").windSpeed(2.0)
            .windDirection(270.0).pasquillStabilityClass('D').isOffshore(true)
            .surfaceRoughness(0.0002).build()));
    cases.add(new WeatherCase("winter-F-stable-low-wind",
        BoundaryConditions.builder().ambientTemperature(5.0, "C").windSpeed(1.5)
            .windDirection(270.0).pasquillStabilityClass('F').isOffshore(true)
            .surfaceRoughness(0.0002).build()));
    cases.add(new WeatherCase("winter-D-high-wind",
        BoundaryConditions.builder().ambientTemperature(5.0, "C").windSpeed(15.0)
            .windDirection(270.0).pasquillStabilityClass('D').isOffshore(true)
            .surfaceRoughness(0.0002).build()));
    cases.add(new WeatherCase("summer-C-prevailing",
        BoundaryConditions.builder().ambientTemperature(15.0, "C").windSpeed(8.0)
            .windDirection(270.0).pasquillStabilityClass('C').isOffshore(true)
            .surfaceRoughness(0.0002).build()));
    cases.add(sectorWeather("sector-north-D", 0.0));
    cases.add(sectorWeather("sector-east-D", 90.0));
    cases.add(sectorWeather("sector-south-D", 180.0));
    cases.add(sectorWeather("sector-west-D", 270.0));
    return Collections.unmodifiableList(cases);
  }

  /**
   * Gets built-in consequence branches for QRA and CFD handoff.
   *
   * @return consequence branch list
   */
  public static List<ConsequenceBranch> defaultConsequenceBranches() {
    List<ConsequenceBranch> branches = new ArrayList<ConsequenceBranch>();
    branches.add(new ConsequenceBranch("immediate-ignition", "Jet fire", 0.05, "immediate",
        "Immediate ignition leading to jet fire."));
    branches.add(new ConsequenceBranch("delayed-ignition-vce", "VCE", 0.03, "delayed",
        "Delayed ignition in congested or confined region."));
    branches.add(new ConsequenceBranch("delayed-ignition-flash-fire", "Flash fire", 0.07, "delayed",
        "Delayed ignition without significant congestion."));
    branches.add(new ConsequenceBranch("toxic-only", "Toxic dispersion", 0.10, "none",
        "Unignited toxic exposure branch for sour or toxic releases."));
    branches.add(new ConsequenceBranch("no-ignition", "Unignited dispersion", 0.75, "none",
        "No ignition; dispersion endpoint only."));
    return branches;
  }

  /**
   * Creates a neutral-sector weather case.
   *
   * @param name weather case name
   * @param windDirectionDeg wind direction in degrees
   * @return weather case
   */
  private static WeatherCase sectorWeather(String name, double windDirectionDeg) {
    return new WeatherCase(name,
        BoundaryConditions.builder().ambientTemperature(10.0, "C").windSpeed(5.0)
            .windDirection(windDirectionDeg).pasquillStabilityClass('D').isOffshore(true)
            .surfaceRoughness(0.0002).build());
  }

  /**
   * Discovers stream release candidates from standalone streams and equipment outlet streams.
   *
   * @return stream candidates passing basic pressure and flow screening
   */
  private List<StreamCandidate> discoverStreamCandidates() {
    List<StreamCandidate> candidates = new ArrayList<StreamCandidate>();
    Set<String> seenStreamKeys = new HashSet<String>();
    for (ProcessEquipmentInterface equipment : processSystem.getUnitOperations()) {
      if (equipment instanceof StreamInterface) {
        addCandidate(candidates, seenStreamKeys, equipment, (StreamInterface) equipment);
      }
      for (StreamInterface stream : equipment.getOutletStreams()) {
        addCandidate(candidates, seenStreamKeys, equipment, stream);
      }
    }
    return candidates;
  }

  /**
   * Adds a stream candidate if it is valid and has not already been added.
   *
   * @param candidates target candidate list
   * @param seenStreamKeys identity keys already added
   * @param equipment owning or producing equipment
   * @param stream stream to evaluate
   */
  private void addCandidate(List<StreamCandidate> candidates, Set<String> seenStreamKeys,
      ProcessEquipmentInterface equipment, StreamInterface stream) {
    if (stream == null || !isEligibleStream(stream)) {
      return;
    }
    String key = streamIdentityKey(stream);
    if (seenStreamKeys.add(key)) {
      candidates.add(new StreamCandidate(equipment, stream));
    }
  }

  /**
   * Checks whether a stream should be screened for release dispersion.
   *
   * @param stream stream to check
   * @return true if the stream has a fluid, positive flow and sufficient pressure
   */
  private boolean isEligibleStream(StreamInterface stream) {
    try {
      SystemInterface fluid = stream.getThermoSystem();
      return fluid != null && stream.getFlowRate("kg/sec") >= minimumMassFlowRateKgPerS
          && stream.getPressure("bara") >= minimumPressureBara;
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Generates one release-dispersion scenario from a stream candidate.
   *
   * @param candidate stream candidate to screen
   * @param releaseCase release taxonomy case
   * @param weatherCase weather case
   * @return generated scenario, or null if source-term generation gives no release
   */
  private ReleaseDispersionScenario generateScenario(StreamCandidate candidate,
      ReleaseCase releaseCase, WeatherCase weatherCase) {
    StreamInterface stream = candidate.stream;
    double effectiveHoleDiameterM =
        releaseCase.effectiveHoleDiameter(fullBoreDiameterM, holeDiameterM);
    double effectiveInventoryVolumeM3 = effectiveInventoryVolume();
    BoundaryConditions weather = weatherCase.getBoundaryConditions();
    String scenarioName = scenarioName(candidate, releaseCase, weatherCase);
    SourceTermResult sourceTerm =
        LeakModel.builder().fluid(stream.getThermoSystem()).holeDiameter(effectiveHoleDiameterM)
            .orientation(releaseOrientation).vesselVolume(effectiveInventoryVolumeM3)
            .backPressure(backPressureFor(weather), "bar").scenarioName(scenarioName).build()
            .calculateSourceTerm(releaseDurationSeconds, timeStepSeconds);

    if (sourceTerm.getPeakMassFlowRate() <= 0.0) {
      return null;
    }

    GasDispersionAnalyzer.Builder builder = GasDispersionAnalyzer.builder()
        .scenarioName(scenarioName).fluid(stream.getThermoSystem()).sourceTerm(sourceTerm)
        .boundaryConditions(weather).releaseHeight(releaseHeightM).modelSelection(modelSelection);
    if (toxicComponentName != null && Double.isFinite(toxicThresholdPpm)) {
      builder.toxicEndpoint(toxicComponentName, toxicThresholdPpm);
    }
    GasDispersionResult dispersionResult = builder.build().analyze();

    return new ReleaseDispersionScenario(safeName(processSystem.getName(), "process"), scenarioName,
        safeName(candidate.equipment.getName(), "equipment"),
        candidate.equipment.getClass().getSimpleName(), safeName(stream.getName(), "stream"),
        stream.getPressure("bara"), stream.getTemperature(), stream.getFlowRate("kg/sec"),
        effectiveHoleDiameterM, effectiveInventoryVolumeM3, releaseDurationSeconds, releaseHeightM,
        releaseOrientation, releaseCase, weatherCase, sourceTerm, dispersionResult,
        trappedInventoryResult, componentMoleFractions(stream.getThermoSystem()),
        consequenceBranches);
  }

  /**
   * Gets the active release case list.
   *
   * @return active release cases
   */
  private List<ReleaseCase> activeReleaseCases() {
    if (scenarioTaxonomyEnabled) {
      if (releaseCases.isEmpty()) {
        return defaultReleaseTaxonomy();
      }
      return new ArrayList<ReleaseCase>(releaseCases);
    }
    return Collections.singletonList(ReleaseCase.CONFIGURED);
  }

  /**
   * Gets the active weather case list.
   *
   * @return active weather cases
   */
  private List<WeatherCase> activeWeatherCases() {
    if (weatherCases.isEmpty()) {
      return Collections.singletonList(new WeatherCase("configured-weather", boundaryConditions));
    }
    return new ArrayList<WeatherCase>(weatherCases);
  }

  /**
   * Gets the effective inventory volume for source-term calculations.
   *
   * @return inventory volume in m3
   */
  private double effectiveInventoryVolume() {
    if (trappedInventoryResult != null) {
      return effectiveVolume(trappedInventoryResult);
    }
    return inventoryVolumeM3;
  }

  /**
   * Gets the source-term back pressure for a weather case.
   *
   * @param weather weather conditions
   * @return back pressure in bara
   */
  private double backPressureFor(BoundaryConditions weather) {
    if (backPressureManuallySet) {
      return backPressureBara;
    }
    return weather.getAtmosphericPressureBar();
  }

  /**
   * Creates a readable scenario name.
   *
   * @param candidate stream candidate
   * @param releaseCase release case
   * @param weatherCase weather case
   * @return scenario name
   */
  private static String scenarioName(StreamCandidate candidate, ReleaseCase releaseCase,
      WeatherCase weatherCase) {
    String equipmentName = safeName(candidate.equipment.getName(), "equipment");
    String streamName = safeName(candidate.stream.getName(), "stream");
    return "Leak from " + equipmentName + " / " + streamName + " - " + releaseCase.getCaseName()
        + " - " + weatherCase.getName();
  }

  /**
   * Returns a fallback name when a process object name is empty.
   *
   * @param name original name
   * @param fallback fallback name
   * @return non-empty name
   */
  private static String safeName(String name, String fallback) {
    if (name == null || name.trim().isEmpty()) {
      return fallback;
    }
    return name;
  }

  /**
   * Converts a diameter value to meters.
   *
   * @param value diameter value
   * @param unit diameter unit
   * @return diameter in m
   */
  private static double toMeters(double value, String unit) {
    if (value <= 0.0) {
      throw new IllegalArgumentException("diameter must be positive");
    }
    if ("mm".equalsIgnoreCase(unit)) {
      return value / 1000.0;
    }
    if ("in".equalsIgnoreCase(unit)) {
      return value * 0.0254;
    }
    return value;
  }

  /**
   * Gets an effective source volume from a trapped inventory result.
   *
   * @param inventoryResult trapped inventory result
   * @return gas volume, or total volume when gas volume is zero
   */
  private static double effectiveVolume(InventoryResult inventoryResult) {
    if (inventoryResult.getTotalGasVolumeM3() > 0.0) {
      return inventoryResult.getTotalGasVolumeM3();
    }
    return inventoryResult.getTotalVolumeM3();
  }

  /**
   * Creates a stable stream identity key for duplicate suppression.
   *
   * @param stream stream to identify
   * @return identity key
   */
  private static String streamIdentityKey(StreamInterface stream) {
    String name = stream.getName() == null ? "" : stream.getName();
    return System.identityHashCode(stream) + ":" + name;
  }

  /**
   * Creates component mole-fraction metadata.
   *
   * @param fluid stream fluid
   * @return component mole fraction map
   */
  private static Map<String, Double> componentMoleFractions(SystemInterface fluid) {
    Map<String, Double> composition = new LinkedHashMap<String, Double>();
    if (fluid == null) {
      return composition;
    }
    SystemInterface clonedFluid = fluid.clone();
    try {
      clonedFluid.init(0);
      PhaseInterface phase = clonedFluid.getPhase(0);
      for (int index = 0; index < phase.getNumberOfComponents(); index++) {
        ComponentInterface component = phase.getComponent(index);
        composition.put(component.getComponentName(), Double.valueOf(component.getx()));
      }
    } catch (Exception ex) {
      return composition;
    }
    return composition;
  }

  /** Stream candidate discovered from the flowsheet. */
  private static final class StreamCandidate implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final ProcessEquipmentInterface equipment;
    private final StreamInterface stream;

    /**
     * Creates a stream candidate.
     *
     * @param equipment equipment that owns or produces the stream
     * @param stream stream to screen
     */
    private StreamCandidate(ProcessEquipmentInterface equipment, StreamInterface stream) {
      this.equipment = equipment;
      this.stream = stream;
    }
  }

  /** Built-in release cases for industrial screening taxonomies. */
  public enum ReleaseCase {
    /** Single configured leak case using the generator hole diameter. */
    CONFIGURED("configured leak", "configured", Double.NaN, Double.NaN,
        "Single user-configured hole size."),
    /** Five millimetre process leak. */
    FIVE_MM_HOLE("5 mm process leak", "hole-size", 0.005, 1.0e-3,
        "Small process leak screening case."),
    /** Ten millimetre process leak. */
    TEN_MM_HOLE("10 mm process leak", "hole-size", 0.010, 3.0e-4,
        "Medium process leak screening case."),
    /** Twenty-five millimetre process leak. */
    TWENTY_FIVE_MM_HOLE("25 mm process leak", "hole-size", 0.025, 1.0e-4,
        "Large process leak screening case."),
    /** Fifty millimetre process leak. */
    FIFTY_MM_HOLE("50 mm process leak", "hole-size", 0.050, 3.0e-5,
        "Major process leak screening case."),
    /** Full-bore rupture using the configured full-bore diameter. */
    FULL_BORE_RUPTURE("full-bore rupture", "rupture", Double.NaN, 1.0e-6,
        "Full-bore rupture; diameter is taken from fullBoreDiameter()."),
    /** Flange leak screening case. */
    FLANGE_LEAK("flange leak", "failure-mode", 0.010, 1.0e-3,
        "Representative flange or gasket leak."),
    /** Instrument connection leak screening case. */
    INSTRUMENT_LEAK("instrument leak", "failure-mode", 0.005, 2.0e-3,
        "Small-bore instrument connection leak."),
    /** Dropped-object damage screening case. */
    DROPPED_OBJECT_DAMAGE("dropped-object damage", "failure-mode", 0.050, 1.0e-5,
        "Large leak caused by impact or dropped object damage.");

    private final String caseName;
    private final String category;
    private final double holeDiameterM;
    private final double screeningFrequencyPerYear;
    private final String description;

    /**
     * Creates a release case.
     *
     * @param caseName readable case name
     * @param category case category
     * @param holeDiameterM representative hole diameter in m
     * @param screeningFrequencyPerYear generic screening frequency in 1/year
     * @param description case description
     */
    ReleaseCase(String caseName, String category, double holeDiameterM,
        double screeningFrequencyPerYear, String description) {
      this.caseName = caseName;
      this.category = category;
      this.holeDiameterM = holeDiameterM;
      this.screeningFrequencyPerYear = screeningFrequencyPerYear;
      this.description = description;
    }

    /**
     * Gets the readable case name.
     *
     * @return case name
     */
    public String getCaseName() {
      return caseName;
    }

    /**
     * Gets the case category.
     *
     * @return case category
     */
    public String getCategory() {
      return category;
    }

    /**
     * Gets the generic screening frequency.
     *
     * @return frequency in 1/year, or NaN when not supplied
     */
    public double getScreeningFrequencyPerYear() {
      return screeningFrequencyPerYear;
    }

    /**
     * Gets the case description.
     *
     * @return case description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Gets the effective hole diameter.
     *
     * @param fullBoreDiameterM full-bore diameter in m
     * @param configuredHoleDiameterM configured hole diameter in m
     * @return effective hole diameter in m
     */
    private double effectiveHoleDiameter(double fullBoreDiameterM, double configuredHoleDiameterM) {
      if (this == CONFIGURED) {
        return configuredHoleDiameterM;
      }
      if (this == FULL_BORE_RUPTURE) {
        return fullBoreDiameterM;
      }
      return holeDiameterM;
    }
  }

  /** Named weather case for batch consequence screening. */
  public static class WeatherCase implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String name;
    private final BoundaryConditions boundaryConditions;

    /**
     * Creates a weather case.
     *
     * @param name weather case name
     * @param boundaryConditions boundary conditions
     */
    public WeatherCase(String name, BoundaryConditions boundaryConditions) {
      if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("weather case name must not be empty");
      }
      if (boundaryConditions == null) {
        throw new IllegalArgumentException("boundaryConditions must not be null");
      }
      this.name = name;
      this.boundaryConditions = boundaryConditions;
    }

    /**
     * Gets the weather case name.
     *
     * @return weather case name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets the boundary conditions.
     *
     * @return boundary conditions
     */
    public BoundaryConditions getBoundaryConditions() {
      return boundaryConditions;
    }

    /**
     * Converts the weather case to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("name", name);
      map.put("ambientTemperatureK", Double.valueOf(boundaryConditions.getAmbientTemperature()));
      map.put("windSpeedMPerS", Double.valueOf(boundaryConditions.getWindSpeed()));
      map.put("windDirectionDeg", Double.valueOf(boundaryConditions.getWindDirection()));
      map.put("stabilityClass", Character.toString(boundaryConditions.getPasquillStabilityClass()));
      map.put("offshore", Boolean.valueOf(boundaryConditions.isOffshore()));
      return map;
    }
  }

  /** Consequence branch for ignition and no-ignition outcomes. */
  public static class ConsequenceBranch implements Serializable {
    private static final long serialVersionUID = 1000L;
    private final String branchId;
    private final String consequenceType;
    private final double conditionalProbability;
    private final String ignitionTiming;
    private final String description;

    /**
     * Creates a consequence branch.
     *
     * @param branchId stable branch id
     * @param consequenceType consequence type, for example Jet fire or VCE
     * @param conditionalProbability branch probability conditional on the release, from 0 to 1
     * @param ignitionTiming ignition timing, for example immediate, delayed or none
     * @param description branch description
     */
    public ConsequenceBranch(String branchId, String consequenceType, double conditionalProbability,
        String ignitionTiming, String description) {
      if (conditionalProbability < 0.0 || conditionalProbability > 1.0) {
        throw new IllegalArgumentException("conditionalProbability must be in [0,1]");
      }
      this.branchId = branchId == null ? "branch" : branchId;
      this.consequenceType = consequenceType == null ? "unspecified" : consequenceType;
      this.conditionalProbability = conditionalProbability;
      this.ignitionTiming = ignitionTiming == null ? "unspecified" : ignitionTiming;
      this.description = description == null ? "" : description;
    }

    /**
     * Gets branch id.
     *
     * @return branch id
     */
    public String getBranchId() {
      return branchId;
    }

    /**
     * Gets consequence type.
     *
     * @return consequence type
     */
    public String getConsequenceType() {
      return consequenceType;
    }

    /**
     * Gets conditional probability.
     *
     * @return conditional probability
     */
    public double getConditionalProbability() {
      return conditionalProbability;
    }

    /**
     * Gets ignition timing.
     *
     * @return ignition timing
     */
    public String getIgnitionTiming() {
      return ignitionTiming;
    }

    /**
     * Converts the branch to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("branchId", branchId);
      map.put("consequenceType", consequenceType);
      map.put("conditionalProbability", Double.valueOf(conditionalProbability));
      map.put("ignitionTiming", ignitionTiming);
      map.put("description", description);
      return map;
    }
  }

  /** Result from one automatically generated release-dispersion scenario. */
  public static class ReleaseDispersionScenario implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String processName;
    private final String scenarioName;
    private final String equipmentName;
    private final String equipmentType;
    private final String streamName;
    private final double streamPressureBara;
    private final double streamTemperatureK;
    private final double streamMassFlowRateKgPerS;
    private final double holeDiameterM;
    private final double inventoryVolumeM3;
    private final double releaseDurationSeconds;
    private final double releaseHeightM;
    private final ReleaseOrientation releaseOrientation;
    private final ReleaseCase releaseCase;
    private final WeatherCase weatherCase;
    private final SourceTermResult sourceTerm;
    private final GasDispersionResult dispersionResult;
    private final InventoryResult trappedInventoryResult;
    private final Map<String, Double> componentMoleFractions;
    private final List<ConsequenceBranch> consequenceBranches;

    /**
     * Creates a release-dispersion scenario result.
     *
     * @param processName process system name
     * @param scenarioName scenario name
     * @param equipmentName equipment name
     * @param equipmentType equipment Java type
     * @param streamName stream name
     * @param streamPressureBara stream pressure in bara
     * @param streamTemperatureK stream temperature in K
     * @param streamMassFlowRateKgPerS stream mass flow rate in kg/s
     * @param holeDiameterM leak hole diameter in m
     * @param inventoryVolumeM3 representative inventory volume in m3
     * @param releaseDurationSeconds source-term duration in s
     * @param releaseHeightM release height in m
     * @param releaseOrientation release orientation
     * @param releaseCase release taxonomy case
     * @param weatherCase weather case
     * @param sourceTerm source-term result
     * @param dispersionResult dispersion screening result
     * @param trappedInventoryResult optional trapped inventory result
     * @param componentMoleFractions stream component mole fractions
     * @param consequenceBranches consequence branches
     */
    public ReleaseDispersionScenario(String processName, String scenarioName, String equipmentName,
        String equipmentType, String streamName, double streamPressureBara,
        double streamTemperatureK, double streamMassFlowRateKgPerS, double holeDiameterM,
        double inventoryVolumeM3, double releaseDurationSeconds, double releaseHeightM,
        ReleaseOrientation releaseOrientation, ReleaseCase releaseCase, WeatherCase weatherCase,
        SourceTermResult sourceTerm, GasDispersionResult dispersionResult,
        InventoryResult trappedInventoryResult, Map<String, Double> componentMoleFractions,
        List<ConsequenceBranch> consequenceBranches) {
      this.processName = processName;
      this.scenarioName = scenarioName;
      this.equipmentName = equipmentName;
      this.equipmentType = equipmentType;
      this.streamName = streamName;
      this.streamPressureBara = streamPressureBara;
      this.streamTemperatureK = streamTemperatureK;
      this.streamMassFlowRateKgPerS = streamMassFlowRateKgPerS;
      this.holeDiameterM = holeDiameterM;
      this.inventoryVolumeM3 = inventoryVolumeM3;
      this.releaseDurationSeconds = releaseDurationSeconds;
      this.releaseHeightM = releaseHeightM;
      this.releaseOrientation = releaseOrientation;
      this.releaseCase = releaseCase;
      this.weatherCase = weatherCase;
      this.sourceTerm = sourceTerm;
      this.dispersionResult = dispersionResult;
      this.trappedInventoryResult = trappedInventoryResult;
      this.componentMoleFractions = new LinkedHashMap<String, Double>(componentMoleFractions);
      this.consequenceBranches = new ArrayList<ConsequenceBranch>(consequenceBranches);
    }

    /**
     * Gets the process name.
     *
     * @return process name
     */
    public String getProcessName() {
      return processName;
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
     * Gets the equipment name.
     *
     * @return equipment name
     */
    public String getEquipmentName() {
      return equipmentName;
    }

    /**
     * Gets the equipment Java type.
     *
     * @return equipment type
     */
    public String getEquipmentType() {
      return equipmentType;
    }

    /**
     * Gets the stream name.
     *
     * @return stream name
     */
    public String getStreamName() {
      return streamName;
    }

    /**
     * Gets the stream pressure.
     *
     * @return stream pressure in bara
     */
    public double getStreamPressureBara() {
      return streamPressureBara;
    }

    /**
     * Gets the stream temperature.
     *
     * @return stream temperature in K
     */
    public double getStreamTemperatureK() {
      return streamTemperatureK;
    }

    /**
     * Gets the stream mass flow rate.
     *
     * @return stream mass flow rate in kg/s
     */
    public double getStreamMassFlowRateKgPerS() {
      return streamMassFlowRateKgPerS;
    }

    /**
     * Gets the leak hole diameter.
     *
     * @return hole diameter in m
     */
    public double getHoleDiameterM() {
      return holeDiameterM;
    }

    /**
     * Gets the representative inventory volume.
     *
     * @return inventory volume in m3
     */
    public double getInventoryVolumeM3() {
      return inventoryVolumeM3;
    }

    /**
     * Gets the source-term duration.
     *
     * @return release duration in s
     */
    public double getReleaseDurationSeconds() {
      return releaseDurationSeconds;
    }

    /**
     * Gets release height.
     *
     * @return release height in m
     */
    public double getReleaseHeightM() {
      return releaseHeightM;
    }

    /**
     * Gets release orientation.
     *
     * @return release orientation
     */
    public ReleaseOrientation getReleaseOrientation() {
      return releaseOrientation;
    }

    /**
     * Gets release case name.
     *
     * @return release case name
     */
    public String getReleaseCaseName() {
      return releaseCase.getCaseName();
    }

    /**
     * Gets release case category.
     *
     * @return release category
     */
    public String getReleaseCaseCategory() {
      return releaseCase.getCategory();
    }

    /**
     * Gets release frequency.
     *
     * @return generic screening frequency in 1/year, or NaN
     */
    public double getReleaseFrequencyPerYear() {
      return releaseCase.getScreeningFrequencyPerYear();
    }

    /**
     * Gets weather case name.
     *
     * @return weather case name
     */
    public String getWeatherCaseName() {
      return weatherCase.getName();
    }

    /**
     * Gets boundary conditions.
     *
     * @return boundary conditions
     */
    public BoundaryConditions getBoundaryConditions() {
      return weatherCase.getBoundaryConditions();
    }

    /**
     * Gets the source-term result.
     *
     * @return source-term result
     */
    public SourceTermResult getSourceTerm() {
      return sourceTerm;
    }

    /**
     * Gets the dispersion result.
     *
     * @return gas dispersion result
     */
    public GasDispersionResult getDispersionResult() {
      return dispersionResult;
    }

    /**
     * Gets optional trapped inventory result.
     *
     * @return trapped inventory result, or null when representative volume is used
     */
    public InventoryResult getTrappedInventoryResult() {
      return trappedInventoryResult;
    }

    /**
     * Gets component mole fractions.
     *
     * @return immutable component mole-fraction map
     */
    public Map<String, Double> getComponentMoleFractions() {
      return Collections.unmodifiableMap(componentMoleFractions);
    }

    /**
     * Gets consequence branches.
     *
     * @return immutable branch list
     */
    public List<ConsequenceBranch> getConsequenceBranches() {
      return Collections.unmodifiableList(consequenceBranches);
    }

    /**
     * Checks whether the scenario has a flammable cloud endpoint.
     *
     * @return true if an LFL endpoint was calculated
     */
    public boolean hasFlammableCloud() {
      return dispersionResult != null && dispersionResult.hasFlammableCloud();
    }

    /**
     * Checks whether the scenario has a toxic endpoint.
     *
     * @return true if a toxic endpoint was calculated
     */
    public boolean hasToxicEndpoint() {
      return dispersionResult != null && dispersionResult.hasToxicEndpoint();
    }

    /**
     * Converts this scenario to a CFD source-term handoff case.
     *
     * @return CFD source-term case
     */
    public CfdSourceTermCase toCfdSourceTermCase() {
      return CfdSourceTermCase.fromScenario(this);
    }

    /**
     * Converts the scenario to a JSON-friendly map.
     *
     * @return ordered map representation
     */
    public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      map.put("processName", processName);
      map.put("scenarioName", scenarioName);
      map.put("equipmentName", equipmentName);
      map.put("equipmentType", equipmentType);
      map.put("streamName", streamName);
      map.put("streamPressureBara", Double.valueOf(streamPressureBara));
      map.put("streamTemperatureK", Double.valueOf(streamTemperatureK));
      map.put("streamMassFlowRateKgPerS", Double.valueOf(streamMassFlowRateKgPerS));
      map.put("releaseCase", releaseCase.getCaseName());
      map.put("releaseCaseCategory", releaseCase.getCategory());
      map.put("weatherCase", weatherCase.toMap());
      map.put("holeDiameterM", Double.valueOf(holeDiameterM));
      map.put("inventoryVolumeM3", Double.valueOf(inventoryVolumeM3));
      map.put("releaseDurationSeconds", Double.valueOf(releaseDurationSeconds));
      map.put("releaseHeightM", Double.valueOf(releaseHeightM));
      map.put("releaseOrientation", releaseOrientation.name());
      map.put("peakMassReleaseRateKgPerS", Double.valueOf(sourceTerm.getPeakMassFlowRate()));
      map.put("totalMassReleasedKg", Double.valueOf(sourceTerm.getTotalMassReleased()));
      map.put("componentMoleFractions", componentMoleFractions);
      if (trappedInventoryResult != null) {
        map.put("trappedInventory", trappedInventoryResult.toMap());
      }
      List<Map<String, Object>> branchMaps = new ArrayList<Map<String, Object>>();
      for (ConsequenceBranch branch : consequenceBranches) {
        branchMaps.add(branch.toMap());
      }
      map.put("consequenceBranches", branchMaps);
      map.put("dispersionResult", dispersionResult.toJson());
      return map;
    }

    /**
     * Converts the scenario to compact JSON.
     *
     * @return JSON representation of the scenario
     */
    public String toJson() {
      return new GsonBuilder().setPrettyPrinting().create().toJson(toMap());
    }
  }
}
