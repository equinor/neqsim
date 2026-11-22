package neqsim.process.operations.replay;

import java.io.Serializable;
import java.util.function.DoubleUnaryOperator;

/**
 * Binding between a data-series tag and a {@link neqsim.process.automation.ProcessAutomation} address used by
 * {@link ProcessReplay}.
 *
 * <p>
 * An input binding reads a column of the time series, converts it with {@code value * scale + offset}, applies an
 * optional transform and writes the result to the automation address in {@link #getUnit()}. An output binding reads the
 * automation address in {@link #getUnit()} as the model prediction and converts the measured column with the same
 * scale, offset and transform so that both are compared in the model unit.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ReplayBinding implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Direction of a binding. */
  public enum Direction {
    /** Data column written to the model. */
    INPUT,
    /** Model value compared with a measured data column. */
    OUTPUT
  }

  private final Direction direction;
  private final String tag;
  private final String automationAddress;
  private final String unit;
  private double scale = 1.0;
  private double offset = 0.0;
  private double minValid = Double.NEGATIVE_INFINITY;
  private double maxValid = Double.POSITIVE_INFINITY;
  private transient DoubleUnaryOperator transform;

  /**
   * Creates a binding.
   *
   * @param direction input or output
   * @param tag data-series column name
   * @param automationAddress automation address in the model
   * @param unit model unit of the automation variable
   */
  private ReplayBinding(Direction direction, String tag, String automationAddress, String unit) {
    if (tag == null || tag.trim().isEmpty()) {
      throw new IllegalArgumentException("tag must not be empty");
    }
    if (automationAddress == null || automationAddress.trim().isEmpty()) {
      throw new IllegalArgumentException("automationAddress must not be empty");
    }
    this.direction = direction;
    this.tag = tag.trim();
    this.automationAddress = automationAddress.trim();
    this.unit = unit == null ? "" : unit.trim();
  }

  /**
   * Creates an input binding that writes a data column to a model variable.
   *
   * @param tag data-series column name
   * @param automationAddress automation address, for example {@code "feed.temperature"}
   * @param unit unit of the value written to the model, for example {@code "C"}
   * @return the input binding
   */
  public static ReplayBinding input(String tag, String automationAddress, String unit) {
    return new ReplayBinding(Direction.INPUT, tag, automationAddress, unit);
  }

  /**
   * Creates an output binding that compares a model variable with a measured data column.
   *
   * @param automationAddress automation address of the predicted value
   * @param measuredTag data-series column holding the measurement
   * @param unit unit in which prediction and measurement are compared
   * @return the output binding
   */
  public static ReplayBinding output(String automationAddress, String measuredTag, String unit) {
    return new ReplayBinding(Direction.OUTPUT, measuredTag, automationAddress, unit);
  }

  /**
   * Sets a linear unit conversion applied to the raw data value: {@code value * scale + offset}.
   *
   * @param scale multiplier
   * @param offset additive offset applied after scaling
   * @return this binding
   */
  public ReplayBinding withLinearConversion(double scale, double offset) {
    this.scale = scale;
    this.offset = offset;
    return this;
  }

  /**
   * Sets an optional transform applied after the linear conversion. The transform is not serialised.
   *
   * @param transform value transform, or null for none
   * @return this binding
   */
  public ReplayBinding withTransform(DoubleUnaryOperator transform) {
    this.transform = transform;
    return this;
  }

  /**
   * Sets the valid range of the converted value. Values outside the range mark the step as having invalid input (for an
   * input binding) or as an invalid measurement (for an output binding).
   *
   * @param min minimum valid converted value
   * @param max maximum valid converted value
   * @return this binding
   */
  public ReplayBinding withValidRange(double min, double max) {
    this.minValid = min;
    this.maxValid = max;
    return this;
  }

  /**
   * Converts a raw data value to the model unit.
   *
   * @param raw raw data value
   * @return converted value (NaN if the raw value is not finite)
   */
  public double convert(double raw) {
    if (!isFinite(raw)) {
      return Double.NaN;
    }
    double value = raw * scale + offset;
    if (transform != null) {
      value = transform.applyAsDouble(value);
    }
    return value;
  }

  /**
   * Checks whether a converted value is finite and inside the valid range.
   *
   * @param converted converted value
   * @return true when the value is usable
   */
  public boolean isValid(double converted) {
    return isFinite(converted) && converted >= minValid && converted <= maxValid;
  }

  /**
   * Java 8 compatible finite check.
   *
   * @param value value to check
   * @return true when the value is neither NaN nor infinite
   */
  static boolean isFinite(double value) {
    return !Double.isNaN(value) && !Double.isInfinite(value);
  }

  /**
   * Returns the binding direction.
   *
   * @return input or output
   */
  public Direction getDirection() {
    return direction;
  }

  /**
   * Returns the data-series column name (the measured tag for an output binding).
   *
   * @return tag name
   */
  public String getTag() {
    return tag;
  }

  /**
   * Returns the automation address.
   *
   * @return automation address
   */
  public String getAutomationAddress() {
    return automationAddress;
  }

  /**
   * Returns the model unit.
   *
   * @return unit string, empty when the automation default unit is used
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the linear conversion scale.
   *
   * @return scale factor
   */
  public double getScale() {
    return scale;
  }

  /**
   * Returns the linear conversion offset.
   *
   * @return offset
   */
  public double getOffset() {
    return offset;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return direction + " " + tag + (direction == Direction.INPUT ? " -> " : " <- ") + automationAddress + " [" + unit
        + "]";
  }
}
