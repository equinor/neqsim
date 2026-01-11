package neqsim.process.equipment.pipeline;

import neqsim.process.equipment.stream.StreamInterface;

/**
 * Configuration factory for creating risers with standard geometry profiles.
 *
 * <p>
 * This class provides factory methods to create pipeline objects configured as risers with
 * appropriate elevation profiles for different riser types (SCR, flexible, lazy-wave, etc.).
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * {@code
 * // Create a Steel Catenary Riser
 * PipeBeggsAndBrills riser = RiserConfiguration.createRiser(RiserType.STEEL_CATENARY_RISER,
 *     "Production Riser", inletStream, 500.0);
 * riser.run();
 *
 * // Create a lazy-wave riser with custom parameters
 * RiserConfiguration config = new RiserConfiguration(RiserType.LAZY_WAVE);
 * config.setWaterDepth(800.0);
 * config.setBuoyancyModuleDepth(400.0);
 * config.setTopAngle(12.0);
 * PipeBeggsAndBrills lazyWave = config.create("Lazy Wave Riser", inletStream);
 * }
 * </pre>
 *
 * @author ASMF
 * @version 1.0
 */
public class RiserConfiguration {

  /**
   * Types of risers with different geometry profiles.
   */
  public enum RiserType {
    /** Steel Catenary Riser - simple catenary from seabed to platform. */
    STEEL_CATENARY_RISER,
    /** Flexible riser - higher curvature tolerance. */
    FLEXIBLE_RISER,
    /** Top Tensioned Riser - vertical with top tension. */
    TOP_TENSIONED_RISER,
    /** Hybrid riser - jumper to buoy then rigid riser. */
    HYBRID_RISER,
    /** Lazy-wave configuration with buoyancy modules. */
    LAZY_WAVE,
    /** Steep-wave configuration. */
    STEEP_WAVE,
    /** Free-standing hybrid riser. */
    FREE_STANDING,
    /** Simple vertical riser. */
    VERTICAL
  }

  /** Riser type configuration. */
  private RiserType riserType;

  /** Water depth in meters. */
  private double waterDepth = 500.0;

  /** Top hangoff angle from vertical in degrees. */
  private double topAngle = 10.0;

  /** Departure angle from seabed in degrees. */
  private double departureAngle = 15.0;

  /** Depth of buoyancy modules for lazy-wave (meters from surface). */
  private double buoyancyModuleDepth = 0.0;

  /** Length of buoyancy section for lazy-wave (meters). */
  private double buoyancyModuleLength = 100.0;

  /** Inner diameter in meters. */
  private double innerDiameter = 0.2;

  /** Number of sections for elevation profile. */
  private int numberOfSections = 50;

  /** Overall heat transfer coefficient W/(m²·K). */
  private double heatTransferCoefficient = 10.0;

  /** Ambient/seawater temperature in Celsius. */
  private double ambientTemperature = 4.0;

  /**
   * Default constructor with SCR type.
   */
  public RiserConfiguration() {
    this.riserType = RiserType.STEEL_CATENARY_RISER;
  }

  /**
   * Constructor with specified riser type.
   *
   * @param riserType the type of riser configuration
   */
  public RiserConfiguration(RiserType riserType) {
    this.riserType = riserType;
  }

  /**
   * Factory method to create a riser with default parameters.
   *
   * @param type the riser type
   * @param name equipment name
   * @param inlet inlet stream
   * @param waterDepth water depth in meters
   * @return configured PipeBeggsAndBrills as a riser
   */
  public static PipeBeggsAndBrills createRiser(RiserType type, String name, StreamInterface inlet,
      double waterDepth) {
    RiserConfiguration config = new RiserConfiguration(type);
    config.setWaterDepth(waterDepth);
    return config.create(name, inlet);
  }

