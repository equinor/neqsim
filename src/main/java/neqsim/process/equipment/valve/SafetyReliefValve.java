package neqsim.process.equipment.valve;

import java.io.Serializable;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Safety/Relief Valve built on top of ThrottlingValve.
 * 
 * Features:
 *  - Set pressure, overpressure (relieving threshold), blowdown (reseat).
 *  - Opening laws: SNAP (pop) or MODULATING, with hysteresis.
 *  - Backpressure de-rating: Conventional, Balanced Bellows, Pilot Modulating.
 *  - Transient anti-chatter: first-order inertia, min open/close dwell, lift rate limit.
 *
 * Assumptions:
 *  - Pressures in bar(a) as typically used in NeqSim SystemInterface.
 *  - Cv mapping is linear w.r.t. opening; replace with vendor lift curve if available.
 */
public class SafetyReliefValve extends ThrottlingValve implements Serializable {

  private static final long serialVersionUID = 1L;

  public enum ValveType { CONVENTIONAL, BALANCED_BELLOWS, PILOT_MODULATING }
  public enum OpeningLaw { SNAP, MODULATING }

  // ---- PSV parameters (SI-ish; bar for pressure) ----
  private double setPressureBar = 10.0;     // set pressure (bara)
  private double overpressureFrac = 0.10;   // 10% overpressure => full lift
  private double blowdownFrac = 0.07;       // reseat at Pset*(1 - blowdown)
  private double ratedCv = 100.0;           // Cv at full lift (vendor)
  private double kd = 0.975;                // discharge/capacity factor (folded into Cv)
  private double kbMax = 1.0;               // capacity cap due to backpressure
  private double backpressureSensitivity = 0.15; // capacity penalty ~ f(P2/P1)
  private double minStableOpenFrac = 0.0;   // numerical floor when open (e.g., 0.02)

  private ValveType valveType = ValveType.CONVENTIONAL;
  private OpeningLaw openingLaw = OpeningLaw.SNAP;

  // ---- State & timers (hysteresis / anti-chatter) ----
  private double openFraction = 0.0;        // current lift 0..1
  private boolean wasOpenLastStep = false;
  private double timeSinceOpenSec = LARGE_TIME_SEC;
  private double timeSinceCloseSec = LARGE_TIME_SEC;

  // ---- Transient dynamics ----
  private double tauOpenSec = 0.15;         // opening time constant [s]
  private double tauCloseSec = 0.40;        // closing time constant [s]
  private double minOpenTimeSec = 0.50;     // minimum dwell when opened [s]
  private double minCloseTimeSec = 0.20;    // minimum dwell when closed [s]
  private double maxLiftRatePerSec = 3.0;   // |d(lift)/dt| limit [1/s]

  public SafetyReliefValve() { super("SafetyReliefValve"); }
  public SafetyReliefValve(String name, StreamInterface inletStream) { super(name, inletStream); }

  // ---------------- Getters / Setters ----------------
  public double getSetPressureBar() { return setPressureBar; }
  public void setSetPressureBar(double v) { setPressureBar = v; }

  public double getOverpressureFrac() { return overpressureFrac; }
  public void setOverpressureFrac(double v) { overpressureFrac = Math.max(0.0, v); }

  public double getBlowdownFrac() { return blowdownFrac; }
  public void setBlowdownFrac(double v) { blowdownFrac = Math.max(0.0, v); }

  public double getRatedCv() { return ratedCv; }
  public void setRatedCv(double v) { ratedCv = Math.max(0.0, v); }

  public double getKd() { return kd; }
  public void setKd(double v) { kd = Math.max(0.0, v); }

  public double getKbMax() { return kbMax; }
  public void setKbMax(double v) { kbMax = Math.max(0.0, v); }

  public double getBackpressureSensitivity() { return backpressureSensitivity; }
  public void setBackpressureSensitivity(double v) { backpressureSensitivity = Math.max(0.0, v); }

  public double getMinStableOpenFrac() { return minStableOpenFrac; }
  public void setMinStableOpenFrac(double v) { minStableOpenFrac = Math.max(0.0, Math.min(1.0, v)); }

