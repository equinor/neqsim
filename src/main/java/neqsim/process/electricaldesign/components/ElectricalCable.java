package neqsim.process.electricaldesign.components;

import com.google.gson.GsonBuilder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Model of an electrical power cable for process equipment.
 *
 * <p>
 * Supports cable sizing based on load current, voltage drop calculation, derating factors for
 * ambient temperature, grouping, and installation method per IEC 60502 and IEC 60364.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class ElectricalCable implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Standard cable cross-sections in mm2 per IEC 60228. */
  private static final double[] STANDARD_CROSS_SECTIONS =
      {1.5, 2.5, 4.0, 6.0, 10.0, 16.0, 25.0, 35.0, 50.0, 70.0, 95.0, 120.0, 150.0, 185.0,
          240.0, 300.0, 400.0, 500.0, 630.0};

  /**
   * Approximate base ampacity for XLPE copper cables in tray (3-core), per IEC 60502. Index
   * matches STANDARD_CROSS_SECTIONS.
   */
  private static final double[] BASE_AMPACITY_XLPE_TRAY =
      {18, 25, 33, 43, 60, 80, 106, 131, 158, 200, 241, 278, 315, 360, 420, 480, 560, 640, 740};

  private double lengthM = 50.0;
  private double crossSectionMM2;
  private int numberOfCores = 3;
  private String conductorMaterial = "Copper";
  private String insulationType = "XLPE";

  // === Ratings ===
  private double ampacityA;
  private double baseAmpacityA;
  private double voltageDropPercent;
  private double maxVoltageDropPercent = 5.0;

  // === Derating factors ===
  private double ambientTempDeratingFactor = 1.0;
  private double groupingDeratingFactor = 1.0;
  private double burialDepthDeratingFactor = 1.0;

  // === Protection ===
  private double shortCircuitWithstandKA;
  private double shortCircuitDurationS = 1.0;

  // === Installation ===
  private String installationMethod = "Tray";
  private String routeReference = "";

  // === Cost ===
  private double estimatedCostPerMeterUSD;
  private double totalCostUSD;

  /**
   * Size the cable based on load current and conditions.
   *
   * <p>
   * Selects the cable cross-section to carry the load current with appropriate derating, then
   * calculates voltage drop to verify it meets limits.
   * </p>
   *
   * @param loadCurrentA load current in A
   * @param voltageV system voltage in V
   * @param cableLengthM cable length in meters
   * @param installMethod installation method (Tray, Conduit, Direct burial, Ladder)
   * @param ambientTempC ambient temperature in degrees C
   */
  public void sizeCable(double loadCurrentA, double voltageV, double cableLengthM,
      String installMethod, double ambientTempC) {
    this.lengthM = cableLengthM;
    this.installationMethod = installMethod;

    // Calculate derating factors
    ambientTempDeratingFactor = calculateAmbientTempDerating(ambientTempC);
    groupingDeratingFactor = calculateGroupingDerating(installMethod);

    double totalDerating = ambientTempDeratingFactor * groupingDeratingFactor
        * burialDepthDeratingFactor;

    // Required ampacity before derating
    double requiredAmpacity = loadCurrentA / totalDerating;

    // Select cable size
    crossSectionMM2 = 1.5;
    baseAmpacityA = BASE_AMPACITY_XLPE_TRAY[0];
    for (int i = 0; i < STANDARD_CROSS_SECTIONS.length; i++) {
      if (BASE_AMPACITY_XLPE_TRAY[i] >= requiredAmpacity) {
        crossSectionMM2 = STANDARD_CROSS_SECTIONS[i];
        baseAmpacityA = BASE_AMPACITY_XLPE_TRAY[i];
        break;
      }
      if (i == STANDARD_CROSS_SECTIONS.length - 1) {
        // Need parallel cables - select largest size
        crossSectionMM2 = STANDARD_CROSS_SECTIONS[i];
        baseAmpacityA = BASE_AMPACITY_XLPE_TRAY[i];
      }
    }

    // Apply derating
    ampacityA = baseAmpacityA * totalDerating;

    // Adjust for aluminium conductor
    if ("Aluminium".equals(conductorMaterial)) {
      ampacityA *= 0.78;
      crossSectionMM2 = selectNextSizeUp(loadCurrentA / totalDerating / 0.78);
    }

    // Calculate voltage drop
    voltageDropPercent = calculateVoltageDrop(loadCurrentA, voltageV);

    // Check voltage drop - may need to upsize
    while (voltageDropPercent > maxVoltageDropPercent
        && crossSectionMM2 < STANDARD_CROSS_SECTIONS[STANDARD_CROSS_SECTIONS.length - 1]) {
      crossSectionMM2 = selectNextSizeUp(crossSectionMM2 + 1);
      voltageDropPercent = calculateVoltageDrop(loadCurrentA, voltageV);
    }

    // Calculate short-circuit withstand (1s rating)
    shortCircuitWithstandKA = calculateShortCircuitWithstand(crossSectionMM2, shortCircuitDurationS);

    // Estimate cost
    estimatedCostPerMeterUSD = estimateCostPerMeter(crossSectionMM2, voltageV);
    totalCostUSD = estimatedCostPerMeterUSD * lengthM;
  }

  /**
   * Select the next standard cable size above the given cross-section.
   *
   * @param minCrossSection minimum required cross-section in mm2
   * @return next standard cross-section in mm2
   */
  private double selectNextSizeUp(double minCrossSection) {
    for (double std : STANDARD_CROSS_SECTIONS) {
      if (std >= minCrossSection) {
        return std;
      }
    }
    return STANDARD_CROSS_SECTIONS[STANDARD_CROSS_SECTIONS.length - 1];
  }

  /**
   * Calculate voltage drop as a percentage.
   *
   * @param currentA load current in A
   * @param voltageV system voltage in V
   * @return voltage drop in percent
   */
  public double calculateVoltageDrop(double currentA, double voltageV) {
    if (crossSectionMM2 <= 0 || voltageV <= 0 || lengthM <= 0) {
      return 0.0;
    }

    // Resistivity: copper = 0.0175 ohm·mm²/m, aluminium = 0.028
    double resistivity = "Aluminium".equals(conductorMaterial) ? 0.028 : 0.0175;

    // Cable resistance per meter (one-way)
    double rPerMeter = resistivity / crossSectionMM2;

    // Reactance per meter (approximate for 3-phase cables)
    double xPerMeter = 0.00008; // ohm/m typical

    // Assume power factor 0.85 for voltage drop calculation
    double cosPhiVd = 0.85;
    double sinPhiVd = Math.sin(Math.acos(cosPhiVd));

    // 3-phase voltage drop
    double vDrop =
        Math.sqrt(3) * currentA * lengthM * (rPerMeter * cosPhiVd + xPerMeter * sinPhiVd);

    return (vDrop / voltageV) * 100.0;
  }

  /**
   * Calculate ambient temperature derating factor.
   *
   * @param ambientTempC ambient temperature in degrees C
   * @return derating factor
   */
  private double calculateAmbientTempDerating(double ambientTempC) {
    // XLPE rated at 90°C conductor temperature, base ambient 30°C
    double maxConductorTemp = 90.0;
    double baseAmbient = 30.0;
    if (ambientTempC <= baseAmbient) {
      return 1.0;
    }
    double factor =
        Math.sqrt((maxConductorTemp - ambientTempC) / (maxConductorTemp - baseAmbient));
    return Math.max(0.5, Math.min(1.0, factor));
  }

  /**
   * Calculate grouping derating factor based on installation method.
   *
   * @param installMethod installation method
   * @return grouping derating factor
   */
  private double calculateGroupingDerating(String installMethod) {
    if ("Conduit".equals(installMethod)) {
      return 0.80;
    } else if ("Direct burial".equals(installMethod)) {
      return 0.90;
    } else if ("Tray".equals(installMethod)) {
      return 0.85;
    }
    return 1.0; // Ladder, open air
  }

  /**
   * Calculate short-circuit thermal withstand (1s basis) per IEC 60949.
   *
   * @param crossSection cable cross section in mm2
   * @param duration fault duration in seconds
   * @return withstand current in kA
   */
  private double calculateShortCircuitWithstand(double crossSection, double duration) {
    // K factor for XLPE copper: 143 A·√s/mm²
    double kFactor = "Aluminium".equals(conductorMaterial) ? 94.0 : 143.0;
    return (kFactor * crossSection / Math.sqrt(duration)) / 1000.0;
  }

  /**
   * Estimate cable cost per meter.
   *
   * @param crossSection cross-section in mm2
   * @param voltageV rated voltage
   * @return cost per meter in USD
   */
  private double estimateCostPerMeter(double crossSection, double voltageV) {
    double baseCost = crossSection * 0.15 + 2.0;
    if (voltageV > 3300) {
      baseCost *= 3.0;
    } else if (voltageV > 1000) {
      baseCost *= 2.0;
    }
    if ("Aluminium".equals(conductorMaterial)) {
      baseCost *= 0.6;
    }
    return baseCost;
  }

  /**
   * Serialize cable data to JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> map = toMap();
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(map);
  }

  /**
   * Convert cable data to a map.
   *
   * @return map of cable parameters
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("lengthM", lengthM);
    map.put("crossSectionMM2", crossSectionMM2);
    map.put("numberOfCores", numberOfCores);
    map.put("conductorMaterial", conductorMaterial);
    map.put("insulationType", insulationType);
    map.put("ampacityA", ampacityA);
    map.put("voltageDropPercent", voltageDropPercent);
    map.put("installationMethod", installationMethod);
    map.put("shortCircuitWithstandKA", shortCircuitWithstandKA);
    map.put("estimatedCostPerMeterUSD", estimatedCostPerMeterUSD);
    map.put("totalCostUSD", totalCostUSD);
    return map;
  }

  // === Getters and Setters ===

  /**
   * Get cable length in meters.
   *
   * @return cable length in meters
   */
  public double getLengthM() {
    return lengthM;
  }

  /**
   * Set cable length in meters.
   *
   * @param lengthM cable length in meters
   */
  public void setLengthM(double lengthM) {
    this.lengthM = lengthM;
  }

  /**
   * Get cable cross-section in mm2.
   *
   * @return cross-section in mm2
   */
  public double getCrossSectionMM2() {
    return crossSectionMM2;
  }

  /**
   * Set cable cross-section in mm2.
   *
   * @param crossSectionMM2 cross-section in mm2
   */
  public void setCrossSectionMM2(double crossSectionMM2) {
    this.crossSectionMM2 = crossSectionMM2;
  }

  /**
   * Get number of cores.
   *
   * @return number of cores
   */
  public int getNumberOfCores() {
    return numberOfCores;
  }

  /**
   * Set number of cores.
   *
   * @param numberOfCores number of cores
   */
  public void setNumberOfCores(int numberOfCores) {
    this.numberOfCores = numberOfCores;
  }

  /**
   * Get conductor material.
   *
   * @return conductor material (Copper or Aluminium)
   */
  public String getConductorMaterial() {
    return conductorMaterial;
  }

  /**
   * Set conductor material.
   *
   * @param conductorMaterial conductor material (Copper or Aluminium)
   */
  public void setConductorMaterial(String conductorMaterial) {
    this.conductorMaterial = conductorMaterial;
  }

  /**
   * Get insulation type.
   *
   * @return insulation type (XLPE, PVC, EPR)
   */
  public String getInsulationType() {
    return insulationType;
  }

  /**
   * Set insulation type.
   *
   * @param insulationType insulation type
   */
  public void setInsulationType(String insulationType) {
    this.insulationType = insulationType;
  }

  /**
   * Get ampacity (derated current carrying capacity) in A.
   *
   * @return ampacity in A
   */
  public double getAmpacityA() {
    return ampacityA;
  }

  /**
   * Get voltage drop in percent.
   *
   * @return voltage drop percent
   */
  public double getVoltageDropPercent() {
    return voltageDropPercent;
  }

  /**
   * Get maximum allowable voltage drop in percent.
   *
   * @return max voltage drop percent
   */
  public double getMaxVoltageDropPercent() {
    return maxVoltageDropPercent;
  }

  /**
   * Set maximum allowable voltage drop in percent.
   *
   * @param maxVoltageDropPercent max voltage drop percent
   */
  public void setMaxVoltageDropPercent(double maxVoltageDropPercent) {
    this.maxVoltageDropPercent = maxVoltageDropPercent;
  }

  /**
   * Get the installation method.
   *
   * @return installation method
   */
  public String getInstallationMethod() {
    return installationMethod;
  }

  /**
   * Set the installation method.
   *
   * @param installationMethod installation method
   */
  public void setInstallationMethod(String installationMethod) {
    this.installationMethod = installationMethod;
  }

  /**
   * Get route reference.
   *
   * @return route reference
   */
  public String getRouteReference() {
    return routeReference;
  }

  /**
   * Set route reference.
   *
   * @param routeReference route reference
   */
  public void setRouteReference(String routeReference) {
    this.routeReference = routeReference;
  }

  /**
   * Get short-circuit withstand in kA.
   *
   * @return short-circuit withstand in kA
   */
  public double getShortCircuitWithstandKA() {
    return shortCircuitWithstandKA;
  }

  /**
   * Get total cable cost in USD.
   *
   * @return total cost in USD
   */
  public double getTotalCostUSD() {
    return totalCostUSD;
  }

  /**
   * Get estimated cost per meter in USD.
   *
   * @return cost per meter in USD
   */
  public double getEstimatedCostPerMeterUSD() {
    return estimatedCostPerMeterUSD;
  }

  /**
   * Set the burial depth derating factor.
   *
   * @param burialDepthDeratingFactor burial depth derating factor
   */
  public void setBurialDepthDeratingFactor(double burialDepthDeratingFactor) {
    this.burialDepthDeratingFactor = burialDepthDeratingFactor;
  }
}
