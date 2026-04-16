package neqsim.process.corrosion;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.thermo.system.SystemInterface;

/**
 * Sour service material assessment per NACE MR0175 / ISO 15156.
 *
 * <p>
 * Evaluates whether materials are suitable for hydrogen sulfide (H2S)-containing environments by
 * classifying the sour severity region and checking material limits for sulfide stress cracking
 * (SSC), hydrogen-induced cracking (HIC), and stress-oriented hydrogen-induced cracking (SOHIC).
 * </p>
 *
 * <h2>ISO 15156 Sour Service Regions</h2>
 * <ul>
 * <li><b>Region 0</b> — Non-sour: pH2S &lt; 0.3 kPa (0.003 bar) or total P &lt; 0.4 MPa with H2S
 * &lt; 10%</li>
 * <li><b>Region 1</b> — Mildly sour: Low pH2S with moderate pH</li>
 * <li><b>Region 2</b> — Moderately sour: Intermediate pH2S range</li>
 * <li><b>Region 3</b> — Severely sour: High pH2S and/or low pH</li>
 * </ul>
 *
 * <h2>Standards Implemented</h2>
 *
 * <table>
 * <caption>Standards used for sour service assessment</caption>
 * <tr>
 * <th>Standard</th>
 * <th>Scope</th>
 * </tr>
 * <tr>
 * <td>ISO 15156-1</td>
 * <td>General principles for sour service</td>
 * </tr>
 * <tr>
 * <td>ISO 15156-2</td>
 * <td>Carbon and low alloy steels</td>
 * </tr>
 * <tr>
 * <td>ISO 15156-3</td>
 * <td>CRAs and other alloys</td>
 * </tr>
 * <tr>
 * <td>NACE MR0175</td>
 * <td>Equivalent to ISO 15156</td>
 * </tr>
 * <tr>
 * <td>NORSOK M-001</td>
 * <td>Material selection framework</td>
 * </tr>
 * <tr>
 * <td>EFC 16/17</td>
 * <td>Sour service guidelines for CRAs</td>
 * </tr>
 * </table>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * SourServiceAssessment ssa = new SourServiceAssessment();
 * ssa.setH2SPartialPressureBar(0.05);
 * ssa.setTotalPressureBar(100.0);
 * ssa.setCO2PartialPressureBar(3.0);
 * ssa.setInSituPH(4.0);
 * ssa.setTemperatureC(80.0);
 * ssa.setChlorideConcentrationMgL(50000);
 * ssa.setMaterialGrade("X65");
 * ssa.evaluate();
 *
 * int region = ssa.getSourRegion();
 * boolean sscOk = ssa.isSSCAcceptable();
 * boolean hicOk = ssa.isHICAcceptable();
 * String json = ssa.toJson();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see NorsokM001MaterialSelection
 * @see HydrogenMaterialAssessment
 */