  /**
   * Factory method to create a Steel Catenary Riser.
   *
   * @param name equipment name
   * @param inlet inlet stream
   * @param waterDepth water depth in meters
   * @return configured SCR
   */
  public static PipeBeggsAndBrills createSCR(String name, StreamInterface inlet,
      double waterDepth) {
    return createRiser(RiserType.STEEL_CATENARY_RISER, name, inlet, waterDepth);
  }

  /**
   * Factory method to create a Top Tensioned Riser (vertical).
   *
   * @param name equipment name
   * @param inlet inlet stream
   * @param waterDepth water depth in meters
   * @return configured TTR
   */
  public static PipeBeggsAndBrills createTTR(String name, StreamInterface inlet,
      double waterDepth) {
    return createRiser(RiserType.TOP_TENSIONED_RISER, name, inlet, waterDepth);
  }

  /**
   * Factory method to create a Lazy-Wave riser.
   *
   * @param name equipment name
   * @param inlet inlet stream
   * @param waterDepth water depth in meters
   * @param buoyancyDepth depth of buoyancy modules from surface
   * @return configured lazy-wave riser
   */
  public static PipeBeggsAndBrills createLazyWave(String name, StreamInterface inlet,
      double waterDepth, double buoyancyDepth) {
    RiserConfiguration config = new RiserConfiguration(RiserType.LAZY_WAVE);
    config.setWaterDepth(waterDepth);
    config.setBuoyancyModuleDepth(buoyancyDepth);
    return config.create(name, inlet);
  }

  /**
   * Create a riser using the current configuration.
   *
   * @param name equipment name
   * @param inlet inlet stream
   * @return configured pipe as riser
   */
  public PipeBeggsAndBrills create(String name, StreamInterface inlet) {
    PipeBeggsAndBrills riser = new PipeBeggsAndBrills(name, inlet);

    // Configure geometry based on riser type
    switch (riserType) {
      case STEEL_CATENARY_RISER:
        configureSCR(riser);
        break;
      case FLEXIBLE_RISER:
        configureFlexible(riser);
        break;
      case TOP_TENSIONED_RISER:
        configureTTR(riser);
        break;
      case LAZY_WAVE:
        configureLazyWave(riser);
        break;
      case STEEP_WAVE:
        configureSteepWave(riser);
        break;
      case HYBRID_RISER:
        configureHybrid(riser);
        break;
      case FREE_STANDING:
        configureFreeStanding(riser);
        break;
      case VERTICAL:
        configureVertical(riser);
        break;
      default:
        configureSCR(riser);
    }

    // Set common parameters
    riser.setDiameter(innerDiameter);
    riser.setConstantSurfaceTemperature(ambientTemperature, "C");

    return riser;
  }

  /**
   * Configure as Steel Catenary Riser.
   *
   * <p>
   * Catenary profile from seabed touchdown to platform hangoff. The catenary adds approximately
   * 10-20% to the vertical depth for arc length.
   * </p>
   *
   * @param riser the pipe to configure
   */
  private void configureSCR(PipeBeggsAndBrills riser) {
    double theta = Math.toRadians(topAngle);
    double phi = Math.toRadians(departureAngle);

    // Catenary length is longer than vertical depth
    double lengthFactor = 1.0 + 0.5 * (Math.sin(theta) + Math.sin(phi));
    double length = waterDepth * lengthFactor;
    riser.setLength(length);

    // Set total elevation change (positive = upward)
    riser.setElevation(waterDepth);

    // Calculate average angle based on catenary geometry
    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    riser.setAngle(avgAngle);
  }

  /**
   * Configure as Flexible Riser.
   *
   * <p>
   * Similar to SCR but with tighter bend radius capability.
   * </p>
   *
   * @param riser the pipe to configure
   */
  private void configureFlexible(PipeBeggsAndBrills riser) {
    // Flexible risers can have tighter bends
    double length = waterDepth * 1.3;
    riser.setLength(length);
    riser.setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    riser.setAngle(avgAngle);
  }

  /**
   * Configure as Top Tensioned Riser (vertical).
   *
   * @param riser the pipe to configure
   */
  private void configureTTR(PipeBeggsAndBrills riser) {
    riser.setLength(waterDepth);
    riser.setElevation(waterDepth);
    riser.setAngle(90.0); // Vertical
  }

