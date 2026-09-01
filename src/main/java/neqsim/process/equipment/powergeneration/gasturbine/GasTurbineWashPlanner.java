package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;

/**
 * Screening-level planner for gas-turbine compressor water-wash programmes.
 *
 * <p>
 * Axial-compressor fouling degrades a gas turbine gradually and is recovered by washing. This class turns a
 * <em>measured</em> degradation trend and a wash recovery effectiveness into the quantities a wash decision needs: the
 * average performance penalty carried between washes, the extra fuel gas and CO2 that penalty costs, and the wash
 * interval that minimises total annual cost.
 * </p>
 *
 * <h2>Sawtooth degradation model</h2>
 *
 * <p>
 * With a constant fractional efficiency-loss rate {@code r} per fired hour, a wash interval {@code T} and a wash
 * recovery effectiveness {@code e} (fraction of the accumulated loss removed), the loss at the start of each cycle
 * settles at
 * </p>
 *
 * <pre>
 * L0 = (1 - e) * r * T / e
 * </pre>
 *
 * <p>
 * and grows linearly to {@code L0 + r*T} before the next wash. Because fuel consumption at fixed shaft power scales as
 * {@code 1 / (1 - L)}, the extra-fuel fraction is integrated over the cycle rather than taken at the mean loss. A
 * perfect wash ({@code e = 1}) gives the familiar {@code r*T/2} mean loss; an imperfect on-line wash leaves a residual
 * that the model carries explicitly.
 * </p>
 *
 * <h2>Typical usage</h2>
 *
 * <pre>{@code
 * GasTurbineWashPlanner planner = new GasTurbineWashPlanner();
 * planner.setShaftPowerW(22.1e6);
 * planner.setBaselineHeatRateKJPerKWh(10090.0);
 * planner.setFuelLhvKJPerSm3(36500.0);
 * planner.setEfficiencyLossRatePerFiredHour(GasTurbineWashPlanner.lossRateFromCorrectedEfficiencyTrend(0.24, 91.0));
 * planner.setRecoveryEffectiveness(0.40); // on-line wash
 * GasTurbineWashPlanner.WashPlan plan = planner.evaluate(720.0); // monthly
 * double optimum = planner.optimize(24.0, 8760.0, 24.0).getWashIntervalHours();
 * }</pre>
 *
 * <p>
 * The relations are screening-level and are intended to rank wash strategies and size the prize of a permanent wash
 * installation, not to replace an OEM performance guarantee.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class GasTurbineWashPlanner implements Serializable {

  private static final long serialVersionUID = 1L;

  /** Number of integration steps used per wash cycle. */
  private static final int CYCLE_STEPS = 200;

  private double shaftPowerW = 0.0;
  private double baselineHeatRateKJPerKWh = 0.0;
  private double fuelLhvKJPerSm3 = 36500.0;
  private double co2PerSm3Fuel = 2.34;
  private double efficiencyLossRatePerFiredHour = 0.0;
  private double recoveryEffectiveness = 1.0;
  private double maxEfficiencyLossFraction = 0.10;
  private double annualOperatingHours = 8760.0;
  private double outageHoursPerWash = 0.0;
  private double washCostPerEvent = 0.0;
  private double fuelValuePerSm3 = 0.0;
  private double co2PricePerTonne = 0.0;
  private double outageCostPerHour = 0.0;

  /**
   * Result of evaluating one wash interval.
   */
  public static class WashPlan implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double washIntervalHours;
    private final double washesPerYear;
    private final double meanEfficiencyLossFraction;
    private final double meanExtraFuelFraction;
    private final double firedHoursPerYear;
    private final double baseFuelSm3PerYear;
    private final double extraFuelSm3PerYear;
    private final double extraCo2TonnesPerYear;
    private final double fuelCostPerYear;
    private final double co2CostPerYear;
    private final double washCostPerYear;
    private final double outageCostPerYear;
    private final double totalCostPerYear;

    WashPlan(double washIntervalHours, double washesPerYear, double meanEfficiencyLossFraction,
        double meanExtraFuelFraction, double firedHoursPerYear, double baseFuelSm3PerYear, double extraFuelSm3PerYear,
        double extraCo2TonnesPerYear, double fuelCostPerYear, double co2CostPerYear, double washCostPerYear,
        double outageCostPerYear) {
      this.washIntervalHours = washIntervalHours;
      this.washesPerYear = washesPerYear;
      this.meanEfficiencyLossFraction = meanEfficiencyLossFraction;
      this.meanExtraFuelFraction = meanExtraFuelFraction;
      this.firedHoursPerYear = firedHoursPerYear;
      this.baseFuelSm3PerYear = baseFuelSm3PerYear;
      this.extraFuelSm3PerYear = extraFuelSm3PerYear;
      this.extraCo2TonnesPerYear = extraCo2TonnesPerYear;
      this.fuelCostPerYear = fuelCostPerYear;
      this.co2CostPerYear = co2CostPerYear;
      this.washCostPerYear = washCostPerYear;
      this.outageCostPerYear = outageCostPerYear;
      this.totalCostPerYear = fuelCostPerYear + co2CostPerYear + washCostPerYear + outageCostPerYear;
    }

    /**
     * Wash interval evaluated.
     *
     * @return interval in fired hours between washes
     */
    public double getWashIntervalHours() {
      return washIntervalHours;
    }

    /**
     * Number of wash events per year at this interval.
     *
     * @return washes per year
     */
    public double getWashesPerYear() {
      return washesPerYear;
    }

    /**
     * Cycle-average fractional efficiency loss carried between washes.
     *
     * @return mean efficiency loss fraction (0-1)
     */
    public double getMeanEfficiencyLossFraction() {
      return meanEfficiencyLossFraction;
    }

    /**
     * Cycle-average extra fuel fraction relative to a clean machine.
     *
     * @return mean extra fuel fraction (0-1)
     */
    public double getMeanExtraFuelFraction() {
      return meanExtraFuelFraction;
    }

    /**
     * Fired hours per year after deducting wash outage.
     *
     * @return fired hours per year
     */
    public double getFiredHoursPerYear() {
      return firedHoursPerYear;
    }

    /**
     * Fuel consumption of a clean machine over the fired hours.
     *
     * @return base fuel in Sm3 per year
     */
    public double getBaseFuelSm3PerYear() {
      return baseFuelSm3PerYear;
    }

    /**
     * Extra fuel burnt because of fouling.
     *
     * @return extra fuel in Sm3 per year
     */
    public double getExtraFuelSm3PerYear() {
      return extraFuelSm3PerYear;
    }

    /**
     * Extra CO2 emitted because of fouling.
     *
     * @return extra CO2 in tonnes per year
     */
    public double getExtraCo2TonnesPerYear() {
      return extraCo2TonnesPerYear;
    }

    /**
     * Cost of the extra fuel.
     *
     * @return fuel cost per year in the currency of the price inputs
     */
    public double getFuelCostPerYear() {
      return fuelCostPerYear;
    }

    /**
     * Cost of the extra CO2.
     *
     * @return CO2 cost per year in the currency of the price inputs
     */
    public double getCo2CostPerYear() {
      return co2CostPerYear;
    }

    /**
     * Cost of performing the washes.
     *
     * @return wash cost per year in the currency of the price inputs
     */
    public double getWashCostPerYear() {
      return washCostPerYear;
    }

    /**
     * Cost of the production deferred during wash outages.
     *
     * @return outage cost per year in the currency of the price inputs
     */
    public double getOutageCostPerYear() {
      return outageCostPerYear;
    }

    /**
     * Total annual cost of fouling plus washing at this interval.
     *
     * @return total cost per year in the currency of the price inputs
     */
    public double getTotalCostPerYear() {
      return totalCostPerYear;
    }
  }

  /**
   * Convert a measured corrected-efficiency trend into a fractional efficiency-loss rate per fired hour.
   *
   * <p>
   * Energy-management systems commonly trend a "corrected turbine efficiency" in percentage points. A trend of
   * {@code d} percentage points lost per 1000 fired hours on a clean value of {@code eta0} percentage points is a
   * relative loss of {@code d / eta0} per 1000 fired hours.
   * </p>
   *
   * @param percentagePointsPer1000FiredHours magnitude of the measured decline in percentage points per 1000 fired
   * hours (positive number)
   * @param cleanCorrectedEfficiencyPercent corrected efficiency of the clean machine in percent (must be positive)
   * @return fractional efficiency-loss rate per fired hour
   */
  public static double lossRateFromCorrectedEfficiencyTrend(double percentagePointsPer1000FiredHours,
      double cleanCorrectedEfficiencyPercent) {
    if (cleanCorrectedEfficiencyPercent <= 0.0) {
      return 0.0;
    }
    return Math.abs(percentagePointsPer1000FiredHours) / cleanCorrectedEfficiencyPercent / 1000.0;
  }

  /**
   * Cycle-average fractional efficiency loss of the sawtooth for a given interval.
   *
   * @param washIntervalHours wash interval in fired hours
   * @return mean efficiency loss fraction over one steady-state cycle
   */
  public double meanEfficiencyLossFraction(double washIntervalHours) {
    return integrateCycle(washIntervalHours)[0];
  }

  /**
   * Evaluate one wash interval.
   *
   * @param washIntervalHours wash interval in fired hours (must be positive)
   * @return the resulting {@link WashPlan}
   */
  public WashPlan evaluate(double washIntervalHours) {
    if (washIntervalHours <= 0.0) {
      throw new IllegalArgumentException("washIntervalHours must be positive");
    }
    double[] means = integrateCycle(washIntervalHours);
    double meanLoss = means[0];
    double meanExtraFuel = means[1];

    double cycleHours = washIntervalHours + outageHoursPerWash;
    double cyclesPerYear = annualOperatingHours / cycleHours;
    double firedHoursPerYear = cyclesPerYear * washIntervalHours;
    double outageHoursPerYear = cyclesPerYear * outageHoursPerWash;

    double fuelSm3PerFiredHour = cleanFuelSm3PerHour();
    double baseFuel = fuelSm3PerFiredHour * firedHoursPerYear;
    double extraFuel = baseFuel * meanExtraFuel;
    double extraCo2 = extraFuel * co2PerSm3Fuel / 1000.0;

    return new WashPlan(washIntervalHours, cyclesPerYear, meanLoss, meanExtraFuel, firedHoursPerYear, baseFuel,
        extraFuel, extraCo2, extraFuel * fuelValuePerSm3, extraCo2 * co2PricePerTonne, cyclesPerYear * washCostPerEvent,
        outageHoursPerYear * outageCostPerHour);
  }

  /**
   * Scan wash intervals and return the plan with the lowest total annual cost.
   *
   * @param minIntervalHours smallest interval to consider in fired hours
   * @param maxIntervalHours largest interval to consider in fired hours
   * @param stepHours scan step in fired hours
   * @return the lowest-cost {@link WashPlan} found
   */
  public WashPlan optimize(double minIntervalHours, double maxIntervalHours, double stepHours) {
    if (minIntervalHours <= 0.0 || maxIntervalHours < minIntervalHours || stepHours <= 0.0) {
      throw new IllegalArgumentException("invalid scan range");
    }
    WashPlan best = null;
    for (double t = minIntervalHours; t <= maxIntervalHours + 1.0e-9; t += stepHours) {
      WashPlan plan = evaluate(t);
      if (best == null || plan.getTotalCostPerYear() < best.getTotalCostPerYear()) {
        best = plan;
      }
    }
    return best;
  }

  /**
   * Simple payback of a permanent wash installation against a reference wash regime.
   *
   * @param capex installed cost of the permanent wash system in the currency of the price inputs
   * @param reference the wash plan without the permanent system
   * @param withPermanentSystem the wash plan enabled by the permanent system
   * @return payback in years, or {@link Double#POSITIVE_INFINITY} if the permanent system does not save money
   */
  public static double paybackYears(double capex, WashPlan reference, WashPlan withPermanentSystem) {
    if (reference == null || withPermanentSystem == null) {
      return Double.POSITIVE_INFINITY;
    }
    double saving = reference.getTotalCostPerYear() - withPermanentSystem.getTotalCostPerYear();
    if (saving <= 0.0) {
      return Double.POSITIVE_INFINITY;
    }
    return capex / saving;
  }

  /**
   * Fuel consumption of the clean machine at the configured shaft power.
   *
   * @return fuel rate in Sm3 per fired hour
   */
  public double cleanFuelSm3PerHour() {
    if (shaftPowerW <= 0.0 || baselineHeatRateKJPerKWh <= 0.0 || fuelLhvKJPerSm3 <= 0.0) {
      return 0.0;
    }
    double shaftPowerKW = shaftPowerW / 1.0e3;
    double fuelEnergyKJPerHour = baselineHeatRateKJPerKWh * shaftPowerKW;
    return fuelEnergyKJPerHour / fuelLhvKJPerSm3;
  }

  /**
   * Integrate the steady-state sawtooth over one cycle.
   *
   * @param washIntervalHours wash interval in fired hours
   * @return array of {mean efficiency loss fraction, mean extra fuel fraction}
   */
  private double[] integrateCycle(double washIntervalHours) {
    double r = efficiencyLossRatePerFiredHour;
    if (r <= 0.0 || washIntervalHours <= 0.0) {
      return new double[] { 0.0, 0.0 };
    }
    double e = recoveryEffectiveness;
    double residual;
    if (e >= 1.0) {
      residual = 0.0;
    } else if (e <= 0.0) {
      residual = maxEfficiencyLossFraction;
    } else {
      residual = (1.0 - e) * r * washIntervalHours / e;
    }
    double sumLoss = 0.0;
    double sumExtraFuel = 0.0;
    for (int i = 0; i < CYCLE_STEPS; i++) {
      double t = washIntervalHours * (i + 0.5) / CYCLE_STEPS;
      double loss = residual + r * t;
      if (loss > maxEfficiencyLossFraction) {
        loss = maxEfficiencyLossFraction;
      }
      sumLoss += loss;
      sumExtraFuel += 1.0 / (1.0 - loss) - 1.0;
    }
    return new double[] { sumLoss / CYCLE_STEPS, sumExtraFuel / CYCLE_STEPS };
  }

  /**
   * Get the sustained shaft power.
   *
   * @return shaft power in W
   */
  public double getShaftPowerW() {
    return shaftPowerW;
  }

  /**
   * Set the sustained shaft power the turbine delivers.
   *
   * @param shaftPowerW shaft power in W
   */
  public void setShaftPowerW(double shaftPowerW) {
    this.shaftPowerW = shaftPowerW;
  }

  /**
   * Get the clean-machine heat rate.
   *
   * @return heat rate in kJ/kWh (LHV basis)
   */
  public double getBaselineHeatRateKJPerKWh() {
    return baselineHeatRateKJPerKWh;
  }

  /**
   * Set the clean-machine heat rate.
   *
   * @param baselineHeatRateKJPerKWh heat rate in kJ/kWh (LHV basis)
   */
  public void setBaselineHeatRateKJPerKWh(double baselineHeatRateKJPerKWh) {
    this.baselineHeatRateKJPerKWh = baselineHeatRateKJPerKWh;
  }

  /**
   * Get the fuel lower heating value on a standard-volume basis.
   *
   * @return fuel LHV in kJ/Sm3
   */
  public double getFuelLhvKJPerSm3() {
    return fuelLhvKJPerSm3;
  }

  /**
   * Set the fuel lower heating value on a standard-volume basis.
   *
   * @param fuelLhvKJPerSm3 fuel LHV in kJ/Sm3
   */
  public void setFuelLhvKJPerSm3(double fuelLhvKJPerSm3) {
    this.fuelLhvKJPerSm3 = fuelLhvKJPerSm3;
  }

  /**
   * Get the CO2 emitted per standard cubic metre of fuel burnt.
   *
   * @return CO2 factor in kg CO2 per Sm3 fuel
   */
  public double getCo2PerSm3Fuel() {
    return co2PerSm3Fuel;
  }

  /**
   * Set the CO2 emitted per standard cubic metre of fuel burnt.
   *
   * @param co2PerSm3Fuel CO2 factor in kg CO2 per Sm3 fuel
   */
  public void setCo2PerSm3Fuel(double co2PerSm3Fuel) {
    this.co2PerSm3Fuel = co2PerSm3Fuel;
  }

  /**
   * Get the fractional efficiency-loss rate.
   *
   * @return fractional efficiency loss per fired hour
   */
  public double getEfficiencyLossRatePerFiredHour() {
    return efficiencyLossRatePerFiredHour;
  }

  /**
   * Set the fractional efficiency-loss rate, for example from
   * {@link #lossRateFromCorrectedEfficiencyTrend(double, double)}.
   *
   * @param efficiencyLossRatePerFiredHour fractional efficiency loss per fired hour
   */
  public void setEfficiencyLossRatePerFiredHour(double efficiencyLossRatePerFiredHour) {
    this.efficiencyLossRatePerFiredHour = Math.max(0.0, efficiencyLossRatePerFiredHour);
  }

  /**
   * Get the wash recovery effectiveness.
   *
   * @return fraction of the accumulated loss removed by one wash (0-1)
   */
  public double getRecoveryEffectiveness() {
    return recoveryEffectiveness;
  }

  /**
   * Set the wash recovery effectiveness. On-line washing typically recovers 0.3-0.5 of the accumulated loss, an
   * off-line crank wash 0.85-0.95.
   *
   * @param recoveryEffectiveness fraction of the accumulated loss removed by one wash (0-1)
   */
  public void setRecoveryEffectiveness(double recoveryEffectiveness) {
    this.recoveryEffectiveness = Math.max(0.0, Math.min(1.0, recoveryEffectiveness));
  }

  /**
   * Get the cap on the accumulated efficiency loss.
   *
   * @return maximum efficiency loss fraction
   */
  public double getMaxEfficiencyLossFraction() {
    return maxEfficiencyLossFraction;
  }

  /**
   * Set the cap on the accumulated efficiency loss (fouling saturates).
   *
   * @param maxEfficiencyLossFraction maximum efficiency loss fraction (0-1)
   */
  public void setMaxEfficiencyLossFraction(double maxEfficiencyLossFraction) {
    this.maxEfficiencyLossFraction = Math.max(0.0, Math.min(0.99, maxEfficiencyLossFraction));
  }

  /**
   * Get the annual calendar hours the machine is committed to run.
   *
   * @return annual operating hours
   */
  public double getAnnualOperatingHours() {
    return annualOperatingHours;
  }

  /**
   * Set the annual calendar hours the machine is committed to run (fired hours plus wash outage).
   *
   * @param annualOperatingHours annual operating hours
   */
  public void setAnnualOperatingHours(double annualOperatingHours) {
    this.annualOperatingHours = Math.max(0.0, annualOperatingHours);
  }

  /**
   * Get the outage per wash event.
   *
   * @return outage hours per wash (0 for on-line washing)
   */
  public double getOutageHoursPerWash() {
    return outageHoursPerWash;
  }

  /**
   * Set the outage per wash event (0 for on-line washing).
   *
   * @param outageHoursPerWash outage hours per wash
   */
  public void setOutageHoursPerWash(double outageHoursPerWash) {
    this.outageHoursPerWash = Math.max(0.0, outageHoursPerWash);
  }

  /**
   * Get the direct cost of one wash event.
   *
   * @return cost per wash event
   */
  public double getWashCostPerEvent() {
    return washCostPerEvent;
  }

  /**
   * Set the direct cost of one wash event (detergent, demineralised water, labour).
   *
   * @param washCostPerEvent cost per wash event
   */
  public void setWashCostPerEvent(double washCostPerEvent) {
    this.washCostPerEvent = Math.max(0.0, washCostPerEvent);
  }

  /**
   * Get the value of the fuel gas.
   *
   * @return fuel value per Sm3
   */
  public double getFuelValuePerSm3() {
    return fuelValuePerSm3;
  }

  /**
   * Set the value of the fuel gas (sales value of the gas burnt as fuel).
   *
   * @param fuelValuePerSm3 fuel value per Sm3
   */
  public void setFuelValuePerSm3(double fuelValuePerSm3) {
    this.fuelValuePerSm3 = Math.max(0.0, fuelValuePerSm3);
  }

  /**
   * Get the CO2 price.
   *
   * @return CO2 price per tonne
   */
  public double getCo2PricePerTonne() {
    return co2PricePerTonne;
  }

  /**
   * Set the CO2 price (carbon tax plus emission-trading allowance).
   *
   * @param co2PricePerTonne CO2 price per tonne
   */
  public void setCo2PricePerTonne(double co2PricePerTonne) {
    this.co2PricePerTonne = Math.max(0.0, co2PricePerTonne);
  }

  /**
   * Get the deferred-production cost of an outage hour.
   *
   * @return outage cost per hour
   */
  public double getOutageCostPerHour() {
    return outageCostPerHour;
  }

  /**
   * Set the deferred-production cost of an outage hour.
   *
   * @param outageCostPerHour outage cost per hour
   */
  public void setOutageCostPerHour(double outageCostPerHour) {
    this.outageCostPerHour = Math.max(0.0, outageCostPerHour);
  }
}
