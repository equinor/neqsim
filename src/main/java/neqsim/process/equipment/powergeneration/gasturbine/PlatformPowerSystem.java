package neqsim.process.equipment.powergeneration.gasturbine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Screening model of a platform or complex power system: site-rated gas-turbine generators, optional power from shore,
 * waste-heat recovery (WHRU) on the generator exhaust and an optional steam bottoming cycle (combined-cycle yield).
 *
 * <p>
 * For an ambient temperature and an electric (and heat) demand {@link #evaluate} dispatches shore power first (or last,
 * see {@link #setShorePriority(boolean)}), shares the rest over the in-service gas turbines in proportion to their site
 * rating, runs each {@link GasTurbineUnit} at its share (fuel, exhaust flow and temperature, CO2), adds the recovered
 * exhaust heat and deducts the electric output of the bottoming cycle, and reports the capacity, the deficit, the N-1
 * margin and the heat balance. {@link #evaluateAcrossAmbient} repeats it over an ambient-temperature distribution,
 * which is the usual form of the question "does power capacity bind on a hot day?".
 * </p>
 *
 * <p>
 * Mechanical-drive turbines that sit on a compressor shaft are modelled with
 * {@link neqsim.process.equipment.compressor.CompressorShaft#setGasTurbineUnitDriver}; this class covers the electric
 * and heat side. Use {@link neqsim.process.equipment.energy.CoupledProcessEnergySolver} when the power system must be
 * coupled to a running process model.
 * </p>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PlatformPowerSystem implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  private final String name;
  private final List<GasTurbineUnit> generators = new ArrayList<GasTurbineUnit>();
  private final List<Double> generatorEfficiencies = new ArrayList<Double>();
  private double shoreLimitW = 0.0;
  private boolean shorePriority = true;
  private double bottomingYield = 0.0;
  private double whruStackTemperatureK = 423.15;
  private double whruEffectiveness = 0.85;

  /**
   * Constructor.
   *
   * @param name name of the power system
   */
  public PlatformPowerSystem(String name) {
    this.name = name;
  }

  /**
   * Get the name.
   *
   * @return name of the power system
   */
  public String getName() {
    return name;
  }

  /**
   * Add a gas-turbine generator set.
   *
   * @param unit the gas turbine unit (its fuel stream and spec define fuel use and rating)
   * @param electricalEfficiency generator and transformer efficiency from shaft to bus, in (0, 1]
   * @throws IllegalArgumentException if the unit is null or the efficiency is outside (0, 1]
   */
  public void addGenerator(GasTurbineUnit unit, double electricalEfficiency) {
    if (unit == null || !(electricalEfficiency > 0.0 && electricalEfficiency <= 1.0)) {
      throw new IllegalArgumentException("a unit and an electrical efficiency in (0, 1] are required");
    }
    generators.add(unit);
    generatorEfficiencies.add(Double.valueOf(electricalEfficiency));
  }

  /**
   * Set the power-from-shore (or other grid import) limit.
   *
   * @param power electrical import limit (0 or negative: no import)
   * @param unit "W", "kW" or "MW"
   * @throws IllegalArgumentException if the unit is not supported
   */
  public void setPowerFromShoreLimit(double power, String unit) {
    double factor;
    if ("W".equals(unit)) {
      factor = 1.0;
    } else if ("kW".equals(unit)) {
      factor = 1.0e3;
    } else if ("MW".equals(unit)) {
      factor = 1.0e6;
    } else {
      throw new IllegalArgumentException("unsupported power unit '" + unit + "' (use W, kW or MW).");
    }
    this.shoreLimitW = power > 0.0 ? power * factor : 0.0;
  }

  /**
   * Choose whether imported power is used before the gas turbines (default) or only to cover what the turbines cannot
   * supply.
   *
   * @param shoreFirst true to dispatch shore power first
   */
  public void setShorePriority(boolean shoreFirst) {
    this.shorePriority = shoreFirst;
  }

  /**
   * Configure the waste-heat recovery and an optional steam bottoming cycle.
   *
   * @param bottomingYield electric output per unit of recovered heat consumed by the bottoming cycle (0 = no bottoming
   * cycle; about 0.25 for a steam cycle on GT exhaust)
   * @param stackTemperatureK gas outlet temperature of the WHRU [K]
   * @param effectiveness fraction of the exhaust heat above the stack temperature that is recovered (0 to 1)
   */
  public void setWasteHeatRecovery(double bottomingYield, double stackTemperatureK, double effectiveness) {
    this.bottomingYield = Math.max(0.0, bottomingYield);
    this.whruStackTemperatureK = stackTemperatureK;
    this.whruEffectiveness = Math.max(0.0, Math.min(1.0, effectiveness));
  }

  /**
   * Get the generator sets.
   *
   * @return unmodifiable list of gas turbine units
   */
  public List<GasTurbineUnit> getGenerators() {
    return Collections.unmodifiableList(generators);
  }

  /**
   * Electric capacity of the gas-turbine generators at an ambient temperature.
   *
   * @param ambientTemperatureK ambient temperature [K]
   * @return electric capacity at the bus [W]
   */
  public double getGeneratorCapacityW(double ambientTemperatureK) {
    double sum = 0.0;
    for (int i = 0; i < generators.size(); i++) {
      sum += generators.get(i).getAvailablePowerAtAmbient(ambientTemperatureK)
          * generatorEfficiencies.get(i).doubleValue();
    }
    return sum;
  }

  /**
   * Dispatch the power system for one ambient temperature, electric demand and heat demand.
   *
   * @param ambientTemperatureK ambient temperature [K]
   * @param electricDemandW electric demand at the bus [W]
   * @param heatDemandW process heat demand to be covered by the WHRU [W]
   * @return the dispatch result
   */
  public Result evaluate(double ambientTemperatureK, double electricDemandW, double heatDemandW) {
    int n = generators.size();
    double[] avail = new double[n];
    double capacity = 0.0;
    double largest = 0.0;
    for (int i = 0; i < n; i++) {
      GasTurbineUnit g = generators.get(i);
      g.setAmbientTemperatureK(ambientTemperatureK);
      avail[i] = g.getAvailablePowerAtAmbient(ambientTemperatureK) * generatorEfficiencies.get(i).doubleValue();
      capacity += avail[i];
      largest = Math.max(largest, avail[i]);
    }
    double demand = Math.max(0.0, electricDemandW);
    double shore;
    if (shorePriority) {
      shore = Math.min(demand, shoreLimitW);
    } else {
      shore = Math.min(Math.max(0.0, demand - capacity), shoreLimitW);
    }
    double gtDemand = demand - shore;
    double bottoming = 0.0;
    double whru = 0.0;
    double[] loads = new double[n];
    double fuel = 0.0;
    double co2 = 0.0;
    double supplied = 0.0;
    for (int pass = 0; pass < 6; pass++) {
      double gtNet = Math.max(0.0, gtDemand - bottoming);
      double gtDelivered = 0.0;
      whru = 0.0;
      fuel = 0.0;
      co2 = 0.0;
      for (int i = 0; i < n; i++) {
        GasTurbineUnit g = generators.get(i);
        double eff = generatorEfficiencies.get(i).doubleValue();
        double shareElectric = capacity > 0.0 ? gtNet * avail[i] / capacity : 0.0;
        g.setDemandedPower(shareElectric / eff);
        g.run();
        loads[i] = g.getLoadFraction();
        gtDelivered += Math.min(shareElectric, avail[i]);
        whru += g.getWasteHeatRecoveryDutyW(whruStackTemperatureK, whruEffectiveness);
        fuel += g.getFuelMassFlowKgPerHr();
        co2 += g.getCO2EmissionKgPerHr();
      }
      supplied = gtDelivered;
      double newBottoming = bottomingYield * whru;
      if (Math.abs(newBottoming - bottoming) < 1.0) {
        bottoming = newBottoming;
        break;
      }
      bottoming = newBottoming;
    }
    double bottomingHeat = bottomingYield > 0.0 ? bottoming / bottomingYield : 0.0;
    double processHeat = Math.max(0.0, whru - bottomingHeat);
    double deficit = Math.max(0.0, demand - shore - supplied - bottoming);
    double totalCapacity = capacity + shoreLimitW;
    List<String> names = new ArrayList<String>();
    for (GasTurbineUnit g : generators) {
      names.add(g.getName());
    }
    return new Result(ambientTemperatureK, demand, shore, supplied, bottoming, deficit, capacity, shoreLimitW,
        totalCapacity > 0.0 ? demand / totalCapacity : 0.0, totalCapacity - largest - demand, whru, processHeat,
        Math.max(0.0, heatDemandW - processHeat), fuel, co2, names, loads);
  }

  /**
   * Dispatch the power system over a set of ambient temperatures at constant demand.
   *
   * @param ambientTemperaturesK ambient temperatures [K]
   * @param electricDemandW electric demand at the bus [W]
   * @param heatDemandW process heat demand [W]
   * @return one result per ambient temperature
   */
  public List<Result> evaluateAcrossAmbient(double[] ambientTemperaturesK, double electricDemandW, double heatDemandW) {
    List<Result> out = new ArrayList<Result>();
    for (double t : ambientTemperaturesK) {
      out.add(evaluate(t, electricDemandW, heatDemandW));
    }
    return out;
  }

  /**
   * Result of one dispatch.
   *
   * @author NeqSim Development Team
   * @version 1.0
   */
  public static class Result implements Serializable {
    /** Serialization version UID. */
    private static final long serialVersionUID = 1L;

    private final double ambientTemperatureK;
    private final double demandW;
    private final double shoreW;
    private final double gasTurbineElectricW;
    private final double bottomingW;
    private final double deficitW;
    private final double generatorCapacityW;
    private final double shoreCapacityW;
    private final double utilisation;
    private final double n1MarginW;
    private final double whruDutyW;
    private final double processHeatAvailableW;
    private final double heatShortfallW;
    private final double fuelKgPerHr;
    private final double co2KgPerHr;
    private final List<String> unitNames;
    private final double[] unitLoadFractions;

    /**
     * Constructor.
     *
     * @param ambientK ambient temperature [K]
     * @param demandW electric demand [W]
     * @param shoreW imported power used [W]
     * @param gtW gas-turbine generator output at the bus [W]
     * @param bottomingW bottoming-cycle output [W]
     * @param deficitW unserved demand [W]
     * @param capacityW generator capacity at the bus [W]
     * @param shoreCapacityW import limit [W]
     * @param utilisation demand over total capacity
     * @param n1MarginW capacity without the largest generator minus demand [W]
     * @param whruW recovered exhaust heat [W]
     * @param processHeatW heat available to the process after the bottoming cycle [W]
     * @param heatShortfallW unserved heat demand [W]
     * @param fuelKgPerHr fuel flow of all generators [kg/h]
     * @param co2KgPerHr CO2 emission of all generators [kg/h]
     * @param unitNames generator names
     * @param unitLoads generator load fractions
     */
    Result(double ambientK, double demandW, double shoreW, double gtW, double bottomingW, double deficitW,
        double capacityW, double shoreCapacityW, double utilisation, double n1MarginW, double whruW,
        double processHeatW, double heatShortfallW, double fuelKgPerHr, double co2KgPerHr, List<String> unitNames,
        double[] unitLoads) {
      this.ambientTemperatureK = ambientK;
      this.demandW = demandW;
      this.shoreW = shoreW;
      this.gasTurbineElectricW = gtW;
      this.bottomingW = bottomingW;
      this.deficitW = deficitW;
      this.generatorCapacityW = capacityW;
      this.shoreCapacityW = shoreCapacityW;
      this.utilisation = utilisation;
      this.n1MarginW = n1MarginW;
      this.whruDutyW = whruW;
      this.processHeatAvailableW = processHeatW;
      this.heatShortfallW = heatShortfallW;
      this.fuelKgPerHr = fuelKgPerHr;
      this.co2KgPerHr = co2KgPerHr;
      this.unitNames = new ArrayList<String>(unitNames);
      this.unitLoadFractions = unitLoads.clone();
    }

    /**
     * Get the ambient temperature.
     *
     * @return ambient temperature [K]
     */
    public double getAmbientTemperatureK() {
      return ambientTemperatureK;
    }

    /**
     * Get the electric demand.
     *
     * @return demand [W]
     */
    public double getDemandW() {
      return demandW;
    }

    /**
     * Get the imported (shore) power used.
     *
     * @return power [W]
     */
    public double getShorePowerW() {
      return shoreW;
    }

    /**
     * Get the gas-turbine generator output at the bus.
     *
     * @return power [W]
     */
    public double getGasTurbineElectricW() {
      return gasTurbineElectricW;
    }

    /**
     * Get the bottoming-cycle electric output.
     *
     * @return power [W]
     */
    public double getBottomingCyclePowerW() {
      return bottomingW;
    }

    /**
     * Get the unserved electric demand.
     *
     * @return power deficit [W], 0 when the demand is covered
     */
    public double getDeficitW() {
      return deficitW;
    }

    /**
     * Whether the power system cannot cover the demand.
     *
     * @return true if there is a deficit
     */
    public boolean isCapacityLimited() {
      return deficitW > 1.0;
    }

    /**
     * Get the gas-turbine generator capacity at the ambient temperature.
     *
     * @return capacity at the bus [W]
     */
    public double getGeneratorCapacityW() {
      return generatorCapacityW;
    }

    /**
     * Get the import limit.
     *
     * @return limit [W]
     */
    public double getShoreCapacityW() {
      return shoreCapacityW;
    }

    /**
     * Get the utilisation of the total capacity.
     *
     * @return demand over generator plus import capacity
     */
    public double getUtilisation() {
      return utilisation;
    }

    /**
     * Get the N-1 margin: capacity with the largest generator out, minus the demand.
     *
     * @return margin [W], negative when N-1 is not met
     */
    public double getN1MarginW() {
      return n1MarginW;
    }

    /**
     * Get the recovered exhaust heat.
     *
     * @return WHRU duty [W]
     */
    public double getWhruDutyW() {
      return whruDutyW;
    }

    /**
     * Get the heat available to the process after the bottoming cycle.
     *
     * @return heat [W]
     */
    public double getProcessHeatAvailableW() {
      return processHeatAvailableW;
    }

    /**
     * Get the unserved heat demand.
     *
     * @return heat shortfall [W]
     */
    public double getHeatShortfallW() {
      return heatShortfallW;
    }

    /**
     * Get the total fuel flow of the generators.
     *
     * @return fuel [kg/h]
     */
    public double getFuelKgPerHr() {
      return fuelKgPerHr;
    }

    /**
     * Get the total CO2 emission of the generators.
     *
     * @return CO2 [kg/h]
     */
    public double getCo2KgPerHr() {
      return co2KgPerHr;
    }

    /**
     * Get the load fraction of a generator.
     *
     * @param index generator index in the order they were added
     * @return load fraction of the site rating
     */
    public double getUnitLoadFraction(int index) {
      return unitLoadFractions[index];
    }

    /**
     * Serialize to compact JSON.
     *
     * @return JSON string
     */
    public String toJson() {
      StringBuilder sb = new StringBuilder();
      sb.append("{\"ambientTemperatureK\":").append(ambientTemperatureK);
      sb.append(",\"demandW\":").append(demandW).append(",\"shorePowerW\":").append(shoreW);
      sb.append(",\"gasTurbineElectricW\":").append(gasTurbineElectricW);
      sb.append(",\"bottomingCyclePowerW\":").append(bottomingW).append(",\"deficitW\":").append(deficitW);
      sb.append(",\"capacityLimited\":").append(isCapacityLimited());
      sb.append(",\"generatorCapacityW\":").append(generatorCapacityW);
      sb.append(",\"shoreCapacityW\":").append(shoreCapacityW).append(",\"utilisation\":").append(utilisation);
      sb.append(",\"n1MarginW\":").append(n1MarginW).append(",\"whruDutyW\":").append(whruDutyW);
      sb.append(",\"processHeatAvailableW\":").append(processHeatAvailableW);
      sb.append(",\"heatShortfallW\":").append(heatShortfallW);
      sb.append(",\"fuelKgPerHr\":").append(fuelKgPerHr).append(",\"co2KgPerHr\":").append(co2KgPerHr);
      sb.append(",\"units\":[");
      for (int i = 0; i < unitNames.size(); i++) {
        if (i > 0) {
          sb.append(',');
        }
        sb.append("{\"name\":\"").append(unitNames.get(i).replace("\"", "'")).append("\",\"loadFraction\":")
            .append(unitLoadFractions[i]).append('}');
      }
      return sb.append("]}").toString();
    }
  }
}
