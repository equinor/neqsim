package neqsim.process.costestimation.heatexchanger;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.costestimation.UnitCostEstimateBaseClass;
import neqsim.process.mechanicaldesign.heatexchanger.BAHXMechanicalDesign;

/**
 * Cost estimation for brazed aluminium plate-fin heat exchangers (BAHX).
 *
 * <p>
 * BAHX cost estimation uses a weight-and-area based model calibrated against published BAHX cost
 * data for LNG and air separation applications. Typical BAHX costs range from $800 to $2000 per m2
 * of heat transfer area for aluminium plate-fin construction.
 * </p>
 *
 * <p>
 * Cost components:
 * </p>
 * <ul>
 * <li>Core cost: weight-based with material surcharge for aluminium alloy</li>
 * <li>Header and nozzle cost: from weight and complexity</li>
 * <li>Brazing cost: area-based (vacuum furnace brazing)</li>
 * <li>Testing and inspection: fraction of material cost</li>
 * <li>Installation factor: 3.0-4.0 for LNG cryogenic service</li>
 * </ul>
 *
 * @author NeqSim
 * @version 1.0
 */
public class BAHXCostEstimator extends UnitCostEstimateBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Cost rate constants (2024 USD basis)
  // ============================================================================

  /** Base aluminium raw material cost in USD/kg. */
  private static final double AL_MATERIAL_COST_PER_KG = 8.0;

  /** Manufacturing labour cost for BAHX in USD/kg of finished core. */
  private static final double MANUFACTURING_COST_PER_KG = 25.0;

  /** Brazing cost (vacuum furnace) in USD/m2 of heat transfer area. */
  private static final double BRAZING_COST_PER_M2 = 120.0;

  /** Testing and inspection cost as fraction of material cost. */
  private static final double TESTING_FRACTION = 0.12;

  /** Header/nozzle fabrication surcharge per kg over core material cost. */
  private static final double HEADER_SURCHARGE_PER_KG = 15.0;

  /** LNG cryogenic service installation factor. */
  private static final double LNG_INSTALLATION_FACTOR = 3.5;

  /** Engineering and procurement overhead factor. */
  private static final double ENGINEERING_FACTOR = 1.15;

  /** Contingency factor. */
  private static final double CONTINGENCY_FACTOR = 1.10;

  // ============================================================================
  // Cost results
  // ============================================================================

  /** Core material and manufacturing cost in USD. */
  private double coreCost = 0.0;

  /** Header and nozzle cost in USD. */
  private double headerNozzleCost = 0.0;

  /** Brazing process cost in USD. */
  private double brazingCost = 0.0;

  /** Testing and inspection cost in USD. */
  private double testingCost = 0.0;

  /** Equipment cost (ex-works) in USD. */
  private double equipmentCostUSD = 0.0;

  /** Installed cost in USD. */
  private double installedCostUSD = 0.0;

  /** Specific cost per m2 in USD. */
  private double specificCostPerM2 = 0.0;

  /** Annual maintenance cost estimate in USD. */
  private double annualMaintenanceCostUSD = 0.0;

  /**
   * Constructor for BAHXCostEstimator.
   *
   * @param mechanicalDesign the BAHX mechanical design
   */
  public BAHXCostEstimator(BAHXMechanicalDesign mechanicalDesign) {
    super(mechanicalDesign);
    setEquipmentType("BAHX");
  }

  /** {@inheritDoc} */
  @Override
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    BAHXMechanicalDesign bahxDesign = (BAHXMechanicalDesign) mechanicalEquipment;

    double coreWeight = bahxDesign.getCoreWeightKg();
    double totalWeight = bahxDesign.getWeightTotal();
    double area = bahxDesign.getHeatTransferAreaM2();
    double headerNozzleWeight = totalWeight - coreWeight;

    // Core cost = material + manufacturing
    coreCost = coreWeight * (AL_MATERIAL_COST_PER_KG + MANUFACTURING_COST_PER_KG);

    // Header/nozzle cost = material + surcharge
    headerNozzleCost = headerNozzleWeight * (AL_MATERIAL_COST_PER_KG + HEADER_SURCHARGE_PER_KG);

    // Brazing cost
    brazingCost = area * BRAZING_COST_PER_M2;

    // Testing and inspection
    testingCost = (coreCost + headerNozzleCost) * TESTING_FRACTION;

    // Purchased equipment cost (ex-works)
    equipmentCostUSD = coreCost + headerNozzleCost + brazingCost + testingCost;

    // Apply engineering overhead
    equipmentCostUSD *= ENGINEERING_FACTOR;

    // Specific cost
    if (area > 0) {
      specificCostPerM2 = equipmentCostUSD / area;
    }

    // Annual maintenance (2% of equipment cost for LNG service)
    annualMaintenanceCostUSD = equipmentCostUSD * 0.02;

    return equipmentCostUSD;
  }

  /**
   * Calculate the total installed cost.
   *
   * @return installed cost in USD
   */
  public double calcInstalledCost() {
    if (equipmentCostUSD <= 0) {
      calcPurchasedEquipmentCost();
    }
    installedCostUSD = equipmentCostUSD * LNG_INSTALLATION_FACTOR * CONTINGENCY_FACTOR;
    return installedCostUSD;
  }

  /**
   * Get the cost breakdown as a map.
   *
   * @return map of cost components
   */
  public Map<String, Object> getCostBreakdown() {
    if (equipmentCostUSD <= 0) {
      calcPurchasedEquipmentCost();
    }
    if (installedCostUSD <= 0) {
      calcInstalledCost();
    }

    Map<String, Object> breakdown = new LinkedHashMap<String, Object>();

    Map<String, Object> capex = new LinkedHashMap<String, Object>();
    capex.put("coreMaterialAndManufacturing_USD", round(coreCost, 0));
    capex.put("headerAndNozzle_USD", round(headerNozzleCost, 0));
    capex.put("brazingProcess_USD", round(brazingCost, 0));
    capex.put("testingAndInspection_USD", round(testingCost, 0));
    capex.put("purchasedEquipmentCost_USD", round(equipmentCostUSD, 0));
    capex.put("installationFactor", LNG_INSTALLATION_FACTOR);
    capex.put("contingencyFactor", CONTINGENCY_FACTOR);
    capex.put("totalInstalledCost_USD", round(installedCostUSD, 0));
    capex.put("specificCost_USDperM2", round(specificCostPerM2, 0));
    breakdown.put("capex", capex);

    Map<String, Object> opex = new LinkedHashMap<String, Object>();
    opex.put("annualMaintenance_USD", round(annualMaintenanceCostUSD, 0));
    opex.put("maintenanceFraction_pct", 2.0);
    breakdown.put("opex", opex);

    return breakdown;
  }

  /**
   * Get the equipment (ex-works) cost.
   *
   * @return equipment cost in USD
   */
  public double getEquipmentCostUSD() {
    if (equipmentCostUSD <= 0) {
      calcPurchasedEquipmentCost();
    }
    return equipmentCostUSD;
  }

  /**
   * Get the total installed cost.
   *
   * @return installed cost in USD
   */
  public double getInstalledCostUSD() {
    if (installedCostUSD <= 0) {
      calcInstalledCost();
    }
    return installedCostUSD;
  }

  /**
   * Get the specific cost per unit area.
   *
   * @return cost per m2 in USD
   */
  public double getSpecificCostPerM2() {
    if (equipmentCostUSD <= 0) {
      calcPurchasedEquipmentCost();
    }
    return specificCostPerM2;
  }

  /**
   * Get the annual maintenance cost estimate.
   *
   * @return annual maintenance cost in USD
   */
  public double getAnnualMaintenanceCostUSD() {
    if (equipmentCostUSD <= 0) {
      calcPurchasedEquipmentCost();
    }
    return annualMaintenanceCostUSD;
  }

  /**
   * Round a double value to the specified number of decimal places.
   *
   * @param value the value to round
   * @param decimals number of decimal places
   * @return rounded value
   */
  private double round(double value, int decimals) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return value;
    }
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }
}
