package neqsim.process.chemistry.acid;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple stoichiometric simulator for acid stimulation / cleaning treatments.
 *
 * <p>
 * Models the reaction of mineral and organic acids with carbonate/sulphate scale and the resulting
 * spent acid composition. Used for screening:
 * </p>
 * <ul>
 * <li>How much scale a given acid volume can dissolve.</li>
 * <li>The CO2 produced (carbonate dissolution) and gas evolution risk.</li>
 * <li>The pH and Ca content of the spent acid (returns / disposal).</li>
 * <li>Whether a corrosion inhibitor is required (HCl &gt; 5 wt% on carbon steel above 60 C).</li>
 * </ul>
 *
 * <p>
 * Reactions implemented:
 * </p>
 *
 * <pre>
 * {@code
 * 2 HCl + CaCO3   -> CaCl2 + H2O + CO2    (calcite dissolution)
 * 2 HCl + MgCO3   -> MgCl2 + H2O + CO2    (dolomite dissolution, partial)
 * 2 HCl + Fe(OH)3 -> FeCl3 + 3 H2O        (iron oxide / rust)
 * HF + CaCO3      -> CaF2 + H2O + CO2     (mud acid carbonate side)
 * HOAc + CaCO3    -> Ca(OAc)2 + H2O + CO2 (organic acid)
 * }
 * </pre>
 *
 * <p>
 * Limitations: kinetics are not modelled. The simulator assumes complete reaction subject to
 * stoichiometric availability of reactants. Use vendor lab data for live-acid kinetic studies.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class AcidTreatmentSimulator implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Acid type.
   */
  public enum AcidType {
    /** Hydrochloric acid (HCl). */
    HCL,
    /** Hydrofluoric acid (HF) — usually mud acid blend. */
    HF,
    /** Acetic acid (HOAc). */
    ACETIC,
    /** Formic acid (HCOOH). */
    FORMIC;
  }

  // ─── Inputs ─────────────────────────────────────────────

  private AcidType acidType = AcidType.HCL;
  private double acidStrengthWtPct = 15.0;
  private double acidVolumeM3 = 10.0;
  private double acidDensityKgM3 = 1075.0; // 15% HCl
  private double scaleMassKgCaCO3 = 100.0;
  private double scaleMassKgMgCO3 = 0.0;
  private double scaleMassKgFeOH3 = 0.0;
  private double temperatureC = 60.0;
  private String tubularMaterial = "carbon_steel";
  private boolean inhibitorPresent = false;

  // ─── Outputs ────────────────────────────────────────────

  private double dissolvableScaleKg = 0.0;
  private double scaleDissolvedKg = 0.0;
  private double dissolutionFractionPct = 0.0;
  private double co2GeneratedKg = 0.0;
  private double spentAcidPH = 0.0;
  private double spentCalciumMgL = 0.0;
  private double residualAcidWtPct = 0.0;
  private final Map<String, String> warnings = new LinkedHashMap<String, String>();
  private boolean evaluated = false;

  // Molar masses
  private static final double M_HCL = 36.46;
  private static final double M_HF = 20.01;
  private static final double M_HOAC = 60.05;
  private static final double M_HCOOH = 46.03;
  private static final double M_CACO3 = 100.09;
  private static final double M_MGCO3 = 84.31;
  private static final double M_FEOH3 = 106.87;
  private static final double M_CO2 = 44.01;
  private static final double M_CA = 40.08;

  // ─── Setters ────────────────────────────────────────────

  /**
   * Sets the acid type.
   *
   * @param type acid type
   */
  public void setAcidType(AcidType type) {
    this.acidType = type;
  }

  /**
   * Sets the acid strength.
   *
   * @param wtPct strength in wt%
   */
  public void setAcidStrengthWtPct(double wtPct) {
    this.acidStrengthWtPct = wtPct;
  }

  /**
   * Sets the acid volume.
   *
   * @param m3 volume in m3
   */
  public void setAcidVolumeM3(double m3) {
    this.acidVolumeM3 = m3;
  }

  /**
   * Sets the acid solution density.
   *
   * @param kgM3 density in kg/m3
   */
  public void setAcidDensityKgM3(double kgM3) {
    this.acidDensityKgM3 = kgM3;
  }

  /**
   * Sets the calcium carbonate scale to dissolve.
   *
   * @param kg mass in kg
   */
  public void setScaleCaCO3Kg(double kg) {
    this.scaleMassKgCaCO3 = kg;
  }

  /**
   * Sets the magnesium carbonate scale.
   *
   * @param kg mass in kg
   */
  public void setScaleMgCO3Kg(double kg) {
    this.scaleMassKgMgCO3 = kg;
  }

  /**
   * Sets the iron oxide scale.
   *
   * @param kg mass in kg
   */
  public void setScaleFeOH3Kg(double kg) {
    this.scaleMassKgFeOH3 = kg;
  }

  /**
   * Sets the temperature.
   *
   * @param tC temperature in Celsius
   */
  public void setTemperatureCelsius(double tC) {
    this.temperatureC = tC;
  }

  /**
   * Sets the tubular material identifier.
   *
   * @param material material identifier
   */
  public void setTubularMaterial(String material) {
    this.tubularMaterial = material;
  }

  /**
   * Sets whether an acid corrosion inhibitor is co-injected.
   *
   * @param present true if CI is present
   */
  public void setInhibitorPresent(boolean present) {
    this.inhibitorPresent = present;
  }

  // ─── Evaluation ─────────────────────────────────────────

  /**
   * Computes scale dissolution, CO2 release, and spent acid composition.
   */
  public void evaluate() {
    warnings.clear();
    double acidMassKg = acidVolumeM3 * acidDensityKgM3;
    double acidActiveKg = acidMassKg * acidStrengthWtPct / 100.0;
    double molAcid = acidActiveKg * 1000.0 / acidMolarMass();

    // Reaction stoichiometry: assumes diprotic for HCl/HOAc/HF acting on carbonates, monoprotic
    // per H+ basis. We treat HCl as monoprotic; CaCO3 needs 2 H+ to release Ca2+.
    double molH = (acidType == AcidType.HCL || acidType == AcidType.HF) ? molAcid : molAcid;

    double molCaCO3 = scaleMassKgCaCO3 * 1000.0 / M_CACO3;
    double molMgCO3 = scaleMassKgMgCO3 * 1000.0 / M_MGCO3;
    double molFeOH3 = scaleMassKgFeOH3 * 1000.0 / M_FEOH3;

    // Each mole of CaCO3 needs 2 mol H+
    double molAvail = molH;
    double molCaDissolved = Math.min(molCaCO3, molAvail / 2.0);
    molAvail -= 2.0 * molCaDissolved;
    double molMgDissolved = Math.min(molMgCO3, molAvail / 2.0);
    molAvail -= 2.0 * molMgDissolved;
    // Fe(OH)3 needs 3 mol H+
    double molFeDissolved = Math.min(molFeOH3, molAvail / 3.0);
    molAvail -= 3.0 * molFeDissolved;

    scaleDissolvedKg = molCaDissolved * M_CACO3 / 1000.0 + molMgDissolved * M_MGCO3 / 1000.0
        + molFeDissolved * M_FEOH3 / 1000.0;

    double scaleTotal = scaleMassKgCaCO3 + scaleMassKgMgCO3 + scaleMassKgFeOH3;
    dissolvableScaleKg = scaleTotal;
    dissolutionFractionPct = scaleTotal > 0.0 ? scaleDissolvedKg / scaleTotal * 100.0 : 0.0;

    // CO2 from carbonate dissolution: 1 mol CO2 per mol carbonate dissolved
    co2GeneratedKg = (molCaDissolved + molMgDissolved) * M_CO2 / 1000.0;

    // Spent acid: residual H+, Ca2+, pH
    double waterMassKg = acidMassKg - acidActiveKg + scaleDissolvedKg; // roughly
    double waterVolumeL = waterMassKg / 1.0; // assume water-like
    double residualMolH = Math.max(0.0, molAvail);
    double residualHPerL = residualMolH / Math.max(1.0, waterVolumeL);
    double caMolL = (molCaDissolved + molMgDissolved) / Math.max(1.0, waterVolumeL);

    spentCalciumMgL = caMolL * M_CA * 1000.0;
    residualAcidWtPct =
        residualMolH * acidMolarMass() / 1000.0 / Math.max(1e-6, acidMassKg) * 100.0;

    if (residualHPerL > 1e-6) {
      spentAcidPH = -Math.log10(residualHPerL);
    } else {
      // weak acid or fully spent — pH governed by Ca/CO2 buffer; ~5-6 typical
      spentAcidPH = 5.5;
    }
    if (acidType == AcidType.ACETIC || acidType == AcidType.FORMIC) {
      // weak acids equilibrate higher
      spentAcidPH = Math.max(spentAcidPH, 3.5);
    }

    // Warnings
    if (acidType == AcidType.HCL && acidStrengthWtPct > 5.0 && tubularMaterial != null
        && tubularMaterial.toLowerCase().contains("carbon") && temperatureC > 60.0
        && !inhibitorPresent) {
      warnings.put("severe_corrosion",
          "HCl above 5 wt% on carbon steel above 60 C without inhibitor: severe corrosion expected");
    }
    if (acidType == AcidType.HF) {
      warnings.put("hf_handling",
          "HF requires special handling and CRA tubulars; iron fluoride scale may form on flowback");
    }
    if (co2GeneratedKg > 50.0) {
      warnings.put("co2_evolution", "Significant CO2 release (" + Math.round(co2GeneratedKg)
          + " kg); ensure venting/return capacity");
    }
    if (residualAcidWtPct > 1.0) {
      warnings.put("excess_acid",
          "Spent acid still contains " + String.format("%.1f", residualAcidWtPct)
              + " wt% live acid; neutralise before disposal");
    }
    if (acidType == AcidType.HCL && temperatureC > 120.0) {
      warnings.put("acid_temperature",
          "HCl above 120 C: even inhibited acid corrodes carbon steel rapidly; use organic acid");
    }
    evaluated = true;
  }

  /**
   * Returns the molar mass of the active acid.
   *
   * @return molar mass in g/mol
   */
  private double acidMolarMass() {
    switch (acidType) {
      case HCL:
        return M_HCL;
      case HF:
        return M_HF;
      case ACETIC:
        return M_HOAC;
      case FORMIC:
        return M_HCOOH;
      default:
        return M_HCL;
    }
  }

  // ─── Getters ────────────────────────────────────────────

  /**
   * Returns the mass of scale that can be dissolved by the supplied acid (sum of scales).
   *
   * @return dissolvable scale in kg
   */
  public double getDissolvableScaleKg() {
    return dissolvableScaleKg;
  }

  /**
   * Returns the mass of scale actually dissolved (subject to acid availability).
   *
   * @return dissolved scale in kg
   */
  public double getScaleDissolvedKg() {
    return scaleDissolvedKg;
  }

  /**
   * Returns dissolution fraction.
   *
   * @return dissolved fraction in percent
   */
  public double getDissolutionFractionPct() {
    return dissolutionFractionPct;
  }

  /**
   * Returns the CO2 released from carbonate dissolution.
   *
   * @return CO2 in kg
   */
  public double getCO2GeneratedKg() {
    return co2GeneratedKg;
  }

  /**
   * Returns the pH of the spent acid.
   *
   * @return pH
   */
  public double getSpentAcidPH() {
    return spentAcidPH;
  }

  /**
   * Returns the calcium content of the spent acid.
   *
   * @return Ca in mg/L
   */
  public double getSpentCalciumMgL() {
    return spentCalciumMgL;
  }

  /**
   * Returns the residual live acid concentration.
   *
   * @return residual acid in wt%
   */
  public double getResidualAcidWtPct() {
    return residualAcidWtPct;
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
    in.put("acidType", acidType.name());
    in.put("acidStrengthWtPct", acidStrengthWtPct);
    in.put("acidVolumeM3", acidVolumeM3);
    in.put("acidDensityKgM3", acidDensityKgM3);
    in.put("scaleMassKgCaCO3", scaleMassKgCaCO3);
    in.put("scaleMassKgMgCO3", scaleMassKgMgCO3);
    in.put("scaleMassKgFeOH3", scaleMassKgFeOH3);
    in.put("temperatureC", temperatureC);
    in.put("tubularMaterial", tubularMaterial);
    in.put("inhibitorPresent", inhibitorPresent);
    map.put("inputs", in);
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("dissolvableScaleKg", dissolvableScaleKg);
    out.put("scaleDissolvedKg", scaleDissolvedKg);
    out.put("dissolutionFractionPct", dissolutionFractionPct);
    out.put("co2GeneratedKg", co2GeneratedKg);
    out.put("spentAcidPH", spentAcidPH);
    out.put("spentCalciumMgL", spentCalciumMgL);
    out.put("residualAcidWtPct", residualAcidWtPct);
    map.put("outputs", out);
    map.put("warnings", warnings);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns the industry standards applied by this acid-treatment model.
   *
   * @return list of standards (each as an ordered map)
   */
  public java.util.List<java.util.Map<String, Object>> getStandardsApplied() {
    return neqsim.process.chemistry.util.StandardsRegistry.toMapList(
        neqsim.process.chemistry.util.StandardsRegistry.API_RP87,
        neqsim.process.chemistry.util.StandardsRegistry.NACE_MR0175);
  }
}