public class SourServiceAssessment implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1004L;

  // ─── Input Parameters ───────────────────────────────────

  /** H2S partial pressure in bar. */
  private double h2sPartialPressureBar = 0.0;

  /** Total system pressure in bar. */
  private double totalPressureBar = 1.0;

  /** CO2 partial pressure in bar. */
  private double co2PartialPressureBar = 0.0;

  /** In-situ pH of aqueous phase. */
  private double inSituPH = 4.5;

  /** Temperature in degrees Celsius. */
  private double temperatureC = 25.0;

  /** Chloride concentration in mg/L (ppm). */
  private double chlorideConcentrationMgL = 0.0;

  /** Material grade (e.g., "X52", "X65", "316L", "22Cr duplex"). */
  private String materialGrade = "X65";

  /** Material yield strength in MPa. */
  private double yieldStrengthMPa = 448.0;

  /** Material hardness in HRC. */
  private double hardnessHRC = 22.0;

  /** Whether post-weld heat treatment has been done. */
  private boolean pwhtApplied = false;

  /** Whether free water is present. */
  private boolean freeWaterPresent = true;

  /** Elemental sulfur present. */
  private boolean elementalSulfurPresent = false;

  // ─── Evaluated Results ──────────────────────────────────

  /** ISO 15156 sour region (0–3). */
  private int sourRegion = -1;

  /** Whether SSC risk is acceptable. */
  private boolean sscAcceptable = true;

  /** Whether HIC risk is acceptable. */
  private boolean hicAcceptable = true;

  /** Whether SOHIC risk is acceptable. */
  private boolean sohicAcceptable = true;

  /** SSC risk level. */
  private String sscRiskLevel = "";

  /** HIC risk level. */
  private String hicRiskLevel = "";

  /** Overall sour service risk. */
  private String overallRiskLevel = "";

  /** Recommended material. */
  private String recommendedMaterial = "";

  /** Maximum allowable hardness for this region. */
  private double maxAllowableHardnessHRC = 22.0;

  /** Maximum allowable yield strength in MPa. */
  private double maxAllowableYieldStrengthMPa = 760.0;

  /** Notes and warnings. */
  private List<String> notes = new ArrayList<String>();

  /** Standards applied. */
  private List<String> standardsApplied = new ArrayList<String>();

  /** Whether evaluated. */
  private boolean evaluated = false;

  /**
   * Default constructor.
   */
  public SourServiceAssessment() {}

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets H2S partial pressure.
   *
   * @param pressureBar H2S partial pressure in bar
   */
  public void setH2SPartialPressureBar(double pressureBar) {
    this.h2sPartialPressureBar = pressureBar;
  }

  /**
   * Sets total system pressure.
   *
   * @param pressureBar total pressure in bar
   */
  public void setTotalPressureBar(double pressureBar) {
    this.totalPressureBar = pressureBar;
  }

  /**
   * Sets CO2 partial pressure.
   *
   * @param pressureBar CO2 partial pressure in bar
   */
  public void setCO2PartialPressureBar(double pressureBar) {
    this.co2PartialPressureBar = pressureBar;
  }

  /**
   * Sets in-situ pH of aqueous phase.
   *
   * @param pH in-situ pH value (0-14)
   */
  public void setInSituPH(double pH) {
    this.inSituPH = pH;
  }

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
   * @param mgL chloride concentration in mg/L
   */
  public void setChlorideConcentrationMgL(double mgL) {
    this.chlorideConcentrationMgL = mgL;
  }

  /**
   * Sets material grade.
   *
   * @param grade material grade string (e.g., "X52", "X65", "316L", "22Cr duplex")
   */
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
    autoSetYieldStrength(grade);
  }

  /**
   * Sets yield strength.
   *
   * @param mpa yield strength in MPa
   */
  public void setYieldStrengthMPa(double mpa) {
    this.yieldStrengthMPa = mpa;
  }

  /**
   * Sets material hardness.
   *
   * @param hrc hardness in HRC
   */
  public void setHardnessHRC(double hrc) {
    this.hardnessHRC = hrc;
  }

  /**
   * Sets PWHT status.
   *
   * @param applied true if PWHT has been applied
   */
  public void setPWHTApplied(boolean applied) {
    this.pwhtApplied = applied;
  }

  /**
   * Sets free water presence.
   *
   * @param present true if free water is present
   */
  public void setFreeWaterPresent(boolean present) {
    this.freeWaterPresent = present;
  }

  /**
   * Sets elemental sulfur presence.
   *
   * @param present true if elemental sulfur is present
   */
  public void setElementalSulfurPresent(boolean present) {
    this.elementalSulfurPresent = present;
  }

  /**
   * Sets conditions from a NeqSim fluid object.
   *
   * @param fluid the NeqSim thermodynamic system (after flash)
   */
  public void setFluid(SystemInterface fluid) {
    this.totalPressureBar = fluid.getPressure("bara");
    this.temperatureC = fluid.getTemperature("C");

    if (fluid.hasPhaseType("gas")) {
      int gasIdx = fluid.getPhaseNumberOfPhase("gas");
      if (fluid.getPhase(gasIdx).hasComponent("H2S")) {
        this.h2sPartialPressureBar =
            totalPressureBar * fluid.getPhase(gasIdx).getComponent("H2S").getx();
      }
      if (fluid.getPhase(gasIdx).hasComponent("CO2")) {
        this.co2PartialPressureBar =
            totalPressureBar * fluid.getPhase(gasIdx).getComponent("CO2").getx();
      }
    }
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Performs the full sour service assessment.
   */
  public void evaluate() {
    notes.clear();
    standardsApplied.clear();
    standardsApplied.add("ISO 15156-1:2020 (NACE MR0175)");
    standardsApplied.add("ISO 15156-2:2020 — Carbon and low alloy steels");

    evaluateSourRegion();
    evaluateSSC();
    evaluateHIC();
    evaluateSOHIC();
    evaluateOverallRisk();
    evaluateMaterialRecommendation();

    if (elementalSulfurPresent) {
      notes.add("WARNING: Elemental sulfur present — increases cracking susceptibility. "
          + "Consult ISO 15156-2 Annex B for additional restrictions.");
    }
    if (!freeWaterPresent) {
      notes.add("No free water present — sour service restrictions may be relaxed "
          + "if dry conditions can be guaranteed throughout service life.");
    }
    evaluated = true;
  }

  /**
   * Classifies sour region per ISO 15156-2 Figure B.1.
   */
  private void evaluateSourRegion() {
    double h2sKPa = h2sPartialPressureBar * 100.0;

    // Region 0: Non-sour
    if (h2sKPa < 0.3) {
      sourRegion = 0;
      notes.add("Region 0 (non-sour): pH2S < 0.3 kPa. "
          + "ISO 15156 sour service requirements do not apply.");
      return;
    }

    // Also non-sour if total pressure < 0.4 MPa and H2S < 10%
    if (totalPressureBar < 4.0 && (h2sPartialPressureBar / totalPressureBar) < 0.10) {
      sourRegion = 0;
      notes.add("Region 0: Total P < 0.4 MPa with H2S < 10%.");
      return;
    }

    // Region determination based on pH2S and pH
    // Simplified per ISO 15156-2 Figure B.1 boundaries
    if (h2sKPa >= 0.3 && h2sKPa < 1.0 && inSituPH >= 3.5) {
      sourRegion = 1;
    } else if (h2sKPa >= 1.0 && h2sKPa < 10.0 && inSituPH >= 3.5) {
      sourRegion = 2;
    } else if (h2sKPa >= 10.0 || inSituPH < 3.5) {
      sourRegion = 3;
    } else {
      sourRegion = 1;
    }

    notes.add("Sour Region " + sourRegion + " per ISO 15156-2 Figure B.1 " + "(pH2S="
        + String.format("%.2f", h2sKPa) + " kPa, pH=" + String.format("%.1f", inSituPH) + ").");
  }

  /**
   * Evaluates SSC risk per ISO 15156-2.
   */
  private void evaluateSSC() {
    if (sourRegion == 0) {
      sscAcceptable = true;
      sscRiskLevel = "None";
      return;
    }

    boolean isCRA = isCRAMaterial(materialGrade);
    if (isCRA) {
      standardsApplied.add("ISO 15156-3:2020 — CRAs and other alloys");
      evaluateCRAForSSC();
      return;
    }

    // Carbon/low alloy steels per ISO 15156-2
    maxAllowableHardnessHRC = 22.0;
    maxAllowableYieldStrengthMPa = 760.0;

    boolean hardnessOk = hardnessHRC <= maxAllowableHardnessHRC;
    boolean strengthOk = yieldStrengthMPa <= maxAllowableYieldStrengthMPa;

    if (sourRegion == 1) {
      sscAcceptable = hardnessOk && strengthOk;
      sscRiskLevel = sscAcceptable ? "Low" : "High";
      if (!hardnessOk) {
        notes.add("SSC: Hardness " + hardnessHRC + " HRC exceeds max " + maxAllowableHardnessHRC
            + " HRC for Region 1.");
      }
    } else if (sourRegion == 2) {
      sscAcceptable = hardnessOk && strengthOk && pwhtApplied;
      sscRiskLevel = sscAcceptable ? "Medium" : "High";
      if (!pwhtApplied) {
        notes.add("SSC: PWHT required for Region 2 sour service — not applied.");
      }
    } else {
      // Region 3
      sscAcceptable = hardnessOk && strengthOk && pwhtApplied && yieldStrengthMPa <= 550.0;
      sscRiskLevel = sscAcceptable ? "Medium" : "Very High";
      maxAllowableYieldStrengthMPa = 550.0;
      if (yieldStrengthMPa > 550.0) {
        notes.add(
            "SSC: Yield strength " + yieldStrengthMPa + " MPa exceeds 550 MPa limit for Region 3.");
      }
    }
  }

  /**
   * Evaluates CRA materials for SSC per ISO 15156-3.
   */
  private void evaluateCRAForSSC() {
    String upperGrade = materialGrade.toUpperCase().trim();

    if (upperGrade.contains("316") || upperGrade.contains("304")
        || upperGrade.contains("AUSTENITIC")) {
      // Austenitic SS — generally resistant to SSC but susceptible to Cl-SCC
      sscAcceptable = true;
      sscRiskLevel = "Low";
      if (chlorideConcentrationMgL > 50 && temperatureC > 60) {
        notes.add("Austenitic SS: chloride SCC risk at " + temperatureC + "°C with "
            + chlorideConcentrationMgL + " mg/L Cl⁻.");
      }
    } else if (upperGrade.contains("22CR") || upperGrade.contains("DUPLEX")
        || upperGrade.contains("2205")) {
      sscAcceptable = temperatureC <= 232;
      sscRiskLevel = sscAcceptable ? "Low" : "Medium";
      if (h2sPartialPressureBar > 1.0 && temperatureC > 150) {
        sscAcceptable = false;
        sscRiskLevel = "High";
        notes.add("22Cr duplex: pH2S > 1 bar at " + temperatureC + "°C exceeds EFC 17 limits.");
      }
    } else if (upperGrade.contains("25CR") || upperGrade.contains("SUPER")) {
      sscAcceptable = temperatureC <= 232;
      sscRiskLevel = "Low";
    } else if (upperGrade.contains("625") || upperGrade.contains("C276")
        || upperGrade.contains("NICKEL") || upperGrade.contains("INCONEL")) {
      sscAcceptable = true;
      sscRiskLevel = "None";
    } else if (upperGrade.contains("13CR")) {
      sscAcceptable = h2sPartialPressureBar <= 0.1 && temperatureC <= 150;
      sscRiskLevel = sscAcceptable ? "Low" : "High";
    } else {
      sscAcceptable = hardnessHRC <= 22.0;
      sscRiskLevel = sscAcceptable ? "Medium" : "High";
    }
  }

  /**
   * Evaluates HIC risk per ISO 15156-2 Annex B.
   */
  private void evaluateHIC() {
    if (sourRegion == 0) {
      hicAcceptable = true;
      hicRiskLevel = "None";
      return;
    }

    if (isCRAMaterial(materialGrade)) {
      hicAcceptable = true;
      hicRiskLevel = "None";
      notes.add("CRA materials are immune to HIC.");
      return;
    }

    // Carbon steel HIC susceptibility increases with H2S and low pH
    if (sourRegion == 1) {
      hicAcceptable = true;
      hicRiskLevel = "Low";
    } else if (sourRegion == 2) {
      hicAcceptable = true;
      hicRiskLevel = "Medium";
      notes.add("HIC: Region 2 — HIC-resistant steel recommended (e.g., NACE TM0284 tested).");
    } else {
      hicRiskLevel = "High";
      hicAcceptable = false;
      notes.add("HIC: Region 3 — HIC-resistant steel mandatory. "
          + "Must pass NACE TM0284 testing with CLR < 15%, CTR < 5%.");
    }

    if (elementalSulfurPresent) {
      hicRiskLevel = "Very High";
      hicAcceptable = false;
      notes.add("HIC: Elemental sulfur greatly increases HIC susceptibility.");
    }
  }

  /**
   * Evaluates SOHIC risk.
   */
  private void evaluateSOHIC() {
    if (sourRegion <= 1) {
      sohicAcceptable = true;
      return;
    }

    sohicAcceptable = pwhtApplied;
    if (!sohicAcceptable) {
      notes.add("SOHIC: PWHT required for Region " + sourRegion + " to mitigate SOHIC risk.");
    }
  }

  /**
   * Determines overall risk level.
   */
  private void evaluateOverallRisk() {
    if (sourRegion == 0) {
      overallRiskLevel = "None";
      return;
    }

    List<String> risks = Arrays.asList(sscRiskLevel, hicRiskLevel);
    if (risks.contains("Very High")) {
      overallRiskLevel = "Very High";
    } else if (risks.contains("High")) {
      overallRiskLevel = "High";
    } else if (risks.contains("Medium")) {
      overallRiskLevel = "Medium";
    } else {
      overallRiskLevel = "Low";
    }
  }

  /**
   * Selects recommended material for the sour severity.
   */
  private void evaluateMaterialRecommendation() {
    if (sourRegion == 0) {
      recommendedMaterial = "Carbon steel (no sour restrictions)";
      return;
    }

    if (sourRegion == 1) {
      recommendedMaterial = "Carbon steel (max 22 HRC, SMYS <= 760 MPa)";
    } else if (sourRegion == 2) {
      recommendedMaterial = "HIC-resistant carbon steel + PWHT (or 22Cr duplex for high Cl⁻)";
    } else {
      if (chlorideConcentrationMgL > 50000 || temperatureC > 150) {
        recommendedMaterial = "Nickel alloy (625/C276) or 25Cr super duplex";
      } else {
        recommendedMaterial = "22Cr duplex or HIC-resistant CS with PWHT + full NACE TM0284";
      }
    }
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Gets the ISO 15156 sour region (0-3).
   *
   * @return sour region number
   */
  public int getSourRegion() {
    return sourRegion;
  }

  /**
   * Checks if SSC risk is acceptable.
   *
   * @return true if acceptable
   */
  public boolean isSSCAcceptable() {
    return sscAcceptable;
  }

  /**
   * Checks if HIC risk is acceptable.
   *
   * @return true if acceptable
   */
  public boolean isHICAcceptable() {
    return hicAcceptable;
  }

  /**
   * Checks if SOHIC risk is acceptable.
   *
   * @return true if acceptable
   */
  public boolean isSOHICAcceptable() {
    return sohicAcceptable;
  }

  /**
   * Gets SSC risk level.
   *
   * @return risk level string
   */
  public String getSSCRiskLevel() {
    return sscRiskLevel;
  }

  /**
   * Gets HIC risk level.
   *
   * @return risk level string
   */
  public String getHICRiskLevel() {
    return hicRiskLevel;
  }

  /**
   * Gets overall sour service risk level.
   *
   * @return risk level string
   */
  public String getOverallRiskLevel() {
    return overallRiskLevel;
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
   * Gets maximum allowable hardness for this region.
   *
   * @return maximum hardness in HRC
   */
  public double getMaxAllowableHardnessHRC() {
    return maxAllowableHardnessHRC;
  }

  /**
   * Gets notes and warnings.
   *
   * @return list of notes
   */
  public List<String> getNotes() {
    return notes;
  }

  /**
   * Gets standards applied.
   *
   * @return list of standard references
   */
  public List<String> getStandardsApplied() {
    return standardsApplied;
  }

  /**
   * Checks whether evaluation has been performed.
   *
   * @return true if evaluate() has been called
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  // ─── Utility ────────────────────────────────────────────

  /**
   * Determines if a material grade is a CRA.
   *
   * @param grade material grade string
   * @return true if CRA
   */
  private boolean isCRAMaterial(String grade) {
    String upper = grade.toUpperCase().trim();
    return upper.contains("13CR") || upper.contains("22CR") || upper.contains("25CR")
        || upper.contains("DUPLEX") || upper.contains("316") || upper.contains("304")
        || upper.contains("625") || upper.contains("C276") || upper.contains("INCONEL")
        || upper.contains("HASTELLOY") || upper.contains("SUPER") || upper.contains("AUSTENITIC")
        || upper.contains("NICKEL") || upper.contains("2205") || upper.contains("2507");
  }

  /**
   * Auto-sets yield strength from grade.
   *
   * @param grade material grade
   */
  private void autoSetYieldStrength(String grade) {
    String upper = grade.toUpperCase().trim();
    if (upper.equals("X42")) {
      yieldStrengthMPa = 290.0;
    } else if (upper.equals("X52")) {
      yieldStrengthMPa = 359.0;
    } else if (upper.equals("X60")) {
      yieldStrengthMPa = 414.0;
    } else if (upper.equals("X65")) {
      yieldStrengthMPa = 448.0;
    } else if (upper.equals("X70")) {
      yieldStrengthMPa = 483.0;
    } else if (upper.equals("X80")) {
      yieldStrengthMPa = 552.0;
    }
  }

  /**
   * Converts results to a map.
   *
   * @return ordered map of all results
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("standard", "ISO 15156:2020 (NACE MR0175)");
    map.put("h2sPartialPressure_bar", h2sPartialPressureBar);
    map.put("h2sPartialPressure_kPa", h2sPartialPressureBar * 100.0);
    map.put("totalPressure_bar", totalPressureBar);
    map.put("co2PartialPressure_bar", co2PartialPressureBar);
    map.put("inSituPH", inSituPH);
    map.put("temperature_C", temperatureC);
    map.put("chlorideConcentration_mgL", chlorideConcentrationMgL);
    map.put("materialGrade", materialGrade);
    map.put("yieldStrength_MPa", yieldStrengthMPa);
    map.put("hardness_HRC", hardnessHRC);
    map.put("pwhtApplied", pwhtApplied);
    map.put("freeWaterPresent", freeWaterPresent);
    map.put("elementalSulfurPresent", elementalSulfurPresent);
    map.put("sourRegion", sourRegion);
    map.put("sscAcceptable", sscAcceptable);
    map.put("sscRiskLevel", sscRiskLevel);
    map.put("hicAcceptable", hicAcceptable);
    map.put("hicRiskLevel", hicRiskLevel);
    map.put("sohicAcceptable", sohicAcceptable);
    map.put("overallRiskLevel", overallRiskLevel);
    map.put("maxAllowableHardness_HRC", maxAllowableHardnessHRC);
    map.put("maxAllowableYieldStrength_MPa", maxAllowableYieldStrengthMPa);
    map.put("recommendedMaterial", recommendedMaterial);
    map.put("standardsApplied", standardsApplied);
    map.put("notes", notes);
    return map;
  }

  /**
   * Converts results to JSON string.
   *
   * @return JSON representation of the assessment
   */
  public String toJson() {
    Gson gson =
        new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
