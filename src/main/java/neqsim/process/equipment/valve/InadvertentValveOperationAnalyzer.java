package neqsim.process.equipment.valve;

import neqsim.process.equipment.valve.InadvertentValveOperationResult.ConsequenceSeverity;
import neqsim.process.equipment.valve.InadvertentValveOperationResult.IvoMode;
import neqsim.process.equipment.valve.InadvertentValveOperationResult.ValveRole;
import neqsim.thermo.system.SystemInterface;

/**
 * Diagnoses inadvertent valve operation (IVO) scenarios on a {@link ValveInterface} per API 521
 * §4.4.13 and NORSOK P-002 §5.5.
 *
 * <p>
 * The analyser takes a configured valve, a functional role and a failure mode, then evaluates the
 * credible consequence (overpressure of the upstream / downstream segment, blocked outlet, loss of
 * relief path, failure to isolate on demand, reverse flow). Initiating-event frequencies default to
 * typical screening values from IOGP Report 434-9 / OREDA and can be overridden.
 * </p>
 *
 * <p>
 * The class is deliberately conservative — it screens for credible IVO scenarios and produces
 * recommendations (PSV, HIPPS, double block and bleed, key-locked open, mechanical interlock,
 * position monitoring). It does <em>not</em> compute relief loads — feed the resulting
 * frequency-tagged scenario into {@code runRelief} or LOPA for that.
 * </p>
 *
 * @author esol
 * @version 1.0
 */
public class InadvertentValveOperationAnalyzer {
  /** Default initiating-event frequency for spurious open / close of a power-actuated valve. */
  public static final double DEFAULT_SPURIOUS_FREQUENCY_PER_YEAR = 1.0e-1;
  /** Default failure-on-demand frequency for stuck valve modes. */
  public static final double DEFAULT_STUCK_FREQUENCY_PER_YEAR = 1.0e-2;

  /** API 521 §5.4.2 maximum accumulation for non-fire single-jeopardy scenarios. */
  public static final double API521_MAX_ACCUMULATION_FACTOR = 1.10;
  /** API 521 maximum accumulation for fire-case / multiple jeopardy. */
  public static final double API521_FIRE_ACCUMULATION_FACTOR = 1.21;

  private final ValveInterface valve;
  private ValveRole role = ValveRole.BLOCK;
  private IvoMode mode = IvoMode.SPURIOUS_CLOSE;
  private double designPressureBara = Double.NaN;
  private double downstreamDesignPressureBara = Double.NaN;
  private double frequencyPerYear = Double.NaN;
  private double upstreamPressureOverrideBara = Double.NaN;

  /**
   * Construct an analyser for the given valve.
   *
   * @param valve the valve to analyse; must have been run at least once
   */
  public InadvertentValveOperationAnalyzer(ValveInterface valve) {
    if (valve == null) {
      throw new IllegalArgumentException("valve must not be null");
    }
    this.valve = valve;
  }

  /**
   * Set the functional role of the valve.
   *
   * @param role valve role
   * @return this analyser (for chaining)
   */
  public InadvertentValveOperationAnalyzer setRole(ValveRole role) {
    if (role == null) {
      throw new IllegalArgumentException("role must not be null");
    }
    this.role = role;
    return this;
  }

  /**
   * Set the inadvertent operation mode to evaluate.
   *
   * @param mode IVO mode
   * @return this analyser (for chaining)
   */
  public InadvertentValveOperationAnalyzer setMode(IvoMode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    this.mode = mode;
    return this;
  }

  /**
   * Set the design pressure (MAWP) of the segment that becomes exposed by the IVO.
   *
   * @param pressure design pressure value
   * @param unit unit (only {@code "bara"} is supported)
   * @return this analyser (for chaining)
   */
  public InadvertentValveOperationAnalyzer setDesignPressure(double pressure, String unit) {
    if (!"bara".equalsIgnoreCase(unit)) {
      throw new IllegalArgumentException("Only 'bara' is supported, got: " + unit);
    }
    this.designPressureBara = pressure;
    return this;
  }

  /**
   * Set the design pressure of the downstream segment (for SPURIOUS_OPEN scenarios where a
   * high-pressure source is connected to a lower-pressure system).
   *
   * @param pressure downstream design pressure value
   * @param unit unit (only {@code "bara"} is supported)
   * @return this analyser (for chaining)
   */
  public InadvertentValveOperationAnalyzer setDownstreamDesignPressure(double pressure,
      String unit) {
    if (!"bara".equalsIgnoreCase(unit)) {
      throw new IllegalArgumentException("Only 'bara' is supported, got: " + unit);
    }
    this.downstreamDesignPressureBara = pressure;
    return this;
  }

