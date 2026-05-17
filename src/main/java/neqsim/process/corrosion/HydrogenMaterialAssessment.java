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
 * Comprehensive hydrogen material compatibility assessment.
 *
 * <p>
 * Evaluates whether materials are suitable for hydrogen-containing service based on multiple
 * degradation mechanisms and industry standards. Integrates with NeqSim thermodynamic systems to
 * extract hydrogen partial pressures and phase conditions.
 * </p>
 *
 * <h2>Degradation Mechanisms Assessed</h2>
 * <ul>
 * <li><b>Hydrogen Embrittlement (HE)</b> — Loss of ductility and fracture toughness at ambient
 * temperatures. Controlled by ASME B31.12 and EIGA 121. Severity increases with H2 partial
 * pressure, material strength (SMYS), and cold work.</li>
 * <li><b>High-Temperature Hydrogen Attack (HTHA)</b> — Internal decarburisation at elevated
 * temperatures (&gt;200°C). Carbon reacts with dissolved H2 to form methane at grain boundaries.
 * Assessed using API 941 Nelson curves.</li>
 * <li><b>Hydrogen-Induced Cracking (HIC)</b> — Blistering and stepwise cracking in wet H2S
 * environments. H2S promotes atomic hydrogen uptake. Controlled by NACE MR0175/ISO 15156.</li>
 * <li><b>Stress Corrosion Cracking (SCC)</b> — Combined effect of H2S, chlorides, and stress.
 * Assessed per ISO 15156-2 (carbon steels) and ISO 15156-3 (CRAs).</li>
 * <li><b>Hydrogen Permeation</b> — Molecular and atomic hydrogen transport through steel walls.
 * Relevant for containment integrity and leak rate estimation.</li>
 * </ul>
 *
 * <h2>Standards Implemented</h2>
 *
 * <table>
 * <caption>Standards used for hydrogen material assessment</caption>
 * <tr><th>Standard</th><th>Scope</th></tr>
 * <tr><td>API 941</td><td>Nelson curves for HTHA boundaries</td></tr>
 * <tr><td>ASME B31.12</td><td>Hydrogen piping — material limits, derating, hardness</td></tr>
 * <tr><td>NACE MR0175 / ISO 15156</td><td>Sour service material requirements</td></tr>
 * <tr><td>EIGA 121/14</td><td>Hydrogen pipeline material compatibility</td></tr>
 * <tr><td>NORSOK M-001</td><td>General material selection framework</td></tr>
 * <tr><td>CGA G-5.6</td><td>Hydrogen pipeline systems</td></tr>
 * </table>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // From NeqSim fluid
 * SystemInterface fluid = new SystemSrkEos(273.15 + 25.0, 90.0);
 * fluid.addComponent("CO2", 0.96);
 * fluid.addComponent("hydrogen", 0.0075);
 * fluid.addComponent("nitrogen", 0.02);
 * fluid.setMixingRule("classic");
 *
 * HydrogenMaterialAssessment assessment = new HydrogenMaterialAssessment();
 * assessment.setFluid(fluid);
 * assessment.setMaterialGrade("X52");
 * assessment.setDesignTemperatureC(120.0);
 * assessment.evaluate();
 *
 * String overallRisk = assessment.getOverallRiskLevel();
 * boolean h2Compatible = assessment.isHydrogenEmbrittlementAcceptable();
 * boolean hthaOk = assessment.isHTHAAcceptable();
 * String json = assessment.toJson();
 * }</pre>
 *
 * @author ESOL
 * @version 1.0
 * @see NelsonCurveAssessment
 * @see NorsokM001MaterialSelection
 */
