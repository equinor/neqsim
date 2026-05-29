package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Late-life retrofit study: compares the operating cost and CO2 emissions of a baseline gas turbine
 * fleet against a proposed replacement fleet over a multi-year operating profile, and computes net
 * present value (NPV), cumulative CO2 avoided, and simple payback.
 *
 * <p>
 * Typical use: a platform currently operates two large aero-derivative turbines (e.g. 40+ MW) that
 * are running far below their design load because production has declined. This study quantifies
 * the savings from replacing them with smaller industrial turbines that match the actual load
 * profile, including CO2 tax escalation.
 * </p>
 *
 * <p>
 * Inputs are deliberately framed in generic engineering terms — no operator, asset, or vendor names
 * appear in this class.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public class LateLifeRetrofitStudy implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(LateLifeRetrofitStudy.class);

  private final List<GasTurbineUnit> baselineFleet;
  private final List<GasTurbineUnit> retrofitFleet;
  private final double[] demandProfileMW;
  private final int startYear;
  private final CO2TaxSchedule co2TaxSchedule;
  private double fuelPriceNOKPerKg;
  private double discountRate = 0.08;
  private double retrofitCapexMNOK = 0.0;
  private double annualOperatingHours = 8000.0;

  /**
   * Construct a study.
   *
   * @param baselineFleet existing turbines (each with spec/ambient/degradation)
   * @param retrofitFleet replacement turbines
   * @param demandProfileMW year-by-year average demanded power [MW]
   * @param startYear calendar year of the first demand entry
   * @param co2TaxSchedule CO2 cost schedule
   * @param fuelPriceNOKPerKg fuel price [NOK/kg]
   */
  public LateLifeRetrofitStudy(List<GasTurbineUnit> baselineFleet,
      List<GasTurbineUnit> retrofitFleet, double[] demandProfileMW, int startYear,
      CO2TaxSchedule co2TaxSchedule, double fuelPriceNOKPerKg) {
    this.baselineFleet = new ArrayList<GasTurbineUnit>(baselineFleet);
    this.retrofitFleet = new ArrayList<GasTurbineUnit>(retrofitFleet);
    this.demandProfileMW = demandProfileMW.clone();
    this.startYear = startYear;
    this.co2TaxSchedule = co2TaxSchedule;
    this.fuelPriceNOKPerKg = fuelPriceNOKPerKg;
  }

  /**
   * Set the discount rate for NPV.
   *
   * @param discountRate fractional discount rate (e.g. 0.08)
   */
  public void setDiscountRate(double discountRate) {
    this.discountRate = discountRate;
  }

  /**
   * Set the retrofit capital expenditure (paid in {@code startYear}).
   *
   * @param retrofitCapexMNOK CAPEX in million NOK
   */
  public void setRetrofitCapexMNOK(double retrofitCapexMNOK) {
    this.retrofitCapexMNOK = retrofitCapexMNOK;
  }

  /**
   * Set the equivalent operating hours per year.
   *
   * @param annualOperatingHours operating hours [h/yr]
   */
  public void setAnnualOperatingHours(double annualOperatingHours) {
    this.annualOperatingHours = annualOperatingHours;
  }

  /**
   * Run the study.
   *
   * @return result with year-by-year breakdown and NPV
   */
  public RetrofitResult run() {
    RetrofitResult res = new RetrofitResult();
    res.startYear = startYear;
    res.discountRate = discountRate;

    for (int i = 0; i < demandProfileMW.length; i++) {
      int year = startYear + i;
      double demandW = demandProfileMW[i] * 1.0e6;
      double co2Cost = co2TaxSchedule.getTotalNOKPerTonne(year);

      TurbineDispatchOptimizer baselineDisp =
          new TurbineDispatchOptimizer(fuelPriceNOKPerKg, co2Cost);
      TurbineDispatchOptimizer retrofitDisp =
          new TurbineDispatchOptimizer(fuelPriceNOKPerKg, co2Cost);

      TurbineDispatchOptimizer.DispatchResult baseDr =
          baselineDisp.dispatch(baselineFleet, demandW);
      TurbineDispatchOptimizer.DispatchResult retroDr =
          retrofitDisp.dispatch(retrofitFleet, demandW);

      YearResult yr = new YearResult();
      yr.year = year;
      yr.demandedPowerMW = demandProfileMW[i];
      yr.co2CostNOKPerTonne = co2Cost;
      yr.baselineFeasible = baseDr.feasible;
      yr.retrofitFeasible = retroDr.feasible;

      if (baseDr.feasible) {
        yr.baselineFuelKgPerYr = baseDr.totalFuelKgPerHr * annualOperatingHours;
        yr.baselineCO2TonneYr = baseDr.totalCO2KgPerHr * annualOperatingHours / 1.0e3;
        yr.baselineCostMNOK = baseDr.totalCostNOKPerHr * annualOperatingHours / 1.0e6;
      }
      if (retroDr.feasible) {
        yr.retrofitFuelKgPerYr = retroDr.totalFuelKgPerHr * annualOperatingHours;
        yr.retrofitCO2TonneYr = retroDr.totalCO2KgPerHr * annualOperatingHours / 1.0e3;
        yr.retrofitCostMNOK = retroDr.totalCostNOKPerHr * annualOperatingHours / 1.0e6;
      }
      if (baseDr.feasible && retroDr.feasible) {
        yr.savingsMNOK = yr.baselineCostMNOK - yr.retrofitCostMNOK;
        yr.co2AvoidedTonne = yr.baselineCO2TonneYr - yr.retrofitCO2TonneYr;
      }
      res.years.add(yr);
    }

    // NPV and totals
    res.totalCO2AvoidedTonne = 0.0;
    res.totalUndiscountedSavingsMNOK = 0.0;
    res.npvMNOK = -retrofitCapexMNOK;
    int paybackYear = -1;
    double cum = -retrofitCapexMNOK;
    for (int i = 0; i < res.years.size(); i++) {
      YearResult yr = res.years.get(i);
      res.totalCO2AvoidedTonne += yr.co2AvoidedTonne;
      res.totalUndiscountedSavingsMNOK += yr.savingsMNOK;
      double disc = Math.pow(1.0 + discountRate, -(i + 1));
      res.npvMNOK += yr.savingsMNOK * disc;
      cum += yr.savingsMNOK;
      if (paybackYear < 0 && cum >= 0.0) {
        paybackYear = startYear + i;
      }
    }
    res.simplePaybackYear = paybackYear;
    res.retrofitCapexMNOK = retrofitCapexMNOK;

    return res;
  }

  /** Per-year result. */
  public static class YearResult implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Calendar year. */
    public int year;
    /** Demanded power [MW]. */
    public double demandedPowerMW;
    /** Effective CO2 cost [NOK/tonne]. */
    public double co2CostNOKPerTonne;
    /** Baseline dispatch feasibility. */
    public boolean baselineFeasible;
    /** Retrofit dispatch feasibility. */
    public boolean retrofitFeasible;
    /** Baseline annual fuel [kg/yr]. */
    public double baselineFuelKgPerYr;
    /** Baseline annual CO2 [tonne/yr]. */
    public double baselineCO2TonneYr;
    /** Baseline annual operating cost [MNOK/yr]. */
    public double baselineCostMNOK;
    /** Retrofit annual fuel [kg/yr]. */
    public double retrofitFuelKgPerYr;
    /** Retrofit annual CO2 [tonne/yr]. */
    public double retrofitCO2TonneYr;
    /** Retrofit annual operating cost [MNOK/yr]. */
    public double retrofitCostMNOK;
    /** Annual savings [MNOK/yr]. */
    public double savingsMNOK;
    /** Annual CO2 avoided [tonne/yr]. */
    public double co2AvoidedTonne;
  }

  /** Overall retrofit result. */
  public static class RetrofitResult implements Serializable {
    private static final long serialVersionUID = 1L;
    /** First year of the profile. */
    public int startYear;
    /** Discount rate used. */
    public double discountRate;
    /** Retrofit CAPEX [MNOK]. */
    public double retrofitCapexMNOK;
    /** Year-by-year results. */
    public List<YearResult> years = new ArrayList<YearResult>();
    /** Total CO2 avoided over the profile [tonne]. */
    public double totalCO2AvoidedTonne;
    /** Undiscounted lifetime savings [MNOK]. */
    public double totalUndiscountedSavingsMNOK;
    /** NPV of (savings − capex) [MNOK]. */
    public double npvMNOK;
    /** First year with cumulative cash flow ≥ 0; −1 if no payback. */
    public int simplePaybackYear;

    /**
     * Convert the result to a summary map for serialization.
     *
     * @return summary map
     */
    public Map<String, Object> toSummary() {
      Map<String, Object> m = new LinkedHashMap<String, Object>();
      m.put("startYear", startYear);
      m.put("years", years.size());
      m.put("discountRate", discountRate);
      m.put("retrofitCapexMNOK", retrofitCapexMNOK);
      m.put("npvMNOK", npvMNOK);
      m.put("totalCO2AvoidedTonne", totalCO2AvoidedTonne);
      m.put("totalUndiscountedSavingsMNOK", totalUndiscountedSavingsMNOK);
      m.put("simplePaybackYear", simplePaybackYear);
      return m;
    }
  }
}
