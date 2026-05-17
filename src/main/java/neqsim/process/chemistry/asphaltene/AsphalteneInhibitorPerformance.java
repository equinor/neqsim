package neqsim.process.chemistry.asphaltene;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import neqsim.process.chemistry.util.StandardsRegistry;
import neqsim.process.chemistry.util.StandardsRegistry.StandardReference;

/**
 * Performance model for asphaltene inhibitors / dispersants.
 *
 * <p>
 * The model exposes:
 * </p>
 * <ul>
 * <li><strong>Colloidal Instability Index (CII) shift.</strong> The reduction of CII achieved by a
 * polymeric inhibitor at the given dose. CII below 0.7 is generally considered stable; above 0.9 is
 * unstable. The shift is dose-dependent with a Langmuir saturation.</li>
 * <li><strong>Onset-pressure shift.</strong> Approximate downward shift of the asphaltene onset
 * pressure (AOP), enabling the inhibitor to keep production above the new onset.</li>
 * </ul>
 *
 * <p>
 * For the baseline CII / AOP use NeqSim's {@code AsphalteneStabilityAnalyzer}; this class adds the
 * chemistry response.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class AsphalteneInhibitorPerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Inhibitor chemistry. */
  public enum InhibitorChemistry {
    /** Alkyl-phenol-formaldehyde resin. */
    ALKYLPHENOL_RESIN,
    /** Polyolefin amide alkeneamine. */
    POAA,
    /** Surfactant-based dispersant. */
    SURFACTANT,
    /** Polymeric ester. */
    POLYMERIC_ESTER;
  }

  // ─── Inputs ─────────────────────────────────────────────

  private InhibitorChemistry chemistry = InhibitorChemistry.POAA;
  private double baseColloidalInstabilityIndex = 0.95;
  private double baseAsphalteneOnsetPressureBara = 200.0;
  private double doseMgL = 100.0;
  private double maxCiiReduction = 0.30;
  private double maxAopShiftBar = 60.0;
  private double doseAt50PctEfficacyMgL = 75.0;

  // ─── Outputs ────────────────────────────────────────────

  private double inhibitedCii;
  private double inhibitedAopBara;
  private double ciiReduction;
  private double aopShiftBar;
  private double efficacyFraction;
  private boolean stableAfterTreatment;
  private boolean evaluated = false;
  private final List<String> warnings = new ArrayList<String>();

  /**
   * Default constructor.
   */
  public AsphalteneInhibitorPerformance() {}

  /**
   * Sets the inhibitor chemistry.
   *
   * @param chemistry chemistry enum
   */
  public void setInhibitorChemistry(InhibitorChemistry chemistry) {
    this.chemistry = chemistry;
  }

  /**
   * Sets the untreated CII.
   *
   * @param cii colloidal instability index
   */
  public void setBaseColloidalInstabilityIndex(double cii) {
    this.baseColloidalInstabilityIndex = cii;
  }

  /**
   * Sets the untreated asphaltene onset pressure.
   *
   * @param bara onset pressure in bara
   */
  public void setBaseAsphalteneOnsetPressureBara(double bara) {
    this.baseAsphalteneOnsetPressureBara = bara;
  }

  /**
   * Sets the inhibitor dose.
   *
   * @param mgL dose in mg/L
   */
  public void setDoseMgL(double mgL) {
    this.doseMgL = mgL;
  }

  /**
   * Sets the maximum CII reduction achievable.
   *
   * @param dCii maximum reduction (absolute)
   */
  public void setMaxCiiReduction(double dCii) {
    this.maxCiiReduction = dCii;
  }

  /**
   * Sets the maximum AOP downward shift achievable.
   *
   * @param bar shift in bar
   */
  public void setMaxAopShiftBar(double bar) {
    this.maxAopShiftBar = bar;
  }

  /**
   * Sets the dose at 50% efficacy.
   *
   * @param mgL dose in mg/L
   */
  public void setDoseAt50PctEfficacyMgL(double mgL) {
    this.doseAt50PctEfficacyMgL = mgL;
  }

  /**
   * Runs the calculation.
   */
  public void evaluate() {
    warnings.clear();
    efficacyFraction = doseMgL / (doseAt50PctEfficacyMgL + doseMgL);
    ciiReduction = efficacyFraction * maxCiiReduction;
    aopShiftBar = efficacyFraction * maxAopShiftBar;
    inhibitedCii = Math.max(0.0, baseColloidalInstabilityIndex - ciiReduction);
    inhibitedAopBara = baseAsphalteneOnsetPressureBara - aopShiftBar;
    stableAfterTreatment = inhibitedCii < 0.7;
    if (!stableAfterTreatment) {
      warnings.add("CII after treatment " + String.format("%.2f", inhibitedCii)
          + " still > 0.7 — fluid remains unstable");
    }
    if (efficacyFraction < 0.4) {
      warnings.add("Dose " + doseMgL + " mg/L is below 50% efficacy point");
    }
    evaluated = true;
  }

  /**
   * Returns the inhibited CII.
   *
   * @return CII (dimensionless)
   */
  public double getInhibitedCii() {
    return inhibitedCii;
  }

  /**
   * Returns the inhibited asphaltene onset pressure.
   *
   * @return AOP in bara
   */
  public double getInhibitedAopBara() {
    return inhibitedAopBara;
  }

  /**
   * Returns the CII reduction.
   *
   * @return reduction
   */
  public double getCiiReduction() {
    return ciiReduction;
  }

  /**
   * Returns the AOP shift.
   *
   * @return shift in bar
   */
  public double getAopShiftBar() {
    return aopShiftBar;
  }

  /**
   * Returns the dose-response efficacy fraction.
   *
   * @return fraction (0-1)
   */
  public double getEfficacyFraction() {
    return efficacyFraction;
  }

  /**
   * Returns whether the fluid is stable after treatment (CII &lt; 0.7).
   *
   * @return true if stable
   */
  public boolean isStableAfterTreatment() {
    return stableAfterTreatment;
  }

  /**
   * Returns warnings.
   *
   * @return list of warnings
   */
  public List<String> getWarnings() {
    return new ArrayList<String>(warnings);
  }

  /**
   * Returns whether evaluate() was called.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns the standards applied.
   *
   * @return list of standards
   */
  public List<Map<String, Object>> getStandardsApplied() {
    return StandardsRegistry.toMapList(
        new StandardReference("ASTM D6560", "ASTM",
            "Determination of asphaltenes (heptane insolubles) in crude petroleum"),
        new StandardReference("Yen-Mullins model", "Industrial",
            "Asphaltene aggregation reference framework"));
  }

  /**
   * Returns the structured result.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("inhibitorChemistry", chemistry.name());
    map.put("baseColloidalInstabilityIndex", baseColloidalInstabilityIndex);
    map.put("baseAsphalteneOnsetPressureBara", baseAsphalteneOnsetPressureBara);
    map.put("doseMgL", doseMgL);
    map.put("efficacyFraction", efficacyFraction);
    map.put("ciiReduction", ciiReduction);
    map.put("aopShiftBar", aopShiftBar);
    map.put("inhibitedCii", inhibitedCii);
    map.put("inhibitedAopBara", inhibitedAopBara);
    map.put("stableAfterTreatment", stableAfterTreatment);
    map.put("warnings", warnings);
    map.put("standardsApplied", getStandardsApplied());
    return map;
  }

  /**
   * Returns the result as JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    return gson.toJson(toMap());
  }
}
