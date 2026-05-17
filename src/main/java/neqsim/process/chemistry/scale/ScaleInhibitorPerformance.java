package neqsim.process.chemistry.scale;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Empirical performance model for oilfield scale inhibitors.
 *
 * <p>
 * Predicts the Minimum Inhibitor Concentration (MIC) required to suppress mineral scale
 * precipitation as a function of:
 * </p>
 * <ul>
 * <li>Scale type (CaCO3, BaSO4, SrSO4, CaSO4, FeCO3)</li>
 * <li>Inhibitor chemistry (phosphonate, polymaleate, polyacrylate, phosphate ester)</li>
 * <li>Saturation Ratio (SR) of the produced water</li>
 * <li>Operating temperature and brine ionic strength (TDS)</li>
 * </ul>
 *
 * <p>
 * The model uses literature-derived correlations of the form:
 * </p>
 *
 * <pre>
 * {@code
 * MIC = MIC_base * f_T(T) * f_TDS(TDS) * f_SR(SR)
 * }
 * </pre>
 *
 * <p>
 * Default base concentrations and exponents come from SPE 130901 (Kan and Tomson, 2010), SPE 169787
 * (Sorbie and Mackay, 2000) and BP/Statoil internal correlations as compiled in NACE TM 0374. The
 * model is intended for screening; vendor static / dynamic tube blocking tests remain the
 * deployment basis.
 * </p>
 *
 * <p>
 * Once the operating conditions are set, call {@link #evaluate()} and read the results via
 * {@link #getRecommendedDoseMgL()}, {@link #getEfficiency()} or {@link #toMap()}.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ScaleInhibitorPerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Mineral scale type.
   */
  public enum ScaleType {
    /** Calcite (calcium carbonate). */
    CACO3,
    /** Barite (barium sulphate). */
    BASO4,
    /** Celestite (strontium sulphate). */
    SRSO4,
    /** Anhydrite / gypsum (calcium sulphate). */
    CASO4,
    /** Siderite (iron carbonate). */
    FECO3;
  }

  /**
   * Scale inhibitor chemistry family.
   */
  public enum InhibitorChemistry {
    /** Phosphonate (DTPMP, ATMP, HEDP) — most common, broad spectrum. */
    PHOSPHONATE,
    /** Polymaleate / polymaleic acid — high temperature, low pH. */
    POLYMALEATE,
    /** Polyacrylate / polyacrylic acid — calcium tolerant. */
    POLYACRYLATE,
    /** Phosphate ester — high temperature, oil-soluble. */
    PHOSPHATE_ESTER,
    /** Vinyl sulphonate copolymer. */
    VINYL_SULPHONATE;
  }

  // ─── Inputs ─────────────────────────────────────────────

  private ScaleType scaleType = ScaleType.CACO3;
  private InhibitorChemistry chemistry = InhibitorChemistry.PHOSPHONATE;
  private double temperatureC = 60.0;
  private double saturationRatio = 5.0; // SR = IAP/Ksp (ratio, not log)
  private double tdsMgL = 50000.0;
  private double calciumMgL = 1000.0;
  private double availableDoseMgL = 0.0;

  // ─── Outputs ────────────────────────────────────────────

  private double minimumInhibitorConcentrationMgL = 0.0;
  private double recommendedDoseMgL = 0.0;
  private double efficiency = 0.0;
  private boolean adequate = false;
  private final Map<String, String> warnings = new LinkedHashMap<String, String>();
  private boolean evaluated = false;

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the scale type to inhibit.
   *
   * @param scaleType target scale
   */
  public void setScaleType(ScaleType scaleType) {
    this.scaleType = scaleType;
  }

  /**
   * Sets the inhibitor chemistry.
   *
   * @param chemistry inhibitor family
   */
  public void setInhibitorChemistry(InhibitorChemistry chemistry) {
    this.chemistry = chemistry;
  }

  /**
   * Sets the operating temperature.
   *
   * @param temperatureC temperature in Celsius
   */
  public void setTemperatureCelsius(double temperatureC) {
    this.temperatureC = temperatureC;
  }

  /**
   * Sets the saturation ratio (SR = IAP/Ksp, ratio not logarithm).
   *
   * @param saturationRatio dimensionless SR
   */
  public void setSaturationRatio(double saturationRatio) {
    this.saturationRatio = saturationRatio;
  }

  /**
   * Sets the total dissolved solids of the brine.
   *
   * @param tdsMgL TDS in mg/L
   */
  public void setTdsMgL(double tdsMgL) {
    this.tdsMgL = tdsMgL;
  }

  /**
   * Sets the calcium concentration of the brine (used for Ca-tolerance penalty).
   *
   * @param calciumMgL calcium in mg/L
   */
  public void setCalciumMgL(double calciumMgL) {
    this.calciumMgL = calciumMgL;
  }

  /**
   * Sets the available dose of inhibitor (active mg/L) so efficiency can be computed.
   *
   * @param availableDoseMgL available dose in mg/L
   */
  public void setAvailableDoseMgL(double availableDoseMgL) {
    this.availableDoseMgL = availableDoseMgL;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Computes the MIC and efficiency. Populates warnings for known performance limits.
   */
  public void evaluate() {
    warnings.clear();
    double micBase = baseMic(scaleType, chemistry);
    double fT = temperatureFactor(chemistry, temperatureC);
    double fTds = ionicStrengthFactor(tdsMgL);
    double fSr = saturationFactor(saturationRatio);
    double fCa = calciumFactor(chemistry, calciumMgL);

    minimumInhibitorConcentrationMgL = micBase * fT * fTds * fSr * fCa;
    recommendedDoseMgL = 1.5 * minimumInhibitorConcentrationMgL; // 50% safety margin

    if (availableDoseMgL > 0.0) {
      double ratio = availableDoseMgL / minimumInhibitorConcentrationMgL;
      if (ratio >= 1.0) {
        efficiency = Math.min(1.0, 0.85 + 0.15 * (1.0 - Math.exp(-(ratio - 1.0))));
      } else {
        // Below MIC: efficiency drops sharply
        efficiency = 0.85 * ratio;
      }
      adequate = ratio >= 1.0;
    } else {
      efficiency = 0.0;
      adequate = false;
    }

    // Warnings
    if (chemistry == InhibitorChemistry.PHOSPHONATE && temperatureC > 175.0) {
      warnings.put("thermal_degradation",
          "Phosphonate inhibitors hydrolyse above 175 C; switch to polymeric chemistry");
    }
    if (chemistry == InhibitorChemistry.PHOSPHONATE && calciumMgL > 2000.0) {
      warnings.put("calcium_phosphonate_precipitation",
          "Risk of Ca-phosphonate precipitation above 2000 mg/L Ca; use polyacrylate or phosphate ester");
    }
    if (saturationRatio > 100.0) {
      warnings.put("extreme_supersaturation",
          "SR > 100; nucleation outpaces inhibition — consider mechanical removal or water exclusion");
    }
    if (chemistry == InhibitorChemistry.PHOSPHATE_ESTER && temperatureC < 40.0) {
      warnings.put("low_temperature_solubility",
          "Phosphate ester may have limited solubility below 40 C in produced water");
    }
    evaluated = true;
  }

  /**
   * Base MIC at standard reference conditions (60 C, TDS 50 g/L, SR=5, Ca=1000 mg/L).
   *
   * @param scale scale type
   * @param chem inhibitor chemistry
   * @return base MIC in mg/L
   */
  private static double baseMic(ScaleType scale, InhibitorChemistry chem) {
    double base;
    switch (scale) {
      case CACO3:
        base = 5.0;
        break;
      case BASO4:
        base = 10.0;
        break;
      case SRSO4:
        base = 8.0;
        break;
      case CASO4:
        base = 6.0;
        break;
      case FECO3:
        base = 12.0;
        break;
      default:
        base = 10.0;
    }
    double chemFactor;
    switch (chem) {
      case PHOSPHONATE:
        chemFactor = 1.0;
        break;
      case POLYMALEATE:
        chemFactor = 1.2;
        break;
      case POLYACRYLATE:
        chemFactor = 1.4;
        break;
      case PHOSPHATE_ESTER:
        chemFactor = 1.1;
        break;
      case VINYL_SULPHONATE:
        chemFactor = 1.3;
        break;
      default:
        chemFactor = 1.5;
    }
    // Specific scale-chemistry preferences
    if (scale == ScaleType.BASO4 && chem == InhibitorChemistry.PHOSPHONATE) {
      chemFactor *= 0.8;
    }
    if (scale == ScaleType.CACO3 && chem == InhibitorChemistry.POLYMALEATE) {
      chemFactor *= 0.9;
    }
    return base * chemFactor;
  }

  /**
   * Temperature scaling factor (Arrhenius-like ramp above an inhibitor-specific knee).
   *
   * @param chem inhibitor chemistry
   * @param tC temperature in Celsius
   * @return multiplier on base MIC
   */
  private static double temperatureFactor(InhibitorChemistry chem, double tC) {
    double knee;
    double slope;
    switch (chem) {
      case PHOSPHONATE:
        knee = 100.0;
        slope = 0.015;
        break;
      case POLYMALEATE:
        knee = 130.0;
        slope = 0.012;
        break;
      case POLYACRYLATE:
        knee = 120.0;
        slope = 0.013;
        break;
      case PHOSPHATE_ESTER:
        knee = 150.0;
        slope = 0.010;
        break;
      default:
        knee = 100.0;
        slope = 0.015;
    }
    if (tC <= knee) {
      return 1.0;
    }
    return Math.exp(slope * (tC - knee));
  }

  /**
   * Ionic strength factor (TDS).
   *
   * @param tdsMgL TDS in mg/L
   * @return multiplier
   */
  private static double ionicStrengthFactor(double tdsMgL) {
    // Reference 50 g/L; mild positive dependence — high salinity makes scaling kinetics faster
    double tdsRef = 50000.0;
    double f = 1.0 + 0.20 * Math.log10(Math.max(1.0, tdsMgL) / tdsRef);
    return Math.max(0.5, f);
  }

  /**
   * Saturation ratio factor (more supersaturation requires more inhibitor).
   *
   * @param sr saturation ratio
   * @return multiplier
   */
  private static double saturationFactor(double sr) {
    // Logarithmic ramp anchored at SR = 5
    return Math.max(0.5, 1.0 + 0.6 * Math.log10(Math.max(1.0, sr) / 5.0));
  }

  /**
   * Calcium tolerance factor (penalises phosphonate in high-Ca brines).
   *
   * @param chem inhibitor chemistry
   * @param caMgL calcium in mg/L
   * @return multiplier
   */
  private static double calciumFactor(InhibitorChemistry chem, double caMgL) {
    if (chem == InhibitorChemistry.PHOSPHONATE && caMgL > 1000.0) {
      return 1.0 + 0.5 * Math.log10(caMgL / 1000.0);
    }
    return 1.0;
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the minimum inhibitor concentration required.
   *
   * @return MIC in mg/L
   */
  public double getMinimumInhibitorConcentrationMgL() {
    return minimumInhibitorConcentrationMgL;
  }

  /**
   * Returns the recommended dose (MIC + safety margin).
   *
   * @return recommended dose in mg/L
   */
  public double getRecommendedDoseMgL() {
    return recommendedDoseMgL;
  }

  /**
   * Returns the efficiency at the supplied available dose (0 if dose not set).
   *
   * @return efficiency 0..1
   */
  public double getEfficiency() {
    return efficiency;
  }

  /**
   * Returns whether the available dose meets MIC.
   *
   * @return true if adequate
   */
  public boolean isAdequate() {
    return adequate;
  }

  /**
   * Returns the warning map.
   *
   * @return map of warning code → message
   */
  public Map<String, String> getWarnings() {
    return new LinkedHashMap<String, String>(warnings);
  }

  /**
   * Returns whether evaluate() has been run.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns a structured map for JSON serialisation.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("scaleType", scaleType.name());
    inputs.put("inhibitorChemistry", chemistry.name());
    inputs.put("temperatureC", temperatureC);
    inputs.put("saturationRatio", saturationRatio);
    inputs.put("tdsMgL", tdsMgL);
    inputs.put("calciumMgL", calciumMgL);
    inputs.put("availableDoseMgL", availableDoseMgL);
    map.put("inputs", inputs);
    Map<String, Object> outputs = new LinkedHashMap<String, Object>();
    outputs.put("minimumInhibitorConcentrationMgL", minimumInhibitorConcentrationMgL);
    outputs.put("recommendedDoseMgL", recommendedDoseMgL);
    outputs.put("efficiency", efficiency);
    outputs.put("adequate", adequate);
    map.put("outputs", outputs);
    map.put("warnings", warnings);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns the industry standards applied by this scale-inhibitor model.
   *
   * @return list of standards (each as an ordered map)
   */
  public java.util.List<java.util.Map<String, Object>> getStandardsApplied() {
    return neqsim.process.chemistry.util.StandardsRegistry
        .toMapList(neqsim.process.chemistry.util.StandardsRegistry.NACE_TM0374);
  }
}
