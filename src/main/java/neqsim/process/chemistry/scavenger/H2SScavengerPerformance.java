package neqsim.process.chemistry.scavenger;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Performance model for H2S scavengers (triazine and iron-based).
 *
 * <p>
 * Predicts:
 * </p>
 * <ul>
 * <li>Stoichiometric scavenger demand for the supplied H2S load.</li>
 * <li>Theoretical breakthrough time given a scavenger inventory.</li>
 * <li>Spent product chemistry warnings (dithiazine deposition for triazine, iron sulphide for
 * iron-based scavengers).</li>
 * <li>Temperature stability flags.</li>
 * </ul>
 *
 * <p>
 * Stoichiometry (mol scavenger active / mol H2S):
 * </p>
 * <ul>
 * <li>MEA-triazine (MEA-tris): theoretical 2/3, practical 1.0 (overdose for kinetics).</li>
 * <li>MMA-triazine (Sulfa-Treat): similar to MEA-triazine.</li>
 * <li>Iron-based (Fe-chelate): 1 mol Fe per mol H2S → forms FeS.</li>
 * </ul>
 *
 * <p>
 * Mass-based scavenger capacity is reported in kg H2S removed per kg active scavenger. Vendor data
 * overrides theoretical values; the model is for screening / sizing.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class H2SScavengerPerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Scavenger chemistry family.
   */
  public enum ScavengerChemistry {
    /** Monoethanolamine triazine. */
    MEA_TRIAZINE,
    /** Methylamine triazine. */
    MMA_TRIAZINE,
    /** Iron chelate (water soluble). */
    IRON_CHELATE,
    /** Solid iron sponge / iron oxide bed. */
    IRON_SPONGE,
    /** Aldehyde-based (glyoxal). */
    ALDEHYDE;
  }

  // Molar masses
  private static final double M_H2S = 34.08;
  private static final double M_TRIAZINE_MEA = 189.26; // hexahydro-1,3,5-tris(2-hydroxyethyl)-s-triazine
  private static final double M_TRIAZINE_MMA = 129.21;
  private static final double M_FE = 55.85;
  private static final double M_GLYOXAL = 58.04;

  // ─── Inputs ─────────────────────────────────────────────

  private ScavengerChemistry chemistry = ScavengerChemistry.MEA_TRIAZINE;
  private double activeWtPct = 40.0;
  private double scavengerMassKg = 0.0;
  private double gasFlowMSm3D = 1.0;
  private double h2sInletPpm = 100.0;
  private double h2sTargetPpm = 4.0;
  private double temperatureC = 50.0;
  private double pressureBara = 70.0;

  // ─── Outputs ────────────────────────────────────────────

  private double h2sToRemoveKgPerDay = 0.0;
  private double scavengerDemandKgPerDay = 0.0;
  private double capacityKgH2SPerKg = 0.0;
  private double breakthroughDays = 0.0;
  private final Map<String, String> warnings = new LinkedHashMap<String, String>();
  private boolean evaluated = false;

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the scavenger chemistry.
   *
   * @param chemistry scavenger family
   */
  public void setChemistry(ScavengerChemistry chemistry) {
    this.chemistry = chemistry;
  }

  /**
   * Sets the active ingredient concentration of the neat product.
   *
   * @param wtPct wt% active
   */
  public void setActiveWtPct(double wtPct) {
    this.activeWtPct = wtPct;
  }

  /**
   * Sets the scavenger inventory (for breakthrough calculation).
   *
   * @param kg mass in kg
   */
  public void setScavengerInventoryKg(double kg) {
    this.scavengerMassKg = kg;
  }

  /**
   * Sets the gas flow rate.
   *
   * @param mSm3D flow in million standard m3 per day
   */
  public void setGasFlowMSm3PerDay(double mSm3D) {
    this.gasFlowMSm3D = mSm3D;
  }

  /**
   * Sets the inlet H2S concentration.
   *
   * @param ppm H2S in ppm (mol/mol)
   */
  public void setH2SInletPpm(double ppm) {
    this.h2sInletPpm = ppm;
  }

  /**
   * Sets the H2S sales gas target.
   *
   * @param ppm target H2S in ppm
   */
  public void setH2STargetPpm(double ppm) {
    this.h2sTargetPpm = ppm;
  }

  /**
   * Sets the operating temperature.
   *
   * @param tC temperature in Celsius
   */
  public void setTemperatureCelsius(double tC) {
    this.temperatureC = tC;
  }

  /**
   * Sets the operating pressure.
   *
   * @param bara pressure in bara
   */
  public void setPressureBara(double bara) {
    this.pressureBara = bara;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Computes scavenger demand, capacity and breakthrough.
   */
  public void evaluate() {
    warnings.clear();

    // Convert gas flow to molar flow at standard conditions: 1 Sm3 ≈ 41.6 mol (ISO 13443)
    double molGasPerDay = gasFlowMSm3D * 1.0e6 * 41.6;
    double h2sRemovedPpm = Math.max(0.0, h2sInletPpm - h2sTargetPpm);
    double molH2SPerDay = molGasPerDay * h2sRemovedPpm * 1.0e-6;
    h2sToRemoveKgPerDay = molH2SPerDay * M_H2S / 1000.0;

    // Stoichiometric scavenger active mass
    double activeMolPerKgH2S = stoichiometryMolActivePerMolH2S(chemistry);
    double activeMolarMass = activeMolarMass(chemistry);
    double activeMassKgPerKgH2S = activeMolPerKgH2S * activeMolarMass / M_H2S;
    capacityKgH2SPerKg = 1.0 / activeMassKgPerKgH2S;

    double activeKgPerDay = h2sToRemoveKgPerDay * activeMassKgPerKgH2S;
    scavengerDemandKgPerDay = activeKgPerDay * 100.0 / Math.max(0.1, activeWtPct);

    if (scavengerMassKg > 0.0 && scavengerDemandKgPerDay > 0.0) {
      breakthroughDays = scavengerMassKg / scavengerDemandKgPerDay;
    } else {
      breakthroughDays = 0.0;
    }

    // Warnings
    if ((chemistry == ScavengerChemistry.MEA_TRIAZINE
        || chemistry == ScavengerChemistry.MMA_TRIAZINE) && temperatureC > 80.0) {
      warnings.put("triazine_decomposition",
          "Triazines decompose above 80 C; reduces capacity and releases amine — relocate downstream of cooling");
    }
    if (chemistry == ScavengerChemistry.MEA_TRIAZINE && h2sInletPpm > 1000.0) {
      warnings.put("dithiazine_deposition",
          "Above 1000 ppm H2S, spent triazine forms dithiazine (solid) — risk of fouling");
    }
    if (chemistry == ScavengerChemistry.IRON_CHELATE
        || chemistry == ScavengerChemistry.IRON_SPONGE) {
      warnings.put("iron_sulphide",
          "Spent iron scavenger contains FeS (pyrophoric on exposure to air); special handling required");
    }
    if (chemistry == ScavengerChemistry.IRON_SPONGE && h2sInletPpm < 50.0) {
      warnings.put("oversized_solid_bed",
          "Iron sponge is only economic above ~50 ppm H2S; consider liquid scavenger for trace loads");
    }
    if (h2sToRemoveKgPerDay > 100.0 && (chemistry == ScavengerChemistry.MEA_TRIAZINE
        || chemistry == ScavengerChemistry.MMA_TRIAZINE)) {
      warnings.put("high_load_economics",
          "Above ~100 kg H2S/day, liquid scavenger OPEX is high — evaluate amine treating or Sulfinol");
    }
    evaluated = true;
  }

  /**
   * Returns mol active scavenger per mol H2S (practical, including kinetic overdose factor).
   *
   * @param chem chemistry
   * @return mol active per mol H2S
   */
  private static double stoichiometryMolActivePerMolH2S(ScavengerChemistry chem) {
    switch (chem) {
      case MEA_TRIAZINE:
        return 1.0; // theoretical 2/3, practical 1.0
      case MMA_TRIAZINE:
        return 1.0;
      case IRON_CHELATE:
        return 1.0;
      case IRON_SPONGE:
        return 1.1; // bed efficiency penalty
      case ALDEHYDE:
        return 1.5;
      default:
        return 1.0;
    }
  }

  /**
   * Returns the molar mass of the active scavenger species.
   *
   * @param chem chemistry
   * @return g/mol
   */
  private static double activeMolarMass(ScavengerChemistry chem) {
    switch (chem) {
      case MEA_TRIAZINE:
        return M_TRIAZINE_MEA;
      case MMA_TRIAZINE:
        return M_TRIAZINE_MMA;
      case IRON_CHELATE:
      case IRON_SPONGE:
        return M_FE;
      case ALDEHYDE:
        return M_GLYOXAL;
      default:
        return M_TRIAZINE_MEA;
    }
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the H2S mass to be removed.
   *
   * @return kg/day
   */
  public double getH2SToRemoveKgPerDay() {
    return h2sToRemoveKgPerDay;
  }

  /**
   * Returns the scavenger demand (neat product).
   *
   * @return kg/day
   */
  public double getScavengerDemandKgPerDay() {
    return scavengerDemandKgPerDay;
  }

  /**
   * Returns the theoretical capacity.
   *
   * @return kg H2S removed per kg active scavenger
   */
  public double getCapacityKgH2SPerKgActive() {
    return capacityKgH2SPerKg;
  }

  /**
   * Returns the predicted breakthrough time.
   *
   * @return days (0 if no inventory specified)
   */
  public double getBreakthroughDays() {
    return breakthroughDays;
  }

  /**
   * Returns warnings.
   *
   * @return ordered map of warning code → message
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
   * Returns a structured map for JSON output.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    Map<String, Object> in = new LinkedHashMap<String, Object>();
    in.put("chemistry", chemistry.name());
    in.put("activeWtPct", activeWtPct);
    in.put("scavengerInventoryKg", scavengerMassKg);
    in.put("gasFlowMSm3PerDay", gasFlowMSm3D);
    in.put("h2sInletPpm", h2sInletPpm);
    in.put("h2sTargetPpm", h2sTargetPpm);
    in.put("temperatureC", temperatureC);
    in.put("pressureBara", pressureBara);
    map.put("inputs", in);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("h2sToRemoveKgPerDay", h2sToRemoveKgPerDay);
    out.put("scavengerDemandKgPerDay", scavengerDemandKgPerDay);
    out.put("capacityKgH2SPerKgActive", capacityKgH2SPerKg);
    out.put("breakthroughDays", breakthroughDays);
    map.put("outputs", out);
    map.put("warnings", warnings);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns the industry standards applied by this H2S-scavenger model.
   *
   * @return list of standards (each as an ordered map)
   */
  public java.util.List<java.util.Map<String, Object>> getStandardsApplied() {
    return neqsim.process.chemistry.util.StandardsRegistry.toMapList(
        neqsim.process.chemistry.util.StandardsRegistry.GPSA_DB,
        neqsim.process.chemistry.util.StandardsRegistry.ISO_13443,
        neqsim.process.chemistry.util.StandardsRegistry.NACE_MR0175);
  }
}
