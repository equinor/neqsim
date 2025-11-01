package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable mechanical design limits for a unit of equipment.
 */
public final class DesignLimitData implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Empty data set with undefined limits. */
  public static final DesignLimitData EMPTY = DesignLimitData.builder().build();

  private final double maxPressure;
  private final double minPressure;
  private final double maxTemperature;
  private final double minTemperature;
  private final double corrosionAllowance;
  private final double jointEfficiency;

  private DesignLimitData(Builder builder) {
    this.maxPressure = builder.maxPressure;
    this.minPressure = builder.minPressure;
    this.maxTemperature = builder.maxTemperature;
    this.minTemperature = builder.minTemperature;
    this.corrosionAllowance = builder.corrosionAllowance;
    this.jointEfficiency = builder.jointEfficiency;
  }

  public static Builder builder() {
    return new Builder();
  }

  public double getMaxPressure() {
    return maxPressure;
  }

  public double getMinPressure() {
    return minPressure;
  }

  public double getMaxTemperature() {
    return maxTemperature;
  }

  public double getMinTemperature() {
    return minTemperature;
  }

  public double getCorrosionAllowance() {
    return corrosionAllowance;
  }

  public double getJointEfficiency() {
    return jointEfficiency;
  }

  /** Builder for {@link DesignLimitData}. */
  public static final class Builder {
    private double maxPressure = Double.NaN;
    private double minPressure = Double.NaN;
    private double maxTemperature = Double.NaN;
    private double minTemperature = Double.NaN;
    private double corrosionAllowance = Double.NaN;
    private double jointEfficiency = Double.NaN;

    private Builder() {}

    public Builder maxPressure(double value) {
      this.maxPressure = value;
      return this;
    }

    public Builder minPressure(double value) {
      this.minPressure = value;
      return this;
    }

    public Builder maxTemperature(double value) {
      this.maxTemperature = value;
      return this;
    }

    public Builder minTemperature(double value) {
      this.minTemperature = value;
      return this;
    }

    public Builder corrosionAllowance(double value) {
      this.corrosionAllowance = value;
      return this;
    }

    public Builder jointEfficiency(double value) {
      this.jointEfficiency = value;
      return this;
    }

    public DesignLimitData build() {
      return new DesignLimitData(this);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxPressure, minPressure, maxTemperature, minTemperature, corrosionAllowance,
        jointEfficiency);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DesignLimitData)) {
      return false;
    }
    DesignLimitData other = (DesignLimitData) obj;
    return Double.doubleToLongBits(maxPressure) == Double.doubleToLongBits(other.maxPressure)
        && Double.doubleToLongBits(minPressure) == Double.doubleToLongBits(other.minPressure)
        && Double.doubleToLongBits(maxTemperature) == Double.doubleToLongBits(other.maxTemperature)
        && Double.doubleToLongBits(minTemperature) == Double.doubleToLongBits(other.minTemperature)
        && Double.doubleToLongBits(corrosionAllowance) == Double.doubleToLongBits(other.corrosionAllowance)
        && Double.doubleToLongBits(jointEfficiency) == Double.doubleToLongBits(other.jointEfficiency);
  }

  @Override
  public String toString() {
    return "DesignLimitData{" + "maxPressure=" + maxPressure + ", minPressure=" + minPressure
        + ", maxTemperature=" + maxTemperature + ", minTemperature=" + minTemperature
        + ", corrosionAllowance=" + corrosionAllowance + ", jointEfficiency=" + jointEfficiency
        + '}';
  }
}
