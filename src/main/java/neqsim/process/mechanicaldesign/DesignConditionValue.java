package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.Objects;
import neqsim.util.unit.LengthUnit;
import neqsim.util.unit.PressureUnit;
import neqsim.util.unit.TemperatureUnit;

/** Immutable, typed engineering value for one declared equipment design condition. */
public final class DesignConditionValue implements Serializable {
  private static final long serialVersionUID = 1000L;

  private enum Dimension {
    PRESSURE,
    TEMPERATURE,
    LENGTH
  }

  /** Supported declared design-condition quantities and their canonical storage units. */
  public enum Type {
    /** Maximum allowable working pressure, stored in bara. */
    DESIGN_PRESSURE(Dimension.PRESSURE, "bara"),
    /** Maximum design temperature, stored in degrees Celsius. */
    MAX_DESIGN_TEMPERATURE(Dimension.TEMPERATURE, "C"),
    /** Minimum design metal temperature, stored in degrees Celsius. */
    MIN_DESIGN_TEMPERATURE(Dimension.TEMPERATURE, "C"),
    /** Protecting relief-device set pressure, stored in bara. */
    RELIEF_SET_PRESSURE(Dimension.PRESSURE, "bara"),
    /** Corrosion allowance, stored in millimetres. */
    CORROSION_ALLOWANCE(Dimension.LENGTH, "mm");

    private final Dimension dimension;
    private final String canonicalUnit;

    Type(Dimension dimension, String canonicalUnit) {
      this.dimension = dimension;
      this.canonicalUnit = canonicalUnit;
    }

    /** @return canonical storage unit */
    public String getCanonicalUnit() {
      return canonicalUnit;
    }
  }

  private final Type type;
  private final double canonicalValue;

  private DesignConditionValue(Type type, double canonicalValue) {
    this.type = type;
    this.canonicalValue = canonicalValue;
  }

  /**
   * Create a typed condition and convert it to the quantity's canonical unit.
   *
   * @param type declared condition type
   * @param value numeric value
   * @param unit engineering unit
   * @return immutable typed value
   */
  public static DesignConditionValue of(Type type, double value, String unit) {
    if (type == null) {
      throw new IllegalArgumentException("type cannot be null");
    }
    if (unit == null || unit.trim().isEmpty()) {
      throw new IllegalArgumentException("unit cannot be blank");
    }
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      throw new IllegalArgumentException("value must be finite");
    }

    double converted = convert(value, unit.trim(), type.getCanonicalUnit(), type.dimension);
    validatePhysicalRange(type, converted);
    return new DesignConditionValue(type, converted);
  }

  /** @return declared condition type */
  public Type getType() {
    return type;
  }

  /** @return value in the canonical unit returned by {@link Type#getCanonicalUnit()} */
  public double getCanonicalValue() {
    return canonicalValue;
  }

  /** @return canonical storage unit */
  public String getCanonicalUnit() {
    return type.getCanonicalUnit();
  }

  /**
   * Convert this condition to another compatible engineering unit.
   *
   * @param unit requested unit
   * @return converted value
   */
  public double getValue(String unit) {
    if (unit == null || unit.trim().isEmpty()) {
      throw new IllegalArgumentException("unit cannot be blank");
    }
    return convert(canonicalValue, type.getCanonicalUnit(), unit.trim(), type.dimension);
  }

  private static double convert(double value, String fromUnit, String toUnit, Dimension dimension) {
    switch (dimension) {
    case PRESSURE:
      return new PressureUnit(value, fromUnit).getValue(toUnit);
    case TEMPERATURE:
      return new TemperatureUnit(value, fromUnit).getValue(toUnit);
    case LENGTH:
      return new LengthUnit(value, fromUnit).getValue(toUnit);
    default:
      throw new IllegalStateException("Unsupported condition dimension " + dimension);
    }
  }

  private static void validatePhysicalRange(Type type, double canonicalValue) {
    if ((type == Type.DESIGN_PRESSURE || type == Type.RELIEF_SET_PRESSURE) && canonicalValue < 0.0) {
      throw new IllegalArgumentException(type + " cannot be below absolute vacuum");
    }
    if (type == Type.CORROSION_ALLOWANCE && canonicalValue < 0.0) {
      throw new IllegalArgumentException("CORROSION_ALLOWANCE cannot be negative");
    }
    if ((type == Type.MAX_DESIGN_TEMPERATURE || type == Type.MIN_DESIGN_TEMPERATURE)
        && new TemperatureUnit(canonicalValue, "C").getValue("K") < 0.0) {
      throw new IllegalArgumentException(type + " cannot be below absolute zero");
    }
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(type, Double.valueOf(canonicalValue));
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DesignConditionValue)) {
      return false;
    }
    DesignConditionValue other = (DesignConditionValue) obj;
    return type == other.type
        && Double.doubleToLongBits(canonicalValue) == Double.doubleToLongBits(other.canonicalValue);
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return type + "=" + canonicalValue + " " + type.getCanonicalUnit();
  }
}
