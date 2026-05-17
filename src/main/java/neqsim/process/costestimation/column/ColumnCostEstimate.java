package neqsim.process.costestimation.column;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Cost estimation class for distillation and absorption columns.
 *
 * <p>
 * This class provides column-specific cost estimation methods using chemical engineering cost
 * correlations for trayed and packed columns. Includes costs for internals, reboiler, and
 * condenser.
 * </p>
 *
 * @author AGAS
 * @version 1.0
 */
public class ColumnCostEstimate extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Column type. */
  private String columnType = "trayed";

  /** Tray type. */
  private String trayType = "sieve";

  /** Packing type. */
  private String packingType = "structured";

  /** Column diameter in meters. */
  private double columnDiameter = 1.0;

  /** Column height in meters. */
  private double columnHeight = 10.0;

  /** Number of trays. */
  private int numberOfTrays = 20;

  /** Packing height in meters. */
  private double packingHeight = 5.0;

  /** Design pressure in barg. */
  private double designPressure = 10.0;

  /** Include reboiler in cost. */
  private boolean includeReboiler = true;

  /** Include condenser in cost. */
  private boolean includeCondenser = true;

  /** Reboiler duty in kW. */
  private double reboilerDuty = 1000.0;

  /** Condenser duty in kW. */
  private double condenserDuty = 800.0;

  /**
   * Constructor for ColumnCostEstimate.
   *
   * @param mechanicalEquipment the column mechanical design
   */
  public ColumnCostEstimate(MechanicalDesign mechanicalEquipment) {
    super(mechanicalEquipment);
    setEquipmentType("column");
  }

  /**
   * Set column type.
   *
   * @param type column type ("trayed", "packed")
   */
  public void setColumnType(String type) {
    this.columnType = type;
  }

  /**
   * Get column type.
   *
   * @return column type
   */
  public String getColumnType() {
    return columnType;
  }

  /**
   * Set tray type.
   *
   * @param type tray type ("sieve", "valve", "bubble-cap")
   */
  public void setTrayType(String type) {
    this.trayType = type;
  }

  /**
   * Set packing type.
   *
   * @param type packing type ("structured", "random", "grid")
   */
  public void setPackingType(String type) {
    this.packingType = type;
  }

  /**
   * Set column diameter.
   *
   * @param diameter column diameter in meters
   */
  public void setColumnDiameter(double diameter) {
    this.columnDiameter = diameter;
  }

  /**
   * Get column diameter.
   *
   * @return column diameter in meters
   */
  public double getColumnDiameter() {
    return columnDiameter;
  }

  /**
   * Set column height.
   *
   * @param height column height in meters
   */
  public void setColumnHeight(double height) {
    this.columnHeight = height;
  }

  /**
   * Set number of trays.
   *
   * @param trays number of trays
   */
  public void setNumberOfTrays(int trays) {
    this.numberOfTrays = trays;
  }

  /**
   * Set packing height.
   *
   * @param height packing height in meters
   */
  public void setPackingHeight(double height) {
    this.packingHeight = height;
  }

  /**
   * Set design pressure.
   *
   * @param pressure design pressure in barg
   */
  public void setDesignPressure(double pressure) {
    this.designPressure = pressure;
  }

  /**
   * Set whether to include reboiler.
   *
   * @param include true to include reboiler
   */
  public void setIncludeReboiler(boolean include) {
    this.includeReboiler = include;
  }

  /**
   * Set whether to include condenser.
   *
   * @param include true to include condenser
   */
  public void setIncludeCondenser(boolean include) {
    this.includeCondenser = include;
  }

  /**
   * Set reboiler duty.
   *
   * @param duty reboiler duty in kW
   */
  public void setReboilerDuty(double duty) {
    this.reboilerDuty = duty;
  }

  /**
   * Set condenser duty.
   *
   * @param duty condenser duty in kW
   */
  public void setCondenserDuty(double duty) {
    this.condenserDuty = duty;
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    // Calculate shell cost
    double shellCost = calcColumnShellCost();

    // Calculate internals cost
    double internalsCost;
    if ("packed".equalsIgnoreCase(columnType)) {
      internalsCost = calcPackingCost();
    } else {
      internalsCost = calcTraysCost();
    }

    // Add reboiler cost
    double reboilerCost = 0.0;
    if (includeReboiler && reboilerDuty > 0) {
      reboilerCost = calcReboilerCost();
    }

    // Add condenser cost
    double condenserCost = 0.0;
    if (includeCondenser && condenserDuty > 0) {
      condenserCost = calcCondenserCost();
    }

    return shellCost + internalsCost + reboilerCost + condenserCost;
  }

  /**
   * Calculate column shell cost.
   *
   * @return shell cost in USD
   */
  private double calcColumnShellCost() {
    // Calculate shell weight for cost estimation
    double shellWeight = calcColumnWeight();

    // Use vertical vessel correlation
    double shellCost = getCostCalculator().calcVerticalVesselCost(shellWeight);

    // Apply pressure factor
    shellCost *= getPressureFactor();

    // Apply material factor
    shellCost *= getMaterialFactor();

    return shellCost;
  }

  /**
   * Get pressure factor for shell cost.
   *
   * @return pressure factor
   */
  private double getPressureFactor() {
    if (designPressure <= 5) {
      return 1.0;
    } else if (designPressure <= 20) {
      return 1.2;
    } else if (designPressure <= 50) {
      return 1.5;
    } else if (designPressure <= 100) {
      return 2.0;
    }
    return 2.5;
  }

  /**
   * Calculate trays cost.
   *
   * @return trays cost in USD
   */
  private double calcTraysCost() {
    if (numberOfTrays <= 0) {
      return 0.0;
    }

    double trayCost;
    if ("valve".equalsIgnoreCase(trayType)) {
      trayCost = getCostCalculator().calcValveTraysCost(columnDiameter, numberOfTrays);
    } else if ("bubble-cap".equalsIgnoreCase(trayType)) {
      trayCost = getCostCalculator().calcBubbleCapTraysCost(columnDiameter, numberOfTrays);
    } else {
      // Default sieve trays
      trayCost = getCostCalculator().calcSieveTraysCost(columnDiameter, numberOfTrays);
    }

    // Apply material factor for trays
    trayCost *= getMaterialFactor();

    return trayCost;
  }

  /**
   * Calculate packing cost.
   *
   * @return packing cost in USD
   */
  private double calcPackingCost() {
    if (packingHeight <= 0) {
      return 0.0;
    }

    // Calculate packing volume
    double packingVolume = Math.PI / 4.0 * columnDiameter * columnDiameter * packingHeight;

    double packingCost = getCostCalculator().calcPackingCost(packingVolume, packingType);

    // Apply material factor
    packingCost *= getMaterialFactor();

    // Add distributor and collector costs
    double distributorCost = calcDistributorCost();

    return packingCost + distributorCost;
  }

  /**
   * Calculate liquid distributor cost.
   *
   * @return distributor cost in USD
   */
  private double calcDistributorCost() {
    double cepciRatio = getCostCalculator().getCurrentCepci() / 607.5;

    // Typical 2 distributors per packed section
    double area = Math.PI / 4.0 * columnDiameter * columnDiameter;
    double distributorCost = 1500.0 * Math.pow(area, 0.6) * 2;

    return distributorCost * cepciRatio * getMaterialFactor();
  }

  /**
   * Calculate reboiler cost.
   *
   * @return reboiler cost in USD
   */
  private double calcReboilerCost() {
    // Estimate reboiler area from duty
    // Assume U = 1000 W/m2K, deltaT = 30 K
    double area = reboilerDuty * 1000.0 / (1000.0 * 30.0);

    // Shell-tube heat exchanger (kettle reboiler factor ~1.3)
    double cost = getCostCalculator().calcShellTubeHeatExchangerCost(area) * 1.3;

    // Apply material factor
    return cost * getMaterialFactor();
  }

  /**
   * Calculate condenser cost.
   *
   * @return condenser cost in USD
   */
  private double calcCondenserCost() {
    // Estimate condenser area from duty
    // Assume U = 800 W/m2K, deltaT = 20 K
    double area = condenserDuty * 1000.0 / (800.0 * 20.0);

    // Shell-tube heat exchanger
    double cost = getCostCalculator().calcShellTubeHeatExchangerCost(area);

    // Apply material factor
    return cost * getMaterialFactor();
  }

  /**
   * Calculate column weight.
   *
   * @return column weight in kg
   */
  public double calcColumnWeight() {
    // Estimate wall thickness
    double wallThickness = estimateWallThickness();

    // Shell surface area
    double shellArea = Math.PI * columnDiameter * columnHeight;

    // Shell weight
    double shellWeight = shellArea * wallThickness * 7850.0;

    // Add heads (approximately 2x shell weight per head)
    double headsWeight =
        2 * Math.PI / 4.0 * columnDiameter * columnDiameter * wallThickness * 7850 * 2;

    return shellWeight + headsWeight;
  }

  /**
   * Estimate wall thickness based on diameter and pressure.
   *
   * @return wall thickness in meters
   */
  private double estimateWallThickness() {
    // Simplified formula: t = P*D / (2*S*E - 1.2*P) + CA
    // S = 137.9 MPa for SA-516-70, E = 0.85, CA = 3mm
    double p = designPressure / 10.0; // Convert barg to MPa
    double d = columnDiameter * 1000; // Convert to mm
    double s = 137.9;
    double e = 0.85;
    double ca = 3.0;

    double t = (p * d) / (2 * s * e - 1.2 * p) + ca;
    return Math.max(t, 6.0) / 1000.0; // Minimum 6mm, convert to meters
  }

  /**
   * Calculate annual utility cost for reboiler and condenser.
   *
   * @param hoursPerYear operating hours per year
   * @param steamCostPerTonne steam cost in $/tonne
   * @param coolingWaterCostPerM3 cooling water cost in $/m3
   * @return annual utility cost in USD
   */
  public double calcAnnualUtilityCost(double hoursPerYear, double steamCostPerTonne,
      double coolingWaterCostPerM3) {
    double totalCost = 0.0;

    // Reboiler steam cost
    // Assume LP steam at 2760 kJ/kg latent heat
    if (includeReboiler && reboilerDuty > 0) {
      double steamFlowKgH = reboilerDuty * 3600.0 / 2760.0;
      double annualSteamTonnes = steamFlowKgH * hoursPerYear / 1000.0;
      totalCost += annualSteamTonnes * steamCostPerTonne;
    }

    // Condenser cooling water cost
    // Assume 10 K rise, 4.18 kJ/kg-K
    if (includeCondenser && condenserDuty > 0) {
      double cwFlowKgH = condenserDuty * 3600.0 / (4.18 * 10.0);
      double annualCwM3 = cwFlowKgH * hoursPerYear / 1000.0;
      totalCost += annualCwM3 * coolingWaterCostPerM3;
    }

    return totalCost;
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

    breakdown.put("columnType", columnType);
    if ("packed".equalsIgnoreCase(columnType)) {
      breakdown.put("packingType", packingType);
      breakdown.put("packingHeight_m", packingHeight);
    } else {
      breakdown.put("trayType", trayType);
      breakdown.put("numberOfTrays", numberOfTrays);
    }
    breakdown.put("columnDiameter_m", columnDiameter);
    breakdown.put("columnHeight_m", columnHeight);
    breakdown.put("designPressure_barg", designPressure);
    breakdown.put("materialGrade", getMaterialGrade());
    breakdown.put("includeReboiler", includeReboiler);
    breakdown.put("reboilerDuty_kW", reboilerDuty);
    breakdown.put("includeCondenser", includeCondenser);
    breakdown.put("condenserDuty_kW", condenserDuty);
    breakdown.put("columnWeight_kg", calcColumnWeight());
    breakdown.put("shellCost_USD", calcColumnShellCost());
    if ("packed".equalsIgnoreCase(columnType)) {
      breakdown.put("packingCost_USD", calcPackingCost());
    } else {
      breakdown.put("traysCost_USD", calcTraysCost());
    }
    breakdown.put("purchasedEquipmentCost_USD", purchasedEquipmentCost);
    breakdown.put("bareModuleCost_USD", bareModuleCost);
    breakdown.put("totalModuleCost_USD", totalModuleCost);
    breakdown.put("installationManHours", installationManHours);

    return breakdown;
  }
}
