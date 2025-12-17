package neqsim.fluidmechanics.geometrydefinitions.surrounding;

import neqsim.fluidmechanics.geometrydefinitions.internalgeometry.wall.PipeMaterial;

/**
 * Represents the surrounding environment of a pipe including special cases like buried pipes.
 *
 * <p>
 * This class extends {@link SurroundingEnvironmentBaseClass} to provide specialized models for:
 * </p>
 * <ul>
 * <li>Exposed pipes (air, sea water, etc.)</li>
 * <li>Buried pipes with soil thermal resistance</li>
 * <li>Subsea pipes with seawater convection</li>
 * </ul>
 *
 * <h2>Buried Pipe Heat Transfer</h2>
 * <p>
 * For buried pipes, the soil thermal resistance depends on burial depth and soil conductivity.
 * Using the shape factor method:
 * </p>
 *
 * <pre>
 * R_soil = (1 / (2π * k_soil)) * ln(2 * z / r_outer + sqrt((2 * z / r_outer)² - 1))
 * </pre>
 *
 * <p>
 * Where z is the depth to pipe centerline and r_outer is the outer pipe radius.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class PipeSurroundingEnvironment extends SurroundingEnvironmentBaseClass {

  /**
   * Environment types for pipe surroundings.
   */
  public enum EnvironmentType {
    /** Pipe exposed to ambient air. */
    AIR,
    /** Pipe submerged in seawater. */
    SEAWATER,
    /** Pipe buried in soil. */
    BURIED,
    /** Custom environment with user-defined properties. */
    CUSTOM
  }

  /** Type of surrounding environment. */
  private EnvironmentType environmentType = EnvironmentType.AIR;

  /** Burial depth to pipe centerline in meters (for buried pipes). */
  private double burialDepth = 0.0;

  /** Outer radius of the pipe in meters (for buried pipe calculations). */
  private double pipeOuterRadius = 0.0;

  /** Soil thermal conductivity in W/(m·K). */
  private double soilConductivity = 1.0;

  /** Soil material (for buried pipes). */
  private PipeMaterial soilMaterial = PipeMaterial.SOIL_TYPICAL;

  /** Seawater current velocity in m/s (for subsea pipes). */
  private double seawaterVelocity = 0.0;

  /** Wind velocity in m/s (for exposed pipes). */
  private double windVelocity = 0.0;

  /**
   * Default constructor for exposed pipe in air.
   */
  public PipeSurroundingEnvironment() {
    super();
    setForAir(0.0);
  }

  /**
   * Constructor for specific environment type.
   *
   * @param typeName Environment type name (AIR, SEAWATER, BURIED, CUSTOM)
   */
  public PipeSurroundingEnvironment(String typeName) {
    super();
    try {
      this.environmentType = EnvironmentType.valueOf(typeName.toUpperCase());
    } catch (IllegalArgumentException e) {
      this.environmentType = EnvironmentType.CUSTOM;
    }
  }

  // ===== Factory methods for common environments =====

  /**
   * Creates an environment for a pipe exposed to air.
   *
   * @param airTemperatureK Air temperature in Kelvin
   * @param windVelocityMs Wind velocity in m/s
   * @return Configured PipeSurroundingEnvironment
   */
  public static PipeSurroundingEnvironment exposedToAir(double airTemperatureK,
      double windVelocityMs) {
    PipeSurroundingEnvironment env = new PipeSurroundingEnvironment();
    env.setForAir(windVelocityMs);
    env.setTemperature(airTemperatureK);
    return env;
  }

  /**
   * Creates an environment for a subsea pipe.
   *
   * @param seawaterTemperatureK Seawater temperature in Kelvin
   * @param currentVelocityMs Current velocity in m/s
   * @return Configured PipeSurroundingEnvironment
   */
  public static PipeSurroundingEnvironment subseaPipe(double seawaterTemperatureK,
      double currentVelocityMs) {
    PipeSurroundingEnvironment env = new PipeSurroundingEnvironment();
    env.setForSeawater(currentVelocityMs);
    env.setTemperature(seawaterTemperatureK);
    return env;
  }

  /**
   * Creates an environment for a buried pipe.
   *
   * @param groundTemperatureK Undisturbed ground temperature in Kelvin
   * @param burialDepthM Burial depth to pipe centerline in meters
   * @param pipeOuterRadiusM Outer radius of the pipe in meters
   * @param soilMaterial Type of soil
   * @return Configured PipeSurroundingEnvironment
   */
  public static PipeSurroundingEnvironment buriedPipe(double groundTemperatureK,
      double burialDepthM, double pipeOuterRadiusM, PipeMaterial soilMaterial) {
    PipeSurroundingEnvironment env = new PipeSurroundingEnvironment();
    env.setForBuried(burialDepthM, pipeOuterRadiusM, soilMaterial);
    env.setTemperature(groundTemperatureK);
    return env;
  }

  // ===== Configuration methods =====

  /**
   * Configures for air exposure with wind.
   *
   * <p>
   * Uses empirical correlation for forced convection over a cylinder:
   * </p>
   *
   * <pre>
   * h = 5.7 + 3.8 * v (for v &lt; 5 m/s, W/(m²·K))
   * </pre>
   *
   * @param windVelocityMs Wind velocity in m/s
   */
  public void setForAir(double windVelocityMs) {
    this.environmentType = EnvironmentType.AIR;
    this.windVelocity = windVelocityMs;

    // Simplified correlation for air (natural + forced convection)
    // Based on typical engineering correlations
    double hNatural = 5.7; // Natural convection base W/(m²·K)
    double hForced = 3.8 * windVelocityMs; // Forced convection contribution
    setHeatTransferCoefficient(hNatural + hForced);
  }

  /**
   * Configures for seawater exposure.
   *
   * <p>
   * Seawater has much higher heat transfer coefficients than air due to its higher thermal
   * conductivity and density.
   * </p>
   *
   * @param currentVelocityMs Seawater current velocity in m/s
   */
  public void setForSeawater(double currentVelocityMs) {
    this.environmentType = EnvironmentType.SEAWATER;
    this.seawaterVelocity = currentVelocityMs;

    // Typical seawater heat transfer coefficients
    // Still water: ~200-500 W/(m²·K)
    // Flowing water: can be 1000+ W/(m²·K)
    double hBase = 300.0;
    double hVelocity = 700.0 * Math.sqrt(currentVelocityMs); // Velocity enhancement
    setHeatTransferCoefficient(hBase + hVelocity);
  }

  /**
   * Configures for buried pipe.
   *
   * <p>
   * Calculates the soil thermal resistance using the shape factor method and converts to an
   * equivalent heat transfer coefficient.
   * </p>
   *
   * @param depthM Burial depth to pipe centerline in meters
   * @param outerRadiusM Outer radius of the pipe in meters
   * @param soil Soil material type
   */
  public void setForBuried(double depthM, double outerRadiusM, PipeMaterial soil) {
    this.environmentType = EnvironmentType.BURIED;
    this.burialDepth = depthM;
    this.pipeOuterRadius = outerRadiusM;
    this.soilMaterial = soil;
    this.soilConductivity = soil.getThermalConductivity();

    // Calculate equivalent heat transfer coefficient
    double h = calcBuriedPipeHeatTransferCoefficient(depthM, outerRadiusM, soilConductivity);
    setHeatTransferCoefficient(h);
  }

  /**
   * Calculates the heat transfer coefficient for a buried pipe.
   *
   * <p>
   * Uses the shape factor for a cylinder buried in a semi-infinite medium:
   * </p>
   *
   * <pre>
   * S = 2π / ln(2z/r + sqrt((2z/r)² - 1))
   * </pre>
   *
   * <p>
   * The equivalent heat transfer coefficient (referenced to outer surface) is:
   * </p>
   *
   * <pre>
   * h = k_soil * S / (2π * r)
   * </pre>
   *
   * @param depthM Burial depth to centerline in meters
   * @param outerRadiusM Pipe outer radius in meters
   * @param kSoil Soil thermal conductivity in W/(m·K)
   * @return Equivalent heat transfer coefficient in W/(m²·K)
   */
  public static double calcBuriedPipeHeatTransferCoefficient(double depthM, double outerRadiusM,
      double kSoil) {
    if (depthM <= outerRadiusM) {
      // Pipe is at or above ground surface
      throw new IllegalArgumentException("Burial depth must be greater than pipe outer radius");
    }

    // Shape factor calculation
    double ratio = depthM / outerRadiusM;
    double shapeFactor = 2.0 * Math.PI / Math.log(2.0 * ratio + Math.sqrt(4.0 * ratio * ratio - 1));

    // Convert to equivalent heat transfer coefficient (per unit area of outer surface)
    return kSoil * shapeFactor / (2.0 * Math.PI * outerRadiusM);
  }

  /**
   * Calculates the thermal resistance per unit length for a buried pipe.
   *
   * @param depthM Burial depth to centerline in meters
   * @param outerRadiusM Pipe outer radius in meters
   * @param kSoil Soil thermal conductivity in W/(m·K)
   * @return Thermal resistance in K·m/W
   */
  public static double calcBuriedPipeThermalResistance(double depthM, double outerRadiusM,
      double kSoil) {
    if (depthM <= outerRadiusM) {
      throw new IllegalArgumentException("Burial depth must be greater than pipe outer radius");
    }

    double ratio = depthM / outerRadiusM;
    return Math.log(2.0 * ratio + Math.sqrt(4.0 * ratio * ratio - 1)) / (2.0 * Math.PI * kSoil);
  }

  // ===== Getters =====

  /**
   * Gets the environment type.
   *
   * @return Environment type
   */
  public EnvironmentType getEnvironmentType() {
    return environmentType;
  }

  /**
   * Gets the burial depth (for buried pipes).
   *
   * @return Burial depth in meters
   */
  public double getBurialDepth() {
    return burialDepth;
  }

  /**
   * Gets the soil thermal conductivity.
   *
   * @return Soil conductivity in W/(m·K)
   */
  public double getSoilConductivity() {
    return soilConductivity;
  }

  /**
   * Gets the seawater velocity.
   *
   * @return Seawater current velocity in m/s
   */
  public double getSeawaterVelocity() {
    return seawaterVelocity;
  }

  /**
   * Gets the wind velocity.
   *
   * @return Wind velocity in m/s
   */
  public double getWindVelocity() {
    return windVelocity;
  }

  /**
   * Checks if this is a buried pipe configuration.
   *
   * @return true if pipe is buried
   */
  public boolean isBuried() {
    return environmentType == EnvironmentType.BURIED;
  }

  /**
   * Checks if this is a subsea configuration.
   *
   * @return true if pipe is subsea
   */
  public boolean isSubsea() {
    return environmentType == EnvironmentType.SEAWATER;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("PipeSurroundingEnvironment[type=%s, T=%.1f K, h=%.1f W/(m²·K)]",
        environmentType, getTemperature(), getHeatTransferCoefficient()));

    if (environmentType == EnvironmentType.BURIED) {
      sb.append(
          String.format(" (depth=%.2f m, soil=%s)", burialDepth, soilMaterial.getDisplayName()));
    } else if (environmentType == EnvironmentType.SEAWATER) {
      sb.append(String.format(" (velocity=%.2f m/s)", seawaterVelocity));
    } else if (environmentType == EnvironmentType.AIR) {
      sb.append(String.format(" (wind=%.1f m/s)", windVelocity));
    }

    return sb.toString();
  }
}
