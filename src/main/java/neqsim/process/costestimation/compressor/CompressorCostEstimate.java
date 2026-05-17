package neqsim.process.costestimation.compressor;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign;

/**
 * Cost estimation class for compressors.
 *
 * <p>
 * This class provides compressor-specific cost estimation methods using chemical engineering cost
 * correlations for centrifugal, reciprocating, and screw compressors. Includes driver costs for
 * electric motors, gas turbines, and steam turbines.
 * </p>
 *
 * @author ESOL
 * @version 2.0
 */
public class CompressorCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Compressor type. */
  private String compressorType = "centrifugal";

  /** Driver type. */
  private String driverType = "electric-motor";

  /** Include driver in cost. */
  private boolean includeDriver = true;

  /** Number of stages. */
  private int numberOfStages = 1;

  /** Include intercoolers. */
  private boolean includeIntercoolers = false;

  /**
   * Constructor for CompressorCostEstimate.
   *
   * @param mechanicalEquipment a
   *        {@link neqsim.process.mechanicaldesign.compressor.CompressorMechanicalDesign} object
   */
  public CompressorCostEstimate(CompressorMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("compressor");
  }

  /**
   * Set the compressor type.
   *
   * @param type compressor type ("centrifugal", "reciprocating", "screw", "axial")
   */
  public void setCompressorType(String type) {
    this.compressorType = type;
  }

  /**
   * Get the compressor type.
   *
   * @return compressor type
   */
  public String getCompressorType() {
    return compressorType;
  }

  /**
   * Set the driver type.
   *
   * @param type driver type ("electric-motor", "gas-turbine", "steam-turbine", "gas-engine")
   */
  public void setDriverType(String type) {
    this.driverType = type;
  }

  /**
   * Get the driver type.
   *
   * @return driver type
   */
  public String getDriverType() {
    return driverType;
  }

  /**
   * Set whether to include driver cost.
   *
   * @param include true to include driver
   */
  public void setIncludeDriver(boolean include) {
    this.includeDriver = include;
  }

  /**
   * Set number of compression stages.
   *
   * @param stages number of stages
   */
  public void setNumberOfStages(int stages) {
    this.numberOfStages = stages;
  }

  /**
   * Set whether to include intercoolers.
   *
   * @param include true to include intercoolers
   */
  public void setIncludeIntercoolers(boolean include) {
    this.includeIntercoolers = include;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    CompressorMechanicalDesign compMecDesign = (CompressorMechanicalDesign) mechanicalEquipment;

    // Get compressor power
    double power = compMecDesign.getPower(); // kW
    if (power <= 0) {
      // Fallback to weight-based estimate
      return mechanicalEquipment.getWeightTotal() * 20.0;
    }

    // Calculate compressor cost
    double compressorCost;
    if ("reciprocating".equalsIgnoreCase(compressorType)) {
      compressorCost = getCostCalculator().calcReciprocatingCompressorCost(power);
    } else if ("screw".equalsIgnoreCase(compressorType)) {
      compressorCost = calcScrewCompressorCost(power);
    } else if ("axial".equalsIgnoreCase(compressorType)) {
      compressorCost = calcAxialCompressorCost(power);
    } else {
      // Default centrifugal
      compressorCost = getCostCalculator().calcCentrifugalCompressorCost(power);
    }

    // Apply multi-stage factor
    if (numberOfStages > 1) {
      compressorCost *= (1.0 + (numberOfStages - 1) * 0.25);
    }

    // Add driver cost if included
    if (includeDriver) {
      compressorCost += calcDriverCost(power);
    }

    // Add intercooler cost if included
    if (includeIntercoolers && numberOfStages > 1) {
      compressorCost += calcIntercoolersCost(power, numberOfStages - 1);
    }

    return compressorCost;
  }

  /**
   * Calculate screw compressor cost.
   *
   * @param power power in kW
   * @return cost in USD
   */
  private double calcScrewCompressorCost(double power) {
    // Screw compressors: typically 0.8x centrifugal cost
    return getCostCalculator().calcCentrifugalCompressorCost(power) * 0.8;
  }

  /**
   * Calculate axial compressor cost.
   *
   * @param power power in kW
   * @return cost in USD
   */
  private double calcAxialCompressorCost(double power) {
    // Axial compressors: typically 1.3x centrifugal cost (for same power)
    return getCostCalculator().calcCentrifugalCompressorCost(power) * 1.3;
  }

  /**
   * Calculate driver cost.
   *
   * @param power shaft power required in kW
   * @return driver cost in USD
   */
  private double calcDriverCost(double power) {
    if (power <= 0) {
      return 0.0;
    }

    double cepciRatio = getCostCalculator().getCurrentCepci() / 607.5;

    if ("gas-turbine".equalsIgnoreCase(driverType)) {
      // Gas turbine: Cp = 4000 * P^0.8
      return 4000.0 * Math.pow(power, 0.8) * cepciRatio;
    } else if ("steam-turbine".equalsIgnoreCase(driverType)) {
      // Steam turbine: Cp = 2000 * P^0.75
      return 2000.0 * Math.pow(power, 0.75) * cepciRatio;
    } else if ("gas-engine".equalsIgnoreCase(driverType)) {
      // Gas engine: Cp = 500 * P^0.85
      return 500.0 * Math.pow(power, 0.85) * cepciRatio;
    } else {
      // Electric motor (default): Cp = 300 * P^0.75
      double motorEfficiency = 0.95;
      double electricPower = power / motorEfficiency;
      return 300.0 * Math.pow(electricPower, 0.75) * cepciRatio;
    }
  }

  /**
   * Calculate intercoolers cost.
   *
   * @param power compressor power in kW
   * @param numberOfIntercoolers number of intercoolers
   * @return total intercoolers cost in USD
   */
  private double calcIntercoolersCost(double power, int numberOfIntercoolers) {
    // Estimate intercooler heat duty as ~70% of stage power
    // Typical U = 500 W/m2K, deltaT = 30K
    double heatDuty = power * 0.7 * 1000; // W per intercooler
    double area = heatDuty / (500.0 * 30.0); // m2

    // Shell-tube heat exchanger cost
    double costPerIntercooler = getCostCalculator().calcShellTubeHeatExchangerCost(area);
    return costPerIntercooler * numberOfIntercoolers;
  }

  /**
   * Calculate annual operating cost (electricity or fuel).
   *
   * @param hoursPerYear operating hours per year
   * @param electricityRate electricity rate in $/kWh (for electric motor)
   * @param fuelRate fuel rate in $/GJ (for gas turbine/engine)
   * @return annual operating cost in USD
   */
  public double calcAnnualOperatingCost(double hoursPerYear, double electricityRate,
      double fuelRate) {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    CompressorMechanicalDesign compMecDesign = (CompressorMechanicalDesign) mechanicalEquipment;
    double power = compMecDesign.getPower(); // kW

    if ("gas-turbine".equalsIgnoreCase(driverType)) {
      // Gas turbine efficiency ~30%
      double fuelPower = power / 0.30; // kW
      double fuelEnergyGJ = fuelPower * hoursPerYear * 3600 / 1e9; // GJ
      return fuelEnergyGJ * fuelRate;
    } else if ("gas-engine".equalsIgnoreCase(driverType)) {
      // Gas engine efficiency ~38%
      double fuelPower = power / 0.38;
      double fuelEnergyGJ = fuelPower * hoursPerYear * 3600 / 1e9;
      return fuelEnergyGJ * fuelRate;
    } else if ("steam-turbine".equalsIgnoreCase(driverType)) {
      // Steam turbine - cost depends on steam source
      // Assume 3 kg steam per kWh at $10/tonne
      double steamKg = power * hoursPerYear * 3.0;
      return steamKg * 10.0 / 1000.0;
    } else {
      // Electric motor
      double motorEfficiency = 0.95;
      double electricPower = power / motorEfficiency;
      return electricPower * hoursPerYear * electricityRate;
    }
  }

  /**
   * Calculate maintenance cost per year (typically 3-6% of capital).
   *
   * @return annual maintenance cost in USD
   */
  public double calcAnnualMaintenanceCost() {
    if (purchasedEquipmentCost <= 0) {
      calculateCostEstimate();
    }

    // Reciprocating compressors have higher maintenance
    double maintenanceFactor = "reciprocating".equalsIgnoreCase(compressorType) ? 0.06 : 0.04;
    return purchasedEquipmentCost * maintenanceFactor;
  }

  /** {@inheritDoc} */
  @Override
  public double getTotalCost() {
    if (totalModuleCost > 0) {
      return totalModuleCost;
    }

    if (purchasedEquipmentCost <= 0) {
      calculateCostEstimate();
    }

    return totalModuleCost;
  }

  /**
   * Get cost breakdown as map.
   *
   * @return map with cost breakdown
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    calculateCostEstimate();

    breakdown.put("compressorType", compressorType);
    breakdown.put("driverType", driverType);
    breakdown.put("numberOfStages", numberOfStages);
    breakdown.put("includeDriver", includeDriver);
    breakdown.put("includeIntercoolers", includeIntercoolers);
    breakdown.put("purchasedEquipmentCost_USD", purchasedEquipmentCost);
    breakdown.put("bareModuleCost_USD", bareModuleCost);
    breakdown.put("totalModuleCost_USD", totalModuleCost);
    breakdown.put("installationManHours", installationManHours);

    if (mechanicalEquipment != null) {
      CompressorMechanicalDesign compMecDesign = (CompressorMechanicalDesign) mechanicalEquipment;
      breakdown.put("power_kW", compMecDesign.getPower());
      breakdown.put("weight_kg", compMecDesign.getWeightTotal());
    }

    return breakdown;
  }
}
