package neqsim.process.equipment.pipeline;

import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.pipeline.PipelineMechanicalDesign;
import neqsim.process.mechanicaldesign.pipeline.RiserMechanicalDesign;

/**
 * Riser equipment class for subsea and offshore applications.
 *
 * <p>
 * This class extends PipeBeggsAndBrills to provide riser-specific functionality including:
 * </p>
 * <ul>
 * <li>Riser configuration types (SCR, TTR, Lazy-Wave, Flexible, Hybrid)</li>
 * <li>Water depth and platform offset handling</li>
 * <li>Riser-specific mechanical design (top tension, VIV, fatigue)</li>
 * <li>Dynamic response parameters</li>
 * </ul>
 *
 * <h2>Riser Types</h2>
 * <p>
 * The class supports multiple riser configurations:
 * </p>
 * <ul>
 * <li><b>SCR (Steel Catenary Riser)</b>: Simple catenary from seabed to platform</li>
 * <li><b>TTR (Top Tensioned Riser)</b>: Vertical riser with top tension</li>
 * <li><b>Lazy-Wave</b>: Catenary with buoyancy modules creating wave shape</li>
 * <li><b>Flexible</b>: Flexible pipe with higher curvature tolerance</li>
 * <li><b>Hybrid</b>: Combination of rigid and flexible sections</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Create an SCR riser
 * Riser scr = new Riser("Production Riser", inletStream);
 * scr.setRiserType(Riser.RiserType.STEEL_CATENARY_RISER);
 * scr.setWaterDepth(800.0); // meters
 * scr.setTopAngle(12.0); // degrees from vertical
 * scr.setDiameter(0.254); // 10 inch ID
 * scr.run();
 *
 * // Get mechanical design
 * RiserMechanicalDesign design = (RiserMechanicalDesign) scr.getMechanicalDesign();
 * design.setMaxOperationPressure(100.0);
 * design.setMaterialGrade("X65");
 * design.setDesignStandardCode("DNV-OS-F101");
 * design.readDesignSpecifications();
 * design.calcDesign();
 *
 * // Get riser-specific results
 * double topTension = design.getCalculator().getTopTension();
 * double viv = design.getCalculator().getVIVResponse();
 * }</pre>
 *
 * @author ASMF
 * @version 1.0
 * @see neqsim.process.mechanicaldesign.pipeline.RiserMechanicalDesign
 * @see neqsim.process.mechanicaldesign.pipeline.RiserMechanicalDesignCalculator
 */
public class Riser extends PipeBeggsAndBrills {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Riser mechanical design. */
  private RiserMechanicalDesign riserMechanicalDesign;

  /**
   * Types of risers with different geometry profiles and mechanical characteristics.
   */
  public enum RiserType {
    /** Steel Catenary Riser - simple catenary from seabed to platform. */
    STEEL_CATENARY_RISER("SCR"),
    /** Flexible riser - higher curvature tolerance. */
    FLEXIBLE_RISER("Flexible"),
    /** Top Tensioned Riser - vertical with top tension. */
    TOP_TENSIONED_RISER("TTR"),
    /** Hybrid riser - jumper to buoy then rigid riser. */
    HYBRID_RISER("Hybrid"),
    /** Lazy-wave configuration with buoyancy modules. */
    LAZY_WAVE("Lazy-Wave"),
    /** Steep-wave configuration. */
    STEEP_WAVE("Steep-Wave"),
    /** Free-standing hybrid riser. */
    FREE_STANDING("FSHR"),
    /** Simple vertical riser. */
    VERTICAL("Vertical");

    private final String displayName;

    RiserType(String displayName) {
      this.displayName = displayName;
    }

    /**
     * Get display name for riser type.
     *
     * @return display name
     */
    public String getDisplayName() {
      return displayName;
    }
  }

  // ============ Riser Configuration ============
  /** Riser type. */
  private RiserType riserType = RiserType.STEEL_CATENARY_RISER;

  /** Water depth in meters. */
  private double waterDepth = 500.0;

  /** Top hangoff angle from vertical in degrees. */
  private double topAngle = 10.0;

  /** Departure angle from seabed in degrees. */
  private double departureAngle = 15.0;

  /** Platform horizontal offset in meters. */
  private double platformOffset = 0.0;

