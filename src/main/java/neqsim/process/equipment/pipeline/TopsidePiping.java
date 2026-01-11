package neqsim.process.equipment.pipeline;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.TopsidePipingMechanicalDesign;

/**
 * Topside piping equipment class for offshore platforms and onshore facilities.
 *
 * &lt;p&gt; This class extends PipeBeggsAndBrills to provide topside piping-specific functionality
 * including: &lt;/p&gt; &lt;ul&gt; &lt;li&gt;ASME B31.3 Process Piping design&lt;/li&gt;
 * &lt;li&gt;Velocity limits for erosion and vibration prevention&lt;/li&gt; &lt;li&gt;Pipe support
 * spacing calculations&lt;/li&gt; &lt;li&gt;Flow-induced vibration (FIV) analysis&lt;/li&gt;
 * &lt;li&gt;Acoustic-induced vibration (AIV) screening&lt;/li&gt; &lt;li&gt;Thermal expansion and
 * stress analysis&lt;/li&gt; &lt;/ul&gt;
 *
 * &lt;h2&gt;Service Types&lt;/h2&gt; &lt;p&gt; The class supports multiple service types:
 * &lt;/p&gt; &lt;ul&gt; &lt;li&gt;&lt;b&gt;PROCESS_GAS&lt;/b&gt;: High-pressure gas
 * piping&lt;/li&gt; &lt;li&gt;&lt;b&gt;PROCESS_LIQUID&lt;/b&gt;: Liquid hydrocarbon
 * piping&lt;/li&gt; &lt;li&gt;&lt;b&gt;MULTIPHASE&lt;/b&gt;: Two-phase or multiphase flow
 * piping&lt;/li&gt; &lt;li&gt;&lt;b&gt;PRODUCED_WATER&lt;/b&gt;: Produced water handling&lt;/li&gt;
 * &lt;li&gt;&lt;b&gt;STEAM&lt;/b&gt;: Steam distribution piping&lt;/li&gt;
 * &lt;li&gt;&lt;b&gt;UTILITY_AIR&lt;/b&gt;: Instrument and utility air&lt;/li&gt;
 * &lt;li&gt;&lt;b&gt;FLARE&lt;/b&gt;: Flare header and knockout drum piping&lt;/li&gt;
 * &lt;li&gt;&lt;b&gt;FUEL_GAS&lt;/b&gt;: Fuel gas distribution&lt;/li&gt; &lt;/ul&gt;
 *
 * &lt;h2&gt;Usage Example&lt;/h2&gt;
 *
 * &lt;pre&gt;{@code
 * // Create topside process piping
 * TopsidePiping gasHeader = new TopsidePiping("HP Gas Header", inletStream);
 * gasHeader.setServiceType(TopsidePiping.ServiceType.PROCESS_GAS);
 * gasHeader.setDiameter(0.2032); // 8 inch
 * gasHeader.setLength(50.0); // 50 meters
 * gasHeader.run();
 *
 * // Get mechanical design
 * TopsidePipingMechanicalDesign design = gasHeader.getTopsideMechanicalDesign();
 * design.setMaxOperationPressure(100.0);
 * design.setMaterialGrade("A106-B");
 * design.setDesignStandardCode("ASME-B31.3");
 * design.setCompanySpecificDesignStandards("Equinor");
 * design.readDesignSpecifications();
 * design.calcDesign();
 *
 * // Check velocity limits
 * double erosionVelocity = design.getCalculator().getErosionalVelocity();
 * double actualVelocity = design.getCalculator().getActualVelocity();
 * boolean velocityOk = actualVelocity &lt; erosionVelocity;
 *
 * // Get support spacing
 * double supportSpan = design.getCalculator().getSupportSpacing();
 * }&lt;/pre&gt;
 *
 * @author ASMF
 * @version 1.0
 * @see TopsidePipingMechanicalDesign
 * @see TopsidePipingMechanicalDesignCalculator
 */
public class TopsidePiping extends PipeBeggsAndBrills {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Topside piping mechanical design. */
  private TopsidePipingMechanicalDesign topsideMechanicalDesign;

