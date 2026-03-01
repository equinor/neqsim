package neqsim.process.mechanicaldesign.subsea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.SubseaWell;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for subsea wells.
 *
 * <p>
 * Provides casing design, tubing stress analysis, well barrier verification, and cost estimation
 * for subsea wells per applicable standards:
 * </p>
 * <ul>
 * <li>NORSOK D-010 - Well Integrity in Drilling and Well Operations</li>
 * <li>API 5CT - Casing and Tubing</li>
 * <li>ISO 11960 - Steel Pipes for Use as Casing or Tubing</li>
 * <li>API RP 90 - Annular Casing Pressure Management</li>
 * <li>API Bull 5C3 - Formulas and Calculations for Casing and Tubing Properties</li>
 * </ul>
 *
 * <h2>Calculation Scope</h2>
 * <ul>
 * <li><b>Casing Design</b> - Burst, collapse, tension checks per API 5C3</li>
 * <li><b>Tubing Analysis</b> - Stress, burst/collapse, DHSV requirements</li>
 * <li><b>Well Barriers</b> - Primary/secondary barrier verification per NORSOK D-010</li>
 * <li><b>Weight Estimation</b> - Casing, tubing, cement, completion string weights</li>
 * <li><b>Cost Estimation</b> - Drilling, completion, subsea wellhead, logging, contingency</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * SubseaWell well = new SubseaWell("Producer-1", stream);
 * well.setWellType(SubseaWell.WellType.OIL_PRODUCER);
 * well.setMeasuredDepth(3800.0);
 * well.setWaterDepth(350.0);
 *
 * well.initMechanicalDesign();
 * WellMechanicalDesign design = (WellMechanicalDesign) well.getMechanicalDesign();
 * design.calcDesign();
 * design.calculateCostEstimate();
 *
 * System.out.println("Total well cost: $" + design.getTotalCostUSD() / 1e6 + "M");
 * System.out.println(design.toJson());
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see SubseaWell
 * @see WellCostEstimator
 * @see WellDesignCalculator
 */
