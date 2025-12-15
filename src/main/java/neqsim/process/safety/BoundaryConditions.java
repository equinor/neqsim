package neqsim.process.safety;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable container for environmental boundary conditions in safety scenarios.
 *
 * <p>
 * Boundary conditions define the ambient environment during a safety event, which affects:
 * <ul>
 * <li>Heat transfer to/from equipment</li>
 * <li>Dispersion of released material</li>
 * <li>Fire development and radiation</li>
 * <li>Ignition probability</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * 
 * <pre>
 * BoundaryConditions conditions = BoundaryConditions.builder().ambientTemperature(278.15) // 5°C
 *     .windSpeed(8.0) // 8 m/s
 *     .relativeHumidity(0.75) // 75%
 *     .pasquillStabilityClass('D').build();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public final class BoundaryConditions implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Default ambient temperature [K] - approximately 15°C. */
  public static final double DEFAULT_AMBIENT_TEMPERATURE = 288.15;

  /** Default wind speed [m/s]. */
  public static final double DEFAULT_WIND_SPEED = 5.0;

  /** Default relative humidity [fraction]. */
  public static final double DEFAULT_RELATIVE_HUMIDITY = 0.6;

  /** Default atmospheric pressure [Pa]. */
  public static final double DEFAULT_ATMOSPHERIC_PRESSURE = 101325.0;

  private final double ambientTemperature; // K
  private final double windSpeed; // m/s
  private final double windDirection; // degrees from North (0-360)
  private final double relativeHumidity; // fraction (0-1)
  private final double atmosphericPressure; // Pa
  private final double solarRadiation; // W/m²
  private final char pasquillStabilityClass; // A-F
  private final double surfaceRoughness; // m
  private final double seaWaterTemperature; // K (for offshore)
  private final boolean isOffshore;

  private BoundaryConditions(Builder builder) {
    this.ambientTemperature = builder.ambientTemperature;
    this.windSpeed = builder.windSpeed;
    this.windDirection = builder.windDirection;
    this.relativeHumidity = builder.relativeHumidity;
    this.atmosphericPressure = builder.atmosphericPressure;
    this.solarRadiation = builder.solarRadiation;
    this.pasquillStabilityClass = builder.pasquillStabilityClass;
    this.surfaceRoughness = builder.surfaceRoughness;
    this.seaWaterTemperature = builder.seaWaterTemperature;
    this.isOffshore = builder.isOffshore;
  }

  /**
   * Gets ambient temperature.
   *
   * @return ambient temperature [K]
   */
  public double getAmbientTemperature() {
    return ambientTemperature;
  }

  /**
   * Gets ambient temperature in specified unit.
   *
   * @param unit temperature unit ("K", "C", "F")
   * @return ambient temperature in specified unit
   */
  public double getAmbientTemperature(String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      return ambientTemperature - 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      return (ambientTemperature - 273.15) * 9.0 / 5.0 + 32.0;
    }
    return ambientTemperature;
  }

  /**
   * Gets wind speed.
   *
   * @return wind speed [m/s]
   */
  public double getWindSpeed() {
    return windSpeed;
  }

  /**
   * Gets wind direction.
   *
   * @return wind direction [degrees from North, 0-360]
   */
  public double getWindDirection() {
    return windDirection;
  }

  /**
   * Gets relative humidity.
   *
   * @return relative humidity [fraction 0-1]
   */
  public double getRelativeHumidity() {
    return relativeHumidity;
  }

  /**
   * Gets atmospheric pressure.
   *
   * @return atmospheric pressure [Pa]
   */
  public double getAtmosphericPressure() {
    return atmosphericPressure;
  }

  /**
   * Gets atmospheric pressure in bar.
   *
   * @return atmospheric pressure [bar]
   */
  public double getAtmosphericPressureBar() {
    return atmosphericPressure / 1e5;
  }

  /**
   * Gets solar radiation.
   *
   * @return solar radiation [W/m²]
   */
  public double getSolarRadiation() {
    return solarRadiation;
  }

  /**
   * Gets Pasquill atmospheric stability class.
   *
   * <p>
   * Stability classes range from A (very unstable) to F (very stable):
   * <ul>
   * <li>A - Very unstable (strong insolation, light wind)</li>
   * <li>B - Unstable</li>
   * <li>C - Slightly unstable</li>
   * <li>D - Neutral (overcast, strong wind)</li>
   * <li>E - Slightly stable</li>
   * <li>F - Stable (night, light wind)</li>
   * </ul>
   *
   * @return Pasquill stability class (A-F)
   */
  public char getPasquillStabilityClass() {
    return pasquillStabilityClass;
  }

  /**
   * Gets surface roughness length.
   *
   * @return surface roughness [m]
   */
  public double getSurfaceRoughness() {
    return surfaceRoughness;
  }

  /**
   * Gets sea water temperature (for offshore installations).
   *
   * @return sea water temperature [K]
   */
  public double getSeaWaterTemperature() {
    return seaWaterTemperature;
  }

  /**
   * Gets sea water temperature in specified unit.
   *
   * @param unit temperature unit ("K", "C", "F")
   * @return sea water temperature in specified unit
   */
  public double getSeaWaterTemperature(String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      return seaWaterTemperature - 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      return (seaWaterTemperature - 273.15) * 9.0 / 5.0 + 32.0;
    }
    return seaWaterTemperature;
  }

  /**
   * Checks if this is an offshore location.
   *
   * @return true if offshore
   */
  public boolean isOffshore() {
    return isOffshore;
  }

  /**
   * Creates a new builder with default values.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates default boundary conditions (temperate onshore).
   *
   * @return default boundary conditions
   */
  public static BoundaryConditions defaultConditions() {
    return builder().build();
  }

  /**
   * Creates typical North Sea winter conditions.
   *
   * @return North Sea winter conditions
   */
  public static BoundaryConditions northSeaWinter() {
    return builder().ambientTemperature(278.15) // 5°C
        .windSpeed(15.0).relativeHumidity(0.85).pasquillStabilityClass('D')
        .seaWaterTemperature(280.15) // 7°C
        .isOffshore(true).surfaceRoughness(0.0002) // Open sea
        .build();
  }

  /**
   * Creates typical North Sea summer conditions.
   *
   * @return North Sea summer conditions
   */
  public static BoundaryConditions northSeaSummer() {
    return builder().ambientTemperature(288.15) // 15°C
        .windSpeed(8.0).relativeHumidity(0.75).solarRadiation(500.0).pasquillStabilityClass('C')
        .seaWaterTemperature(285.15) // 12°C
        .isOffshore(true).surfaceRoughness(0.0002).build();
  }

  /**
   * Creates typical Gulf of Mexico conditions.
   *
   * @return Gulf of Mexico conditions
   */
  public static BoundaryConditions gulfOfMexico() {
    return builder().ambientTemperature(303.15) // 30°C
        .windSpeed(5.0).relativeHumidity(0.80).solarRadiation(800.0).pasquillStabilityClass('B')
        .seaWaterTemperature(299.15) // 26°C
        .isOffshore(true).surfaceRoughness(0.0002).build();
  }

  /**
   * Creates typical onshore industrial conditions.
   *
   * @return onshore industrial conditions
   */
  public static BoundaryConditions onshoreIndustrial() {
    return builder().ambientTemperature(293.15) // 20°C
        .windSpeed(3.0).relativeHumidity(0.50).solarRadiation(400.0).pasquillStabilityClass('D')
        .isOffshore(false).surfaceRoughness(1.0) // Industrial area
        .build();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BoundaryConditions)) {
      return false;
    }
    BoundaryConditions that = (BoundaryConditions) o;
    return Double.compare(that.ambientTemperature, ambientTemperature) == 0
        && Double.compare(that.windSpeed, windSpeed) == 0
        && Double.compare(that.windDirection, windDirection) == 0
        && Double.compare(that.relativeHumidity, relativeHumidity) == 0
        && Double.compare(that.atmosphericPressure, atmosphericPressure) == 0
        && pasquillStabilityClass == that.pasquillStabilityClass && isOffshore == that.isOffshore;
  }

  @Override
  public int hashCode() {
    return Objects.hash(ambientTemperature, windSpeed, windDirection, relativeHumidity,
        atmosphericPressure, pasquillStabilityClass, isOffshore);
  }

  @Override
  public String toString() {
    return String.format(
        "BoundaryConditions[T=%.1f°C, wind=%.1f m/s @ %.0f°, RH=%.0f%%, P=%.0f Pa, stability=%c, %s]",
        getAmbientTemperature("C"), windSpeed, windDirection, relativeHumidity * 100,
        atmosphericPressure, pasquillStabilityClass, isOffshore ? "offshore" : "onshore");
  }

  /**
   * Builder for BoundaryConditions.
   */
  public static final class Builder {
    private double ambientTemperature = DEFAULT_AMBIENT_TEMPERATURE;
    private double windSpeed = DEFAULT_WIND_SPEED;
    private double windDirection = 270.0; // West wind
    private double relativeHumidity = DEFAULT_RELATIVE_HUMIDITY;
    private double atmosphericPressure = DEFAULT_ATMOSPHERIC_PRESSURE;
    private double solarRadiation = 0.0;
    private char pasquillStabilityClass = 'D'; // Neutral
    private double surfaceRoughness = 0.1; // Default for mixed terrain
    private double seaWaterTemperature = 283.15; // 10°C
    private boolean isOffshore = false;

    /**
     * Sets ambient temperature [K].
     *
     * @param temperature ambient temperature in Kelvin
     * @return this builder
     */
    public Builder ambientTemperature(double temperature) {
      this.ambientTemperature = temperature;
      return this;
    }

    /**
     * Sets ambient temperature with unit.
     *
     * @param temperature ambient temperature value
     * @param unit temperature unit ("K", "C", "F")
     * @return this builder
     */
    public Builder ambientTemperature(double temperature, String unit) {
      if ("C".equalsIgnoreCase(unit)) {
        this.ambientTemperature = temperature + 273.15;
      } else if ("F".equalsIgnoreCase(unit)) {
        this.ambientTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
      } else {
        this.ambientTemperature = temperature;
      }
      return this;
    }

    /**
     * Sets wind speed [m/s].
     *
     * @param speed wind speed
     * @return this builder
     */
    public Builder windSpeed(double speed) {
      this.windSpeed = speed;
      return this;
    }

    /**
     * Sets wind direction [degrees from North].
     *
     * @param direction wind direction (0-360)
     * @return this builder
     */
    public Builder windDirection(double direction) {
      this.windDirection = direction % 360.0;
      return this;
    }

    /**
     * Sets relative humidity [fraction 0-1].
     *
     * @param humidity relative humidity
     * @return this builder
     */
    public Builder relativeHumidity(double humidity) {
      this.relativeHumidity = Math.max(0.0, Math.min(1.0, humidity));
      return this;
    }

    /**
     * Sets atmospheric pressure [Pa].
     *
     * @param pressure atmospheric pressure
     * @return this builder
     */
    public Builder atmosphericPressure(double pressure) {
      this.atmosphericPressure = pressure;
      return this;
    }

    /**
     * Sets solar radiation [W/m²].
     *
     * @param radiation solar radiation
     * @return this builder
     */
    public Builder solarRadiation(double radiation) {
      this.solarRadiation = radiation;
      return this;
    }

    /**
     * Sets Pasquill atmospheric stability class (A-F).
     *
     * @param stabilityClass stability class character
     * @return this builder
     */
    public Builder pasquillStabilityClass(char stabilityClass) {
      char upper = Character.toUpperCase(stabilityClass);
      if (upper < 'A' || upper > 'F') {
        throw new IllegalArgumentException(
            "Pasquill stability class must be A-F, got: " + stabilityClass);
      }
      this.pasquillStabilityClass = upper;
      return this;
    }

    /**
     * Sets surface roughness length [m].
     *
     * <p>
     * Typical values:
     * <ul>
     * <li>0.0002 - Open sea</li>
     * <li>0.03 - Open flat terrain</li>
     * <li>0.1 - Agricultural land</li>
     * <li>0.5 - Suburban</li>
     * <li>1.0 - Urban/Industrial</li>
     * </ul>
     *
     * @param roughness surface roughness
     * @return this builder
     */
    public Builder surfaceRoughness(double roughness) {
      this.surfaceRoughness = roughness;
      return this;
    }

    /**
     * Sets sea water temperature [K].
     *
     * @param temperature sea water temperature
     * @return this builder
     */
    public Builder seaWaterTemperature(double temperature) {
      this.seaWaterTemperature = temperature;
      return this;
    }

    /**
     * Sets whether location is offshore.
     *
     * @param offshore true if offshore
     * @return this builder
     */
    public Builder isOffshore(boolean offshore) {
      this.isOffshore = offshore;
      return this;
    }

    /**
     * Builds the immutable BoundaryConditions.
     *
     * @return new BoundaryConditions instance
     */
    public BoundaryConditions build() {
      return new BoundaryConditions(this);
    }
  }
}
