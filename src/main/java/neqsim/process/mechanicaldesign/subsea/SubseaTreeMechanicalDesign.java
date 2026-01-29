package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.SubseaTree;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for Subsea Tree (Christmas Tree) equipment.
 *
 * <p>
 * Calculates valve design, pressure containment, and structural requirements per:
 * </p>
 * <ul>
 * <li>API Spec 17D - Design and Operation of Subsea Production Systems</li>
 * <li>API RP 17A - Design and Operation of Subsea Production Systems</li>
 * <li>ISO 13628-4 - Subsea Wellhead and Tree Equipment</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaTree
 */
public class SubseaTreeMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to tree equipment. */
  private SubseaTree tree;

  /** Design standard code. */
  private String designStandardCode = "API-Spec-17D";

  // ============ Calculated Properties ============
  /** Bore wall thickness in mm. */
  private double boreWallThickness = 0.0;

  /** Tree block wall thickness in mm. */
  private double blockWallThickness = 0.0;

  /** Required test pressure in bar. */
  private double testPressure = 0.0;

  /** Connector capacity in kN. */
  private double connectorCapacity = 0.0;

  /** Valve Cv total. */
  private double totalValveCv = 0.0;

  /** Actuator force required in kN. */
  private double actuatorForce = 0.0;

  // ============ Cost Estimation Fields ============
  /** Cost estimator. */
  private transient SubseaCostEstimator costEstimator;

  /** Total estimated cost in USD. */
  private double totalCostUSD = 0.0;

  /** Equipment cost in USD. */
  private double equipmentCostUSD = 0.0;

  /** Installation cost in USD. */
  private double installationCostUSD = 0.0;

  /** Vessel days required. */
  private double vesselDays = 0.0;

  /** Total manhours. */
  private double totalManhours = 0.0;

  /**
   * Constructor.
   *
   * @param equipment tree equipment instance
   */
  public SubseaTreeMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.tree = (SubseaTree) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load from database
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (tree == null) {
      return;
    }

    calculatePressureContainment();
    calculateConnectorCapacity();
    calculateValveSizing();
    calculateActuatorSizing();
    calculateWeight();
  }

  /**
   * Calculate pressure containment requirements.
   */
  private void calculatePressureContainment() {
    double designPressure = tree.getDesignPressure();
    double boreSize = tree.getBoreSizeInches() * 25.4; // mm

    // Material properties for tree block (typically F22 or Inconel clad)
    double smys = 517.0; // MPa for F22
    double designFactor = 0.67; // Per API 17D

    // Bore wall thickness
    double pressureMPa = designPressure / 10.0;
    boreWallThickness = (pressureMPa * boreSize) / (2 * smys * designFactor);
    boreWallThickness = Math.max(boreWallThickness, 25.4); // Minimum 1 inch

    // Block wall thickness (additional for structural integrity)
    blockWallThickness = boreWallThickness * 2.0;

    // Test pressure per API 17D (1.5x design)
    testPressure = designPressure * 1.5;
  }

  /**
   * Calculate connector capacity.
   */
  private void calculateConnectorCapacity() {
    double boreSize = tree.getBoreSizeInches();
    double designPressure = tree.getDesignPressure();

    // Connector must handle pressure end load plus external loads
    double pressureEndLoad =
        Math.PI * Math.pow(boreSize * 25.4 / 2, 2) * designPressure / 10 / 1000; // kN

    // Add external load allowance (current, vessel motion, etc.)
    double externalLoad = 500.0; // kN typical

    connectorCapacity = pressureEndLoad + externalLoad;
  }

  /**
   * Calculate valve sizing.
   */
  private void calculateValveSizing() {
    double boreSize = tree.getBoreSizeInches();

    // Cv for gate valve ≈ 28 * d² (full bore)
    double valveCv = 28 * boreSize * boreSize;

    // PMV, PWV, choke
    totalValveCv = valveCv * 2; // PMV and PWV in series
    // Choke Cv depends on opening
  }

  /**
   * Calculate actuator sizing.
   */
  private void calculateActuatorSizing() {
    double designPressure = tree.getDesignPressure();
    double boreSize = tree.getBoreSizeInches() * 25.4; // mm

    // Force to overcome pressure differential
    double seatArea = Math.PI * Math.pow(boreSize / 2, 2) / 1e6; // m²
    double pressureForce = designPressure * 100 * seatArea; // kN

    // Add friction and safety factor
    actuatorForce = pressureForce * 1.5;
  }

  /**
   * Calculate weight.
   */
  private void calculateWeight() {
    double boreSize = tree.getBoreSizeInches();
    double designPressure = tree.getDesignPressure();

    // Empirical weight estimation based on pressure rating and bore size
    double baseWeight = 20.0; // Base weight in tonnes

    // Scale with pressure rating
    double pressureFactor = designPressure / 690; // Normalize to 10k psi

    // Scale with bore size
    double sizeFactor = boreSize / 5.0; // Normalize to 5"

    double estimatedWeight = baseWeight * pressureFactor * Math.pow(sizeFactor, 1.5);

    // Add for tree type
    if (tree.getTreeType() == SubseaTree.TreeType.HORIZONTAL) {
      estimatedWeight *= 1.1; // Horizontal trees slightly heavier
    } else if (tree.getTreeType() == SubseaTree.TreeType.DUAL_BORE) {
      estimatedWeight *= 1.6; // Dual bore significantly heavier
    }

    tree.setDryWeight(estimatedWeight);
    tree.setSubmergedWeight(estimatedWeight * 0.87);
  }

  /**
   * Calculate cost estimate for the subsea tree.
   */
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    Map<String, Object> costResult = costEstimator.calculateTreeCost(tree.getWaterDepth(),
        tree.getDryWeight(), tree.getBoreSizeInches(), tree.getPressureRating().name());

    totalCostUSD = ((Number) costResult.get("totalCost")).doubleValue();
    equipmentCostUSD = ((Number) costResult.get("equipmentCost")).doubleValue();
    installationCostUSD = ((Number) costResult.get("installationCost")).doubleValue();
    vesselDays = ((Number) costResult.get("vesselDays")).doubleValue();
    totalManhours = ((Number) costResult.get("totalManhours")).doubleValue();
  }

  /**
   * Get cost breakdown.
   *
   * @return map of cost categories
   */
  public Map<String, Object> getCostBreakdown() {
    if (costEstimator == null) {
      calculateCostEstimate();
    }
    return costEstimator.calculateTreeCost(tree.getWaterDepth(), tree.getDryWeight(),
        tree.getBoreSizeInches(), tree.getPressureRating().name());
  }

  /**
   * Generate bill of materials.
   *
   * @return list of BOM items
   */
  public List<Map<String, Object>> generateBillOfMaterials() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }
    return costEstimator.generateBOM("SubseaTree", tree.getDryWeight(), tree.getWaterDepth());
  }

  /**
   * Get total cost USD.
   *
   * @return total cost
   */
  public double getTotalCostUSD() {
    return totalCostUSD;
  }

  /**
   * Get equipment cost USD.
   *
   * @return equipment cost
   */
  public double getEquipmentCostUSD() {
    return equipmentCostUSD;
  }

  /**
   * Get installation cost USD.
   *
   * @return installation cost
   */
  public double getInstallationCostUSD() {
    return installationCostUSD;
  }

  /**
   * Get vessel days.
   *
   * @return vessel days
   */
  public double getVesselDays() {
    return vesselDays;
  }

  /**
   * Get total manhours.
   *
   * @return total manhours
   */
  public double getTotalManhours() {
    return totalManhours;
  }

  /**
   * Set region for cost estimation.
   *
   * @param region cost region
   */
  public void setRegion(SubseaCostEstimator.Region region) {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }
    costEstimator.setRegion(region);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    JsonObject jsonObj = JsonParser.parseString(response.toJson()).getAsJsonObject();

    jsonObj.addProperty("equipmentType", "SubseaTree");
    jsonObj.addProperty("designStandardCode", designStandardCode);

    JsonObject config = new JsonObject();
    config.addProperty("treeType", tree.getTreeType().name());
    config.addProperty("pressureRating", tree.getPressureRating().name());
    config.addProperty("boreSizeInches", tree.getBoreSizeInches());
    config.addProperty("actuatorType", tree.getActuatorType());
    config.addProperty("failSafeClose", tree.isFailSafeClose());
    jsonObj.add("configuration", config);

    JsonObject design = new JsonObject();
    design.addProperty("designPressureBar", tree.getDesignPressure());
    design.addProperty("designTemperatureC", tree.getDesignTemperature());
    design.addProperty("waterDepthM", tree.getWaterDepth());
    jsonObj.add("designParameters", design);

    JsonObject results = new JsonObject();
    results.addProperty("boreWallThicknessMm", boreWallThickness);
    results.addProperty("blockWallThicknessMm", blockWallThickness);
    results.addProperty("testPressureBar", testPressure);
    results.addProperty("connectorCapacityKN", connectorCapacity);
    results.addProperty("totalValveCv", totalValveCv);
    results.addProperty("actuatorForceKN", actuatorForce);
    jsonObj.add("calculatedResults", results);

    JsonObject weight = new JsonObject();
    weight.addProperty("dryWeightTonnes", tree.getDryWeight());
    weight.addProperty("submergedWeightTonnes", tree.getSubmergedWeight());
    weight.addProperty("heightM", tree.getTreeHeight());
    weight.addProperty("diameterM", tree.getTreeDiameter());
    jsonObj.add("weight", weight);

    // Cost estimation
    JsonObject cost = new JsonObject();
    cost.addProperty("totalCostUSD", totalCostUSD);
    cost.addProperty("equipmentCostUSD", equipmentCostUSD);
    cost.addProperty("installationCostUSD", installationCostUSD);
    cost.addProperty("vesselDays", vesselDays);
    cost.addProperty("totalManhours", totalManhours);
    if (costEstimator != null) {
      cost.addProperty("region", costEstimator.getRegion().name());
      cost.addProperty("currency", costEstimator.getCurrency().name());
    }
    jsonObj.add("costEstimation", cost);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(jsonObj);
  }

  /**
   * Get design results as Map.
   *
   * @return design results map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("equipmentName", tree.getName());
    result.put("equipmentType", "SubseaTree");
    result.put("treeType", tree.getTreeType().name());
    result.put("boreWallThicknessMm", boreWallThickness);
    result.put("blockWallThicknessMm", blockWallThickness);
    result.put("testPressureBar", testPressure);
    result.put("connectorCapacityKN", connectorCapacity);
    result.put("actuatorForceKN", actuatorForce);
    return result;
  }

  // Getters

  /**
   * Get design standard code.
   *
   * @return design standard code
   */
  public String getDesignStandardCode() {
    return designStandardCode;
  }

  /**
   * Set design standard code.
   *
   * @param designStandardCode design standard code
   */
  public void setDesignStandardCode(String designStandardCode) {
    this.designStandardCode = designStandardCode;
  }

  /**
   * Get bore wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getBoreWallThickness() {
    return boreWallThickness;
  }

  /**
   * Get test pressure.
   *
   * @return test pressure in bar
   */
  public double getTestPressure() {
    return testPressure;
  }

  /**
   * Get connector capacity.
   *
   * @return capacity in kN
   */
  public double getConnectorCapacity() {
    return connectorCapacity;
  }
}
