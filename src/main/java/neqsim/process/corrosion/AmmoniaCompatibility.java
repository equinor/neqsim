package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Ammonia compatibility assessment for materials in ammonia service.
 *
 * <p>
 * Evaluates material suitability for anhydrous ammonia, aqueous ammonia, and ammonia as a hydrogen
 * carrier. Covers SCC of carbon steel in anhydrous NH3, copper alloy incompatibility, and
 * temperature/stress limits.
 * </p>
 *
 * <h2>Key Failure Mechanisms</h2>
 * <ul>
 * <li>SCC of carbon steel in anhydrous NH3 (requires O2 inhibitor at 0.1-0.2% by weight)</li>
 * <li>Copper and copper alloy dissolution and SCC in ammonia environments</li>
 * <li>Nitridation at elevated temperatures (&gt;300°C)</li>
 * <li>Caustic embrittlement in concentrated aqueous NH3</li>
 * </ul>
 *
 * <h2>Standards</h2>
 *
 * <table>
 * <caption>Standards for ammonia service assessment</caption>
 * <tr>
 * <th>Standard</th>
 * <th>Scope</th>
 * </tr>
 * <tr>
 * <td>CGA G-2</td>
 * <td>Anhydrous ammonia hose connections</td>
 * </tr>
 * <tr>
 * <td>CGA G-2.1</td>
 * <td>Safety requirements for ammonia storage</td>
 * </tr>
 * <tr>
 * <td>ASME B31.3</td>
 * <td>Process piping — ammonia service</td>
 * </tr>
 * <tr>
 * <td>IGC Code</td>
 * <td>Gas carriers — ammonia compatibility</td>
 * </tr>
 * <tr>
 * <td>49 CFR 173.315</td>
 * <td>DOT requirements — anhydrous ammonia</td>
 * </tr>
 * <tr>
 * <td>API 660</td>
 * <td>Shell-and-tube HX — ammonia considerations</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 */