public class WellMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to well equipment. */
  private SubseaWell well;

  /** Design standard code. */
  private String designStandardCode = "NORSOK-D-010";

  /** Internal calculator. */
  private transient WellDesignCalculator calculator;

  /** Cost estimator. */
  private transient WellCostEstimator costEstimator;

  // ============ Design Results ============
  /** Production casing required wall thickness in mm. */
  private double productionCasingWallThickness = 0.0;

  /** Intermediate casing required wall thickness in mm. */
  private double intermediateCasingWallThickness = 0.0;

  /** Surface casing required wall thickness in mm. */
  private double surfaceCasingWallThickness = 0.0;

  /** Tubing required wall thickness in mm. */
  private double tubingWallThickness = 0.0;

  /** Production casing burst design factor. */
  private double productionCasingBurstDF = 0.0;

  /** Production casing collapse design factor. */
  private double productionCasingCollapseDF = 0.0;

  /** Production casing tension design factor. */
  private double productionCasingTensionDF = 0.0;

  /** Tubing burst design factor. */
  private double tubingBurstDF = 0.0;

  /** Total casing weight in tonnes. */
  private double totalCasingWeight = 0.0;

  /** Total tubing weight in tonnes. */
  private double totalTubingWeight = 0.0;

  /** Total cement volume in m3. */
  private double totalCementVolume = 0.0;

  /** Total drill cuttings volume in m3. */
  private double totalCuttingsVolume = 0.0;

  /** Well barrier verification result (pass/fail). */
  private boolean barrierVerificationPassed = false;

  /** Number of barrier issues. */
  private int barrierIssueCount = 0;

  /** Barrier verification notes. */
  private final List<String> barrierNotes = new ArrayList<String>();

  // ============ Cost Results ============
  /** Total well cost in USD. */
  private double totalCostUSD = 0.0;

  /** Drilling cost in USD. */
  private double drillingCostUSD = 0.0;

  /** Completion cost in USD. */
  private double completionCostUSD = 0.0;

  /** Wellhead cost in USD. */
  private double wellheadCostUSD = 0.0;

  /** Logging and testing cost in USD. */
  private double loggingCostUSD = 0.0;

  /** Material cost (casing, cement, mud) in USD. */
  private double materialCostUSD = 0.0;

  /**
   * Constructor.
   *
   * @param equipment well equipment instance
   */
  public WellMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.well = (SubseaWell) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Initialize calculator with well parameters
    getCalculator();
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (well == null) {
      return;
    }

    WellDesignCalculator calc = getCalculator();
    calc.setMeasuredDepth(well.getMeasuredDepth());
    calc.setTrueVerticalDepth(well.getTrueVerticalDepth());
    calc.setWaterDepth(well.getWaterDepth());
    calc.setMaxWellheadPressure(well.getMaxWellheadPressure());
    calc.setReservoirPressure(well.getReservoirPressure());
    calc.setReservoirTemperature(well.getReservoirTemperature());
    calc.setMaxBottomholeTemperature(well.getMaxBottomholeTemperature());

    // Set casing program
    calc.setConductorCasing(well.getConductorOD(), well.getConductorDepth());
    calc.setSurfaceCasing(well.getSurfaceCasingOD(), well.getSurfaceCasingDepth());
    calc.setIntermediateCasing(well.getIntermediateCasingOD(), well.getIntermediateCasingDepth());
    calc.setProductionCasing(well.getProductionCasingOD(), well.getProductionCasingDepth());

    if (well.getProductionLinerDepth() > 0) {
      calc.setProductionLiner(well.getProductionLinerOD(), well.getProductionLinerDepth());
    }

    // Set tubing
    calc.setTubing(well.getTubingOD(), well.getTubingWeight(), well.getTubingGrade());

    // Run calculations
    calc.calculateCasingDesign();
    calc.calculateTubingDesign();
    calc.calculateWeights();
    calc.calculateCementVolumes();

    // Store results
    productionCasingWallThickness = calc.getProductionCasingWallThickness();
    intermediateCasingWallThickness = calc.getIntermediateCasingWallThickness();
    surfaceCasingWallThickness = calc.getSurfaceCasingWallThickness();
    tubingWallThickness = calc.getTubingWallThickness();

    productionCasingBurstDF = calc.getProductionCasingBurstDF();
    productionCasingCollapseDF = calc.getProductionCasingCollapseDF();
    productionCasingTensionDF = calc.getProductionCasingTensionDF();
    tubingBurstDF = calc.getTubingBurstDF();

    totalCasingWeight = calc.getTotalCasingWeight();
    totalTubingWeight = calc.getTotalTubingWeight();
    totalCementVolume = calc.getTotalCementVolume();
    totalCuttingsVolume = calc.getTotalCuttingsVolume();

    // Well barrier verification
    verifyWellBarriers();
  }

  /**
   * Verify well barriers per NORSOK D-010.
   */
  private void verifyWellBarriers() {
    barrierNotes.clear();
    barrierIssueCount = 0;

    // Primary barrier check
    if (well.getPrimaryBarrierElements() < 2) {
      barrierNotes.add("WARNING: Primary barrier requires minimum 2 elements (NORSOK D-010)");
      barrierIssueCount++;
    }

    // Secondary barrier check
    if (well.getSecondaryBarrierElements() < 2) {
      barrierNotes.add("WARNING: Secondary barrier requires minimum 2 elements (NORSOK D-010)");
      barrierIssueCount++;
    }

    // DHSV check for subsea wells
    if (!well.hasDHSV() && well.isProducer()) {
      barrierNotes.add("WARNING: DHSV (SSSV) required for subsea production wells (NORSOK D-010)");
      barrierIssueCount++;
    }

    // Annulus monitoring
    barrierNotes.add("INFO: Annular pressure monitoring required per NORSOK D-010 Section 9");

    // Two-barrier principle
    if (well.getPrimaryBarrierElements() >= 2 && well.getSecondaryBarrierElements() >= 2
        && (well.hasDHSV() || !well.isProducer())) {
      barrierVerificationPassed = true;
      barrierNotes.add("PASS: Two-barrier principle satisfied per NORSOK D-010");
    } else {
      barrierVerificationPassed = false;
      barrierNotes.add("FAIL: Two-barrier principle NOT satisfied — review well design");
    }
  }

  /**
   * Calculate cost estimate for the well.
   */
  public void calculateCostEstimate() {
    WellCostEstimator ce = getCostEstimator();

    ce.calculateWellCost(well.getWellType().name(), well.getRigType().name(),
        well.getCompletionType().name(), well.getMeasuredDepth(), well.getWaterDepth(),
        well.getDrillingDays(), well.getCompletionDays(), well.getRigDayRate(), well.hasDHSV(),
        well.getNumberOfCasingStrings());

    totalCostUSD = ce.getTotalCost();
    drillingCostUSD = ce.getDrillingCost();
    completionCostUSD = ce.getCompletionCost();
    wellheadCostUSD = ce.getWellheadCost();
    loggingCostUSD = ce.getLoggingCost();
    materialCostUSD = ce.getCasingMaterialCost() + ce.getCementCost() + ce.getMudCost();
  }

  /**
   * Get the well design calculator, creating if needed.
   *
   * @return the calculator
   */
  public WellDesignCalculator getCalculator() {
    if (calculator == null) {
      calculator = new WellDesignCalculator();
    }
    return calculator;
  }

  /**
   * Get the cost estimator, creating if needed.
   *
   * @return the cost estimator
   */
  public WellCostEstimator getCostEstimator() {
    if (costEstimator == null) {
      costEstimator = new WellCostEstimator();
    }
    return costEstimator;
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
    return costEstimator.getCostBreakdown();
  }

  /**
   * Generate bill of materials for the well.
   *
   * @return list of BOM items
   */
  public List<Map<String, Object>> generateBillOfMaterials() {
    return getCostEstimator().generateBillOfMaterials(well);
  }

  /**
   * Get design results as Map.
   *
   * @return design results map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("equipmentName", well.getName());
    result.put("equipmentType", "SubseaWell");
    if (well.getWellType() != null) {
      result.put("wellType", well.getWellType().name());
    }
    if (well.getCompletionType() != null) {
      result.put("completionType", well.getCompletionType().name());
    }

    Map<String, Object> geometry = new LinkedHashMap<String, Object>();
    geometry.put("measuredDepthM", well.getMeasuredDepth());
    geometry.put("trueVerticalDepthM", well.getTrueVerticalDepth());
    geometry.put("waterDepthM", well.getWaterDepth());
    geometry.put("maxInclinationDeg", well.getMaxInclination());
    result.put("geometry", geometry);

    Map<String, Object> casing = new LinkedHashMap<String, Object>();
    casing.put("productionCasingWallThicknessMm", productionCasingWallThickness);
    casing.put("intermediateCasingWallThicknessMm", intermediateCasingWallThickness);
    casing.put("surfaceCasingWallThicknessMm", surfaceCasingWallThickness);
    casing.put("productionCasingBurstDF", productionCasingBurstDF);
    casing.put("productionCasingCollapseDF", productionCasingCollapseDF);
    casing.put("productionCasingTensionDF", productionCasingTensionDF);
    result.put("casingDesign", casing);

    Map<String, Object> weights = new LinkedHashMap<String, Object>();
    weights.put("totalCasingWeightTonnes", totalCasingWeight);
    weights.put("totalTubingWeightTonnes", totalTubingWeight);
    weights.put("totalCementVolumeM3", totalCementVolume);
    result.put("weights", weights);

    result.put("barrierVerificationPassed", barrierVerificationPassed);
    result.put("totalCostUSD", totalCostUSD);

    return result;
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    MechanicalDesignResponse response = new MechanicalDesignResponse(this);
    JsonObject jsonObj = JsonParser.parseString(response.toJson()).getAsJsonObject();

    jsonObj.addProperty("equipmentType", "SubseaWell");
    jsonObj.addProperty("designStandardCode", designStandardCode);

    // Well configuration
    JsonObject config = new JsonObject();
    config.addProperty("wellType", well.getWellType().name());
    config.addProperty("completionType", well.getCompletionType().name());
    config.addProperty("rigType", well.getRigType().name());
    config.addProperty("hasDHSV", well.hasDHSV());
    config.addProperty("numberOfCasingStrings", well.getNumberOfCasingStrings());
    jsonObj.add("configuration", config);

    // Geometry
    JsonObject geometry = new JsonObject();
    geometry.addProperty("measuredDepthM", well.getMeasuredDepth());
    geometry.addProperty("trueVerticalDepthM", well.getTrueVerticalDepth());
    geometry.addProperty("waterDepthM", well.getWaterDepth());
    geometry.addProperty("kickOffPointM", well.getKickOffPoint());
    geometry.addProperty("maxInclinationDeg", well.getMaxInclination());
    jsonObj.add("geometry", geometry);

    // Casing program
    JsonObject casingProgram = new JsonObject();
    casingProgram.addProperty("conductorOD_in", well.getConductorOD());
    casingProgram.addProperty("conductorDepthM", well.getConductorDepth());
    casingProgram.addProperty("surfaceCasingOD_in", well.getSurfaceCasingOD());
    casingProgram.addProperty("surfaceCasingDepthM", well.getSurfaceCasingDepth());
    casingProgram.addProperty("intermediateCasingOD_in", well.getIntermediateCasingOD());
    casingProgram.addProperty("intermediateCasingDepthM", well.getIntermediateCasingDepth());
    casingProgram.addProperty("productionCasingOD_in", well.getProductionCasingOD());
    casingProgram.addProperty("productionCasingDepthM", well.getProductionCasingDepth());
    if (well.getProductionLinerDepth() > 0) {
      casingProgram.addProperty("productionLinerOD_in", well.getProductionLinerOD());
      casingProgram.addProperty("productionLinerDepthM", well.getProductionLinerDepth());
    }
    jsonObj.add("casingProgram", casingProgram);

    // Tubing
    JsonObject tubing = new JsonObject();
    tubing.addProperty("tubingOD_in", well.getTubingOD());
    tubing.addProperty("tubingWeight_lbft", well.getTubingWeight());
    tubing.addProperty("tubingGrade", well.getTubingGrade());
    jsonObj.add("tubing", tubing);

    // Design results
    JsonObject designResults = new JsonObject();
    designResults.addProperty("productionCasingWallThicknessMm", productionCasingWallThickness);
    designResults.addProperty("intermediateCasingWallThicknessMm", intermediateCasingWallThickness);
    designResults.addProperty("surfaceCasingWallThicknessMm", surfaceCasingWallThickness);
    designResults.addProperty("tubingWallThicknessMm", tubingWallThickness);
    designResults.addProperty("productionCasingBurstDF", productionCasingBurstDF);
    designResults.addProperty("productionCasingCollapseDF", productionCasingCollapseDF);
    designResults.addProperty("productionCasingTensionDF", productionCasingTensionDF);
    designResults.addProperty("tubingBurstDF", tubingBurstDF);
    jsonObj.add("designResults", designResults);

    // Weights
    JsonObject weights = new JsonObject();
    weights.addProperty("totalCasingWeightTonnes", totalCasingWeight);
    weights.addProperty("totalTubingWeightTonnes", totalTubingWeight);
    weights.addProperty("totalCementVolumeM3", totalCementVolume);
    weights.addProperty("totalCuttingsVolumeM3", totalCuttingsVolume);
    jsonObj.add("weights", weights);

    // Barrier verification
    JsonObject barriers = new JsonObject();
    barriers.addProperty("verificationPassed", barrierVerificationPassed);
    barriers.addProperty("issueCount", barrierIssueCount);
    JsonArray notesArray = new JsonArray();
    for (String note : barrierNotes) {
      notesArray.add(note);
    }
    barriers.add("notes", notesArray);
    jsonObj.add("barrierVerification", barriers);

    // Cost estimation
    JsonObject cost = new JsonObject();
    cost.addProperty("totalCostUSD", totalCostUSD);
    cost.addProperty("drillingCostUSD", drillingCostUSD);
    cost.addProperty("completionCostUSD", completionCostUSD);
    cost.addProperty("wellheadCostUSD", wellheadCostUSD);
    cost.addProperty("loggingCostUSD", loggingCostUSD);
    cost.addProperty("materialCostUSD", materialCostUSD);
    if (costEstimator != null) {
      cost.addProperty("contingencyCostUSD", costEstimator.getContingencyCost());
      cost.addProperty("contingencyPct", costEstimator.getContingencyPct() * 100);
      cost.addProperty("region", costEstimator.getRegion().name());
    }
    jsonObj.add("costEstimation", cost);

    // Drilling schedule
    JsonObject schedule = new JsonObject();
    schedule.addProperty("drillingDays", well.getDrillingDays());
    schedule.addProperty("completionDays", well.getCompletionDays());
    schedule.addProperty("totalDays", well.getDrillingDays() + well.getCompletionDays());
    schedule.addProperty("rigDayRateUSD", well.getRigDayRate());
    jsonObj.add("schedule", schedule);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(jsonObj);
  }

  // ============ Getters ============

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
   * Get total well cost in USD.
   *
   * @return total cost
   */
  public double getTotalCostUSD() {
    return totalCostUSD;
  }

  /**
   * Get drilling cost in USD.
   *
   * @return drilling cost
   */
  public double getDrillingCostUSD() {
    return drillingCostUSD;
  }

  /**
   * Get completion cost in USD.
   *
   * @return completion cost
   */
  public double getCompletionCostUSD() {
    return completionCostUSD;
  }

  /**
   * Get wellhead cost in USD.
   *
   * @return wellhead cost
   */
  public double getWellheadCostUSD() {
    return wellheadCostUSD;
  }

  /**
   * Get logging and testing cost in USD.
   *
   * @return logging cost
   */
  public double getLoggingCostUSD() {
    return loggingCostUSD;
  }

  /**
   * Get material cost in USD.
   *
   * @return material cost
   */
  public double getMaterialCostUSD() {
    return materialCostUSD;
  }

  /**
   * Get production casing wall thickness in mm.
   *
   * @return wall thickness
   */
  public double getProductionCasingWallThickness() {
    return productionCasingWallThickness;
  }

  /**
   * Get total casing weight in tonnes.
   *
   * @return casing weight
   */
  public double getTotalCasingWeight() {
    return totalCasingWeight;
  }

  /**
   * Get total tubing weight in tonnes.
   *
   * @return tubing weight
   */
  public double getTotalTubingWeight() {
    return totalTubingWeight;
  }

  /**
   * Get total cement volume in m3.
   *
   * @return cement volume
   */
  public double getTotalCementVolume() {
    return totalCementVolume;
  }

  /**
   * Check if barrier verification passed.
   *
   * @return true if passed
   */
  public boolean isBarrierVerificationPassed() {
    return barrierVerificationPassed;
  }

  /**
   * Get barrier verification notes.
   *
   * @return list of notes
   */
  public List<String> getBarrierNotes() {
    return barrierNotes;
  }

  /**
   * Set region for cost estimation.
   *
   * @param region cost region
   */
  public void setRegion(SubseaCostEstimator.Region region) {
    getCostEstimator().setRegion(region);
  }

  /**
   * Get production casing burst design factor.
   *
   * @return burst design factor
   */
  public double getProductionCasingBurstDF() {
    return productionCasingBurstDF;
  }

  /**
   * Get production casing collapse design factor.
   *
   * @return collapse design factor
   */
  public double getProductionCasingCollapseDF() {
    return productionCasingCollapseDF;
  }

  /**
   * Get production casing tension design factor.
   *
   * @return tension design factor
   */
  public double getProductionCasingTensionDF() {
    return productionCasingTensionDF;
  }
}
