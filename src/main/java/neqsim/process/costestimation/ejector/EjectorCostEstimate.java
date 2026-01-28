package neqsim.process.costestimation.ejector;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.ejector.EjectorMechanicalDesign;

/**
 * Cost estimation class for ejectors.
 *
 * <p>
 * This class provides ejector-specific cost estimation methods for steam ejectors, gas ejectors,
 * and vacuum systems used in process applications.
 * </p>
 *
 * <p>
 * Correlations are based on:
 * </p>
 * <ul>
 * <li>Heat Exchange Institute (HEI) standards</li>
 * <li>Turton et al. - Analysis, Synthesis and Design of Chemical Processes</li>
 * <li>Vendor quotes and industry data</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class EjectorCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Ejector type. */
  private String ejectorType = "steam";

  /** Number of stages. */
  private int numberOfStages = 1;

  /** Suction pressure in mbar abs. */
  private double suctionPressure = 100.0;

  /** Discharge pressure in bara. */
  private double dischargePressure = 1.013;

  /** Suction capacity in kg/hr. */
  private double suctionCapacity = 100.0;

  /** Motive steam pressure in bara. */
  private double motivePressure = 10.0;

  /** Include intercondensers. */
  private boolean includeIntercondensers = false;

  /** Include aftercondenser. */
  private boolean includeAftercondenser = true;

  /**
   * Constructor for EjectorCostEstimate.
   *
   * @param mechanicalEquipment the ejector mechanical design
   */
  public EjectorCostEstimate(EjectorMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("ejector");
  }

  /**
   * Set ejector type.
   *
   * @param type ejector type ("steam", "gas", "liquid", "hybrid")
   */
  public void setEjectorType(String type) {
    this.ejectorType = type;
  }

  /**
   * Get ejector type.
   *
   * @return ejector type
   */
  public String getEjectorType() {
    return ejectorType;
  }

  /**
   * Set number of stages.
   *
   * @param stages number of stages
   */
  public void setNumberOfStages(int stages) {
    this.numberOfStages = stages;
  }

  /**
   * Get number of stages.
   *
   * @return number of stages
   */
  public int getNumberOfStages() {
    return numberOfStages;
  }

  /**
   * Set suction pressure.
   *
   * @param pressure suction pressure in mbar abs
   */
  public void setSuctionPressure(double pressure) {
    this.suctionPressure = pressure;
  }

  /**
   * Set discharge pressure.
   *
   * @param pressure discharge pressure in bara
   */
  public void setDischargePressure(double pressure) {
    this.dischargePressure = pressure;
  }

  /**
   * Set suction capacity.
   *
   * @param capacity suction capacity in kg/hr
   */
  public void setSuctionCapacity(double capacity) {
    this.suctionCapacity = capacity;
  }

  /**
   * Set motive steam pressure.
   *
   * @param pressure motive pressure in bara
   */
  public void setMotivePressure(double pressure) {
    this.motivePressure = pressure;
  }

  /**
   * Set whether to include intercondensers.
   *
   * @param include true to include intercondensers
   */
  public void setIncludeIntercondensers(boolean include) {
    this.includeIntercondensers = include;
  }

  /**
   * Set whether to include aftercondenser.
   *
   * @param include true to include aftercondenser
   */
  public void setIncludeAftercondenser(boolean include) {
    this.includeAftercondenser = include;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    // Calculate ejector cost based on type and stages
    double ejectorCost;
    if ("gas".equalsIgnoreCase(ejectorType)) {
      ejectorCost = calcGasEjectorCost();
    } else if ("liquid".equalsIgnoreCase(ejectorType)) {
      ejectorCost = calcLiquidEjectorCost();
    } else if ("hybrid".equalsIgnoreCase(ejectorType)) {
      ejectorCost = calcHybridEjectorCost();
    } else {
      // Default steam ejector
      ejectorCost = calcSteamEjectorCost();
    }

    // Apply material factor
    ejectorCost *= getMaterialFactor();

    // Add intercondensers
    if (includeIntercondensers && numberOfStages > 1) {
      ejectorCost += calcIntercondensersCost();
    }

    // Add aftercondenser
    if (includeAftercondenser) {
      ejectorCost += calcAftercondenserCost();
    }

    return ejectorCost;
  }

  /**
   * Calculate steam ejector cost.
   *
   * @return cost in USD
   */
  private double calcSteamEjectorCost() {
    // Steam ejector cost based on suction capacity and compression ratio
    // Cp = K * Q^0.6 * CR^0.3
    double compressionRatio = (dischargePressure * 1000) / suctionPressure;
    double k = 500.0; // Base constant

    // Adjust for number of stages
    double stageFactor = 1.0 + (numberOfStages - 1) * 0.7;

    double baseCost = k * Math.pow(Math.max(suctionCapacity, 10.0), 0.6)
        * Math.pow(compressionRatio, 0.3) * stageFactor;

    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate gas ejector cost.
   *
   * @return cost in USD
   */
  private double calcGasEjectorCost() {
    // Gas ejectors are typically more expensive than steam
    return calcSteamEjectorCost() * 1.3;
  }

  /**
   * Calculate liquid ejector cost.
   *
   * @return cost in USD
   */
  private double calcLiquidEjectorCost() {
    // Liquid ejectors (eductors) are simpler
    double baseCost = 300.0 * Math.pow(Math.max(suctionCapacity, 10.0), 0.5);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate hybrid ejector cost.
   *
   * @return cost in USD
   */
  private double calcHybridEjectorCost() {
    // Hybrid systems (ejector + liquid ring pump)
    return calcSteamEjectorCost() + calcLiquidRingPumpCost();
  }

  /**
   * Calculate liquid ring pump cost (for hybrid systems).
   *
   * @return cost in USD
   */
  private double calcLiquidRingPumpCost() {
    // Liquid ring vacuum pump
    double baseCost = 800.0 * Math.pow(Math.max(suctionCapacity, 10.0), 0.55);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate intercondensers cost.
   *
   * @return intercondensers cost in USD
   */
  private double calcIntercondensersCost() {
    // One intercondenser between each stage
    int numIntercondensers = numberOfStages - 1;

    // Estimate condenser area from capacity
    double area = suctionCapacity * 0.1; // Rough estimate m2

    // Use shell-tube heat exchanger correlation
    double condenserCost = getCostCalculator().calcShellTubeHeatExchangerCost(area);

    return numIntercondensers * condenserCost * getMaterialFactor();
  }

  /**
   * Calculate aftercondenser cost.
   *
   * @return aftercondenser cost in USD
   */
  private double calcAftercondenserCost() {
    // Final condenser to recover motive steam
    double area = suctionCapacity * 0.15; // Rough estimate m2
    return getCostCalculator().calcShellTubeHeatExchangerCost(area) * getMaterialFactor();
  }

  /**
   * Calculate annual operating cost.
   *
   * @param operatingHoursPerYear operating hours per year
   * @param steamCostPerTonne steam cost in $/tonne
   * @param coolingWaterCostPerM3 cooling water cost in $/m3
   * @return annual operating cost in USD
   */
  public double calcAnnualOperatingCost(int operatingHoursPerYear, double steamCostPerTonne,
      double coolingWaterCostPerM3) {
    double operatingCost = 0.0;

    // Steam consumption (estimate based on entrainment ratio)
    if ("steam".equalsIgnoreCase(ejectorType)) {
      // Steam consumption ~5-10 times suction load
      double compressionRatio = (dischargePressure * 1000) / suctionPressure;
      double steamFactor = 3.0 + 2.0 * Math.log(compressionRatio);
      double steamConsumption = suctionCapacity * steamFactor; // kg/hr

      operatingCost += steamConsumption * operatingHoursPerYear * steamCostPerTonne / 1000.0;
    }

    // Cooling water for condensers
    if (includeIntercondensers || includeAftercondenser) {
      // Estimate cooling water from heat duty
      double heatDuty = suctionCapacity * 2.5; // kW (rough estimate)
      double cwFlow = heatDuty * 3600 / (4.18 * 10) / 1000; // m3/hr (10°C rise)
      operatingCost += cwFlow * operatingHoursPerYear * coolingWaterCostPerM3;
    }

    // Maintenance: ~2% of PEC
    operatingCost += getPurchasedEquipmentCost() * 0.02;

    return operatingCost;
  }

  /**
   * Get cost breakdown by component.
   *
   * @return map of cost components
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    breakdown.put("ejectorType", ejectorType);
    breakdown.put("numberOfStages", numberOfStages);
    breakdown.put("suctionPressure_mbar", suctionPressure);
    breakdown.put("dischargePressure_bara", dischargePressure);
    breakdown.put("suctionCapacity_kg_hr", suctionCapacity);
    breakdown.put("compressionRatio", (dischargePressure * 1000) / suctionPressure);

    // Calculate individual costs
    double ejectorCost;
    if ("gas".equalsIgnoreCase(ejectorType)) {
      ejectorCost = calcGasEjectorCost();
    } else if ("liquid".equalsIgnoreCase(ejectorType)) {
      ejectorCost = calcLiquidEjectorCost();
    } else if ("hybrid".equalsIgnoreCase(ejectorType)) {
      ejectorCost = calcHybridEjectorCost();
    } else {
      ejectorCost = calcSteamEjectorCost();
    }

    breakdown.put("ejectorCost_USD", ejectorCost * getMaterialFactor());

    if (includeIntercondensers && numberOfStages > 1) {
      breakdown.put("intercondensersCost_USD", calcIntercondensersCost());
    }
    if (includeAftercondenser) {
      breakdown.put("aftercondenserCost_USD", calcAftercondenserCost());
    }

    breakdown.put("materialFactor", getMaterialFactor());
    breakdown.put("totalPurchasedCost_USD", getPurchasedEquipmentCost());

    return breakdown;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> result = super.toMap();
    result.put("ejectorCostBreakdown", getCostBreakdown());
    return result;
  }
}
