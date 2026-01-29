package neqsim.process.mechanicaldesign.subsea;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.subsea.FlexiblePipe;
import neqsim.process.mechanicaldesign.MechanicalDesign;
import neqsim.process.mechanicaldesign.MechanicalDesignResponse;

/**
 * Mechanical design class for Flexible Pipe equipment.
 *
 * <p>
 * Calculates layer design, tensile capacity, and bend radius requirements per:
 * </p>
 * <ul>
 * <li>API RP 17B - Recommended Practice for Flexible Pipe</li>
 * <li>API Spec 17J - Specification for Unbonded Flexible Pipe</li>
 * <li>API Spec 17K - Specification for Bonded Flexible Pipe</li>
 * <li>DNV-ST-F201 - Dynamic Risers</li>
 * </ul>
 *
 * @author ESOL
 * @version 1.0
 * @see FlexiblePipe
 */
public class FlexiblePipeMechanicalDesign extends MechanicalDesign {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Reference to flexible pipe equipment. */
  private FlexiblePipe flexPipe;

  /** Design standard code. */
  private String designStandardCode = "API-RP-17B";

  // ============ Calculated Properties ============
  /** Required burst pressure in bar. */
  private double requiredBurstPressure = 0.0;

  /** Required collapse pressure in bar. */
  private double requiredCollapsePressure = 0.0;

  /** Required tensile capacity in kN. */
  private double requiredTensileCapacity = 0.0;

  /** Carcass thickness in mm. */
  private double carcassThickness = 0.0;

  /** Internal sheath thickness in mm. */
  private double internalSheathThickness = 0.0;

  /** Pressure armor thickness in mm. */
  private double pressureArmorThickness = 0.0;

  /** Tensile armor wire size in mm. */
  private double tensileArmorWireSize = 0.0;

  /** Calculated minimum bend radius in meters. */
  private double calculatedMBR = 0.0;

  /** Storage bend radius in meters. */
  private double storageMBR = 0.0;

  /** Design life in years. */
  private int designLife = 25;

