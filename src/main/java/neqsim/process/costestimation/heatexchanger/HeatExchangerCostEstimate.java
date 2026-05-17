package neqsim.process.costestimation.heatexchanger;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.heatexchanger.HeatExchangerMechanicalDesign;

/**
 * Cost estimation class for heat exchangers.
 *
 * <p>
 * This class provides heat exchanger-specific cost estimation methods using chemical engineering
 * cost correlations for shell-and-tube, plate, and air-cooled heat exchangers.
 * </p>
 *
 * <p>
 * Correlations are based on:
 * </p>
 * <ul>
 * <li>Turton et al. - Analysis, Synthesis and Design of Chemical Processes</li>
 * <li>Peters &amp; Timmerhaus - Plant Design and Economics</li>
 * </ul>
 *
 * @author AGAS
 * @version 1.0
 */
public class HeatExchangerCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Heat exchanger type. */
  private String exchangerType = "shell-tube";

  /** Number of shell passes. */
  private int shellPasses = 1;

  /** Number of tube passes. */
  private int tubePasses = 2;

  /** TEMA type (e.g., "AES", "BEM", "AEU"). */
  private String temaType = "AES";

  /**
   * Constructor for HeatExchangerCostEstimate.
   *
   * @param mechanicalEquipment the heat exchanger mechanical design
   */
  public HeatExchangerCostEstimate(HeatExchangerMechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("exchanger");
  }

  /**
   * Set the heat exchanger type.
   *
   * @param type exchanger type ("shell-tube", "plate", "air-cooler", "double-pipe")
   */
  public void setExchangerType(String type) {
    this.exchangerType = type;
  }

  /**
   * Get the heat exchanger type.
   *
   * @return exchanger type
   */
  public String getExchangerType() {
    return exchangerType;
  }

  /**
   * Set the TEMA type.
   *
   * @param tema TEMA designation (e.g., "AES", "BEM")
   */
  public void setTemaType(String tema) {
    this.temaType = tema;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    HeatExchangerMechanicalDesign hxMecDesign = (HeatExchangerMechanicalDesign) mechanicalEquipment;

    // Get heat transfer area
    double area = hxMecDesign.getHeatTransferArea();
    if (area <= 0) {
      // Estimate from duty if area not available
      double duty = Math.abs(hxMecDesign.getDuty()); // kW
      double uValue = 500.0; // Assumed U in W/m2K
      double lmtd = 30.0; // Assumed LMTD in K
      area = duty * 1000.0 / (uValue * lmtd);
    }

    if (area <= 0) {
      return mechanicalEquipment.getWeightTotal() * 8.0; // Fallback
    }

    // Calculate cost based on type
    double baseCost;
    if ("plate".equalsIgnoreCase(exchangerType)) {
      baseCost = getCostCalculator().calcPlateHeatExchangerCost(area);
    } else if ("air-cooler".equalsIgnoreCase(exchangerType)
        || "aircooler".equalsIgnoreCase(exchangerType)) {
      baseCost = getCostCalculator().calcAirCoolerCost(area);
    } else if ("double-pipe".equalsIgnoreCase(exchangerType)) {
      // Double pipe is typically cheaper than shell-tube
      baseCost = getCostCalculator().calcShellTubeHeatExchangerCost(area) * 0.6;
    } else {
      // Default to shell-and-tube
      baseCost = getCostCalculator().calcShellTubeHeatExchangerCost(area);
    }

    // Apply TEMA type factor
    double temaFactor = getTemaTypeFactor();
    baseCost *= temaFactor;

    // Apply shell/tube pass factors
    if (shellPasses > 1) {
      baseCost *= (1.0 + 0.1 * (shellPasses - 1));
    }

    return baseCost;
  }

  /**
   * Get TEMA type cost factor.
   *
   * @return TEMA type factor
   */
  private double getTemaTypeFactor() {
    if (temaType == null) {
      return 1.0;
    }
    // TEMA factors based on front end, shell, and rear end types
    String frontEnd = temaType.length() > 0 ? temaType.substring(0, 1) : "A";
    String rearEnd = temaType.length() > 2 ? temaType.substring(2, 3) : "S";

    double factor = 1.0;

    // Front end factors
    if ("B".equals(frontEnd)) {
      factor *= 1.1; // Bonnet
    } else if ("C".equals(frontEnd)) {
      factor *= 1.2; // Channel integral with tubesheet
    } else if ("N".equals(frontEnd)) {
      factor *= 1.3; // Channel integral, removable cover
    }

    // Rear end factors
    if ("L".equals(rearEnd)) {
      factor *= 1.0; // Fixed tubesheet like A
    } else if ("M".equals(rearEnd)) {
      factor *= 1.0; // Fixed tubesheet like B
    } else if ("N".equals(rearEnd)) {
      factor *= 1.0; // Fixed tubesheet like C
    } else if ("P".equals(rearEnd)) {
      factor *= 1.2; // Outside packed floating head
    } else if ("S".equals(rearEnd)) {
      factor *= 1.15; // Floating head with backing device
    } else if ("T".equals(rearEnd)) {
      factor *= 1.25; // Pull-through floating head
    } else if ("U".equals(rearEnd)) {
      factor *= 0.9; // U-tube bundle (cheapest)
    } else if ("W".equals(rearEnd)) {
      factor *= 1.1; // Externally sealed floating tubesheet
    }

    return factor;
  }

  /**
   * Calculate utility operating cost per year.
   *
   * @param utilityType type of utility ("cooling_water", "steam", "electricity")
   * @param hoursPerYear operating hours per year
   * @return annual utility cost in USD
   */
  public double calcUtilityOperatingCost(String utilityType, double hoursPerYear) {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    HeatExchangerMechanicalDesign hxMecDesign = (HeatExchangerMechanicalDesign) mechanicalEquipment;
    double duty = Math.abs(hxMecDesign.getDuty()); // kW

    // Utility unit costs (typical 2024 values)
    double costPerKwh;
    if ("cooling_water".equalsIgnoreCase(utilityType)) {
      // Cooling water: ~$0.05 per 1000 gal, assume 10°C rise
      // 1 kW = 3600 kJ/hr, cooling water specific heat = 4.18 kJ/kg·K
      // Mass flow = 3600 / (4.18 * 10) = 86 kg/hr = 22.7 gal/hr
      costPerKwh = 0.001; // $/kWh
    } else if ("steam".equalsIgnoreCase(utilityType)) {
      // Steam: ~$15 per 1000 lb, latent heat = 2200 kJ/kg
      // 1 kW = 3600 kJ/hr, mass flow = 3600/2200 = 1.64 kg/hr = 3.6 lb/hr
      costPerKwh = 0.05; // $/kWh for LP steam
    } else if ("refrigeration".equalsIgnoreCase(utilityType)) {
      // Refrigeration: electrical cost * COP
      costPerKwh = 0.15; // $/kWh (assuming COP of 3)
    } else {
      // Default (electricity)
      costPerKwh = 0.10; // $/kWh
    }

    return duty * hoursPerYear * costPerKwh;
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

    breakdown.put("exchangerType", exchangerType);
    breakdown.put("temaType", temaType);
    breakdown.put("purchasedEquipmentCost_USD", purchasedEquipmentCost);
    breakdown.put("bareModuleCost_USD", bareModuleCost);
    breakdown.put("totalModuleCost_USD", totalModuleCost);
    breakdown.put("installationManHours", installationManHours);

    if (mechanicalEquipment != null) {
      HeatExchangerMechanicalDesign hxMecDesign =
          (HeatExchangerMechanicalDesign) mechanicalEquipment;
      breakdown.put("heatTransferArea_m2", hxMecDesign.getHeatTransferArea());
      breakdown.put("duty_kW", hxMecDesign.getDuty());
      breakdown.put("weight_kg", hxMecDesign.getWeightTotal());
    }

    return breakdown;
  }
}
