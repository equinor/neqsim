package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.SubseaJumper;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for Subsea Jumper equipment.
 *
 * <p>
 * Calculates wall thickness, stress analysis, and connection design for subsea jumpers per:
 * </p>
 * <ul>
 * <li>DNV-ST-F101 - Submarine Pipeline Systems (for rigid)</li>
 * <li>API RP 17B - Flexible Pipe (for flexible)</li>
 * <li>API RP 17G - Subsea Production System Design</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaJumper
 */
public class SubseaJumperMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to jumper equipment. */
  private SubseaJumper jumper;

  /** Design standard code. */
  private String designStandardCode = "DNV-ST-F101";

  // ============ Calculated Properties ============
  /** Required wall thickness in mm. */
  private double requiredWallThickness = 0.0;

  /** Hoop stress in MPa. */
  private double hoopStress = 0.0;

  /** Combined stress in MPa. */
  private double combinedStress = 0.0;

  /** Allowable stress in MPa. */
  private double allowableStress = 0.0;

  /** Unity check (stress ratio). */
  private double unityCheck = 0.0;

  /** Minimum bend radius check. */
  private boolean bendRadiusOK = true;

  /** Hub connection stress in MPa. */
  private double hubStress = 0.0;

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
   * @param equipment jumper equipment instance
   */
  public SubseaJumperMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.jumper = (SubseaJumper) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load from database
    if (jumper.isRigid()) {
      designStandardCode = "DNV-ST-F101";
    } else {
      designStandardCode = "API-RP-17B";
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (jumper == null) {
      return;
    }

    if (jumper.isRigid()) {
      calculateRigidJumperDesign();
    } else {
      calculateFlexibleJumperDesign();
    }

    calculateWeight();
  }

  /**
   * Calculate rigid jumper design per DNV-ST-F101.
   */
  private void calculateRigidJumperDesign() {
    double designPressure = jumper.getDesignPressure();
    double outerDiameter = jumper.getOuterDiameterInches() * 25.4; // mm

    // Get SMYS based on material
    double smys = getMaterialSMYS(jumper.getMaterialGrade());

    // Design factors per DNV-ST-F101
    double safetyClassFactor = 1.26; // High safety class
    double materialFactor = 1.15;
    double conditionFactor = 1.0;

    // Allowable hoop stress
    allowableStress = smys / (safetyClassFactor * materialFactor * conditionFactor);

    // Required wall thickness (Barlow's formula)
    double pressureMPa = designPressure / 10.0;
    requiredWallThickness = (pressureMPa * outerDiameter) / (2 * allowableStress);

    // Add corrosion allowance
    requiredWallThickness += 3.0; // 3mm

    // Add fabrication tolerance
    requiredWallThickness *= 1.125; // 12.5% tolerance

    // Minimum wall thickness
    requiredWallThickness = Math.max(requiredWallThickness, 9.52); // 3/8 inch minimum

    // Calculate actual stresses with specified wall thickness
    double wallThickness = jumper.getWallThicknessMm();
    if (wallThickness <= 0) {
      wallThickness = requiredWallThickness;
      jumper.setWallThicknessMm(wallThickness);
    }

    // Hoop stress
    double innerDiameter = outerDiameter - 2 * wallThickness;
    hoopStress = (pressureMPa * innerDiameter) / (2 * wallThickness);

    // Bending stress at bends
    double bendRadius = jumper.getMinimumBendRadius() * 1000; // mm
    double bendingStress = (outerDiameter / 2) * smys / bendRadius * 0.001;

    // Combined stress (von Mises approximation)
    combinedStress = Math.sqrt(hoopStress * hoopStress + bendingStress * bendingStress);

    // Unity check
    unityCheck = combinedStress / allowableStress;

    // Check minimum bend radius
    double minAllowableBendRadius = 3 * outerDiameter / 1000; // 3D minimum
    bendRadiusOK = jumper.getMinimumBendRadius() >= minAllowableBendRadius;

    // Hub connection stress
    hubStress = designPressure * 0.1 * (jumper.getInletHubSizeInches() / 10); // Simplified
  }

  /**
   * Calculate flexible jumper design per API RP 17B.
   */
  private void calculateFlexibleJumperDesign() {
    double designPressure = jumper.getDesignPressure();

    // Flexible pipe wall thickness is manufacturer-specific
    // Use approximate calculation for sizing

    // Design factor for flexible pipe
    double designFactor = 0.67;

    // Approximate required burst pressure
    double requiredBurst = designPressure / designFactor;

    // Check bend radius
    double minBendRadius = jumper.getFlexibleMinBendRadius();
    double nominalBore = jumper.getNominalBoreInches() * 25.4;

    // Typical MBR is 6-8 times ID for unbonded flexible
    double recommendedMBR = 7 * nominalBore / 1000; // meters
    bendRadiusOK = minBendRadius >= recommendedMBR;

    // Set stress values (flexible uses different criteria)
    allowableStress = 0; // N/A for flexible
    hoopStress = 0;
    combinedStress = 0;
    unityCheck = requiredBurst / designPressure; // Use burst margin as check
  }

  /**
   * Get material SMYS.
   *
   * @param materialGrade material grade
   * @return SMYS in MPa
   */
  private double getMaterialSMYS(String materialGrade) {
    if (materialGrade == null) {
      return 450.0;
    }

    if (materialGrade.startsWith("X")) {
      // API 5L grades
      try {
        int grade = Integer.parseInt(materialGrade.substring(1));
        return grade * 6.895; // Convert ksi to MPa
      } catch (NumberFormatException e) {
        return 450.0;
      }
    } else if (materialGrade.contains("316")) {
      return 205.0; // 316L stainless
    } else if (materialGrade.contains("6Mo")) {
      return 300.0; // 6Mo super austenitic
    } else if (materialGrade.contains("Duplex")) {
      return 450.0; // Duplex stainless
    }

    return 450.0; // Default X65 equivalent
  }

  /**
   * Calculate weight.
   */
  private void calculateWeight() {
    double steelDensity = 7850.0; // kg/m³

    if (jumper.isRigid()) {
      double od = jumper.getOuterDiameterInches() * 0.0254; // meters
      double wt = jumper.getWallThicknessMm() / 1000; // meters
      double length = jumper.getLength();

      // Steel cross-section area
      double steelArea = Math.PI * (od * wt - wt * wt);
      double steelWeight = steelArea * length * steelDensity / 1000; // tonnes

      // Add hub weights (approximate 0.5 tonnes each)
      double hubWeight = 1.0;

      jumper.setDryWeight(steelWeight + hubWeight);
      jumper.setSubmergedWeight((steelWeight + hubWeight) * 0.87);
    } else {
      // Flexible - use typical weight per meter
      double length = jumper.getLength();
      double weightPerMeter = 50.0; // kg/m typical

      double dryWeight = length * weightPerMeter / 1000; // tonnes
      jumper.setDryWeight(dryWeight);
      jumper.setSubmergedWeight(dryWeight * 0.7); // Higher buoyancy for flexible
    }
  }

  /**
   * Calculate cost estimate for the jumper.
   */
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    boolean isRigid = jumper.getJumperType() == SubseaJumper.JumperType.RIGID_M_SHAPE
        || jumper.getJumperType() == SubseaJumper.JumperType.RIGID_Z_SHAPE
        || jumper.getJumperType() == SubseaJumper.JumperType.RIGID_VERTICAL;

    costEstimator.calculateJumperCost(jumper.getLength(), jumper.getNominalBoreInches(), isRigid,
        jumper.getWaterDepth());

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
    return costEstimator.generateBOM("SubseaJumper", jumper.getDryWeight(), jumper.getWaterDepth());
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

    jsonObj.addProperty("equipmentType", "SubseaJumper");
    jsonObj.addProperty("designStandardCode", designStandardCode);

    JsonObject config = new JsonObject();
    config.addProperty("jumperType", jumper.getJumperType().name());
    config.addProperty("isRigid", jumper.isRigid());
    config.addProperty("lengthM", jumper.getLength());
    config.addProperty("materialGrade", jumper.getMaterialGrade());
    jsonObj.add("configuration", config);

    JsonObject design = new JsonObject();
    design.addProperty("designPressureBar", jumper.getDesignPressure());
    design.addProperty("nominalBoreInches", jumper.getNominalBoreInches());
    design.addProperty("outerDiameterInches", jumper.getOuterDiameterInches());
    jsonObj.add("designParameters", design);

    JsonObject results = new JsonObject();
    results.addProperty("requiredWallThicknessMm", requiredWallThickness);
    results.addProperty("specifiedWallThicknessMm", jumper.getWallThicknessMm());
    results.addProperty("hoopStressMPa", hoopStress);
    results.addProperty("combinedStressMPa", combinedStress);
    results.addProperty("allowableStressMPa", allowableStress);
    results.addProperty("unityCheck", unityCheck);
    results.addProperty("bendRadiusOK", bendRadiusOK);
    jsonObj.add("calculatedResults", results);

    JsonObject weight = new JsonObject();
    weight.addProperty("dryWeightTonnes", jumper.getDryWeight());
    weight.addProperty("submergedWeightTonnes", jumper.getSubmergedWeight());
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
    result.put("equipmentName", jumper.getName());
    result.put("equipmentType", "SubseaJumper");
    result.put("jumperType", jumper.getJumperType().name());
    result.put("requiredWallThicknessMm", requiredWallThickness);
    result.put("hoopStressMPa", hoopStress);
    result.put("combinedStressMPa", combinedStress);
    result.put("unityCheck", unityCheck);
    result.put("bendRadiusOK", bendRadiusOK);
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
   * Get required wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getRequiredWallThickness() {
    return requiredWallThickness;
  }

  /**
   * Get hoop stress.
   *
   * @return hoop stress in MPa
   */
  public double getHoopStress() {
    return hoopStress;
  }

  /**
   * Get unity check.
   *
   * @return unity check ratio
   */
  public double getUnityCheck() {
    return unityCheck;
  }

  /**
   * Check if bend radius is OK.
   *
   * @return true if bend radius meets requirements
   */
  public boolean isBendRadiusOK() {
    return bendRadiusOK;
  }
}
