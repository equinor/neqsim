package neqsim.process.equipment.stream;

import java.io.Serializable;
import java.util.Objects;

/**
 * Quality metadata carried by an {@link EnergyStream}.
 *
 * <p>
 * Power alone is not sufficient to determine whether two energy connections are compatible. Electrical systems also
 * require voltage and frequency, thermal utilities require a temperature grade, and rotating equipment requires a
 * compatible shaft speed. Unspecified values remain backward compatible and act as wildcards.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class EnergyQuality implements Serializable, Cloneable {
  private static final long serialVersionUID = 1000L;

  private UtilityLevel utilityLevel = UtilityLevel.UNSPECIFIED;
  private double voltage = Double.NaN;
  private double frequency = Double.NaN;
  private double temperature = Double.NaN;
  private double pressure = Double.NaN;
  private double shaftSpeed = Double.NaN;

  /** Creates unspecified energy quality metadata. */
  public EnergyQuality() {
  }

  /**
   * Creates quality metadata for a standard utility grade.
   *
   * @param utilityLevel standard utility grade
   */
  public EnergyQuality(UtilityLevel utilityLevel) {
    setUtilityLevel(utilityLevel);
  }

  /** {@inheritDoc} */
  @Override
  public EnergyQuality clone() {
    try {
      return (EnergyQuality) super.clone();
    } catch (CloneNotSupportedException ex) {
      throw new IllegalStateException("Energy quality could not be cloned", ex);
    }
  }

  /**
   * Checks whether this supplied quality can satisfy a requirement.
   *
   * <p>
   * Unspecified supplied or required values are treated as compatible. Specified electrical values use a five-percent
   * voltage tolerance and a two-percent frequency tolerance. A specified utility level must match exactly.
   * </p>
   *
   * @param required required quality metadata
   * @return {@code true} when the supplied quality is compatible
   */
  public boolean satisfies(EnergyQuality required) {
    if (required == null) {
      return true;
    }
    if (required.utilityLevel != UtilityLevel.UNSPECIFIED && utilityLevel != UtilityLevel.UNSPECIFIED
        && required.utilityLevel != utilityLevel) {
      return false;
    }
    return matchesWithinTolerance(voltage, required.voltage, 0.05)
        && matchesWithinTolerance(frequency, required.frequency, 0.02)
        && matchesWithinTolerance(temperature, required.temperature, 0.02)
        && matchesWithinTolerance(pressure, required.pressure, 0.05)
        && matchesWithinTolerance(shaftSpeed, required.shaftSpeed, 0.05);
  }

  /**
   * Compares a supplied and required value using a relative tolerance.
   *
   * @param supplied supplied value
   * @param required required value
   * @param tolerance relative tolerance
   * @return {@code true} when compatible
   */
  private static boolean matchesWithinTolerance(double supplied, double required, double tolerance) {
    if (!Double.isFinite(supplied) || !Double.isFinite(required)) {
      return true;
    }
    return Math.abs(supplied - required) <= Math.max(1.0, Math.abs(required)) * tolerance;
  }

  /**
   * Gets the utility grade.
   *
   * @return utility grade
   */
  public UtilityLevel getUtilityLevel() {
    return utilityLevel;
  }

  /**
   * Sets the utility grade.
   *
   * @param utilityLevel utility grade
   */
  public void setUtilityLevel(UtilityLevel utilityLevel) {
    this.utilityLevel = Objects.requireNonNull(utilityLevel, "utilityLevel cannot be null");
  }

  /**
   * Gets electrical voltage.
   *
   * @return voltage in V, or {@link Double#NaN} when unspecified
   */
  public double getVoltage() {
    return voltage;
  }

  /**
   * Sets electrical voltage.
   *
   * @param voltage voltage in V
   */
  public void setVoltage(double voltage) {
    this.voltage = requirePositiveOrNaN(voltage, "voltage");
  }

  /**
   * Gets electrical frequency.
   *
   * @return frequency in Hz, or {@link Double#NaN} when unspecified
   */
  public double getFrequency() {
    return frequency;
  }

  /**
   * Sets electrical frequency.
   *
   * @param frequency frequency in Hz
   */
  public void setFrequency(double frequency) {
    this.frequency = requirePositiveOrNaN(frequency, "frequency");
  }

  /**
   * Gets thermal temperature.
   *
   * @return temperature in K, or {@link Double#NaN} when unspecified
   */
  public double getTemperature() {
    return temperature;
  }

  /**
   * Sets thermal temperature.
   *
   * @param temperature temperature in K
   */
  public void setTemperature(double temperature) {
    this.temperature = requirePositiveOrNaN(temperature, "temperature");
  }

  /**
   * Gets utility pressure.
   *
   * @return pressure in Pa, or {@link Double#NaN} when unspecified
   */
  public double getPressure() {
    return pressure;
  }

  /**
   * Sets utility pressure.
   *
   * @param pressure pressure in Pa
   */
  public void setPressure(double pressure) {
    this.pressure = requirePositiveOrNaN(pressure, "pressure");
  }

  /**
   * Gets shaft speed.
   *
   * @return speed in rpm, or {@link Double#NaN} when unspecified
   */
  public double getShaftSpeed() {
    return shaftSpeed;
  }

  /**
   * Sets shaft speed.
   *
   * @param shaftSpeed speed in rpm
   */
  public void setShaftSpeed(double shaftSpeed) {
    this.shaftSpeed = requirePositiveOrNaN(shaftSpeed, "shaftSpeed");
  }

  /**
   * Validates a positive optional value.
   *
   * @param value candidate value
   * @param property property name
   * @return validated value
   */
  private static double requirePositiveOrNaN(double value, String property) {
    if (Double.isNaN(value)) {
      return value;
    }
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(property + " must be positive and finite, or NaN when unspecified");
    }
    return value;
  }
}
