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
 * Performance model for thermodynamic hydrate inhibitors (THI) — MEG, MeOH, DEG, EG.
 *
 * <p>
 * Two complementary calculations:
 * </p>
 * <ol>
 * <li><strong>Hammerschmidt</strong> — engineering screening estimate of the wt% inhibitor in the
 * water phase required to suppress the hydrate equilibrium temperature by a target {@code dT}.
 * Recommended only for first-pass sizing; for FEED use a NeqSim hydrate flash.</li>
 * <li><strong>Required injection rate</strong> — given the produced-water rate and a target
 * lean/rich split, returns the kg/h of neat inhibitor.</li>
 * </ol>
 *
 * <p>
 * For rigorous suppression the recommended path is to use NeqSim's
 * {@code HydrateInhibitorConcentrationFlash}; this class provides the screening result and
 * standards traceability used by the compatibility and RCA layers.
 * </p>
 *
 * <p>
 * Reference: Hammerschmidt 1934, GPSA Engineering Data Book Section 20.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class ThermodynamicHydrateInhibitorPerformance implements Serializable {

  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /**
   * Inhibitor chemistry.
   */
  public enum InhibitorChemistry {
    /** Methanol — molar mass 32.04 g/mol, Hammerschmidt constant K=1297. */
    METHANOL(32.04, 1297.0),
    /** Mono-ethylene glycol — molar mass 62.07 g/mol, K=2335. */
    MEG(62.07, 2335.0),
    /** Di-ethylene glycol — molar mass 106.12 g/mol, K=4370. */
    DEG(106.12, 4370.0),
    /** Tri-ethylene glycol — molar mass 150.17 g/mol, K=5400. */
    TEG(150.17, 5400.0);

    private final double molarMassGmol;
    private final double hammerschmidtK;

    InhibitorChemistry(double molarMassGmol, double hammerschmidtK) {
      this.molarMassGmol = molarMassGmol;
      this.hammerschmidtK = hammerschmidtK;
    }

    /**
     * Returns the molar mass of the inhibitor.
     *
     * @return molar mass in g/mol
     */
    public double getMolarMassGmol() {
      return molarMassGmol;
    }

    /**
     * Returns the Hammerschmidt constant K.
     *
     * @return Hammerschmidt constant
     */
    public double getHammerschmidtK() {
      return hammerschmidtK;
    }
  }

  // ─── Inputs ─────────────────────────────────────────────

  private InhibitorChemistry chemistry = InhibitorChemistry.MEG;
  private double targetSubcoolingC = 5.0;
  private double waterFlowKgPerHour = 1000.0;
  private double inhibitorPurityWtPct = 90.0;
  private double leanInhibitorWtPctInWater = 0.0;

  // ─── Outputs ────────────────────────────────────────────

  private double requiredInhibitorWtPctInWater;
  private double requiredInjectionKgPerHour;
  private boolean evaluated = false;
  private final List<String> warnings = new ArrayList<String>();

  /**
   * Default constructor.
   */
  public ThermodynamicHydrateInhibitorPerformance() {}

  /**
   * Sets the inhibitor chemistry.
   *
   * @param chemistry inhibitor chemistry
   */
  public void setInhibitorChemistry(InhibitorChemistry chemistry) {
    this.chemistry = chemistry;
  }

  /**
   * Sets the target subcooling (C below hydrate equilibrium temperature).
   *
   * @param dT subcooling in Celsius
   */
  public void setTargetSubcoolingC(double dT) {
    this.targetSubcoolingC = dT;
  }

  /**
   * Sets the produced-water flow rate.
   *
   * @param kgHr water flow in kg/h
   */
  public void setWaterFlowKgPerHour(double kgHr) {
    this.waterFlowKgPerHour = kgHr;
  }

  /**
   * Sets the purity of the neat inhibitor.
   *
   * @param wtPct weight percent active ingredient (0-100)
   */
  public void setInhibitorPurityWtPct(double wtPct) {
    this.inhibitorPurityWtPct = wtPct;
  }

  /**
   * Sets the lean inhibitor concentration already present in the produced water (e.g. from
   * carry-over of regenerated MEG).
   *
   * @param wtPct weight percent (0-100)
   */
  public void setLeanInhibitorWtPctInWater(double wtPct) {
    this.leanInhibitorWtPctInWater = wtPct;
  }

  /**
   * Runs the Hammerschmidt screening calculation.
   */
  public void evaluate() {
    warnings.clear();
    double k = chemistry.getHammerschmidtK();
    double mw = chemistry.getMolarMassGmol();
    // Hammerschmidt: dT = K * w / (MW * (100 - w)) ⇒ w = 100 * dT * MW / (K + dT * MW)
    double w = 100.0 * targetSubcoolingC * mw / (k + targetSubcoolingC * mw);
    requiredInhibitorWtPctInWater = w;
    if (w > 60.0) {
      warnings.add("Required inhibitor wt% > 60 — Hammerschmidt accuracy degraded; use rigorous"
          + " NeqSim hydrate flash");
    }
    if (w <= leanInhibitorWtPctInWater) {
      warnings.add("Lean carry-over already meets target — no make-up required");
    }

    // Mass balance: w*M_total = leanW*M_water + 100*M_pure
    // M_pure = (w - leanW) * M_water / (100 - w)
    double makeupPureKgHr = Math.max(0.0,
        (w - leanInhibitorWtPctInWater) * waterFlowKgPerHour / Math.max(1.0e-3, 100.0 - w));
    requiredInjectionKgPerHour = makeupPureKgHr / Math.max(1.0e-3, inhibitorPurityWtPct / 100.0);
    evaluated = true;
  }

  /**
   * Returns the required inhibitor weight percent in the water phase.
   *
   * @return required wt%
   */
  public double getRequiredInhibitorWtPctInWater() {
    return requiredInhibitorWtPctInWater;
  }

  /**
   * Returns the required injection rate of the neat product.
   *
   * @return injection rate in kg/h
   */
  public double getRequiredInjectionKgPerHour() {
    return requiredInjectionKgPerHour;
  }

  /**
   * Returns warnings raised during evaluation.
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
   * Returns the standards applied by this model.
   *
   * @return list of standard reference maps
   */
  public List<Map<String, Object>> getStandardsApplied() {
    return StandardsRegistry.toMapList(StandardsRegistry.GPSA_DB, new StandardReference(
        "Hammerschmidt 1934", "Industrial", "Original empirical hydrate suppression correlation"));
  }

  /**
   * Returns the structured result map.
   *
   * @return ordered map
   */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("inhibitorChemistry", chemistry.name());
    map.put("targetSubcoolingC", targetSubcoolingC);
    map.put("waterFlowKgPerHour", waterFlowKgPerHour);
    map.put("inhibitorPurityWtPct", inhibitorPurityWtPct);
    map.put("leanInhibitorWtPctInWater", leanInhibitorWtPctInWater);
    map.put("requiredInhibitorWtPctInWater", requiredInhibitorWtPctInWater);
    map.put("requiredInjectionKgPerHour", requiredInjectionKgPerHour);
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