  /**
   * Configure as Lazy-Wave riser.
   *
   * <p>
   * Profile: Seabed → Sag bend → Buoyancy section (rise) → Hog bend → Platform. Lazy-wave
   * configuration reduces dynamic loads on the touchdown point.
   * </p>
   *
   * @param riser the pipe to configure
   */
  private void configureLazyWave(PipeBeggsAndBrills riser) {
    // Lazy-wave is longer due to the wave shape
    double length = waterDepth * 1.8;
    riser.setLength(length);
    riser.setElevation(waterDepth);

    // Average angle for lazy-wave
    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    riser.setAngle(avgAngle);
  }

  /**
   * Configure as Steep-Wave riser.
   *
   * <p>
   * Similar to lazy-wave but with steeper angles. Used in deeper water.
   * </p>
   *
   * @param riser the pipe to configure
   */
  private void configureSteepWave(PipeBeggsAndBrills riser) {
    double length = waterDepth * 1.5;
    riser.setLength(length);
    riser.setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    riser.setAngle(avgAngle);
  }

  /**
   * Configure as Hybrid Riser.
   *
   * <p>
   * Hybrid: Subsea jumper → Riser base → Vertical riser to buoy → Flexible jumper to platform.
   * </p>
   *
   * @param riser the pipe to configure
   */
  private void configureHybrid(PipeBeggsAndBrills riser) {
    double length = waterDepth * 1.4;
    riser.setLength(length);
    riser.setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    riser.setAngle(avgAngle);
  }

  /**
   * Configure as Free-Standing Hybrid Riser.
   *
   * @param riser the pipe to configure
   */
  private void configureFreeStanding(PipeBeggsAndBrills riser) {
    double length = waterDepth * 1.2;
    riser.setLength(length);
    riser.setElevation(waterDepth);

    double avgAngle = Math.toDegrees(Math.asin(waterDepth / length));
    riser.setAngle(avgAngle);
  }

