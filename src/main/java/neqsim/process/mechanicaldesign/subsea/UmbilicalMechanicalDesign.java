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
import neqsim.process.equipment.subsea.Umbilical;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for Umbilical equipment.
 *
 * <p>
 * Sizes tubes, armour and cross-section and checks the umbilical per:
 * </p>
 * <ul>
 * <li>API RP 17E - Specification for Subsea Umbilicals</li>
 * <li>API Spec 17E - Specification for Subsea Production Control Umbilicals</li>
 * <li>ISO 13628-5 - Subsea Umbilicals</li>
 * </ul>
 *
 * <p>
 * The design sequence is: tube wall thickness from internal design pressure and external collapse (per element), armour
 * layers from the free-hanging catenary top tension (the smallest layer count whose capacity covers the amplified
 * tension of the umbilical including that armour's own weight), touchdown bend radius against the minimum bend radius,
 * and the hydraulic response of every fluid line. Safety factors and material values are screening defaults set on the
 * {@link Umbilical}.
 * </p>
 *
 * @author ESOL
 * @version 2.0
 * @see Umbilical
 */
public class UmbilicalMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Maximum number of armour layers considered. */
  private static final int MAX_ARMOR_LAYERS = 12;

  /** Single-mode fibre attenuation used for the optical budget in dB/km (screening default). */
  private static final double OPTICAL_ATTENUATION_DB_PER_KM = 0.35;

  /** Connector pairs in the optical budget (screening default). */
  private static final int OPTICAL_CONNECTOR_PAIRS = 2;

  /** Loss per connector pair in dB (screening default). */
  private static final double OPTICAL_CONNECTOR_LOSS_DB = 0.5;

  /** Ageing and repair margin in the optical budget in dB (screening default). */
  private static final double OPTICAL_AGEING_MARGIN_DB = 3.0;

  /** Reference to umbilical equipment. */
  private Umbilical umbilical;

  /** Design standard code. */
  private String designStandardCode = "API-RP-17E";

  // ============ Calculated Properties ============
  /** Hydraulic tube wall thickness in mm. */
  private double hydraulicTubeWallThickness = 0.0;

  /** Chemical tube wall thickness in mm. */
  private double chemicalTubeWallThickness = 0.0;

  /** Allowable tension of the armour in kN. */
  private double maxAllowableTension = 0.0;

  /** Static top tension in kN. */
  private double staticTopTensionKN = 0.0;

  /** Design (amplified) top tension in kN. */
  private double designTopTensionKN = 0.0;

  /** Horizontal catenary tension in kN. */
  private double horizontalTensionKN = 0.0;

  /** Suspended length in m. */
  private double suspendedLengthM = 0.0;

  /** Top angle from vertical in degrees. */
  private double topAngleDeg = 0.0;

  /** Bend radius at touchdown in m. */
  private double touchdownBendRadiusM = 0.0;

  /** Armour axial stiffness in kN. */
  private double armorAxialStiffnessKN = 0.0;

  /** Minimum bend radius in meters. */
  private double calculatedMinBendRadius = 0.0;

  /** Required armor layers. */
  private int requiredArmorLayers = 2;

  /** Total cross-section area in mm2. */
  private double totalCrossSection = 0.0;

  /** Calculated outer diameter in mm. */
  private double calculatedOuterDiameter = 0.0;

  /** Weight in air (fluid filled) divided by the weight of displaced seawater. */
  private double weightRatio = 0.0;

  /** Optical loss budget of the fibre link in dB, 0 without fibres. */
  private double opticalBudgetDb = 0.0;

  /** Highest tube collapse utilisation. */
  private double maxCollapseUtilization = 0.0;

  /** Highest tube burst utilisation. */
  private double maxBurstUtilization = 0.0;

  /** True if the umbilical sinks under its own weight. */
  private boolean selfSinking = true;

  /** Result of the power cable calculation, null if no load is defined. */
  private transient UmbilicalElectricalCalculator.PowerResult powerResult;

  /** Per-element results. */
  private List<Map<String, Object>> elementResults = new ArrayList<Map<String, Object>>();

  /** Design checks (true = pass). */
  private Map<String, Boolean> designChecks = new LinkedHashMap<String, Boolean>();

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

    umbilical.run(null);
    calculateTubeDesign();
    calculateArmorAndTension();
    calculateBendRadius();
    calculatePower();
    calculateElementResults();
    evaluateChecks();
    totalCrossSection = umbilical.calculateTotalCrossSection();
    calculatedOuterDiameter = umbilical.getOverallDiameterMm();
    // Weight ratio is a mass ratio (g cancels), so no gravity constant is involved
    weightRatio = umbilical.getFilledWeightPerMeter() / (Math.PI / 4.0 * calculatedOuterDiameter
        * calculatedOuterDiameter * 1.0e-6 * UmbilicalStructuralCalculator.SEAWATER_DENSITY);
    opticalBudgetDb = umbilical.getFiberOpticCount() > 0
        ? UmbilicalFunctionalDesign.opticalBudgetDb(umbilical.getLength() / 1000.0, OPTICAL_ATTENUATION_DB_PER_KM, 0,
            0.0, OPTICAL_CONNECTOR_PAIRS, OPTICAL_CONNECTOR_LOSS_DB, OPTICAL_AGEING_MARGIN_DB)
        : 0.0;
  }

  /**
   * Resolve tube wall thickness and utilisation for every fluid line.
   */
  private void calculateTubeDesign() {
    hydraulicTubeWallThickness = 0.0;
    chemicalTubeWallThickness = 0.0;
    maxCollapseUtilization = 0.0;
    maxBurstUtilization = 0.0;
    double pExt = UmbilicalStructuralCalculator.hydrostaticPressureMPa(umbilical.getWaterDepth());
    for (Umbilical.UmbilicalElement e : umbilical.getElements()) {
      if (!e.isFluidLine()) {
        continue;
      }
      double wall = e.getWallThicknessMm();
      if ("hydraulic".equals(e.getElementType())) {
        hydraulicTubeWallThickness = Math.max(hydraulicTubeWallThickness, wall);
      } else {
        chemicalTubeWallThickness = Math.max(chemicalTubeWallThickness, wall);
      }
      if (e.isSteelTube() && wall > 0.0) {
        double id = e.getInnerDiameterMm();
        double pc = UmbilicalStructuralCalculator.collapsePressureMPa(id + 2.0 * wall, wall, umbilical.getTubeSmysMPa(),
            umbilical.getTubeYoungsModulusGPa() * 1000.0, umbilical.getTubeOvality());
        maxCollapseUtilization = Math.max(maxCollapseUtilization, pExt * umbilical.getCollapseSafetyFactor() / pc);
        double p = e.getDesignPressureBar() / 10.0;
        double hoop = p * (id / (2.0 * wall) + 1.0 - 0.4);
        maxBurstUtilization = Math.max(maxBurstUtilization,
            hoop / (umbilical.getTubeUsageFactor() * umbilical.getTubeSmysMPa()));
      }
    }
  }

  /**
   * Find the smallest number of armour layers whose capacity covers the amplified catenary top tension, including the
   * weight of those layers.
   */
  private void calculateArmorAndTension() {
    double g = UmbilicalStructuralCalculator.GRAVITY;
    int layers = 0;
    boolean armored = umbilical.hasArmorWires();
    int first = armored ? 1 : 0;
    int last = armored ? MAX_ARMOR_LAYERS : 0;
    for (int n = first; n <= last; n++) {
      if (armored) {
        umbilical.setArmorLayerCount(n);
      }
      umbilical.run(null);
      layers = n;
      updateTension(g);
      if (!armored || armorCapacityKN() >= designTopTensionKN) {
        break;
      }
    }
    requiredArmorLayers = layers;
    maxAllowableTension = armored ? armorCapacityKN() : 0.0;
    armorAxialStiffnessKN = armored
        ? UmbilicalStructuralCalculator.armorAxialStiffnessKN(coreDiameterMm(), umbilical.getArmorWireDiameterMm(),
            umbilical.getArmorLayAngleDeg(), layers, 200000.0)
        : 0.0;
  }

  /**
   * Update the catenary and tensions from the current submerged weight.
   *
   * @param g gravitational acceleration in m/s2
   */
  private void updateTension(double g) {
    double w = umbilical.getSubmergedWeightPerMeter() * g;
    selfSinking = w > 0.0;
    if (!selfSinking) {
      staticTopTensionKN = 0.0;
      designTopTensionKN = 0.0;
      horizontalTensionKN = 0.0;
      suspendedLengthM = umbilical.getWaterDepth();
      topAngleDeg = 0.0;
      touchdownBendRadiusM = Double.POSITIVE_INFINITY;
      return;
    }
    UmbilicalStructuralCalculator.CatenaryResult cat = UmbilicalStructuralCalculator.freeHangingCatenary(w,
        umbilical.getWaterDepth(), umbilical.getTouchdownOffsetM());
    staticTopTensionKN = cat.getTopTensionKN();
    designTopTensionKN = staticTopTensionKN * umbilical.getTensionAmplificationFactor();
    horizontalTensionKN = cat.getHorizontalTensionKN();
    suspendedLengthM = cat.getSuspendedLengthM();
    topAngleDeg = cat.getTopAngleFromVerticalDeg();
    touchdownBendRadiusM = cat.getTouchdownBendRadiusM();
  }

  /**
   * Diameter under the first armour layer.
   *
   * @return core diameter in mm
   */
  private double coreDiameterMm() {
    return umbilical.getOverallDiameterMm() - 2.0 * umbilical.getOuterSheathThicknessMm()
        - 2.0 * (umbilical.hasArmorWires() ? umbilical.getArmorLayerCount() : 0) * umbilical.getArmorWireDiameterMm();
  }

  /**
   * Allowable axial capacity of the current armour.
   *
   * @return capacity in kN
   */
  private double armorCapacityKN() {
    return UmbilicalStructuralCalculator.armorCapacityKN(coreDiameterMm(), umbilical.getArmorWireDiameterMm(),
        umbilical.getArmorLayAngleDeg(), umbilical.getArmorLayerCount(), umbilical.getArmorAllowableStressMPa());
  }

  /**
   * Calculate minimum bend radius.
   */
  private void calculateBendRadius() {
    double od = umbilical.getOverallDiameterMm();

    // MBR typically 6-8 times OD for steel tube umbilicals
    double mbrFactor = umbilical.getUmbilicalType() == Umbilical.UmbilicalType.STEEL_TUBE ? 8.0 : 6.0;

    calculatedMinBendRadius = od * mbrFactor / 1000; // meters

    // Update umbilical if calculated MBR is larger
    if (calculatedMinBendRadius > umbilical.getMinimumBendRadius()) {
      umbilical.setMinimumBendRadius(calculatedMinBendRadius);
    }
  }

  /**
   * Solve the power cable when a load is defined.
   */
  private void calculatePower() {
    powerResult = null;
    if (umbilical.getPowerLoad() <= 0.0 || umbilical.getElectricalCableCount() == 0) {
      return;
    }
    try {
      powerResult = umbilical.calculatePowerTransmission();
    } catch (IllegalStateException ex) {
      powerResult = null;
    }
  }

  /**
   * Collect per-element geometry, utilisation and hydraulics.
   */
  private void calculateElementResults() {
    elementResults = new ArrayList<Map<String, Object>>();
    for (Umbilical.UmbilicalElement e : umbilical.getElements()) {
      Map<String, Object> r = new LinkedHashMap<String, Object>();
      r.put("name", e.getName());
      r.put("type", e.getElementType());
      r.put("outerDiameterMm", e.getOuterDiameterMm());
      r.put("emptyMassKgPerM", e.getEmptyMassKgPerM());
      if (e.isFluidLine()) {
        r.put("innerDiameterMm", e.getInnerDiameterMm());
        r.put("wallThicknessMm", e.getWallThicknessMm());
        r.put("designPressureBar", e.getDesignPressureBar());
        r.put("fluid", e.getFluid() == null ? "" : e.getFluid().getName());
        r.put("fluidMassKgPerM", e.getFluidMassKgPerM());
        UmbilicalHydraulicsCalculator.LineResult line = umbilical.getLineHydraulics(e.getName());
        r.put("designFlowM3PerHr", e.getDesignFlowRateM3PerHr());
        r.put("velocityMs", line.getVelocityMs());
        r.put("reynolds", line.getReynolds());
        r.put("frictionDropBar", line.getFrictionDropBar());
        r.put("hydrostaticHeadBar", line.getHydrostaticBar());
        UmbilicalHydraulicsCalculator.ResponseResult resp = umbilical.calculateLineResponse(e.getName(), 0.9);
        r.put("acousticVelocityMs", resp.getAcousticVelocityMs());
        r.put("acousticTransitTimeS", resp.getTransitTimeS());
        r.put("time90PercentS", resp.getTimeToFractionS());
        double fillVolumeM3 = UmbilicalFunctionalDesign.tubeVolumeM3(e.getInnerDiameterMm(), umbilical.getLength());
        r.put("fillVolumeLitres", fillVolumeM3 * 1000.0);
        if (e.getDesignFlowRateM3PerHr() > 0.0) {
          r.put("displacementTimeHours", fillVolumeM3 / e.getDesignFlowRateM3PerHr());
        }
      } else if ("electrical".equals(e.getElementType())) {
        r.put("cores", e.getNumberOfCores());
        r.put("voltageV", e.getVoltageRating());
        r.put("conductorAreaMm2", e.getConductorAreaMm2());
        r.put("insulationThicknessMm", e.getInsulationThicknessMm());
      }
      elementResults.add(r);
    }
  }

  /**
   * Evaluate pass/fail design checks.
   */
  private void evaluateChecks() {
    designChecks = new LinkedHashMap<String, Boolean>();
    designChecks.put("selfSinking", selfSinking);
    designChecks.put("tubeBurstUtilizationBelow1", maxBurstUtilization <= 1.0 + 1.0e-6);
    designChecks.put("tubeCollapseUtilizationBelow1", maxCollapseUtilization <= 1.0 + 1.0e-6);
    if (umbilical.hasArmorWires()) {
      designChecks.put("armorCapacityCoversDesignTension", armorCapacityKN() >= designTopTensionKN);
    }
    designChecks.put("touchdownBendRadiusAboveMBR", touchdownBendRadiusM >= umbilical.getMinimumBendRadius());
    designChecks.put("installationTensionWithinVesselLimit",
        designTopTensionKN <= umbilical.getMaxInstallationTensionKN());
    boolean dropOk = true;
    for (Umbilical.UmbilicalElement e : umbilical.getElements()) {
      if (e.isFluidLine() && e.getDesignFlowRateM3PerHr() > 0.0 && e.getFluid() != null) {
        double drop = umbilical.calculatePressureDrop(e.getName());
        if (drop > umbilical.getMaxFrictionDropFraction() * e.getDesignPressureBar()) {
          dropOk = false;
        }
      }
    }
    designChecks.put("lineFrictionDropWithinPressureBudget", dropOk);
  }

  /**
   * Check whether all design checks pass.
   *
   * @return true if every check passes
   */
  public boolean isDesignAcceptable() {
    for (Boolean ok : designChecks.values()) {
      if (!ok) {
        return false;
      }
    }
    return !designChecks.isEmpty();
  }

  /**
   * Calculate cost estimate for the umbilical.
   */
  @Override
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    double lengthKm = umbilical.getLength() / 1000.0;
    int hydraulicLines = umbilical.getHydraulicLineCount();
    int chemicalLines = umbilical.getChemicalLineCount();
    int electricalCables = umbilical.getElectricalCableCount() + umbilical.getFiberOpticCount();

    costEstimator.calculateUmbilicalCost(lengthKm, hydraulicLines, chemicalLines, electricalCables,
        umbilical.getWaterDepth(), umbilical.isDynamicService());

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
  @Override
  public List<Map<String, Object>> generateBillOfMaterials() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }
    return costEstimator.generateBOM("Umbilical", umbilical.getDryWeightPerMeter() * umbilical.getLength() / 1000,
        umbilical.getWaterDepth());
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
    config.addProperty("dynamicService", umbilical.isDynamicService());
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
    results.addProperty("maxCollapseUtilization", maxCollapseUtilization);
    results.addProperty("maxBurstUtilization", maxBurstUtilization);
    results.addProperty("armorCapacityKN", maxAllowableTension);
    results.addProperty("staticTopTensionKN", staticTopTensionKN);
    results.addProperty("designTopTensionKN", designTopTensionKN);
    results.addProperty("horizontalTensionKN", horizontalTensionKN);
    results.addProperty("suspendedLengthM", suspendedLengthM);
    results.addProperty("topAngleFromVerticalDeg", topAngleDeg);
    results.addProperty("touchdownBendRadiusM", touchdownBendRadiusM);
    results.addProperty("calculatedMinBendRadiusM", calculatedMinBendRadius);
    results.addProperty("requiredArmorLayers", requiredArmorLayers);
    results.addProperty("armorAxialStiffnessKN", armorAxialStiffnessKN);
    results.addProperty("totalCrossSectionMm2", totalCrossSection);
    results.addProperty("calculatedOuterDiameterMm", calculatedOuterDiameter);
    results.addProperty("weightRatio", weightRatio);
    if (umbilical.getFiberOpticCount() > 0) {
      results.addProperty("opticalBudgetDb", opticalBudgetDb);
    }
    jsonObj.add("calculatedResults", results);

    JsonArray lines = new JsonArray();
    for (Map<String, Object> r : elementResults) {
      JsonObject o = new JsonObject();
      for (Map.Entry<String, Object> en : r.entrySet()) {
        Object v = en.getValue();
        if (v instanceof Number) {
          o.addProperty(en.getKey(), (Number) v);
        } else {
          o.addProperty(en.getKey(), String.valueOf(v));
        }
      }
      lines.add(o);
    }
    jsonObj.add("elementResults", lines);

    if (powerResult != null) {
      JsonObject p = new JsonObject();
      p.addProperty("loadMW", umbilical.getPowerLoad());
      p.addProperty("acResistanceOhmPerKm", powerResult.getAcResistanceOhmPerKm());
      p.addProperty("reactanceOhmPerKm", powerResult.getReactanceOhmPerKm());
      p.addProperty("capacitanceUfPerKm", powerResult.getCapacitanceUfPerKm());
      p.addProperty("chargingCurrentA", powerResult.getChargingCurrentA());
      p.addProperty("loadCurrentA", powerResult.getLoadCurrentA());
      p.addProperty("sendingVoltageV", powerResult.getSendingVoltageV());
      p.addProperty("sendingCurrentA", powerResult.getSendingCurrentA());
      p.addProperty("lossMW", powerResult.getLossMW());
      p.addProperty("voltageDropPercent", powerResult.getVoltageDropPercent());
      p.addProperty("noLoadRisePercent", powerResult.getNoLoadRisePercent());
      jsonObj.add("powerTransmission", p);
    }

    addVisualizationData(jsonObj);

    JsonObject checks = new JsonObject();
    for (Map.Entry<String, Boolean> en : designChecks.entrySet()) {
      checks.addProperty(en.getKey(), en.getValue());
    }
    jsonObj.add("designChecks", checks);

    JsonObject weight = new JsonObject();
    weight.addProperty("dryWeightPerMeterKgM", umbilical.getDryWeightPerMeter());
    weight.addProperty("filledWeightPerMeterKgM", umbilical.getFilledWeightPerMeter());
    weight.addProperty("submergedWeightPerMeterKgM", umbilical.getSubmergedWeightPerMeter());
    weight.addProperty("totalDryWeightTonnes", umbilical.getDryWeightPerMeter() * umbilical.getLength() / 1000);
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

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(jsonObj);
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
    result.put("armorCapacityKN", maxAllowableTension);
    result.put("staticTopTensionKN", staticTopTensionKN);
    result.put("designTopTensionKN", designTopTensionKN);
    result.put("touchdownBendRadiusM", touchdownBendRadiusM);
    result.put("calculatedMinBendRadiusM", calculatedMinBendRadius);
    result.put("requiredArmorLayers", requiredArmorLayers);
    result.put("calculatedOuterDiameterMm", calculatedOuterDiameter);
    result.put("designChecks", new LinkedHashMap<String, Boolean>(designChecks));
    return result;
  }

  /**
   * Add the data that drawings and charts need: cross-section dimensions, friction-drop curves of every fluid line, the
   * catenary profile and a power-cable length sweep.
   *
   * @param jsonObj JSON object to extend
   */
  private void addVisualizationData(JsonObject jsonObj) {
    JsonObject xs = new JsonObject();
    xs.addProperty("overallDiameterMm", umbilical.getOverallDiameterMm());
    xs.addProperty("bundleDiameterMm", umbilical.getBundleDiameterMm());
    xs.addProperty("innerSheathThicknessMm", umbilical.getInnerSheathThicknessMm());
    xs.addProperty("outerSheathThicknessMm", umbilical.getOuterSheathThicknessMm());
    xs.addProperty("armorLayers", umbilical.hasArmorWires() ? umbilical.getArmorLayerCount() : 0);
    xs.addProperty("armorWireDiameterMm", umbilical.getArmorWireDiameterMm());
    xs.addProperty("armorLayAngleDeg", umbilical.getArmorLayAngleDeg());
    jsonObj.add("crossSection", xs);

    JsonArray curves = new JsonArray();
    double tK = umbilical.getSeawaterTemperatureC() + 273.15;
    for (Umbilical.UmbilicalElement e : umbilical.getElements()) {
      if (!e.isFluidLine() || e.getFluid() == null) {
        continue;
      }
      double design = e.getDesignFlowRateM3PerHr();
      double maxFlow = design > 0.0 ? 2.0 * design : umbilical.calculateMaxFlowM3PerHr(e.getName(), 20.0);
      JsonArray flow = new JsonArray();
      JsonArray drop = new JsonArray();
      for (int i = 0; i <= 24; i++) {
        double q = maxFlow * i / 24.0;
        flow.add(q);
        drop.add(UmbilicalHydraulicsCalculator.calculate(e.getFluid(), e.getInnerDiameterMm(), umbilical.getLength(),
            q / 3600.0, tK, UmbilicalHydraulicsCalculator.DEFAULT_ROUGHNESS_MM, 0.0).getFrictionDropBar());
      }
      JsonObject c = new JsonObject();
      c.addProperty("name", e.getName());
      c.addProperty("designFlowM3PerHr", design);
      c.add("flowM3PerHr", flow);
      c.add("frictionDropBar", drop);
      curves.add(c);
    }
    jsonObj.add("hydraulicCurves", curves);

    if (selfSinking && umbilical.getSubmergedWeightPerMeter() > 0.0 && !Double.isInfinite(touchdownBendRadiusM)) {
      double[][] p = UmbilicalStructuralCalculator.catenaryProfile(
          umbilical.getSubmergedWeightPerMeter() * UmbilicalStructuralCalculator.GRAVITY, touchdownBendRadiusM,
          umbilical.getWaterDepth(), 40);
      JsonObject cat = new JsonObject();
      cat.add("horizontalDistanceM", toJsonArray(p[0]));
      cat.add("heightAboveSeabedM", toJsonArray(p[1]));
      cat.add("tensionKN", toJsonArray(p[2]));
      jsonObj.add("catenaryProfile", cat);
    }

    if (powerResult != null) {
      JsonArray len = new JsonArray();
      JsonArray drop = new JsonArray();
      JsonArray loss = new JsonArray();
      JsonArray charging = new JsonArray();
      for (int i = 1; i <= 20; i++) {
        double l = 1.5 * umbilical.getLength() * i / 20.0;
        UmbilicalElectricalCalculator.PowerResult r = umbilical.calculatePowerTransmission(l);
        len.add(l / 1000.0);
        drop.add(r.getVoltageDropPercent());
        loss.add(r.getLossMW());
        charging.add(r.getChargingCurrentA());
      }
      JsonObject sweep = new JsonObject();
      sweep.add("lengthKm", len);
      sweep.add("voltageDropPercent", drop);
      sweep.add("lossMW", loss);
      sweep.add("chargingCurrentA", charging);
      jsonObj.add("powerSweep", sweep);
    }
  }

  /**
   * Convert a double array to a JSON array.
   *
   * @param values values
   * @return JSON array
   */
  private static JsonArray toJsonArray(double[] values) {
    JsonArray a = new JsonArray();
    for (double v : values) {
      a.add(v);
    }
    return a;
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
   * Get the weight ratio: weight in air of the fluid-filled umbilical over the weight of displaced seawater.
   *
   * @return weight ratio
   */
  public double getWeightRatio() {
    return weightRatio;
  }

  /**
   * Get the optical loss budget of the fibre link (screening defaults: 0.35 dB/km, two connector pairs of 0.5 dB, 3 dB
   * ageing margin).
   *
   * @return loss budget in dB, 0 without fibres
   */
  public double getOpticalBudgetDb() {
    return opticalBudgetDb;
  }

  /**
   * Get the longest time to displace a line volume at its design flow (control-fluid or chemical changeout).
   *
   * @return time in hours, 0 if no line has a design flow
   */
  public double getMaxDisplacementTimeHours() {
    double max = 0.0;
    for (Map<String, Object> r : elementResults) {
      Object t = r.get("displacementTimeHours");
      if (t instanceof Number) {
        max = Math.max(max, ((Number) t).doubleValue());
      }
    }
    return max;
  }

  /**
   * Get the allowable axial tension of the armour.
   *
   * @return tension in kN
   */
  public double getMaxAllowableTension() {
    return maxAllowableTension;
  }

  /**
   * Get the static top tension of the free-hanging catenary.
   *
   * @return tension in kN
   */
  public double getStaticTopTensionKN() {
    return staticTopTensionKN;
  }

  /**
   * Get the design top tension (static tension times the amplification factor).
   *
   * @return tension in kN
   */
  public double getDesignTopTensionKN() {
    return designTopTensionKN;
  }

  /**
   * Get the bend radius at the touchdown point.
   *
   * @return radius in m
   */
  public double getTouchdownBendRadiusM() {
    return touchdownBendRadiusM;
  }

  /**
   * Get the highest tube collapse utilisation (external pressure times safety factor over collapse pressure).
   *
   * @return utilisation, below 1 is acceptable
   */
  public double getMaxCollapseUtilization() {
    return maxCollapseUtilization;
  }

  /**
   * Get the highest tube burst utilisation (hoop stress at design pressure over allowable).
   *
   * @return utilisation, below 1 is acceptable
   */
  public double getMaxBurstUtilization() {
    return maxBurstUtilization;
  }

  /**
   * Get the power cable result.
   *
   * @return power result, null if no load is defined
   */
  public UmbilicalElectricalCalculator.PowerResult getPowerResult() {
    return powerResult;
  }

  /**
   * Get the design checks (true = pass).
   *
   * @return map of check name to result
   */
  public Map<String, Boolean> getDesignChecks() {
    return new LinkedHashMap<String, Boolean>(designChecks);
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
