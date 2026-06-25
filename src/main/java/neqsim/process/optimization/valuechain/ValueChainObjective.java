package neqsim.process.optimization.valuechain;

import java.io.Serializable;
import java.util.Locale;

/**
 * Converts a converged process operating point into a single economic objective value.
 *
 * <p>
 * This is the foundational value-chain objective: it rolls produced sales streams into revenue, consumed energy into
 * operating cost, and the carbon associated with that energy into a carbon cost, then returns the net daily value (and,
 * for life-of-field work, the present value of an annual cash flow). It is an open analogue of the "activated
 * economics" layer found in commercial flowsheet optimizers, but it lives <em>inside</em> the optimization loop so it
 * can be used directly as the objective of {@code AgenticProcessOptimizer} or any of the value-chain optimizers in this
 * package.
 * </p>
 *
 * <p>
 * The class is deliberately decoupled from the flowsheet: callers pass already-computed physical quantities (export gas
 * rate, oil rate, total shaft/electrical power) so the objective is pure, fast, and trivially testable. The carbon
 * price can be overridden per call to sweep a CO <sub>2</sub>-price axis and build a revenue-versus-emissions Pareto
 * front without re-running the simulation.
 * </p>
 *
 * <pre>
 * ValueChainObjective obj = new ValueChainObjective(econ);
 * ValueChainObjective.ValueResult r = obj.evaluate(3.2e6, 0.0, 4500.0);
 * double npv = obj.presentValueOfAnnualCashFlow(r.getNetValueNokPerDay(), 0);
 * </pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class ValueChainObjective implements Serializable {

  /** Serialization version identifier. */
  private static final long serialVersionUID = 1000L;

  /** Days of production assumed per calendar year for annualisation. */
  public static final double PRODUCTION_DAYS_PER_YEAR = 365.0;

  /** The economic parameters backing this objective. */
  private final EconomicParameters econ;

  /**
   * Immutable breakdown of the economic value of a single operating point.
   */
  public static class ValueResult implements Serializable {

    /** Serialization version identifier. */
    private static final long serialVersionUID = 1000L;

    /** Sales revenue in currency per day. */
    private final double revenueNokPerDay;

    /** Energy operating cost in currency per day. */
    private final double energyCostNokPerDay;

    /** Carbon cost in currency per day. */
    private final double carbonCostNokPerDay;

    /** Emitted CO2 in tonnes per day. */
    private final double co2TonnePerDay;

    /** Net value (revenue minus energy and carbon cost) in currency per day. */
    private final double netValueNokPerDay;

    /**
     * Creates an immutable value result.
     *
     * @param revenueNokPerDay sales revenue in currency per day
     * @param energyCostNokPerDay energy operating cost in currency per day
     * @param carbonCostNokPerDay carbon cost in currency per day
     * @param co2TonnePerDay emitted CO2 in tonnes per day
     * @param netValueNokPerDay net value in currency per day
     */
    public ValueResult(double revenueNokPerDay, double energyCostNokPerDay, double carbonCostNokPerDay,
        double co2TonnePerDay, double netValueNokPerDay) {
      this.revenueNokPerDay = revenueNokPerDay;
      this.energyCostNokPerDay = energyCostNokPerDay;
      this.carbonCostNokPerDay = carbonCostNokPerDay;
      this.co2TonnePerDay = co2TonnePerDay;
      this.netValueNokPerDay = netValueNokPerDay;
    }

    /**
     * Gets the sales revenue.
     *
     * @return revenue in currency per day
     */
    public double getRevenueNokPerDay() {
      return revenueNokPerDay;
    }

    /**
     * Gets the energy operating cost.
     *
     * @return energy cost in currency per day
     */
    public double getEnergyCostNokPerDay() {
      return energyCostNokPerDay;
    }

    /**
     * Gets the carbon cost.
     *
     * @return carbon cost in currency per day
     */
    public double getCarbonCostNokPerDay() {
      return carbonCostNokPerDay;
    }

    /**
     * Gets the emitted CO2.
     *
     * @return emitted CO2 in tonnes per day
     */
    public double getCo2TonnePerDay() {
      return co2TonnePerDay;
    }

    /**
     * Gets the net value.
     *
     * @return net value in currency per day
     */
    public double getNetValueNokPerDay() {
      return netValueNokPerDay;
    }

    /**
     * Renders this result as a compact JSON object.
     *
     * @return a JSON string with the value breakdown
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{");
      sb.append("\"revenueNokPerDay\":").append(fmt(revenueNokPerDay)).append(",");
      sb.append("\"energyCostNokPerDay\":").append(fmt(energyCostNokPerDay)).append(",");
      sb.append("\"carbonCostNokPerDay\":").append(fmt(carbonCostNokPerDay)).append(",");
      sb.append("\"co2TonnePerDay\":").append(fmt(co2TonnePerDay)).append(",");
      sb.append("\"netValueNokPerDay\":").append(fmt(netValueNokPerDay));
      sb.append("}");
      return sb.toString();
    }

    /**
     * Formats a double for JSON output with six significant digits.
     *
     * @param value the value to format
     * @return the formatted string
     */
    private static String fmt(double value) {
      return String.format(Locale.US, "%.6g", value);
    }
  }

  /**
   * Creates a value-chain objective backed by the supplied economic parameters.
   *
   * @param econ the economic parameters (must not be null)
   */
  public ValueChainObjective(EconomicParameters econ) {
    if (econ == null) {
      throw new IllegalArgumentException("EconomicParameters must not be null");
    }
    this.econ = econ;
  }

  /**
   * Gets the economic parameters backing this objective.
   *
   * @return the economic parameters
   */
  public EconomicParameters getEconomicParameters() {
    return econ;
  }

  /**
   * Evaluates the net daily economic value of an operating point using the configured carbon price.
   *
   * @param exportGasSm3PerDay export sales-gas rate in Sm3/day (non-negative)
   * @param exportOilSm3PerDay export oil/condensate rate in Sm3/day (non-negative; 0 for dry gas)
   * @param totalPowerKw total consumed shaft/electrical power in kW (non-negative)
   * @return the value breakdown for this operating point
   */
  public ValueResult evaluate(double exportGasSm3PerDay, double exportOilSm3PerDay, double totalPowerKw) {
    return evaluate(exportGasSm3PerDay, exportOilSm3PerDay, totalPowerKw, econ.getCo2Tax());
  }

  /**
   * Evaluates the net daily economic value of an operating point with an explicit carbon price.
   *
   * <p>
   * Energy is converted to CO<sub>2</sub> via the configured carbon intensity (tonnes/MWh). Sweep
   * {@code carbonPriceNokPerTonne} to trace a revenue-versus-emissions Pareto front for a fixed operating point.
   * </p>
   *
   * @param exportGasSm3PerDay export sales-gas rate in Sm3/day (non-negative)
   * @param exportOilSm3PerDay export oil/condensate rate in Sm3/day (non-negative)
   * @param totalPowerKw total consumed shaft/electrical power in kW (non-negative)
   * @param carbonPriceNokPerTonne carbon price in currency per tonne CO2 (non-negative)
   * @return the value breakdown for this operating point
   */
  public ValueResult evaluate(double exportGasSm3PerDay, double exportOilSm3PerDay, double totalPowerKw,
      double carbonPriceNokPerTonne) {
    double revenue = exportGasSm3PerDay * econ.getGasPrice() + exportOilSm3PerDay * econ.getOilPrice();
    double energyMwhPerDay = totalPowerKw * 24.0 / 1000.0;
    double energyCost = energyMwhPerDay * 1000.0 * econ.getPowerCost();
    double co2TonnePerDay = energyMwhPerDay * econ.getCo2IntensityTonnePerMWh();
    double carbonCost = co2TonnePerDay * carbonPriceNokPerTonne;
    double net = revenue - energyCost - carbonCost;
    return new ValueResult(revenue, energyCost, carbonCost, co2TonnePerDay, net);
  }

  /**
   * Computes the present value of an annual cash flow received in a given year.
   *
   * <p>
   * The daily net value is annualised over {@link #PRODUCTION_DAYS_PER_YEAR} and discounted to the present using the
   * configured discount rate.
   * </p>
   *
   * @param netValueNokPerDay the net daily value in currency per day
   * @param year the year in which the cash flow is received (0 = present)
   * @return the present value of one year of cash flow in the configured currency
   */
  public double presentValueOfAnnualCashFlow(double netValueNokPerDay, double year) {
    double annual = netValueNokPerDay * PRODUCTION_DAYS_PER_YEAR;
    return annual * econ.discountFactor(year);
  }
}
