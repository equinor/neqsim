package neqsim.process.streaming;

import java.io.Serializable;
import java.time.Instant;

/**
 * Represents a value with an associated timestamp for real-time data streaming.
 *
 * <p>
 * This class is designed for high-frequency data exchange with AI-based production optimization
 * platforms and real-time digital twin systems.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class TimestampedValue implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double value;
  private final Instant timestamp;
  private final String unit;
  private final Quality quality;

  /**
   * Quality indicator for the measurement value.
   */
  public enum Quality {
    /** Good quality, normal operation. */
    GOOD,
    /** Uncertain quality, use with caution. */
    UNCERTAIN,
    /** Bad quality, value should not be used. */
    BAD,
    /** Value is from simulation, not measurement. */
    SIMULATED,
    /** Value is interpolated or estimated. */
    ESTIMATED
  }

  /**
   * Creates a new timestamped value with current time and GOOD quality.
   *
   * @param value the numeric value
   * @param unit the engineering unit
   */
  public TimestampedValue(double value, String unit) {
    this(value, unit, Instant.now(), Quality.GOOD);
  }

  /**
   * Creates a new timestamped value with specified timestamp and GOOD quality.
   *
   * @param value the numeric value
   * @param unit the engineering unit
   * @param timestamp the timestamp
   */
  public TimestampedValue(double value, String unit, Instant timestamp) {
    this(value, unit, timestamp, Quality.GOOD);
  }

  /**
   * Creates a new timestamped value with all parameters.
   *
   * @param value the numeric value
   * @param unit the engineering unit
   * @param timestamp the timestamp
   * @param quality the quality indicator
   */
  public TimestampedValue(double value, String unit, Instant timestamp, Quality quality) {
    this.value = value;
    this.unit = unit;
    this.timestamp = timestamp;
    this.quality = quality;
  }

  /**
   * Gets the numeric value.
   *
   * @return the value
   */
  public double getValue() {
    return value;
  }

  /**
   * Gets the engineering unit.
   *
   * @return the unit string
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Gets the timestamp.
   *
   * @return the timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the quality indicator.
   *
   * @return the quality
   */
  public Quality getQuality() {
    return quality;
  }

  /**
   * Checks if the value is usable (GOOD, SIMULATED, or ESTIMATED quality).
   *
   * @return true if the value can be used for calculations
   */
  public boolean isUsable() {
    return quality == Quality.GOOD || quality == Quality.SIMULATED || quality == Quality.ESTIMATED;
  }

  /**
   * Creates a new TimestampedValue marked as simulated.
   *
   * @param value the numeric value
   * @param unit the engineering unit
   * @return a new TimestampedValue with SIMULATED quality
   */
  public static TimestampedValue simulated(double value, String unit) {
    return new TimestampedValue(value, unit, Instant.now(), Quality.SIMULATED);
  }

  @Override
  public String toString() {
    return String.format("%.4f %s @ %s [%s]", value, unit, timestamp, quality);
  }
}
