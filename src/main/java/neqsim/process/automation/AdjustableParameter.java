package neqsim.process.automation;

import java.io.Serializable;
import com.google.gson.JsonObject;

/**
 * Describes a single adjustable parameter (a degree of freedom) exposed by a process simulation.
 *
 * <p>
 * An adjustable parameter is an input that an agent or optimizer may change to influence the process. It is sourced
 * either from a writable {@link SimulationVariable} of type {@link SimulationVariable.VariableType#INPUT}, or from an
 * {@link neqsim.process.equipment.util.Adjuster} unit operation. For adjuster-sourced parameters the
 * {@link #getTargetUnitName()} and {@link #getTargetProperty()} fields make explicit what the parameter actually
 * affects, eliminating the guesswork that arises when a handle name does not match the variable it drives.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class AdjustableParameter implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Identifies where an adjustable parameter originates.
   */
  public enum Source {
    /** A writable INPUT variable discovered through process automation. */
    INPUT_VARIABLE,
    /** An {@link neqsim.process.equipment.util.Adjuster} unit operation. */
    ADJUSTER
  }

  private final String name;
  private final String address;
  private final String unit;
  private final Double lowerBound;
  private final Double upperBound;
  private final String targetUnitName;
  private final String targetProperty;
  private final Source source;

  /**
   * Creates a new adjustable parameter descriptor.
   *
   * @param name the short, human-readable parameter name
   * @param address the stable dot-notation address used with
   * {@link ProcessAutomation#setVariableValue(String, double, String)}
   * @param unit the unit of measure for the parameter, e.g. "bara", "C", "kg/hr"
   * @param lowerBound the lower bound of the parameter, or null if unbounded
   * @param upperBound the upper bound of the parameter, or null if unbounded
   * @param targetUnitName the name of the unit operation that the parameter actually affects, or null if the parameter
   * affects its own owning unit
   * @param targetProperty the property the parameter actually drives toward, or null if not applicable
   * @param source where the parameter originates ({@link Source#INPUT_VARIABLE} or {@link Source#ADJUSTER})
   */
  public AdjustableParameter(String name, String address, String unit, Double lowerBound, Double upperBound,
      String targetUnitName, String targetProperty, Source source) {
    this.name = name;
    this.address = address;
    this.unit = unit;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
    this.targetUnitName = targetUnitName;
    this.targetProperty = targetProperty;
    this.source = source;
  }

  /**
   * Returns the short, human-readable parameter name.
   *
   * @return the parameter name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the stable dot-notation address for this parameter.
   *
   * @return the parameter address
   */
  public String getAddress() {
    return address;
  }

  /**
   * Returns the unit of measure for this parameter.
   *
   * @return the unit string (may be empty if dimensionless)
   */
  public String getUnit() {
    return unit;
  }

  /**
   * Returns the lower bound of this parameter.
   *
   * @return the lower bound, or null if unbounded
   */
  public Double getLowerBound() {
    return lowerBound;
  }

  /**
   * Returns the upper bound of this parameter.
   *
   * @return the upper bound, or null if unbounded
   */
  public Double getUpperBound() {
    return upperBound;
  }

  /**
   * Returns the name of the unit operation this parameter actually affects.
   *
   * <p>
   * For an {@link Source#ADJUSTER}-sourced parameter this is the adjusted equipment's name, which may differ from the
   * parameter name. For an {@link Source#INPUT_VARIABLE} parameter this is the owning unit's name.
   * </p>
   *
   * @return the affected unit name, or null if not applicable
   */
  public String getTargetUnitName() {
    return targetUnitName;
  }

  /**
   * Returns the property this parameter actually drives.
   *
   * @return the affected property name, or null if not applicable
   */
  public String getTargetProperty() {
    return targetProperty;
  }

  /**
   * Returns where this parameter originates.
   *
   * @return the parameter source
   */
  public Source getSource() {
    return source;
  }

  /**
   * Serializes this parameter to a {@link JsonObject}.
   *
   * @return a JSON object describing this adjustable parameter
   */
  public JsonObject toJsonObject() {
    JsonObject obj = new JsonObject();
    obj.addProperty("name", name);
    obj.addProperty("address", address);
    obj.addProperty("unit", unit);
    if (lowerBound != null && !Double.isInfinite(lowerBound) && !Double.isNaN(lowerBound)) {
      obj.addProperty("lowerBound", lowerBound);
    } else {
      obj.add("lowerBound", null);
    }
    if (upperBound != null && !Double.isInfinite(upperBound) && !Double.isNaN(upperBound)) {
      obj.addProperty("upperBound", upperBound);
    } else {
      obj.add("upperBound", null);
    }
    obj.addProperty("targetUnitName", targetUnitName);
    obj.addProperty("targetProperty", targetProperty);
    obj.addProperty("source", source == null ? null : source.name());
    return obj;
  }

  /**
   * Serializes this parameter to a JSON string.
   *
   * @return a JSON string describing this adjustable parameter
   */
  public String toJson() {
    return toJsonObject().toString();
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return name + " [" + unit + "] -> " + (targetUnitName == null ? "(self)" : targetUnitName + "." + targetProperty);
  }
}
