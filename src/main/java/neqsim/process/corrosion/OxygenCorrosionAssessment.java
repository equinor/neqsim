package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Oxygen corrosion assessment for injection water, utility water, and process systems.
 *
 * <p>
 * Evaluates corrosion risk from dissolved oxygen, including pitting potential and general corrosion
 * rate estimation. Provides treatment recommendations for oxygen removal/scavenging.
 * </p>
 *
 * <h2>Standards</h2>
 *
 * <table>
 * <caption>Standards for oxygen corrosion assessment</caption>
 * <tr>
 * <th>Standard</th>
 * <th>Scope</th>
 * </tr>
 * <tr>
 * <td>NORSOK M-001 Rev 6</td>
 * <td>Material selection — O2 limits</td>
 * </tr>
 * <tr>
 * <td>DNV-RP-B401</td>
 * <td>Cathodic protection design</td>
 * </tr>
 * <tr>
 * <td>ISO 21457</td>
 * <td>Materials for petroleum/natural gas</td>
 * </tr>
 * <tr>
 * <td>NACE SP0499</td>
 * <td>Produced water injection</td>
 * </tr>
 * </table>
 *
 * @author ESOL
 * @version 1.0
 */
public class OxygenCorrosionAssessment implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1007L;

  // ─── Input Parameters ───────────────────────────────────

  /** Dissolved O2 in ppb. */
  private double dissolvedO2Ppb = 0.0;

  /** Temperature in Celsius. */
  private double temperatureC = 25.0;

  /** Chloride concentration in mg/L. */
  private double chlorideMgL = 0.0;

  /** Flow velocity in m/s. */
  private double velocityMS = 1.0;

  /** Material type. */
  private String materialType = "Carbon steel";

  /** Whether chemical scavenger is applied. */
  private boolean scavengerApplied = false;

  /** Whether deaeration tower is used. */
  private boolean deaerationApplied = false;

  /** System type. */
  private String systemType = "injection_water";

  // ─── Results ────────────────────────────────────────────

  /** Corrosion risk level. */
  private String riskLevel = "";

  /** Estimated general corrosion rate in mm/yr. */
  private double corrosionRateMmYr = 0.0;

  /** Maximum pitting rate in mm/yr. */
  private double pittingRateMmYr = 0.0;

  /** Pitting factor (pitting / general corrosion rate). */
  private double pittingFactor = 1.0;

  /** Whether O2 level meets target. */
  private boolean meetsO2Target = false;

  /** Target O2 level for the system in ppb. */
  private double targetO2Ppb = 0.0;

  /** Recommended treatment. */
  private String recommendedTreatment = "";

  /** Notes. */
  private List<String> notes = new ArrayList<String>();

  /** Evaluated flag. */
  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public OxygenCorrosionAssessment() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets dissolved oxygen.
   *
   * @param ppb dissolved O2 in ppb
   */
  public void setDissolvedO2Ppb(double ppb) {
    this.dissolvedO2Ppb = ppb;
  }

  /**
   * Sets temperature.
   *
   * @param tempC temperature in Celsius
   */
  public void setTemperatureC(double tempC) {
    this.temperatureC = tempC;
  }

  /**
   * Sets chloride concentration.
   *
   * @param mgL chloride in mg/L
   */
  public void setChlorideMgL(double mgL) {
    this.chlorideMgL = mgL;
  }

  /**
   * Sets flow velocity.
   *
   * @param ms velocity in m/s
   */
  public void setVelocityMS(double ms) {
    this.velocityMS = ms;
  }

  /**
   * Sets material type.
   *
   * @param type material type
   */
  public void setMaterialType(String type) {
    this.materialType = type;
  }

  /**
   * Sets scavenger application flag.
   *
   * @param applied true if scavenger is used
   */
  public void setScavengerApplied(boolean applied) {
    this.scavengerApplied = applied;
  }

  /**
   * Sets deaeration application flag.
   *
   * @param applied true if deaeration is used
   */
  public void setDeaerationApplied(boolean applied) {
    this.deaerationApplied = applied;
  }

  /**
   * Sets system type.
   *
   * @param type one of "injection_water", "seawater", "utility", "process", "closed_loop"
   */
  public void setSystemType(String type) {
    this.systemType = type;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Performs oxygen corrosion assessment.
   */
  public void evaluate() {
    notes.clear();

    determineTargetO2();
    meetsO2Target = dissolvedO2Ppb <= targetO2Ppb;

    estimateCorrosionRate();
    estimatePittingRate();
    determineRiskLevel();
    generateRecommendations();

    evaluated = true;
  }

  /**
   * Determines target O2 level based on system type.
   */
  private void determineTargetO2() {
    String lower = systemType.toLowerCase().trim();
    if (lower.contains("injection")) {
      targetO2Ppb = 10.0;
      notes.add("Injection water target: <10 ppb O2 (NORSOK M-001 / NACE SP0499).");
    } else if (lower.contains("seawater")) {
      targetO2Ppb = 20.0;
      notes.add("Treated seawater target: <20 ppb O2.");
    } else if (lower.contains("closed")) {
      targetO2Ppb = 5.0;
      notes.add("Closed loop target: <5 ppb O2.");
    } else if (lower.contains("process")) {
      targetO2Ppb = 50.0;
      notes.add("Process water target: <50 ppb O2.");
    } else {
      targetO2Ppb = 20.0;
      notes.add("General utility target: <20 ppb O2.");
    }
  }

  /**
   * Estimates general corrosion rate from dissolved O2.
   */
  private void estimateCorrosionRate() {
    // Empirical correlation: ~0.025 mm/yr per ppb O2 at 25°C for CS
    // with temperature acceleration factor
    double tempFactor = 1.0 + 0.02 * (temperatureC - 25.0);
    if (tempFactor < 0.5) {
      tempFactor = 0.5;
    }
    if (tempFactor > 3.0) {
      tempFactor = 3.0;
    }

    double velocityFactor = 1.0 + 0.3 * Math.log(1.0 + velocityMS);
    if (velocityFactor < 1.0) {
      velocityFactor = 1.0;
    }

    double materialFactor = getMaterialFactor();

    // Base rate from O2 concentration
    double baseRate;
    if (dissolvedO2Ppb <= 0) {
      baseRate = 0.0;
    } else if (dissolvedO2Ppb <= 20) {
      baseRate = dissolvedO2Ppb * 0.005;
    } else if (dissolvedO2Ppb <= 200) {
      baseRate = 0.1 + (dissolvedO2Ppb - 20) * 0.003;
    } else {
      baseRate = 0.64 + (dissolvedO2Ppb - 200) * 0.001;
    }

    corrosionRateMmYr = baseRate * tempFactor * velocityFactor * materialFactor;
    if (corrosionRateMmYr < 0) {
      corrosionRateMmYr = 0;
    }
  }

  /**
   * Estimates pitting rate.
   */
  private void estimatePittingRate() {
    // Pitting factor increases with chloride and O2
    pittingFactor = 2.0;
    if (chlorideMgL > 1000) {
      pittingFactor = 3.0;
    }
    if (chlorideMgL > 20000) {
      pittingFactor = 5.0;
    }
    if (dissolvedO2Ppb > 100) {
      pittingFactor += 1.0;
    }
    pittingRateMmYr = corrosionRateMmYr * pittingFactor;
  }

  /**
   * Gets material corrosion factor.
   *
   * @return material factor
   */
  private double getMaterialFactor() {
    String upper = materialType.toUpperCase().trim();
    if (upper.contains("625") || upper.contains("C276") || upper.contains("C-276")) {
      return 0.01;
    } else if (upper.contains("25CR") || upper.contains("SUPER")) {
      return 0.05;
    } else if (upper.contains("22CR") || upper.contains("DUPLEX")) {
      return 0.1;
    } else if (upper.contains("316") || upper.contains("304")) {
      return 0.2;
    } else if (upper.contains("13CR")) {
      return 0.3;
    } else {
      return 1.0; // Carbon steel baseline
    }
  }

  /**
   * Determines overall risk level.
   */
  private void determineRiskLevel() {
    if (corrosionRateMmYr < 0.05) {
      riskLevel = "Low";
    } else if (corrosionRateMmYr < 0.13) {
      riskLevel = "Medium";
    } else if (corrosionRateMmYr < 0.25) {
      riskLevel = "High";
    } else {
      riskLevel = "Very High";
    }
  }

  /**
   * Generates treatment recommendations.
   */
  private void generateRecommendations() {
    if (meetsO2Target) {
      recommendedTreatment = "O2 within target — maintain current treatment.";
      return;
    }

    List<String> treatments = new ArrayList<String>();
    if (!deaerationApplied && dissolvedO2Ppb > 100) {
      treatments.add("Vacuum deaeration tower (reduces to <20 ppb)");
    }
    if (!scavengerApplied) {
      treatments.add("Chemical O2 scavenger (sodium bisulfite or ammonium bisulfite)");
    }
    if (dissolvedO2Ppb > 1000) {
      treatments.add("Gas stripping with nitrogen blanket");
    }
    if (treatments.isEmpty()) {
      treatments.add("Increase scavenger dosage or add deaeration");
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < treatments.size(); i++) {
      if (i > 0) {
        sb.append("; ");
      }
      sb.append(treatments.get(i));
    }
    recommendedTreatment = sb.toString();
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Gets risk level.
   *
   * @return risk level string
   */
  public String getRiskLevel() {
    return riskLevel;
  }

  /**
   * Gets estimated corrosion rate.
   *
   * @return corrosion rate in mm/yr
   */
  public double getCorrosionRateMmYr() {
    return corrosionRateMmYr;
  }

  /**
   * Gets estimated pitting rate.
   *
   * @return pitting rate in mm/yr
   */
  public double getPittingRateMmYr() {
    return pittingRateMmYr;
  }

  /**
   * Gets pitting factor.
   *
   * @return pitting factor
   */
  public double getPittingFactor() {
    return pittingFactor;
  }

  /**
   * Checks if O2 level meets target.
   *
   * @return true if meets target
   */
  public boolean isMeetsO2Target() {
    return meetsO2Target;
  }

  /**
   * Gets target O2 level.
   *
   * @return target in ppb
   */
  public double getTargetO2Ppb() {
    return targetO2Ppb;
  }

  /**
   * Gets recommended treatment.
   *
   * @return treatment recommendation
   */
  public String getRecommendedTreatment() {
    return recommendedTreatment;
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
    map.put("standard", "NORSOK M-001 / NACE SP0499 / DNV-RP-B401");
    map.put("systemType", systemType);
    map.put("materialType", materialType);
    map.put("dissolvedO2_ppb", dissolvedO2Ppb);
    map.put("targetO2_ppb", targetO2Ppb);
    map.put("meetsO2Target", meetsO2Target);
    map.put("temperature_C", temperatureC);
    map.put("chloride_mgL", chlorideMgL);
    map.put("velocity_ms", velocityMS);
    map.put("corrosionRate_mmyr", Math.round(corrosionRateMmYr * 1000.0) / 1000.0);
    map.put("pittingRate_mmyr", Math.round(pittingRateMmYr * 1000.0) / 1000.0);
    map.put("pittingFactor", pittingFactor);
    map.put("riskLevel", riskLevel);
    map.put("recommendedTreatment", recommendedTreatment);
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
