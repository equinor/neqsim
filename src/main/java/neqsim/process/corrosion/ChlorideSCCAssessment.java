package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Chloride stress corrosion cracking (Cl-SCC) assessment for austenitic and duplex stainless
 * steels.
 *
 * <p>
 * Evaluates whether an alloy is susceptible to chloride-induced SCC based on temperature, chloride
 * concentration, and material type. Each alloy family has a defined temperature-chloride envelope
 * beyond which SCC risk is unacceptable.
 * </p>
 *
 * <h2>Standards</h2>
 *
 * <table>
 * <caption>Standards for chloride SCC assessment</caption>
 * <tr>
 * <th>Standard</th>
 * <th>Scope</th>
 * </tr>
 * <tr>
 * <td>NORSOK M-001 Rev 6</td>
 * <td>Material selection — Table A-3 temperature limits</td>
 * </tr>
 * <tr>
 * <td>ISO 15156-3</td>
 * <td>CRA limits in sour/chloride environments</td>
 * </tr>
 * <tr>
 * <td>EFC 17</td>
 * <td>CRA guidelines for oilfield service</td>
 * </tr>
 * <tr>
 * <td>MTI Publication 15</td>
 * <td>SCC guidelines for SS</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 * @see NorsokM001MaterialSelection
 */