  /**
   * Override the default initiating-event frequency.
   *
   * @param frequency events per year
   * @return this analyser (for chaining)
   */
  public InadvertentValveOperationAnalyzer setFrequencyPerYear(double frequency) {
    if (frequency < 0.0 || Double.isNaN(frequency)) {
      throw new IllegalArgumentException("frequency must be a non-negative finite number");
    }
    this.frequencyPerYear = frequency;
    return this;
  }

  /**
   * Override the upstream source pressure (e.g. when the valve has not been simulated with the IVO
   * upset condition).
   *
   * @param pressure upstream pressure value
   * @param unit unit (only {@code "bara"} is supported)
   * @return this analyser (for chaining)
   */
  public InadvertentValveOperationAnalyzer setUpstreamPressure(double pressure, String unit) {
    if (!"bara".equalsIgnoreCase(unit)) {
      throw new IllegalArgumentException("Only 'bara' is supported, got: " + unit);
    }
    this.upstreamPressureOverrideBara = pressure;
    return this;
  }

  /**
   * Run the IVO analysis.
   *
   * @return populated {@link InadvertentValveOperationResult}
   */
  public InadvertentValveOperationResult analyze() {
    InadvertentValveOperationResult r = new InadvertentValveOperationResult();
    r.setValveName(valve.getName());
    r.setRole(role);
    r.setMode(mode);

    double p1 = readUpstreamPressureBara();
    double p2 = readDownstreamPressureBara();
    r.setUpstreamPressureBara(p1);
    r.setDownstreamPressureBara(p2);
    r.setDesignPressureBara(designPressureBara);
    r.setFrequencyPerYear(resolveFrequency());

    classify(r, p1, p2);
    addBaselineRecommendations(r);
    return r;
  }

  // ── private helpers ──────────────────────────────────────────────────────

  private double readUpstreamPressureBara() {
    if (!Double.isNaN(upstreamPressureOverrideBara)) {
      return upstreamPressureOverrideBara;
    }
    SystemInterface sys = valve.getInletStream().getThermoSystem();
    return sys.getPressure("bara");
  }

  private double readDownstreamPressureBara() {
    SystemInterface sys = valve.getOutletStream().getThermoSystem();
    return sys.getPressure("bara");
  }

  private double resolveFrequency() {
    if (!Double.isNaN(frequencyPerYear)) {
      return frequencyPerYear;
    }
    switch (mode) {
      case SPURIOUS_OPEN:
      case SPURIOUS_CLOSE:
        return DEFAULT_SPURIOUS_FREQUENCY_PER_YEAR;
      case STUCK_OPEN:
      case STUCK_CLOSED:
      case PARTIAL_STROKE:
      default:
        return DEFAULT_STUCK_FREQUENCY_PER_YEAR;
    }
  }

  private void classify(InadvertentValveOperationResult r, double p1, double p2) {
    double designP = Double.isNaN(downstreamDesignPressureBara) ? designPressureBara
        : downstreamDesignPressureBara;

    switch (mode) {
      case SPURIOUS_OPEN:
        classifySpuriousOpen(r, p1, p2, designP);
        break;
      case SPURIOUS_CLOSE:
      case STUCK_CLOSED:
        classifyClosure(r, p1, designPressureBara);
        break;
      case STUCK_OPEN:
        classifyStuckOpen(r, p1, p2);
        break;
      case PARTIAL_STROKE:
      default:
        classifyPartialStroke(r);
        break;
    }
  }

  private void classifySpuriousOpen(InadvertentValveOperationResult r, double p1, double p2,
      double designP) {
    if (role == ValveRole.PSV_ISOLATION) {
      // Spurious open of a PSV isolation valve does NOT defeat protection — the PSV can still
      // relieve. The dangerous mode for PSV_ISOLATION is the closed direction.
      r.setSeverity(ConsequenceSeverity.NONE);
      r.setDescription("Spurious open of PSV isolation valve — relief path remains available.");
      return;
    }
    boolean highToLow = !Double.isNaN(designP) && p1 > designP;
    if (highToLow) {
      double factor = p1 / designP;
      r.setOverpressureFactor(factor);
      r.setSeverity(severityFromOverpressure(factor));
      r.setDescription(String.format(java.util.Locale.ROOT,
          "Spurious opening of %s connects %.1f bara source to %.1f bara design segment "
              + "(overpressure factor %.2f)",
          role, p1, designP, factor));
      return;
    }
    if (role == ValveRole.BLOWDOWN || role == ValveRole.ESD) {
      r.setSeverity(ConsequenceSeverity.MINOR);
      r.setDescription(
          "Spurious opening of " + role + " — production loss / flare load, no overpressure.");
      return;
    }
    r.setSeverity(ConsequenceSeverity.MINOR);
    r.setDescription("Spurious opening — process upset (off-spec / bypass) without overpressure.");
  }