  // ============ Lazy-Wave/Steep-Wave Configuration ============
  /** Depth of buoyancy modules for lazy-wave (meters from surface). */
  private double buoyancyModuleDepth = 0.0;

  /** Length of buoyancy section in meters. */
  private double buoyancyModuleLength = 100.0;

  /** Buoyancy per unit length in N/m (for lazy-wave). */
  private double buoyancyPerMeter = 0.0;

  // ============ TTR Configuration ============
  /** Applied top tension in kN. */
  private double appliedTopTension = 0.0;

  /** Tension variation range (heave) as fraction. */
  private double tensionVariationFactor = 0.1;

  // ============ Dynamic Response Parameters ============
  /** Significant wave height in meters. */
  private double significantWaveHeight = 2.0;

  /** Peak wave period in seconds. */
  private double peakWavePeriod = 8.0;

  /** Current velocity at surface in m/s. */
  private double currentVelocity = 0.5;

  /** Current velocity at seabed in m/s. */
  private double seabedCurrentVelocity = 0.2;

  /** Platform heave motion amplitude in meters. */
  private double platformHeaveAmplitude = 2.0;

  /** Platform heave period in seconds. */
  private double platformHeavePeriod = 10.0;

  // ============ Seabed and Environmental ============
  /** Seabed soil type. */
  private String soilType = "clay";

  /** Seabed friction coefficient. */
  private double seabedFriction = 0.3;

  /** Seawater temperature in Celsius. */
  private double seawaterTemperature = 4.0;

  /**
   * Default constructor.
   */
  public Riser() {
    super("riser");
  }

  /**
   * Constructor with name.
   *
   * @param name equipment name
   */
  public Riser(String name) {
    super(name);
  }

  /**
   * Constructor with name and inlet stream.
   *
   * @param name equipment name
   * @param inStream inlet stream
   */
  public Riser(String name, StreamInterface inStream) {
    super(name, inStream);
    updateGeometryFromType();
  }

  /**
   * Constructor with riser type, name and inlet stream.
   *
   * @param riserType the type of riser
   * @param name equipment name
   * @param inStream inlet stream
   */
  public Riser(RiserType riserType, String name, StreamInterface inStream) {
    super(name, inStream);
    this.riserType = riserType;
    updateGeometryFromType();
  }

  // ============ Factory Methods ============

  /**
   * Create a Steel Catenary Riser.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @param waterDepth water depth in meters
   * @return configured SCR
   */
  public static Riser createSCR(String name, StreamInterface inStream, double waterDepth) {
    Riser riser = new Riser(RiserType.STEEL_CATENARY_RISER, name, inStream);
    riser.setWaterDepth(waterDepth);
    return riser;
  }

  /**
   * Create a Top Tensioned Riser.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @param waterDepth water depth in meters
   * @return configured TTR
   */
  public static Riser createTTR(String name, StreamInterface inStream, double waterDepth) {
    Riser riser = new Riser(RiserType.TOP_TENSIONED_RISER, name, inStream);
    riser.setWaterDepth(waterDepth);
    return riser;
  }

  /**
   * Create a Lazy-Wave riser.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @param waterDepth water depth in meters
   * @param buoyancyDepth depth of buoyancy modules from surface
   * @return configured lazy-wave riser
   */
  public static Riser createLazyWave(String name, StreamInterface inStream, double waterDepth,
      double buoyancyDepth) {
    Riser riser = new Riser(RiserType.LAZY_WAVE, name, inStream);
    riser.setWaterDepth(waterDepth);
    riser.setBuoyancyModuleDepth(buoyancyDepth);
    return riser;
  }

  /**
   * Create a Flexible riser.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @param waterDepth water depth in meters
   * @return configured flexible riser
   */
  public static Riser createFlexible(String name, StreamInterface inStream, double waterDepth) {
    Riser riser = new Riser(RiserType.FLEXIBLE_RISER, name, inStream);
    riser.setWaterDepth(waterDepth);
    return riser;
  }

  /**
   * Create a Hybrid riser.
   *
   * @param name equipment name
   * @param inStream inlet stream
   * @param waterDepth water depth in meters
   * @return configured hybrid riser
   */
  public static Riser createHybrid(String name, StreamInterface inStream, double waterDepth) {
    Riser riser = new Riser(RiserType.HYBRID_RISER, name, inStream);
    riser.setWaterDepth(waterDepth);
    return riser;
  }

