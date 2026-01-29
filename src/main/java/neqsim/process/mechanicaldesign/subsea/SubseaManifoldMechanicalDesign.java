package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.SubseaManifold;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for Subsea Manifold equipment.
 *
 * <p>
 * Calculates structural design for subsea manifolds per applicable standards:
 * </p>
 * <ul>
 * <li>API RP 17G - Subsea Production System Design</li>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems</li>
 * <li>NORSOK U-001 - Subsea Production Systems</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaManifold
 */
public class SubseaManifoldMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to manifold equipment. */
  private SubseaManifold manifold;

  /** Design standard code. */
  private String designStandardCode = "API-RP-17G";

  /** Material grade for structure. */
  private String structureMaterialGrade = "S355";

  /** Header material grade. */
  private String headerMaterialGrade = "X65";

  // ============ Calculated Properties ============
  /** Production header wall thickness in mm. */
  private double productionHeaderWallThickness = 0.0;

  /** Test header wall thickness in mm. */
  private double testHeaderWallThickness = 0.0;

  /** Required foundation weight in tonnes. */
  private double requiredFoundationWeight = 0.0;

  /** Required mudmat area in m². */
  private double requiredMudmatArea = 0.0;

  /** Structure weight in tonnes. */
  private double structureWeight = 0.0;

  /** Piping weight in tonnes. */
  private double pipingWeight = 0.0;

  /** Valves weight in tonnes. */
  private double valvesWeight = 0.0;

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
   * @param equipment manifold equipment instance
   */
  public SubseaManifoldMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.manifold = (SubseaManifold) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load from database
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (manifold == null) {
      return;
    }

    calculateHeaderWallThickness();
    calculateFoundationRequirements();
    calculateDetailedWeight();
  }

  /**
   * Calculate header wall thickness.
   */
  private void calculateHeaderWallThickness() {
    double designPressure = manifold.getDesignPressure();
    double prodHeaderSize = manifold.getProductionHeaderSizeInches() * 25.4;

    double smys = 450.0; // MPa for X65
    double designFactor = 0.72;
    double pressureMPa = designPressure / 10.0;

    // Production header
    double prodOD = prodHeaderSize * 1.25;
    productionHeaderWallThickness = (pressureMPa * prodOD) / (2 * smys * designFactor);
    productionHeaderWallThickness += 3.0; // Corrosion allowance
    productionHeaderWallThickness = Math.max(productionHeaderWallThickness, 12.7);

    // Test header (if present)
    if (manifold.getManifoldType() == SubseaManifold.ManifoldType.PRODUCTION_TEST
        || manifold.getManifoldType() == SubseaManifold.ManifoldType.FULL_SERVICE) {
      double testHeaderSize = manifold.getTestHeaderSizeInches() * 25.4;
      double testOD = testHeaderSize * 1.25;
      testHeaderWallThickness = (pressureMPa * testOD) / (2 * smys * designFactor);
      testHeaderWallThickness += 3.0;
      testHeaderWallThickness = Math.max(testHeaderWallThickness, 9.52);
    }
  }

  /**
   * Calculate foundation requirements.
   */
  private void calculateFoundationRequirements() {
    double maxBearingPressure = 50.0; // kPa

    // Calculate total weight first
    calculateDetailedWeight();

    double totalWeight = structureWeight + pipingWeight + valvesWeight;
    double submergedWeight = totalWeight * 0.87 * 1000; // kg

    double safetyFactor = 2.0;
    double requiredCapacity = submergedWeight * 9.81 / 1000; // kN
    requiredMudmatArea = (requiredCapacity * safetyFactor) / maxBearingPressure;

    // Minimum based on well slots
    double minArea = manifold.getNumberOfSlots() * 6.0; // 6 m² per slot
    requiredMudmatArea = Math.max(requiredMudmatArea, minArea);

    // Environmental load
    double horizontalLoad = estimateEnvironmentalLoad();
    double frictionCoeff = 0.6;
    double requiredSubmerged = (horizontalLoad * safetyFactor) / frictionCoeff;
    requiredFoundationWeight = requiredSubmerged / 9.81 * 1.15;
  }

  /**
   * Estimate environmental load.
   *
   * @return load in kN
   */
  private double estimateEnvironmentalLoad() {
    double currentVelocity = 0.5;
    double seawaterDensity = 1025.0;
    double dragCoeff = 1.2;
    double projectedArea = manifold.getStructureLength() * manifold.getStructureWidth() * 0.6;

    return 0.5 * seawaterDensity * currentVelocity * currentVelocity * dragCoeff * projectedArea
        / 1000;
  }

  /**
   * Calculate detailed weight breakdown.
   */
  private void calculateDetailedWeight() {
    double steelDensity = 7850.0; // kg/m³

    // Structure weight
    double footprint = manifold.getStructureLength() * manifold.getStructureWidth();
    double height = manifold.getStructureHeight();
    double structureVolume = footprint * height * 0.05;
    structureWeight = structureVolume * steelDensity / 1000;

    // Piping weight
    int wellSlots = manifold.getNumberOfSlots();
    double prodHeaderLength = wellSlots * 2.0; // 2m per slot
    double prodHeaderSize = manifold.getProductionHeaderSizeInches() * 25.4 / 1000;
    double headerArea = Math.PI * prodHeaderSize * productionHeaderWallThickness / 1000;
    pipingWeight = headerArea * prodHeaderLength * steelDensity / 1000;

    // Add test header if present
    if (testHeaderWallThickness > 0) {
      double testHeaderSize = manifold.getTestHeaderSizeInches() * 25.4 / 1000;
      double testHeaderArea = Math.PI * testHeaderSize * testHeaderWallThickness / 1000;
      pipingWeight += testHeaderArea * prodHeaderLength * steelDensity / 1000;
    }

    // Valves weight (approximate 1.5 tonnes per valve)
    int numValves = wellSlots * 2; // Production and test valve per well
    if (manifold.getManifoldType() == SubseaManifold.ManifoldType.FULL_SERVICE) {
      numValves += wellSlots; // Additional service valves
    }
    valvesWeight = numValves * 1.5;

    // Update manifold
    double totalWeight = structureWeight + pipingWeight + valvesWeight;
    manifold.setDryWeight(totalWeight);
    manifold.setSubmergedWeight(totalWeight * 0.87);
  }

  /**
   * Calculate cost estimate for the manifold.
   */
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    boolean hasTestHeader =
        manifold.getManifoldType() == SubseaManifold.ManifoldType.PRODUCTION_TEST
            || manifold.getManifoldType() == SubseaManifold.ManifoldType.FULL_SERVICE;

    costEstimator.calculateManifoldCost(manifold.getNumberOfSlots(), manifold.getDryWeight(),
        manifold.getWaterDepth(), hasTestHeader);

    totalCostUSD = costEstimator.getTotalCost();
    equipmentCostUSD = costEstimator.getEquipmentCost();
    installationCostUSD = costEstimator.getInstallationCost();
    vesselDays = costEstimator.getVesselDays();
    totalManhours = costEstimator.getTotalManhours();
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
    Map<String, Object> breakdown = new java.util.HashMap<>();
    breakdown.put("totalCost", totalCostUSD);
    breakdown.put("equipmentCost", equipmentCostUSD);
    breakdown.put("installationCost", installationCostUSD);
    breakdown.put("vesselDays", vesselDays);
    breakdown.put("totalManhours", totalManhours);
    return breakdown;
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
    return costEstimator.generateBOM("SubseaManifold", manifold.getDryWeight(),
        manifold.getWaterDepth());
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

    jsonObj.addProperty("equipmentType", "SubseaManifold");
    jsonObj.addProperty("designStandardCode", designStandardCode);

    JsonObject config = new JsonObject();
    config.addProperty("manifoldType", manifold.getManifoldType().name());
    config.addProperty("numberOfWellSlots", manifold.getNumberOfSlots());
    config.addProperty("productionHeaderSizeInches", manifold.getProductionHeaderSizeInches());
    jsonObj.add("configuration", config);

    JsonObject results = new JsonObject();
    results.addProperty("productionHeaderWallThicknessMm", productionHeaderWallThickness);
    results.addProperty("testHeaderWallThicknessMm", testHeaderWallThickness);
    results.addProperty("requiredFoundationWeightTonnes", requiredFoundationWeight);
    results.addProperty("requiredMudmatAreaM2", requiredMudmatArea);
    jsonObj.add("calculatedResults", results);

    JsonObject weightBreakdown = new JsonObject();
    weightBreakdown.addProperty("structureWeightTonnes", structureWeight);
    weightBreakdown.addProperty("pipingWeightTonnes", pipingWeight);
    weightBreakdown.addProperty("valvesWeightTonnes", valvesWeight);
    weightBreakdown.addProperty("totalDryWeightTonnes", manifold.getDryWeight());
    weightBreakdown.addProperty("submergedWeightTonnes", manifold.getSubmergedWeight());
    jsonObj.add("weightBreakdown", weightBreakdown);

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
    result.put("equipmentName", manifold.getName());
    result.put("equipmentType", "SubseaManifold");
    result.put("productionHeaderWallThicknessMm", productionHeaderWallThickness);
    result.put("testHeaderWallThicknessMm", testHeaderWallThickness);
    result.put("requiredFoundationWeightTonnes", requiredFoundationWeight);
    result.put("requiredMudmatAreaM2", requiredMudmatArea);
    result.put("structureWeightTonnes", structureWeight);
    result.put("pipingWeightTonnes", pipingWeight);
    result.put("valvesWeightTonnes", valvesWeight);
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
   * Get production header wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getProductionHeaderWallThickness() {
    return productionHeaderWallThickness;
  }

  /**
   * Get test header wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getTestHeaderWallThickness() {
    return testHeaderWallThickness;
  }
}
