package neqsim.process.operations;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

/**
 * Assesses a historian setpoint trial and checks whether its evidence supports a causal before/after comparison for
 * process-model optimization.
 *
 * <p>
 * The class collects time-stamped signal observations, a single defined intervention (a setpoint change), and an
 * optional set of confounder gates. {@link #assess(double, double, double, double)} computes the descriptive
 * before/after effect for every signal and evaluates three kinds of quality gate:
 * </p>
 *
 * <ul>
 * <li><b>Intervention</b> - the observed setpoint signal must actually move from the old to the new value.</li>
 * <li><b>Data quality</b> - each signal must have enough good-quality samples in both windows.</li>
 * <li><b>Confounder</b> - each configured confounder signal must not move more than its allowed normalized change.</li>
 * </ul>
 *
 * <p>
 * A causal interpretation is only declared defensible when all gates pass; otherwise the result is labelled
 * {@code DESCRIPTIVE}. This mirrors ISO 14224-style source and data-quality traceability and deliberately makes no
 * claim of a formal causal-inference standard. The class orchestrates plain time-series evidence and does not depend on
 * any specific NeqSim equipment, so histories can come from {@link OperationalTagMap} reads, imported tagreader data,
 * or simulated results.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public final class OperatingTrialAssessment implements Serializable {
  private static final long serialVersionUID = 1L;

  /** Default minimum fraction of good-quality samples required in each window. */
  public static final double DEFAULT_MIN_GOOD_DATA_FRACTION = 0.8;

  /** Small value used to guard against division by zero when normalizing changes. */
  private static final double EPSILON = 1.0e-12;

  private final String studyName;
  private final Map<String, Signal> signals = new LinkedHashMap<String, Signal>();
  private final List<Confounder> confounders = new ArrayList<Confounder>();
  private double minGoodDataFraction = DEFAULT_MIN_GOOD_DATA_FRACTION;
  private Intervention intervention;
  private TrialAssessmentResult lastResult;

  /**
   * Creates a trial assessment.
   *
   * @param studyName study or operating-case name
   */
  public OperatingTrialAssessment(String studyName) {
    this.studyName = studyName == null ? "" : studyName;
  }

  /**
   * Adds a single time-stamped observation for a signal.
   *
   * <p>
   * Call this repeatedly to build up a signal time series. Samples do not need to be added in time order; they are
   * sorted logically at assessment time by their window membership.
   * </p>
   *
   * @param name signal name, for example a logical tag or historian tag
   * @param time observation time in consistent units, typically seconds or epoch seconds
   * @param value observed value
   * @param status data-quality status; a null or empty value, or {@code good}, {@code ok}, or {@code valid}
   * (case-insensitive) is treated as good quality, anything else as bad quality
   * @param unit engineering unit for the signal; the first non-empty unit provided for a signal is retained
   * @return this assessment for chaining
   * @throws IllegalArgumentException if the signal name is null or empty
   */
  public OperatingTrialAssessment addSignal(String name, double time, double value, String status, String unit) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Signal name must not be null or empty");
    }
    Signal signal = signals.get(name);
    if (signal == null) {
      signal = new Signal(name, unit);
      signals.put(name, signal);
    } else if ((signal.unit == null || signal.unit.isEmpty()) && unit != null && !unit.isEmpty()) {
      signal.unit = unit;
    }
    signal.observations.add(new Observation(time, value, status));
    return this;
  }

  /**
   * Defines the intervention that the trial tests.
   *
   * @param setpointName name of the setpoint signal that was changed; a signal with this name should be added so the
   * intervention gate can confirm the move
   * @param oldValue setpoint value before the change
   * @param newValue setpoint value after the change
   * @param transitionTime time at which the setpoint was changed
   * @throws IllegalArgumentException if the setpoint name is null or empty
   */
  public void setIntervention(String setpointName, double oldValue, double newValue, double transitionTime) {
    if (setpointName == null || setpointName.trim().isEmpty()) {
      throw new IllegalArgumentException("Intervention setpoint name must not be null or empty");
    }
    this.intervention = new Intervention(setpointName, oldValue, newValue, transitionTime);
  }

  /**
   * Registers a confounder gate.
   *
   * <p>
   * A confounder is a signal expected to stay approximately constant for the before/after comparison to be causally
   * meaningful. If the signal's normalized change between the windows exceeds the allowed value the causal claim is
   * rejected and the result stays descriptive.
   * </p>
   *
   * @param signalName name of the confounding signal
   * @param maxNormalizedChange maximum allowed absolute normalized change, for example 0.05 for five percent
   * @throws IllegalArgumentException if the signal name is null or empty, or the limit is negative or not finite
   */
  public void addConfounder(String signalName, double maxNormalizedChange) {
    if (signalName == null || signalName.trim().isEmpty()) {
      throw new IllegalArgumentException("Confounder signal name must not be null or empty");
    }
    if (maxNormalizedChange < 0.0 || Double.isNaN(maxNormalizedChange) || Double.isInfinite(maxNormalizedChange)) {
      throw new IllegalArgumentException("Maximum normalized change must be a finite, non-negative value");
    }
    confounders.add(new Confounder(signalName, maxNormalizedChange));
  }

  /**
   * Sets the minimum fraction of good-quality samples required in each window.
   *
   * @param fraction minimum good-data fraction from 0 to 1
   * @throws IllegalArgumentException if the fraction is outside the range 0 to 1
   */
  public void setMinGoodDataFraction(double fraction) {
    if (fraction < 0.0 || fraction > 1.0 || Double.isNaN(fraction)) {
      throw new IllegalArgumentException("Minimum good-data fraction must be between 0 and 1");
    }
    this.minGoodDataFraction = fraction;
  }

  /**
   * Assesses the trial using a pre-intervention window and a post-intervention window.
   *
   * @param preWindowStart start time of the pre-intervention window, inclusive
   * @param preWindowEnd end time of the pre-intervention window, inclusive
   * @param postWindowStart start time of the post-intervention window, inclusive
   * @param postWindowEnd end time of the post-intervention window, inclusive
   * @return the trial assessment result
   * @throws IllegalStateException if no intervention has been defined
   * @throws IllegalArgumentException if a window is empty or the windows overlap
   */
  public TrialAssessmentResult assess(double preWindowStart, double preWindowEnd, double postWindowStart,
      double postWindowEnd) {
    if (intervention == null) {
      throw new IllegalStateException("An intervention must be defined before assessment");
    }
    if (preWindowStart > preWindowEnd) {
      throw new IllegalArgumentException("Pre-window start must not be after pre-window end");
    }
    if (postWindowStart > postWindowEnd) {
      throw new IllegalArgumentException("Post-window start must not be after post-window end");
    }
    if (postWindowStart < preWindowEnd) {
      throw new IllegalArgumentException("Post-window must not start before the pre-window ends");
    }

    List<String> warnings = new ArrayList<String>();
    if (intervention.transitionTime < preWindowEnd || intervention.transitionTime > postWindowStart) {
      warnings.add("Intervention transition time " + intervention.transitionTime
          + " is not between the pre-window end and the post-window start");
    }

    List<TrialAssessmentResult.SignalEffect> effects = new ArrayList<TrialAssessmentResult.SignalEffect>();
    Map<String, TrialAssessmentResult.SignalEffect> effectByName = new LinkedHashMap<String, TrialAssessmentResult.SignalEffect>();
    for (Signal signal : signals.values()) {
      TrialAssessmentResult.SignalEffect effect = buildEffect(signal, preWindowStart, preWindowEnd, postWindowStart,
          postWindowEnd);
      effects.add(effect);
      effectByName.put(signal.name, effect);
    }

    List<TrialAssessmentResult.QualityGate> gates = new ArrayList<TrialAssessmentResult.QualityGate>();

    boolean interventionApplied = evaluateInterventionGate(effectByName, gates, warnings);
    boolean dataQualityOk = evaluateDataQualityGates(effects, gates);
    boolean confoundersOk = evaluateConfounderGates(effectByName, gates, warnings);

    boolean causalClaimAllowed = interventionApplied && dataQualityOk && confoundersOk;

    lastResult = new TrialAssessmentResult(studyName, intervention.setpointName, intervention.oldValue,
        intervention.newValue, intervention.transitionTime, interventionApplied, effects, gates, causalClaimAllowed,
        warnings);
    return lastResult;
  }

  /**
   * Serializes the assessment configuration and the most recent result to schema-versioned JSON.
   *
   * @return JSON evidence document; when no assessment has run yet the {@code result} field is null
   */
  public String toJson() {
    Map<String, Object> document = new LinkedHashMap<String, Object>();
    document.put("schemaVersion", TrialAssessmentResult.SCHEMA_VERSION);
    document.put("studyName", studyName);
    document.put("minGoodDataFraction", minGoodDataFraction);

    Map<String, Object> interventionDoc = new LinkedHashMap<String, Object>();
    if (intervention != null) {
      interventionDoc.put("setpointName", intervention.setpointName);
      interventionDoc.put("oldValue", intervention.oldValue);
      interventionDoc.put("newValue", intervention.newValue);
      interventionDoc.put("transitionTime", intervention.transitionTime);
    }
    document.put("intervention", interventionDoc);

    List<Map<String, Object>> confounderDocs = new ArrayList<Map<String, Object>>();
    for (Confounder confounder : confounders) {
      Map<String, Object> confounderDoc = new LinkedHashMap<String, Object>();
      confounderDoc.put("signalName", confounder.signalName);
      confounderDoc.put("maxNormalizedChange", confounder.maxNormalizedChange);
      confounderDocs.add(confounderDoc);
    }
    document.put("confounders", confounderDocs);
    document.put("signalCount", signals.size());
    document.put("result", lastResult);

    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create().toJson(document);
  }

  /**
   * Returns the most recent assessment result.
   *
   * @return last result, or null when no assessment has run yet
   */
  public TrialAssessmentResult getLastResult() {
    return lastResult;
  }

  /**
   * Builds the before/after effect for one signal.
   *
   * @param signal signal to evaluate
   * @param preStart pre-window start
   * @param preEnd pre-window end
   * @param postStart post-window start
   * @param postEnd post-window end
   * @return signal effect
   */
  private TrialAssessmentResult.SignalEffect buildEffect(Signal signal, double preStart, double preEnd,
      double postStart, double postEnd) {
    WindowStatistics pre = windowStatistics(signal, preStart, preEnd);
    WindowStatistics post = windowStatistics(signal, postStart, postEnd);
    double delta = post.mean - pre.mean;
    double normalizedChange = delta / Math.max(Math.abs(pre.mean), EPSILON);
    boolean dataQualityOk = pre.goodFraction >= minGoodDataFraction && post.goodFraction >= minGoodDataFraction;
    return new TrialAssessmentResult.SignalEffect(signal.name, signal.unit, pre.mean, post.mean, delta,
        normalizedChange, pre.totalCount, post.totalCount, pre.goodFraction, post.goodFraction, dataQualityOk);
  }

  /**
   * Computes mean of good samples and good-data fraction inside a time window.
   *
   * @param signal signal to summarize
   * @param start window start, inclusive
   * @param end window end, inclusive
   * @return window statistics
   */
  private WindowStatistics windowStatistics(Signal signal, double start, double end) {
    double sum = 0.0;
    int goodCount = 0;
    int totalCount = 0;
    for (Observation observation : signal.observations) {
      if (observation.time < start || observation.time > end) {
        continue;
      }
      totalCount++;
      if (isGood(observation.status) && !Double.isNaN(observation.value)) {
        sum += observation.value;
        goodCount++;
      }
    }
    double mean = goodCount > 0 ? sum / goodCount : Double.NaN;
    double goodFraction = totalCount > 0 ? (double) goodCount / totalCount : 0.0;
    return new WindowStatistics(mean, goodFraction, totalCount);
  }

  /**
   * Evaluates the intervention gate by confirming the setpoint moved as declared.
   *
   * @param effectByName signal effects keyed by name
   * @param gates gate list to append to
   * @param warnings warning list to append to
   * @return true when the intervention is confirmed by data
   */
  private boolean evaluateInterventionGate(Map<String, TrialAssessmentResult.SignalEffect> effectByName,
      List<TrialAssessmentResult.QualityGate> gates, List<String> warnings) {
    TrialAssessmentResult.SignalEffect setpointEffect = effectByName.get(intervention.setpointName);
    if (setpointEffect == null) {
      warnings.add("No signal named '" + intervention.setpointName
          + "' was supplied, so the intervention could not be confirmed from data");
      gates.add(new TrialAssessmentResult.QualityGate("intervention:" + intervention.setpointName, "INTERVENTION",
          false, "Setpoint signal not present in the supplied data"));
      return false;
    }
    double declaredChange = intervention.newValue - intervention.oldValue;
    double observedChange = setpointEffect.getDelta();
    double declaredMagnitude = Math.max(Math.abs(declaredChange), EPSILON);
    boolean directionOk = Double.isNaN(observedChange) ? false : declaredChange * observedChange >= 0.0;
    boolean magnitudeOk = Math.abs(observedChange) >= 0.5 * Math.abs(declaredChange);
    boolean passed = directionOk && magnitudeOk && Math.abs(declaredChange) > EPSILON;
    double agreement = Double.isNaN(observedChange) ? Double.NaN : observedChange / declaredMagnitude;
    String message = passed
        ? "Observed setpoint change " + observedChange + " is consistent with the declared change " + declaredChange
        : "Observed setpoint change " + observedChange + " does not match the declared change " + declaredChange
            + " (agreement fraction " + agreement + ")";
    gates.add(new TrialAssessmentResult.QualityGate("intervention:" + intervention.setpointName, "INTERVENTION", passed,
        message));
    return passed;
  }

  /**
   * Evaluates the per-signal data-quality gates.
   *
   * @param effects signal effects
   * @param gates gate list to append to
   * @return true when every signal meets the minimum good-data fraction
   */
  private boolean evaluateDataQualityGates(List<TrialAssessmentResult.SignalEffect> effects,
      List<TrialAssessmentResult.QualityGate> gates) {
    boolean allOk = true;
    for (TrialAssessmentResult.SignalEffect effect : effects) {
      boolean passed = effect.isDataQualityOk();
      allOk = allOk && passed;
      String message = "Good-data fraction pre=" + effect.getPreGoodFraction() + ", post="
          + effect.getPostGoodFraction() + ", required=" + minGoodDataFraction;
      gates.add(
          new TrialAssessmentResult.QualityGate("dataQuality:" + effect.getName(), "DATA_QUALITY", passed, message));
    }
    return allOk;
  }

  /**
   * Evaluates the confounder gates.
   *
   * @param effectByName signal effects keyed by name
   * @param gates gate list to append to
   * @param warnings warning list to append to
   * @return true when no confounder exceeds its allowed normalized change
   */
  private boolean evaluateConfounderGates(Map<String, TrialAssessmentResult.SignalEffect> effectByName,
      List<TrialAssessmentResult.QualityGate> gates, List<String> warnings) {
    boolean allOk = true;
    for (Confounder confounder : confounders) {
      TrialAssessmentResult.SignalEffect effect = effectByName.get(confounder.signalName);
      if (effect == null) {
        warnings.add("Confounder signal '" + confounder.signalName + "' was not supplied and could not be checked");
        gates.add(new TrialAssessmentResult.QualityGate("confounder:" + confounder.signalName, "CONFOUNDER", false,
            "Confounder signal not present in the supplied data"));
        allOk = false;
        continue;
      }
      double magnitude = Math.abs(effect.getNormalizedChange());
      boolean passed = !Double.isNaN(magnitude) && magnitude <= confounder.maxNormalizedChange;
      allOk = allOk && passed;
      String message = "Normalized change " + effect.getNormalizedChange() + " versus allowed "
          + confounder.maxNormalizedChange;
      gates.add(
          new TrialAssessmentResult.QualityGate("confounder:" + confounder.signalName, "CONFOUNDER", passed, message));
    }
    return allOk;
  }

  /**
   * Classifies a data-quality status string as good or bad.
   *
   * @param status status string, may be null
   * @return true when the status represents good quality
   */
  private static boolean isGood(String status) {
    if (status == null) {
      return true;
    }
    String trimmed = status.trim();
    if (trimmed.isEmpty()) {
      return true;
    }
    return trimmed.equalsIgnoreCase("good") || trimmed.equalsIgnoreCase("ok") || trimmed.equalsIgnoreCase("valid");
  }

  /**
   * Summary statistics for a signal inside a time window.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class WindowStatistics {
    private final double mean;
    private final double goodFraction;
    private final int totalCount;

    /**
     * Creates window statistics.
     *
     * @param mean mean of good samples, or NaN when no good samples exist
     * @param goodFraction fraction of good samples from 0 to 1
     * @param totalCount total samples in the window
     */
    private WindowStatistics(double mean, double goodFraction, int totalCount) {
      this.mean = mean;
      this.goodFraction = goodFraction;
      this.totalCount = totalCount;
    }
  }

  /**
   * A named signal with its observations.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class Signal implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private String unit;
    private final List<Observation> observations = new ArrayList<Observation>();

    /**
     * Creates a signal.
     *
     * @param name signal name
     * @param unit engineering unit, may be null
     */
    private Signal(String name, String unit) {
      this.name = name;
      this.unit = unit == null ? "" : unit;
    }
  }

  /**
   * A single time-stamped observation.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class Observation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double time;
    private final double value;
    private final String status;

    /**
     * Creates an observation.
     *
     * @param time observation time
     * @param value observed value
     * @param status data-quality status, may be null
     */
    private Observation(double time, double value, String status) {
      this.time = time;
      this.value = value;
      this.status = status;
    }
  }

  /**
   * The defined intervention for the trial.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class Intervention implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String setpointName;
    private final double oldValue;
    private final double newValue;
    private final double transitionTime;

    /**
     * Creates an intervention.
     *
     * @param setpointName setpoint signal name
     * @param oldValue setpoint value before the change
     * @param newValue setpoint value after the change
     * @param transitionTime time of the setpoint transition
     */
    private Intervention(String setpointName, double oldValue, double newValue, double transitionTime) {
      this.setpointName = setpointName;
      this.oldValue = oldValue;
      this.newValue = newValue;
      this.transitionTime = transitionTime;
    }
  }

  /**
   * A confounder gate definition.
   *
   * @author ESOL
   * @version 1.0
   */
  private static final class Confounder implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String signalName;
    private final double maxNormalizedChange;

    /**
     * Creates a confounder gate.
     *
     * @param signalName confounding signal name
     * @param maxNormalizedChange maximum allowed absolute normalized change
     */
    private Confounder(String signalName, double maxNormalizedChange) {
      this.signalName = signalName;
      this.maxNormalizedChange = maxNormalizedChange;
    }
  }
}
