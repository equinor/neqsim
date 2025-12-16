package neqsim.process.fielddevelopment.concept;

import java.io.Serializable;
import java.util.Objects;

/**
 * Well configuration input for field concept definition.
 *
 * <p>
 * Captures the key well-related parameters needed for concept screening:
 * <ul>
 * <li>Number of wells</li>
 * <li>Tubing head pressure (THP)</li>
 * <li>Production rates per well</li>
 * <li>Well type (producer, injector)</li>
 * <li>Artificial lift requirements</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 */
public final class WellsInput implements Serializable {
  private static final long serialVersionUID = 1000L;

  /** Well type classification. */
  public enum WellType {
    /** Natural flow producer. */
    NATURAL_FLOW,
    /** Gas lift producer. */
    GAS_LIFT,
    /** ESP producer. */
    ESP,
    /** Water injector. */
    WATER_INJECTOR,
    /** Gas injector. */
    GAS_INJECTOR
  }

  /** Completion type. */
  public enum CompletionType {
    /** Subsea tree. */
    SUBSEA,
    /** Platform wellhead. */
    PLATFORM,
    /** Onshore. */
    ONSHORE
  }

  private final int producerCount;
  private final int injectorCount;
  private final WellType producerType;
  private final CompletionType completionType;
  private final double thp; // bara
  private final double ratePerWell;
  private final String rateUnit;
  private final double shutInPressure; // bara
  private final double productivityIndex; // Sm3/d/bar
  private final double waterInjectionRate;
  private final String waterInjectionUnit;
  private final double gasLiftRate;
  private final String gasLiftUnit;

  private WellsInput(Builder builder) {
    this.producerCount = builder.producerCount;
    this.injectorCount = builder.injectorCount;
    this.producerType = builder.producerType;
    this.completionType = builder.completionType;
    this.thp = builder.thp;
    this.ratePerWell = builder.ratePerWell;
    this.rateUnit = builder.rateUnit;
    this.shutInPressure = builder.shutInPressure;
    this.productivityIndex = builder.productivityIndex;
    this.waterInjectionRate = builder.waterInjectionRate;
    this.waterInjectionUnit = builder.waterInjectionUnit;
    this.gasLiftRate = builder.gasLiftRate;
    this.gasLiftUnit = builder.gasLiftUnit;
  }

  /**
   * Creates a new builder for WellsInput.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  // Getters

  public int getProducerCount() {
    return producerCount;
  }

  public int getInjectorCount() {
    return injectorCount;
  }

  public int getTotalWellCount() {
    return producerCount + injectorCount;
  }

  public WellType getProducerType() {
    return producerType;
  }

  public CompletionType getCompletionType() {
    return completionType;
  }

  public double getThp() {
    return thp;
  }

  public double getRatePerWell() {
    return ratePerWell;
  }

  public String getRateUnit() {
    return rateUnit;
  }

  public double getTotalRate() {
    return ratePerWell * producerCount;
  }

  public double getShutInPressure() {
    return shutInPressure;
  }

  public double getProductivityIndex() {
    return productivityIndex;
  }

  public double getWaterInjectionRate() {
    return waterInjectionRate;
  }

  public String getWaterInjectionUnit() {
    return waterInjectionUnit;
  }

  public double getGasLiftRate() {
    return gasLiftRate;
  }

  public String getGasLiftUnit() {
    return gasLiftUnit;
  }

  /**
   * Checks if artificial lift is required.
   *
   * @return true if artificial lift needed
   */
  public boolean needsArtificialLift() {
    return producerType == WellType.ESP || producerType == WellType.GAS_LIFT;
  }

  /**
   * Gets the tubing head pressure in bara.
   *
   * @return THP in bara
   */
  public double getTubeheadPressure() {
    return thp;
  }

  /**
   * Gets the rate per well in Sm3/d (converts from other units if needed).
   *
   * @return rate per well in Sm3/d
   */
  public double getRatePerWellSm3d() {
    if ("Sm3/d".equals(rateUnit) || "Sm3/day".equals(rateUnit)) {
      return ratePerWell;
    } else if ("MSm3/d".equals(rateUnit)) {
      return ratePerWell * 1e6;
    } else if ("bbl/d".equals(rateUnit) || "bopd".equals(rateUnit)) {
      return ratePerWell * 0.159; // Convert barrels to m3
    }
    return ratePerWell;
  }

  /**
   * Checks if this is a subsea development.
   *
   * @return true if subsea
   */
  public boolean isSubsea() {
    return completionType == CompletionType.SUBSEA;
  }

  @Override
  public String toString() {
    return String.format("WellsInput[producers=%d, injectors=%d, THP=%.0f bara, rate=%.0f %s/well]",
        producerCount, injectorCount, thp, ratePerWell, rateUnit);
  }

  /**
   * Builder for WellsInput.
   */
  public static final class Builder {
    private int producerCount = 4;
    private int injectorCount = 0;
    private WellType producerType = WellType.NATURAL_FLOW;
    private CompletionType completionType = CompletionType.SUBSEA;
    private double thp = 100.0;
    private double ratePerWell = 1.0e6;
    private String rateUnit = "Sm3/d";
    private double shutInPressure = 250.0;
    private double productivityIndex = 10000.0;
    private double waterInjectionRate = 0.0;
    private String waterInjectionUnit = "m3/d";
    private double gasLiftRate = 0.0;
    private String gasLiftUnit = "MSm3/d";

    private Builder() {}

    public Builder producerCount(int count) {
      if (count < 0) {
        throw new IllegalArgumentException("Producer count cannot be negative");
      }
      this.producerCount = count;
      return this;
    }

    public Builder injectorCount(int count) {
      if (count < 0) {
        throw new IllegalArgumentException("Injector count cannot be negative");
      }
      this.injectorCount = count;
      return this;
    }

    public Builder producerType(WellType type) {
      this.producerType = Objects.requireNonNull(type);
      return this;
    }

    public Builder completionType(CompletionType type) {
      this.completionType = Objects.requireNonNull(type);
      return this;
    }

    public Builder thp(double bara) {
      this.thp = bara;
      return this;
    }

    public Builder tubeheadPressure(double bara) {
      this.thp = bara;
      return this;
    }

    public Builder ratePerWell(double rate, String unit) {
      this.ratePerWell = rate;
      this.rateUnit = unit;
      return this;
    }

    public Builder shutInPressure(double bara) {
      this.shutInPressure = bara;
      return this;
    }

    public Builder productivityIndex(double pi) {
      this.productivityIndex = pi;
      return this;
    }

    public Builder waterInjection(double rate, String unit) {
      this.waterInjectionRate = rate;
      this.waterInjectionUnit = unit;
      return this;
    }

    public Builder gasLift(double rate, String unit) {
      this.gasLiftRate = rate;
      this.gasLiftUnit = unit;
      this.producerType = WellType.GAS_LIFT;
      return this;
    }

    public WellsInput build() {
      return new WellsInput(this);
    }
  }
}
