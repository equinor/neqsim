package neqsim.process.costestimation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.google.gson.GsonBuilder;
import neqsim.process.mechanicaldesign.MechanicalDesign;

/**
 * Base class for equipment cost estimation.
 *
 * <p>
 * This class provides comprehensive cost estimation methods for process equipment based on chemical
 * engineering cost correlations. It supports multiple cost estimation methodologies and provides
 * JSON export capabilities for integration with project cost systems.
 * </p>
 *
 * <p>
 * Cost estimation features include:
 * </p>
 * <ul>
 * <li>Purchased Equipment Cost (PEC) - base cost from correlations</li>
 * <li>Bare Module Cost (BMC) - includes installation, piping, instrumentation</li>
 * <li>Total Module Cost (TMC) - includes contingency and engineering</li>
 * <li>Grass Roots Cost - for new facility construction</li>
 * <li>Operating cost estimation</li>
 * <li>Labor man-hours estimation</li>
 * <li>Bill of Materials generation</li>
 * </ul>
 *
 * @author esol
 * @version 2.0
 */
public class UnitCostEstimateBaseClass implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // Cost Parameters
  // ============================================================================

  /** Cost per weight unit ($/kg) - simple estimation. */
  private double costPerWeightUnit = 8.0;

  /** Associated mechanical design. */
  public MechanicalDesign mechanicalEquipment = null;

  /** Cost estimation calculator. */
  protected transient CostEstimationCalculator costCalculator;

  // ============================================================================
  // Cost Results
  // ============================================================================

  /** Purchased equipment cost in USD. */
  protected double purchasedEquipmentCost = 0.0;

  /** Bare module cost in USD. */
  protected double bareModuleCost = 0.0;

  /** Total module cost in USD. */
  protected double totalModuleCost = 0.0;

  /** Grass roots cost in USD. */
  protected double grassRootsCost = 0.0;

  /** Installation labor man-hours. */
  protected double installationManHours = 0.0;

  /** Annual operating cost in USD. */
  protected double annualOperatingCost = 0.0;

  /** Equipment type identifier. */
  protected String equipmentType = "general";

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   */
  public UnitCostEstimateBaseClass() {
    this.costCalculator = new CostEstimationCalculator();
  }

  /**
   * Constructor with mechanical equipment.
   *
   * @param mechanicalEquipment a {@link neqsim.process.mechanicaldesign.MechanicalDesign} object
   */
  public UnitCostEstimateBaseClass(MechanicalDesign mechanicalEquipment) {
    this.mechanicalEquipment = mechanicalEquipment;
    this.costCalculator = new CostEstimationCalculator();
  }

  // ============================================================================
  // Cost Calculation Methods
  // ============================================================================

  /**
   * Calculate all cost estimates for the equipment.
   *
   * <p>
   * This method calculates purchased equipment cost, bare module cost, total module cost, and grass
   * roots cost based on the equipment weight and design pressure.
   * </p>
   */
  public void calculateCostEstimate() {
    if (mechanicalEquipment == null) {
      return;
    }

    // Get basic parameters
    double weight = mechanicalEquipment.getWeightTotal();
    double designPressure = mechanicalEquipment.getMaxDesignPressure();

    // Calculate purchased equipment cost based on weight
    purchasedEquipmentCost = calcPurchasedEquipmentCost();

    // Calculate bare module cost
    bareModuleCost = getCostCalculator().calcBareModuleCost(purchasedEquipmentCost, designPressure);

    // Calculate total module cost
    totalModuleCost = getCostCalculator().calcTotalModuleCost(bareModuleCost);

    // Calculate grass roots cost
    grassRootsCost = getCostCalculator().calcGrassRootsCost(totalModuleCost);

    // Calculate installation man-hours
    installationManHours = getCostCalculator().calcInstallationManHours(weight, getEquipmentType());
  }

  /**
   * Calculate purchased equipment cost.
   *
   * <p>
   * Override this method in subclasses to provide equipment-specific cost correlations.
   * </p>
   *
   * @return purchased equipment cost in USD
   */
  protected double calcPurchasedEquipmentCost() {
    if (mechanicalEquipment == null) {
      return 0.0;
    }

    double weight = mechanicalEquipment.getWeightTotal();
    if (weight <= 0) {
      return 0.0;
    }

    // Default: use weight-based correlation
    return weight * costPerWeightUnit * getCostCalculator().getMaterialFactor();
  }

  /**
   * Get total cost using simple weight-based method.
   *
   * @return the total cost in USD
   */
  public double getTotalCost() {
    if (totalModuleCost > 0) {
      return totalModuleCost;
    }
    if (mechanicalEquipment == null) {
      return 0.0;
    }
    return mechanicalEquipment.getWeightTotal() * costPerWeightUnit;
  }

  /**
   * Get purchased equipment cost.
   *
   * @return purchased equipment cost in USD
   */
  public double getPurchasedEquipmentCost() {
    if (purchasedEquipmentCost <= 0) {
      purchasedEquipmentCost = calcPurchasedEquipmentCost();
    }
    return purchasedEquipmentCost;
  }

  /**
   * Get bare module cost.
   *
   * @return bare module cost in USD
   */
  public double getBareModuleCost() {
    if (bareModuleCost <= 0) {
      calculateCostEstimate();
    }
    return bareModuleCost;
  }

  /**
   * Get total module cost.
   *
   * @return total module cost in USD
   */
  public double getTotalModuleCost() {
    if (totalModuleCost <= 0) {
      calculateCostEstimate();
    }
    return totalModuleCost;
  }

  /**
   * Get grass roots cost.
   *
   * @return grass roots cost in USD
   */
  public double getGrassRootsCost() {
    if (grassRootsCost <= 0) {
      calculateCostEstimate();
    }
    return grassRootsCost;
  }

  /**
   * Get installation labor man-hours.
   *
   * @return installation man-hours
   */
  public double getInstallationManHours() {
    if (installationManHours <= 0) {
      calculateCostEstimate();
    }
    return installationManHours;
  }

  // ============================================================================
  // Operating Cost Methods
  // ============================================================================

  /**
   * Calculate annual operating cost.
   *
   * @param electricityCostPerKWh electricity cost in $/kWh
   * @param steamCostPerTonne steam cost in $/tonne
   * @param coolingWaterCostPerM3 cooling water cost in $/m3
   * @param operatingHoursPerYear annual operating hours
   * @return annual operating cost in USD
   */
  public double calcAnnualOperatingCost(double electricityCostPerKWh, double steamCostPerTonne,
      double coolingWaterCostPerM3, int operatingHoursPerYear) {
    // Base implementation - override in subclasses for equipment-specific costs
    double maintenanceCost = purchasedEquipmentCost * 0.05; // 5% of PEC annually
    annualOperatingCost = maintenanceCost;
    return annualOperatingCost;
  }

  // ============================================================================
  // Bill of Materials Methods
  // ============================================================================

  /**
   * Generate bill of materials for the equipment.
   *
   * @return list of BOM items as maps
   */
  public List<Map<String, Object>> generateBillOfMaterials() {
    List<Map<String, Object>> bom = new ArrayList<Map<String, Object>>();

    if (mechanicalEquipment == null) {
      return bom;
    }

    // Vessel/shell
    if (mechanicalEquipment.getWeigthVesselShell() > 0) {
      Map<String, Object> shell = new LinkedHashMap<String, Object>();
      shell.put("item", "Vessel Shell");
      shell.put("material", mechanicalEquipment.getConstrutionMaterial());
      shell.put("weight_kg", mechanicalEquipment.getWeigthVesselShell());
      shell.put("unit_cost_USD", mechanicalEquipment.getWeigthVesselShell() * costPerWeightUnit);
      bom.add(shell);
    }

    // Internals
    if (mechanicalEquipment.getWeigthInternals() > 0) {
      Map<String, Object> internals = new LinkedHashMap<String, Object>();
      internals.put("item", "Internals");
      internals.put("material", mechanicalEquipment.getConstrutionMaterial());
      internals.put("weight_kg", mechanicalEquipment.getWeigthInternals());
      internals.put("unit_cost_USD",
          mechanicalEquipment.getWeigthInternals() * costPerWeightUnit * 1.5);
      bom.add(internals);
    }

    // Nozzles
    if (mechanicalEquipment.getWeightNozzle() > 0) {
      Map<String, Object> nozzles = new LinkedHashMap<String, Object>();
      nozzles.put("item", "Nozzles");
      nozzles.put("material", mechanicalEquipment.getConstrutionMaterial());
      nozzles.put("weight_kg", mechanicalEquipment.getWeightNozzle());
      nozzles.put("unit_cost_USD", mechanicalEquipment.getWeightNozzle() * costPerWeightUnit * 2.0);
      bom.add(nozzles);
    }

    // Piping
    if (mechanicalEquipment.getWeightPiping() > 0) {
      Map<String, Object> piping = new LinkedHashMap<String, Object>();
      piping.put("item", "Piping");
      piping.put("material", mechanicalEquipment.getConstrutionMaterial());
      piping.put("weight_kg", mechanicalEquipment.getWeightPiping());
      piping.put("unit_cost_USD", mechanicalEquipment.getWeightPiping() * costPerWeightUnit * 1.2);
      bom.add(piping);
    }

    // E&I
    if (mechanicalEquipment.getWeightElectroInstrument() > 0) {
      Map<String, Object> ei = new LinkedHashMap<String, Object>();
      ei.put("item", "Electrical & Instrumentation");
      ei.put("weight_kg", mechanicalEquipment.getWeightElectroInstrument());
      ei.put("unit_cost_USD",
          mechanicalEquipment.getWeightElectroInstrument() * costPerWeightUnit * 3.0);
      bom.add(ei);
    }

    // Structural
    if (mechanicalEquipment.getWeightStructualSteel() > 0) {
      Map<String, Object> structural = new LinkedHashMap<String, Object>();
      structural.put("item", "Structural Steel");
      structural.put("material", "Carbon Steel");
      structural.put("weight_kg", mechanicalEquipment.getWeightStructualSteel());
      structural.put("unit_cost_USD",
          mechanicalEquipment.getWeightStructualSteel() * costPerWeightUnit * 0.8);
      bom.add(structural);
    }

    return bom;
  }

  // ============================================================================
  // JSON Export Methods
  // ============================================================================

  /**
   * Convert cost estimate to map for JSON export.
   *
   * @return map of cost data
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Equipment identification
    if (mechanicalEquipment != null && mechanicalEquipment.getProcessEquipment() != null) {
      result.put("equipmentName", mechanicalEquipment.getProcessEquipment().getName());
      result.put("equipmentType", equipmentType);
    }

    // Cost summary
    Map<String, Object> costs = new LinkedHashMap<String, Object>();
    costs.put("purchasedEquipmentCost_USD", getPurchasedEquipmentCost());
    costs.put("bareModuleCost_USD", getBareModuleCost());
    costs.put("totalModuleCost_USD", getTotalModuleCost());
    costs.put("grassRootsCost_USD", getGrassRootsCost());
    result.put("costEstimates", costs);

    // Labor
    Map<String, Object> labor = new LinkedHashMap<String, Object>();
    labor.put("installationManHours", getInstallationManHours());
    result.put("laborEstimates", labor);

    // Weight basis
    if (mechanicalEquipment != null) {
      Map<String, Object> weights = new LinkedHashMap<String, Object>();
      weights.put("totalWeight_kg", mechanicalEquipment.getWeightTotal());
      weights.put("vesselWeight_kg", mechanicalEquipment.getWeigthVesselShell());
      weights.put("internalsWeight_kg", mechanicalEquipment.getWeigthInternals());
      weights.put("pipingWeight_kg", mechanicalEquipment.getWeightPiping());
      result.put("weightBasis", weights);
    }

    // Cost factors
    Map<String, Object> factors = new LinkedHashMap<String, Object>();
    factors.put("costPerWeightUnit_USD_kg", costPerWeightUnit);
    factors.put("materialFactor", getCostCalculator().getMaterialFactor());
    factors.put("locationFactor", getCostCalculator().getLocationFactor());
    factors.put("contingencyFactor", getCostCalculator().getContingencyFactor());
    factors.put("engineeringFactor", getCostCalculator().getEngineeringFactor());
    result.put("costFactors", factors);

    // Cost index
    Map<String, Object> index = new LinkedHashMap<String, Object>();
    index.put("cepci", getCostCalculator().getCurrentCepci());
    index.put("currencyCode", getCostCalculator().getCurrencyCode());
    result.put("costIndex", index);

    return result;
  }

  /**
   * Export cost estimate to JSON format.
   *
   * @return JSON string
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Export cost estimate to compact JSON format.
   *
   * @return compact JSON string
   */
  public String toCompactJson() {
    return new GsonBuilder().serializeSpecialFloatingPointValues().create().toJson(toMap());
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Get the cost estimation calculator.
   *
   * @return cost calculator instance
   */
  public CostEstimationCalculator getCostCalculator() {
    if (costCalculator == null) {
      costCalculator = new CostEstimationCalculator();
    }
    return costCalculator;
  }

  /**
   * Set the cost estimation calculator.
   *
   * @param calculator cost calculator instance
   */
  public void setCostCalculator(CostEstimationCalculator calculator) {
    this.costCalculator = calculator;
  }

  /**
   * Get cost per weight unit.
   *
   * @return cost per kg in USD
   */
  public double getCostPerWeightUnit() {
    return costPerWeightUnit;
  }

  /**
   * Set cost per weight unit.
   *
   * @param cost cost per kg in USD
   */
  public void setCostPerWeightUnit(double cost) {
    this.costPerWeightUnit = cost;
  }

  /**
   * Get equipment type.
   *
   * @return equipment type string
   */
  public String getEquipmentType() {
    return equipmentType;
  }

  /**
   * Set equipment type.
   *
   * @param type equipment type string
   */
  public void setEquipmentType(String type) {
    this.equipmentType = type;
  }

  /**
   * Set material of construction for cost estimation.
   *
   * @param material material name (e.g., "Carbon Steel", "SS316", "Monel")
   */
  public void setMaterialOfConstruction(String material) {
    getCostCalculator().setMaterialOfConstruction(material);
  }

  /**
   * Set location factor for regional cost adjustment.
   *
   * @param factor location factor (1.0 = US Gulf Coast)
   */
  public void setLocationFactor(double factor) {
    getCostCalculator().setLocationFactor(factor);
  }

  /**
   * Set current CEPCI for cost escalation.
   *
   * @param cepci Chemical Engineering Plant Cost Index
   */
  public void setCurrentCepci(double cepci) {
    getCostCalculator().setCurrentCepci(cepci);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return Objects.hash(costPerWeightUnit, mechanicalEquipment, equipmentType);
  }

  /**
   * Get material factor for cost adjustment.
   *
   * @return material factor
   */
  public double getMaterialFactor() {
    return getCostCalculator().getMaterialFactor();
  }

  /**
   * Get material grade name.
   *
   * @return material grade
   */
  public String getMaterialGrade() {
    return getCostCalculator().getMaterialOfConstruction();
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    UnitCostEstimateBaseClass other = (UnitCostEstimateBaseClass) obj;
    return Double.doubleToLongBits(costPerWeightUnit) == Double
        .doubleToLongBits(other.costPerWeightUnit)
        && Objects.equals(mechanicalEquipment, other.mechanicalEquipment)
        && Objects.equals(equipmentType, other.equipmentType);
  }
}