  public ValveType getValveType() { return valveType; }
  public void setValveType(ValveType t) { valveType = t; }

  public OpeningLaw getOpeningLaw() { return openingLaw; }
  public void setOpeningLaw(OpeningLaw law) { openingLaw = law; }

  public double getOpenFraction() { return openFraction; }

  public void setTauOpenSec(double v){ tauOpenSec = Math.max(0.0, v); }
  public void setTauCloseSec(double v){ tauCloseSec = Math.max(0.0, v); }
  public void setMinOpenTimeSec(double v){ minOpenTimeSec = Math.max(0.0, v); }
  public void setMinCloseTimeSec(double v){ minCloseTimeSec = Math.max(0.0, v); }
  public void setMaxLiftRatePerSec(double v){ maxLiftRatePerSec = Math.max(0.0, v); }

  // ---------------- Internals ----------------
  private double relievingPressureBar() { return setPressureBar * (1.0 + overpressureFrac); }
  private double reseatPressureBar()    { return setPressureBar * (1.0 - blowdownFrac); }

  private double computeOpeningFraction(double pUpBar) {
    final double pRel = relievingPressureBar();
    final double pReseat = reseatPressureBar();

    double frac;
    switch (openingLaw) {
      case SNAP:
        if (pUpBar >= pRel) {
          frac = 1.0;
        } else if (wasOpenLastStep && pUpBar > pReseat) {
          // Ramp within band to reduce numerical jerk
          frac = (pUpBar - setPressureBar) / Math.max(DIVISOR_PROTECTION, (pRel - setPressureBar));
          frac = Math.max(frac, minStableOpenFrac);
        } else if (pUpBar <= pReseat) {
          frac = 0.0;
        } else {
          frac = 0.0; // not previously open → stay closed until pRel
        }
        break;

      case MODULATING:
      default:
        if (wasOpenLastStep && pUpBar <= pReseat) return 0.0;
        if (pUpBar <= setPressureBar) return 0.0;
        if (pUpBar >= pRel) return 1.0;
        frac = (pUpBar - setPressureBar) / Math.max(SMALL_DENOMINATOR, (pRel - setPressureBar));
        break;
    }

    return Math.max(0.0, Math.min(1.0, frac));
  }

  private double capacityBackpressureFactor(double pUpBar, double pDownBar) {
    if (pUpBar <= 0.0) return 1.0;
    double ratio = Math.max(0.0, Math.min(1.0, pDownBar / pUpBar));
    double kb;
    switch (valveType) {
      case CONVENTIONAL:
        kb = 1.0 - backpressureSensitivity * ratio;
        break;
      case BALANCED_BELLOWS:
        kb = 1.0 - 0.05 * backpressureSensitivity * ratio; // tiny effect
        break;
      case PILOT_MODULATING:
      default:
        kb = 1.0 - 0.5 * backpressureSensitivity * ratio;
        break;
    }
    kb = Math.max(0.0, Math.min(kbMax, kb));
    return kb;
  }

  private void applyOpeningToCv(double openFrac, double kb) {
    double effectiveCv = ratedCv * openFrac * kd * kb;
    if (effectiveCv <= 0.0) {
      setCv(0.0);
      setPercentValveOpening(0.0);
    } else {
      setCv(effectiveCv);
      setPercentValveOpening(Math.max(0.0, Math.min(100.0, openFrac * 100.0)));
    }
  }

  // ---------------- Steady-state ----------------
  @Override
  public void run() {
    StreamInterface in = getInletStream();
    StreamInterface out = getOutletStream();

    // Ensure pressure is in bar. If your build uses Pa, convert by dividing by 1e5.
    double pUpBar = ensureBar(in.getThermoSystem().getPressure());
    double pDownBar = out != null ? ensureBar(out.getThermoSystem().getPressure()) : 1.01325;

    double newCmd = computeOpeningFraction(pUpBar);
    if (newCmd > 0.0) newCmd = Math.max(newCmd, minStableOpenFrac);

    double kb = capacityBackpressureFactor(pUpBar, pDownBar);

    applyOpeningToCv(newCmd, kb);
    super.run();

    this.wasOpenLastStep = (newCmd > 0.0);
    this.openFraction = newCmd;
    // reset dwell timers heuristically in steady state
    this.timeSinceOpenSec = wasOpenLastStep ? INFINITE_TIME_SEC : 0.0;
    this.timeSinceCloseSec = wasOpenLastStep ? 0.0 : INFINITE_TIME_SEC;
  }

