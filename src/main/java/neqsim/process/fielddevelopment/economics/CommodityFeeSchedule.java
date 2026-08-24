package neqsim.process.fielddevelopment.economics;

import java.io.Serializable;

/**
 * Immutable schedule of non-transport commodity processing and service fees.
 *
 * <p>
 * Transport tariffs remain configured directly on {@link CashFlowEngine}. This class represents separate charges such
 * as gas processing, oil terminal service, and NGL handling so commercial scenarios can preserve that distinction in
 * annual cash-flow reporting.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class CommodityFeeSchedule implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final double gasProcessingFeeUsdPerSm3;
  private final double oilProcessingFeeUsdPerBbl;
  private final double nglProcessingFeeUsdPerBbl;
  private final double annualFixedFeeMusd;

  /**
   * Creates an immutable fee schedule from a builder.
   *
   * @param builder configured fee-schedule builder
   */
  private CommodityFeeSchedule(Builder builder) {
    this.gasProcessingFeeUsdPerSm3 = builder.gasProcessingFeeUsdPerSm3;
    this.oilProcessingFeeUsdPerBbl = builder.oilProcessingFeeUsdPerBbl;
    this.nglProcessingFeeUsdPerBbl = builder.nglProcessingFeeUsdPerBbl;
    this.annualFixedFeeMusd = builder.annualFixedFeeMusd;
  }

  /**
   * Creates a builder with all fees set to zero.
   *
   * @return new fee-schedule builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a schedule with no processing or service fees.
   *
   * @return immutable zero-fee schedule
   */
  public static CommodityFeeSchedule none() {
    return builder().build();
  }

  /**
   * Calculates the total fee for one year.
   *
   * @param oilBbl annual oil volume in barrels
   * @param gasSm3 annual gas volume in standard cubic metres
   * @param nglBbl annual NGL volume in barrels
   * @return total annual processing and service fee in million USD
   */
  public double calculateAnnualFeeMusd(double oilBbl, double gasSm3, double nglBbl) {
    return annualFixedFeeMusd + oilBbl * oilProcessingFeeUsdPerBbl / 1.0e6 + gasSm3 * gasProcessingFeeUsdPerSm3 / 1.0e6
        + nglBbl * nglProcessingFeeUsdPerBbl / 1.0e6;
  }

  /**
   * Gets the gas processing fee.
   *
   * @return gas processing fee in USD per Sm3
   */
  public double getGasProcessingFeeUsdPerSm3() {
    return gasProcessingFeeUsdPerSm3;
  }

  /**
   * Gets the oil processing or terminal fee.
   *
   * @return oil processing fee in USD per barrel
   */
  public double getOilProcessingFeeUsdPerBbl() {
    return oilProcessingFeeUsdPerBbl;
  }

  /**
   * Gets the NGL processing or handling fee.
   *
   * @return NGL processing fee in USD per barrel
   */
  public double getNglProcessingFeeUsdPerBbl() {
    return nglProcessingFeeUsdPerBbl;
  }

  /**
   * Gets the fixed annual service fee.
   *
   * @return fixed annual fee in million USD
   */
  public double getAnnualFixedFeeMusd() {
    return annualFixedFeeMusd;
  }

  /** Builder for an immutable {@link CommodityFeeSchedule}. */
  public static final class Builder {
    private double gasProcessingFeeUsdPerSm3;
    private double oilProcessingFeeUsdPerBbl;
    private double nglProcessingFeeUsdPerBbl;
    private double annualFixedFeeMusd;

    /** Creates a builder with zero-valued fees. */
    private Builder() {
    }

    /**
     * Sets the gas processing fee.
     *
     * @param feeUsdPerSm3 fee in USD per Sm3, greater than or equal to zero
     * @return this builder
     */
    public Builder gasProcessingFee(double feeUsdPerSm3) {
      this.gasProcessingFeeUsdPerSm3 = validateFee(feeUsdPerSm3, "Gas processing fee");
      return this;
    }

    /**
     * Sets the oil processing or terminal fee.
     *
     * @param feeUsdPerBbl fee in USD per barrel, greater than or equal to zero
     * @return this builder
     */
    public Builder oilProcessingFee(double feeUsdPerBbl) {
      this.oilProcessingFeeUsdPerBbl = validateFee(feeUsdPerBbl, "Oil processing fee");
      return this;
    }

    /**
     * Sets the NGL processing or handling fee.
     *
     * @param feeUsdPerBbl fee in USD per barrel, greater than or equal to zero
     * @return this builder
     */
    public Builder nglProcessingFee(double feeUsdPerBbl) {
      this.nglProcessingFeeUsdPerBbl = validateFee(feeUsdPerBbl, "NGL processing fee");
      return this;
    }

    /**
     * Sets a fixed annual service fee.
     *
     * @param feeMusd fee in million USD per represented project year, greater than or equal to zero
     * @return this builder
     */
    public Builder annualFixedFee(double feeMusd) {
      this.annualFixedFeeMusd = validateFee(feeMusd, "Annual fixed fee");
      return this;
    }

    /**
     * Builds the immutable fee schedule.
     *
     * @return configured fee schedule
     */
    public CommodityFeeSchedule build() {
      return new CommodityFeeSchedule(this);
    }

    /**
     * Validates a fee value.
     *
     * @param value fee value to validate
     * @param name human-readable fee name
     * @return the validated value
     * @throws IllegalArgumentException if the value is negative or non-finite
     */
    private static double validateFee(double value, String name) {
      if (value < 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
        throw new IllegalArgumentException(name + " must be finite and non-negative");
      }
      return value;
    }
  }
}