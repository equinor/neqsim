package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.SubseaBooster;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for Subsea Booster equipment.
 *
 * <p>
 * Calculates motor sizing, pressure containment, and structural requirements per:
 * </p>
 * <ul>
 * <li>API RP 17Q - Subsea Equipment Qualification</li>
 * <li>API RP 17V - Subsea Boosting Systems</li>
 * <li>DNV-ST-E101 - Drilling Plants</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaBooster
 */
public class SubseaBoosterMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to booster equipment. */
  private SubseaBooster booster;

  /** Design standard code. */
  private String designStandardCode = "API-RP-17V";

  // ============ Calculated Properties ============
  /** Required motor power in MW. */
  private double requiredMotorPower = 0.0;

  /** Pump/compressor head in meters or pressure ratio. */
  private double calculatedHead = 0.0;

  /** Housing wall thickness in mm. */
  private double housingWallThickness = 0.0;

  /** Required cooling capacity in kW. */
  private double coolingCapacity = 0.0;

  /** Bearing life in hours. */
  private double bearingLife = 0.0;

  /** Seal life in hours. */
  private double sealLife = 0.0;

  /** Module foundation area in m². */
  private double foundationArea = 0.0;

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
   * @param equipment booster equipment instance
   */
  public SubseaBoosterMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.booster = (SubseaBooster) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load from database
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (booster == null) {
      return;
    }

    calculateMotorSizing();
    calculatePressureContainment();
    calculateCoolingRequirements();
    calculateBearingLife();
    calculateFoundation();
    calculateWeight();
  }

  /**
   * Calculate motor sizing.
   */
  private void calculateMotorSizing() {
    // Use equipment's calculation method
    requiredMotorPower = booster.calculateRequiredPower();

    // Add margin for efficiency and derating
    double margin = 1.2;
    requiredMotorPower *= margin;

    // Round up to next standard size
    double[] standardSizes = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0};
    for (double size : standardSizes) {
      if (size >= requiredMotorPower) {
        requiredMotorPower = size;
        break;
      }
    }

    // Calculate head
    if (booster.isCompressor()) {
      calculatedHead = booster.getPressureRatio();
    } else {
      // For pump, calculate head in meters
      double deltaP = booster.getDifferentialPressure() * 100000; // Pa
      double density = 800.0; // Approximate liquid density kg/m³
      calculatedHead = deltaP / (density * 9.81);
    }
  }

  /**
   * Calculate pressure containment.
   */
  private void calculatePressureContainment() {
    double designPressure =
        Math.max(booster.getDesignInletPressure(), booster.getOutletPressure()) * 1.1;

    // Housing OD based on impeller/stage count
    double housingOD = 500.0; // mm baseline
    housingOD += booster.getNumberOfStages() * 30; // 30mm per stage

    // Material yield strength (Inconel 718 typical)
    double yield = 1034.0; // MPa

    // Wall thickness calculation
    double pressureMPa = designPressure / 10.0;
    housingWallThickness = (pressureMPa * housingOD) / (2 * yield * 0.67);
    housingWallThickness = Math.max(housingWallThickness, 25.4); // Minimum 1 inch
  }

  /**
   * Calculate cooling requirements.
   */
  private void calculateCoolingRequirements() {
    // Heat generated = Power * (1 - efficiency)
    double powerLoss = requiredMotorPower * (1 - booster.getEfficiency()) * 1000; // kW

    // Cooling capacity must handle this plus margin
    coolingCapacity = powerLoss * 1.2;
  }

  /**
   * Calculate bearing life.
   */
  private void calculateBearingLife() {
    // L10 bearing life calculation (simplified)
    double speed = booster.getSpeedRPM();
    double load = requiredMotorPower * 1000 / speed; // Approximate radial load

    // Basic rating life
    double C = 100000.0; // Basic dynamic load rating (N) - typical
    double p = 3.0; // Exponent for ball bearings

    bearingLife = Math.pow(C / load, p) * 1e6 / (60 * speed);

    // Target minimum 40000 hours
    bearingLife = Math.max(bearingLife, booster.getMtbfHours());

    // Seal life (typically same or lower than bearing)
    sealLife = bearingLife * 0.8;
  }

  /**
   * Calculate foundation requirements.
   */
  private void calculateFoundation() {
    double weight = booster.getModuleDryWeight() * 1000 * 9.81; // N
    double bearingCapacity = 50000.0; // Pa (50 kPa soft clay)
    double safetyFactor = 2.0;

    foundationArea = weight * safetyFactor / bearingCapacity;

    // Minimum based on module size
    double minArea = Math.PI * Math.pow(booster.getModuleDryWeight() * 0.1, 2); // Rough estimate
    foundationArea = Math.max(foundationArea, minArea);
  }

  /**
   * Calculate weight.
   */
  private void calculateWeight() {
    // Estimate weight based on power rating
    double baseWeight = 50.0; // Base module weight in tonnes

    // Scale with power
    double powerFactor = requiredMotorPower / 5.0; // Normalize to 5 MW

    // Scale with stages
    double stageFactor = 1.0 + (booster.getNumberOfStages() - 6) * 0.05;

    double estimatedWeight = baseWeight * Math.pow(powerFactor, 0.7) * stageFactor;

    // Add for redundancy
    if (booster.hasRedundantMotor()) {
      estimatedWeight *= 1.5;
    }

    booster.setModuleDryWeight(estimatedWeight);
  }

  /**
   * Calculate cost estimate for the booster.
   */
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    boolean isCompressor = booster.getBoosterType() == SubseaBooster.BoosterType.WET_GAS_COMPRESSOR;
    boolean hasRedundancy = booster.hasRedundantMotor();

    costEstimator.calculateBoosterCost(requiredMotorPower / 1000.0, isCompressor,
        booster.getWaterDepth(), hasRedundancy);

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
    return costEstimator.generateBOM("SubseaBooster", booster.getModuleDryWeight(),
        booster.getWaterDepth());
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

    jsonObj.addProperty("equipmentType", "SubseaBooster");
    jsonObj.addProperty("designStandardCode", designStandardCode);

    JsonObject config = new JsonObject();
    config.addProperty("boosterType", booster.getBoosterType().name());
    if (booster.isCompressor()) {
      config.addProperty("compressorType", booster.getCompressorType().name());
    } else {
      config.addProperty("pumpType", booster.getPumpType().name());
    }
    config.addProperty("driveType", booster.getDriveType().name());
    config.addProperty("numberOfStages", booster.getNumberOfStages());
    jsonObj.add("configuration", config);

    JsonObject design = new JsonObject();
    design.addProperty("inletPressureBar", booster.getDesignInletPressure());
    design.addProperty("outletPressureBar", booster.getOutletPressure());
    design.addProperty("designFlowRateM3h", booster.getDesignFlowRate());
    design.addProperty("waterDepthM", booster.getWaterDepth());
    jsonObj.add("designParameters", design);

    JsonObject results = new JsonObject();
    results.addProperty("requiredMotorPowerMW", requiredMotorPower);
    if (booster.isCompressor()) {
      results.addProperty("pressureRatio", calculatedHead);
    } else {
      results.addProperty("pumpHeadM", calculatedHead);
    }
    results.addProperty("housingWallThicknessMm", housingWallThickness);
    results.addProperty("coolingCapacityKW", coolingCapacity);
    results.addProperty("bearingLifeHours", bearingLife);
    results.addProperty("sealLifeHours", sealLife);
    results.addProperty("foundationAreaM2", foundationArea);
    jsonObj.add("calculatedResults", results);

    JsonObject weight = new JsonObject();
    weight.addProperty("moduleDryWeightTonnes", booster.getModuleDryWeight());
    jsonObj.add("weight", weight);

    JsonObject reliability = new JsonObject();
    reliability.addProperty("designLifeYears", booster.getDesignLifeYears());
    reliability.addProperty("mtbfHours", booster.getMtbfHours());
    reliability.addProperty("retrievable", booster.isRetrievable());
    reliability.addProperty("redundantMotor", booster.hasRedundantMotor());
    jsonObj.add("reliability", reliability);

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
    result.put("equipmentName", booster.getName());
    result.put("equipmentType", "SubseaBooster");
    result.put("boosterType", booster.getBoosterType().name());
    result.put("requiredMotorPowerMW", requiredMotorPower);
    result.put("calculatedHead", calculatedHead);
    result.put("housingWallThicknessMm", housingWallThickness);
    result.put("coolingCapacityKW", coolingCapacity);
    result.put("bearingLifeHours", bearingLife);
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
   * Get required motor power.
   *
   * @return motor power in MW
   */
  public double getRequiredMotorPower() {
    return requiredMotorPower;
  }

  /**
   * Get calculated head.
   *
   * @return head in meters (pump) or pressure ratio (compressor)
   */
  public double getCalculatedHead() {
    return calculatedHead;
  }

  /**
   * Get housing wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getHousingWallThickness() {
    return housingWallThickness;
  }

  /**
   * Get bearing life.
   *
   * @return bearing life in hours
   */
  public double getBearingLife() {
    return bearingLife;
  }
}
