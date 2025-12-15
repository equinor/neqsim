package neqsim.process.measurementdevice.vfm;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Result from a Virtual Flow Meter calculation.
 *
 * <p>
 * Contains calculated flow rates for oil, gas, and water phases along with uncertainty bounds and
 * quality indicators.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class VFMResult implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final Instant timestamp;
  private final double oilFlowRate;
  private final double gasFlowRate;
  private final double waterFlowRate;
  private final double totalLiquidFlowRate;
  private final double gasOilRatio;
  private final double waterCut;

  private final UncertaintyBounds oilUncertainty;
  private final UncertaintyBounds gasUncertainty;
  private final UncertaintyBounds waterUncertainty;

  private final Quality quality;
  private final Map<String, Double> additionalProperties;

  /**
   * Quality indicator for VFM result.
   */
  public enum Quality {
    /** High confidence result based on recent calibration. */
    HIGH,
    /** Normal confidence result. */
    NORMAL,
    /** Low confidence, model may need recalibration. */
    LOW,
    /** Result is extrapolated outside calibration range. */
    EXTRAPOLATED,
    /** Result should not be used. */
    INVALID
  }

  /**
   * Builder for VFMResult.
   */
  public static class Builder {
    private Instant timestamp = Instant.now();
    private double oilFlowRate;
    private double gasFlowRate;
    private double waterFlowRate;
    private UncertaintyBounds oilUncertainty;
    private UncertaintyBounds gasUncertainty;
    private UncertaintyBounds waterUncertainty;
    private Quality quality = Quality.NORMAL;
    private Map<String, Double> additionalProperties = new HashMap<>();

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder oilFlowRate(double rate, String unit) {
      this.oilFlowRate = rate;
      return this;
    }

    public Builder oilFlowRate(double rate, double stdDev, String unit) {
      this.oilFlowRate = rate;
      this.oilUncertainty = new UncertaintyBounds(rate, stdDev, unit);
      return this;
    }

    public Builder gasFlowRate(double rate, String unit) {
      this.gasFlowRate = rate;
      return this;
    }

    public Builder gasFlowRate(double rate, double stdDev, String unit) {
      this.gasFlowRate = rate;
      this.gasUncertainty = new UncertaintyBounds(rate, stdDev, unit);
      return this;
    }

    public Builder waterFlowRate(double rate, String unit) {
      this.waterFlowRate = rate;
      return this;
    }

    public Builder waterFlowRate(double rate, double stdDev, String unit) {
      this.waterFlowRate = rate;
      this.waterUncertainty = new UncertaintyBounds(rate, stdDev, unit);
      return this;
    }

    public Builder quality(Quality quality) {
      this.quality = quality;
      return this;
    }

    public Builder addProperty(String name, double value) {
      this.additionalProperties.put(name, value);
      return this;
    }

    public VFMResult build() {
      return new VFMResult(this);
    }
  }

  private VFMResult(Builder builder) {
    this.timestamp = builder.timestamp;
    this.oilFlowRate = builder.oilFlowRate;
    this.gasFlowRate = builder.gasFlowRate;
    this.waterFlowRate = builder.waterFlowRate;
    this.totalLiquidFlowRate = builder.oilFlowRate + builder.waterFlowRate;
    this.gasOilRatio = (builder.oilFlowRate > 0) ? builder.gasFlowRate / builder.oilFlowRate : 0;
    this.waterCut =
        (totalLiquidFlowRate > 0) ? builder.waterFlowRate / totalLiquidFlowRate * 100.0 : 0;
    this.oilUncertainty = builder.oilUncertainty;
    this.gasUncertainty = builder.gasUncertainty;
    this.waterUncertainty = builder.waterUncertainty;
    this.quality = builder.quality;
    this.additionalProperties = new HashMap<>(builder.additionalProperties);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the calculation timestamp.
   *
   * @return the timestamp
   */
  public Instant getTimestamp() {
    return timestamp;
  }

  /**
   * Gets the oil flow rate.
   *
   * @return oil flow rate in Sm3/d
   */
  public double getOilFlowRate() {
    return oilFlowRate;
  }

  /**
   * Gets the gas flow rate.
   *
   * @return gas flow rate in Sm3/d
   */
  public double getGasFlowRate() {
    return gasFlowRate;
  }

  /**
   * Gets the water flow rate.
   *
   * @return water flow rate in Sm3/d
   */
  public double getWaterFlowRate() {
    return waterFlowRate;
  }

  /**
   * Gets the total liquid flow rate (oil + water).
   *
   * @return total liquid flow rate in Sm3/d
   */
  public double getTotalLiquidFlowRate() {
    return totalLiquidFlowRate;
  }

  /**
   * Gets the gas-oil ratio.
   *
   * @return GOR in Sm3/Sm3
   */
  public double getGasOilRatio() {
    return gasOilRatio;
  }

  /**
   * Gets the water cut percentage.
   *
   * @return water cut in percent
   */
  public double getWaterCut() {
    return waterCut;
  }

  /**
   * Gets uncertainty bounds for oil flow rate.
   *
   * @return oil uncertainty or null if not calculated
   */
  public UncertaintyBounds getOilUncertainty() {
    return oilUncertainty;
  }

  /**
   * Gets uncertainty bounds for gas flow rate.
   *
   * @return gas uncertainty or null if not calculated
   */
  public UncertaintyBounds getGasUncertainty() {
    return gasUncertainty;
  }

  /**
   * Gets uncertainty bounds for water flow rate.
   *
   * @return water uncertainty or null if not calculated
   */
  public UncertaintyBounds getWaterUncertainty() {
    return waterUncertainty;
  }

  /**
   * Gets the result quality indicator.
   *
   * @return the quality
   */
  public Quality getQuality() {
    return quality;
  }

  /**
   * Gets an additional property by name.
   *
   * @param name the property name
   * @return the property value or null
   */
  public Double getProperty(String name) {
    return additionalProperties.get(name);
  }

  /**
   * Gets all additional properties.
   *
   * @return map of property names to values
   */
  public Map<String, Double> getAdditionalProperties() {
    return new HashMap<>(additionalProperties);
  }

  /**
   * Checks if this result is usable for production allocation.
   *
   * @return true if quality is HIGH or NORMAL
   */
  public boolean isUsable() {
    return quality == Quality.HIGH || quality == Quality.NORMAL;
  }

  @Override
  public String toString() {
    return String.format(
        "VFMResult{oil=%.1f, gas=%.1f, water=%.1f Sm3/d, GOR=%.1f, WC=%.1f%%, quality=%s}",
        oilFlowRate, gasFlowRate, waterFlowRate, gasOilRatio, waterCut, quality);
  }
}
