package neqsim.process.costestimation.expander;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.expander.ExpanderMechanicalDesign;

/**
 * Cost estimation class for turboexpanders.
 *
 * <p>
 * This class provides expander-specific cost estimation methods using chemical engineering cost
 * correlations for radial inflow and axial turboexpanders used in gas processing, cryogenic, and
 * power recovery applications.
 * </p>
 *
 * <p>
 * Correlations are based on:
 * </p>
 * <ul>
 * <li>Turton et al. - Analysis, Synthesis and Design of Chemical Processes</li>
 * <li>API 617 - Axial and Centrifugal Compressors and Expander-compressors</li>
 * <li>Industry data for turboexpander costs</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class ExpanderCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Expander type. */
  private String expanderType = "radial-inflow";

  /** Load type (generator, compressor, brake). */
  private String loadType = "generator";

  /** Include load in cost. */
  private boolean includeLoad = true;

  /** Include gearbox. */
  private boolean includeGearbox = false;

  /** Include lube oil system. */
  private boolean includeLubeOilSystem = true;

  /** Include control system. */
  private boolean includeControlSystem = true;

  /** Inlet temperature in K. */
  private double inletTemperature = 300.0;

  /** Is cryogenic service (below -40°C). */
  private boolean cryogenicService = false;

  /** Manual shaft power in kW (used when no MechanicalDesign available). */
  private double manualShaftPower = 0.0;

  /**
   * Constructor for ExpanderCostEstimate.
   *
   * @param mechanicalEquipment the expander mechanical design
   */
  public ExpanderCostEstimate(ExpanderMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("expander");
  }

  /**
   * Set shaft power manually (for standalone cost estimation without MechanicalDesign).
   *
   * @param power shaft power in kW
   */
  public void setShaftPower(double power) {
    this.manualShaftPower = power;
  }

  /**
   * Get shaft power.
   *
   * @return shaft power in kW
   */
  public double getShaftPower() {
    if (mechanicalEquipment != null) {
      ExpanderMechanicalDesign expMecDesign = (ExpanderMechanicalDesign) mechanicalEquipment;
      double power = expMecDesign.getPower();
      if (power > 0) {
        return power;
      }
    }
    return manualShaftPower;
  }

  /**
   * Set expander type.
   *
   * @param type expander type ("radial-inflow", "axial", "mixed-flow")
   */
  public void setExpanderType(String type) {
    this.expanderType = type;
  }

  /**
   * Get expander type.
   *
   * @return expander type
   */
  public String getExpanderType() {
    return expanderType;
  }

  /**
   * Set load type.
   *
   * @param type load type ("generator", "compressor", "brake")
   */
  public void setLoadType(String type) {
    this.loadType = type;
  }

  /**
   * Get load type.
   *
   * @return load type
   */
  public String getLoadType() {
    return loadType;
  }

  /**
   * Set whether to include load.
   *
   * @param include true to include load cost
   */
  public void setIncludeLoad(boolean include) {
    this.includeLoad = include;
  }

  /**
   * Set whether to include gearbox.
   *
   * @param include true to include gearbox
   */
  public void setIncludeGearbox(boolean include) {
    this.includeGearbox = include;
  }

  /**
   * Set whether to include lube oil system.
   *
   * @param include true to include lube oil system
   */
  public void setIncludeLubeOilSystem(boolean include) {
    this.includeLubeOilSystem = include;
  }

  /**
   * Set whether to include control system.
   *
   * @param include true to include control system
   */
  public void setIncludeControlSystem(boolean include) {
    this.includeControlSystem = include;
  }

  /**
   * Set inlet temperature.
   *
   * @param temp inlet temperature in K
   */
  public void setInletTemperature(double temp) {
    this.inletTemperature = temp;
    this.cryogenicService = temp < 233.15; // Below -40°C
  }

  /**
   * Set cryogenic service flag.
   *
   * @param cryogenic true for cryogenic service
   */
  public void setCryogenicService(boolean cryogenic) {
    this.cryogenicService = cryogenic;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    // Get expander power (from mechanical design or manual setting)
    double power = getShaftPower();
    if (power <= 0) {
      if (mechanicalEquipment != null && mechanicalEquipment.getWeightTotal() > 0) {
        return mechanicalEquipment.getWeightTotal() * 30.0; // Fallback based on weight
      }
      return 0.0;
    }

    // Calculate expander cost
    double expanderCost;
    if ("axial".equalsIgnoreCase(expanderType)) {
      expanderCost = calcAxialExpanderCost(power);
    } else if ("mixed-flow".equalsIgnoreCase(expanderType)) {
      expanderCost = calcMixedFlowExpanderCost(power);
    } else {
      // Default radial inflow
      expanderCost = calcRadialInflowExpanderCost(power);
    }

    // Apply cryogenic factor
    if (cryogenicService) {
      expanderCost *= 1.4; // Cryogenic materials add ~40%
    }

    // Apply material factor
    expanderCost *= getMaterialFactor();

    // Add load cost
    if (includeLoad) {
      expanderCost += calcLoadCost(power);
    }

    // Add gearbox
    if (includeGearbox) {
      expanderCost += calcGearboxCost(power);
    }

    // Add lube oil system
    if (includeLubeOilSystem) {
      expanderCost += calcLubeOilSystemCost(power);
    }

    // Add control system
    if (includeControlSystem) {
      expanderCost += calcControlSystemCost(power);
    }

    return expanderCost;
  }

  /**
   * Calculate radial inflow expander cost.
   *
   * @param power shaft power in kW
   * @return cost in USD
   */
  private double calcRadialInflowExpanderCost(double power) {
    // Turboexpander cost correlation: Cp = 3000 * P^0.75
    // For radial inflow turbines (most common)
    double baseCost = 3000.0 * Math.pow(Math.max(power, 50.0), 0.75);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate axial expander cost.
   *
   * @param power shaft power in kW
   * @return cost in USD
   */
  private double calcAxialExpanderCost(double power) {
    // Axial turbines: typically 1.2x radial cost (more stages)
    return calcRadialInflowExpanderCost(power) * 1.2;
  }

  /**
   * Calculate mixed flow expander cost.
   *
   * @param power shaft power in kW
   * @return cost in USD
   */
  private double calcMixedFlowExpanderCost(double power) {
    // Mixed flow: between radial and axial
    return calcRadialInflowExpanderCost(power) * 1.1;
  }

  /**
   * Calculate load (generator/compressor/brake) cost.
   *
   * @param power shaft power in kW
   * @return cost in USD
   */
  private double calcLoadCost(double power) {
    double cepciRatio = getCostCalculator().getCurrentCepci() / 607.5;

    if ("generator".equalsIgnoreCase(loadType)) {
      // Generator cost: Cp = 400 * P^0.7
      return 400.0 * Math.pow(power, 0.7) * cepciRatio;
    } else if ("compressor".equalsIgnoreCase(loadType)) {
      // Expander-compressor: compressor end cost similar to centrifugal compressor
      return getCostCalculator().calcCentrifugalCompressorCost(power);
    } else {
      // Brake (oil brake or hydraulic)
      return 150.0 * Math.pow(power, 0.6) * cepciRatio;
    }
  }

  /**
   * Calculate gearbox cost.
   *
   * @param power transmitted power in kW
   * @return cost in USD
   */
  private double calcGearboxCost(double power) {
    // Gearbox cost: Cp = 200 * P^0.65
    double baseCost = 200.0 * Math.pow(Math.max(power, 10.0), 0.65);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate lube oil system cost.
   *
   * @param power expander power in kW (determines system size)
   * @return cost in USD
   */
  private double calcLubeOilSystemCost(double power) {
    // Lube oil system: ~5% of expander cost
    return calcRadialInflowExpanderCost(power) * 0.05;
  }

  /**
   * Calculate control system cost.
   *
   * @param power expander power in kW
   * @return cost in USD
   */
  private double calcControlSystemCost(double power) {
    // Control system: base cost + power-dependent
    double baseCost = 15000.0 + 10.0 * power;
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate annual operating cost.
   *
   * @param operatingHoursPerYear operating hours per year
   * @return annual operating cost in USD
   */
  public double calcAnnualOperatingCost(int operatingHoursPerYear) {
    // Expander maintenance: ~3% of PEC annually
    double maintenanceCost = getPurchasedEquipmentCost() * 0.03;

    // Lube oil consumption
    if (includeLubeOilSystem) {
      // ~0.5 L/hr per 1000 kW, ~$3/L
      double power = 0.0;
      if (mechanicalEquipment != null) {
        power = ((ExpanderMechanicalDesign) mechanicalEquipment).getPower();
      }
      double lubeOilCost = power / 1000.0 * 0.5 * operatingHoursPerYear * 3.0;
      maintenanceCost += lubeOilCost;
    }

    return maintenanceCost;
  }

  /**
   * Calculate power generation revenue.
   *
   * @param operatingHoursPerYear operating hours per year
   * @param electricityPrice electricity price in $/kWh
   * @return annual revenue in USD
   */
  public double calcPowerGenerationRevenue(int operatingHoursPerYear, double electricityPrice) {
    if (!"generator".equalsIgnoreCase(loadType)) {
      return 0.0;
    }

    double power = 0.0;
    if (mechanicalEquipment != null) {
      power = ((ExpanderMechanicalDesign) mechanicalEquipment).getPower();
    }

    // Assume 95% availability and 97% generator efficiency
    return power * 0.95 * 0.97 * operatingHoursPerYear * electricityPrice;
  }

  /**
   * Get cost breakdown by component.
   *
   * @return map of cost components
   */
  public Map<String, Object> getCostBreakdown() {
    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    double power = 0.0;
    if (mechanicalEquipment != null) {
      power = ((ExpanderMechanicalDesign) mechanicalEquipment).getPower();
    }

    breakdown.put("expanderType", expanderType);
    breakdown.put("loadType", loadType);
    breakdown.put("power_kW", power);
    breakdown.put("cryogenicService", cryogenicService);

    // Calculate individual costs
    double expanderCost;
    if ("axial".equalsIgnoreCase(expanderType)) {
      expanderCost = calcAxialExpanderCost(power);
    } else {
      expanderCost = calcRadialInflowExpanderCost(power);
    }

    if (cryogenicService) {
      expanderCost *= 1.4;
    }
    expanderCost *= getMaterialFactor();

    breakdown.put("expanderCost_USD", expanderCost);

    if (includeLoad) {
      breakdown.put("loadCost_USD", calcLoadCost(power));
    }
    if (includeGearbox) {
      breakdown.put("gearboxCost_USD", calcGearboxCost(power));
    }
    if (includeLubeOilSystem) {
      breakdown.put("lubeOilSystemCost_USD", calcLubeOilSystemCost(power));
    }
    if (includeControlSystem) {
      breakdown.put("controlSystemCost_USD", calcControlSystemCost(power));
    }

    breakdown.put("materialFactor", getMaterialFactor());
    breakdown.put("totalPurchasedCost_USD", getPurchasedEquipmentCost());

    return breakdown;
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> result = super.toMap();
    result.put("expanderCostBreakdown", getCostBreakdown());
    return result;
  }
}