  /**
   * Configure as simple vertical riser.
   *
   * @param riser the pipe to configure
   */
  private void configureVertical(PipeBeggsAndBrills riser) {
    riser.setLength(waterDepth);
    riser.setElevation(waterDepth);
    riser.setAngle(90.0); // Vertical
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
   * @return this configuration for chaining
   */
  public RiserConfiguration setRiserType(RiserType riserType) {
    this.riserType = riserType;
    return this;
  }

  /**
   * Get water depth in meters.
   *
   * @return water depth
   */
  public double getWaterDepth() {
    return waterDepth;
  }

  /**
   * Set water depth.
   *
   * @param waterDepth depth in meters
   * @return this configuration for chaining
   */
  public RiserConfiguration setWaterDepth(double waterDepth) {
    this.waterDepth = waterDepth;
    return this;
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
   * @return this configuration for chaining
   */
  public RiserConfiguration setTopAngle(double topAngle) {
    this.topAngle = topAngle;
    return this;
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
   * @return this configuration for chaining
   */
  public RiserConfiguration setDepartureAngle(double departureAngle) {
    this.departureAngle = departureAngle;
    return this;
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
   * @return this configuration for chaining
   */
  public RiserConfiguration setBuoyancyModuleDepth(double buoyancyModuleDepth) {
    this.buoyancyModuleDepth = buoyancyModuleDepth;
    return this;
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
   * @return this configuration for chaining
   */
  public RiserConfiguration setBuoyancyModuleLength(double buoyancyModuleLength) {
    this.buoyancyModuleLength = buoyancyModuleLength;
    return this;
  }

  /**
   * Get inner diameter.
   *
   * @return diameter in meters
   */
  public double getInnerDiameter() {
    return innerDiameter;
  }

  /**
   * Set inner diameter.
   *
   * @param innerDiameter diameter in meters
   * @return this configuration for chaining
   */
  public RiserConfiguration setInnerDiameter(double innerDiameter) {
    this.innerDiameter = innerDiameter;
    return this;
  }

  /**
   * Set inner diameter with unit.
   *
   * @param innerDiameter diameter value
   * @param unit unit (m, mm, inch, in)
   * @return this configuration for chaining
   */
  public RiserConfiguration setInnerDiameter(double innerDiameter, String unit) {
    if (unit.equalsIgnoreCase("mm")) {
      this.innerDiameter = innerDiameter / 1000.0;
    } else if (unit.equalsIgnoreCase("inch") || unit.equalsIgnoreCase("in")) {
      this.innerDiameter = innerDiameter * 0.0254;
    } else {
      this.innerDiameter = innerDiameter;
    }
    return this;
  }

  /**
   * Get number of sections for profile.
   *
   * @return number of sections
   */
  public int getNumberOfSections() {
    return numberOfSections;
  }

  /**
   * Set number of sections for elevation profile.
   *
   * @param numberOfSections number of sections (more = smoother profile)
   * @return this configuration for chaining
   */
  public RiserConfiguration setNumberOfSections(int numberOfSections) {
    this.numberOfSections = numberOfSections;
    return this;
  }

  /**
   * Get heat transfer coefficient.
   *
   * @return U-value in W/(m²·K)
   */
  public double getHeatTransferCoefficient() {
    return heatTransferCoefficient;
  }

  /**
   * Set overall heat transfer coefficient.
   *
   * @param heatTransferCoefficient U-value in W/(m²·K)
   * @return this configuration for chaining
   */
  public RiserConfiguration setHeatTransferCoefficient(double heatTransferCoefficient) {
    this.heatTransferCoefficient = heatTransferCoefficient;
    return this;
  }

  /**
   * Get ambient temperature.
   *
   * @return temperature in Celsius
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Set ambient/seawater temperature.
   *
   * @param ambientTemperature temperature in Celsius
   * @return this configuration for chaining
   */
  public RiserConfiguration setAmbientTemperature(double ambientTemperature) {
    this.ambientTemperature = ambientTemperature;
    return this;
  }

  /**
   * Calculate the total riser length based on configuration.
   *
   * @return estimated riser length in meters
   */
  public double calculateRiserLength() {
    switch (riserType) {
      case STEEL_CATENARY_RISER:
        return waterDepth * 1.15;
      case FLEXIBLE_RISER:
        return waterDepth * 1.3;
      case TOP_TENSIONED_RISER:
      case VERTICAL:
        return waterDepth;
      case LAZY_WAVE:
        return waterDepth * 1.8;
      case STEEP_WAVE:
        return waterDepth * 1.5;
      case HYBRID_RISER:
        return waterDepth * 1.4;
      case FREE_STANDING:
        return waterDepth * 1.2;
      default:
        return waterDepth * 1.15;
    }
  }

  /**
   * Get a description of the riser configuration.
   *
   * @return description string
   */
  public String getDescription() {
    StringBuilder sb = new StringBuilder();
    sb.append("Riser Configuration: ").append(riserType.name()).append("\n");
    sb.append("  Water depth: ").append(waterDepth).append(" m\n");
    sb.append("  Estimated length: ").append(String.format("%.1f", calculateRiserLength()))
        .append(" m\n");
    sb.append("  Inner diameter: ").append(innerDiameter * 1000).append(" mm\n");
    sb.append("  Sections: ").append(numberOfSections).append("\n");

    if (riserType == RiserType.STEEL_CATENARY_RISER) {
      sb.append("  Top angle: ").append(topAngle).append("°\n");
      sb.append("  Departure angle: ").append(departureAngle).append("°\n");
    } else if (riserType == RiserType.LAZY_WAVE) {
      sb.append("  Buoyancy depth: ").append(buoyancyModuleDepth).append(" m\n");
      sb.append("  Buoyancy length: ").append(buoyancyModuleLength).append(" m\n");
    }

    return sb.toString();
  }
}
