package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.PLET;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for PLET (Pipeline End Termination) equipment.
 *
 * <p>
 * Calculates structural design, foundation requirements, and connection sizing for PLET structures
 * per applicable standards:
 * </p>
 * <ul>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems</li>
 * <li>API RP 17G - Subsea Production System Design</li>
 * <li>DNV-RP-F109 - On-Bottom Stability Design</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see PLET
 */
public class PLETMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to PLET equipment. */
  private PLET plet;

  /** Design standard code. */
  private String designStandardCode = "DNV-ST-F101";

  /** Material grade for structure. */
  private String structureMaterialGrade = "S355";

  /** Hub material grade. */
  private String hubMaterialGrade = "F22-Inconel625";

  // ============ Calculated Properties ============
  /** Required foundation weight in tonnes. */
  private double requiredFoundationWeight = 0.0;

  /** Required mudmat area in m². */
  private double requiredMudmatArea = 0.0;

  /** Maximum allowable bearing pressure in kPa. */
  private double maxBearingPressure = 50.0;

  /** Hub wall thickness in mm. */
  private double hubWallThickness = 0.0;

  /** Connector load capacity in kN. */
  private double connectorLoadCapacity = 0.0;

  /** Calculated pile penetration in meters. */
  private double pileDepth = 0.0;

  /** Calculated suction anchor diameter in meters. */
  private double suctionAnchorDiameter = 0.0;

  // ============ Cost Estimation ============
  /** Cost estimator instance. */
  private SubseaCostEstimator costEstimator;

  /** Total project cost in USD. */
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
   * @param equipment PLET equipment instance
   */
  public PLETMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.plet = (PLET) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load design specifications from database
    // Would query TechnicalRequirements_Process.csv and standards tables
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (plet == null) {
      return;
    }

    // Calculate hub wall thickness for pressure containment
    calculateHubWallThickness();

    // Calculate foundation requirements
    calculateFoundationRequirements();

    // Calculate connector capacity
    calculateConnectorCapacity();

    // Calculate weight
    calculateWeight();

    // Calculate cost estimate
    calculateCostEstimate();
  }

  /**
   * Calculate hub wall thickness per DNV-ST-F101.
   */
  private void calculateHubWallThickness() {
    double designPressure = plet.getDesignPressure();
    double hubSize = plet.getHubSizeInches() * 25.4; // Convert to mm
    double outerDiameter = hubSize * 1.5; // Approximate OD

    // SMYS for F22 is approximately 414 MPa
    double smys = 414.0; // MPa
    double designFactor = 0.72;
    double jointFactor = 1.0;
    double tempDeratingFactor = 1.0;

    if (plet.getDesignTemperature() > 50) {
      // Temperature derating
      tempDeratingFactor = 1.0 - (plet.getDesignTemperature() - 50) * 0.001;
    }

    // Wall thickness calculation (Barlow's formula modified per DNV)
    // t = P * D / (2 * SMYS * DF * JF * TF)
    double pressureMPa = designPressure / 10.0; // Convert bar to MPa
    hubWallThickness = (pressureMPa * outerDiameter)
        / (2 * smys * designFactor * jointFactor * tempDeratingFactor);

    // Add corrosion allowance (3mm typical)
    hubWallThickness += 3.0;

    // Minimum wall thickness
    hubWallThickness = Math.max(hubWallThickness, 12.7); // Minimum 0.5 inch
  }

  /**
   * Calculate foundation requirements.
   */
  private void calculateFoundationRequirements() {
    double waterDepth = plet.getWaterDepth();
    double dryWeight = plet.getDryWeight() * 1000; // Convert to kg
    double submergedWeight = plet.getSubmergedWeight() * 1000;

    // Soil bearing capacity (assume soft clay, 50 kPa)
    maxBearingPressure = 50.0; // kPa

    // Calculate required mudmat area for gravity base
    // Includes safety factor of 2.0
    double safetyFactor = 2.0;
    double requiredCapacity = submergedWeight * 9.81 / 1000; // kN
    requiredMudmatArea = (requiredCapacity * safetyFactor) / maxBearingPressure; // m²

    // Calculate required foundation weight for stability
    // Consider current loads, wave-induced loads, etc.
    double horizontalLoad = estimateEnvironmentalLoad();
    double frictionCoeff = 0.6; // Typical for clay

    // Required submerged weight for sliding stability
    double requiredSubmergedWeight = (horizontalLoad * safetyFactor) / frictionCoeff; // kN
    requiredFoundationWeight = requiredSubmergedWeight / 9.81 * 1.15; // tonnes (air weight)

    // Ensure minimum footprint
    if (requiredMudmatArea < 16.0) {
      requiredMudmatArea = 16.0; // Minimum 4m x 4m
    }

    // Calculate pile depth if piled
    if (plet.getStructureType() == PLET.StructureType.PILED) {
      calculatePileRequirements();
    }

    // Calculate suction anchor if applicable
    if (plet.getStructureType() == PLET.StructureType.SUCTION_ANCHOR) {
      calculateSuctionAnchorRequirements();
    }
  }

  /**
   * Estimate environmental horizontal load.
   *
   * @return horizontal load in kN
   */
  private double estimateEnvironmentalLoad() {
    double waterDepth = plet.getWaterDepth();

    // Simplified current load calculation
    double currentVelocity = 0.5; // m/s typical
    double seawaterDensity = 1025.0; // kg/m³
    double dragCoeff = 1.2;

    // Projected area (approximate)
    double projectedArea = plet.getFootprintArea() * 0.5; // m²

    // Drag force
    double currentLoad = 0.5 * seawaterDensity * currentVelocity * currentVelocity * dragCoeff
        * projectedArea / 1000; // kN

    // Add wave load if shallow water (simplified)
    double waveLoad = 0.0;
    if (waterDepth < 200) {
      waveLoad = currentLoad * 0.5; // Approximate
    }

    return currentLoad + waveLoad;
  }

  /**
   * Calculate pile requirements.
   */
  private void calculatePileRequirements() {
    double verticalLoad = plet.getSubmergedWeight() * 9.81; // kN
    double horizontalLoad = estimateEnvironmentalLoad();

    // Soil properties (assume medium clay)
    double soilUndrained = 50.0; // kPa
    double pileDiameter = 0.914; // 36 inch pile

    // Required pile length for axial capacity (simplified)
    double skinFriction = soilUndrained * 0.3; // Reduced for long-term
    double pilePerimeter = Math.PI * pileDiameter;
    pileDepth = (verticalLoad * 2.0) / (skinFriction * pilePerimeter);

    // Minimum penetration
    pileDepth = Math.max(pileDepth, 10.0);
  }

  /**
   * Calculate suction anchor requirements.
   */
  private void calculateSuctionAnchorRequirements() {
    double verticalLoad = plet.getSubmergedWeight() * 9.81; // kN

    // Suction anchor capacity depends on soil shear strength
    double soilUndrained = 50.0; // kPa

    // Simplified sizing (L/D ratio of 5)
    double ld_ratio = 5.0;
    double safetyFactor = 2.0;

    // Required capacity
    double requiredCapacity = verticalLoad * safetyFactor;

    // Capacity = π * D * L * Su * α + π * D²/4 * Nc * Su (skin + tip)
    // Simplified: solve for D
    double alpha = 0.5; // Skin friction factor
    double Nc = 9.0; // Bearing capacity factor

    // D = sqrt(requiredCapacity / (π * Su * (L/D * α + Nc/4)))
    suctionAnchorDiameter =
        Math.sqrt(requiredCapacity / (Math.PI * soilUndrained * (ld_ratio * alpha + Nc / 4.0)));

    // Minimum diameter
    suctionAnchorDiameter = Math.max(suctionAnchorDiameter, 2.0);
  }

  /**
   * Calculate connector load capacity.
   */
  private void calculateConnectorCapacity() {
    double hubSize = plet.getHubSizeInches();

    // Connector capacity depends on hub size and type
    // Values based on typical hub capacities
    switch (plet.getConnectionType()) {
      case VERTICAL_HUB:
      case HORIZONTAL_HUB:
        // Cameron/Vetco type hubs
        connectorLoadCapacity = hubSize * 100; // Approximate kN per inch
        break;
      case CLAMP_CONNECTOR:
        connectorLoadCapacity = hubSize * 80;
        break;
      case COLLET_CONNECTOR:
        connectorLoadCapacity = hubSize * 120;
        break;
      case DIVER_FLANGE:
        connectorLoadCapacity = hubSize * 60;
        break;
      default:
        connectorLoadCapacity = hubSize * 80;
    }
  }

  /**
   * Calculate total weight.
   */
  private void calculateWeight() {
    double structureWeight = 0.0;

    // Base structure weight
    double footprint = plet.getFootprintArea();
    double height = plet.getHeight();
    double steelDensity = 7850.0; // kg/m³

    // Estimate steel volume (very approximate)
    double structureVolume = footprint * height * 0.05; // 5% steel fill
    structureWeight += structureVolume * steelDensity / 1000; // tonnes

    // Add hub weight
    double hubSize = plet.getHubSizeInches() * 25.4 / 1000; // meters
    double hubWeight = Math.PI * hubSize * hubSize * 0.3 * steelDensity / 1000; // tonnes
    structureWeight += hubWeight;

    // Add valve weight (if isolation valve)
    if (plet.hasIsolationValve()) {
      structureWeight += 2.0; // Approximate valve weight
    }

    // Add pigging facilities
    if (plet.hasPiggingFacilities()) {
      structureWeight += 1.5; // Approximate
    }

    // Update PLET weights
    plet.setDryWeight(structureWeight);
    plet.setSubmergedWeight(structureWeight * 0.87); // Approximate buoyancy
  }

  /**
   * Calculate cost estimate for PLET.
   */
  private void calculateCostEstimate() {
    costEstimator = new SubseaCostEstimator(SubseaCostEstimator.Region.NORWAY);

    costEstimator.calculatePLETCost(plet.getDryWeight(), plet.getHubSizeInches(),
        plet.getWaterDepth(), plet.hasIsolationValve(), plet.hasPiggingFacilities());

    totalCostUSD = costEstimator.getTotalCost();
    equipmentCostUSD = costEstimator.getEquipmentCost();
    installationCostUSD = costEstimator.getInstallationCost();
    vesselDays = costEstimator.getVesselDays();
    totalManhours = costEstimator.getTotalManhours();
  }

  /**
   * Get cost breakdown as Map.
   *
   * @return cost breakdown map
   */
  public Map<String, Object> getCostBreakdown() {
    if (costEstimator == null) {
      calculateCostEstimate();
    }
    return costEstimator.getCostBreakdown();
  }

  /**
   * Generate bill of materials.
   *
   * @return list of BOM items
   */
  public List<Map<String, Object>> generateBillOfMaterials() {
    if (costEstimator == null) {
      calculateCostEstimate();
    }
    return costEstimator.generateBillOfMaterials("PLET", toMap());
  }

  /**
   * Get total project cost.
   *
   * @return total cost in USD
   */
  public double getTotalCostUSD() {
    return totalCostUSD;
  }

  /**
   * Get equipment cost.
   *
   * @return equipment cost in USD
   */
  public double getEquipmentCostUSD() {
    return equipmentCostUSD;
  }

  /**
   * Get installation cost.
   *
   * @return installation cost in USD
   */
  public double getInstallationCostUSD() {
    return installationCostUSD;
  }

  /**
   * Get vessel days required.
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

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    JsonObject jsonObj = JsonParser.parseString(response.toJson()).getAsJsonObject();

    // Add PLET-specific properties
    jsonObj.addProperty("equipmentType", "PLET");
    jsonObj.addProperty("designStandardCode", designStandardCode);
    jsonObj.addProperty("structureMaterialGrade", structureMaterialGrade);
    jsonObj.addProperty("hubMaterialGrade", hubMaterialGrade);

    // Configuration
    JsonObject config = new JsonObject();
    config.addProperty("connectionType", plet.getConnectionType().name());
    config.addProperty("structureType", plet.getStructureType().name());
    config.addProperty("hubSizeInches", plet.getHubSizeInches());
    config.addProperty("hasIsolationValve", plet.hasIsolationValve());
    config.addProperty("hasPiggingFacilities", plet.hasPiggingFacilities());
    config.addProperty("hasFutureTieIn", plet.hasFutureTieIn());
    jsonObj.add("configuration", config);

    // Design parameters
    JsonObject design = new JsonObject();
    design.addProperty("waterDepthM", plet.getWaterDepth());
    design.addProperty("designPressureBar", plet.getDesignPressure());
    design.addProperty("designTemperatureC", plet.getDesignTemperature());
    jsonObj.add("designParameters", design);

    // Calculated results
    JsonObject results = new JsonObject();
    results.addProperty("hubWallThicknessMm", hubWallThickness);
    results.addProperty("requiredFoundationWeightTonnes", requiredFoundationWeight);
    results.addProperty("requiredMudmatAreaM2", requiredMudmatArea);
    results.addProperty("maxBearingPressureKPa", maxBearingPressure);
    results.addProperty("connectorLoadCapacityKN", connectorLoadCapacity);
    if (plet.getStructureType() == PLET.StructureType.PILED) {
      results.addProperty("pileDepthM", pileDepth);
    }
    if (plet.getStructureType() == PLET.StructureType.SUCTION_ANCHOR) {
      results.addProperty("suctionAnchorDiameterM", suctionAnchorDiameter);
    }
    jsonObj.add("calculatedResults", results);

    // Weight
    JsonObject weight = new JsonObject();
    weight.addProperty("dryWeightTonnes", plet.getDryWeight());
    weight.addProperty("submergedWeightTonnes", plet.getSubmergedWeight());
    weight.addProperty("heightM", plet.getHeight());
    weight.addProperty("footprintAreaM2", plet.getFootprintArea());
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

    result.put("equipmentName", plet.getName());
    result.put("equipmentType", "PLET");
    result.put("connectionType", plet.getConnectionType().name());
    result.put("structureType", plet.getStructureType().name());
    result.put("designStandardCode", designStandardCode);

    // Design parameters
    Map<String, Object> designParams = new LinkedHashMap<String, Object>();
    designParams.put("waterDepthM", plet.getWaterDepth());
    designParams.put("designPressureBar", plet.getDesignPressure());
    designParams.put("designTemperatureC", plet.getDesignTemperature());
    designParams.put("hubSizeInches", plet.getHubSizeInches());
    result.put("designParameters", designParams);

    // Calculated results
    Map<String, Object> calcResults = new LinkedHashMap<String, Object>();
    calcResults.put("hubWallThicknessMm", hubWallThickness);
    calcResults.put("requiredFoundationWeightTonnes", requiredFoundationWeight);
    calcResults.put("requiredMudmatAreaM2", requiredMudmatArea);
    calcResults.put("connectorLoadCapacityKN", connectorLoadCapacity);
    result.put("calculatedResults", calcResults);

    return result;
  }

  // Getters and setters

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
   * Get structure material grade.
   *
   * @return structure material grade
   */
  public String getStructureMaterialGrade() {
    return structureMaterialGrade;
  }

  /**
   * Set structure material grade.
   *
   * @param structureMaterialGrade structure material grade
   */
  public void setStructureMaterialGrade(String structureMaterialGrade) {
    this.structureMaterialGrade = structureMaterialGrade;
  }

  /**
   * Get hub wall thickness.
   *
   * @return hub wall thickness in mm
   */
  public double getHubWallThickness() {
    return hubWallThickness;
  }

  /**
   * Get required foundation weight.
   *
   * @return required foundation weight in tonnes
   */
  public double getRequiredFoundationWeight() {
    return requiredFoundationWeight;
  }

  /**
   * Get required mudmat area.
   *
   * @return required mudmat area in m²
   */
  public double getRequiredMudmatArea() {
    return requiredMudmatArea;
  }
}
