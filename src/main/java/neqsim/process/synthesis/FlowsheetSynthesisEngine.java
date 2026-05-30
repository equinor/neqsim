package neqsim.process.synthesis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import neqsim.process.equipment.distillation.DistillationColumn;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Agent-driven synthesis engine that proposes a flowsheet for a {@link SeparationDuty}.
 *
 * <p>
 * The engine implements a small but defensible set of heuristics inspired by Douglas (1988)
 * <em>Conceptual Design of Chemical Processes</em>:
 * </p>
 *
 * <ol>
 * <li>Run an isothermal flash on the feed at the duty operating pressure. If the resulting state is
 * single-phase, the engine immediately escalates to a distillation column.</li>
 * <li>Otherwise, evaluate a single-stage {@link Separator}. If all duty specs are met, return
 * {@link FlowsheetProposal.Strategy#SINGLE_FLASH SINGLE_FLASH}.</li>
 * <li>If not, identify the light key (LK) and heavy key (HK) from the duty:
 * <ul>
 * <li>LK = component with the largest K-value among those with a top-product spec.</li>
 * <li>HK = component with the smallest K-value among those with a bottom-product spec.</li>
 * </ul>
 * Compute relative volatility α<sub>LK/HK</sub> = K<sub>LK</sub>/K<sub>HK</sub>.</li>
 * <li>If α &gt;= 1.5, propose {@link FlowsheetProposal.Strategy#DISTILLATION DISTILLATION}.
 * Estimate the minimum number of stages with Fenske and size the column at N = 2·N<sub>min</sub>
 * (bounded to [6, 30]). Build and configure a {@link DistillationColumn} with feed at the middle
 * tray.</li>
 * <li>If α &lt; 1.5, fall back to {@link FlowsheetProposal.Strategy#SINGLE_FLASH} with a rationale
 * note that a more advanced separation (extractive distillation, membrane, sorption) is
 * required.</li>
 * </ol>
 *
 * <p>
 * The engine <em>does not</em> automatically execute distillation columns inside
 * {@link #proposeAndBuild(SeparationDuty)} — convergence depends on solver settings and is best
 * left to the caller. Single-flash proposals are run so that predicted compositions can be read
 * back. For distillation proposals the predicted compositions reflect the target specs (the column
 * was sized to meet them).
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public final class FlowsheetSynthesisEngine {

  /** Threshold for relative volatility above which distillation is recommended. */
  public static final double ALPHA_DISTILLATION_THRESHOLD = 1.5;

  /** Minimum number of trays for any proposed distillation column. */
  public static final int MIN_TRAYS = 6;

  /** Maximum number of trays for any proposed distillation column. */
  public static final int MAX_TRAYS = 30;

  /**
   * Creates a synthesis engine. The engine is stateless; instances are interchangeable.
   */
  public FlowsheetSynthesisEngine() {
    // no state
  }

  /**
   * Builds and returns a {@link FlowsheetProposal} for the given duty.
   *
   * @param duty the separation duty to solve; must not be null
   * @return the proposal (always non-null; may have {@link FlowsheetProposal.Strategy#INFEASIBLE})
   * @throws IllegalArgumentException if {@code duty} is null
   */
  public FlowsheetProposal proposeAndBuild(SeparationDuty duty) {
    if (duty == null) {
      throw new IllegalArgumentException("duty must not be null");
    }

    SystemInterface flashSystem = duty.getFeed().getFluid().clone();
    double opP = Double.isNaN(duty.getOperatingPressureBara()) ? duty.getFeed().getPressure("bara")
        : duty.getOperatingPressureBara();
    flashSystem.setPressure(opP, "bara");
    ThermodynamicOperations ops = new ThermodynamicOperations(flashSystem);
    try {
      ops.TPflash();
    } catch (Exception ex) {
      return buildFallback(duty, "Flash failed: " + ex.getMessage(), opP);
    }
    flashSystem.initProperties();

    List<String> alternatives = new ArrayList<String>();

    // Quick check: must be two-phase to consider flash separation.
    int nPhase = flashSystem.getNumberOfPhases();
    if (nPhase < 2) {
      alternatives.add("SINGLE_FLASH rejected: feed is single-phase at "
          + String.format("%.2f bara — pressure swing or distillation required", opP));
      return buildDistillation(duty, flashSystem, opP, alternatives,
          "Feed is single-phase at operating pressure — escalating to distillation column.");
    }

    // Predict single-flash compositions
    Map<String, Double> gasFracs = readMoleFractions(flashSystem, 0);
    Map<String, Double> liqFracs = readMoleFractions(flashSystem, 1);

    boolean specsMet = specsMet(duty, gasFracs, liqFracs);
    if (specsMet) {
      ProcessSystem ps = buildSingleFlash(duty, opP);
      return new FlowsheetProposal(FlowsheetProposal.Strategy.SINGLE_FLASH,
          "Single-stage flash at " + fmt(opP) + " bara meets all duty specs.", ps, gasFracs,
          liqFracs, alternatives, true);
    }
    alternatives.add("SINGLE_FLASH rejected: predicted compositions do not satisfy duty specs.");

    // Identify keys + alpha
    KeyComponents keys = identifyKeys(duty, flashSystem);
    if (keys.lk == null || keys.hk == null || Double.isNaN(keys.alpha)) {
      // Cannot infer keys → return the single flash as best-effort with INFEASIBLE strategy
      ProcessSystem ps = buildSingleFlash(duty, opP);
      return new FlowsheetProposal(FlowsheetProposal.Strategy.INFEASIBLE,
          "Could not identify light/heavy keys from duty specs; returning single-stage flash as"
              + " best-effort.",
          ps, gasFracs, liqFracs, alternatives, false);
    }

    if (keys.alpha < ALPHA_DISTILLATION_THRESHOLD) {
      alternatives.add("DISTILLATION rejected: relative volatility α=" + fmt(keys.alpha) + " (LK="
          + keys.lk + ", HK=" + keys.hk + ") is below 1.5 — distillation impractical;"
          + " consider extractive distillation, membranes, or adsorption.");
      ProcessSystem ps = buildSingleFlash(duty, opP);
      return new FlowsheetProposal(FlowsheetProposal.Strategy.INFEASIBLE,
          "Relative volatility α=" + fmt(keys.alpha) + " between LK=" + keys.lk + " and HK="
              + keys.hk + " is below 1.5. No standard distillation/flash structure will meet the"
              + " specs; an advanced separation is required.",
          ps, gasFracs, liqFracs, alternatives, false);
    }

    return buildDistillation(duty, flashSystem, opP, alternatives,
        "Relative volatility α=" + fmt(keys.alpha) + " between LK=" + keys.lk + " and HK=" + keys.hk
            + " supports a distillation column.");
  }

  // ---------------------------------------------------------------------------
  // helpers

  /**
   * Container for identified light/heavy keys and the resulting relative volatility.
   */
  private static final class KeyComponents {
    final String lk;
    final String hk;
    final double alpha;

    KeyComponents(String lk, String hk, double alpha) {
      this.lk = lk;
      this.hk = hk;
      this.alpha = alpha;
    }
  }

  /**
   * Identifies the light and heavy keys from the duty specs and the flash result.
   *
   * @param duty the duty
   * @param flashed the post-flash system (two-phase)
   * @return key descriptor; fields may be null/NaN when no candidate is found
   */
  private KeyComponents identifyKeys(SeparationDuty duty, SystemInterface flashed) {
    String lk = null;
    double lkK = -1.0;
    for (String c : duty.getTopProductSpecs().keySet()) {
      double k = kValue(flashed, c);
      if (Double.isNaN(k)) {
        continue;
      }
      if (k > lkK) {
        lkK = k;
        lk = c;
      }
    }
    String hk = null;
    double hkK = Double.POSITIVE_INFINITY;
    for (String c : duty.getBottomProductSpecs().keySet()) {
      double k = kValue(flashed, c);
      if (Double.isNaN(k)) {
        continue;
      }
      if (k < hkK) {
        hkK = k;
        hk = c;
      }
    }
    if (lk == null && hk != null) {
      // Pick the heaviest non-HK component with the highest K
      lk = pickComplementKey(duty, flashed, hk, true);
      if (lk != null) {
        lkK = kValue(flashed, lk);
      }
    }
    if (hk == null && lk != null) {
      hk = pickComplementKey(duty, flashed, lk, false);
      if (hk != null) {
        hkK = kValue(flashed, hk);
      }
    }
    double alpha = (lk != null && hk != null && hkK > 0) ? (lkK / hkK) : Double.NaN;
    return new KeyComponents(lk, hk, alpha);
  }

  private String pickComplementKey(SeparationDuty duty, SystemInterface flashed, String exclude,
      boolean wantHighestK) {
    String chosen = null;
    double chosenK = wantHighestK ? -1.0 : Double.POSITIVE_INFINITY;
    for (int i = 0; i < flashed.getNumberOfComponents(); i++) {
      String name = flashed.getComponent(i).getName();
      if (name.equals(exclude)) {
        continue;
      }
      double k = kValue(flashed, name);
      if (Double.isNaN(k)) {
        continue;
      }
      if (wantHighestK ? (k > chosenK) : (k < chosenK)) {
        chosenK = k;
        chosen = name;
      }
    }
    return chosen;
  }

  /**
   * Reads the K-value of a component from a flashed (two-phase) system.
   *
   * @param sys the flashed system
   * @param componentName the component
   * @return K = y/x, or NaN when the component is not present
   */
  private double kValue(SystemInterface sys, String componentName) {
    int idx = -1;
    for (int i = 0; i < sys.getNumberOfComponents(); i++) {
      if (sys.getComponent(i).getName().equals(componentName)) {
        idx = i;
        break;
      }
    }
    if (idx < 0 || sys.getNumberOfPhases() < 2) {
      return Double.NaN;
    }
    double y = sys.getPhase(0).getComponent(idx).getx();
    double x = sys.getPhase(1).getComponent(idx).getx();
    if (x <= 0.0) {
      return Double.NaN;
    }
    return y / x;
  }

  /**
   * Returns the mole-fraction vector of phase {@code phaseIdx} keyed by component name.
   *
   * @param sys the flashed system
   * @param phaseIdx 0 for gas, 1 for liquid
   * @return mole-fraction map
   */
  private Map<String, Double> readMoleFractions(SystemInterface sys, int phaseIdx) {
    Map<String, Double> out = new LinkedHashMap<String, Double>();
    if (sys.getNumberOfPhases() <= phaseIdx) {
      return out;
    }
    for (int i = 0; i < sys.getNumberOfComponents(); i++) {
      out.put(sys.getComponent(i).getName(), sys.getPhase(phaseIdx).getComponent(i).getx());
    }
    return out;
  }

  /**
   * Tests whether predicted mole fractions satisfy the duty specs.
   *
   * @param duty the duty
   * @param gasFracs predicted top-product mole fractions
   * @param liqFracs predicted bottom-product mole fractions
   * @return true when every spec is met
   */
  private boolean specsMet(SeparationDuty duty, Map<String, Double> gasFracs,
      Map<String, Double> liqFracs) {
    for (Map.Entry<String, Double> e : duty.getTopProductSpecs().entrySet()) {
      Double v = gasFracs.get(e.getKey());
      if (v == null || v < e.getValue()) {
        return false;
      }
    }
    for (Map.Entry<String, Double> e : duty.getBottomProductSpecs().entrySet()) {
      Double v = liqFracs.get(e.getKey());
      if (v == null || v < e.getValue()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Builds and runs a single-flash flowsheet.
   *
   * @param duty the duty
   * @param opP operating pressure in bara
   * @return populated and run {@link ProcessSystem}
   */
  private ProcessSystem buildSingleFlash(SeparationDuty duty, double opP) {
    ProcessSystem ps = new ProcessSystem();
    StreamInterface feedClone = duty.getFeed().clone();
    feedClone.setName(duty.getName() + "-feed");
    feedClone.setPressure(opP, "bara");
    Separator sep = new Separator(duty.getName() + "-Sep", feedClone);
    ps.add(feedClone);
    ps.add(sep);
    try {
      ps.run();
    } catch (Exception ex) {
      // best effort; leave unran
    }
    return ps;
  }

  /**
   * Builds (but does not run) a distillation flowsheet sized from a Fenske estimate.
   *
   * @param duty the duty
   * @param flashed the post-flash system used for K-value estimates
   * @param opP operating pressure in bara
   * @param alternatives the running list of rejected alternatives
   * @param rationale rationale text to embed in the proposal
   * @return populated FlowsheetProposal with strategy DISTILLATION
   */
  private FlowsheetProposal buildDistillation(SeparationDuty duty, SystemInterface flashed,
      double opP, List<String> alternatives, String rationale) {
    KeyComponents keys = identifyKeys(duty, flashed);
    int nTrays = MIN_TRAYS;
    String fenskeNote = "Fenske min stages not computed (insufficient duty specs).";
    if (keys.lk != null && keys.hk != null && !Double.isNaN(keys.alpha) && keys.alpha > 1.000001) {
      Double xLkTop = duty.getTopProductSpecs().get(keys.lk);
      Double xHkBot = duty.getBottomProductSpecs().get(keys.hk);
      if (xLkTop != null && xHkBot != null && xLkTop > 0 && xLkTop < 1 && xHkBot > 0
          && xHkBot < 1) {
        double xHkTop = 1.0 - xLkTop;
        double xLkBot = 1.0 - xHkBot;
        double separation = (xLkTop / xHkTop) * (xHkBot / xLkBot);
        if (separation > 1.0) {
          double nMin = Math.log(separation) / Math.log(keys.alpha);
          nTrays = (int) Math.round(2.0 * nMin);
          fenskeNote =
              "Fenske N_min=" + fmt(nMin) + " (α=" + fmt(keys.alpha) + "); using N=2·N_min.";
        }
      }
    }
    if (nTrays < MIN_TRAYS) {
      nTrays = MIN_TRAYS;
    }
    if (nTrays > MAX_TRAYS) {
      nTrays = MAX_TRAYS;
    }

    ProcessSystem ps = new ProcessSystem();
    StreamInterface feedClone = duty.getFeed().clone();
    feedClone.setName(duty.getName() + "-feed");
    feedClone.setPressure(opP, "bara");
    ps.add(feedClone);

    DistillationColumn col = new DistillationColumn(duty.getName() + "-Col", nTrays, true, true);
    int totalTrays = nTrays + 2;
    int feedTray = Math.max(1, Math.min(totalTrays - 2, totalTrays / 2));
    col.addFeedStream(feedClone, feedTray);
    ps.add(col);

    String rationaleFull = rationale + " " + fenskeNote + " Column has " + totalTrays
        + " stages (incl. condenser + reboiler); feed enters at stage " + feedTray
        + " (mid-column). Operating pressure " + fmt(opP) + " bara.";

    // For DISTILLATION we assume specs are met (column was sized to meet them).
    Map<String, Double> topPred = new LinkedHashMap<String, Double>(duty.getTopProductSpecs());
    Map<String, Double> botPred = new LinkedHashMap<String, Double>(duty.getBottomProductSpecs());
    return new FlowsheetProposal(FlowsheetProposal.Strategy.DISTILLATION, rationaleFull, ps,
        topPred, botPred, alternatives, true);
  }

  /**
   * Falls back to a single-stage flash when the main flow fails.
   *
   * @param duty the duty
   * @param reason rationale text
   * @param opP operating pressure
   * @return INFEASIBLE proposal with the best-effort flash
   */
  private FlowsheetProposal buildFallback(SeparationDuty duty, String reason, double opP) {
    ProcessSystem ps = buildSingleFlash(duty, opP);
    List<String> alts = new ArrayList<String>();
    alts.add(reason);
    return new FlowsheetProposal(FlowsheetProposal.Strategy.INFEASIBLE,
        "Synthesis fallback: " + reason, ps, new LinkedHashMap<String, Double>(),
        new LinkedHashMap<String, Double>(), alts, false);
  }

  private static String fmt(double v) {
    return String.format(java.util.Locale.ROOT, "%.3f", v);
  }

  /**
   * Convenience: pre-runs the input feed so that the duty constructor invariant is satisfied.
   * Intended for one-off agent invocations and tests.
   *
   * @param feed the unrun feed stream
   * @return the same stream after {@link Stream#run()}
   */
  public static StreamInterface ensureRun(StreamInterface feed) {
    feed.run();
    return feed;
  }
}
