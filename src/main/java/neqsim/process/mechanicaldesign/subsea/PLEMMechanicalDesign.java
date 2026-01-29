package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.PLEM;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for PLEM (Pipeline End Manifold) equipment.
 *
 * <p>
 * Calculates structural design for PLEM structures per applicable standards.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 * @see PLEM
 */
public class PLEMMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to PLEM equipment. */
  private PLEM plem;

  /** Design standard code. */
  private String designStandardCode = "DNV-ST-F101";

  /** Material grade for structure. */
  private String structureMaterialGrade = "S355";

  // ============ Calculated Properties ============
  /** Header wall thickness in mm. */
  private double headerWallThickness = 0.0;

  /** Required foundation weight in tonnes. */
  private double requiredFoundationWeight = 0.0;

  /** Required mudmat area in m². */
  private double requiredMudmatArea = 0.0;

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
   * @param equipment PLEM equipment instance
   */
  public PLEMMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.plem = (PLEM) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load from database
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (plem == null) {
      return;
    }

    calculateHeaderWallThickness();
    calculateFoundationRequirements();
    calculateWeight();
  }

  /**
   * Calculate header wall thickness.
   */
  private void calculateHeaderWallThickness() {
    double designPressure = plem.getDesignPressure();
    double headerSize = plem.getHeaderSizeInches() * 25.4;
    double outerDiameter = headerSize * 1.3;

    double smys = 414.0; // MPa for typical carbon steel
    double designFactor = 0.72;

    double pressureMPa = designPressure / 10.0;
    headerWallThickness = (pressureMPa * outerDiameter) / (2 * smys * designFactor);
    headerWallThickness += 3.0; // Corrosion allowance
    headerWallThickness = Math.max(headerWallThickness, 12.7);
  }

  /**
   * Calculate foundation requirements.
   */
  private void calculateFoundationRequirements() {
    double submergedWeight = plem.getSubmergedWeight() * 1000;
    double maxBearingPressure = 50.0; // kPa for soft clay

    double safetyFactor = 2.0;
    double requiredCapacity = submergedWeight * 9.81 / 1000;
    requiredMudmatArea = (requiredCapacity * safetyFactor) / maxBearingPressure;
    requiredMudmatArea = Math.max(requiredMudmatArea, 25.0); // Minimum 5m x 5m

    // Environmental loads
    double horizontalLoad = estimateEnvironmentalLoad();
    double frictionCoeff = 0.6;
    double requiredSubmergedWeight = (horizontalLoad * safetyFactor) / frictionCoeff;
    requiredFoundationWeight = requiredSubmergedWeight / 9.81 * 1.15;
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
    double projectedArea = plem.getStructureLength() * plem.getStructureWidth() * 0.5;

    return 0.5 * seawaterDensity * currentVelocity * currentVelocity * dragCoeff * projectedArea
        / 1000;
  }

  /**
   * Calculate weight.
   */
  private void calculateWeight() {
    double footprint = plem.getStructureLength() * plem.getStructureWidth();
    double height = plem.getStructureHeight();
    double steelDensity = 7850.0;

    double structureVolume = footprint * height * 0.06;
    double structureWeight = structureVolume * steelDensity / 1000;

    // Add slots weight
    structureWeight += plem.getNumberOfSlots() * 0.5;

    // Add valves
    structureWeight += plem.getNumberOfSlots() * 1.5;

    plem.setDryWeight(structureWeight);
    plem.setSubmergedWeight(structureWeight * 0.87);
  }

  /**
   * Calculate cost estimate for the PLEM.
   */
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    Map<String, Object> costResult = costEstimator.calculatePLETCost(plem.getWaterDepth(),
        plem.getDryWeight(), plem.getNumberOfSlots() > 2);

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
    return costEstimator.calculatePLETCost(plem.getWaterDepth(), plem.getDryWeight(),
        plem.getNumberOfSlots() > 2);
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
    return costEstimator.generateBOM("PLEM", plem.getDryWeight(), plem.getWaterDepth());
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

    jsonObj.addProperty("equipmentType", "PLEM");
    jsonObj.addProperty("designStandardCode", designStandardCode);
    jsonObj.addProperty("structureMaterialGrade", structureMaterialGrade);

    JsonObject config = new JsonObject();
    config.addProperty("configurationType", plem.getConfigurationType().name());
    config.addProperty("numberOfSlots", plem.getNumberOfSlots());
    config.addProperty("headerSizeInches", plem.getHeaderSizeInches());
    jsonObj.add("configuration", config);

    JsonObject results = new JsonObject();
    results.addProperty("headerWallThicknessMm", headerWallThickness);
    results.addProperty("requiredFoundationWeightTonnes", requiredFoundationWeight);
    results.addProperty("requiredMudmatAreaM2", requiredMudmatArea);
    jsonObj.add("calculatedResults", results);

    JsonObject weight = new JsonObject();
    weight.addProperty("dryWeightTonnes", plem.getDryWeight());
    weight.addProperty("submergedWeightTonnes", plem.getSubmergedWeight());
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
    result.put("equipmentName", plem.getName());
    result.put("equipmentType", "PLEM");
    result.put("headerWallThicknessMm", headerWallThickness);
    result.put("requiredFoundationWeightTonnes", requiredFoundationWeight);
    result.put("requiredMudmatAreaM2", requiredMudmatArea);
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
   * Get header wall thickness.
   *
   * @return header wall thickness in mm
   */
  public double getHeaderWallThickness() {
    return headerWallThickness;
  }
}
