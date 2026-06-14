package neqsim.process.optimization.valuechain;

import java.io.Serializable;

/**
 * Economic and carbon parameters shared by the value-chain optimization classes.
 *
 * <p>
 * Collects the prices, costs and financial assumptions needed to turn a converged NeqSim flowsheet
 * into an economic objective: gas and oil sales prices, the cost of the energy consumed by rotating
 * equipment, a carbon price/tax applied to the associated CO<sub>2</sub>, the carbon intensity of
 * that energy, and the annual discount rate used for net-present-value calculations. All monetary
 * values are expressed in the configured {@link #getCurrency() currency} (default {@code "NOK"}).
 * </p>
 *
 * <p>
 * The class is a plain, serializable parameter holder with a fluent API so it can be built inline:
 * </p>
 *
 * <pre>
 * EconomicParameters econ = new EconomicParameters().setGasPrice(3.0).setPowerCost(0.6)
 *     .setCo2Tax(1200.0).setCo2IntensityTonnePerMWh(0.20).setDiscountRate(0.08);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class EconomicParameters implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Sales price of gas in currency per standard cubic metre. */
  private double gasPriceNokPerSm3 = 3.0;

  /** Sales price of oil/condensate in currency per standard cubic metre. */
  private double oilPriceNokPerSm3 = 4500.0;

  /** Cost of consumed electrical/shaft energy in currency per kWh. */
  private double powerCostNokPerKWh = 0.6;

  /** Carbon price/tax in currency per tonne of CO<sub>2</sub>. */
  private double co2TaxNokPerTonne = 1200.0;

  /** Carbon intensity of the consumed energy in tonnes CO<sub>2</sub> per MWh. */
  private double co2IntensityTonnePerMwh = 0.20;

  /** Annual discount rate used for net-present-value calculations (0.08 = 8%). */
  private double discountRate = 0.08;

  /** Currency label used for reporting. */
  private String currency = "NOK";

  /**
   * Creates a set of economic parameters with default North-Sea-style values.
   */
  public EconomicParameters() {}

  /**
   * Sets the gas sales price.
   *
   * @param value gas price in currency per Sm3 (must be finite and non-negative)
   * @return this instance for chaining
   */
  public EconomicParameters setGasPrice(double value) {
    this.gasPriceNokPerSm3 = value;
    return this;
  }

  /**
   * Sets the oil/condensate sales price.
   *
   * @param value oil price in currency per Sm3 (must be finite and non-negative)
   * @return this instance for chaining
   */
  public EconomicParameters setOilPrice(double value) {
    this.oilPriceNokPerSm3 = value;
    return this;
  }

  /**
   * Sets the cost of consumed energy.
   *
   * @param value energy cost in currency per kWh (must be finite and non-negative)
   * @return this instance for chaining
   */
  public EconomicParameters setPowerCost(double value) {
    this.powerCostNokPerKWh = value;
    return this;
  }

  /**
   * Sets the carbon price/tax.
   *
   * @param value carbon price in currency per tonne CO2 (must be finite and non-negative)
   * @return this instance for chaining
   */
  public EconomicParameters setCo2Tax(double value) {
    this.co2TaxNokPerTonne = value;
    return this;
  }

  /**
   * Sets the carbon intensity of the consumed energy.
   *
   * @param value carbon intensity in tonnes CO2 per MWh (must be finite and non-negative)
   * @return this instance for chaining
   */
  public EconomicParameters setCo2IntensityTonnePerMWh(double value) {
    this.co2IntensityTonnePerMwh = value;
    return this;
  }

  /**
   * Sets the annual discount rate.
   *
   * @param value annual discount rate as a fraction (0.08 = 8%); must be greater than -1
   * @return this instance for chaining
   */
  public EconomicParameters setDiscountRate(double value) {
    this.discountRate = value;
    return this;
  }

  /**
   * Sets the reporting currency label.
   *
   * @param value the currency label (null is replaced with {@code "NOK"})
   * @return this instance for chaining
   */
  public EconomicParameters setCurrency(String value) {
    this.currency = value != null ? value : "NOK";
    return this;
  }

  /**
   * Gets the gas sales price.
   *
   * @return gas price in currency per Sm3
   */
  public double getGasPrice() {
    return gasPriceNokPerSm3;
  }

  /**
   * Gets the oil/condensate sales price.
   *
   * @return oil price in currency per Sm3
   */
  public double getOilPrice() {
    return oilPriceNokPerSm3;
  }

  /**
   * Gets the cost of consumed energy.
   *
   * @return energy cost in currency per kWh
   */
  public double getPowerCost() {
    return powerCostNokPerKWh;
  }

  /**
   * Gets the carbon price/tax.
   *
   * @return carbon price in currency per tonne CO2
   */
  public double getCo2Tax() {
    return co2TaxNokPerTonne;
  }

  /**
   * Gets the carbon intensity of the consumed energy.
   *
   * @return carbon intensity in tonnes CO2 per MWh
   */
  public double getCo2IntensityTonnePerMWh() {
    return co2IntensityTonnePerMwh;
  }

  /**
   * Gets the annual discount rate.
   *
   * @return annual discount rate as a fraction
   */
  public double getDiscountRate() {
    return discountRate;
  }

  /**
   * Gets the reporting currency label.
   *
   * @return the currency label
   */
  public String getCurrency() {
    return currency;
  }

  /**
   * Computes the discount factor for a given period.
   *
   * <p>
   * The factor is {@code 1 / (1 + r)^year}, where {@code r} is the annual discount rate. Year 0
   * returns 1.0 (present value of money today).
   * </p>
   *
   * @param year the period index in years (0 = present)
   * @return the discount factor in the range (0, 1]
   */
  public double discountFactor(double year) {
    return 1.0 / Math.pow(1.0 + discountRate, year);
  }
}