  private void classifyClosure(InadvertentValveOperationResult r, double p1, double designP) {
    if (role == ValveRole.PSV_ISOLATION) {
      r.setLossOfReliefPath(true);
      r.setSeverity(ConsequenceSeverity.SAFETY_CRITICAL);
      r.setDescription(
          "PSV isolation valve closed inadvertently — relief path defeated, overpressure"
              + " protection lost on next demand.");
      return;
    }
    if (role == ValveRole.CHECK) {
      r.setSeverity(ConsequenceSeverity.MINOR);
      r.setDescription("Check valve stuck closed — flow stopped, no overpressure risk.");
      return;
    }
    // Block, control, bypass closed → blocked outlet on upstream segment
    r.setBlockedOutlet(true);
    if (!Double.isNaN(designP) && p1 > 0.0) {
      double factor = p1 / designP;
      r.setOverpressureFactor(factor);
      r.setSeverity(severityFromOverpressure(factor));
    } else {
      r.setSeverity(ConsequenceSeverity.MAJOR);
    }
    r.setDescription(
        "Inadvertent closure creates blocked outlet — credible overpressure of upstream segment"
            + " unless a relief path or HIPPS is provided.");
  }

  private void classifyStuckOpen(InadvertentValveOperationResult r, double p1, double p2) {
    if (role == ValveRole.CHECK) {
      r.setReverseFlowRisk(true);
      r.setSeverity(ConsequenceSeverity.MAJOR);
      r.setDescription(
          "Check valve stuck open — reverse flow possible if downstream pressure exceeds"
              + " upstream (currently p2=" + p2 + " bara, p1=" + p1 + " bara).");
      return;
    }
    if (role == ValveRole.ESD || role == ValveRole.BLOWDOWN || role == ValveRole.PSV_ISOLATION) {
      r.setFailureToIsolateOnDemand(role != ValveRole.PSV_ISOLATION);
      r.setSeverity(ConsequenceSeverity.SAFETY_CRITICAL);
      r.setDescription(role + " fails to close on demand — required safety function unavailable.");
      return;
    }
    r.setSeverity(ConsequenceSeverity.MINOR);
    r.setDescription("Valve stuck open — loss of regulation / isolation, no immediate hazard.");
  }

  private void classifyPartialStroke(InadvertentValveOperationResult r) {
    r.setSeverity(ConsequenceSeverity.MINOR);
    r.setDescription("Partial stroke detected — degraded control / isolation; investigate"
        + " mechanical condition (hydrate, debris, wear).");
  }

  private ConsequenceSeverity severityFromOverpressure(double factor) {
    if (factor > API521_FIRE_ACCUMULATION_FACTOR) {
      return ConsequenceSeverity.SAFETY_CRITICAL;
    }
    if (factor > API521_MAX_ACCUMULATION_FACTOR) {
      return ConsequenceSeverity.MAJOR;
    }
    if (factor > 1.0) {
      return ConsequenceSeverity.MINOR;
    }
    return ConsequenceSeverity.NONE;
  }

  private void addBaselineRecommendations(InadvertentValveOperationResult r) {
    if (r.getSeverity() == ConsequenceSeverity.SAFETY_CRITICAL) {
      r.addRecommendation("Provide an independent protection layer (PSV, HIPPS, or SIL-rated trip) "
          + "and verify SIL per IEC 61511.");
    }
    if (r.isBlockedOutlet()) {
      r.addRecommendation("Confirm relief path (PSV) is sized for blocked-outlet contingency"
          + " per API 521 §4.4.2.");
    }
    if (r.isLossOfReliefPath()) {
      r.addRecommendation("Use car-seal-open (CSO) or key-locked-open (LO) administrative"
          + " controls on PSV isolation valves, with full bore matching the PSV inlet.");
      r.addRecommendation("Consider mechanical interlock (Castell / Smith) between PSV inlet"
          + " and outlet isolation valves.");
    }
    if (r.isReverseFlowRisk()) {
      r.addRecommendation(
          "Provide dual check valves in series (API 14C §A.7) or motor-operated isolation.");
    }
    if (r.isFailureToIsolateOnDemand()) {
      r.addRecommendation(
          "Implement partial-stroke testing and full-stroke proof testing per IEC 61511.");
    }
    if (role == ValveRole.BYPASS && (mode == IvoMode.SPURIOUS_OPEN || mode == IvoMode.STUCK_OPEN)) {
      r.addRecommendation(
          "Apply car-seal-closed (CSC) administrative control on the bypass valve.");
    }
    if (r.getRecommendations().isEmpty()) {
      r.addRecommendation("Document scenario in HAZOP / LOPA; existing barriers appear adequate.");
    }
  }
}
