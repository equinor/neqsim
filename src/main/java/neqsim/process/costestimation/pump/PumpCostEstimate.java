package neqsim.process.costestimation.pump;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.pump.PumpMechanicalDesign;

/**
 * Cost estimation class for pumps.
 *
 * <p>
 * This class provides pump-specific cost estimation methods using chemical engineering cost
 * correlations for centrifugal, positive displacement, and specialty pumps.
 * </p>
 *
 * @author AGAS
 * @version 1.0
 */
public class PumpCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Pump type. */
  private String pumpType = "centrifugal";

  /** Motor included in cost. */
  private boolean includeMotor = true;

  /** API rating. */
  private boolean apiRated = false;

  /** Seal type. */
  private String sealType = "single-mechanical";

  /**
   * Constructor for PumpCostEstimate.
   *
   * @param mechanicalEquipment the pump mechanical design
   */
  public PumpCostEstimate(PumpMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("pump");
  }

  /**
   * Set the pump type.
   *
   * @param type pump type ("centrifugal", "reciprocating", "gear", "screw", "diaphragm")
   */
  public void setPumpType(String type) {
    this.pumpType = type;
  }

  /**
   * Get the pump type.
   *
   * @return pump type
   */
  public String getPumpType() {
    return pumpType;
  }

  /**
   * Set whether motor is included.
   *
   * @param include true to include motor
   */
  public void setIncludeMotor(boolean include) {
    this.includeMotor = include;
  }

  /**
   * Set API rating.
   *
   * @param rated true if API rated (e.g., API 610)
   */
  public void setApiRated(boolean rated) {
    this.apiRated = rated;
  }

  /**
   * Set seal type.
   *
   * @param type seal type ("single-mechanical", "double-mechanical", "packed", "mag-drive")
   */
  public void setSealType(String type) {
    this.sealType = type;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    PumpMechanicalDesign pumpMecDesign = (PumpMechanicalDesign) mechanicalEquipment;

    // Get pump power
    double power = pumpMecDesign.getPower(); // kW
    if (power <= 0) {
      return mechanicalEquipment.getWeightTotal() * 15.0; // Fallback
    }

    // Calculate base pump cost
    double baseCost;
    if ("reciprocating".equalsIgnoreCase(pumpType)) {
      baseCost = calcReciprocatingPumpCost(power);
    } else if ("gear".equalsIgnoreCase(pumpType)) {
      baseCost = calcGearPumpCost(power);
    } else if ("screw".equalsIgnoreCase(pumpType)) {
      baseCost = calcScrewPumpCost(power);
    } else if ("diaphragm".equalsIgnoreCase(pumpType)) {
      baseCost = calcDiaphragmPumpCost(power);
    } else {
      // Default centrifugal
      baseCost = getCostCalculator().calcCentrifugalPumpCost(power);
    }

    // Apply API factor
    if (apiRated) {
      baseCost *= 1.5; // API 610 pumps are ~50% more expensive
    }

    // Apply seal type factor
    baseCost *= getSealTypeFactor();

    // Add motor cost if included
    if (includeMotor) {
      baseCost += calcMotorCost(power);
    }

    return baseCost;
  }

  /**
   * Calculate reciprocating pump cost.
   *
   * @param power pump power in kW
   * @return cost in USD
   */
  private double calcReciprocatingPumpCost(double power) {
    // Reciprocating pumps are typically 1.5x centrifugal
    return getCostCalculator().calcCentrifugalPumpCost(power) * 1.5;
  }

  /**
   * Calculate gear pump cost.
   *
   * @param power pump power in kW
   * @return cost in USD
   */
  private double calcGearPumpCost(double power) {
    // Gear pumps: Cp = 400 * P^0.7
    double baseCost = 400.0 * Math.pow(Math.max(power, 1.0), 0.7);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate screw pump cost.
   *
   * @param power pump power in kW
   * @return cost in USD
   */
  private double calcScrewPumpCost(double power) {
    // Screw pumps are ~2x centrifugal
    return getCostCalculator().calcCentrifugalPumpCost(power) * 2.0;
  }

  /**
   * Calculate diaphragm pump cost.
   *
   * @param power pump power in kW
   * @return cost in USD
   */
  private double calcDiaphragmPumpCost(double power) {
    // Diaphragm pumps: Cp = 600 * P^0.6
    double baseCost = 600.0 * Math.pow(Math.max(power, 1.0), 0.6);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate electric motor cost.
   *
   * @param power motor power in kW
   * @return cost in USD
   */
  private double calcMotorCost(double power) {
    if (power <= 0) {
      return 0.0;
    }
    // Motor cost correlation: Cp = 200 * P^0.75
    // Includes motor efficiency factor
    double motorPower = power / 0.9; // Assume 90% motor efficiency
    double baseCost = 200.0 * Math.pow(motorPower, 0.75);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Get seal type cost factor.
   *
   * @return seal type factor
   */
  private double getSealTypeFactor() {
    if ("double-mechanical".equalsIgnoreCase(sealType)) {
      return 1.3; // Double mechanical seals
    } else if ("packed".equalsIgnoreCase(sealType)) {
      return 0.8; // Packed glands (cheapest)
    } else if ("mag-drive".equalsIgnoreCase(sealType)
        || "magnetic-drive".equalsIgnoreCase(sealType)) {
      return 2.0; // Magnetic drive (sealless)
    } else if ("canned".equalsIgnoreCase(sealType)) {
      return 2.5; // Canned motor pump
    }
    return 1.0; // Single mechanical seal (default)
  }

  /**
   * Calculate annual operating cost (electricity).
   *
   * @param hoursPerYear operating hours per year
   * @param electricityRate electricity rate in $/kWh
   * @return annual operating cost in USD
   */
  public double calcAnnualOperatingCost(double hoursPerYear, double electricityRate) {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    PumpMechanicalDesign pumpMecDesign = (PumpMechanicalDesign) mechanicalEquipment;
    double power = pumpMecDesign.getPower(); // kW

    // Account for motor efficiency
    double electricalPower = power / 0.9;

    return electricalPower * hoursPerYear * electricityRate;
  }

  /**
   * Calculate maintenance cost per year (typically 3-5% of capital).
   *
   * @return annual maintenance cost in USD
   */
  public double calcAnnualMaintenanceCost() {
    if (purchasedEquipmentCost <= 0) {
      calculateCostEstimate();
    }
    // Pumps typically 4% annual maintenance
    return purchasedEquipmentCost * 0.04;
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

    breakdown.put("pumpType", pumpType);
    breakdown.put("apiRated", apiRated);
    breakdown.put("sealType", sealType);
    breakdown.put("includeMotor", includeMotor);
    breakdown.put("purchasedEquipmentCost_USD", purchasedEquipmentCost);
    breakdown.put("bareModuleCost_USD", bareModuleCost);
    breakdown.put("totalModuleCost_USD", totalModuleCost);
    breakdown.put("installationManHours", installationManHours);

    if (mechanicalEquipment != null) {
      PumpMechanicalDesign pumpMecDesign = (PumpMechanicalDesign) mechanicalEquipment;
      breakdown.put("power_kW", pumpMecDesign.getPower());
      breakdown.put("weight_kg", pumpMecDesign.getWeightTotal());
    }

    return breakdown;
  }
}
