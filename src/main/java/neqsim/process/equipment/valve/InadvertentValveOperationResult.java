package neqsim.process.equipment.valve;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.GsonBuilder;

/**
 * Result of an {@link InadvertentValveOperationAnalyzer} analysis.
 *
 * <p>
 * Captures the diagnosed inadvertent valve operation (IVO) scenario per API 521 §4.4.13 and NORSOK
 * P-002: the assumed valve role, the failure mode considered, the consequence severity and the
 * initiating-event frequency used to drive downstream LOPA / SIL studies.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class InadvertentValveOperationResult implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Functional role of the valve in the process. Used by the analyser to decide which IVO modes are
   * credible and which consequences to evaluate.
   */
  public enum ValveRole {
    /** Manual or motor-operated block / isolation valve. */
    BLOCK,
    /** Modulating control valve in a regulatory loop. */
    CONTROL,
    /** Bypass around a control valve or piece of equipment. */
    BYPASS,
    /** Check valve preventing reverse flow. */
    CHECK,
    /** Isolation valve in a relief path (upstream/downstream of a PSV). */
    PSV_ISOLATION,
    /** Emergency shutdown valve (ESDV / SDV). */
    ESD,
    /** Blowdown / depressurisation valve (BDV). */
    BLOWDOWN
  }

  /**
   * Inadvertent operation mode considered.
   */
  public enum IvoMode {
    /** Valve opens unexpectedly (operator error, spurious signal, actuator failure open). */
    SPURIOUS_OPEN,
    /** Valve closes unexpectedly (loss of instrument air on fail-close, spurious trip). */
    SPURIOUS_CLOSE,
    /** Valve fails to close on demand (stuck in open position). */
    STUCK_OPEN,
    /** Valve fails to open on demand (stuck in closed position). */
    STUCK_CLOSED,
    /** Valve only partially strokes (mechanical wear, hydrate / debris obstruction). */
    PARTIAL_STROKE
  }

  /**
   * Consequence severity classification per API 521 acceptance limits.
   */
  public enum ConsequenceSeverity {
    /** No credible consequence — protected by existing barriers. */
    NONE,
    /** Off-spec product, process upset; no safety impact. */
    MINOR,
    /** Pressure exceeds MAWP but stays below the API 521 accumulation limit. */
    MAJOR,
    /** Pressure exceeds the API 521 accumulation limit, or loss of safety function. */
    SAFETY_CRITICAL
  }

  private String valveName = "";
  private ValveRole role = ValveRole.BLOCK;
  private IvoMode mode = IvoMode.SPURIOUS_CLOSE;
  private ConsequenceSeverity severity = ConsequenceSeverity.NONE;
  private double frequencyPerYear = Double.NaN;
  private double upstreamPressureBara = Double.NaN;
  private double downstreamPressureBara = Double.NaN;
  private double designPressureBara = Double.NaN;
  private double overpressureFactor = Double.NaN;
  private boolean blockedOutlet = false;
  private boolean reverseFlowRisk = false;
  private boolean lossOfReliefPath = false;
  private boolean failureToIsolateOnDemand = false;
  private String description = "";
  private final List<String> recommendations = new ArrayList<>();

  /** Default constructor. */
  public InadvertentValveOperationResult() {}

  /**
   * @return valve tag / name
   */
  public String getValveName() {
    return valveName;
  }

  /**
   * @param name valve tag / name
   */
  public void setValveName(String name) {
    this.valveName = name;
  }

  /**
   * @return functional role assumed for the valve
   */
  public ValveRole getRole() {
    return role;
  }

  /**
   * @param role valve role
   */
  public void setRole(ValveRole role) {
    this.role = role;
  }

  /**
   * @return inadvertent operation mode considered
   */
  public IvoMode getMode() {
    return mode;
  }

  /**
   * @param mode inadvertent operation mode
   */
  public void setMode(IvoMode mode) {
    this.mode = mode;
  }

  /**
   * @return diagnosed consequence severity
   */
  public ConsequenceSeverity getSeverity() {
    return severity;
  }

  /**
   * @param severity consequence severity
   */
  public void setSeverity(ConsequenceSeverity severity) {
    this.severity = severity;
  }

  /**
   * @return initiating-event frequency in events per year
   */
  public double getFrequencyPerYear() {
    return frequencyPerYear;
  }

  /**
   * @param f initiating-event frequency in events per year
   */
  public void setFrequencyPerYear(double f) {
    this.frequencyPerYear = f;
  }

  /**
   * @return upstream pressure in bara at the time of analysis
   */
  public double getUpstreamPressureBara() {
    return upstreamPressureBara;
  }

  /**
   * @param p upstream pressure in bara
   */
  public void setUpstreamPressureBara(double p) {
    this.upstreamPressureBara = p;
  }

  /**
   * @return downstream pressure in bara at the time of analysis
   */
  public double getDownstreamPressureBara() {
    return downstreamPressureBara;
  }

  /**
   * @param p downstream pressure in bara
   */
  public void setDownstreamPressureBara(double p) {
    this.downstreamPressureBara = p;
  }

  /**
   * @return design pressure (MAWP) of the protected segment in bara
   */
  public double getDesignPressureBara() {
    return designPressureBara;
  }

  /**
   * @param p design pressure (MAWP) in bara
   */
  public void setDesignPressureBara(double p) {
    this.designPressureBara = p;
  }

  /**
   * @return ratio of credible peak pressure to design pressure (1.0 = at MAWP)
   */
  public double getOverpressureFactor() {
    return overpressureFactor;
  }

  /**
   * @param f overpressure factor
   */
  public void setOverpressureFactor(double f) {
    this.overpressureFactor = f;
  }

  /**
   * @return true if the IVO leaves the protected segment with no flow outlet
   */
  public boolean isBlockedOutlet() {
    return blockedOutlet;
  }

  /**
   * @param b blocked-outlet flag
   */
  public void setBlockedOutlet(boolean b) {
    this.blockedOutlet = b;
  }

  /**
   * @return true if the IVO can drive reverse flow into the upstream segment
   */
  public boolean isReverseFlowRisk() {
    return reverseFlowRisk;
  }

  /**
   * @param b reverse-flow risk flag
   */
  public void setReverseFlowRisk(boolean b) {
    this.reverseFlowRisk = b;
  }

  /**
   * @return true if the IVO disables a relief path (e.g. PSV isolation valve closed)
   */
  public boolean isLossOfReliefPath() {
    return lossOfReliefPath;
  }

  /**
   * @param b loss-of-relief-path flag
   */
  public void setLossOfReliefPath(boolean b) {
    this.lossOfReliefPath = b;
  }

  /**
   * @return true if the IVO defeats a required isolation function on demand
   */
  public boolean isFailureToIsolateOnDemand() {
    return failureToIsolateOnDemand;
  }

  /**
   * @param b failure-to-isolate-on-demand flag
   */
  public void setFailureToIsolateOnDemand(boolean b) {
    this.failureToIsolateOnDemand = b;
  }

  /**
   * @return human-readable scenario description
   */
  public String getDescription() {
    return description;
  }

  /**
   * @param d scenario description
   */
  public void setDescription(String d) {
    this.description = d;
  }

  /**
   * @return mutable list of recommended barriers / safeguards
   */
  public List<String> getRecommendations() {
    return recommendations;
  }

  /**
   * Add a recommendation to the result.
   *
   * @param rec recommendation text
   */
  public void addRecommendation(String rec) {
    if (rec != null && !rec.trim().isEmpty()) {
      recommendations.add(rec);
    }
  }

  /**
   * Serialise the result to a JSON string.
   *
   * @return JSON representation of the result
   */
  public String toJson() {
    return new GsonBuilder().setPrettyPrinting().serializeSpecialFloatingPointValues().create()
        .toJson(this);
  }
}