  // ============ Geometry Update ============

  /**
   * Update pipe geometry based on riser type.
   *
   * <p>
   * This method configures length, elevation and angle based on the riser type and water depth.
   * </p>
   */
  public void updateGeometryFromType() {
    switch (riserType) {
      case STEEL_CATENARY_RISER:
        configureSCR();
        break;
      case FLEXIBLE_RISER:
        configureFlexible();
        break;
      case TOP_TENSIONED_RISER:
        configureTTR();
        break;
      case LAZY_WAVE:
        configureLazyWave();
        break;
      case STEEP_WAVE:
        configureSteepWave();
        break;
      case HYBRID_RISER:
        configureHybrid();
        break;
      case FREE_STANDING:
        configureFreeStanding();
        break;
      case VERTICAL:
        configureVertical();
        break;
      default:
        configureSCR();
    }

    // Set ambient temperature
    setConstantSurfaceTemperature(seawaterTemperature, "C");
  }

  /**
   * Configure as Steel Catenary Riser.
   */
  private void configureSCR() {
    double theta = Math.toRadians(topAngle);
    double phi = Math.toRadians(departureAngle);

    // Catenary length is longer than vertical depth
    double lengthFactor = 1.0 + 0.5 * (Math.sin(theta) + Math.sin(phi));
    double length = waterDepth * lengthFactor;
    setLength(length);
    setElevation(waterDepth);

    // Calculate average angle
    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    setAngle(avgAngle);
  }

  /**
   * Configure as Flexible Riser.
   */
  private void configureFlexible() {
    double length = waterDepth * 1.3;
    setLength(length);
    setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    setAngle(avgAngle);
  }

  /**
   * Configure as Top Tensioned Riser (vertical).
   */
  private void configureTTR() {
    setLength(waterDepth);
    setElevation(waterDepth);
    setAngle(90.0);
  }

  /**
   * Configure as Lazy-Wave riser.
   */
  private void configureLazyWave() {
    double length = waterDepth * 1.8;
    setLength(length);
    setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    setAngle(avgAngle);
  }

  /**
   * Configure as Steep-Wave riser.
   */
  private void configureSteepWave() {
    double length = waterDepth * 1.5;
    setLength(length);
    setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    setAngle(avgAngle);
  }

  /**
   * Configure as Hybrid Riser.
   */
  private void configureHybrid() {
    double length = waterDepth * 1.4;
    setLength(length);
    setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    setAngle(avgAngle);
  }

  /**
   * Configure as Free-Standing Hybrid Riser.
   */
  private void configureFreeStanding() {
    double length = waterDepth * 1.2;
    setLength(length);
    setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    setAngle(avgAngle);
  }

  /**
   * Configure as simple vertical riser.
   */
  private void configureVertical() {
    setLength(waterDepth);
    setElevation(waterDepth);
    setAngle(90.0);
  }