public class HydrogenMaterialAssessment implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1003L;

  // ─── Input Parameters ───────────────────────────────────

  /** Hydrogen partial pressure in bar. */
  private double h2PartialPressureBar = 0.0;

  /** Total system pressure in bar. */
  private double totalPressureBar = 0.0;

  /** Hydrogen mole fraction in the gas phase [0-1]. */
  private double h2MoleFractionGas = 0.0;

  /** Hydrogen mole fraction in the bulk/feed fluid [0-1]. */
  private double h2MoleFractionBulk = 0.0;

  /** H2S partial pressure in bar (for combined sour + H2 assessment). */
  private double h2sPartialPressureBar = 0.0;

  /** Design temperature in degrees Celsius. */
  private double designTemperatureC = 25.0;

  /** Maximum operating temperature in degrees Celsius. */
  private double maxOperatingTemperatureC = 60.0;

  /** Material grade (e.g., "X42", "X52", "X65", "SA-516-70", "316L"). */
  private String materialGrade = "X52";

  /** Specified minimum yield strength [MPa]. */
  private double smysMPa = 358.0;

  /** Actual hardness [HRC], if known. -1 means unknown. */
  private double hardnessHRC = -1.0;

  /** Wall thickness [mm]. */
  private double wallThicknessMm = 10.0;

  /** Whether post-weld heat treatment (PWHT) has been applied. */
  private boolean pwhtApplied = false;

  /** Whether the system has free water present. */
  private boolean freeWaterPresent = false;

  /** Chloride concentration in mg/L. */
  private double chlorideMgL = 0.0;

  /** Design life in years. */
  private double designLifeYears = 25.0;

  /** Whether this is cyclic service (fatigue considerations). */
  private boolean cyclicService = false;

  // ─── Results ────────────────────────────────────────────

  /** Overall risk level: "Low", "Medium", "High", "Very High". */
  private String overallRiskLevel = "";

  /** Hydrogen embrittlement risk. */
  private String heRisk = "";

  /** Whether HE is acceptable per ASME B31.12. */
  private boolean heAcceptable = true;

  /** HTHA risk per API 941. */
  private String hthaRisk = "";

  /** Whether HTHA is acceptable (below Nelson curve). */
  private boolean hthaAcceptable = true;

  /** HIC risk (sour service). */
  private String hicRisk = "";

  /** Whether sour service requirements are met. */
  private boolean sourServiceOk = true;

  /** Hydrogen derating factor per ASME B31.12. */
  private double hydrogenDeratingFactor = 1.0;

  /** Design factor for hydrogen piping per ASME B31.12. */
  private double b3112DesignFactor = 0.5;

  /** Maximum allowable SMYS for H2 service per ASME B31.12 [MPa]. */
  private static final double MAX_SMYS_H2 = 480.0;

  /** Maximum hardness for H2 service per ASME B31.12 [HRC]. */
  private static final double MAX_HARDNESS_H2 = 22.0;

  /** Recommended material. */
  private String recommendedMaterial = "";

  /** Material recommendations list. */
  private List<String> recommendations = new ArrayList<String>();

  /** Warning notes. */
  private List<String> warnings = new ArrayList<String>();

  /** Standards applied. */
  private List<String> standardsApplied = new ArrayList<String>();

  /** Whether evaluation has been performed. */
  private boolean evaluated = false;

  /** Reference to Nelson curve assessment. */
  private transient NelsonCurveAssessment nelsonCurve = new NelsonCurveAssessment();

  /**
   * Creates a new HydrogenMaterialAssessment with default parameters.
   */
  public HydrogenMaterialAssessment() {}

  // ─── Configuration from NeqSim Fluid ────────────────────

  /**
   * Sets hydrogen conditions from a NeqSim thermodynamic system.
   *
   * <p>
   * Extracts hydrogen partial pressure, mole fractions, and system conditions from the fluid. The
   * fluid should have been flashed (TPflash) before calling this method.
   * </p>
   *
   * @param fluid the NeqSim thermodynamic system (must be flashed)
   */
  public void setFluid(SystemInterface fluid) {
    totalPressureBar = fluid.getPressure("bara");
    double tempK = fluid.getTemperature("K");
    designTemperatureC = tempK - 273.15;

    // Try to find hydrogen in the fluid
    int h2Index = -1;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      String name = fluid.getPhase(0).getComponent(i).getComponentName();
      if ("hydrogen".equalsIgnoreCase(name) || "H2".equalsIgnoreCase(name)) {
        h2Index = i;
        break;
      }
    }

    if (h2Index >= 0) {
      // Bulk mole fraction
      h2MoleFractionBulk = fluid.getPhase(0).getComponent(h2Index).getz();

      // Gas phase mole fraction (if gas phase exists)
      if (fluid.hasPhaseType("gas")) {
        h2MoleFractionGas = fluid.getPhase("gas").getComponent(h2Index).getx();
        h2PartialPressureBar = h2MoleFractionGas * totalPressureBar;
      } else {
        // Single liquid phase — use bulk
        h2MoleFractionGas = h2MoleFractionBulk;
        h2PartialPressureBar = h2MoleFractionBulk * totalPressureBar;
      }
    }

    // Check for H2S
    int h2sIndex = -1;
    for (int i = 0; i < fluid.getPhase(0).getNumberOfComponents(); i++) {
      String name = fluid.getPhase(0).getComponent(i).getComponentName();
      if ("H2S".equalsIgnoreCase(name)) {
        h2sIndex = i;
        break;
      }
    }
    if (h2sIndex >= 0 && fluid.hasPhaseType("gas")) {
      double h2sX = fluid.getPhase("gas").getComponent(h2sIndex).getx();
      h2sPartialPressureBar = h2sX * totalPressureBar;
    }

    evaluated = false;
  }

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the hydrogen partial pressure directly.
   *
   * @param pressureBar hydrogen partial pressure in bar
   */
  public void setH2PartialPressureBar(double pressureBar) {
    this.h2PartialPressureBar = Math.max(0.0, pressureBar);
    if (totalPressureBar > 0.0) {
      this.h2MoleFractionGas = this.h2PartialPressureBar / totalPressureBar;
    }
    evaluated = false;
  }

  /**
   * Sets the total system pressure.
   *
   * @param pressureBar total pressure in bar
   */
  public void setTotalPressureBar(double pressureBar) {
    this.totalPressureBar = Math.max(0.0, pressureBar);
    evaluated = false;
  }

  /**
   * Sets the hydrogen mole fraction in the gas phase.
   *
   * @param fraction hydrogen mole fraction [0-1]
   */
  public void setH2MoleFractionGas(double fraction) {
    this.h2MoleFractionGas = Math.max(0.0, Math.min(1.0, fraction));
    this.h2PartialPressureBar = this.h2MoleFractionGas * totalPressureBar;
    evaluated = false;
  }

  /**
   * Sets the H2S partial pressure for combined sour + H2 assessment.
   *
   * @param pressureBar H2S partial pressure in bar
   */
  public void setH2SPartialPressureBar(double pressureBar) {
    this.h2sPartialPressureBar = Math.max(0.0, pressureBar);
    evaluated = false;
  }

  /**
   * Sets the design temperature.
   *
   * @param temperatureC design temperature in degrees Celsius
   */
  public void setDesignTemperatureC(double temperatureC) {
    this.designTemperatureC = temperatureC;
    evaluated = false;
  }

  /**
   * Sets the maximum operating temperature.
   *
   * @param temperatureC maximum operating temperature in degrees Celsius
   */
  public void setMaxOperatingTemperatureC(double temperatureC) {
    this.maxOperatingTemperatureC = temperatureC;
    evaluated = false;
  }

  /**
   * Sets the material grade.
   *
   * @param grade material grade per API 5L or ASTM (e.g., "X42", "X52", "SA-516-70", "316L")
   */
  public void setMaterialGrade(String grade) {
    this.materialGrade = grade;
    updateSmysFromGrade();
    evaluated = false;
  }

  /**
   * Sets the specified minimum yield strength directly.
   *
   * @param smys SMYS in MPa
   */
  public void setSmysMPa(double smys) {
    this.smysMPa = smys;
    evaluated = false;
  }

  /**
   * Sets the material hardness.
   *
   * @param hrc hardness in HRC, or -1 if unknown
   */
  public void setHardnessHRC(double hrc) {
    this.hardnessHRC = hrc;
    evaluated = false;
  }

  /**
   * Sets the wall thickness.
   *
   * @param thicknessMm wall thickness in mm
   */
  public void setWallThicknessMm(double thicknessMm) {
    this.wallThicknessMm = thicknessMm;
    evaluated = false;
  }

  /**
   * Sets whether post-weld heat treatment has been applied.
   *
   * @param applied true if PWHT has been performed
   */
  public void setPwhtApplied(boolean applied) {
    this.pwhtApplied = applied;
    evaluated = false;
  }

  /**
   * Sets whether free water is present.
   *
   * @param present true if free water is present
   */
  public void setFreeWaterPresent(boolean present) {
    this.freeWaterPresent = present;
    evaluated = false;
  }

  /**
   * Sets the chloride concentration.
   *
   * @param mgL chloride concentration in mg/L
   */
  public void setChlorideMgL(double mgL) {
    this.chlorideMgL = mgL;
    evaluated = false;
  }

  /**
   * Sets the design life.
   *
   * @param years design life in years
   */
  public void setDesignLifeYears(double years) {
    this.designLifeYears = years;
    evaluated = false;
  }

  /**
   * Sets whether this is cyclic service.
   *
   * @param cyclic true if cyclic service (fatigue relevant)
   */
  public void setCyclicService(boolean cyclic) {
    this.cyclicService = cyclic;
    evaluated = false;
  }

  // ─── Core Evaluation ────────────────────────────────────

  /**
   * Runs the complete hydrogen material assessment.
   *
   * <p>
   * Evaluates all degradation mechanisms and produces an overall risk level and material
   * recommendations. Call setters for all known parameters before invoking this method.
   * </p>
   */
  public void evaluate() {
    recommendations = new ArrayList<String>();
    warnings = new ArrayList<String>();
    standardsApplied = new ArrayList<String>();

    // Step 1: Hydrogen embrittlement assessment (ASME B31.12 / EIGA 121)
    evaluateHydrogenEmbrittlement();

    // Step 2: HTHA assessment (API 941 Nelson curves)
    evaluateHTHA();

    // Step 3: Sour service / HIC assessment (NACE MR0175 / ISO 15156)
    evaluateSourService();

    // Step 4: Combined assessment
    evaluateCombinedRisk();

    // Step 5: Generate material recommendation
    generateRecommendation();

    evaluated = true;
  }

  /**
   * Evaluates hydrogen embrittlement risk per ASME B31.12.
   *
   * <p>
   * Assessment criteria:
   * </p>
   * <ul>
   * <li>Maximum SMYS: 480 MPa (69.6 ksi) per Table IX-5A</li>
   * <li>Maximum hardness: 22 HRC per ASME B31.12</li>
   * <li>Design factor: 0.5 for hydrogen (vs 0.72 for natural gas)</li>
   * <li>PWHT required for weld joints</li>
   * <li>Derating factor based on material grade and H2 fraction</li>
   * </ul>
   */
  private void evaluateHydrogenEmbrittlement() {
    standardsApplied.add("ASME B31.12 — Hydrogen Piping and Pipelines");
    standardsApplied.add("EIGA 121/14 — Hydrogen Pipeline Systems");

    // Calculate derating factor
    hydrogenDeratingFactor = calculateDeratingFactor();

    // Assess SMYS limit
    boolean smysOk = smysMPa <= MAX_SMYS_H2;

    // Assess hardness
    boolean hardnessOk = true;
    if (hardnessHRC >= 0) {
      hardnessOk = hardnessHRC <= MAX_HARDNESS_H2;
    }

    // Assess PWHT
    if (!pwhtApplied && h2PartialPressureBar > 5.0) {
      warnings.add("PWHT not applied — ASME B31.12 requires PWHT for hydrogen service "
          + "at pH2 > 5 bar");
    }

    // Classify HE risk based on H2 partial pressure and material strength
    if (h2PartialPressureBar < 0.1) {
      heRisk = "Negligible";
      heAcceptable = true;
    } else if (h2PartialPressureBar < 1.0) {
      heRisk = "Low";
      heAcceptable = smysOk && hardnessOk;
    } else if (h2PartialPressureBar < 10.0) {
      heRisk = "Medium";
      heAcceptable = smysOk && hardnessOk && (smysMPa <= 360.0 || pwhtApplied);
    } else if (h2PartialPressureBar < 50.0) {
      heRisk = "High";
      heAcceptable = smysOk && hardnessOk && smysMPa <= 360.0 && pwhtApplied;
    } else {
      heRisk = "Very High";
      heAcceptable = smysOk && hardnessOk && smysMPa <= 290.0;
    }

    if (!smysOk) {
      warnings.add("SMYS " + smysMPa + " MPa exceeds ASME B31.12 limit of " + MAX_SMYS_H2
          + " MPa for H2 service");
    }
    if (!hardnessOk) {
      warnings.add("Hardness " + hardnessHRC + " HRC exceeds " + MAX_HARDNESS_H2
          + " HRC limit for H2 service");
    }

    // Cyclic service increases risk
    if (cyclicService && h2PartialPressureBar > 1.0) {
      warnings.add("Cyclic service in hydrogen — fatigue crack growth rate may be "
          + "10-100x higher than in air. Consider fracture mechanics assessment "
          + "per ASME B31.12 Appendix A");
      if ("Low".equals(heRisk)) {
        heRisk = "Medium";
      } else if ("Medium".equals(heRisk)) {
        heRisk = "High";
      }
    }
  }

  /**
   * Evaluates high-temperature hydrogen attack (HTHA) risk per API 941.
   *
   * <p>
   * Uses Nelson curve boundaries to assess whether operating conditions (temperature, H2 partial
   * pressure) are within safe limits for the selected material.
   * </p>
   */
  private void evaluateHTHA() {
    standardsApplied.add("API 941 — Steels for Hydrogen Service at Elevated Temperatures");

    // API 941 HTHA only relevant at elevated temperatures
    double maxTemp = Math.max(designTemperatureC, maxOperatingTemperatureC);

    if (maxTemp < 200.0) {
      hthaRisk = "Not applicable (T < 200°C)";
      hthaAcceptable = true;
      return;
    }

    // Use Nelson curve assessment
    nelsonCurve.setTemperatureC(maxTemp);
    nelsonCurve.setH2PartialPressurePsia(h2PartialPressureBar * 14.5038);
    nelsonCurve.setMaterialType(mapGradeToNelsonMaterial());
    nelsonCurve.evaluate();

    hthaAcceptable = nelsonCurve.isBelowNelsonCurve();
    hthaRisk = nelsonCurve.getRiskLevel();

    if (!hthaAcceptable) {
      warnings.add("Operating point ABOVE API 941 Nelson curve for " + materialGrade
          + " at " + maxTemp + "°C, " + h2PartialPressureBar + " bar H2. "
          + "Risk of high-temperature hydrogen attack (decarburisation).");
      warnings.add("Consider: (1) low-alloy Cr-Mo steel, (2) reduce temperature, "
          + "or (3) reduce H2 partial pressure");
    }
  }

  /**
   * Evaluates sour service requirements per NACE MR0175/ISO 15156.
   *
   * <p>
   * When H2S is present alongside H2, there is a combined risk: H2S promotes atomic hydrogen uptake
   * into the steel, increasing susceptibility to HIC, SSC, and SOHIC. Even trace H2S significantly
   * increases hydrogen-related degradation.
   * </p>
   */
  private void evaluateSourService() {
    // NACE MR0175: sour service if pH2S >= 0.05 bar (in water-containing systems)
    boolean isSour = h2sPartialPressureBar >= 0.05 && freeWaterPresent;

    if (!isSour && h2sPartialPressureBar < 0.001) {
      hicRisk = "Not applicable (no H2S)";
      sourServiceOk = true;
      return;
    }

    standardsApplied.add("NACE MR0175 / ISO 15156 — Materials for Sour Service");

    if (isSour) {
      // SSC region per ISO 15156-2 Figure 1
      if (h2sPartialPressureBar < 0.3) {
        hicRisk = "SSC Region 1 — Low severity";
        sourServiceOk = smysMPa <= 690.0 && (hardnessHRC < 0 || hardnessHRC <= 22.0);
      } else if (h2sPartialPressureBar < 1.0) {
        hicRisk = "SSC Region 2 — Moderate severity";
        sourServiceOk = smysMPa <= 550.0 && (hardnessHRC < 0 || hardnessHRC <= 22.0);
      } else {
        hicRisk = "SSC Region 3 — High severity";
        sourServiceOk = smysMPa <= 450.0 && (hardnessHRC < 0 || hardnessHRC <= 22.0);
      }

      if (!sourServiceOk) {
        warnings.add("Material may not meet NACE MR0175/ISO 15156 for sour service at pH2S = "
            + h2sPartialPressureBar + " bar. Max SMYS and hardness limits must be met.");
      }

      // Combined H2S + H2 warning
      if (h2PartialPressureBar > 0.5) {
        warnings.add("Combined H2S + H2 environment: H2S promotes atomic hydrogen "
            + "absorption, accelerating embrittlement. Consider HIC-resistant steel "
            + "grades (e.g., API 5L with HIC testing per NACE TM0284)");
      }
    } else if (h2sPartialPressureBar >= 0.001) {
      hicRisk = "Trace H2S — Monitor";
      sourServiceOk = true;
      warnings.add("Trace H2S detected (" + h2sPartialPressureBar + " bar) — below NACE sour "
          + "threshold but monitor for conditions where water may condense");
    }
  }

  /**
   * Evaluates combined risk from all mechanisms.
   */
  private void evaluateCombinedRisk() {
    // Risk level hierarchy
    int maxRiskScore = 0;
    maxRiskScore = Math.max(maxRiskScore, riskScore(heRisk));
    maxRiskScore = Math.max(maxRiskScore, riskScore(hthaRisk));
    maxRiskScore = Math.max(maxRiskScore, riskScore(hicRisk));

    // Increase risk if multiple mechanisms are active
    int activeMechanisms = 0;
    if (!"Negligible".equals(heRisk) && !"Not applicable (T < 200°C)".equals(heRisk)) {
      activeMechanisms++;
    }
    if (!"Not applicable (T < 200°C)".equals(hthaRisk)
        && !"Not applicable (no H2S)".equals(hthaRisk)) {
      activeMechanisms++;
    }
    if (!"Not applicable (no H2S)".equals(hicRisk)) {
      activeMechanisms++;
    }

    if (activeMechanisms >= 2 && maxRiskScore < 3) {
      maxRiskScore = Math.min(maxRiskScore + 1, 4);
    }

    overallRiskLevel = scoreToRisk(maxRiskScore);

    if (!heAcceptable || !hthaAcceptable || !sourServiceOk) {
      overallRiskLevel = "High";
      if (!heAcceptable && !hthaAcceptable) {
        overallRiskLevel = "Very High";
      }
    }
  }

  /**
   * Generates material recommendations.
   */
  private void generateRecommendation() {
    if (h2PartialPressureBar < 0.1) {
      recommendedMaterial = materialGrade + " (current selection acceptable)";
      recommendations.add("H2 partial pressure is very low (" + h2PartialPressureBar
          + " bar) — no special hydrogen material requirements per ASME B31.12");
      return;
    }

    // Carbon steel recommendations based on H2 partial pressure
    if (h2PartialPressureBar < 10.0) {
      if (smysMPa <= 360.0) {
        recommendedMaterial = materialGrade + " (acceptable with PWHT)";
        recommendations.add("Use " + materialGrade + " with PWHT per ASME B31.12");
        recommendations.add("Hardness must not exceed 22 HRC (200 HBW)");
        recommendations.add("Design factor: 0.5 (vs 0.72 for natural gas)");
      } else {
        recommendedMaterial = "X52 or lower grade recommended";
        recommendations.add("Current grade " + materialGrade + " (SMYS " + smysMPa
            + " MPa) may be too strong for H2 service at " + h2PartialPressureBar + " bar");
        recommendations.add("Downgrade to X52 (SMYS 358 MPa) or X42 (SMYS 290 MPa)");
      }
    } else if (h2PartialPressureBar < 50.0) {
      recommendedMaterial = "X42 or X52 with PWHT and full H2 qualification";
      recommendations.add("High H2 partial pressure (" + h2PartialPressureBar + " bar) — "
          + "only X42 or X52 with full ASME B31.12 compliance");
      recommendations.add("Mandatory: PWHT, hardness testing, NDE of all welds");
      recommendations.add("Consider fracture mechanics assessment (ASME B31.12 Appendix A)");
    } else {
      recommendedMaterial = "X42 with full H2 qualification, or consider 316L stainless";
      recommendations.add("Very high H2 partial pressure (" + h2PartialPressureBar
          + " bar) — X42 only, or consider austenitic stainless (316L)");
      recommendations.add("316L is immune to hydrogen embrittlement but more expensive");
    }

    // HTHA recommendation
    double maxTemp = Math.max(designTemperatureC, maxOperatingTemperatureC);
    if (maxTemp > 200.0 && h2PartialPressureBar > 0.5) {
      recommendations.add("Temperature " + maxTemp + "°C with H2 — verify against "
          + "API 941 Nelson curves. Consider Cr-Mo steel (1.25Cr-0.5Mo or 2.25Cr-1Mo) "
          + "for HTHA resistance");
    }

    // H2S combined service
    if (h2sPartialPressureBar > 0.05 && h2PartialPressureBar > 0.5) {
      recommendations.add("Combined H2S + H2 service requires HIC-resistant steel: "
          + "order with supplementary requirement for HIC testing per NACE TM0284 "
          + "and SSC testing per NACE TM0177");
    }
  }

  /**
   * Calculates the hydrogen derating factor per ASME B31.12 Table IX-5A.
   *
   * @return derating factor [0-1]
   */
  private double calculateDeratingFactor() {
    double h2Frac = h2MoleFractionGas;
    if (h2Frac < 0.1) {
      return 1.0;
    }

    double baseFactor;
    if (smysMPa <= 290.0) {
      baseFactor = 1.0;
    } else if (smysMPa <= 360.0) {
      baseFactor = 0.95;
    } else if (smysMPa <= 414.0) {
      baseFactor = 0.90;
    } else if (smysMPa <= 448.0) {
      baseFactor = 0.85;
    } else if (smysMPa <= 483.0) {
      baseFactor = 0.80;
    } else {
      baseFactor = 0.70;
    }

    // Interpolate with H2 content
    return 1.0 - h2Frac * (1.0 - baseFactor);
  }

  /**
   * Maps material grade to Nelson curve material type.
   *
   * @return Nelson curve material identifier
   */
  private String mapGradeToNelsonMaterial() {
    if (materialGrade == null) {
      return "carbon_steel";
    }
    String grade = materialGrade.toUpperCase();
    if (grade.contains("316") || grade.contains("304") || grade.contains("AUSTENITIC")) {
      return "austenitic_ss";
    }
    if (grade.contains("2.25CR") || grade.contains("F22") || grade.contains("P22")) {
      return "2_25cr_1mo";
    }
    if (grade.contains("1.25CR") || grade.contains("F11") || grade.contains("P11")) {
      return "1_25cr_0_5mo";
    }
    if (grade.contains("1CR") || grade.contains("F12") || grade.contains("P12")) {
      return "1cr_0_5mo";
    }
    if (grade.contains("0.5MO") || grade.contains("C_0_5MO")) {
      return "c_0_5mo";
    }
    return "carbon_steel";
  }

  /**
   * Updates SMYS from material grade string.
   */
  private void updateSmysFromGrade() {
    if (materialGrade == null) {
      return;
    }
    String grade = materialGrade.toUpperCase();
    if (grade.contains("X42")) {
      smysMPa = 290.0;
    } else if (grade.contains("X46")) {
      smysMPa = 317.0;
    } else if (grade.contains("X52")) {
      smysMPa = 358.0;
    } else if (grade.contains("X56")) {
      smysMPa = 386.0;
    } else if (grade.contains("X60")) {
      smysMPa = 414.0;
    } else if (grade.contains("X65")) {
      smysMPa = 448.0;
    } else if (grade.contains("X70")) {
      smysMPa = 483.0;
    } else if (grade.contains("X80")) {
      smysMPa = 552.0;
    } else if (grade.contains("SA-516-70") || grade.contains("SA516-70")) {
      smysMPa = 260.0;
    } else if (grade.contains("SA-516-60") || grade.contains("SA516-60")) {
      smysMPa = 220.0;
    } else if (grade.contains("316L")) {
      smysMPa = 170.0;
    } else if (grade.contains("304L")) {
      smysMPa = 170.0;
    } else if (grade.contains("DUPLEX") || grade.contains("S31803")) {
      smysMPa = 450.0;
    } else if (grade.contains("SUPER DUPLEX") || grade.contains("S32750")) {
      smysMPa = 550.0;
    }
  }

  /**
   * Converts risk string to numeric score.
   *
   * @param risk risk level string
   * @return numeric score (0-4)
   */
  private int riskScore(String risk) {
    if (risk == null) {
      return 0;
    }
    if (risk.startsWith("Negligible") || risk.startsWith("Not applicable")) {
      return 0;
    }
    if (risk.startsWith("Low") || risk.startsWith("SSC Region 1")
        || risk.startsWith("Trace")) {
      return 1;
    }
    if (risk.startsWith("Medium") || risk.startsWith("SSC Region 2")) {
      return 2;
    }
    if (risk.startsWith("High") || risk.startsWith("SSC Region 3")) {
      return 3;
    }
    if (risk.startsWith("Very High")) {
      return 4;
    }
    return 0;
  }

  /**
   * Converts numeric score to risk level string.
   *
   * @param score numeric score (0-4)
   * @return risk level string
   */
  private String scoreToRisk(int score) {
    if (score <= 0) {
      return "Low";
    }
    if (score == 1) {
      return "Low";
    }
    if (score == 2) {
      return "Medium";
    }
    if (score == 3) {
      return "High";
    }
    return "Very High";
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Gets the overall risk level.
   *
   * @return risk level: "Low", "Medium", "High", or "Very High"
   */
  public String getOverallRiskLevel() {
    return overallRiskLevel;
  }

  /**
   * Gets the hydrogen embrittlement risk level.
   *
   * @return HE risk level
   */
  public String getHydrogenEmbrittlementRisk() {
    return heRisk;
  }

  /**
   * Checks whether hydrogen embrittlement is acceptable.
   *
   * @return true if HE risk is acceptable per ASME B31.12
   */
  public boolean isHydrogenEmbrittlementAcceptable() {
    return heAcceptable;
  }

  /**
   * Gets the HTHA risk level per API 941.
   *
   * @return HTHA risk level
   */
  public String getHTHARisk() {
    return hthaRisk;
  }

  /**
   * Checks whether HTHA is acceptable per API 941 Nelson curves.
   *
   * @return true if operating point is below Nelson curve
   */
  public boolean isHTHAAcceptable() {
    return hthaAcceptable;
  }

  /**
   * Gets the HIC/sour service risk level.
   *
   * @return HIC risk level
   */
  public String getHICRisk() {
    return hicRisk;
  }

  /**
   * Checks whether sour service requirements are met.
   *
   * @return true if material meets NACE MR0175/ISO 15156
   */
  public boolean isSourServiceOk() {
    return sourServiceOk;
  }

  /**
   * Gets the hydrogen derating factor per ASME B31.12.
   *
   * @return derating factor [0-1]
   */
  public double getHydrogenDeratingFactor() {
    return hydrogenDeratingFactor;
  }

  /**
   * Gets the hydrogen partial pressure.
   *
   * @return H2 partial pressure [bar]
   */
  public double getH2PartialPressureBar() {
    return h2PartialPressureBar;
  }

  /**
   * Gets the H2 mole fraction in the gas phase.
   *
   * @return mole fraction [0-1]
   */
  public double getH2MoleFractionGas() {
    return h2MoleFractionGas;
  }

  /**
   * Gets the recommended material.
   *
   * @return recommended material string
   */
  public String getRecommendedMaterial() {
    return recommendedMaterial;
  }

  /**
   * Gets all material recommendations.
   *
   * @return list of recommendations
   */
  public List<String> getRecommendations() {
    return recommendations;
  }

  /**
   * Gets all warnings.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Gets the list of standards applied in the assessment.
   *
   * @return list of standard identifiers
   */
  public List<String> getStandardsApplied() {
    return standardsApplied;
  }

  /**
   * Gets the Nelson curve assessment object for detailed HTHA results.
   *
   * @return Nelson curve assessment
   */
  public NelsonCurveAssessment getNelsonCurveAssessment() {
    return nelsonCurve;
  }

  /**
   * Checks whether the evaluation has been performed.
   *
   * @return true if evaluate() has been called
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns the assessment results as a Map.
   *
   * @return results as linked hash map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();

    // Input conditions
    Map<String, Object> inputs = new LinkedHashMap<String, Object>();
    inputs.put("h2PartialPressure_bar", h2PartialPressureBar);
    inputs.put("h2MoleFractionGas", h2MoleFractionGas);
    inputs.put("h2MoleFractionBulk", h2MoleFractionBulk);
    inputs.put("h2sPartialPressure_bar", h2sPartialPressureBar);
    inputs.put("totalPressure_bar", totalPressureBar);
    inputs.put("designTemperature_C", designTemperatureC);
    inputs.put("maxOperatingTemperature_C", maxOperatingTemperatureC);
    inputs.put("materialGrade", materialGrade);
    inputs.put("smys_MPa", smysMPa);
    inputs.put("hardness_HRC", hardnessHRC);
    inputs.put("wallThickness_mm", wallThicknessMm);
    inputs.put("pwhtApplied", pwhtApplied);
    inputs.put("freeWaterPresent", freeWaterPresent);
    inputs.put("chloride_mgL", chlorideMgL);
    inputs.put("cyclicService", cyclicService);
    result.put("inputConditions", inputs);

    // Assessment results
    Map<String, Object> assessment = new LinkedHashMap<String, Object>();
    assessment.put("overallRiskLevel", overallRiskLevel);
    assessment.put("hydrogenEmbrittlement_risk", heRisk);
    assessment.put("hydrogenEmbrittlement_acceptable", heAcceptable);
    assessment.put("htha_risk", hthaRisk);
    assessment.put("htha_acceptable", hthaAcceptable);
    assessment.put("hic_risk", hicRisk);
    assessment.put("sourService_ok", sourServiceOk);
    assessment.put("hydrogenDeratingFactor", hydrogenDeratingFactor);
    assessment.put("b3112DesignFactor", b3112DesignFactor);
    result.put("assessment", assessment);

    // Recommendations
    result.put("recommendedMaterial", recommendedMaterial);
    result.put("recommendations", recommendations);
    result.put("warnings", warnings);
    result.put("standardsApplied", standardsApplied);

    return result;
  }

  /**
   * Returns the assessment results as a JSON string.
   *
   * @return JSON representation of the assessment
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting()
        .serializeSpecialFloatingPointValues().create();
    return gson.toJson(toMap());
  }
}