public class AmmoniaCompatibility implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1009L;

  // ─── Input Parameters ───────────────────────────────────

  /** Temperature in Celsius. */
  private double temperatureC = 25.0;

  /** Pressure in bara. */
  private double pressureBara = 10.0;

  /** Ammonia concentration in wt%. */
  private double nh3ConcentrationWtPct = 99.5;

  /** Whether this is anhydrous ammonia service. */
  private boolean anhydrous = true;

  /** Water content in wt%. */
  private double waterContentWtPct = 0.2;

  /** O2 content in wt% (inhibitor). */
  private double o2InhibitorWtPct = 0.0;

  /** Material type. */
  private String materialType = "Carbon steel";

  /** Applied stress as fraction of yield. */
  private double stressRatio = 0.8;

  /** Whether PWHT has been applied. */
  private boolean pwhtApplied = false;

  /** Hardness in HRC. */
  private double hardnessHRC = 20.0;

  // ─── Results ────────────────────────────────────────────

  /** Whether material is compatible. */
  private boolean compatible = true;

  /** Risk level. */
  private String riskLevel = "";

  /** Primary failure mechanism. */
  private String primaryMechanism = "";

  /** Whether O2 inhibitor is adequate. */
  private boolean o2InhibitorAdequate = true;

  /** Required O2 inhibitor level. */
  private double requiredO2InhibitorWtPct = 0.0;

  /** Maximum allowable temperature for material. */
  private double maxAllowableTempC = 0.0;

  /** Maximum allowable hardness. */
  private double maxAllowableHRC = 0.0;

  /** Recommended material. */
  private String recommendedMaterial = "";

  /** Notes. */
  private List<String> notes = new ArrayList<String>();

  /** Evaluated flag. */
  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public AmmoniaCompatibility() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets temperature.
   *
   * @param tempC temperature in Celsius
   */
  public void setTemperatureC(double tempC) {
    this.temperatureC = tempC;
  }

  /**
   * Sets pressure.
   *
   * @param bara pressure in bara
   */
  public void setPressureBara(double bara) {
    this.pressureBara = bara;
  }

  /**
   * Sets NH3 concentration.
   *
   * @param wtPct NH3 weight percent
   */
  public void setNh3ConcentrationWtPct(double wtPct) {
    this.nh3ConcentrationWtPct = wtPct;
  }

  /**
   * Sets anhydrous flag.
   *
   * @param anhydrous true for anhydrous ammonia
   */
  public void setAnhydrous(boolean anhydrous) {
    this.anhydrous = anhydrous;
  }

  /**
   * Sets water content.
   *
   * @param wtPct water in wt%
   */
  public void setWaterContentWtPct(double wtPct) {
    this.waterContentWtPct = wtPct;
  }

  /**
   * Sets O2 inhibitor content.
   *
   * @param wtPct O2 in wt%
   */
  public void setO2InhibitorWtPct(double wtPct) {
    this.o2InhibitorWtPct = wtPct;
  }

  /**
   * Sets material type.
   *
   * @param type material type string
   */
  public void setMaterialType(String type) {
    this.materialType = type;
  }

  /**
   * Sets stress ratio.
   *
   * @param ratio stress as fraction of yield (0-1)
   */
  public void setStressRatio(double ratio) {
    this.stressRatio = ratio;
  }

  /**
   * Sets PWHT flag.
   *
   * @param applied true if PWHT applied
   */
  public void setPwhtApplied(boolean applied) {
    this.pwhtApplied = applied;
  }

  /**
   * Sets hardness.
   *
   * @param hrc hardness in HRC
   */
  public void setHardnessHRC(double hrc) {
    this.hardnessHRC = hrc;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Performs ammonia compatibility assessment.
   */
  public void evaluate() {
    notes.clear();

    String upperType = materialType.toUpperCase().trim();

    if (upperType.contains("COPPER") || upperType.contains("BRASS") || upperType.contains("BRONZE")
        || upperType.contains("CU")) {
      evaluateCopperAlloy();
    } else if (upperType.contains("316") || upperType.contains("304")
        || upperType.contains("AUSTENITIC")) {
      evaluateAusteniticSS();
    } else if (upperType.contains("22CR") || upperType.contains("DUPLEX")) {
      evaluateDuplexSS();
    } else if (upperType.contains("625") || upperType.contains("C276")
        || upperType.contains("C-276") || upperType.contains("NICKEL")) {
      evaluateNickelAlloy();
    } else {
      evaluateCarbonSteel();
    }

    if (temperatureC > 300.0 && !upperType.contains("625") && !upperType.contains("C276")
        && !upperType.contains("C-276")) {
      notes.add("WARNING: Nitridation risk at T > 300°C. "
          + "Consider alloy 625 or specialized nitridation-resistant material.");
      if (primaryMechanism.isEmpty()) {
        primaryMechanism = "Nitridation";
      }
    }

    evaluated = true;
  }

  /**
   * Evaluates carbon steel in ammonia service.
   */
  private void evaluateCarbonSteel() {
    maxAllowableTempC = 150.0;
    maxAllowableHRC = 22.0;

    if (anhydrous) {
      evaluateCarbonSteelAnhydrous();
    } else {
      evaluateCarbonSteelAqueous();
    }
  }

  /**
   * Evaluates CS in anhydrous NH3 — SCC is the primary concern.
   */
  private void evaluateCarbonSteelAnhydrous() {
    primaryMechanism = "SCC (anhydrous ammonia)";

    // O2 inhibitor requirement: 0.1-0.2 wt%
    requiredO2InhibitorWtPct = 0.1;
    o2InhibitorAdequate = o2InhibitorWtPct >= requiredO2InhibitorWtPct;

    boolean hardnessOk = hardnessHRC <= maxAllowableHRC;
    boolean tempOk = temperatureC <= maxAllowableTempC;

    if (!o2InhibitorAdequate) {
      compatible = false;
      riskLevel = "Very High";
      notes.add("CRITICAL: O2 inhibitor required at >= 0.1 wt% for anhydrous NH3 with CS. "
          + "Current: " + o2InhibitorWtPct + " wt%.");
    } else if (!hardnessOk) {
      compatible = false;
      riskLevel = "High";
      notes.add("Hardness " + hardnessHRC + " HRC exceeds " + maxAllowableHRC
          + " HRC limit for NH3 SCC resistance.");
    } else if (!tempOk) {
      compatible = false;
      riskLevel = "High";
      notes.add("Temperature " + temperatureC + "°C exceeds " + maxAllowableTempC + "°C limit.");
    } else if (!pwhtApplied) {
      compatible = true;
      riskLevel = "High";
      notes.add("PWHT strongly recommended for CS in anhydrous NH3 to reduce "
          + "residual stress and SCC susceptibility.");
    } else {
      compatible = true;
      riskLevel = "Low";
    }

    recommendedMaterial = compatible ? "Carbon steel with O2 inhibitor + PWHT"
        : "316L stainless steel (immune to NH3 SCC)";
  }

  /**
   * Evaluates CS in aqueous NH3.
   */
  private void evaluateCarbonSteelAqueous() {
    primaryMechanism = "General corrosion / alkaline SCC";
    requiredO2InhibitorWtPct = 0.0;
    o2InhibitorAdequate = true;

    if (nh3ConcentrationWtPct > 25.0 && temperatureC > 60.0) {
      compatible = false;
      riskLevel = "High";
      notes.add(
          "Concentrated aqueous NH3 at elevated temperature — " + "caustic embrittlement risk.");
      recommendedMaterial = "316L stainless steel";
    } else {
      compatible = true;
      riskLevel = "Low";
      notes.add("Aqueous ammonia at moderate concentration — CS acceptable.");
      recommendedMaterial = "Carbon steel";
    }
  }

  /**
   * Evaluates copper alloy — NEVER compatible with ammonia.
   */
  private void evaluateCopperAlloy() {
    compatible = false;
    riskLevel = "Very High";
    primaryMechanism = "SCC + dissolution (copper-ammonia complex)";
    maxAllowableTempC = -273.15; // Not allowed at any temperature
    maxAllowableHRC = 0;
    o2InhibitorAdequate = false;
    requiredO2InhibitorWtPct = Double.POSITIVE_INFINITY;
    recommendedMaterial = "316L stainless steel or carbon steel";
    notes.add("PROHIBITED: Copper and copper alloys are incompatible with ammonia. "
        + "NH3 forms soluble copper-ammine complexes causing rapid dissolution and SCC. "
        + "Replace with stainless steel or carbon steel.");
  }

  /**
   * Evaluates austenitic SS — generally excellent in NH3.
   */
  private void evaluateAusteniticSS() {
    compatible = true;
    riskLevel = "Low";
    primaryMechanism = "None (immune to NH3 SCC)";
    maxAllowableTempC = 400.0;
    maxAllowableHRC = 35.0;
    o2InhibitorAdequate = true;
    requiredO2InhibitorWtPct = 0.0;
    recommendedMaterial = materialType;
    notes.add("Austenitic stainless steel is immune to ammonia SCC and "
        + "is an excellent choice for NH3 service.");

    if (temperatureC > 400.0) {
      compatible = false;
      riskLevel = "Medium";
      notes.add("Above 400°C — nitridation/sensitization concern.");
    }
  }

  /**
   * Evaluates duplex SS.
   */
  private void evaluateDuplexSS() {
    compatible = true;
    riskLevel = "Low";
    primaryMechanism = "None";
    maxAllowableTempC = 300.0;
    maxAllowableHRC = 32.0;
    o2InhibitorAdequate = true;
    requiredO2InhibitorWtPct = 0.0;
    recommendedMaterial = materialType;
    notes.add("Duplex SS is suitable for ammonia service up to 300°C.");

    if (temperatureC > 300.0) {
      compatible = false;
      riskLevel = "Medium";
      notes.add("475°C embrittlement concerns for duplex above 300°C.");
    }
  }

  /**
   * Evaluates nickel alloy — best choice for high-temp NH3.
   */
  private void evaluateNickelAlloy() {
    compatible = true;
    riskLevel = "Low";
    primaryMechanism = "None";
    maxAllowableTempC = 600.0;
    maxAllowableHRC = 40.0;
    o2InhibitorAdequate = true;
    requiredO2InhibitorWtPct = 0.0;
    recommendedMaterial = materialType;
    notes.add("Nickel alloys provide excellent ammonia compatibility "
        + "including high-temperature service.");
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Checks if material is compatible.
   *
   * @return true if compatible
   */
  public boolean isCompatible() {
    return compatible;
  }

  /**
   * Gets risk level.
   *
   * @return risk level string
   */
  public String getRiskLevel() {
    return riskLevel;
  }

  /**
   * Gets primary failure mechanism.
   *
   * @return mechanism description
   */
  public String getPrimaryMechanism() {
    return primaryMechanism;
  }

  /**
   * Checks if O2 inhibitor is adequate.
   *
   * @return true if adequate or not needed
   */
  public boolean isO2InhibitorAdequate() {
    return o2InhibitorAdequate;
  }

  /**
   * Gets required O2 inhibitor level.
   *
   * @return required level in wt%
   */
  public double getRequiredO2InhibitorWtPct() {
    return requiredO2InhibitorWtPct;
  }

  /**
   * Gets max allowable temperature.
   *
   * @return max temp in Celsius
   */
  public double getMaxAllowableTempC() {
    return maxAllowableTempC;
  }

  /**
   * Gets max allowable hardness.
   *
   * @return max HRC
   */
  public double getMaxAllowableHRC() {
    return maxAllowableHRC;
  }

  /**
   * Gets recommended material.
   *
   * @return recommended material string
   */
  public String getRecommendedMaterial() {
    return recommendedMaterial;
  }

  /**
   * Gets notes.
   *
   * @return list of notes
   */
  public List<String> getNotes() {
    return notes;
  }

  /**
   * Converts results to a map.
   *
   * @return ordered map of results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("standard", "CGA G-2 / CGA G-2.1 / ASME B31.3 / IGC Code");
    map.put("materialType", materialType);
    map.put("anhydrous", anhydrous);
    map.put("nh3Concentration_wtPct", nh3ConcentrationWtPct);
    map.put("temperature_C", temperatureC);
    map.put("pressure_bara", pressureBara);
    map.put("waterContent_wtPct", waterContentWtPct);
    map.put("o2Inhibitor_wtPct", o2InhibitorWtPct);
    map.put("hardness_HRC", hardnessHRC);
    map.put("pwhtApplied", pwhtApplied);
    map.put("compatible", compatible);
    map.put("riskLevel", riskLevel);
    map.put("primaryMechanism", primaryMechanism);
    map.put("o2InhibitorAdequate", o2InhibitorAdequate);
    map.put("requiredO2Inhibitor_wtPct", requiredO2InhibitorWtPct);
    map.put("maxAllowableTemperature_C", maxAllowableTempC);
    map.put("maxAllowableHardness_HRC", maxAllowableHRC);
    map.put("recommendedMaterial", recommendedMaterial);
    map.put("notes", notes);
    return map;
  }

  /**
   * Converts results to JSON string.
   *
   * @return JSON representation
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
