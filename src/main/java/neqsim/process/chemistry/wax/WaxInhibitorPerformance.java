package neqsim.process.chemistry.wax;

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
 * Performance model for paraffin (wax) inhibitors.
 *
 * <p>
 * The model captures the two effects measured in field trials:
 * </p>
 * <ul>
 * <li><strong>Pour-point depression (PPD).</strong> The reduction of the pour point achieved by an
 * EVA / poly-acrylate / vinyl-acetate-class inhibitor at a given dose. Linear dose response
 * saturating at the maximum efficacy.</li>
 * <li><strong>Yield-stress reduction.</strong> Fraction by which the cold-restart yield stress is
 * reduced — typically 60–95% for a well-fit inhibitor.</li>
 * </ul>
 *
 * <p>
 * Inputs come from the user (lab data preferred) or default literature ranges. The class is
 * intentionally light-weight; for rigorous wax appearance temperature use NeqSim's
 * {@code WaxCurveCalculator}, then bridge here for the dose response.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class WaxInhibitorPerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Inhibitor chemistry. */
  public enum InhibitorChemistry {
    /** Ethylene-vinyl-acetate copolymer. */
    EVA,
    /** Poly-acrylate-ester. */
    POLY_ACRYLATE,
    /** Olefin-ester copolymer. */
    OLEFIN_ESTER,
    /** Maleic-anhydride / vinyl-ester copolymer. */
    MALEIC_VINYL_ESTER;
  }

  // ─── Inputs ─────────────────────────────────────────────

  private InhibitorChemistry chemistry = InhibitorChemistry.EVA;
  private double basePourPointC = 25.0;
  private double baseWaxAppearanceTemperatureC = 35.0;
  private double doseMgL = 200.0;
  private double maxPourPointDepressionC = 25.0;
  private double doseAt50PctEfficacyMgL = 150.0;

  // ─── Outputs ────────────────────────────────────────────

  private double inhibitedPourPointC;
  private double inhibitedWaxAppearanceTemperatureC;
  private double pourPointDepressionC;
  private double yieldStressReductionFraction;
  private double efficacyFraction;
  private boolean evaluated = false;
  private final List<String> warnings = new ArrayList<String>();

  /**
   * Default constructor.
   */
  public WaxInhibitorPerformance() {}

  /**
   * Sets the inhibitor chemistry.
   *
   * @param chemistry chemistry enum
   */
  public void setInhibitorChemistry(InhibitorChemistry chemistry) {
    this.chemistry = chemistry;
  }

  /**
   * Sets the untreated pour point.
   *
   * @param tC pour point in Celsius
   */
  public void setBasePourPointC(double tC) {
    this.basePourPointC = tC;
  }

  /**
   * Sets the untreated wax appearance temperature.
   *
   * @param tC WAT in Celsius
   */
  public void setBaseWaxAppearanceTemperatureC(double tC) {
    this.baseWaxAppearanceTemperatureC = tC;
  }

  /**
   * Sets the inhibitor dose in the oil phase.
   *
   * @param mgL dose in mg/L
   */
  public void setDoseMgL(double mgL) {
    this.doseMgL = mgL;
  }

  /**
   * Sets the maximum pour-point depression achievable by the inhibitor (lab-measured saturation).
   *
   * @param dT depression in Celsius
   */
  public void setMaxPourPointDepressionC(double dT) {
    this.maxPourPointDepressionC = dT;
  }

  /**
   * Sets the dose at which 50% of the maximum efficacy is achieved.
   *
   * @param mgL dose in mg/L
   */
  public void setDoseAt50PctEfficacyMgL(double mgL) {
    this.doseAt50PctEfficacyMgL = mgL;
  }

  /**
   * Runs the calculation. Uses a Langmuir-style dose-response curve.
   */
  public void evaluate() {
    warnings.clear();
    efficacyFraction = doseMgL / (doseAt50PctEfficacyMgL + doseMgL);
    pourPointDepressionC = efficacyFraction * maxPourPointDepressionC;
    inhibitedPourPointC = basePourPointC - pourPointDepressionC;
    // WAT typically depresses by ~30% of the PP depression for a well-fit inhibitor
    inhibitedWaxAppearanceTemperatureC = baseWaxAppearanceTemperatureC - 0.3 * pourPointDepressionC;
    // Yield-stress reduction empirically follows 0.6 + 0.35 * efficacy
    yieldStressReductionFraction = Math.min(0.95, 0.6 * efficacyFraction + 0.35 * efficacyFraction);
    if (efficacyFraction < 0.4) {
      warnings.add(
          "Dose below 50% efficacy — consider increasing or switching to a different chemistry");
    }
    if (chemistry == InhibitorChemistry.EVA && baseWaxAppearanceTemperatureC > 60.0) {
      warnings.add("EVA chemistry typically loses efficacy at WAT > 60 C");
    }
    evaluated = true;
  }

  /**
   * Returns the inhibited pour point.
   *
   * @return pour point in Celsius
   */
  public double getInhibitedPourPointC() {
    return inhibitedPourPointC;
  }

  /**
   * Returns the inhibited wax appearance temperature.
   *
   * @return WAT in Celsius
   */
  public double getInhibitedWaxAppearanceTemperatureC() {
    return inhibitedWaxAppearanceTemperatureC;
  }

  /**
   * Returns the pour-point depression.
   *
   * @return depression in Celsius
   */
  public double getPourPointDepressionC() {
    return pourPointDepressionC;
  }

  /**
   * Returns the fractional yield-stress reduction.
   *
   * @return reduction fraction (0-1)
   */
  public double getYieldStressReductionFraction() {
    return yieldStressReductionFraction;
  }

  /**
   * Returns the dose-response efficacy fraction.
   *
   * @return efficacy (0-1)
   */
  public double getEfficacyFraction() {
    return efficacyFraction;
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
        new StandardReference("ASTM D97", "ASTM",
            "Standard test method for pour point of petroleum products"),
        new StandardReference("ASTM D7346", "ASTM",
            "No-flow point and pour point of petroleum products"));
  }

  /**
   * Returns the structured result.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("inhibitorChemistry", chemistry.name());
    map.put("basePourPointC", basePourPointC);
    map.put("baseWaxAppearanceTemperatureC", baseWaxAppearanceTemperatureC);
    map.put("doseMgL", doseMgL);
    map.put("efficacyFraction", efficacyFraction);
    map.put("pourPointDepressionC", pourPointDepressionC);
    map.put("inhibitedPourPointC", inhibitedPourPointC);
    map.put("inhibitedWaxAppearanceTemperatureC", inhibitedWaxAppearanceTemperatureC);
    map.put("yieldStressReductionFraction", yieldStressReductionFraction);
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