public class ChlorideSCCAssessment implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1006L;

  // ─── Input Parameters ───────────────────────────────────

  /** Temperature in degrees Celsius. */
  private double temperatureC = 60.0;

  /** Chloride concentration in mg/L. */
  private double chlorideConcentrationMgL = 0.0;

  /** Material type. */
  private String materialType = "316L";

  /** Applied stress as fraction of yield (0-1). */
  private double stressRatio = 0.8;

  /** Whether dissolved oxygen is present (&gt; 10 ppb). */
  private boolean oxygenPresent = false;

  /** pH of the aqueous phase. */
  private double aqueousPH = 7.0;

  // ─── Results ────────────────────────────────────────────

  /** Whether SCC risk is acceptable. */
  private boolean sccAcceptable = true;

  /** Risk level. */
  private String riskLevel = "";

  /** Maximum allowable temperature for this alloy at the given Cl⁻. */
  private double maxAllowableTemperatureC = 0.0;

  /** Maximum allowable Cl⁻ at the given temperature. */
  private double maxAllowableChlorideMgL = 0.0;

  /** Temperature margin (positive = unsafe). */
  private double temperatureMarginC = 0.0;

  /** Recommended alloy upgrade if current is insufficient. */
  private String recommendedUpgrade = "";

  /** Notes. */
  private List<String> notes = new ArrayList<String>();

  /** Evaluated flag. */
  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public ChlorideSCCAssessment() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets temperature.
   *
   * @param tempC temperature in degrees Celsius
   */
  public void setTemperatureC(double tempC) {
    this.temperatureC = tempC;
  }

  /**
   * Sets chloride concentration.
   *
   * @param mgL chloride in mg/L
   */
  public void setChlorideConcentrationMgL(double mgL) {
    this.chlorideConcentrationMgL = mgL;
  }

  /**
   * Sets material type.
   *
   * @param type material type (e.g., "316L", "304", "22Cr duplex", "25Cr super duplex", "Alloy
   *        625")
   */
  public void setMaterialType(String type) {
    this.materialType = type;
  }

  /**
   * Sets applied stress ratio.
   *
   * @param ratio stress as fraction of yield (0-1)
   */
  public void setStressRatio(double ratio) {
    this.stressRatio = ratio;
  }

  /**
   * Sets oxygen presence.
   *
   * @param present true if dissolved O2 is present (&gt; 10 ppb)
   */
  public void setOxygenPresent(boolean present) {
    this.oxygenPresent = present;
  }

  /**
   * Sets aqueous pH.
   *
   * @param pH pH value
   */
  public void setAqueousPH(double pH) {
    this.aqueousPH = pH;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Performs chloride SCC assessment.
   */
  public void evaluate() {
    notes.clear();

    String upperType = materialType.toUpperCase().trim();

    if (upperType.contains("625") || upperType.contains("C276") || upperType.contains("C-276")
        || upperType.contains("INCONEL") || upperType.contains("HASTELLOY")) {
      evaluateNickelAlloy();
    } else if (upperType.contains("25CR") || upperType.contains("SUPER")
        || upperType.contains("2507")) {
      evaluate25CrSuperDuplex();
    } else if (upperType.contains("22CR") || upperType.contains("DUPLEX")
        || upperType.contains("2205")) {
      evaluate22CrDuplex();
    } else if (upperType.contains("13CR")) {
      evaluate13Cr();
    } else {
      evaluateAustenitic();
    }

    temperatureMarginC = temperatureC - maxAllowableTemperatureC;
    determineRisk();

    if (oxygenPresent) {
      notes.add("WARNING: Dissolved oxygen present — reduces SCC threshold temperature by "
          + "approximately 20-30°C for austenitic grades.");
      if ("316".contains(upperType) || "304".contains(upperType)) {
        maxAllowableTemperatureC -= 25.0;
        temperatureMarginC = temperatureC - maxAllowableTemperatureC;
        determineRisk();
      }
    }

    evaluated = true;
  }

  /**
   * Evaluates austenitic SS (304, 316L, 317L, etc.).
   */
  private void evaluateAustenitic() {
    // NORSOK M-001 / MTI limits for 316L type
    // Low Cl: safe up to ~60°C; very low Cl: up to 100°C
    if (chlorideConcentrationMgL <= 50) {
      maxAllowableTemperatureC = 100.0;
    } else if (chlorideConcentrationMgL <= 200) {
      maxAllowableTemperatureC = 80.0;
    } else if (chlorideConcentrationMgL <= 1000) {
      maxAllowableTemperatureC = 60.0;
    } else if (chlorideConcentrationMgL <= 10000) {
      maxAllowableTemperatureC = 50.0;
    } else {
      maxAllowableTemperatureC = 40.0;
    }

    maxAllowableChlorideMgL = getMaxChlorideForAustenitic(temperatureC);
    recommendedUpgrade = "22Cr duplex stainless steel";
  }

  /**
   * Evaluates 13Cr martensitic.
   */
  private void evaluate13Cr() {
    if (chlorideConcentrationMgL <= 50000) {
      maxAllowableTemperatureC = 150.0;
    } else if (chlorideConcentrationMgL <= 100000) {
      maxAllowableTemperatureC = 120.0;
    } else {
      maxAllowableTemperatureC = 90.0;
    }
    maxAllowableChlorideMgL = getMaxChlorideFor13Cr(temperatureC);
    recommendedUpgrade = "22Cr duplex stainless steel";
  }

  /**
   * Evaluates 22Cr duplex.
   */
  private void evaluate22CrDuplex() {
    // Per EFC 17 and NORSOK M-001
    if (chlorideConcentrationMgL <= 50000) {
      maxAllowableTemperatureC = 232.0;
    } else if (chlorideConcentrationMgL <= 120000) {
      maxAllowableTemperatureC = 200.0;
    } else {
      maxAllowableTemperatureC = 150.0;
    }
    maxAllowableChlorideMgL = getMaxChlorideFor22Cr(temperatureC);
    recommendedUpgrade = "25Cr super duplex or Alloy 625";
  }

  /**
   * Evaluates 25Cr super duplex.
   */
  private void evaluate25CrSuperDuplex() {
    if (chlorideConcentrationMgL <= 120000) {
      maxAllowableTemperatureC = 232.0;
    } else {
      maxAllowableTemperatureC = 200.0;
    }
    maxAllowableChlorideMgL = 200000;
    recommendedUpgrade = "Nickel alloy (Alloy 625 or C-276)";
  }

  /**
   * Evaluates nickel alloys — essentially immune to Cl-SCC.
   */
  private void evaluateNickelAlloy() {
    maxAllowableTemperatureC = 350.0;
    maxAllowableChlorideMgL = 300000;
    sccAcceptable = true;
    riskLevel = "None";
    recommendedUpgrade = "N/A — highest resistance alloy";
    notes.add("Nickel alloys are essentially immune to chloride SCC.");
  }

  /**
   * Determines risk level from temperature margin.
   */
  private void determineRisk() {
    if (temperatureMarginC < -30) {
      sccAcceptable = true;
      riskLevel = "Low";
    } else if (temperatureMarginC < -10) {
      sccAcceptable = true;
      riskLevel = "Medium";
      notes.add("Operating within 30°C of SCC limit — monitor closely.");
    } else if (temperatureMarginC < 0) {
      sccAcceptable = true;
      riskLevel = "High";
      notes.add("Within 10°C of SCC limit — consider upgrade to " + recommendedUpgrade);
    } else {
      sccAcceptable = false;
      riskLevel = "Very High";
      notes.add("ABOVE Cl-SCC limit by " + String.format("%.0f", temperatureMarginC)
          + "°C — upgrade to " + recommendedUpgrade);
    }
  }

  /**
   * Gets max Cl⁻ for austenitic at given temperature.
   *
   * @param tempC temperature in Celsius
   * @return max chloride in mg/L
   */
  private double getMaxChlorideForAustenitic(double tempC) {
    if (tempC <= 40) {
      return 100000;
    } else if (tempC <= 50) {
      return 10000;
    } else if (tempC <= 60) {
      return 1000;
    } else if (tempC <= 80) {
      return 200;
    } else if (tempC <= 100) {
      return 50;
    } else {
      return 10;
    }
  }

  /**
   * Gets max Cl⁻ for 13Cr at given temperature.
   *
   * @param tempC temperature in Celsius
   * @return max chloride in mg/L
   */
  private double getMaxChlorideFor13Cr(double tempC) {
    if (tempC <= 90) {
      return 150000;
    } else if (tempC <= 120) {
      return 100000;
    } else if (tempC <= 150) {
      return 50000;
    } else {
      return 10000;
    }
  }

  /**
   * Gets max Cl⁻ for 22Cr duplex at given temperature.
   *
   * @param tempC temperature in Celsius
   * @return max chloride in mg/L
   */
  private double getMaxChlorideFor22Cr(double tempC) {
    if (tempC <= 150) {
      return 200000;
    } else if (tempC <= 200) {
      return 120000;
    } else if (tempC <= 232) {
      return 50000;
    } else {
      return 10000;
    }
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Checks if SCC risk is acceptable.
   *
   * @return true if acceptable
   */
  public boolean isSCCAcceptable() {
    return sccAcceptable;
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
   * Gets max allowable temperature.
   *
   * @return max temperature in Celsius
   */
  public double getMaxAllowableTemperatureC() {
    return maxAllowableTemperatureC;
  }

  /**
   * Gets max allowable chloride at current temperature.
   *
   * @return max chloride in mg/L
   */
  public double getMaxAllowableChlorideMgL() {
    return maxAllowableChlorideMgL;
  }

  /**
   * Gets temperature margin (positive = unsafe).
   *
   * @return margin in Celsius
   */
  public double getTemperatureMarginC() {
    return temperatureMarginC;
  }

  /**
   * Gets recommended upgrade.
   *
   * @return recommended alloy
   */
  public String getRecommendedUpgrade() {
    return recommendedUpgrade;
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
    map.put("standard", "NORSOK M-001 / EFC 17 / ISO 15156-3");
    map.put("materialType", materialType);
    map.put("temperature_C", temperatureC);
    map.put("chlorideConcentration_mgL", chlorideConcentrationMgL);
    map.put("oxygenPresent", oxygenPresent);
    map.put("sccAcceptable", sccAcceptable);
    map.put("riskLevel", riskLevel);
    map.put("maxAllowableTemperature_C", maxAllowableTemperatureC);
    map.put("maxAllowableChloride_mgL", maxAllowableChlorideMgL);
    map.put("temperatureMargin_C", temperatureMarginC);
    map.put("recommendedUpgrade", recommendedUpgrade);
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
