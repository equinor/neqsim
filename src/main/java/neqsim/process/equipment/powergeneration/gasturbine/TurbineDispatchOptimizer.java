package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Economic dispatch optimizer for a fleet of {@link GasTurbineUnit}s.
 *
 * <p>
 * Given a demanded shaft / generator power and a list of candidate turbines, the optimizer selects
 * which units to run and at what load to minimise the sum of fuel cost and CO2 cost, subject to:
 * </p>
 *
 * <ul>
 * <li>Σ available power of running units ≥ demand (with optional N+1 spare reserve margin)</li>
 * <li>Each running unit operates between its minimum stable load fraction and its site-corrected
 * available power</li>
 * </ul>
 *
 * <p>
 * The current implementation enumerates all on/off combinations for fleets up to
 * {@value #BRUTE_FORCE_LIMIT} units (2^N feasible for small dispatch problems) and within each
 * combination distributes load by greedy heat-rate-curve ordering. For larger fleets, a heuristic
 * merit-order dispatch is used.
 * </p>
 *
 * @author neqsim
 * @version $Id: $Id
 */
public class TurbineDispatchOptimizer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(TurbineDispatchOptimizer.class);

  /** Maximum fleet size for brute force enumeration. */
  public static final int BRUTE_FORCE_LIMIT = 8;

  private double fuelPriceNOKPerKg;
  private double co2CostNOKPerTonne;
  private boolean requireNplusOne = true;

  /**
   * Construct an optimizer with fuel and CO2 cost coefficients.
   *
   * @param fuelPriceNOKPerKg fuel gas price [NOK/kg]
   * @param co2CostNOKPerTonne effective CO2 cost [NOK/tonne]
   */
  public TurbineDispatchOptimizer(double fuelPriceNOKPerKg, double co2CostNOKPerTonne) {
    this.fuelPriceNOKPerKg = fuelPriceNOKPerKg;
    this.co2CostNOKPerTonne = co2CostNOKPerTonne;
  }

  /**
   * Toggle the N+1 spare requirement.
   *
   * @param requireNplusOne true to require one spare unit on stand-by
   */
  public void setRequireNplusOne(boolean requireNplusOne) {
    this.requireNplusOne = requireNplusOne;
  }

  /**
   * Update the CO2 cost coefficient (used by year-by-year studies).
   *
   * @param co2CostNOKPerTonne effective CO2 cost [NOK/tonne]
   */
  public void setCO2CostNOKPerTonne(double co2CostNOKPerTonne) {
    this.co2CostNOKPerTonne = co2CostNOKPerTonne;
  }

  /**
   * Update the fuel price (used by year-by-year studies).
   *
   * @param fuelPriceNOKPerKg fuel gas price [NOK/kg]
   */
  public void setFuelPriceNOKPerKg(double fuelPriceNOKPerKg) {
    this.fuelPriceNOKPerKg = fuelPriceNOKPerKg;
  }

  /**
   * Dispatch the fleet to meet a given demand. Each turbine in {@code fleet} should already have
   * its fuel stream, spec, ambient conditions, and degradation configured.
   *
   * @param fleet candidate gas turbines
   * @param demandedPowerW total demanded shaft power [W]
   * @return dispatch result
   */
  public DispatchResult dispatch(List<GasTurbineUnit> fleet, double demandedPowerW) {
    if (fleet == null || fleet.isEmpty()) {
      return DispatchResult.infeasible("Empty fleet");
    }
    if (fleet.size() > BRUTE_FORCE_LIMIT) {
      return dispatchMeritOrder(fleet, demandedPowerW);
    }
    return dispatchBruteForce(fleet, demandedPowerW);
  }

  private DispatchResult dispatchBruteForce(List<GasTurbineUnit> fleet, double demandedPowerW) {
    int n = fleet.size();
    int combos = 1 << n;
    DispatchResult best = DispatchResult.infeasible("No feasible combination");
    double bestCost = Double.POSITIVE_INFINITY;

    for (int mask = 1; mask < combos; mask++) {
      List<GasTurbineUnit> running = new ArrayList<GasTurbineUnit>();
      List<GasTurbineUnit> spare = new ArrayList<GasTurbineUnit>();
      for (int i = 0; i < n; i++) {
        if ((mask & (1 << i)) != 0) {
          running.add(fleet.get(i));
        } else {
          spare.add(fleet.get(i));
        }
      }
      if (requireNplusOne && spare.isEmpty()) {
        continue;
      }
      DispatchResult candidate = evaluateRunningSet(running, demandedPowerW);
      if (!candidate.feasible) {
        continue;
      }
      if (candidate.totalCostNOKPerHr < bestCost) {
        bestCost = candidate.totalCostNOKPerHr;
        candidate.runningUnits = running;
        candidate.spareUnits = spare;
        best = candidate;
      }
    }
    return best;
  }

  private DispatchResult dispatchMeritOrder(List<GasTurbineUnit> fleet, double demandedPowerW) {
    // Sort by ISO heat rate ascending — pick most efficient first
    List<GasTurbineUnit> sorted = new ArrayList<GasTurbineUnit>(fleet);
    Collections.sort(sorted, new java.util.Comparator<GasTurbineUnit>() {
      @Override
      public int compare(GasTurbineUnit a, GasTurbineUnit b) {
        return Double.compare(a.getSpec().getHeatRateKJPerKWh(), b.getSpec().getHeatRateKJPerKWh());
      }
    });
    List<GasTurbineUnit> running = new ArrayList<GasTurbineUnit>();
    double cum = 0.0;
    for (GasTurbineUnit u : sorted) {
      running.add(u);
      // Force evaluation to get site-corrected power
      u.setDemandedPower(0.0);
      u.run();
      cum += u.getAvailablePowerW();
      if (cum >= demandedPowerW) {
        break;
      }
    }
    List<GasTurbineUnit> spare = new ArrayList<GasTurbineUnit>(sorted);
    spare.removeAll(running);
    if (requireNplusOne && spare.isEmpty()) {
      return DispatchResult.infeasible("N+1 not met in merit order");
    }
    DispatchResult res = evaluateRunningSet(running, demandedPowerW);
    res.runningUnits = running;
    res.spareUnits = spare;
    return res;
  }

  private DispatchResult evaluateRunningSet(List<GasTurbineUnit> running, double demandedPowerW) {
    // Compute available power
    double totalAvailable = 0.0;
    for (GasTurbineUnit u : running) {
      u.setDemandedPower(0.0);
      u.run();
      totalAvailable += u.getAvailablePowerW();
    }
    if (totalAvailable < demandedPowerW) {
      return DispatchResult.infeasible("Insufficient available power");
    }

    // Equal-load-fraction distribution (good starting heuristic — minimises
    // part-load penalty in flat heat-rate region for similar units)
    double loadFraction = demandedPowerW / totalAvailable;
    double totalFuelKgPerS = 0.0;
    double totalCO2KgPerS = 0.0;
    double totalAvailableUsed = 0.0;
    for (GasTurbineUnit u : running) {
      double unitPower = u.getAvailablePowerW() * loadFraction;
      // Enforce minimum stable load — if violated, this candidate is infeasible
      double minLoad = u.getPerformanceMap().getMinLoadFraction();
      if (loadFraction < minLoad) {
        return DispatchResult.infeasible("Load fraction " + String.format("%.2f", loadFraction)
            + " below min " + String.format("%.2f", minLoad));
      }
      u.setDemandedPower(unitPower);
      u.run();
      totalFuelKgPerS += u.getFuelMassFlowKgPerS();
      totalCO2KgPerS += u.getCO2EmissionKgPerS();
      totalAvailableUsed += u.getAvailablePowerW();
    }

    double fuelCostNOKPerHr = totalFuelKgPerS * 3600.0 * fuelPriceNOKPerKg;
    double co2CostNOKPerHr = totalCO2KgPerS * 3.6 * co2CostNOKPerTonne; // kg/s -> tonne/hr
    DispatchResult res = new DispatchResult();
    res.feasible = true;
    res.demandedPowerW = demandedPowerW;
    res.totalAvailablePowerW = totalAvailableUsed;
    res.loadFraction = loadFraction;
    res.totalFuelKgPerHr = totalFuelKgPerS * 3600.0;
    res.totalCO2KgPerHr = totalCO2KgPerS * 3600.0;
    res.fuelCostNOKPerHr = fuelCostNOKPerHr;
    res.co2CostNOKPerHr = co2CostNOKPerHr;
    res.totalCostNOKPerHr = fuelCostNOKPerHr + co2CostNOKPerHr;
    res.runningUnits = new ArrayList<GasTurbineUnit>(running);
    res.spareUnits = new ArrayList<GasTurbineUnit>();
    return res;
  }

  /**
   * Dispatch result container.
   */
  public static class DispatchResult implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Whether a feasible dispatch was found. */
    public boolean feasible;
    /** Reason (only meaningful when {@link #feasible} is false). */
    public String reason = "";
    /** Demanded shaft power [W]. */
    public double demandedPowerW;
    /** Sum of available power across running units [W]. */
    public double totalAvailablePowerW;
    /** Common load fraction across running units. */
    public double loadFraction;
    /** Total fuel consumption [kg/hr]. */
    public double totalFuelKgPerHr;
    /** Total CO2 emission [kg/hr]. */
    public double totalCO2KgPerHr;
    /** Fuel cost [NOK/hr]. */
    public double fuelCostNOKPerHr;
    /** CO2 cost [NOK/hr]. */
    public double co2CostNOKPerHr;
    /** Total operating cost [NOK/hr]. */
    public double totalCostNOKPerHr;
    /** Running units. */
    public List<GasTurbineUnit> runningUnits = new ArrayList<GasTurbineUnit>();
    /** Spare units. */
    public List<GasTurbineUnit> spareUnits = new ArrayList<GasTurbineUnit>();

    /**
     * Build an infeasible result with a reason.
     *
     * @param reason explanation
     * @return infeasible dispatch result
     */
    public static DispatchResult infeasible(String reason) {
      DispatchResult r = new DispatchResult();
      r.feasible = false;
      r.reason = reason;
      return r;
    }

    /**
     * Get a one-line summary of the dispatch.
     *
     * @return summary string
     */
    public String summary() {
      if (!feasible) {
        return "INFEASIBLE: " + reason;
      }
      List<String> names = new ArrayList<String>();
      for (GasTurbineUnit u : runningUnits) {
        names.add(u.getName());
      }
      return String.format("Running %s at %.0f%% load — %.1f kg/s fuel, %.1f kg/s CO2, %.0f NOK/hr",
          Arrays.toString(names.toArray()), loadFraction * 100.0, totalFuelKgPerHr / 3600.0,
          totalCO2KgPerHr / 3600.0, totalCostNOKPerHr);
    }
  }
}
