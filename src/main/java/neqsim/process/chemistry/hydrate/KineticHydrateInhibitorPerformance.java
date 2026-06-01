package neqsim.process.chemistry.hydrate;

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
 * Performance model for kinetic hydrate inhibitors (KHI / LDHI). KHIs do not move the hydrate
 * equilibrium curve; they delay nucleation by an induction time {@code t_ind}. The model is the
 * Larsen-Makogon-Sloan style fit:
 *
 * <p>
 * {@code log10(t_ind) = a + b * dose - c * subcooling}
 * </p>
 *
 * <p>
 * with default coefficients tuned to PVP/Luvicap-class polymers (literature ranges; intended for
 * screening only). The class returns:
 * </p>
 * <ul>
 * <li>predicted induction time at the given subcooling and dose</li>
 * <li>required dose to achieve a target induction time at a given subcooling</li>
 * <li>safe-operating-envelope flag (typical KHI limit dT &lt; 12 C, dose 0.5–3 wt%)</li>
 * </ul>
 *
 * <p>
 * Reference: Sloan &amp; Koh, Clathrate Hydrates of Natural Gases, 3rd ed., Chapter 8 (LDHI
 * screening).
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class KineticHydrateInhibitorPerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  // ─── Coefficients (literature defaults for PVP-class polymers) ──

  private double a = 1.0;
  private double b = 0.6;
  private double c = 0.25;
  private double maxAllowedSubcoolingC = 12.0;
  private double minDoseWtPct = 0.25;
  private double maxDoseWtPct = 3.0;

  // ─── Inputs ─────────────────────────────────────────────

  private double subcoolingC = 5.0;
  private double doseWtPct = 1.0;
  private double targetInductionTimeHours = 24.0;

  // ─── Outputs ────────────────────────────────────────────

  private double predictedInductionTimeHours;
  private double requiredDoseWtPct;
  private boolean evaluated = false;
  private final List<String> warnings = new ArrayList<String>();

  /**
   * Default constructor.
   */
  public KineticHydrateInhibitorPerformance() {}

  /**
   * Sets the empirical coefficients.
   *
   * @param a intercept
   * @param b dose coefficient (per wt%)
   * @param c subcooling coefficient (per Celsius)
   */
  public void setCoefficients(double a, double b, double c) {
    this.a = a;
    this.b = b;
    this.c = c;
  }

  /**
   * Sets the operating subcooling.
   *
   * @param dT subcooling in Celsius
   */
  public void setSubcoolingC(double dT) {
    this.subcoolingC = dT;
  }

  /**
   * Sets the inhibitor dose in weight percent of the water phase.
   *
   * @param wtPct dose in wt%
   */
  public void setDoseWtPct(double wtPct) {
    this.doseWtPct = wtPct;
  }

  /**
   * Sets the target induction time used for the inverse problem.
   *
   * @param hours target induction time in hours
   */
  public void setTargetInductionTimeHours(double hours) {
    this.targetInductionTimeHours = hours;
  }

  /**
   * Runs the calculation.
   */
  public void evaluate() {
    warnings.clear();
    double log10t = a + b * doseWtPct - c * subcoolingC;
    predictedInductionTimeHours = Math.pow(10.0, log10t);
    // Inverse: required dose for target induction time.
    requiredDoseWtPct =
        (Math.log10(Math.max(1.0e-3, targetInductionTimeHours)) - a + c * subcoolingC) / b;
    if (subcoolingC > maxAllowedSubcoolingC) {
      warnings.add("Subcooling " + subcoolingC + " C exceeds typical KHI envelope ("
          + maxAllowedSubcoolingC + " C) — switch to THI or AA-LDHI");
    }
    if (requiredDoseWtPct > maxDoseWtPct) {
      warnings.add("Required dose " + String.format("%.2f", requiredDoseWtPct)
          + " wt% exceeds typical maximum " + maxDoseWtPct + " wt%");
    }
    if (requiredDoseWtPct < minDoseWtPct) {
      warnings.add("Required dose below typical minimum effective " + minDoseWtPct + " wt%");
    }
    evaluated = true;
  }

  /**
   * Returns the predicted induction time.
   *
   * @return induction time in hours
   */
  public double getPredictedInductionTimeHours() {
    return predictedInductionTimeHours;
  }

  /**
   * Returns the required dose for the target induction time.
   *
   * @return required dose in wt%
   */
  public double getRequiredDoseWtPct() {
    return requiredDoseWtPct;
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
   * Returns whether evaluate() has been called.
   *
   * @return true if evaluated
   */
  public boolean isEvaluated() {
    return evaluated;
  }

  /**
   * Returns standards used.
   *
   * @return list of standards
   */
  public List<Map<String, Object>> getStandardsApplied() {
    return StandardsRegistry.toMapList(new StandardReference("Sloan & Koh 2008", "Industrial",
        "Clathrate Hydrates of Natural Gases — LDHI screening"));
  }

  /**
   * Returns the structured result.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("subcoolingC", subcoolingC);
    map.put("doseWtPct", doseWtPct);
    map.put("targetInductionTimeHours", targetInductionTimeHours);
    map.put("predictedInductionTimeHours", predictedInductionTimeHours);
    map.put("requiredDoseWtPct", requiredDoseWtPct);
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