  /** Fatigue life cycles. */
  private double fatigueLifeCycles = 0.0;

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
   * @param equipment flexible pipe equipment instance
   */
  public FlexiblePipeMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    this.flexPipe = (FlexiblePipe) equipment;
  }

  /** {@inheritDoc} */
  @Override
  public void readDesignSpecifications() {
    // Load from database
    if (flexPipe.getPipeType() == FlexiblePipe.PipeType.BONDED) {
      designStandardCode = "API-Spec-17K";
    } else {
      designStandardCode = "API-Spec-17J";
    }
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    if (flexPipe == null) {
      return;
    }

    calculatePressureCapacity();
    calculateTensileCapacity();
    calculateLayerThickness();
    calculateBendRadius();
    calculateFatigueLife();
    calculateWeight();
  }

  /**
   * Calculate pressure capacity requirements.
   */
  private void calculatePressureCapacity() {
    double designPressure = flexPipe.getDesignPressure();
    double waterDepth = flexPipe.getWaterDepth();

    // Burst pressure per API 17J/K
    // Design factor 0.67 for normal operation
    double burstDesignFactor = 0.67;
    requiredBurstPressure = designPressure / burstDesignFactor;

    // Collapse pressure
    // External pressure at water depth plus factor
    double externalPressure = waterDepth * 0.1; // bar (approximately 1 bar per 10m)
    double collapseDesignFactor = 0.67;
    requiredCollapsePressure = externalPressure / collapseDesignFactor;

    // Update pipe properties
    flexPipe.setBurstPressure(requiredBurstPressure);
    flexPipe.setCollapsePressure(requiredCollapsePressure);
  }

  /**
   * Calculate tensile capacity requirements.
   */
  private void calculateTensileCapacity() {
    double length = flexPipe.getLength();
    double waterDepth = flexPipe.getWaterDepth();
    double submergedWeight = flexPipe.getSubmergedWeightPerMeter();

    // Calculate catenary tension for risers
    if (flexPipe.getApplication() == FlexiblePipe.Application.DYNAMIC_RISER
        || flexPipe.getApplication() == FlexiblePipe.Application.STATIC_RISER) {

      // Simplified catenary calculation
      double hangoffAngle = Math.toRadians(10); // Typical 10 degree hangoff
      double catTension = submergedWeight * 9.81 * waterDepth / Math.sin(hangoffAngle) / 1000; // kN

      // Add dynamic amplification for dynamic risers
      double dynamicFactor =
          flexPipe.getApplication() == FlexiblePipe.Application.DYNAMIC_RISER ? 1.5 : 1.2;

      requiredTensileCapacity = catTension * dynamicFactor;
    } else {
      // For flowlines, tension is mainly installation
      requiredTensileCapacity = submergedWeight * 9.81 * length * 0.1 / 1000; // 10% of weight
    }

    // Safety factor
    requiredTensileCapacity *= 1.5;

    // Update pipe
    flexPipe.setMaxTensionKN(requiredTensileCapacity);
  }

  /**
   * Calculate layer thicknesses.
   */
  private void calculateLayerThickness() {
    double innerDiameter = flexPipe.getInnerDiameterInches() * 25.4; // mm
    double designPressure = flexPipe.getDesignPressure();

    // Carcass thickness (based on collapse resistance)
    // Simplified calculation
    if (flexPipe.hasCarcass()) {
      double externalPressure = flexPipe.getWaterDepth() * 0.1; // bar
      carcassThickness = innerDiameter * 0.02 * Math.sqrt(externalPressure / 50);
      carcassThickness = Math.max(carcassThickness, 3.0); // Minimum 3mm
    }

    // Internal sheath thickness
    // Based on permeation and service
    if (flexPipe.isSourService()) {
      internalSheathThickness = 8.0; // Thicker for sour service
    } else {
      internalSheathThickness = 6.0; // Standard
    }

    // Pressure armor thickness
    if (flexPipe.hasPressureArmor()) {
      // Based on hoop stress
      double pressureMPa = designPressure / 10.0;
      double armorYield = 700.0; // MPa for high strength wire
      double armorOD = innerDiameter + 2 * carcassThickness + 2 * internalSheathThickness;
      pressureArmorThickness = (pressureMPa * armorOD) / (2 * armorYield * 0.67);
      pressureArmorThickness = Math.max(pressureArmorThickness, 6.0);
    }

    // Tensile armor wire size
    int numLayers = flexPipe.getTensileArmorLayers();
    double tensileYield = 1400.0; // MPa for tensile armor wire
    double armorAngle = Math.toRadians(35); // Typical lay angle

    double tensilePerLayer = requiredTensileCapacity * 1000 / numLayers; // N
    double wirePitch = 5.0; // mm between wires
    double numWires = Math.PI * innerDiameter / wirePitch;
    double wireForce = tensilePerLayer / numWires / Math.cos(armorAngle);
    double wireArea = wireForce / tensileYield / 0.67;
    tensileArmorWireSize = Math.sqrt(wireArea * 4 / Math.PI);
    tensileArmorWireSize = Math.max(tensileArmorWireSize, 3.0);

    // Calculate outer diameter
    double calculatedOD = innerDiameter + 2 * carcassThickness + 2 * internalSheathThickness
        + 2 * pressureArmorThickness + numLayers * 2 * tensileArmorWireSize + 2 * 2.0 + // Anti-wear
                                                                                        // tape
        2 * 5.0; // Outer sheath

    flexPipe.setOuterDiameterMm(calculatedOD);
  }

  /**
   * Calculate bend radius requirements.
   */
  private void calculateBendRadius() {
    double outerDiameter = flexPipe.getOuterDiameterMm();

    // MBR typically 6-8 times OD for unbonded
    double mbrFactor = flexPipe.getPipeType() == FlexiblePipe.PipeType.UNBONDED ? 7.0 : 10.0;

    calculatedMBR = outerDiameter * mbrFactor / 1000; // meters

    // Storage MBR is typically 1.5x operational MBR
    storageMBR = calculatedMBR * 1.5;

    // Update pipe
    if (calculatedMBR > flexPipe.getMinimumBendRadius()) {
      flexPipe.setMinimumBendRadius(calculatedMBR);
    }
  }

  /**
   * Calculate fatigue life.
   */
  private void calculateFatigueLife() {
    // Simplified fatigue calculation
    if (flexPipe.getApplication() == FlexiblePipe.Application.DYNAMIC_RISER) {
      // Assume wave period of 10 seconds
      double wavePeriod = 10.0;
      double cyclesPerYear = 365.25 * 24 * 3600 / wavePeriod;
      fatigueLifeCycles = cyclesPerYear * designLife;
    } else {
      // Static applications have lower fatigue requirements
      fatigueLifeCycles = 1e6;
    }
  }

  /**
   * Calculate weight.
   */
  private void calculateWeight() {
    double length = flexPipe.getLength();
    double outerDiameter = flexPipe.getOuterDiameterMm() / 1000; // meters
    double innerDiameter = flexPipe.getInnerDiameterInches() * 0.0254; // meters

    // Calculate weights based on layer volumes
    double steelDensity = 7850.0; // kg/m³
    double polymerDensity = 1400.0; // kg/m³
    double seawaterDensity = 1025.0; // kg/m³

    // Simplified weight calculation
    double totalSteelArea = carcassThickness * innerDiameter * Math.PI / 1e6
        + pressureArmorThickness * (innerDiameter + 0.02) * Math.PI / 1e6
        + flexPipe.getTensileArmorLayers() * tensileArmorWireSize * (innerDiameter + 0.04) * Math.PI
            / 1e6;

    double totalPolymerArea =
        (internalSheathThickness + 5.0) * (innerDiameter + 0.01) * Math.PI / 1e6;

    double dryWeightPerMeter = totalSteelArea * steelDensity + totalPolymerArea * polymerDensity;
    double displacedVolume = Math.PI * outerDiameter * outerDiameter / 4;
    double submergedWeightPerMeter = dryWeightPerMeter - displacedVolume * seawaterDensity;

    // Flooded weight
    double internalVolume = Math.PI * innerDiameter * innerDiameter / 4;
    double floodedWeightPerMeter = dryWeightPerMeter + internalVolume * seawaterDensity;

    flexPipe.setDryWeightPerMeter(dryWeightPerMeter);
    flexPipe.setSubmergedWeightPerMeter(submergedWeightPerMeter);
  }

  /**
   * Calculate cost estimate for the flexible pipe.
   */
  public void calculateCostEstimate() {
    if (costEstimator == null) {
      costEstimator = new SubseaCostEstimator();
    }

    boolean isDynamic = flexPipe.getApplication() == FlexiblePipe.Application.DYNAMIC_RISER;
    // Buoyancy typically used for lazy wave and steep wave configurations
    boolean hasBuoyancy =
        flexPipe.getRiserConfiguration() == FlexiblePipe.RiserConfiguration.LAZY_WAVE
            || flexPipe.getRiserConfiguration() == FlexiblePipe.RiserConfiguration.STEEP_WAVE;

    costEstimator.calculateFlexiblePipeCost(flexPipe.getLength(), flexPipe.getInnerDiameterInches(),
        flexPipe.getWaterDepth(), isDynamic, hasBuoyancy);

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
    return costEstimator.generateBOM("FlexiblePipe",
        flexPipe.getDryWeightPerMeter() * flexPipe.getLength() / 1000, flexPipe.getWaterDepth());
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

    jsonObj.addProperty("equipmentType", "FlexiblePipe");
    jsonObj.addProperty("designStandardCode", designStandardCode);

    JsonObject config = new JsonObject();
    config.addProperty("pipeType", flexPipe.getPipeType().name());
    config.addProperty("application", flexPipe.getApplication().name());
    config.addProperty("serviceType", flexPipe.getServiceType().name());
    config.addProperty("lengthM", flexPipe.getLength());
    config.addProperty("sourService", flexPipe.isSourService());
    jsonObj.add("configuration", config);

    JsonObject design = new JsonObject();
    design.addProperty("designPressureBar", flexPipe.getDesignPressure());
    design.addProperty("designTemperatureC", flexPipe.getDesignTemperature());
    design.addProperty("waterDepthM", flexPipe.getWaterDepth());
    design.addProperty("innerDiameterInches", flexPipe.getInnerDiameterInches());
    jsonObj.add("designParameters", design);

    JsonObject layers = new JsonObject();
    layers.addProperty("hasCarcass", flexPipe.hasCarcass());
    layers.addProperty("carcassThicknessMm", carcassThickness);
    layers.addProperty("internalSheathThicknessMm", internalSheathThickness);
    layers.addProperty("hasPressureArmor", flexPipe.hasPressureArmor());
    layers.addProperty("pressureArmorThicknessMm", pressureArmorThickness);
    layers.addProperty("tensileArmorLayers", flexPipe.getTensileArmorLayers());
    layers.addProperty("tensileArmorWireSizeMm", tensileArmorWireSize);
    jsonObj.add("layerDesign", layers);

    JsonObject results = new JsonObject();
    results.addProperty("requiredBurstPressureBar", requiredBurstPressure);
    results.addProperty("requiredCollapsePressureBar", requiredCollapsePressure);
    results.addProperty("requiredTensileCapacityKN", requiredTensileCapacity);
    results.addProperty("calculatedMBRM", calculatedMBR);
    results.addProperty("storageMBRM", storageMBR);
    results.addProperty("calculatedOuterDiameterMm", flexPipe.getOuterDiameterMm());
    results.addProperty("fatigueLifeCycles", fatigueLifeCycles);
    jsonObj.add("calculatedResults", results);

    JsonObject weight = new JsonObject();
    weight.addProperty("dryWeightPerMeterKgM", flexPipe.getDryWeightPerMeter());
    weight.addProperty("submergedWeightPerMeterKgM", flexPipe.getSubmergedWeightPerMeter());
    weight.addProperty("totalDryWeightTonnes",
        flexPipe.getDryWeightPerMeter() * flexPipe.getLength() / 1000);
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
    result.put("equipmentName", flexPipe.getName());
    result.put("equipmentType", "FlexiblePipe");
    result.put("pipeType", flexPipe.getPipeType().name());
    result.put("requiredBurstPressureBar", requiredBurstPressure);
    result.put("requiredTensileCapacityKN", requiredTensileCapacity);
    result.put("calculatedMBRM", calculatedMBR);
    result.put("carcassThicknessMm", carcassThickness);
    result.put("pressureArmorThicknessMm", pressureArmorThickness);
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
   * Get required burst pressure.
   *
   * @return burst pressure in bar
   */
  public double getRequiredBurstPressure() {
    return requiredBurstPressure;
  }

  /**
   * Get required tensile capacity.
   *
   * @return tensile capacity in kN
   */
  public double getRequiredTensileCapacity() {
    return requiredTensileCapacity;
  }

  /**
   * Get calculated MBR.
   *
   * @return MBR in meters
   */
  public double getCalculatedMBR() {
    return calculatedMBR;
  }
}