  /**
   * Service types for topside piping with different design requirements.
   */
  public enum ServiceType {
    /** Process gas piping - high pressure hydrocarbon gas. */
    PROCESS_GAS("Process Gas", 0.72),
    /** Process liquid piping - liquid hydrocarbons. */
    PROCESS_LIQUID("Process Liquid", 0.72),
    /** Multiphase piping - gas-liquid mixtures. */
    MULTIPHASE("Multiphase", 0.67),
    /** Produced water piping. */
    PRODUCED_WATER("Produced Water", 0.72),
    /** Steam piping. */
    STEAM("Steam", 0.80),
    /** Utility air piping. */
    UTILITY_AIR("Utility Air", 0.72),
    /** Flare header piping. */
    FLARE("Flare", 0.67),
    /** Fuel gas distribution. */
    FUEL_GAS("Fuel Gas", 0.72),
    /** Cooling medium piping. */
    COOLING_MEDIUM("Cooling Medium", 0.72),
    /** Chemical injection piping. */
    CHEMICAL_INJECTION("Chemical Injection", 0.67),
    /** Vent and drain piping. */
    VENT_DRAIN("Vent/Drain", 0.50),
    /** Relief valve outlet piping. */
    RELIEF("Relief", 0.67);

    private final String displayName;
    private final double velocityFactor;

    ServiceType(String displayName, double velocityFactor) {
      this.displayName = displayName;
      this.velocityFactor = velocityFactor;
    }

    /**
     * Get display name for service type.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Get velocity reduction factor for this service.
     *
     * @return velocity factor (0.5-1.0)
     */
    public double getVelocityFactor() {
      return velocityFactor;
    }
  }

  /**
   * Pipe schedule for wall thickness selection.
   */
  public enum PipeSchedule {
    /** Schedule 5 - thin wall. */
    SCH_5("5", 0.0),
    /** Schedule 10 - light weight. */
    SCH_10("10", 0.0),
    /** Schedule 20. */
    SCH_20("20", 0.0),
    /** Schedule 30. */
    SCH_30("30", 0.0),
    /** Schedule 40 - standard weight. */
    SCH_40("40", 0.0),
    /** Schedule 60. */
    SCH_60("60", 0.0),
    /** Schedule 80 - extra strong. */
    SCH_80("80", 0.0),
    /** Schedule 100. */
    SCH_100("100", 0.0),
    /** Schedule 120. */
    SCH_120("120", 0.0),
    /** Schedule 140. */
    SCH_140("140", 0.0),
    /** Schedule 160 - double extra strong. */
    SCH_160("160", 0.0),
    /** Schedule STD - standard. */
    STD("STD", 0.0),
    /** Schedule XS - extra strong. */
    XS("XS", 0.0),
    /** Schedule XXS - double extra strong. */
    XXS("XXS", 0.0);

    private final String displayName;
    private final double minThickness;

    PipeSchedule(String displayName, double minThickness) {
      this.displayName = displayName;
      this.minThickness = minThickness;
    }

    /**
     * Get display name for schedule.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Get minimum thickness for this schedule.
     *
     * @return minimum thickness in mm
     */
    public double getMinThickness() {
      return minThickness;
    }
  }

  /**
   * Insulation type for topside piping.
   */
  public enum InsulationType {
    /** No insulation. */
    NONE("None", 0.0, 0.0),
    /** Mineral wool insulation. */
    MINERAL_WOOL("Mineral Wool", 0.04, 100.0),
    /** Calcium silicate insulation. */
    CALCIUM_SILICATE("Calcium Silicate", 0.055, 240.0),
    /** Polyurethane foam insulation. */
    POLYURETHANE_FOAM("PU Foam", 0.025, 40.0),
    /** Aerogel insulation. */
    AEROGEL("Aerogel", 0.015, 150.0),
    /** Cellular glass insulation. */
    CELLULAR_GLASS("Cellular Glass", 0.045, 120.0),
    /** Heat tracing with insulation. */
    HEAT_TRACED("Heat Traced", 0.04, 100.0);

    private final String displayName;
    private final double thermalConductivity;
    private final double density;

    InsulationType(String displayName, double thermalConductivity, double density) {
      this.displayName = displayName;
      this.thermalConductivity = thermalConductivity;
      this.density = density;
    }

    /**
     * Get display name for insulation type.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }

    /**
     * Get thermal conductivity in W/(m·K).
     *
     * @return thermal conductivity
     */
    public double getThermalConductivity() {
      return thermalConductivity;
    }

    /**
     * Get density in kg/m³.
     *
     * @return density
     */
    public double getDensity() {
      return density;
    }
  }

  // ============ Piping Configuration ============
  /** Service type for this piping. */
  private ServiceType serviceType = ServiceType.PROCESS_GAS;