  // ============ Overrides ============

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    riserMechanicalDesign = new RiserMechanicalDesign(this);
    // Also set parent's field
    super.initMechanicalDesign();
  }

  /** {@inheritDoc} */
  @Override
  public PipelineMechanicalDesign getMechanicalDesign() {
    if (riserMechanicalDesign == null) {
      initMechanicalDesign();
    }
    return riserMechanicalDesign;
  }

  /**
   * Get mechanical design as RiserMechanicalDesign.
   *
   * @return riser mechanical design
   */
  public RiserMechanicalDesign getRiserMechanicalDesign() {
    if (riserMechanicalDesign == null) {
      initMechanicalDesign();
    }
    return riserMechanicalDesign;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // Update geometry before running
    updateGeometryFromType();
    super.run();
  }

  // ============ Getters and Setters ============

  /**
   * Get the riser type.
   *
   * @return riser type enum
   */
  public RiserType getRiserType() {
    return riserType;
  }

  /**
   * Set the riser type.
   *
   * @param riserType type of riser
   */
  public void setRiserType(RiserType riserType) {
    this.riserType = riserType;
    updateGeometryFromType();
  }

  /**
   * Get water depth.
   *
   * @return water depth in meters
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth depth in meters
   */
  public void setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
    updateGeometryFromType();
  }

  /**
   * Get top hangoff angle.
   *
   * @return angle in degrees from vertical
   */
  public double getTopAngle() {
    return topAngle;
  }

  /**
   * Set top hangoff angle.
   *
   * @param topAngle angle in degrees from vertical
   */
  public void setTopAngle(double topAngle) {
    this.topAngle = topAngle;
    updateGeometryFromType();
  }

  /**
   * Get departure angle from seabed.
   *
   * @return angle in degrees
   */
  public double getDepartureAngle() {
    return departureAngle;
  }

  /**
   * Set departure angle from seabed.
   *
   * @param departureAngle angle in degrees
   */
  public void setDepartureAngle(double departureAngle) {
    this.departureAngle = departureAngle;
    updateGeometryFromType();
  }

  /**
   * Get platform horizontal offset.
   *
   * @return offset in meters
   */
  public double getPlatformOffset() {
    return platformOffset;
  }

  /**
   * Set platform horizontal offset.
   *
   * @param platformOffset offset in meters
   */
  public void setPlatformOffset(double platformOffset) {
    this.platformOffset = platformOffset;
  }

  /**
   * Get buoyancy module depth.
   *
   * @return depth from surface in meters
   */
  public double getBuoyancyModuleDepth() {
    return buoyancyModuleDepth;
  }

  /**
   * Set buoyancy module depth for lazy-wave configuration.
   *
   * @param buoyancyModuleDepth depth from surface in meters
   */
  public void setBuoyancyModuleDepth(double buoyancyModuleDepth) {
    this.buoyancyModuleDepth = buoyancyModuleDepth;
    updateGeometryFromType();
  }

  /**
   * Get buoyancy module length.
   *
   * @return length in meters
   */
  public double getBuoyancyModuleLength() {
    return buoyancyModuleLength;
  }

  /**
   * Set buoyancy module length.
   *
   * @param buoyancyModuleLength length in meters
   */
  public void setBuoyancyModuleLength(double buoyancyModuleLength) {
    this.buoyancyModuleLength = buoyancyModuleLength;
  }

  /**
   * Get buoyancy per meter.
   *
   * @return buoyancy in N/m
   */
  public double getBuoyancyPerMeter() {
    return buoyancyPerMeter;
  }

  /**
   * Set buoyancy per meter for buoyancy section.
   *
   * @param buoyancyPerMeter buoyancy in N/m
   */
  public void setBuoyancyPerMeter(double buoyancyPerMeter) {
    this.buoyancyPerMeter = buoyancyPerMeter;
  }

  /**
   * Get applied top tension.
   *
   * @return tension in kN
   */
  public double getAppliedTopTension() {
    return appliedTopTension;
  }

  /**
   * Set applied top tension for TTR.
   *
   * @param appliedTopTension tension in kN
   */
  public void setAppliedTopTension(double appliedTopTension) {
    this.appliedTopTension = appliedTopTension;
  }

  /**
   * Get tension variation factor.
   *
   * @return factor (0.1 = 10% variation)
   */
  public double getTensionVariationFactor() {
    return tensionVariationFactor;
  }

  /**
   * Set tension variation factor for heave motion.
   *
   * @param tensionVariationFactor factor as fraction
   */
  public void setTensionVariationFactor(double tensionVariationFactor) {
    this.tensionVariationFactor = tensionVariationFactor;
  }

  /**
   * Get significant wave height.
   *
   * @return wave height in meters
   */
  public double getSignificantWaveHeight() {
    return significantWaveHeight;
  }

  /**
   * Set significant wave height.
   *
   * @param significantWaveHeight height in meters
   */
  public void setSignificantWaveHeight(double significantWaveHeight) {
    this.significantWaveHeight = significantWaveHeight;
  }

  /**
   * Get peak wave period.
   *
   * @return period in seconds
   */
  public double getPeakWavePeriod() {
    return peakWavePeriod;
  }

  /**
   * Set peak wave period.
   *
   * @param peakWavePeriod period in seconds
   */
  public void setPeakWavePeriod(double peakWavePeriod) {
    this.peakWavePeriod = peakWavePeriod;
  }

  /**
   * Get surface current velocity.
   *
   * @return velocity in m/s
   */
  public double getCurrentVelocity() {
    return currentVelocity;
  }

  /**
   * Set surface current velocity.
   *
   * @param currentVelocity velocity in m/s
   */
  public void setCurrentVelocity(double currentVelocity) {
    this.currentVelocity = currentVelocity;
  }

  /**
   * Get seabed current velocity.
   *
   * @return velocity in m/s
   */
  public double getSeabedCurrentVelocity() {
    return seabedCurrentVelocity;
  }

  /**
   * Set seabed current velocity.
   *
   * @param seabedCurrentVelocity velocity in m/s
   */
  public void setSeabedCurrentVelocity(double seabedCurrentVelocity) {
    this.seabedCurrentVelocity = seabedCurrentVelocity;
  }

  /**
   * Get platform heave amplitude.
   *
   * @return amplitude in meters
   */
  public double getPlatformHeaveAmplitude() {
    return platformHeaveAmplitude;
  }

  /**
   * Set platform heave motion amplitude.
   *
   * @param platformHeaveAmplitude amplitude in meters
   */
  public void setPlatformHeaveAmplitude(double platformHeaveAmplitude) {
    this.platformHeaveAmplitude = platformHeaveAmplitude;
  }

  /**
   * Get platform heave period.
   *
   * @return period in seconds
   */
  public double getPlatformHeavePeriod() {
    return platformHeavePeriod;
  }

  /**
   * Set platform heave motion period.
   *
   * @param platformHeavePeriod period in seconds
   */
  public void setPlatformHeavePeriod(double platformHeavePeriod) {
    this.platformHeavePeriod = platformHeavePeriod;
  }

  /**
   * Get soil type at seabed.
   *
   * @return soil type (clay, sand, rock)
   */
  public String getSoilType() {
    return soilType;
  }

  /**
   * Set soil type at seabed.
   *
   * @param soilType type (clay, sand, rock)
   */
  public void setSoilType(String soilType) {
    this.soilType = soilType;
    // Update friction based on soil type
    if ("sand".equalsIgnoreCase(soilType)) {
      this.seabedFriction = 0.6;
    } else if ("rock".equalsIgnoreCase(soilType)) {
      this.seabedFriction = 0.8;
    } else {
      this.seabedFriction = 0.3; // clay default
    }
  }

  /**
   * Get seabed friction coefficient.
   *
   * @return friction coefficient
   */
  public double getSeabedFriction() {
    return seabedFriction;
  }

  /**
   * Set seabed friction coefficient.
   *
   * @param seabedFriction friction coefficient (0-1)
   */
  public void setSeabedFriction(double seabedFriction) {
    this.seabedFriction = seabedFriction;
  }

  /**
   * Get seawater temperature.
   *
   * @return temperature in Celsius
   */
  public double getSeawaterTemperature() {
    return seawaterTemperature;
  }

  /**
   * Set seawater temperature.
   *
   * @param seawaterTemperature temperature in Celsius
   */
  public void setSeawaterTemperature(double seawaterTemperature) {
    this.seawaterTemperature = seawaterTemperature;
    setConstantSurfaceTemperature(seawaterTemperature, "C");
  }

  /**
   * Check if riser is a catenary type (SCR, Flexible, Lazy-wave, Steep-wave).
   *
   * @return true if catenary type
   */
  public boolean isCatenaryType() {
    return riserType == RiserType.STEEL_CATENARY_RISER || riserType == RiserType.FLEXIBLE_RISER
        || riserType == RiserType.LAZY_WAVE || riserType == RiserType.STEEP_WAVE;
  }

  /**
   * Check if riser is a tensioned type (TTR, Hybrid).
   *
   * @return true if tensioned type
   */
  public boolean isTensionedType() {
    return riserType == RiserType.TOP_TENSIONED_RISER || riserType == RiserType.HYBRID_RISER
        || riserType == RiserType.FREE_STANDING;
  }

  /**
   * Check if riser is a flexible type.
   *
   * @return true if flexible type
   */
  public boolean isFlexibleType() {
    return riserType == RiserType.FLEXIBLE_RISER;
  }

  /**
   * Check if riser has buoyancy modules.
   *
   * @return true if has buoyancy modules
   */
  public boolean hasBuoyancyModules() {
    return riserType == RiserType.LAZY_WAVE || riserType == RiserType.STEEP_WAVE
        || riserType == RiserType.HYBRID_RISER;
  }
}
