package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.Umbilical;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for Umbilical equipment.
 *
 * <p>
 * Calculates tube design, cable sizing, and overall umbilical design per:
 * </p>
 * <ul>
 * <li>API RP 17E - Specification for Subsea Umbilicals</li>
 * <li>API Spec 17E - Specification for Subsea Production Control Umbilicals</li>
 * <li>ISO 13628-5 - Subsea Umbilicals</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see Umbilical
 */
public class UmbilicalMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to umbilical equipment. */
  private Umbilical umbilical;

  /** Design standard code. */
  private String designStandardCode = "API-RP-17E";

  // ============ Calculated Properties ============
  /** Hydraulic tube wall thickness in mm. */
  private double hydraulicTubeWallThickness = 0.0;

  /** Chemical tube wall thickness in mm. */
  private double chemicalTubeWallThickness = 0.0;

  /** Maximum allowable tension in kN. */
  private double maxAllowableTension = 0.0;

  /** Minimum bend radius in meters. */
  private double calculatedMinBendRadius = 0.0;

  /** Required armor layers. */
  private int requiredArmorLayers = 2;

  /** Total cross-section area in mm². */
  private double totalCrossSection = 0.0;

  /** Calculated outer diameter in mm. */
  private double calculatedOuterDiameter = 0.0;

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
   * @param equipment umbilical equipment instance
   */
  public UmbilicalMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.umbilical = (Umbilical) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load from database
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (umbilical == null) {
      return;
    }

    calculateTubeWallThickness();
    calculateArmorRequirements();
    calculateBendRadius();
    calculateOverallDiameter();
    calculateWeight();
  }

  /**
   * Calculate tube wall thickness for hydraulic and chemical lines.
   */
  private void calculateTubeWallThickness() {
    // Steel tube umbilical typically uses super duplex steel
    // SMYS for super duplex ≈ 550 MPa
    double smys = 550.0;
    double designFactor = 0.6; // Conservative for umbilical service

    // Get max design pressure from elements
    double maxHydraulicPressure = 0.0;
    double maxChemicalPressure = 0.0;

    for (Umbilical.UmbilicalElement element : umbilical.getElements()) {
      if ("hydraulic".equals(element.getElementType())) {
        maxHydraulicPressure = Math.max(maxHydraulicPressure, element.getDesignPressureBar());
      } else if ("chemical".equals(element.getElementType())) {
        maxChemicalPressure = Math.max(maxChemicalPressure, element.getDesignPressureBar());
      }
    }

    // Calculate hydraulic tube wall thickness
    if (maxHydraulicPressure > 0) {
      double pressureMPa = maxHydraulicPressure / 10.0;
      double typicalID = 12.7; // 1/2 inch typical
      hydraulicTubeWallThickness = (pressureMPa * typicalID) / (2 * smys * designFactor);
      hydraulicTubeWallThickness = Math.max(hydraulicTubeWallThickness, 1.5); // Minimum 1.5mm
    }

    // Calculate chemical tube wall thickness
    if (maxChemicalPressure > 0) {
      double pressureMPa = maxChemicalPressure / 10.0;
      double typicalID = 25.4; // 1 inch typical
      chemicalTubeWallThickness = (pressureMPa * typicalID) / (2 * smys * designFactor);
      chemicalTubeWallThickness = Math.max(chemicalTubeWallThickness, 2.0); // Minimum 2mm
    }
  }

  /**
   * Calculate armor requirements.
   */
  private void calculateArmorRequirements() {
    double waterDepth = umbilical.getWaterDepth();
    double length = umbilical.getLength();

    // Calculate catenary tension at surface
    double submergedWeight = umbilical.getSubmergedWeightPerMeter();
    double catSag = waterDepth * 1.2; // Approximate catenary sag

    // Maximum tension at surface
    double maxTension = submergedWeight * 9.81 * catSag / 1000; // kN

    // Add installation tension factor
    maxTension *= 1.5;

    maxAllowableTension = maxTension;

    // Armor layer sizing
    // Each armor layer contributes to tensile strength
    double armorStrength = 200.0; // kN per layer per 100mm diameter
    double od = umbilical.getOverallDiameterMm();

    requiredArmorLayers = (int) Math.ceil(maxTension / (armorStrength * od / 100));
    requiredArmorLayers = Math.max(requiredArmorLayers, 1);
  }

  /**
   * Calculate minimum bend radius.
   */
  private void calculateBendRadius() {
    double od = umbilical.getOverallDiameterMm();

    // MBR typically 6-8 times OD for steel tube umbilicals
    double mbrFactor =
        umbilical.getUmbilicalType() == Umbilical.UmbilicalType.STEEL_TUBE ? 8.0 : 6.0;

    calculatedMinBendRadius = od * mbrFactor / 1000; // meters

    // Update umbilical if calculated MBR is larger
    if (calculatedMinBendRadius > umbilical.getMinimumBendRadius()) {
      umbilical.setMinimumBendRadius(calculatedMinBendRadius);
    }
  }

  /**
   * Calculate overall diameter.
   */
  private void calculateOverallDiameter() {
    totalCrossSection = umbilical.calculateTotalCrossSection();

    // Add filler and sheath
    double effectiveArea = totalCrossSection * 1.4; // 40% for filler

    // Add armor layers
    if (umbilical.hasArmorWires()) {
      effectiveArea *= (1.0 + 0.15 * requiredArmorLayers);
    }

    // Calculate diameter
    double innerDiameter = Math.sqrt(4 * effectiveArea / Math.PI);
    calculatedOuterDiameter = innerDiameter + 2 * umbilical.getOuterSheathThicknessMm();

    // Update umbilical
    umbilical.setOverallDiameterMm(calculatedOuterDiameter);
  }

  /**
   * Calculate weight.
   */
  private void calculateWeight() {
    // Weight calculation is done in Umbilical.run()
    // This can be called to update
    umbilical.run(null);
  }

  /**
   * Calculate cost estimate for the umbilical.
   */
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    double lengthKm = umbilical.getLength() / 1000.0;
    int hydraulicLines = umbilical.getHydraulicLineCount();
    int chemicalLines = umbilical.getChemicalLineCount();
    int electricalCables = umbilical.getElectricalCableCount() + umbilical.getFiberOpticCount();
    boolean isDynamic = umbilical.getUmbilicalType() == Umbilical.UmbilicalType.DYNAMIC;

    costEstimator.calculateUmbilicalCost(lengthKm, hydraulicLines, chemicalLines, electricalCables,
        umbilical.getWaterDepth(), isDynamic);

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
    return costEstimator.generateBOM("Umbilical",
        umbilical.getDryWeightPerMeter() * umbilical.getLength() / 1000, umbilical.getWaterDepth());
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

    jsonObj.addProperty("equipmentType", "Umbilical");
    jsonObj.addProperty("designStandardCode", designStandardCode);

    JsonObject config = new JsonObject();
    config.addProperty("umbilicalType", umbilical.getUmbilicalType().name());
    config.addProperty("lengthM", umbilical.getLength());
    config.addProperty("waterDepthM", umbilical.getWaterDepth());
    jsonObj.add("configuration", config);

    // Elements summary
    JsonObject elements = new JsonObject();
    elements.addProperty("hydraulicLines", umbilical.getHydraulicLineCount());
    elements.addProperty("chemicalLines", umbilical.getChemicalLineCount());
    elements.addProperty("electricalCables", umbilical.getElectricalCableCount());
    elements.addProperty("fiberOpticCables", umbilical.getFiberOpticCount());
    elements.addProperty("totalElements", umbilical.getTotalElementCount());
    jsonObj.add("elements", elements);

    JsonObject results = new JsonObject();
    results.addProperty("hydraulicTubeWallThicknessMm", hydraulicTubeWallThickness);
    results.addProperty("chemicalTubeWallThicknessMm", chemicalTubeWallThickness);
    results.addProperty("maxAllowableTensionKN", maxAllowableTension);
    results.addProperty("calculatedMinBendRadiusM", calculatedMinBendRadius);
    results.addProperty("requiredArmorLayers", requiredArmorLayers);
    results.addProperty("totalCrossSectionMm2", totalCrossSection);
    results.addProperty("calculatedOuterDiameterMm", calculatedOuterDiameter);
    jsonObj.add("calculatedResults", results);

    JsonObject weight = new JsonObject();
    weight.addProperty("dryWeightPerMeterKgM", umbilical.getDryWeightPerMeter());
    weight.addProperty("submergedWeightPerMeterKgM", umbilical.getSubmergedWeightPerMeter());
    weight.addProperty("totalDryWeightTonnes",
        umbilical.getDryWeightPerMeter() * umbilical.getLength() / 1000);
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
    result.put("equipmentName", umbilical.getName());
    result.put("equipmentType", "Umbilical");
    result.put("umbilicalType", umbilical.getUmbilicalType().name());
    result.put("hydraulicTubeWallThicknessMm", hydraulicTubeWallThickness);
    result.put("chemicalTubeWallThicknessMm", chemicalTubeWallThickness);
    result.put("maxAllowableTensionKN", maxAllowableTension);
    result.put("calculatedMinBendRadiusM", calculatedMinBendRadius);
    result.put("requiredArmorLayers", requiredArmorLayers);
    result.put("calculatedOuterDiameterMm", calculatedOuterDiameter);
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
   * Get hydraulic tube wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getHydraulicTubeWallThickness() {
    return hydraulicTubeWallThickness;
  }

  /**
   * Get chemical tube wall thickness.
   *
   * @return wall thickness in mm
   */
  public double getChemicalTubeWallThickness() {
    return chemicalTubeWallThickness;
  }

  /**
   * Get max allowable tension.
   *
   * @return tension in kN
   */
  public double getMaxAllowableTension() {
    return maxAllowableTension;
  }

  /**
   * Get required armor layers.
   *
   * @return number of armor layers
   */
  public int getRequiredArmorLayers() {
    return requiredArmorLayers;
  }
}