  /** Pipe schedule. */
  private PipeSchedule pipeSchedule = PipeSchedule.SCH_40;

  /** Insulation type. */
  private InsulationType insulationType = InsulationType.NONE;

  /** Insulation thickness in meters. */
  private double insulationThickness = 0.0;

  // ============ Operating Conditions ============
  /** Maximum operating pressure in bara. */
  private double maxOperatingPressure = 0.0;

  /** Minimum operating pressure in bara. */
  private double minOperatingPressure = 0.0;

  /** Maximum operating temperature in Celsius. */
  private double maxOperatingTemperature = 0.0;

  /** Minimum operating temperature in Celsius. */
  private double minOperatingTemperature = 0.0;

  // ============ Layout Parameters ============
  /** Number of 90-degree elbows. */
  private int numberOfElbows90 = 0;

  /** Number of 45-degree elbows. */
  private int numberOfElbows45 = 0;

  /** Number of tees. */
  private int numberOfTees = 0;

  /** Number of reducers. */
  private int numberOfReducers = 0;

  /** Number of valves. */
  private int numberOfValves = 0;

  /** Valve type (Gate, Ball, Globe, Check). */
  private String valveType = "Gate";

  /** Number of flanges. */
  private int numberOfFlanges = 0;

  /** Flange rating class (150, 300, 600, 900, 1500, 2500). */
  private int flangeRating = 300;

  // ============ Support Configuration ============
  /** Support spacing in meters. */
  private double supportSpacing = 0.0;

  /** Number of pipe supports. */
  private int numberOfSupports = 0;

  /** Number of anchors. */
  private int numberOfAnchors = 0;

  /** Number of guides. */
  private int numberOfGuides = 0;

  /** Number of expansion loops. */
  private int numberOfExpansionLoops = 0;

  // ============ Environmental Parameters ============
  /** Ambient temperature in Celsius. */
  private double ambientTemperature = 15.0;

  /** Wind speed in m/s. */
  private double windSpeed = 25.0;

  /** Is the piping exposed to weather? */
  private boolean exposedToWeather = true;

  /** Is the piping in a corrosive environment? */
  private boolean corrosiveEnvironment = false;

