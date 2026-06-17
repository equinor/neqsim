package neqsim.process.equipment.lng;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Top-level orchestrator for LNG ageing simulations.
 *
 * <p>
 * Coordinates all sub-models (layered tank, vapor space, BOG handling, rollover detection, heel
 * management, voyage profile) to produce a time-resolved simulation of LNG composition and quality
 * changes during transport.
 * </p>
 *
 * <p>
 * Usage pattern:
 * </p>
 *
 * <pre>
 * {@code
 * SystemInterface lng = new SystemSrkEos(111.0, 1.013);
 * lng.addComponent("methane", 0.92);
 * lng.addComponent("ethane", 0.05);
 * lng.addComponent("propane", 0.02);
 * lng.addComponent("nitrogen", 0.01);
 * lng.setMixingRule("classic");
 *
 * Stream feed = new Stream("LNG feed", lng);
 * feed.setFlowRate(140000.0, "m3/hr");
 * feed.run();
 *
 * LNGAgeingScenario scenario = new LNGAgeingScenario("Laden Voyage", feed);
 * scenario.setTankVolume(140000.0);
 * scenario.setInitialFillingRatio(0.98);
 * scenario.setSimulationTime(480.0);
 * scenario.setTimeStepHours(1.0);
 * scenario.setOverallHeatTransferCoeff(0.045);
 * scenario.setAmbientTemperature(308.15);
 * scenario.run();
 *
 * List&lt;LNGAgeingResult&gt; results = scenario.getResults();
 * }
 * </pre>
 *
 * @author NeqSim
 * @version 1.0
 */
