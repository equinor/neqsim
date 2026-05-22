package neqsim.process.safety.opendrain;

import java.io.Serializable;
import java.util.List;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Builds open-drain review evidence directly from NeqSim process and thermodynamic objects.
 *
 * <p>
 * The calculator keeps NeqSim central in open-drain reviews by deriving credible process liquid leak
 * rates, liquid density, operating pressure, fire-water load, and gravity-drain hydraulic capacity
 * from {@link StreamInterface}, {@link ProcessEquipmentInterface}, or {@link ProcessSystem}
 * objects. The calculated values are written to {@link OpenDrainReviewItem} fields consumed by
 * {@link OpenDrainReviewEngine}.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class OpenDrainProcessEvidenceCalculator {
  /** Standard acceleration of gravity [m/s2]. */
  private static final double GRAVITY_M_PER_S2 = 9.80665;
  /** Conversion from litre per minute to cubic metre per second. */
  private static final double LITRE_PER_MINUTE_TO_M3_PER_S = 1.0 / 60000.0;
  /** Conversion from bara or barg to Pa. */
  private static final double BAR_TO_PA = 1.0e5;

  /**
   * Utility class constructor.
   */
  private OpenDrainProcessEvidenceCalculator() {}

  /**
   * Creates a review input containing one item calculated from a NeqSim stream.
   *
   * @param projectName project, asset, or review name
   * @param stream stream providing fluid state and flow rate
   * @param designBasis open-drain design and safeguard basis
   * @return review input populated with NeqSim-calculated evidence
   * @throws IllegalArgumentException if {@code stream} is null
   */
  public static OpenDrainReviewInput createInputFromStream(String projectName,
      StreamInterface stream, DesignBasis designBasis) {
    OpenDrainReviewInput input = new OpenDrainReviewInput().setProjectName(projectName);
    input.addItem(createItemFromStream(stream, designBasis));
    return input;
  }

  /**
   * Creates one review item from a NeqSim stream.
   *
   * @param stream stream providing fluid state and flow rate
   * @param designBasis open-drain design and safeguard basis
   * @return review item populated with calculated evidence
   * @throws IllegalArgumentException if {@code stream} is null
   */
  public static OpenDrainReviewItem createItemFromStream(StreamInterface stream,
      DesignBasis designBasis) {
    if (stream == null) {
      throw new IllegalArgumentException("stream must not be null");
    }
    DesignBasis safeBasis = designBasis == null ? new DesignBasis() : designBasis;
    SystemInterface fluid = prepareFluid(stream);
    String areaId = safeBasis.getAreaId().isEmpty() ? stream.getName() : safeBasis.getAreaId();
    OpenDrainReviewItem item = createBaseItem(areaId, safeBasis);
    item.addSourceReference("NeqSim stream: " + stream.getName());

    double liquidMassFlowKgPerS = getLiquidMassFlowRateKgPerS(fluid);
    double liquidDensityKgPerM3 = getLiquidDensityKgPerM3(fluid);
    double pressureBara = getStreamPressureBara(stream, fluid);
    double temperatureC = getStreamTemperatureC(stream, fluid);
    double credibleLeakRateKgPerS = calculateCredibleLiquidLeakRateKgPerS(liquidMassFlowKgPerS,
        safeBasis.getCredibleLeakFractionOfLiquidFlow(),
        safeBasis.getMaximumCredibleLiquidLeakRateKgPerS());
    double fireWaterLoadKgPerS = calculateFireWaterLoadKgPerS(safeBasis.getFireWaterAreaM2(),
        safeBasis.getFireWaterApplicationRateLPerMinM2(), safeBasis.getFireWaterDensityKgPerM3());
    double drainCapacityKgPerS = calculateGravityDrainCapacityKgPerS(liquidDensityKgPerM3,
        safeBasis.getDrainPipeDiameterM(), safeBasis.getAvailableDrainHeadM(),
        safeBasis.getDrainDischargeCoefficient(), safeBasis.getDrainBackpressureBarg());

    putIfFinite(item, "neqsimLiquidMassFlowKgPerS", liquidMassFlowKgPerS);
    putIfFinite(item, "liquidDensityKgPerM3", liquidDensityKgPerM3);
    putIfFinite(item, "pressureBara", pressureBara);
    putIfFinite(item, "temperatureC", temperatureC);
    putIfFinite(item, "liquidLeakRateKgPerS", credibleLeakRateKgPerS);
    putIfFinite(item, "fireWaterCapacityKgPerS", fireWaterLoadKgPerS);
    putIfFinite(item, "drainageCapacityKgPerS", drainCapacityKgPerS);
    item.put("calculationBasis", "NeqSim process and thermodynamic evidence");
    item.put("sourceHasFlammableOrHazardousLiquid",
        Boolean.valueOf(credibleLeakRateKgPerS > 0.0));
    applySafeguardEvidence(item, safeBasis);
    return item;
  }

  /**
   * Creates one review item from a NeqSim process equipment object.
   *
   * @param equipment process equipment providing inlet or outlet streams
   * @param designBasis open-drain design and safeguard basis
   * @return review item populated with calculated evidence
   * @throws IllegalArgumentException if {@code equipment} is null or has no streams
   */
  public static OpenDrainReviewItem createItemFromEquipment(ProcessEquipmentInterface equipment,
      DesignBasis designBasis) {
    if (equipment == null) {
      throw new IllegalArgumentException("equipment must not be null");
    }
    StreamInterface stream = selectRepresentativeStream(equipment);
    DesignBasis safeBasis = designBasis == null ? new DesignBasis() : designBasis.copy();
    if (safeBasis.getAreaId().isEmpty()) {
      safeBasis.setAreaId(equipment.getName());
    }
    OpenDrainReviewItem item = createItemFromStream(stream, safeBasis);
    item.addSourceReference("NeqSim equipment: " + equipment.getName());
    return item;
  }

  /**
   * Creates review input from every process-system unit that exposes an inlet or outlet stream.
   *
   * @param process process system to inspect
   * @param designBasis default open-drain design and safeguard basis
   * @return review input populated with one item per stream-bearing unit
   * @throws IllegalArgumentException if {@code process} is null
   */
  public static OpenDrainReviewInput createInputFromProcessSystem(ProcessSystem process,
      DesignBasis designBasis) {
    if (process == null) {
      throw new IllegalArgumentException("process must not be null");
    }
    OpenDrainReviewInput input = new OpenDrainReviewInput().setProjectName(process.getName());
    List<ProcessEquipmentInterface> units = process.getUnitOperations();
    for (ProcessEquipmentInterface unit : units) {
      try {
        input.addItem(createItemFromEquipment(unit, designBasis));
      } catch (IllegalArgumentException ex) {
        // Equipment without process streams cannot provide thermodynamic drain evidence.
      }
    }
    return input;
  }

  /**
   * Calculates credible liquid leak rate from the NeqSim liquid mass flow.
   *
   * @param liquidMassFlowKgPerS liquid mass flow rate in kg/s
   * @param leakFractionOfLiquidFlow credible leak fraction of liquid flow
   * @param maximumCredibleLeakKgPerS maximum credible leak cap in kg/s, or NaN for no cap
   * @return credible liquid leak rate in kg/s
   */
  public static double calculateCredibleLiquidLeakRateKgPerS(double liquidMassFlowKgPerS,
      double leakFractionOfLiquidFlow, double maximumCredibleLeakKgPerS) {
    if (!Double.isFinite(liquidMassFlowKgPerS) || liquidMassFlowKgPerS <= 0.0) {
      return 0.0;
    }
    double leakFraction = Double.isFinite(leakFractionOfLiquidFlow)
        ? Math.max(0.0, leakFractionOfLiquidFlow) : 1.0;
    double leakRateKgPerS = liquidMassFlowKgPerS * leakFraction;
    if (Double.isFinite(maximumCredibleLeakKgPerS) && maximumCredibleLeakKgPerS >= 0.0) {
      leakRateKgPerS = Math.min(leakRateKgPerS, maximumCredibleLeakKgPerS);
    }
    return Math.max(0.0, leakRateKgPerS);
  }

  /**
   * Calculates fire-water mass load from application rate and area.
   *
   * @param fireWaterAreaM2 protected or deluged area in m2
   * @param applicationRateLPerMinM2 fire-water application rate in L/min/m2
   * @param fireWaterDensityKgPerM3 fire-water density in kg/m3
   * @return fire-water mass load in kg/s, or zero if area or rate is not configured
   */
  public static double calculateFireWaterLoadKgPerS(double fireWaterAreaM2,
      double applicationRateLPerMinM2, double fireWaterDensityKgPerM3) {
    if (!Double.isFinite(fireWaterAreaM2) || !Double.isFinite(applicationRateLPerMinM2)
        || !Double.isFinite(fireWaterDensityKgPerM3) || fireWaterAreaM2 <= 0.0
        || applicationRateLPerMinM2 <= 0.0 || fireWaterDensityKgPerM3 <= 0.0) {
      return 0.0;
    }
    return fireWaterAreaM2 * applicationRateLPerMinM2 * LITRE_PER_MINUTE_TO_M3_PER_S
        * fireWaterDensityKgPerM3;
  }

  /**
   * Calculates gravity drain hydraulic capacity for a liquid-filled open-drain pipe.
   *
   * @param liquidDensityKgPerM3 liquid density in kg/m3
   * @param drainPipeDiameterM drain internal diameter in m
   * @param availableHeadM available hydraulic head in m liquid
   * @param dischargeCoefficient discharge coefficient for the drain inlet or pipe entrance
   * @param backpressureBarg downstream backpressure in barg opposing the available head
   * @return drain mass-flow capacity in kg/s, or zero when effective head is not available
   */
  public static double calculateGravityDrainCapacityKgPerS(double liquidDensityKgPerM3,
      double drainPipeDiameterM, double availableHeadM, double dischargeCoefficient,
      double backpressureBarg) {
    if (!Double.isFinite(liquidDensityKgPerM3) || !Double.isFinite(drainPipeDiameterM)
        || !Double.isFinite(availableHeadM) || !Double.isFinite(dischargeCoefficient)
        || liquidDensityKgPerM3 <= 0.0 || drainPipeDiameterM <= 0.0 || availableHeadM <= 0.0
        || dischargeCoefficient <= 0.0) {
      return 0.0;
    }
    double backpressureHeadM = Double.isFinite(backpressureBarg) && backpressureBarg > 0.0
        ? backpressureBarg * BAR_TO_PA / (liquidDensityKgPerM3 * GRAVITY_M_PER_S2) : 0.0;
    double effectiveHeadM = availableHeadM - backpressureHeadM;
    if (effectiveHeadM <= 0.0) {
      return 0.0;
    }
    double flowAreaM2 = Math.PI * Math.pow(drainPipeDiameterM / 2.0, 2.0);
    double volumetricCapacityM3PerS = dischargeCoefficient * flowAreaM2
        * Math.sqrt(2.0 * GRAVITY_M_PER_S2 * effectiveHeadM);
    return liquidDensityKgPerM3 * volumetricCapacityM3PerS;
  }

  /**
   * Creates a base review item from design-basis metadata.
   *
   * @param areaId calculated area identifier
   * @param designBasis design and safeguard basis
   * @return base review item
   */
  private static OpenDrainReviewItem createBaseItem(String areaId, DesignBasis designBasis) {
    OpenDrainReviewItem item = new OpenDrainReviewItem().setAreaId(areaId)
        .setAreaType(designBasis.getAreaType()).setDrainSystemType(designBasis.getDrainSystemType())
        .put("standards", designBasis.getStandards());
    if (!designBasis.getSourceReference().isEmpty()) {
      item.addSourceReference(designBasis.getSourceReference());
    }
    return item;
  }

  /**
   * Applies document or design-basis safeguards that the numerical calculation cannot infer.
   *
   * @param item item receiving safeguard evidence
   * @param designBasis safeguard basis
   */
  private static void applySafeguardEvidence(OpenDrainReviewItem item, DesignBasis designBasis) {
    item.put("hasOpenDrainMeasures", Boolean.valueOf(designBasis.hasOpenDrainMeasures()));
    item.put("backflowPrevented", Boolean.valueOf(designBasis.isBackflowPrevented()));
    item.put("closedOpenDrainInteractionPrevented",
        Boolean.valueOf(designBasis.isClosedOpenDrainInteractionPrevented()));
    item.put("hazardousNonHazardousPhysicallySeparated",
        Boolean.valueOf(designBasis.isHazardousNonHazardousPhysicallySeparated()));
    item.put("sealDesignedForMaxBackpressure",
        Boolean.valueOf(designBasis.isSealDesignedForMaxBackpressure()));
    item.put("ventTerminatedSafe", Boolean.valueOf(designBasis.isVentTerminatedSafe()));
    item.put("openDrainDependsOnUtility", Boolean.valueOf(designBasis.isOpenDrainDependsOnUtility()));
  }

  /**
   * Prepares a cloned fluid for property reads without mutating the source stream.
   *
   * @param stream stream providing the fluid state
   * @return cloned and initialized fluid
   */
  private static SystemInterface prepareFluid(StreamInterface stream) {
    SystemInterface source = stream.getThermoSystem();
    if (source == null) {
      throw new IllegalArgumentException("stream must contain a thermodynamic system");
    }
    SystemInterface fluid = source.clone();
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
      operations.TPflash();
    } catch (RuntimeException ex) {
      fluid.init(0);
    }
    fluid.initProperties();
    return fluid;
  }

  /**
   * Selects representative stream evidence from equipment.
   *
   * @param equipment process equipment to inspect
   * @return first inlet stream, or first outlet stream if no inlet exists
   * @throws IllegalArgumentException if no streams are connected
   */
  private static StreamInterface selectRepresentativeStream(ProcessEquipmentInterface equipment) {
    List<StreamInterface> inletStreams = equipment.getInletStreams();
    if (inletStreams != null && !inletStreams.isEmpty()) {
      return inletStreams.get(0);
    }
    List<StreamInterface> outletStreams = equipment.getOutletStreams();
    if (outletStreams != null && !outletStreams.isEmpty()) {
      return outletStreams.get(0);
    }
    if (equipment instanceof StreamInterface) {
      return (StreamInterface) equipment;
    }
    throw new IllegalArgumentException("equipment has no inlet or outlet streams");
  }

  /**
   * Gets total liquid mass flow rate from oil, aqueous, liquid, and asphaltene-liquid phases.
   *
   * @param fluid initialized fluid
   * @return liquid mass flow rate in kg/s
   */
  private static double getLiquidMassFlowRateKgPerS(SystemInterface fluid) {
    double liquidMassFlowKgPerS = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      if (isLiquidPhase(phase.getType())) {
        liquidMassFlowKgPerS += Math.max(0.0, phase.getFlowRate("kg/sec"));
      }
    }
    return liquidMassFlowKgPerS;
  }

  /**
   * Gets representative liquid density from liquid phases.
   *
   * @param fluid initialized fluid
   * @return mass-flow-weighted liquid density in kg/m3, or total density when no liquid exists
   */
  private static double getLiquidDensityKgPerM3(SystemInterface fluid) {
    double weightedDensity = 0.0;
    double liquidMassFlowKgPerS = 0.0;
    for (int phaseIndex = 0; phaseIndex < fluid.getNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      if (isLiquidPhase(phase.getType())) {
        double phaseMassFlowKgPerS = Math.max(0.0, phase.getFlowRate("kg/sec"));
        double phaseDensityKgPerM3 = phase.getPhysicalProperties().getDensity();
        if (Double.isFinite(phaseDensityKgPerM3) && phaseDensityKgPerM3 > 0.0) {
          weightedDensity += phaseDensityKgPerM3 * Math.max(phaseMassFlowKgPerS, 1.0e-12);
          liquidMassFlowKgPerS += Math.max(phaseMassFlowKgPerS, 1.0e-12);
        }
      }
    }
    if (liquidMassFlowKgPerS > 0.0) {
      return weightedDensity / liquidMassFlowKgPerS;
    }
    return fluid.getDensity("kg/m3");
  }

  /**
   * Tests whether a phase type should be treated as liquid for open-drain leak evidence.
   *
   * @param phaseType phase type to test
   * @return true for liquid-like phases
   */
  private static boolean isLiquidPhase(PhaseType phaseType) {
    return phaseType == PhaseType.LIQUID || phaseType == PhaseType.OIL
        || phaseType == PhaseType.AQUEOUS || phaseType == PhaseType.LIQUID_ASPHALTENE;
  }

  /**
   * Gets stream pressure from the stream or cloned fluid.
   *
   * @param stream source stream
   * @param fluid prepared fluid fallback
   * @return pressure in bara
   */
  private static double getStreamPressureBara(StreamInterface stream, SystemInterface fluid) {
    try {
      return stream.getPressure("bara");
    } catch (RuntimeException ex) {
      return fluid.getPressure("bara");
    }
  }

  /**
   * Gets stream temperature from the stream or cloned fluid.
   *
   * @param stream source stream
   * @param fluid prepared fluid fallback
   * @return temperature in deg C
   */
  private static double getStreamTemperatureC(StreamInterface stream, SystemInterface fluid) {
    try {
      return stream.getTemperature("C");
    } catch (RuntimeException ex) {
      return fluid.getTemperature("C");
    }
  }

  /**
   * Adds a finite numeric value to a review item.
   *
   * @param item item receiving the value
   * @param key evidence key
   * @param value numeric value
   */
  private static void putIfFinite(OpenDrainReviewItem item, String key, double value) {
    if (Double.isFinite(value)) {
      item.put(key, Double.valueOf(value));
    }
  }

  /**
   * Design and safeguard basis for converting NeqSim process evidence into open-drain evidence.
   */
  public static class DesignBasis implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1000L;

    /** Area identifier overriding stream or equipment name. */
    private String areaId = "";
    /** Area type used by the open-drain review. */
    private String areaType = "process area";
    /** Drain system type used by the open-drain review. */
    private String drainSystemType = "hazardous open drain";
    /** Design standards reference text. */
    private String standards = "NORSOK S-001; NORSOK P-002; ISO 13702";
    /** Optional source reference for document evidence. */
    private String sourceReference = "";
    /** Credible leak fraction of liquid mass flow. */
    private double credibleLeakFractionOfLiquidFlow = 1.0;
    /** Maximum credible liquid leak rate in kg/s. */
    private double maximumCredibleLiquidLeakRateKgPerS = 5.0;
    /** Fire-water area in m2. */
    private double fireWaterAreaM2 = 0.0;
    /** Fire-water application rate in L/min/m2. */
    private double fireWaterApplicationRateLPerMinM2 = 0.0;
    /** Fire-water density in kg/m3. */
    private double fireWaterDensityKgPerM3 = 1000.0;
    /** Drain pipe internal diameter in m. */
    private double drainPipeDiameterM = 0.0;
    /** Available hydraulic head in m liquid. */
    private double availableDrainHeadM = 0.0;
    /** Drain discharge coefficient. */
    private double drainDischargeCoefficient = 0.62;
    /** Drain backpressure in barg. */
    private double drainBackpressureBarg = 0.0;
    /** Evidence that open-drain measures are installed. */
    private boolean hasOpenDrainMeasures = true;
    /** Evidence that backflow is prevented. */
    private boolean backflowPrevented = true;
    /** Evidence that closed and open drains cannot interact. */
    private boolean closedOpenDrainInteractionPrevented = true;
    /** Evidence that hazardous and non-hazardous drains are separated. */
    private boolean hazardousNonHazardousPhysicallySeparated = true;
    /** Evidence that seals are designed for maximum backpressure. */
    private boolean sealDesignedForMaxBackpressure = true;
    /** Evidence that vent routing is safe. */
    private boolean ventTerminatedSafe = true;
    /** Evidence that open-drain function depends on utilities. */
    private boolean openDrainDependsOnUtility = false;

    /**
     * Creates a copy of this design basis.
     *
     * @return copied design basis
     */
    public DesignBasis copy() {
      DesignBasis copy = new DesignBasis();
      copy.areaId = areaId;
      copy.areaType = areaType;
      copy.drainSystemType = drainSystemType;
      copy.standards = standards;
      copy.sourceReference = sourceReference;
      copy.credibleLeakFractionOfLiquidFlow = credibleLeakFractionOfLiquidFlow;
      copy.maximumCredibleLiquidLeakRateKgPerS = maximumCredibleLiquidLeakRateKgPerS;
      copy.fireWaterAreaM2 = fireWaterAreaM2;
      copy.fireWaterApplicationRateLPerMinM2 = fireWaterApplicationRateLPerMinM2;
      copy.fireWaterDensityKgPerM3 = fireWaterDensityKgPerM3;
      copy.drainPipeDiameterM = drainPipeDiameterM;
      copy.availableDrainHeadM = availableDrainHeadM;
      copy.drainDischargeCoefficient = drainDischargeCoefficient;
      copy.drainBackpressureBarg = drainBackpressureBarg;
      copy.hasOpenDrainMeasures = hasOpenDrainMeasures;
      copy.backflowPrevented = backflowPrevented;
      copy.closedOpenDrainInteractionPrevented = closedOpenDrainInteractionPrevented;
      copy.hazardousNonHazardousPhysicallySeparated = hazardousNonHazardousPhysicallySeparated;
      copy.sealDesignedForMaxBackpressure = sealDesignedForMaxBackpressure;
      copy.ventTerminatedSafe = ventTerminatedSafe;
      copy.openDrainDependsOnUtility = openDrainDependsOnUtility;
      return copy;
    }

    /**
     * Sets the area identifier.
     *
     * @param areaId area identifier
     * @return this basis for chaining
     */
    public DesignBasis setAreaId(String areaId) {
      this.areaId = normalize(areaId);
      return this;
    }

    /**
     * Gets the area identifier.
     *
     * @return area identifier
     */
    public String getAreaId() {
      return areaId;
    }

    /**
     * Sets the area type.
     *
     * @param areaType area type
     * @return this basis for chaining
     */
    public DesignBasis setAreaType(String areaType) {
      this.areaType = normalize(areaType);
      return this;
    }

    /**
     * Gets the area type.
     *
     * @return area type
     */
    public String getAreaType() {
      return areaType;
    }

    /**
     * Sets the drain system type.
     *
     * @param drainSystemType drain system type
     * @return this basis for chaining
     */
    public DesignBasis setDrainSystemType(String drainSystemType) {
      this.drainSystemType = normalize(drainSystemType);
      return this;
    }

    /**
     * Gets the drain system type.
     *
     * @return drain system type
     */
    public String getDrainSystemType() {
      return drainSystemType;
    }

    /**
     * Sets standards evidence text.
     *
     * @param standards standards evidence text
     * @return this basis for chaining
     */
    public DesignBasis setStandards(String standards) {
      this.standards = normalize(standards);
      return this;
    }

    /**
     * Gets standards evidence text.
     *
     * @return standards evidence text
     */
    public String getStandards() {
      return standards;
    }

    /**
     * Sets a document or calculation source reference.
     *
     * @param sourceReference source reference
     * @return this basis for chaining
     */
    public DesignBasis setSourceReference(String sourceReference) {
      this.sourceReference = normalize(sourceReference);
      return this;
    }

    /**
     * Gets the source reference.
     *
     * @return source reference
     */
    public String getSourceReference() {
      return sourceReference;
    }

    /**
     * Sets the credible leak fraction of liquid flow.
     *
     * @param fraction credible leak fraction of liquid flow
     * @return this basis for chaining
     */
    public DesignBasis setCredibleLeakFractionOfLiquidFlow(double fraction) {
      this.credibleLeakFractionOfLiquidFlow = Math.max(0.0, fraction);
      return this;
    }

    /**
     * Gets the credible leak fraction of liquid flow.
     *
     * @return credible leak fraction of liquid flow
     */
    public double getCredibleLeakFractionOfLiquidFlow() {
      return credibleLeakFractionOfLiquidFlow;
    }

    /**
     * Sets the maximum credible liquid leak rate.
     *
     * @param rateKgPerS maximum credible liquid leak rate in kg/s
     * @return this basis for chaining
     */
    public DesignBasis setMaximumCredibleLiquidLeakRateKgPerS(double rateKgPerS) {
      this.maximumCredibleLiquidLeakRateKgPerS = rateKgPerS;
      return this;
    }

    /**
     * Gets the maximum credible liquid leak rate.
     *
     * @return maximum credible liquid leak rate in kg/s
     */
    public double getMaximumCredibleLiquidLeakRateKgPerS() {
      return maximumCredibleLiquidLeakRateKgPerS;
    }

    /**
     * Sets fire-water area.
     *
     * @param areaM2 fire-water area in m2
     * @return this basis for chaining
     */
    public DesignBasis setFireWaterAreaM2(double areaM2) {
      this.fireWaterAreaM2 = Math.max(0.0, areaM2);
      return this;
    }

    /**
     * Gets fire-water area.
     *
     * @return fire-water area in m2
     */
    public double getFireWaterAreaM2() {
      return fireWaterAreaM2;
    }

    /**
     * Sets fire-water application rate.
     *
     * @param rateLPerMinM2 fire-water application rate in L/min/m2
     * @return this basis for chaining
     */
    public DesignBasis setFireWaterApplicationRateLPerMinM2(double rateLPerMinM2) {
      this.fireWaterApplicationRateLPerMinM2 = Math.max(0.0, rateLPerMinM2);
      return this;
    }

    /**
     * Gets fire-water application rate.
     *
     * @return fire-water application rate in L/min/m2
     */
    public double getFireWaterApplicationRateLPerMinM2() {
      return fireWaterApplicationRateLPerMinM2;
    }

    /**
     * Sets fire-water density.
     *
     * @param densityKgPerM3 fire-water density in kg/m3
     * @return this basis for chaining
     */
    public DesignBasis setFireWaterDensityKgPerM3(double densityKgPerM3) {
      this.fireWaterDensityKgPerM3 = Math.max(0.0, densityKgPerM3);
      return this;
    }

    /**
     * Gets fire-water density.
     *
     * @return fire-water density in kg/m3
     */
    public double getFireWaterDensityKgPerM3() {
      return fireWaterDensityKgPerM3;
    }

    /**
     * Sets drain pipe internal diameter.
     *
     * @param diameterM drain pipe internal diameter in m
     * @return this basis for chaining
     */
    public DesignBasis setDrainPipeDiameterM(double diameterM) {
      this.drainPipeDiameterM = Math.max(0.0, diameterM);
      return this;
    }

    /**
     * Gets drain pipe internal diameter.
     *
     * @return drain pipe internal diameter in m
     */
    public double getDrainPipeDiameterM() {
      return drainPipeDiameterM;
    }

    /**
     * Sets available hydraulic head.
     *
     * @param headM available hydraulic head in m liquid
     * @return this basis for chaining
     */
    public DesignBasis setAvailableDrainHeadM(double headM) {
      this.availableDrainHeadM = Math.max(0.0, headM);
      return this;
    }

    /**
     * Gets available hydraulic head.
     *
     * @return available hydraulic head in m liquid
     */
    public double getAvailableDrainHeadM() {
      return availableDrainHeadM;
    }

    /**
     * Sets drain discharge coefficient.
     *
     * @param coefficient discharge coefficient
     * @return this basis for chaining
     */
    public DesignBasis setDrainDischargeCoefficient(double coefficient) {
      this.drainDischargeCoefficient = Math.max(0.0, coefficient);
      return this;
    }

    /**
     * Gets drain discharge coefficient.
     *
     * @return discharge coefficient
     */
    public double getDrainDischargeCoefficient() {
      return drainDischargeCoefficient;
    }

    /**
     * Sets drain backpressure.
     *
     * @param backpressureBarg drain backpressure in barg
     * @return this basis for chaining
     */
    public DesignBasis setDrainBackpressureBarg(double backpressureBarg) {
      this.drainBackpressureBarg = Math.max(0.0, backpressureBarg);
      return this;
    }

    /**
     * Gets drain backpressure.
     *
     * @return drain backpressure in barg
     */
    public double getDrainBackpressureBarg() {
      return drainBackpressureBarg;
    }

    /**
     * Sets whether open-drain measures are installed.
     *
     * @param value true if open-drain measures are installed
     * @return this basis for chaining
     */
    public DesignBasis setHasOpenDrainMeasures(boolean value) {
      this.hasOpenDrainMeasures = value;
      return this;
    }

    /**
     * Checks whether open-drain measures are installed.
     *
     * @return true if open-drain measures are installed
     */
    public boolean hasOpenDrainMeasures() {
      return hasOpenDrainMeasures;
    }

    /**
     * Sets whether backflow is prevented.
     *
     * @param value true if backflow is prevented
     * @return this basis for chaining
     */
    public DesignBasis setBackflowPrevented(boolean value) {
      this.backflowPrevented = value;
      return this;
    }

    /**
     * Checks whether backflow is prevented.
     *
     * @return true if backflow is prevented
     */
    public boolean isBackflowPrevented() {
      return backflowPrevented;
    }

    /**
     * Sets whether closed and open drain interaction is prevented.
     *
     * @param value true if interaction is prevented
     * @return this basis for chaining
     */
    public DesignBasis setClosedOpenDrainInteractionPrevented(boolean value) {
      this.closedOpenDrainInteractionPrevented = value;
      return this;
    }

    /**
     * Checks whether closed and open drain interaction is prevented.
     *
     * @return true if interaction is prevented
     */
    public boolean isClosedOpenDrainInteractionPrevented() {
      return closedOpenDrainInteractionPrevented;
    }

    /**
     * Sets whether hazardous and non-hazardous drains are separated.
     *
     * @param value true if drains are separated
     * @return this basis for chaining
     */
    public DesignBasis setHazardousNonHazardousPhysicallySeparated(boolean value) {
      this.hazardousNonHazardousPhysicallySeparated = value;
      return this;
    }

    /**
     * Checks whether hazardous and non-hazardous drains are separated.
     *
     * @return true if drains are separated
     */
    public boolean isHazardousNonHazardousPhysicallySeparated() {
      return hazardousNonHazardousPhysicallySeparated;
    }

    /**
     * Sets whether seals are designed for maximum backpressure.
     *
     * @param value true if seals are designed for maximum backpressure
     * @return this basis for chaining
     */
    public DesignBasis setSealDesignedForMaxBackpressure(boolean value) {
      this.sealDesignedForMaxBackpressure = value;
      return this;
    }

    /**
     * Checks whether seals are designed for maximum backpressure.
     *
     * @return true if seals are designed for maximum backpressure
     */
    public boolean isSealDesignedForMaxBackpressure() {
      return sealDesignedForMaxBackpressure;
    }

    /**
     * Sets whether vent routing terminates safely.
     *
     * @param value true if vent routing terminates safely
     * @return this basis for chaining
     */
    public DesignBasis setVentTerminatedSafe(boolean value) {
      this.ventTerminatedSafe = value;
      return this;
    }

    /**
     * Checks whether vent routing terminates safely.
     *
     * @return true if vent routing terminates safely
     */
    public boolean isVentTerminatedSafe() {
      return ventTerminatedSafe;
    }

    /**
     * Sets whether open-drain function depends on utilities.
     *
     * @param value true if open-drain function depends on utilities
     * @return this basis for chaining
     */
    public DesignBasis setOpenDrainDependsOnUtility(boolean value) {
      this.openDrainDependsOnUtility = value;
      return this;
    }

    /**
     * Checks whether open-drain function depends on utilities.
     *
     * @return true if open-drain function depends on utilities
     */
    public boolean isOpenDrainDependsOnUtility() {
      return openDrainDependsOnUtility;
    }

    /**
     * Normalizes nullable text.
     *
     * @param value value to normalize
     * @return trimmed text or an empty string
     */
    private static String normalize(String value) {
      return value == null ? "" : value.trim();
    }
  }
}