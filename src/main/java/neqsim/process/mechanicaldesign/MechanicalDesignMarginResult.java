package neqsim.process.mechanicaldesign;

import java.io.Serializable;
import java.util.Objects;

/**
 * Result object describing operating margins relative to design limits.
 */
public final class MechanicalDesignMarginResult implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Empty result with undefined margins. */
  public static final MechanicalDesignMarginResult EMPTY = new MechanicalDesignMarginResult(
      Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

  private final double maxPressureMargin;
  private final double minPressureMargin;
  private final double maxTemperatureMargin;
  private final double minTemperatureMargin;
  private final double corrosionAllowanceMargin;
  private final double jointEfficiencyMargin;

  public MechanicalDesignMarginResult(double maxPressureMargin, double minPressureMargin,
      double maxTemperatureMargin, double minTemperatureMargin, double corrosionAllowanceMargin,
      double jointEfficiencyMargin) {
    this.maxPressureMargin = maxPressureMargin;
    this.minPressureMargin = minPressureMargin;
    this.maxTemperatureMargin = maxTemperatureMargin;
    this.minTemperatureMargin = minTemperatureMargin;
    this.corrosionAllowanceMargin = corrosionAllowanceMargin;
    this.jointEfficiencyMargin = jointEfficiencyMargin;
  }

  public double getMaxPressureMargin() {
    return maxPressureMargin;
  }

  public double getMinPressureMargin() {
    return minPressureMargin;
  }

  public double getMaxTemperatureMargin() {
    return maxTemperatureMargin;
  }

  public double getMinTemperatureMargin() {
    return minTemperatureMargin;
  }

  public double getCorrosionAllowanceMargin() {
    return corrosionAllowanceMargin;
  }

  public double getJointEfficiencyMargin() {
    return jointEfficiencyMargin;
  }

  /**
   * @return true if all evaluated margins are non-negative or undefined.
   */
  public boolean isWithinDesignEnvelope() {
    return isNonNegativeOrNaN(maxPressureMargin) && isNonNegativeOrNaN(minPressureMargin)
        && isNonNegativeOrNaN(maxTemperatureMargin) && isNonNegativeOrNaN(minTemperatureMargin)
        && isNonNegativeOrNaN(corrosionAllowanceMargin)
        && isNonNegativeOrNaN(jointEfficiencyMargin);
  }

  private boolean isNonNegativeOrNaN(double value) {
    return Double.isNaN(value) || value >= 0.0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxPressureMargin, minPressureMargin, maxTemperatureMargin,
        minTemperatureMargin, corrosionAllowanceMargin, jointEfficiencyMargin);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof MechanicalDesignMarginResult)) {
      return false;
    }
    MechanicalDesignMarginResult other = (MechanicalDesignMarginResult) obj;
    return Double.doubleToLongBits(maxPressureMargin) == Double.doubleToLongBits(other.maxPressureMargin)
        && Double.doubleToLongBits(minPressureMargin) == Double.doubleToLongBits(other.minPressureMargin)
        && Double.doubleToLongBits(maxTemperatureMargin) == Double.doubleToLongBits(other.maxTemperatureMargin)
        && Double.doubleToLongBits(minTemperatureMargin) == Double.doubleToLongBits(other.minTemperatureMargin)
        && Double.doubleToLongBits(corrosionAllowanceMargin) == Double.doubleToLongBits(other.corrosionAllowanceMargin)
        && Double.doubleToLongBits(jointEfficiencyMargin) == Double.doubleToLongBits(other.jointEfficiencyMargin);
  }

  @Override
  public String toString() {
    return "MechanicalDesignMarginResult{" + "maxPressureMargin=" + maxPressureMargin
        + ", minPressureMargin=" + minPressureMargin + ", maxTemperatureMargin="
        + maxTemperatureMargin + ", minTemperatureMargin=" + minTemperatureMargin
        + ", corrosionAllowanceMargin=" + corrosionAllowanceMargin + ", jointEfficiencyMargin="
        + jointEfficiencyMargin + '}';
  }
}