  // ---------------- Transient with inertia & anti-chatter ----------------
  @Override
  public void runTransient(double dt) {
    StreamInterface in = getInletStream();
    StreamInterface out = getOutletStream();

    double pUpBar = in.getThermoSystem().getPressure();
    double pDownBar = out != null ? out.getThermoSystem().getPressure() : ATM_PRESSURE_BAR;

    // Raw commanded lift from pressure + hysteresis
    double cmd = computeOpeningFraction(pUpBar);

    // Dwell windows
    boolean currentlyOpen = (openFraction > 0.0);
    if (currentlyOpen) {
      if (timeSinceOpenSec < minOpenTimeSec) {
        cmd = Math.max(cmd, Math.max(minStableOpenFrac, 1e-6));
      }
    } else {
      if (timeSinceCloseSec < minCloseTimeSec) {
        cmd = 0.0;
      }
    }

    // Backpressure de-rating
    double kb = capacityBackpressureFactor(pUpBar, pDownBar);

    // First-order lag
    double tau = (cmd >= openFraction) ? tauOpenSec : tauCloseSec;
    double alpha = (tau <= MIN_TIME_CONSTANT_SEC) ? 1.0 : (1.0 - Math.exp(-dt / tau));
    double liftProposed = openFraction + alpha * (cmd - openFraction);

    // Rate limit
    if (maxLiftRatePerSec > 0.0) {
      double maxDelta = maxLiftRatePerSec * dt;
      double delta = liftProposed - openFraction;
      if (Math.abs(delta) > maxDelta) {
        liftProposed = openFraction + Math.copySign(maxDelta, delta);
      }
    }

    // Clip and minimum stable opening if > 0
    liftProposed = Math.max(0.0, Math.min(1.0, liftProposed));
    if (liftProposed > 0.0) liftProposed = Math.max(liftProposed, minStableOpenFrac);

    // Update Cv and run hydraulics
    applyOpeningToCv(liftProposed, kb);
    super.runTransient(dt);

    // Timers & state
    boolean newOpen = (liftProposed > 0.0);
    if (newOpen) {
      timeSinceOpenSec += dt;
      if (!wasOpenLastStep) timeSinceOpenSec = 0.0;
      timeSinceCloseSec = 0.0;
    } else {
      timeSinceCloseSec += dt;
      if (wasOpenLastStep) timeSinceCloseSec = 0.0;
      timeSinceOpenSec = 0.0;
    }
    wasOpenLastStep = newOpen;
    openFraction = liftProposed;
  }

  // ---------------- Quick configurators ----------------
  public SafetyReliefValve configureConventionalSnap(double psetBar, double overFrac, double blowFrac, double cvRated) {
    setValveType(ValveType.CONVENTIONAL);
    setOpeningLaw(OpeningLaw.SNAP);
    setSetPressureBar(psetBar);
    setOverpressureFrac(overFrac);
    setBlowdownFrac(blowFrac);
    setRatedCv(cvRated);
    setMinStableOpenFrac(0.03);
    setBackpressureSensitivity(0.15);
    return this;
  }

  public SafetyReliefValve configureBalancedModulating(double psetBar, double overFrac, double blowFrac, double cvRated) {
    setValveType(ValveType.BALANCED_BELLOWS);
    setOpeningLaw(OpeningLaw.MODULATING);
    setSetPressureBar(psetBar);
    setOverpressureFrac(overFrac);
    setBlowdownFrac(blowFrac);
    setRatedCv(cvRated);
    setMinStableOpenFrac(0.02);
    setKbMax(1.0);
    setBackpressureSensitivity(0.02);
    return this;
  }

  // Handy monitors
  public double getRelievingPressureBar() { return relievingPressureBar(); }
  public double getReseatPressureBar() { return reseatPressureBar(); }
}