  /**
   * Default constructor.
   */
  public TopsidePiping() {
    super("topsidePiping");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public TopsidePiping(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public TopsidePiping(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /**
   * Constructor with service type, name and inlet stream.
   *
   * @param serviceType the service type
   * @param name equipment name
   * @param inStream inlet stream
   */
  public TopsidePiping(ServiceType serviceType, String name, StreamInterface inStream) {
    super(name, inStream);
    this.serviceType = serviceType;
  }

  // ============ Factory Methods ============

  /**
   * Create process gas piping.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @return configured process gas piping
   */
  public static TopsidePiping createProcessGas(String name, StreamInterface inStream) {
    TopsidePiping pipe = new TopsidePiping(ServiceType.PROCESS_GAS, name, inStream);
    return pipe;
  }

  /**
   * Create process liquid piping.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @return configured process liquid piping
   */
  public static TopsidePiping createProcessLiquid(String name, StreamInterface inStream) {
    TopsidePiping pipe = new TopsidePiping(ServiceType.PROCESS_LIQUID, name, inStream);
    return pipe;
  }

  /**
   * Create multiphase piping.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @return configured multiphase piping
   */
  public static TopsidePiping createMultiphase(String name, StreamInterface inStream) {
    TopsidePiping pipe = new TopsidePiping(ServiceType.MULTIPHASE, name, inStream);
    return pipe;
  }

  /**
   * Create flare header piping.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @return configured flare header piping
   */
  public static TopsidePiping createFlareHeader(String name, StreamInterface inStream) {
    TopsidePiping pipe = new TopsidePiping(ServiceType.FLARE, name, inStream);
    return pipe;
  }

  /**
   * Create fuel gas piping.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @return configured fuel gas piping
   */
  public static TopsidePiping createFuelGas(String name, StreamInterface inStream) {
    TopsidePiping pipe = new TopsidePiping(ServiceType.FUEL_GAS, name, inStream);
    return pipe;
  }

  /**
   * Create steam piping.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @return configured steam piping
   */
  public static TopsidePiping createSteam(String name, StreamInterface inStream) {
    TopsidePiping pipe = new TopsidePiping(ServiceType.STEAM, name, inStream);
    return pipe;
  }

  // ============ Overrides ============

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    topsideMechanicalDesign = new TopsidePipingMechanicalDesign(this);
    // Also call parent
    super.initMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public PipelineMechanicalDesign getMechanicalDesign() {
    if (topsideMechanicalDesign == null) {
      initMechanicalDesign();
    }
    return topsideMechanicalDesign;
  }

  /**
   * Get mechanical design as TopsidePipingMechanicalDesign.
   *
   * @return topside piping mechanical design
   */
  public TopsidePipingMechanicalDesign getTopsideMechanicalDesign() {
    if (topsideMechanicalDesign == null) {
      initMechanicalDesign();
    }
    return topsideMechanicalDesign;
  }

  /**
   * Calculate total equivalent length including fittings.
   *
   * @return equivalent length in meters
   */
  public double getEquivalentLength() {
    double diameter = getDiameter();
    double leq = getLength();

    // Add equivalent lengths for fittings (in pipe diameters)
    // 90-degree elbow: ~30 D
    leq += numberOfElbows90 * 30.0 * diameter;
    // 45-degree elbow: ~16 D
    leq += numberOfElbows45 * 16.0 * diameter;
    // Tee (through): ~20 D, Tee (branch): ~60 D - assume average
    leq += numberOfTees * 40.0 * diameter;
    // Reducer: ~5 D
    leq += numberOfReducers * 5.0 * diameter;
    // Gate valve: ~8 D, Ball valve: ~3 D, Globe valve: ~340 D
    if ("Gate".equals(valveType)) {
      leq += numberOfValves * 8.0 * diameter;
    } else if ("Ball".equals(valveType)) {
      leq += numberOfValves * 3.0 * diameter;
    } else if ("Globe".equals(valveType)) {
      leq += numberOfValves * 340.0 * diameter;
    } else if ("Check".equals(valveType)) {
      leq += numberOfValves * 100.0 * diameter;
    }

    return leq;
  }

  // ============ Getters and Setters ============

  /**
   * Get service type.
   *
   * @return service type
   */
  public ServiceType getServiceType() {
    return serviceType;
  }

  /**
   * Set service type.
   *
   * @param serviceType service type
   */
  public void setServiceType(ServiceType serviceType) {
    this.serviceType = serviceType;
  }

  /**
   * Get pipe schedule enum.
   *
   * @return pipe schedule enum
   */
  public PipeSchedule getPipeScheduleEnum() {
    return pipeSchedule;
  }

  /** {@inheritDoc} */
  @Override
  public String getPipeSchedule() {
    return pipeSchedule != null ? pipeSchedule.name() : "SCH_40";
  }

  /**
   * Set pipe schedule.
   *
   * @param pipeSchedule pipe schedule
   */
  public void setPipeSchedule(PipeSchedule pipeSchedule) {
    this.pipeSchedule = pipeSchedule;
  }

  /**
   * Get insulation type enum.
   *
   * @return insulation type enum
   */
  public InsulationType getInsulationTypeEnum() {
    return insulationType;
  }

  /** {@inheritDoc} */
  @Override
  public String getInsulationType() {
    return insulationType != null ? insulationType.getDisplayName() : "none";
  }

  /**
   * Set insulation type.
   *
   * @param insulationType insulation type
   */
  public void setInsulationType(InsulationType insulationType) {
    this.insulationType = insulationType;
  }

  /**
   * Get insulation thickness.
   *
   * @return insulation thickness in meters
   */
  public double getInsulationThickness() {
    return insulationThickness;
  }

  /**
   * Set insulation thickness.
   *
   * @param insulationThickness insulation thickness in meters
   */
  public void setInsulationThickness(double insulationThickness) {
    this.insulationThickness = insulationThickness;
  }

  /**
   * Get maximum operating pressure.
   *
   * @return maximum operating pressure in bara
   */
  public double getMaxOperatingPressure() {
    return maxOperatingPressure;
  }

  /**
   * Set maximum operating pressure.
   *
   * @param maxOperatingPressure maximum operating pressure in bara
   */
  public void setMaxOperatingPressure(double maxOperatingPressure) {
    this.maxOperatingPressure = maxOperatingPressure;
  }

  /**
   * Get minimum operating pressure.
   *
   * @return minimum operating pressure in bara
   */
  public double getMinOperatingPressure() {
    return minOperatingPressure;
  }

  /**
   * Set minimum operating pressure.
   *
   * @param minOperatingPressure minimum operating pressure in bara
   */
  public void setMinOperatingPressure(double minOperatingPressure) {
    this.minOperatingPressure = minOperatingPressure;
  }

  /**
   * Get maximum operating temperature.
   *
   * @return maximum operating temperature in Celsius
   */
  public double getMaxOperatingTemperature() {
    return maxOperatingTemperature;
  }

  /**
   * Set maximum operating temperature.
   *
   * @param maxOperatingTemperature maximum operating temperature in Celsius
   */
  public void setMaxOperatingTemperature(double maxOperatingTemperature) {
    this.maxOperatingTemperature = maxOperatingTemperature;
  }

  /**
   * Get minimum operating temperature.
   *
   * @return minimum operating temperature in Celsius
   */
  public double getMinOperatingTemperature() {
    return minOperatingTemperature;
  }

  /**
   * Set minimum operating temperature.
   *
   * @param minOperatingTemperature minimum operating temperature in Celsius
   */
  public void setMinOperatingTemperature(double minOperatingTemperature) {
    this.minOperatingTemperature = minOperatingTemperature;
  }

  /**
   * Get number of 90-degree elbows.
   *
   * @return number of 90-degree elbows
   */
  public int getNumberOfElbows90() {
    return numberOfElbows90;
  }

  /**
   * Set number of 90-degree elbows.
   *
   * @param numberOfElbows90 number of 90-degree elbows
   */
  public void setNumberOfElbows90(int numberOfElbows90) {
    this.numberOfElbows90 = numberOfElbows90;
  }

  /**
   * Get number of 45-degree elbows.
   *
   * @return number of 45-degree elbows
   */
  public int getNumberOfElbows45() {
    return numberOfElbows45;
  }

  /**
   * Set number of 45-degree elbows.
   *
   * @param numberOfElbows45 number of 45-degree elbows
   */
  public void setNumberOfElbows45(int numberOfElbows45) {
    this.numberOfElbows45 = numberOfElbows45;
  }

  /**
   * Get number of tees.
   *
   * @return number of tees
   */
  public int getNumberOfTees() {
    return numberOfTees;
  }

  /**
   * Set number of tees.
   *
   * @param numberOfTees number of tees
   */
  public void setNumberOfTees(int numberOfTees) {
    this.numberOfTees = numberOfTees;
  }

  /**
   * Get number of reducers.
   *
   * @return number of reducers
   */
  public int getNumberOfReducers() {
    return numberOfReducers;
  }

  /**
   * Set number of reducers.
   *
   * @param numberOfReducers number of reducers
   */
  public void setNumberOfReducers(int numberOfReducers) {
    this.numberOfReducers = numberOfReducers;
  }

  /**
   * Get number of valves.
   *
   * @return number of valves
   */
  public int getNumberOfValves() {
    return numberOfValves;
  }

  /**
   * Set number of valves.
   *
   * @param numberOfValves number of valves
   */
  public void setNumberOfValves(int numberOfValves) {
    this.numberOfValves = numberOfValves;
  }

  /**
   * Get valve type.
   *
   * @return valve type
   */
  public String getValveType() {
    return valveType;
  }

  /**
   * Set valve type.
   *
   * @param valveType valve type (Gate, Ball, Globe, Check)
   */
  public void setValveType(String valveType) {
    this.valveType = valveType;
  }

  /**
   * Get number of flanges.
   *
   * @return number of flanges
   */
  public int getNumberOfFlanges() {
    return numberOfFlanges;
  }

  /**
   * Set number of flanges.
   *
   * @param numberOfFlanges number of flanges
   */
  public void setNumberOfFlanges(int numberOfFlanges) {
    this.numberOfFlanges = numberOfFlanges;
  }

  /**
   * Get flange rating.
   *
   * @return flange rating class
   */
  public int getFlangeRating() {
    return flangeRating;
  }

  /**
   * Set flange rating.
   *
   * @param flangeRating flange rating class (150, 300, 600, 900, 1500, 2500)
   */
  public void setFlangeRating(int flangeRating) {
    this.flangeRating = flangeRating;
  }

  /**
   * Get support spacing.
   *
   * @return support spacing in meters
   */
  public double getSupportSpacing() {
    return supportSpacing;
  }

  /**
   * Set support spacing.
   *
   * @param supportSpacing support spacing in meters
   */
  public void setSupportSpacing(double supportSpacing) {
    this.supportSpacing = supportSpacing;
  }

  /**
   * Get number of pipe supports.
   *
   * @return number of supports
   */
  public int getNumberOfSupports() {
    return numberOfSupports;
  }

  /**
   * Set number of pipe supports.
   *
   * @param numberOfSupports number of supports
   */
  public void setNumberOfSupports(int numberOfSupports) {
    this.numberOfSupports = numberOfSupports;
  }

  /**
   * Get number of anchors.
   *
   * @return number of anchors
   */
  public int getNumberOfAnchors() {
    return numberOfAnchors;
  }

  /**
   * Set number of anchors.
   *
   * @param numberOfAnchors number of anchors
   */
  public void setNumberOfAnchors(int numberOfAnchors) {
    this.numberOfAnchors = numberOfAnchors;
  }

  /**
   * Get number of guides.
   *
   * @return number of guides
   */
  public int getNumberOfGuides() {
    return numberOfGuides;
  }

  /**
   * Set number of guides.
   *
   * @param numberOfGuides number of guides
   */
  public void setNumberOfGuides(int numberOfGuides) {
    this.numberOfGuides = numberOfGuides;
  }

  /**
   * Get number of expansion loops.
   *
   * @return number of expansion loops
   */
  public int getNumberOfExpansionLoops() {
    return numberOfExpansionLoops;
  }

  /**
   * Set number of expansion loops.
   *
   * @param numberOfExpansionLoops number of expansion loops
   */
  public void setNumberOfExpansionLoops(int numberOfExpansionLoops) {
    this.numberOfExpansionLoops = numberOfExpansionLoops;
  }

  /**
   * Get ambient temperature.
   *
   * @return ambient temperature in Celsius
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set ambient temperature.
   *
   * @param ambientTemperature ambient temperature in Celsius
   */
  public void setAmbientTemperature(double ambientTemperature) {
    this.ambientTemperature = ambientTemperature;
  }

  /**
   * Get wind speed.
   *
   * @return wind speed in m/s
   */
  public double getWindSpeed() {
    return windSpeed;
  }

  /**
   * Set wind speed.
   *
   * @param windSpeed wind speed in m/s
   */
  public void setWindSpeed(double windSpeed) {
    this.windSpeed = windSpeed;
  }

  /**
   * Check if piping is exposed to weather.
   *
   * @return true if exposed to weather
   */
  public boolean isExposedToWeather() {
    return exposedToWeather;
  }

  /**
   * Set if piping is exposed to weather.
   *
   * @param exposedToWeather true if exposed to weather
   */
  public void setExposedToWeather(boolean exposedToWeather) {
    this.exposedToWeather = exposedToWeather;
  }

  /**
   * Check if in corrosive environment.
   *
   * @return true if in corrosive environment
   */
  public boolean isCorrosiveEnvironment() {
    return corrosiveEnvironment;
  }

  /**
   * Set if in corrosive environment.
   *
   * @param corrosiveEnvironment true if in corrosive environment
   */
  public void setCorrosiveEnvironment(boolean corrosiveEnvironment) {
    this.corrosiveEnvironment = corrosiveEnvironment;
  }

  /**
   * Set layout configuration for fittings.
   *
   * @param elbows90 number of 90-degree elbows
   * @param elbows45 number of 45-degree elbows
   * @param tees number of tees
   * @param valves number of valves
   */
  public void setFittings(int elbows90, int elbows45, int tees, int valves) {
    this.numberOfElbows90 = elbows90;
    this.numberOfElbows45 = elbows45;
    this.numberOfTees = tees;
    this.numberOfValves = valves;
  }

  /**
   * Set insulation configuration.
   *
   * @param type insulation type
   * @param thickness thickness in meters
   */
  public void setInsulation(InsulationType type, double thickness) {
    this.insulationType = type;
    this.insulationThickness = thickness;
  }

  /**
   * Set operating envelope.
   *
   * @param minPressure minimum operating pressure in bara
   * @param maxPressure maximum operating pressure in bara
   * @param minTemperature minimum operating temperature in Celsius
   * @param maxTemperature maximum operating temperature in Celsius
   */
  public void setOperatingEnvelope(double minPressure, double maxPressure, double minTemperature,
      double maxTemperature) {
    this.minOperatingPressure = minPressure;
    this.maxOperatingPressure = maxPressure;
    this.minOperatingTemperature = minTemperature;
    this.maxOperatingTemperature = maxTemperature;
  }
}
