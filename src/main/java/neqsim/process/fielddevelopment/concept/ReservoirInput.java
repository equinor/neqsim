package neqsim.process.fielddevelopment.concept;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reservoir fluid and conditions input for field concept definition.
 *
 * <p>
 * Captures the key reservoir characteristics needed for concept screening:
 * <ul>
 * <li>Fluid type (lean gas, rich gas, volatile oil, black oil, etc.)</li>
 * <li>GOR - Gas/Oil ratio</li>
 * <li>CO2 and H2S content</li>
 * <li>Water cut and salinity</li>
 * <li>Reservoir pressure and temperature</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public final class ReservoirInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Common fluid type classifications. */
  public enum FluidType {
    /** Lean gas - primarily methane, low liquids. */
    LEAN_GAS,
    /** Rich gas - significant NGL content. */
    RICH_GAS,
    /** Gas condensate - high shrinkage, retrograde behavior. */
    GAS_CONDENSATE,
    /** Volatile oil - high GOR, near-critical. */
    VOLATILE_OIL,
    /** Black oil - moderate GOR, undersaturated. */
    BLACK_OIL,
    /** Heavy oil - low API, high viscosity. */
    HEAVY_OIL,
    /** Custom - user-defined composition. */
    CUSTOM
  }

  private final FluidType fluidType;
  private final double gor; // Sm3/Sm3 for gas, Sm3/m3 for oil
  private final String gorUnit;
  private final double co2Percent;
  private final double h2sPercent;
  private final double n2Percent;
  private final double waterCut; // fraction 0-1
  private final double waterSalinity; // ppm TDS
  private final double reservoirPressure; // bara
  private final double reservoirTemperature; // degC
  private final double apiGravity; // API for oil
  private final double gasGravity; // specific gravity for gas
  private final Map<String, Double> customComposition;

  private ReservoirInput(Builder builder) {
    this.fluidType = builder.fluidType;
    this.gor = builder.gor;
    this.gorUnit = builder.gorUnit;
    this.co2Percent = builder.co2Percent;
    this.h2sPercent = builder.h2sPercent;
    this.n2Percent = builder.n2Percent;
    this.waterCut = builder.waterCut;
    this.waterSalinity = builder.waterSalinity;
    this.reservoirPressure = builder.reservoirPressure;
    this.reservoirTemperature = builder.reservoirTemperature;
    this.apiGravity = builder.apiGravity;
    this.gasGravity = builder.gasGravity;
    this.customComposition = new LinkedHashMap<>(builder.customComposition);
  }

  /**
   * Creates a new builder for ReservoirInput.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a builder initialized with typical lean gas properties.
   *
   * @return builder with lean gas defaults
   */
  public static Builder leanGas() {
    return new Builder().fluidType(FluidType.LEAN_GAS).gasGravity(0.60).co2Percent(0.5)
        .h2sPercent(0.0);
  }

  /**
   * Creates a builder initialized with typical rich gas properties.
   *
   * @return builder with rich gas defaults
   */
  public static Builder richGas() {
    return new Builder().fluidType(FluidType.RICH_GAS).gasGravity(0.75).co2Percent(1.0)
        .h2sPercent(0.0);
  }

  /**
   * Creates a builder initialized with typical gas condensate properties.
   *
   * @return builder with gas condensate defaults
   */
  public static Builder gasCondensate() {
    return new Builder().fluidType(FluidType.GAS_CONDENSATE).gor(5000, "Sm3/Sm3").gasGravity(0.80)
        .apiGravity(50.0).co2Percent(2.0);
  }

  /**
   * Creates a builder initialized with typical black oil properties.
   *
   * @return builder with black oil defaults
   */
  public static Builder blackOil() {
    return new Builder().fluidType(FluidType.BLACK_OIL).gor(100, "Sm3/m3").apiGravity(30.0)
        .co2Percent(0.5);
  }

  // Getters

  public FluidType getFluidType() {
    return fluidType;
  }

  public double getGor() {
    return gor;
  }

  public String getGorUnit() {
    return gorUnit;
  }

  public double getCo2Percent() {
    return co2Percent;
  }

  public double getH2sPercent() {
    return h2sPercent;
  }

  public double getN2Percent() {
    return n2Percent;
  }

  public double getWaterCut() {
    return waterCut;
  }

  public double getWaterSalinity() {
    return waterSalinity;
  }

  public double getReservoirPressure() {
    return reservoirPressure;
  }

  public double getReservoirTemperature() {
    return reservoirTemperature;
  }

  public double getApiGravity() {
    return apiGravity;
  }

  public double getGasGravity() {
    return gasGravity;
  }

  public Map<String, Double> getCustomComposition() {
    return new LinkedHashMap<>(customComposition);
  }

  /**
   * Checks if this is a sour fluid (H2S > 0.5%).
   *
   * @return true if sour
   */
  public boolean isSour() {
    return h2sPercent > 0.5;
  }

  /**
   * Checks if this is a high-CO2 fluid (CO2 > 5%).
   *
   * @return true if high CO2
   */
  public boolean isHighCO2() {
    return co2Percent > 5.0;
  }

  /**
   * Checks if water handling is significant (water cut > 10%).
   *
   * @return true if significant water
   */
  public boolean hasSignificantWater() {
    return waterCut > 0.10;
  }

  /**
   * Gets the water cut as a percentage (0-100).
   *
   * @return water cut percent
   */
  public double getWaterCutPercent() {
    return waterCut * 100.0;
  }

  /**
   * Gets the H2S content as a percentage.
   *
   * @return H2S percent
   */
  public double getH2SPercent() {
    return h2sPercent;
  }

  /**
   * Checks if this fluid has liquid production (oil or condensate).
   *
   * @return true if liquid production expected
   */
  public boolean hasLiquidProduction() {
    return fluidType == FluidType.BLACK_OIL || fluidType == FluidType.HEAVY_OIL
        || fluidType == FluidType.VOLATILE_OIL || fluidType == FluidType.GAS_CONDENSATE;
  }

  @Override
  public String toString() {
    return String.format("ReservoirInput[type=%s, GOR=%.0f %s, CO2=%.1f%%, H2S=%.2f%%, WC=%.0f%%]",
        fluidType, gor, gorUnit, co2Percent, h2sPercent, waterCut * 100);
  }

  /**
   * Builder for ReservoirInput.
   */
  public static final class Builder {
    private FluidType fluidType = FluidType.RICH_GAS;
    private double gor = 1000.0;
    private String gorUnit = "Sm3/Sm3";
    private double co2Percent = 1.0;
    private double h2sPercent = 0.0;
    private double n2Percent = 0.5;
    private double waterCut = 0.0;
    private double waterSalinity = 35000.0;
    private double reservoirPressure = 250.0;
    private double reservoirTemperature = 80.0;
    private double apiGravity = 45.0;
    private double gasGravity = 0.65;
    private final Map<String, Double> customComposition = new LinkedHashMap<>();

    private Builder() {}

    public Builder fluidType(FluidType type) {
      this.fluidType = Objects.requireNonNull(type);
      return this;
    }

    public Builder gor(double gor, String unit) {
      this.gor = gor;
      this.gorUnit = unit;
      return this;
    }

    public Builder gor(double gor) {
      this.gor = gor;
      return this;
    }

    public Builder co2Percent(double percent) {
      this.co2Percent = percent;
      return this;
    }

    public Builder h2sPercent(double percent) {
      this.h2sPercent = percent;
      return this;
    }

    public Builder n2Percent(double percent) {
      this.n2Percent = percent;
      return this;
    }

    public Builder waterCut(double fraction) {
      if (fraction < 0 || fraction > 1) {
        throw new IllegalArgumentException("Water cut must be between 0 and 1");
      }
      this.waterCut = fraction;
      return this;
    }

    public Builder waterSalinity(double ppm) {
      this.waterSalinity = ppm;
      return this;
    }

    public Builder reservoirPressure(double bara) {
      this.reservoirPressure = bara;
      return this;
    }

    public Builder reservoirTemperature(double degC) {
      this.reservoirTemperature = degC;
      return this;
    }

    public Builder apiGravity(double api) {
      this.apiGravity = api;
      return this;
    }

    public Builder gasGravity(double sg) {
      this.gasGravity = sg;
      return this;
    }

    public Builder addComponent(String name, double moleFraction) {
      this.customComposition.put(name, moleFraction);
      this.fluidType = FluidType.CUSTOM;
      return this;
    }

    public ReservoirInput build() {
      return new ReservoirInput(this);
    }
  }
}
