package neqsim.process.costestimation.valve;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign;

/**
 * Cost estimation class for valves.
 *
 * <p>
 * This class provides valve-specific cost estimation methods using chemical engineering cost
 * correlations for control valves, gate valves, ball valves, and other valve types.
 * </p>
 *
 * @author asmund
 * @version 2.0
 */
public class ValveCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Valve type. */
  private String valveType = "control";

  /** Nominal valve size in inches. */
  private double nominalSize = 4.0;

  /** Valve Cv (flow coefficient). */
  private double valveCv = 100.0;

  /** Pressure class (ASME). */
  private int pressureClass = 300;

  /** Include actuator. */
  private boolean includeActuator = true;

  /** Actuator type. */
  private String actuatorType = "pneumatic";

  /**
   * Constructor for ValveCostEstimate.
   *
   * @param mechanicalEquipment a
   *        {@link neqsim.process.mechanicaldesign.valve.ValveMechanicalDesign} object
   */
  public ValveCostEstimate(ValveMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("valve");
  }

  /**
   * Set the valve type.
   *
   * @param type valve type ("control", "gate", "ball", "globe", "check", "butterfly")
   */
  public void setValveType(String type) {
    this.valveType = type;
  }

  /**
   * Get the valve type.
   *
   * @return valve type
   */
  public String getValveType() {
    return valveType;
  }

  /**
   * Set nominal valve size.
   *
   * @param size nominal size in inches
   */
  public void setNominalSize(double size) {
    this.nominalSize = size;
  }

  /**
   * Set valve Cv.
   *
   * @param cv flow coefficient
   */
  public void setValveCv(double cv) {
    this.valveCv = cv;
  }

  /**
   * Get valve Cv.
   *
   * @return flow coefficient
   */
  public double getValveCv() {
    return valveCv;
  }

  /**
   * Set pressure class.
   *
   * @param pressClass ASME pressure class (150, 300, 600, 900, 1500, 2500)
   */
  public void setPressureClass(int pressClass) {
    this.pressureClass = pressClass;
  }

  /**
   * Set whether to include actuator.
   *
   * @param include true to include actuator
   */
  public void setIncludeActuator(boolean include) {
    this.includeActuator = include;
  }

  /**
   * Set actuator type.
   *
   * @param type actuator type ("pneumatic", "electric", "hydraulic", "manual")
   */
  public void setActuatorType(String type) {
    this.actuatorType = type;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    double valveCost;

    if ("control".equalsIgnoreCase(valveType)) {
      // Use Cv-based correlation from CostEstimationCalculator
      valveCost = getCostCalculator().calcControlValveCost(valveCv);
    } else if ("gate".equalsIgnoreCase(valveType)) {
      valveCost = calcGateValveCost();
    } else if ("ball".equalsIgnoreCase(valveType)) {
      valveCost = calcBallValveCost();
    } else if ("globe".equalsIgnoreCase(valveType)) {
      valveCost = calcGlobeValveCost();
    } else if ("check".equalsIgnoreCase(valveType)) {
      valveCost = calcCheckValveCost();
    } else if ("butterfly".equalsIgnoreCase(valveType)) {
      valveCost = calcButterflyValveCost();
    } else {
      // Default to control valve
      valveCost = getCostCalculator().calcControlValveCost(valveCv);
    }

    // Apply pressure class factor
    valveCost *= getPressureClassFactor();

    // Apply material factor
    valveCost *= getMaterialFactor();

    // Add actuator cost if included
    if (includeActuator && "control".equalsIgnoreCase(valveType)) {
      valveCost += calcActuatorCost();
    }

    return valveCost;
  }

  /**
   * Calculate gate valve cost.
   *
   * @return cost in USD
   */
  private double calcGateValveCost() {
    // Gate valve cost: Cv = 40 + 20*D^1.5 (D in inches)
    double baseCost = 40 + 20 * Math.pow(nominalSize, 1.5);
    return baseCost * (getCostCalculator().getCurrentCepci() / 607.5);
  }

  /**
   * Calculate ball valve cost.
   *
   * @return cost in USD
   */
  private double calcBallValveCost() {
    // Ball valve cost: ~1.5x gate valve
    return calcGateValveCost() * 1.5;
  }

  /**
   * Calculate globe valve cost.
   *
   * @return cost in USD
   */
  private double calcGlobeValveCost() {
    // Globe valve cost: ~1.3x gate valve
    return calcGateValveCost() * 1.3;
  }

  /**
   * Calculate check valve cost.
   *
   * @return cost in USD
   */
  private double calcCheckValveCost() {
    // Check valve cost: ~0.8x gate valve
    return calcGateValveCost() * 0.8;
  }

  /**
   * Calculate butterfly valve cost.
   *
   * @return cost in USD
   */
  private double calcButterflyValveCost() {
    // Butterfly valve cost: ~0.6x gate valve
    return calcGateValveCost() * 0.6;
  }

  /**
   * Get pressure class factor.
   *
   * @return pressure class factor
   */
  private double getPressureClassFactor() {
    switch (pressureClass) {
      case 150:
        return 1.0;
      case 300:
        return 1.2;
      case 600:
        return 1.5;
      case 900:
        return 2.0;
      case 1500:
        return 2.5;
      case 2500:
        return 3.5;
      default:
        return 1.0;
    }
  }

  /**
   * Calculate actuator cost.
   *
   * @return actuator cost in USD
   */
  private double calcActuatorCost() {
    double baseCost;
    double cepciRatio = getCostCalculator().getCurrentCepci() / 607.5;

    if ("electric".equalsIgnoreCase(actuatorType)) {
      // Electric actuator: more expensive
      baseCost = 500 + 100 * Math.pow(nominalSize, 0.8);
    } else if ("hydraulic".equalsIgnoreCase(actuatorType)) {
      // Hydraulic actuator
      baseCost = 800 + 150 * Math.pow(nominalSize, 0.8);
    } else if ("manual".equalsIgnoreCase(actuatorType)) {
      // Manual handwheel
      baseCost = 50 + 10 * nominalSize;
    } else {
      // Pneumatic (default)
      baseCost = 300 + 80 * Math.pow(nominalSize, 0.8);
    }

    return baseCost * cepciRatio;
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

    breakdown.put("valveType", valveType);
    breakdown.put("nominalSize_inches", nominalSize);
    breakdown.put("valveCv", valveCv);
    breakdown.put("pressureClass", pressureClass);
    breakdown.put("includeActuator", includeActuator);
    breakdown.put("actuatorType", actuatorType);
    breakdown.put("materialGrade", getMaterialGrade());
    breakdown.put("purchasedEquipmentCost_USD", purchasedEquipmentCost);
    breakdown.put("bareModuleCost_USD", bareModuleCost);
    breakdown.put("totalModuleCost_USD", totalModuleCost);
    breakdown.put("installationManHours", installationManHours);

    return breakdown;
  }
}
