package neqsim.process.mechanicaldesign.heatexchanger;

import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.heatexchanger.LNGHeatExchanger;

/**
 * Mechanical design for brazed aluminium plate-fin heat exchangers (BAHX).
 *
 * <p>
 * Provides BAHX-specific mechanical design calculations including:
 * </p>
 * <ul>
 * <li>Wall thickness per ASME VIII Div.1 for aluminium vessels</li>
 * <li>Brazed joint efficiency (0.80 per ASME VIII)</li>
 * <li>Minimum design metal temperature (MDMT) for cryogenic service</li>
 * <li>Thermal fatigue assessment from cyclic thermal gradients</li>
 * <li>Core weight and nozzle sizing</li>
 * <li>Material specifications for aluminium 3003-H14 and 5083</li>
 * </ul>
 *
 * <p>
 * Standards implemented: ASME VIII Div.1, ALPEMA (Aluminium Plate-Fin Heat Exchanger Manufacturers'
 * Association), API 662 Part II.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public class BAHXMechanicalDesign extends HeatExchangerMechanicalDesign {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  // ============================================================================
  // BAHX-specific material constants
  // ============================================================================

  /** Allowable stress for Al 3003-H14 at room temperature in MPa. */
  private static final double AL_3003_ALLOWABLE_STRESS_MPA = 62.0;

  /** Allowable stress for Al 5083 header/nozzle at room temperature in MPa. */
  private static final double AL_5083_ALLOWABLE_STRESS_MPA = 117.0;

  /** Aluminium 3003 density in kg/m3. */
  private static final double AL_DENSITY = 2730.0;

  /** Aluminium thermal conductivity in W/(m K). */
  private static final double AL_THERMAL_CONDUCTIVITY = 170.0;

  /** Aluminium coefficient of thermal expansion in 1/K. */
  private static final double AL_CTE = 23.1e-6;

  /** Young's modulus for aluminium 3003 in MPa. */
  private static final double AL_YOUNGS_MODULUS_MPA = 69000.0;

  /** Brazed joint efficiency per ASME VIII. */
  private static final double BRAZED_JOINT_EFFICIENCY = 0.80;

  /** BAHX metal fraction of core volume (typical 0.25-0.35). */
  private static final double METAL_FRACTION = 0.30;

  /** Default MDMT for aluminium BAHX in deg C. */
  private static final double DEFAULT_MDMT_C = -269.0;

  /** Allowable thermal gradient per API 662 Part II in deg C per metre. */
  private static final double ALLOWABLE_THERMAL_GRADIENT = 5.0;

  /** Default fatigue design cycles for LNG service. */
  private static final int DEFAULT_DESIGN_CYCLES = 10000;

  // ============================================================================
  // BAHX-specific fields
  // ============================================================================

  /** Core material grade for fins and parting sheets. */
  private String coreMaterialGrade = "3003-H14";

  /** Header material grade. */
  private String headerMaterialGrade = "5083";

  /** Nozzle material grade. */
  private String nozzleMaterialGrade = "5083";

  /** Design life in years. */
  private int designLifeYears = 25;

  /** Number of thermal cycles for fatigue assessment. */
  private int designCycles = DEFAULT_DESIGN_CYCLES;

  /** Maximum allowable thermal gradient in deg C/m. */
  private double allowableThermalGradient = ALLOWABLE_THERMAL_GRADIENT;

  /** Number of nozzles (inlet + outlet per stream). */
  private int numberOfNozzles = 4;

  /** Nozzle outer diameter in mm. */
  private double nozzleODMm = 150.0;

  // ════════════════════════════════════════════════════════════════════
  // Calculated results
  // ════════════════════════════════════════════════════════════════════

  /** Minimum required parting sheet thickness in mm. */
  private double requiredPartingSheetThicknessMm = 0.0;

  /** Minimum required header plate thickness in mm. */
  private double requiredHeaderThicknessMm = 0.0;

  /** Minimum required nozzle wall thickness in mm. */
  private double requiredNozzleThicknessMm = 0.0;

  /** Core weight in kg. */
  private double coreWeightKg = 0.0;

  /** Header weight in kg. */
  private double headerWeightKg = 0.0;

  /** Nozzle weight in kg. */
  private double nozzleWeightKg = 0.0;

  /** Maximum thermal gradient found in analysis in deg C/m. */
  private double maxThermalGradient = 0.0;

  /** Fatigue utilisation factor (0 to 1). */
  private double fatigueUtilisation = 0.0;

  /** Whether thermal fatigue check passes. */
  private boolean fatiguePassed = true;

  /** BAHX heat transfer area in m2. */
  private double heatTransferAreaM2 = 0.0;

  /** Core length in m. */
  private double coreLengthM = 0.0;

  /** Core width in m. */
  private double coreWidthM = 0.0;

  /** Core height in m. */
  private double coreHeightM = 0.0;

  /**
   * Constructor for BAHXMechanicalDesign.
   *
   * @param equipment the LNG heat exchanger equipment
   */
  public BAHXMechanicalDesign(ProcessEquipmentInterface equipment) {
    super(equipment);
    // BAHX defaults: no fouling, aluminium, cryogenic
    setJointEfficiency(BRAZED_JOINT_EFFICIENCY);
    setTemaClass("N/A");
  }

  /** {@inheritDoc} */
  @Override
  public void calcDesign() {
    ProcessEquipmentInterface equipment = getProcessEquipment();

    // Get operating conditions
    double operatingPressureBara = getMaxOperationPressure();
    if (operatingPressureBara <= 0) {
      operatingPressureBara = 50.0; // default BAHX operating pressure
    }
    double operatingTempC = getMaxOperationTemperature() - 273.15;

    // Design conditions (with margins)
    double designPressureBara = operatingPressureBara * 1.10;
    double designPressureMPa = designPressureBara * 0.1;
    double designTempC = operatingTempC + 25.0;

    // ── Parting sheet thickness (ASME VIII Div.1 UG-27) ──
    // t = P * R / (S * E - 0.6 * P)
    // For BAHX, R is half the fin height (passage half-width)
    double finHeight = 0.006; // default 6 mm
    if (equipment instanceof LNGHeatExchanger) {
      LNGHeatExchanger lngHX = (LNGHeatExchanger) equipment;
      LNGHeatExchanger.FinGeometry fin = lngHX.getStreamFinGeometry(0);
      if (fin != null) {
        finHeight = fin.getFinHeight();
      }
    }
    double rMm = finHeight * 1000.0 / 2.0; // half passage width in mm
    double pMPa = designPressureMPa;
    double sMPa = AL_3003_ALLOWABLE_STRESS_MPA;
    double eFactor = BRAZED_JOINT_EFFICIENCY;
    requiredPartingSheetThicknessMm = pMPa * rMm / (sMPa * eFactor - 0.6 * pMPa);
    requiredPartingSheetThicknessMm = Math.max(requiredPartingSheetThicknessMm, 1.0); // min 1mm

    // ── Header plate thickness ──
    // Treat as flat plate: t = d * sqrt(C * P / S)
    // C = 0.33 for simply supported
    double headerWidthMm = 200.0; // typical BAHX header port width
    requiredHeaderThicknessMm =
        headerWidthMm * Math.sqrt(0.33 * pMPa / AL_5083_ALLOWABLE_STRESS_MPA);
    requiredHeaderThicknessMm = Math.max(requiredHeaderThicknessMm, 10.0);

    // ── Nozzle wall thickness (cylindrical) ──
    double nozzleRadiusMm = nozzleODMm / 2.0;
    requiredNozzleThicknessMm =
        pMPa * nozzleRadiusMm / (AL_5083_ALLOWABLE_STRESS_MPA * 1.0 - 0.6 * pMPa);
    requiredNozzleThicknessMm = Math.max(requiredNozzleThicknessMm, 3.0);

    // ── Core geometry from LNGHeatExchanger or estimated ──
    if (equipment instanceof LNGHeatExchanger) {
      LNGHeatExchanger lngHX = (LNGHeatExchanger) equipment;
      LNGHeatExchanger.CoreGeometry core = lngHX.getCoreGeometry();
      if (core != null && core.getLength() > 0) {
        coreLengthM = core.getLength();
        coreWidthM = core.getWidth();
        coreHeightM = core.getHeight();
        coreWeightKg = core.getWeight();
        // If weight not set, estimate from core volume
        if (coreWeightKg <= 0) {
          double vol = coreLengthM * coreWidthM * coreHeightM;
          coreWeightKg = vol * METAL_FRACTION * AL_DENSITY;
        }
      }
      // Estimate area from UA if available
      double[] uaPerZone = lngHX.getUAPerZone();
      double totalUA = 0;
      for (double ua : uaPerZone) {
        totalUA += ua;
      }
      if (totalUA > 0) {
        heatTransferAreaM2 = totalUA / 400.0; // typical h for BAHX
      }
    }

    // Fallback core sizing if not available from LNGHeatExchanger
    if (coreLengthM <= 0) {
      double duty = Math.abs(getDuty()); // W
      // If base class duty not set, try to get from equipment
      if (duty <= 0 && equipment instanceof LNGHeatExchanger) {
        duty = Math.abs(((LNGHeatExchanger) equipment).getDuty());
      }
      double lmtd = 10.0; // conservative LMTD estimate for LNG
      double uValue = 600.0; // W/(m2 K) typical BAHX
      if (duty > 0 && lmtd > 0) {
        heatTransferAreaM2 = duty / (uValue * lmtd);
      } else {
        heatTransferAreaM2 = 1000.0; // default 1000 m2
      }
      double beta = 1000.0; // m2/m3 surface area density
      double coreVolume = heatTransferAreaM2 / beta;
      double wh = Math.pow(coreVolume / 6.0, 1.0 / 3.0);
      coreLengthM = 6.0 * wh;
      coreWidthM = wh;
      coreHeightM = wh;
      coreWeightKg = coreVolume * METAL_FRACTION * AL_DENSITY;
    }

    // ── Header and nozzle weights ──
    double headerVolume = 2.0 * coreWidthM * coreHeightM * (requiredHeaderThicknessMm / 1000.0);
    headerWeightKg = headerVolume * AL_DENSITY;

    double nozzleLength = 0.3; // m, typical nozzle length
    double nozzleVolume = numberOfNozzles * Math.PI * (nozzleODMm / 1000.0) * nozzleLength
        * (requiredNozzleThicknessMm / 1000.0);
    nozzleWeightKg = nozzleVolume * AL_DENSITY;

    // Total weight
    double totalWeight = coreWeightKg + headerWeightKg + nozzleWeightKg;
    setWeightTotal(totalWeight);
    setWallThickness(requiredPartingSheetThicknessMm);
    setInnerDiameter(coreWidthM * 1000.0); // store core width as "diameter"

    // ── Thermal fatigue assessment ──
    calcThermalFatigue();

    // ── Module dimensions ──
    setModuleLength(coreLengthM + 2.0);
    setModuleWidth(coreWidthM + 0.8);
    setModuleHeight(coreHeightM + 0.5);
  }

  /**
   * Calculate thermal fatigue assessment per API 662 Part II.
   *
   * <p>
   * Evaluates thermal stress from cyclic temperature gradients across the core. The allowable
   * number of cycles is estimated from the alternating stress amplitude using aluminium S-N data.
   * </p>
   */
  private void calcThermalFatigue() {
    ProcessEquipmentInterface equipment = getProcessEquipment();
    maxThermalGradient = 0.0;

    if (equipment instanceof LNGHeatExchanger) {
      LNGHeatExchanger lngHX = (LNGHeatExchanger) equipment;
      double[] gradients = lngHX.getThermalGradientPerZone();
      for (double g : gradients) {
        if (Math.abs(g) > maxThermalGradient) {
          maxThermalGradient = Math.abs(g);
        }
      }
    }

    if (maxThermalGradient <= 0) {
      // Estimate from temperature range
      double hotTempC = getMaxOperationTemperature() - 273.15;
      double coldTempC = -162.0; // LNG temperature
      double deltaT = Math.abs(hotTempC - coldTempC);
      if (coreLengthM > 0) {
        maxThermalGradient = deltaT / coreLengthM;
      }
    }

    // Thermal stress = E * alpha * deltaT_local
    // For a zone: deltaT_local = gradient * zone_length
    double zoneDeltaT = maxThermalGradient * 0.1; // typical zone length ~0.1 m
    double thermalStressMPa = AL_YOUNGS_MODULUS_MPA * AL_CTE * zoneDeltaT;

    // Alternating stress amplitude (zero-mean cycle)
    double stressAmplitudeMPa = thermalStressMPa / 2.0;

    // Aluminium S-N curve approximation (based on AA 3003 welded/brazed data)
    // N_allow = (S_e / sigma_a)^m where S_e=35 MPa at 1e7, m=4
    double enduranceLimitMPa = 35.0;
    double slopeM = 4.0;
    double allowableCycles;
    if (stressAmplitudeMPa > 0) {
      allowableCycles = Math.pow(enduranceLimitMPa / stressAmplitudeMPa, slopeM) * 1e7;
    } else {
      allowableCycles = Double.MAX_VALUE;
    }

    fatigueUtilisation = designCycles / allowableCycles;
    fatiguePassed = fatigueUtilisation <= 1.0 && maxThermalGradient <= allowableThermalGradient;
  }

  /**
   * Get the complete mechanical design report as JSON.
   *
   * @return JSON string with all BAHX design data
   */
  @Override
  public String toJson() {
    Map<String, Object> report = new LinkedHashMap<String, Object>();

    // Design basis
    Map<String, Object> designBasis = new LinkedHashMap<String, Object>();
    designBasis.put("designStandard", "ASME VIII Div.1 + ALPEMA");
    designBasis.put("designPressure_bara", round(getMaxOperationPressure() * 1.10, 1));
    designBasis.put("designTemperature_C", round(getMaxOperationTemperature() - 273.15 + 25.0, 1));
    designBasis.put("MDMT_C", DEFAULT_MDMT_C);
    designBasis.put("jointEfficiency", BRAZED_JOINT_EFFICIENCY);
    report.put("designBasis", designBasis);

    // Materials
    Map<String, Object> materials = new LinkedHashMap<String, Object>();
    materials.put("coreMaterial", coreMaterialGrade);
    materials.put("headerMaterial", headerMaterialGrade);
    materials.put("nozzleMaterial", nozzleMaterialGrade);
    materials.put("coreAllowableStress_MPa", AL_3003_ALLOWABLE_STRESS_MPA);
    materials.put("headerAllowableStress_MPa", AL_5083_ALLOWABLE_STRESS_MPA);
    materials.put("thermalConductivity_WmK", AL_THERMAL_CONDUCTIVITY);
    materials.put("density_kgm3", AL_DENSITY);
    report.put("materials", materials);

    // Wall thickness results
    Map<String, Object> wallThickness = new LinkedHashMap<String, Object>();
    wallThickness.put("requiredPartingSheetThickness_mm",
        round(requiredPartingSheetThicknessMm, 2));
    wallThickness.put("requiredHeaderThickness_mm", round(requiredHeaderThicknessMm, 1));
    wallThickness.put("requiredNozzleThickness_mm", round(requiredNozzleThicknessMm, 1));
    wallThickness.put("nozzleOD_mm", nozzleODMm);
    wallThickness.put("numberOfNozzles", numberOfNozzles);
    report.put("wallThickness", wallThickness);

    // Core geometry
    Map<String, Object> coreGeom = new LinkedHashMap<String, Object>();
    coreGeom.put("length_m", round(coreLengthM, 2));
    coreGeom.put("width_m", round(coreWidthM, 2));
    coreGeom.put("height_m", round(coreHeightM, 2));
    coreGeom.put("heatTransferArea_m2", round(heatTransferAreaM2, 0));
    report.put("coreGeometry", coreGeom);

    // Weights
    Map<String, Object> weights = new LinkedHashMap<String, Object>();
    weights.put("coreWeight_kg", round(coreWeightKg, 0));
    weights.put("headerWeight_kg", round(headerWeightKg, 0));
    weights.put("nozzleWeight_kg", round(nozzleWeightKg, 0));
    weights.put("totalWeight_kg", round(getWeightTotal(), 0));
    report.put("weights", weights);

    // Module dimensions
    Map<String, Object> modDims = new LinkedHashMap<String, Object>();
    modDims.put("length_m", round(getModuleLength(), 1));
    modDims.put("width_m", round(getModuleWidth(), 1));
    modDims.put("height_m", round(getModuleHeight(), 1));
    report.put("moduleDimensions", modDims);

    // Thermal fatigue
    Map<String, Object> fatigue = new LinkedHashMap<String, Object>();
    fatigue.put("maxThermalGradient_CperM", round(maxThermalGradient, 2));
    fatigue.put("allowableThermalGradient_CperM", allowableThermalGradient);
    fatigue.put("designCycles", designCycles);
    fatigue.put("fatigueUtilisation", round(fatigueUtilisation, 4));
    fatigue.put("fatiguePassed", fatiguePassed);
    report.put("thermalFatigue", fatigue);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(report);
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Get core material grade.
   *
   * @return core material grade string
   */
  public String getCoreMaterialGrade() {
    return coreMaterialGrade;
  }

  /**
   * Set core material grade.
   *
   * @param grade material grade (e.g. "3003-H14")
   */
  public void setCoreMaterialGrade(String grade) {
    this.coreMaterialGrade = grade;
  }

  /**
   * Get header material grade.
   *
   * @return header material grade string
   */
  public String getHeaderMaterialGrade() {
    return headerMaterialGrade;
  }

  /**
   * Set header material grade.
   *
   * @param grade material grade (e.g. "5083")
   */
  public void setHeaderMaterialGrade(String grade) {
    this.headerMaterialGrade = grade;
  }

  /**
   * Get design life in years.
   *
   * @return design life years
   */
  public int getDesignLifeYears() {
    return designLifeYears;
  }

  /**
   * Set design life in years.
   *
   * @param years design life
   */
  public void setDesignLifeYears(int years) {
    this.designLifeYears = years;
  }

  /**
   * Get design cycles for fatigue.
   *
   * @return number of design cycles
   */
  public int getDesignCycles() {
    return designCycles;
  }

  /**
   * Set design cycles for fatigue.
   *
   * @param cycles number of design cycles
   */
  public void setDesignCycles(int cycles) {
    this.designCycles = cycles;
  }

  /**
   * Get the nozzle outer diameter.
   *
   * @return nozzle OD in mm
   */
  public double getNozzleODMm() {
    return nozzleODMm;
  }

  /**
   * Set the nozzle outer diameter.
   *
   * @param od nozzle OD in mm
   */
  public void setNozzleODMm(double od) {
    this.nozzleODMm = od;
  }

  /**
   * Get required parting sheet thickness.
   *
   * @return thickness in mm
   */
  public double getRequiredPartingSheetThicknessMm() {
    return requiredPartingSheetThicknessMm;
  }

  /**
   * Get required header thickness.
   *
   * @return thickness in mm
   */
  public double getRequiredHeaderThicknessMm() {
    return requiredHeaderThicknessMm;
  }

  /**
   * Get required nozzle wall thickness.
   *
   * @return thickness in mm
   */
  public double getRequiredNozzleThicknessMm() {
    return requiredNozzleThicknessMm;
  }

  /**
   * Get the core weight.
   *
   * @return core weight in kg
   */
  public double getCoreWeightKg() {
    return coreWeightKg;
  }

  /**
   * Get the heat transfer area.
   *
   * @return area in m2
   */
  public double getHeatTransferAreaM2() {
    return heatTransferAreaM2;
  }

  /**
   * Get the core length.
   *
   * @return length in m
   */
  public double getCoreLengthM() {
    return coreLengthM;
  }

  /**
   * Get the core width.
   *
   * @return width in m
   */
  public double getCoreWidthM() {
    return coreWidthM;
  }

  /**
   * Get the core height.
   *
   * @return height in m
   */
  public double getCoreHeightM() {
    return coreHeightM;
  }

  /**
   * Get the maximum thermal gradient.
   *
   * @return gradient in deg C/m
   */
  public double getMaxThermalGradient() {
    return maxThermalGradient;
  }

  /**
   * Get the fatigue utilisation factor.
   *
   * @return utilisation (0 to 1, greater than 1 means failure)
   */
  public double getFatigueUtilisation() {
    return fatigueUtilisation;
  }

  /**
   * Check if thermal fatigue assessment passed.
   *
   * @return true if passed
   */
  public boolean isFatiguePassed() {
    return fatiguePassed;
  }

  /**
   * Get the module length (core + headers + nozzles).
   *
   * @return module length in m
   */
  @Override
  public double getModuleLength() {
    return coreLengthM + 2.0;
  }

  /**
   * Get the module width.
   *
   * @return module width in m
   */
  @Override
  public double getModuleWidth() {
    return coreWidthM + 0.8;
  }

  /**
   * Get the module height.
   *
   * @return module height in m
   */
  @Override
  public double getModuleHeight() {
    return coreHeightM + 0.5;
  }

  /**
   * Round a double value to the specified number of decimal places.
   *
   * @param value the value to round
   * @param decimals number of decimal places
   * @return rounded value
   */
  private double round(double value, int decimals) {
    if (Double.isNaN(value) || Double.isInfinite(value)) {
      return value;
    }
    double factor = Math.pow(10, decimals);
    return Math.round(value * factor) / factor;
  }
}