public class LNGAgeingScenario extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1013L;

  /** Logger object. */
  private static final Logger logger = LogManager.getLogger(LNGAgeingScenario.class);

  /** Tank volume (m3). */
  private double tankVolume = 140000.0;

  /** Initial filling ratio (0-1). */
  private double initialFillingRatio = 0.98;

  /** Total simulation time (hours). */
  private double simulationTime = 480.0;

  /** Time step (hours). */
  private double timeStepHours = 1.0;

  /** Overall heat transfer coefficient (W/m2/K). */
  private double overallHeatTransferCoeff = 0.045;

  /** Ambient temperature (K). */
  private double ambientTemperature = 308.15;

  /** Tank surface area (m2). Estimated from volume if not set. */
  private double tankSurfaceArea = -1;

  /** Initial tank pressure (bara). */
  private double tankPressure = 1.013;

  /** Number of initial layers. */
  private int numberOfLayers = 1;

  /** Layered tank model. */
  private transient LNGTankLayeredModel tankModel;

  /** Vapor space model. */
  private LNGVaporSpaceModel vaporSpaceModel;

  /** Rollover detector. */
  private LNGRolloverDetector rolloverDetector;

  /** BOG handling network. */
  private LNGBOGHandlingNetwork bogNetwork;

  /** Heel manager. */
  private LNGHeelManager heelManager;

  /** Voyage profile (optional). */
  private LNGVoyageProfile voyageProfile;

  /** Operational events (loading, unloading, cooldown, etc.). */
  private List<OperationalEvent> operationalEvents;

  /** Tank geometry model (optional). */
  private TankGeometry tankGeometry;

  /** Methane number calculator (optional). */
  private MethaneNumberCalculator methaneNumberCalculator;

  /** Whether to use GERG-2008 for density calculations. */
  private boolean useGERG2008 = false;

  /** Time-series results. */
  private List<LNGAgeingResult> results;

  /** Inlet stream (LNG cargo). */
  private StreamInterface inletStream;

  /** BOG outlet stream. */
  private StreamInterface bogOutletStream;

  /** Aged LNG outlet stream. */
  private StreamInterface lngOutletStream;

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public LNGAgeingScenario(String name) {
    super(name);
    this.results = new ArrayList<LNGAgeingResult>();
    this.rolloverDetector = new LNGRolloverDetector();
    this.bogNetwork = new LNGBOGHandlingNetwork();
    this.heelManager = new LNGHeelManager();
    this.operationalEvents = new ArrayList<OperationalEvent>();
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inletStream LNG cargo stream
   */
  public LNGAgeingScenario(String name, StreamInterface inletStream) {
    this(name);
    this.inletStream = inletStream;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inletStream == null) {
      logger.error("No inlet stream set for LNGAgeingScenario");
      return;
    }

    SystemInterface lngFluid = inletStream.getFluid().clone();
    results.clear();

    // Estimate surface area from volume if not provided
    if (tankSurfaceArea < 0) {
      tankSurfaceArea = estimateSurfaceArea(tankVolume);
    }

    // Create and configure tank model from the inlet fluid
    tankModel = new LNGTankLayeredModel(lngFluid);
    tankModel.setTotalTankVolume(tankVolume);
    tankModel.setTankSurfaceArea(tankSurfaceArea);
    tankModel.setOverallHeatTransferCoeff(overallHeatTransferCoeff);
    tankModel.setTankPressure(tankPressure);

    // Wire advanced models if configured
    if (tankGeometry != null) {
      tankModel.setTankGeometry(tankGeometry);
    }
    if (methaneNumberCalculator != null) {
      tankModel.setMethaneNumberCalculator(methaneNumberCalculator);
    }
    tankModel.setUseGERG2008(useGERG2008);

    // Initialise from the inlet fluid
    double initialLiquidVolume = tankVolume * initialFillingRatio;
    tankModel.initialise(initialLiquidVolume);

    // Configure vapor space
    vaporSpaceModel = new LNGVaporSpaceModel(tankVolume);
    vaporSpaceModel.setMaxPressure(1.20);
    vaporSpaceModel.setMinPressure(1.00);
    vaporSpaceModel.setTankPressure(tankPressure);
    vaporSpaceModel.setVaporTemperature(tankModel.getBulkTemperature());
    vaporSpaceModel.setCurrentLiquidVolume(initialLiquidVolume);

    // Record initial state using step(0) equivalent
    LNGAgeingResult initialResult = new LNGAgeingResult();
    initialResult.setTimeHours(0);
    initialResult.setTemperature(tankModel.getBulkTemperature());
    initialResult.setPressure(tankPressure);
    initialResult.setDensity(tankModel.getBulkDensity());
    initialResult.setLiquidMoles(tankModel.getTotalLiquidMoles());
    initialResult.setLiquidComposition(
        new LinkedHashMap<String, Double>(tankModel.getBulkLiquidComposition()));
    initialResult.setAmbientTemperature(getAmbientTemperatureAt(0));
    initialResult.setNumberOfLayers(tankModel.getLayers().size());
    results.add(initialResult);

    // Time-stepping loop
    int numSteps = (int) Math.ceil(simulationTime / timeStepHours);
    for (int step = 1; step <= numSteps; step++) {
      double currentTime = step * timeStepHours;

      // Get ambient temperature (from profile or constant)
      double ambT = getAmbientTemperatureAt(currentTime);

      // Step the tank model — returns a fully populated result
      LNGAgeingResult stepResult = tankModel.step(timeStepHours, ambT);
      stepResult.setTimeHours(currentTime);
      stepResult.setAmbientTemperature(ambT);

      // Update vapor space with BOG info
      double bogMassKg = stepResult.getBogMassFlowRate() * timeStepHours;
      double liquidVol = stepResult.getLiquidVolume();
      vaporSpaceModel.update(bogMassKg, bogMassKg, liquidVol, stepResult.getVaporComposition(),
          stepResult.getTemperature());
      stepResult.setPressure(vaporSpaceModel.getTankPressure());

      // Rollover assessment
      LNGRolloverDetector.RolloverAssessment rollover =
          rolloverDetector.assess(tankModel.getLayers());
      stepResult.setMaxLayerDensityDifference(rollover.getMaxDensityDifference());
      stepResult.setRolloverRisk(rollover.getRiskLevel()
          .ordinal() >= LNGRolloverDetector.RolloverRiskLevel.MEDIUM.ordinal());

      // BOG handling disposition
      LNGBOGHandlingNetwork.BOGDisposition disposition =
          bogNetwork.calculateDisposition(stepResult.getBogMassFlowRate());

      results.add(stepResult);

      // Log warnings if rollover risk detected
      if (rollover.getRiskLevel().ordinal() >= LNGRolloverDetector.RolloverRiskLevel.MEDIUM
          .ordinal()) {
        logger.warn(String.format("Rollover risk %s at t=%.1f h: max drho=%.2f kg/m3",
            rollover.getRiskLevel(), currentTime, rollover.getMaxDensityDifference()));
      }
    }

    // Create outlet streams from final state
    createOutletStreams(lngFluid);

    logger.info(String.format("LNG ageing simulation complete: %d steps, %.1f hours, %d results",
        numSteps, simulationTime, results.size()));
  }

  /**
   * Create outlet streams from final simulation state.
   *
   * @param baseFluid the base fluid to clone for stream creation
   */
  private void createOutletStreams(SystemInterface baseFluid) {
    if (!results.isEmpty()) {
      LNGAgeingResult lastResult = results.get(results.size() - 1);
      Map<String, Double> liqComp = lastResult.getLiquidComposition();

      // Create aged LNG stream
      SystemInterface agedFluid = baseFluid.clone();
      for (int i = 0; i < agedFluid.getNumberOfComponents(); i++) {
        String compName = agedFluid.getComponent(i).getComponentName();
        double moleFrac = liqComp.containsKey(compName) ? liqComp.get(compName) : 0;
        agedFluid.addComponent(i, moleFrac - agedFluid.getComponent(i).getz());
      }
      agedFluid.setTemperature(lastResult.getTemperature());
      agedFluid.setPressure(lastResult.getPressure());
      lngOutletStream = new Stream("aged LNG", agedFluid);
    }
  }

  /**
   * Get ambient temperature at a given time, using voyage profile if available.
   *
   * @param timeHours simulation time (hours)
   * @return ambient temperature (K)
   */
  private double getAmbientTemperatureAt(double timeHours) {
    if (voyageProfile != null) {
      return voyageProfile.getAmbientTemperatureAt(timeHours);
    }
    return ambientTemperature;
  }

  /**
   * Estimate tank surface area from volume assuming cylindrical shape.
   *
   * @param volume tank volume (m3)
   * @return estimated surface area (m2)
   */
  private double estimateSurfaceArea(double volume) {
    // Assume L/D = 3.5 for membrane tank
    double aspectRatio = 3.5;
    double diameter = Math.pow(4.0 * volume / (Math.PI * aspectRatio), 1.0 / 3.0);
    double length = aspectRatio * diameter;
    return Math.PI * diameter * length + 2.0 * Math.PI * diameter * diameter / 4.0;
  }

  /**
   * Get the time-series results.
   *
   * @return list of ageing results at each time step
   */
  public List<LNGAgeingResult> getResults() {
    return results;
  }

  /**
   * Get the layered tank model for advanced configuration.
   *
   * @return layered tank model
   */
  public LNGTankLayeredModel getTankModel() {
    return tankModel;
  }

  /**
   * Get the vapor space model.
   *
   * @return vapor space model
   */
  public LNGVaporSpaceModel getVaporSpaceModel() {
    return vaporSpaceModel;
  }

  /**
   * Get the rollover detector.
   *
   * @return rollover detector
   */
  public LNGRolloverDetector getRolloverDetector() {
    return rolloverDetector;
  }

  /**
   * Get the BOG handling network.
   *
   * @return BOG network
   */
  public LNGBOGHandlingNetwork getBogNetwork() {
    return bogNetwork;
  }

  /**
   * Get the heel manager.
   *
   * @return heel manager
   */
  public LNGHeelManager getHeelManager() {
    return heelManager;
  }

  /**
   * Set the voyage profile.
   *
   * @param profile voyage profile
   */
  public void setVoyageProfile(LNGVoyageProfile profile) {
    this.voyageProfile = profile;
  }

  /**
   * Get the voyage profile.
   *
   * @return voyage profile or null
   */
  public LNGVoyageProfile getVoyageProfile() {
    return voyageProfile;
  }

  /**
   * Set tank geometry model.
   *
   * @param geometry tank geometry
   */
  public void setTankGeometry(TankGeometry geometry) {
    this.tankGeometry = geometry;
  }

  /**
   * Get tank geometry model.
   *
   * @return tank geometry or null
   */
  public TankGeometry getTankGeometry() {
    return tankGeometry;
  }

  /**
   * Set methane number calculator.
   *
   * @param calculator methane number calculator
   */
  public void setMethaneNumberCalculator(MethaneNumberCalculator calculator) {
    this.methaneNumberCalculator = calculator;
  }

  /**
   * Get methane number calculator.
   *
   * @return calculator or null
   */
  public MethaneNumberCalculator getMethaneNumberCalculator() {
    return methaneNumberCalculator;
  }

  /**
   * Enable GERG-2008 for density calculations.
   *
   * @param use true to use GERG-2008
   */
  public void setUseGERG2008(boolean use) {
    this.useGERG2008 = use;
  }

  /**
   * Check if GERG-2008 is enabled.
   *
   * @return true if using GERG-2008
   */
  public boolean isUseGERG2008() {
    return useGERG2008;
  }

  /**
   * Add an operational event (loading, unloading, cooldown, etc.).
   *
   * @param event operational event
   */
  public void addOperationalEvent(OperationalEvent event) {
    operationalEvents.add(event);
  }

  /**
   * Get all operational events.
   *
   * @return list of events
   */
  public List<OperationalEvent> getOperationalEvents() {
    return operationalEvents;
  }

  /**
   * Set tank volume.
   *
   * @param volume tank volume (m3)
   */
  public void setTankVolume(double volume) {
    this.tankVolume = volume;
  }

  /**
   * Get tank volume.
   *
   * @return tank volume (m3)
   */
  public double getTankVolume() {
    return tankVolume;
  }

  /**
   * Set initial filling ratio.
   *
   * @param ratio filling ratio (0-1)
   */
  public void setInitialFillingRatio(double ratio) {
    this.initialFillingRatio = Math.max(0, Math.min(1, ratio));
  }

  /**
   * Get initial filling ratio.
   *
   * @return filling ratio (0-1)
   */
  public double getInitialFillingRatio() {
    return initialFillingRatio;
  }

  /**
   * Set total simulation time.
   *
   * @param hours simulation time (hours)
   */
  public void setSimulationTime(double hours) {
    this.simulationTime = hours;
  }

  /**
   * Get total simulation time.
   *
   * @return simulation time (hours)
   */
  public double getSimulationTime() {
    return simulationTime;
  }

  /**
   * Set time step.
   *
   * @param hours time step (hours)
   */
  public void setTimeStepHours(double hours) {
    this.timeStepHours = hours;
  }

  /**
   * Get time step.
   *
   * @return time step (hours)
   */
  public double getTimeStepHours() {
    return timeStepHours;
  }

  /**
   * Set overall heat transfer coefficient.
   *
   * @param u heat transfer coefficient (W/m2/K)
   */
  public void setOverallHeatTransferCoeff(double u) {
    this.overallHeatTransferCoeff = u;
  }

  /**
   * Get overall heat transfer coefficient.
   *
   * @return heat transfer coefficient (W/m2/K)
   */
  public double getOverallHeatTransferCoeff() {
    return overallHeatTransferCoeff;
  }

  /**
   * Set ambient temperature.
   *
   * @param temperature ambient temperature (K)
   */
  public void setAmbientTemperature(double temperature) {
    this.ambientTemperature = temperature;
  }

  /**
   * Get ambient temperature.
   *
   * @return ambient temperature (K)
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set tank surface area.
   *
   * @param area surface area (m2)
   */
  public void setTankSurfaceArea(double area) {
    this.tankSurfaceArea = area;
  }

  /**
   * Get tank surface area.
   *
   * @return surface area (m2), or -1 if auto-estimated
   */
  public double getTankSurfaceArea() {
    return tankSurfaceArea;
  }

  /**
   * Set initial tank pressure.
   *
   * @param pressure pressure (bara)
   */
  public void setTankPressure(double pressure) {
    this.tankPressure = pressure;
  }

  /**
   * Get tank pressure.
   *
   * @return pressure (bara)
   */
  public double getTankPressure() {
    return tankPressure;
  }

  /**
   * Set number of initial layers.
   *
   * @param layers number of layers
   */
  public void setNumberOfLayers(int layers) {
    this.numberOfLayers = layers;
  }

  /**
   * Get number of initial layers.
   *
   * @return number of layers
   */
  public int getNumberOfLayers() {
    return numberOfLayers;
  }

  /**
   * Get the BOG outlet stream.
   *
   * @return BOG stream or null if not yet run
   */
  public StreamInterface getBogOutletStream() {
    return bogOutletStream;
  }

  /**
   * Get the aged LNG outlet stream.
   *
   * @return aged LNG stream or null if not yet run
   */
  public StreamInterface getLngOutletStream() {
    return lngOutletStream;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (inletStream != null) {
      streams.add(inletStream);
    }
    return streams;
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    List<StreamInterface> streams = new ArrayList<StreamInterface>();
    if (lngOutletStream != null) {
      streams.add(lngOutletStream);
    }
    if (bogOutletStream != null) {
      streams.add(bogOutletStream);
    }
    return streams;
  }

  /**
   * Get a summary of the final results.
   *
   * @return formatted summary string
   */
  public String getResultsSummary() {
    if (results.isEmpty()) {
      return "No results — simulation not yet run.";
    }

    LNGAgeingResult first = results.get(0);
    LNGAgeingResult last = results.get(results.size() - 1);

    StringBuilder sb = new StringBuilder();
    sb.append("=== LNG Ageing Simulation Summary ===\n");
    sb.append(
        String.format("Duration: %.1f hours (%.1f days)\n", simulationTime, simulationTime / 24.0));
    sb.append(String.format("Tank volume: %.0f m3, fill: %.0f%%\n", tankVolume,
        initialFillingRatio * 100));
    sb.append(String.format("Heat transfer coeff: %.4f W/m2/K\n", overallHeatTransferCoeff));
    sb.append("\n--- Initial vs Final ---\n");
    sb.append(String.format("Temperature: %.2f -> %.2f K (%.2f -> %.2f C)\n",
        first.getTemperature(), last.getTemperature(), first.getTemperature() - 273.15,
        last.getTemperature() - 273.15));
    sb.append(
        String.format("Density: %.2f -> %.2f kg/m3\n", first.getDensity(), last.getDensity()));
    sb.append(String.format("Wobbe Index: %.2f -> %.2f MJ/Sm3\n", first.getWobbeIndex(),
        last.getWobbeIndex()));
    sb.append(String.format("GCV (vol): %.2f -> %.2f MJ/Sm3\n", first.getGcvVolumetric(),
        last.getGcvVolumetric()));
    sb.append(
        String.format("GCV (mass): %.4f -> %.4f MJ/kg\n", first.getGcvMass(), last.getGcvMass()));
    sb.append(String.format("Liquid volume: %.0f -> %.0f m3\n", first.getLiquidVolume(),
        last.getLiquidVolume()));

    // BOR statistics
    double maxBOR = 0;
    double avgBOR = 0;
    for (LNGAgeingResult r : results) {
      avgBOR += r.getBoilOffRatePctPerDay();
      maxBOR = Math.max(maxBOR, r.getBoilOffRatePctPerDay());
    }
    avgBOR /= results.size();
    sb.append(String.format("\n--- Boil-Off ---\n"));
    sb.append(String.format("Average BOR: %.4f %%/day\n", avgBOR));
    sb.append(String.format("Max BOR: %.4f %%/day\n", maxBOR));

    return sb.toString();
  }

  /**
   * Represents an operational event during an LNG voyage or storage period.
   *
   * <p>
   * Events change the tank operating mode at a specified time. Supported event types include
   * loading/unloading (which add/remove cargo), cooldown (spray-cooling to reduce temperature), and
   * mode changes (e.g., switch between laden voyage and ballast).
   * </p>
   *
   * @author NeqSim
   * @version 1.0
   */
  public static class OperationalEvent implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1030L;

    /**
     * Event type enumeration.
     */
    public enum EventType {
      /** Loading LNG into the tank. */
      LOADING,
      /** Unloading LNG from the tank. */
      UNLOADING,
      /** Cooldown spray cooling. */
      COOLDOWN,
      /** Switch to laden voyage mode. */
      LADEN_VOYAGE,
      /** Switch to ballast voyage mode. */
      BALLAST_VOYAGE,
      /** Port waiting / anchoring. */
      PORT_WAIT,
      /** Custom event. */
      CUSTOM
    }

    /** Event type. */
    private EventType eventType;

    /** Event start time (hours from simulation start). */
    private double startTimeHours;

    /** Event duration (hours). */
    private double durationHours;

    /** Event description. */
    private String description;

    /** Rate parameter for loading/unloading (m3/hr). */
    private double rateM3PerHour;

    /**
     * Constructor.
     *
     * @param eventType type of event
     * @param startTimeHours start time (hours)
     * @param durationHours duration (hours)
     */
    public OperationalEvent(EventType eventType, double startTimeHours, double durationHours) {
      this.eventType = eventType;
      this.startTimeHours = startTimeHours;
      this.durationHours = durationHours;
      this.description = eventType.name();
    }

    /**
     * Get event type.
     *
     * @return event type
     */
    public EventType getEventType() {
      return eventType;
    }

    /**
     * Get start time.
     *
     * @return start time (hours)
     */
    public double getStartTimeHours() {
      return startTimeHours;
    }

    /**
     * Get duration.
     *
     * @return duration (hours)
     */
    public double getDurationHours() {
      return durationHours;
    }

    /**
     * Get description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }

    /**
     * Set description.
     *
     * @param description event description
     */
    public void setDescription(String description) {
      this.description = description;
    }

    /**
     * Get rate (for loading/unloading).
     *
     * @return volume rate (m3/hr)
     */
    public double getRateM3PerHour() {
      return rateM3PerHour;
    }

    /**
     * Set rate (for loading/unloading).
     *
     * @param rate volume rate (m3/hr)
     */
    public void setRateM3PerHour(double rate) {
      this.rateM3PerHour = rate;
    }

    /**
     * Check if this event is active at a given time.
     *
     * @param timeHours current time (hours)
     * @return true if event is active
     */
    public boolean isActiveAt(double timeHours) {
      return timeHours >= startTimeHours && timeHours < startTimeHours + durationHours;
    }
  }
}
