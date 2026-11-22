package neqsim.process.equipment.valve;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.mechanicaldesign.valve.SafetyValveMechanicalDesign;

/**
 * Safety/Relief Valve built on top of ThrottlingValve.
 *
 * <p>
 * Features: - Set pressure, overpressure (relieving threshold), blowdown (reseat). - Opening laws: SNAP (pop) or
 * MODULATING, with hysteresis. - Backpressure de-rating: Conventional, Balanced Bellows, Pilot Modulating. - Transient
 * anti-chatter: first-order inertia, min open/close dwell, lift rate limit.
 *
 * Assumptions: - Pressures in bar(a) as typically used in NeqSim SystemInterface. - Cv mapping is linear w.r.t.
 * opening; replace with vendor lift curve if available.
 * </p>
 *
 * <p>
 * SNAP now commands opening at set pressure and retains demand until reseat. MODULATING uses unlatched set-to-full and
 * latched reseat-to-full linear curves. These are synthetic pressure/lift laws, not vendor certified lift or
 * rated-capacity curves. UUID and convenience entry points share the same implementation; lift is applied once by the
 * configured parent valve characteristic.
 * </p>
 *
 * @author Even Solbraa
 * @version 2.0
 */
public class SafetyReliefValve extends ThrottlingValve {

  private static final long serialVersionUID = 1L;
  private static final double EPS = 1e-9;
  private static final double LARGE_TIME_SEC = 1e9;
  private static final double ATM_PRESSURE_BAR = 1.01325;
  private int reliefResponseSchemaVersion = 1;
  private double seatToleranceFraction = 1.0e-8;
  private boolean liftMotionAvailable = true;
  private double initialLiftFraction;
  private double responseElapsedSeconds;
  private double lastCommandedLiftFraction;
  private double lastBackpressureFactor = 1.0;
  private UUID lastResponseStepId;
  private long openingCycleCount;
  private long reseatCycleCount;

  /** Synthetic backpressure-correction model families, not certified valve ratings. */
  public enum ValveType {
    CONVENTIONAL, BALANCED_BELLOWS, PILOT_MODULATING
  }

  /** Declared idealized pressure-to-command laws. */
  public enum OpeningLaw {
    SNAP, MODULATING
  }

  // ---- PSV parameters (SI-ish; bar for pressure) ----
  private double setPressureBar = 10.0; // set pressure (bara)
  private double overpressureFrac = 0.10; // 10% overpressure => full lift
  private double blowdownFrac = 0.07; // reseat at Pset*(1 - blowdown)
  private double ratedCv = 100.0; // Cv at full lift (vendor)
  private double kd = 0.975; // discharge/capacity factor (folded into Cv)
  private double kbMax = 1.0; // capacity cap due to backpressure
  private double backpressureSensitivity = 0.15; // capacity penalty ~ f(P2/P1)
  private double minStableOpenFrac = 0.0; // numerical floor when open (e.g., 0.02)

  private ValveType valveType = ValveType.CONVENTIONAL;
  private OpeningLaw openingLaw = OpeningLaw.SNAP;

  // ---- State & timers (hysteresis / anti-chatter) ----
  private double openFraction = 0.0; // current lift 0..1
  private boolean wasOpenLastStep = false;
  private double timeSinceOpenSec = LARGE_TIME_SEC;
  private double timeSinceCloseSec = LARGE_TIME_SEC;

  // ---- Transient dynamics ----
  private double tauOpenSec = 0.15; // opening time constant [s]
  private double tauCloseSec = 0.40; // closing time constant [s]
  private double minOpenTimeSec = 0.50; // minimum dwell when opened [s]
  private double minCloseTimeSec = 0.20; // minimum dwell when closed [s]
  private double maxLiftRatePerSec = 3.0; // |d(lift)/dt| limit [1/s]

  /**
   * Constructor for SafetyReliefValve.
   */
  public SafetyReliefValve() {
    this("SafetyReliefValve");
  }

  /**
   * Constructor for a named SafetyReliefValve that can be wired after creation.
   *
   * @param name name of the safety relief valve
   */
  public SafetyReliefValve(String name) {
    super(name);
    valveMechanicalDesign = new SafetyValveMechanicalDesign(this);
  }

  /**
   * Constructor for SafetyReliefValve.
   *
   *
   * @param name a {@link java.lang.String} object
   *
   * @param inletStream a {@link neqsim.process.equipment.stream.StreamInterface} object
   */
  public SafetyReliefValve(String name, StreamInterface inletStream) {
    super(name, inletStream);
    valveMechanicalDesign = new SafetyValveMechanicalDesign(this);
  }

  /** {@inheritDoc} */
  @Override
  public void initMechanicalDesign() {
    valveMechanicalDesign = new SafetyValveMechanicalDesign(this);
  }

  // ---------------- Getters / Setters ----------------
  /**
   * Getter for the field <code>setPressureBar</code>.
   *
   * @return a double
   */
  public double getSetPressureBar() {
    return setPressureBar;
  }

  /**
   * Setter for the field <code>setPressureBar</code>.
   *
   * @param v a double
   */
  public void setSetPressureBar(double v) {
    setPressureBar = positive(v, "setPressureBar");
  }

  /**
   * Getter for the field <code>overpressureFrac</code>.
   *
   * @return a double
   */
  public double getOverpressureFrac() {
    return overpressureFrac;
  }

  /**
   * Setter for the field <code>overpressureFrac</code>.
   *
   * @param v a double
   */
  public void setOverpressureFrac(double v) {
    overpressureFrac = nonNegative(v, "overpressureFrac");
  }

  /**
   * Getter for the field <code>blowdownFrac</code>.
   *
   * @return a double
   */
  public double getBlowdownFrac() {
    return blowdownFrac;
  }

  /**
   * Setter for the field <code>blowdownFrac</code>.
   *
   * @param v a double
   */
  public void setBlowdownFrac(double v) {
    blowdownFrac = fraction(v, "blowdownFrac", false);
  }

  /**
   * Getter for the field <code>ratedCv</code>.
   *
   * @return a double
   */
  public double getRatedCv() {
    return ratedCv;
  }

  /**
   * Setter for the field <code>ratedCv</code>.
   *
   * @param v a double
   */
  public void setRatedCv(double v) {
    ratedCv = nonNegative(v, "ratedCv");
  }

  /**
   * Getter for the field <code>kd</code>.
   *
   * @return a double
   */
  public double getKd() {
    return kd;
  }

  /**
   * Setter for the field <code>kd</code>.
   *
   * @param v a double
   */
  public void setKd(double v) {
    kd = fraction(v, "kd", true);
  }

  /**
   * Getter for the field <code>kbMax</code>.
   *
   * @return a double
   */
  public double getKbMax() {
    return kbMax;
  }

  /**
   * Setter for the field <code>kbMax</code>.
   *
   * @param v a double
   */
  public void setKbMax(double v) {
    kbMax = fraction(v, "kbMax", true);
  }

  /**
   * Getter for the field <code>backpressureSensitivity</code>.
   *
   * @return a double
   */
  public double getBackpressureSensitivity() {
    return backpressureSensitivity;
  }

  /**
   * Setter for the field <code>backpressureSensitivity</code>.
   *
   * @param v a double
   */
  public void setBackpressureSensitivity(double v) {
    backpressureSensitivity = nonNegative(v, "backpressureSensitivity");
  }

  /**
   * Getter for the field <code>minStableOpenFrac</code>.
   *
   * @return a double
   */
  public double getMinStableOpenFrac() {
    return minStableOpenFrac;
  }

  /**
   * Setter for the field <code>minStableOpenFrac</code>.
   *
   * @param v a double
   */
  public void setMinStableOpenFrac(double v) {
    minStableOpenFrac = fraction(v, "minStableOpenFrac", true);
  }

  /**
   * Getter for the field <code>valveType</code>.
   *
   * @return a {@link neqsim.process.equipment.valve.SafetyReliefValve.ValveType} object
   */
  public ValveType getValveType() {
    return valveType;
  }

  /**
   * Setter for the field <code>valveType</code>.
   *
   * @param t a {@link neqsim.process.equipment.valve.SafetyReliefValve.ValveType} object
   */
  public void setValveType(ValveType t) {
    valveType = Objects.requireNonNull(t, "valveType");
  }

  /**
   * Getter for the field <code>openingLaw</code>.
   *
   * @return a {@link neqsim.process.equipment.valve.SafetyReliefValve.OpeningLaw} object
   */
  public OpeningLaw getOpeningLaw() {
    return openingLaw;
  }

  /**
   * Setter for the field <code>openingLaw</code>.
   *
   * @param law a {@link neqsim.process.equipment.valve.SafetyReliefValve.OpeningLaw} object
   */
  public void setOpeningLaw(OpeningLaw law) {
    openingLaw = Objects.requireNonNull(law, "openingLaw");
  }

  /**
   * Getter for the field <code>openFraction</code>.
   *
   * @return a double
   */
  public double getOpenFraction() {
    return openFraction;
  }

  /**
   * Setter for the field <code>tauOpenSec</code>.
   *
   * @param v a double
   */
  public void setTauOpenSec(double v) {
    tauOpenSec = nonNegative(v, "tauOpenSec");
  }

  /**
   * Setter for the field <code>tauCloseSec</code>.
   *
   * @param v a double
   */
  public void setTauCloseSec(double v) {
    tauCloseSec = nonNegative(v, "tauCloseSec");
  }

  /**
   * Setter for the field <code>minOpenTimeSec</code>.
   *
   * @param v a double
   */
  public void setMinOpenTimeSec(double v) {
    minOpenTimeSec = nonNegative(v, "minOpenTimeSec");
  }

  /**
   * Setter for the field <code>minCloseTimeSec</code>.
   *
   * @param v a double
   */
  public void setMinCloseTimeSec(double v) {
    minCloseTimeSec = nonNegative(v, "minCloseTimeSec");
  }

  /**
   * Setter for the field <code>maxLiftRatePerSec</code>.
   *
   * @param v a double
   */
  public void setMaxLiftRatePerSec(double v) {
    maxLiftRatePerSec = nonNegative(v, "maxLiftRatePerSec");
  }

  /** @return opening first-order time constant, seconds */
  public double getTauOpenSec() {
    return tauOpenSec;
  }

  /** @return closing first-order time constant, seconds */
  public double getTauCloseSec() {
    return tauCloseSec;
  }

  /** @return minimum physical-open dwell, seconds */
  public double getMinOpenTimeSec() {
    return minOpenTimeSec;
  }

  /** @return minimum physical-closed dwell, seconds */
  public double getMinCloseTimeSec() {
    return minCloseTimeSec;
  }

  /** @return maximum lift change per second; zero disables the rate limit */
  public double getMaxLiftRatePerSec() {
    return maxLiftRatePerSec;
  }

  /** @return dimensionless declared terminal seating cutoff */
  public double getSeatToleranceFraction() {
    return seatToleranceFraction;
  }

  /** @param value finite terminal lift cutoff in [0,1) */
  public void setSeatToleranceFraction(double value) {
    seatToleranceFraction = fraction(value, "seatToleranceFraction", false);
  }

  /** @return whether the mechanical lift is allowed to move */
  public boolean isLiftMotionAvailable() {
    return liftMotionAvailable;
  }

  /** @param available false freezes actual lift, for an explicitly declared mechanical fault */
  public void setLiftMotionAvailable(boolean available) {
    liftMotionAvailable = available;
  }

  /** @return last successfully evaluated commanded lift, before lag/rate limits */
  public double getLastCommandedLiftFraction() {
    return lastCommandedLiftFraction;
  }

  /** @return current pressure-demand latch, distinct from residual moving lift */
  public boolean isPressureDemandActive() {
    return wasOpenLastStep;
  }

  /** @return accepted positive response time, seconds; same UUID and zero-dt reevaluations do not advance it */
  public double getResponseElapsedSeconds() {
    return responseElapsedSeconds;
  }

  /** @return number of actual zero-to-positive lift transitions */
  public long getOpeningCycleCount() {
    return openingCycleCount;
  }

  /** @return number of actual positive-to-exact-zero lift transitions */
  public long getReseatCycleCount() {
    return reseatCycleCount;
  }

  /** @return last accepted response step UUID, or null before response advancement */
  public UUID getLastResponseStepId() {
    return lastResponseStepId;
  }

  /** @return current synthetic backpressure capacity factor */
  public double getCurrentBackpressureFactor() {
    return lastBackpressureFactor;
  }

  /** @return accepted dwell since the latest physical opening transition, seconds */
  public double getTimeSinceOpenSec() {
    return timeSinceOpenSec;
  }

  /** @return accepted dwell since the latest physical seating transition, seconds */
  public double getTimeSinceCloseSec() {
    return timeSinceCloseSec;
  }

  /** @return actual characterized Cv (US), applying lift and fouling once */
  public double getCurrentEffectiveCapacityCv() {
    return getMechanicalDesign().getValveCharacterizationMethod().getOpeningFactor(getPercentValveOpening())
        * getEffectiveCv();
  }

  /**
   * Initializes actual lift and a new response epoch without inventing flow or an operating permissive.
   *
   * @param liftFraction finite actual initial lift in [0,1]
   */
  public void initializeLiftState(double liftFraction) {
    initialLiftFraction = fraction(liftFraction, "initialLiftFraction", true);
    openFraction = initialLiftFraction;
    wasOpenLastStep = openFraction > 0.0;
    timeSinceOpenSec = openFraction > 0.0 ? LARGE_TIME_SEC : 0.0;
    timeSinceCloseSec = openFraction > 0.0 ? 0.0 : LARGE_TIME_SEC;
    responseElapsedSeconds = 0.0;
    lastCommandedLiftFraction = openFraction;
    openingCycleCount = 0L;
    reseatCycleCount = 0L;
    lastResponseStepId = null;
    setPercentValveOpening(100.0 * openFraction);
  }

  /**
   * Exposes bounded immutable scalar configuration for engineering readback, not certification.
   *
   * @return immutable actual response/configuration values with explicit units
   */
  public Map<String, Object> getPressureReliefConfiguration() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put("schemaVersion", "neqsim.pressure_relief_configuration.v1");
    values.put("pressureUnit", "bara");
    values.put("cvUnit", "US");
    values.put("setPressureBar", setPressureBar);
    values.put("fullLiftPressureBar", relievingPressureBar());
    values.put("reseatPressureBar", reseatPressureBar());
    values.put("overpressureFraction", overpressureFrac);
    values.put("blowdownFraction", blowdownFrac);
    values.put("ratedCv", ratedCv);
    values.put("kd", kd);
    values.put("kbMax", kbMax);
    values.put("backpressureSensitivity", backpressureSensitivity);
    values.put("minimumStableCommandFraction", minStableOpenFrac);
    values.put("valveType", valveType.name());
    values.put("openingLaw", openingLaw.name());
    values.put("tauOpenSeconds", tauOpenSec);
    values.put("tauCloseSeconds", tauCloseSec);
    values.put("minimumOpenDwellSeconds", minOpenTimeSec);
    values.put("minimumClosedDwellSeconds", minCloseTimeSec);
    values.put("maximumLiftRatePerSecond", maxLiftRatePerSec);
    values.put("seatToleranceFraction", seatToleranceFraction);
    values.put("liftMotionAvailable", liftMotionAvailable);
    values.put("initialLiftFraction", initialLiftFraction);
    values.put("valveCharacteristic", getMechanicalDesign().getValveCharacterization());
    values.put("capacityLiftApplication", "PARENT_VALVE_CHARACTERISTIC_ONCE");
    values.put("zeroFlowBoundaryMode", "PRESSURE_DRIVEN_CV_WITH_INTENSIVE_WORKSPACE");
    values.put("valveCharacteristicClass", getMechanicalDesign().getValveCharacterizationMethod().getClass().getName());
    values.put("valveSizingClass", getMechanicalDesign().getValveSizingMethod().getClass().getName());
    values.put("sizingXt", getMechanicalDesign().getValveSizingMethod().getxT());
    values.put("fp", getFp());
    values.put("foulingFraction", getFoulingFraction());
    values.put("allowChoked", isAllowChoked());
    values.put("allowLaminar", isAllowLaminar());
    values.put("gasValve", isGasValve());
    values.put("isothermal", isIsoThermal());
    values.put("specification", getSpecification());
    values.put("calculateOutletPressure", isCalcPressure);
    values.put("minimumValveOpeningPercent", getMinimumValveOpening());
    values.put("maximumValveOpeningPercent", getMaximumValveOpening());
    values.put("acceptNegativePressureDifference", isAcceptNegativeDP());
    values.put("parentTravelModel", getTravelModel().name());
    values.put("parentPhysicalStepIdempotencyEnabled", isPhysicalStepIdempotencyEnabled());
    values.put("activeExternalController", getController() != null && getController().isActive());
    values.put("valveDeadbandPercent", getValveDeadband());
    values.put("valveStictionPercent", getValveStiction());
    values.put("valveHysteresisPercent", getValveHysteresis());
    values.put("pressureFractionBasis", "ABSOLUTE_SET_PRESSURE");
    values.put("modulatingCommandBasis", "UNLATCHED_SET_TO_FULL_LATCHED_RESEAT_TO_FULL");
    values.put("sameUuidResponseIdempotent", Boolean.TRUE);
    values.put("capacityBasis", "SYNTHETIC_CV_ASSUMPTION");
    return Collections.unmodifiableMap(values);
  }

  /** @return declared full-lift pressure, bara; SNAP command begins at set pressure */
  private double relievingPressureBar() {
    return setPressureBar * (1.0 + overpressureFrac);
  }

  /** @return declared reseat pressure, bara */
  private double reseatPressureBar() {
    return setPressureBar * (1.0 - blowdownFrac);
  }

  /**
   * Evaluates the ideal pressure demand without mutating accepted response state.
   *
   *
   * @param upstreamPressure actual upstream pressure, bara
   *
   * @return active pressure demand
   */
  private boolean pressureDemand(double upstreamPressure) {
    if (wasOpenLastStep) {
      return upstreamPressure > reseatPressureBar();
    }
    return openingLaw == OpeningLaw.SNAP ? upstreamPressure >= setPressureBar : upstreamPressure > setPressureBar;
  }

  /**
   * Computes ideal command with an explicit two-leg modulating hysteresis.
   *
   *
   * @param upstreamPressure actual upstream pressure, bara
   *
   * @param demand current pressure latch
   *
   * @return commanded fraction before stable-command floor and dwell
   */
  private double computeOpeningFraction(double upstreamPressure, boolean demand) {
    if (!demand) {
      return 0.0;
    }
    if (openingLaw == OpeningLaw.SNAP) {
      return 1.0;
    }
    double lower = wasOpenLastStep ? reseatPressureBar() : setPressureBar;
    double width = relievingPressureBar() - lower;
    return width <= EPS ? 1.0 : Math.max(0.0, Math.min(1.0, (upstreamPressure - lower) / width));
  }

  /**
   * Evaluates a deliberately simple synthetic backpressure correction.
   *
   *
   * @param upstreamPressure upstream pressure, bara
   *
   * @param downstreamPressure outlet backpressure, bara
   *
   * @return bounded capacity multiplier, additional to actual pressure-driven hydraulics
   */
  private double capacityBackpressureFactor(double upstreamPressure, double downstreamPressure) {
    double ratio = Math.max(0.0, Math.min(1.0, downstreamPressure / upstreamPressure));
    double multiplier = valveType == ValveType.CONVENTIONAL ? 1.0
        : valveType == ValveType.BALANCED_BELLOWS ? 0.05 : 0.5;
    return Math.max(0.0, Math.min(kbMax, 1.0 - multiplier * backpressureSensitivity * ratio));
  }

  /**
   * Publishes full-lift Cv and lift separately so the parent trim applies lift exactly once.
   *
   *
   * @param lift physical lift fraction
   *
   * @param backpressureFactor synthetic capacity multiplier
   */
  private void applyOpeningToCv(double lift, double backpressureFactor) {
    setCv(ratedCv * kd * backpressureFactor);
    setPercentValveOpening(100.0 * lift);
  }

  /** Ensures the self-acting lift model has no competing parent actuator/controller. */
  private void validateSelfAction() {
    if (getInletStream() == null || getInletStream().getFluid() == null) {
      throw new IllegalStateException("SafetyReliefValve requires an initialized upstream intensive state");
    }
    // A zero specified flow remains a pressure boundary only with a valid initialized intensive state.
    // Reject invalid phase flags; repairing or reflashing an upstream model is outside this valve's contract.
    double phaseSum = 0.0;
    for (int i = 0; i < getInletStream().getFluid().getNumberOfPhases(); i++) {
      double beta = getInletStream().getFluid().getPhase(i).getBeta();
      if (!Double.isFinite(beta) || beta < 0.0 || beta > 1.0) {
        throw new IllegalStateException("SafetyReliefValve requires finite phase fractions in [0,1]");
      }
      phaseSum += beta;
    }
    if (Math.abs(phaseSum - 1.0) > 1.0e-8) {
      throw new IllegalStateException("SafetyReliefValve requires initialized phase fractions summing to one");
    }
    if (getTravelModel() != ValveTravelModel.NONE) {
      throw new IllegalStateException("SafetyReliefValve owns lift lag/rate; parent travel model must be NONE");
    }
    if (getController() != null && getController().isActive()) {
      throw new IllegalStateException("SafetyReliefValve self-action cannot have an active external controller");
    }
    if (isCalcPressure) {
      throw new IllegalStateException("SafetyReliefValve requires imposed outlet backpressure");
    }
    if (getMinimumValveOpening() != 0.0 || getMaximumValveOpening() != 100.0 || getValveDeadband() != 0.0
        || getValveStiction() != 0.0 || getValveHysteresis() != 0.0) {
      throw new IllegalStateException(
          "SafetyReliefValve owns lift; parent range must be 0..100 without secondary deadband/stiction/hysteresis");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    validateSelfAction();
    double upstream = positive(getInletStream().getPressure("bara"), "upstreamPressureBar");
    double downstream = positive(getOutletStream().getPressure("bara"), "downstreamPressureBar");
    boolean demand = pressureDemand(upstream);
    double command = computeOpeningFraction(upstream, demand);
    if (command > 0.0) {
      command = Math.max(command, minStableOpenFrac);
    }
    double lift = liftMotionAvailable ? command : openFraction;
    double kb = capacityBackpressureFactor(upstream, downstream);
    applyOpeningToCv(lift, kb);
    runNativeHydraulics(0.0, id);
    openFraction = lift;
    wasOpenLastStep = demand;
    lastCommandedLiftFraction = command;
    lastBackpressureFactor = kb;
    timeSinceOpenSec = lift > 0.0 ? LARGE_TIME_SEC : 0.0;
    timeSinceCloseSec = lift > 0.0 ? 0.0 : LARGE_TIME_SEC;
  }

  /**
   * Evaluates algebraic pressure/Cv capacity on an isolated inlet, preserving the connected source's flow and fluid.
   * Finite transient execution instead publishes withdrawal directly through the parent hydraulic path.
   *
   *
   * @param dt hydraulic reevaluation interval, seconds
   *
   * @param id actual calculation identity
   */
  private void runNativeHydraulics(double dt, UUID id) {
    StreamInterface connectedInlet = inStream;
    boolean steady = getCalculateSteadyState();
    try {
      inStream = connectedInlet.clone();
      setCalculateSteadyState(false);
      super.runTransient(dt, id);
    } finally {
      inStream = connectedInlet;
      setCalculateSteadyState(steady);
    }
    updateRecalculationState();
  }

  /**
   * A self-acting PSV is a pressure/Cv boundary even when its preceding closed demand is zero.
   *
   * @return true; the connected model must supply actual upstream inventory or a declared pressure source
   */
  @Override
  protected boolean isZeroFlowPressureBoundaryEnabled() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double dt) {
    runTransient(dt, UUID.randomUUID());
  }

  /**
   * Advances self-acting lift once per non-null physical UUID, then reevaluates native Cv hydraulics.
   *
   * <p>
   * Zero dt reevaluates hydraulics only. A repeated UUID does not advance lift, latch, dwell, counters or response
   * time, even if the supplied dt differs. Negative/nonfinite dt is rejected before mutation. This is not whole-call
   * transactional rollback; use the owned snapshot plus independent connected stream snapshots for rejected physical
   * trials.
   * </p>
   *
   *
   * @param dt finite nonnegative interval, seconds
   *
   * @param id actual physical-step UUID; null preserves independent legacy-call advancement
   */
  @Override
  public void runTransient(double dt, UUID id) {
    nonNegative(dt, "dt");
    validateSelfAction();
    if (getCalculateSteadyState()) {
      if (dt > 0.0 && (id == null || !id.equals(lastResponseStepId))) {
        run(id);
        increaseTime(dt);
        responseElapsedSeconds += dt;
        lastResponseStepId = id;
      } else {
        runNativeHydraulics(0.0, id);
      }
      return;
    }
    double upstream = positive(getInletStream().getPressure("bara"), "upstreamPressureBar");
    double downstream = positive(getOutletStream().getPressure("bara"), "downstreamPressureBar");
    double kb = capacityBackpressureFactor(upstream, downstream);
    boolean advance = dt > 0.0 && (id == null || !id.equals(lastResponseStepId));
    if (!advance) {
      applyOpeningToCv(openFraction, kb);
      super.runTransient(dt, id);
      return;
    }
    boolean demand = pressureDemand(upstream);
    double command = computeOpeningFraction(upstream, demand);
    if (command > 0.0) {
      command = Math.max(command, minStableOpenFrac);
    }
    boolean previousOpen = openFraction > 0.0;
    if (previousOpen && timeSinceOpenSec < minOpenTimeSec) {
      command = Math.max(command, openFraction);
    }
    if (!previousOpen && timeSinceCloseSec < minCloseTimeSec) {
      command = 0.0;
    }
    double tau = command >= openFraction ? tauOpenSec : tauCloseSec;
    double alpha = tau <= EPS ? 1.0 : -Math.expm1(-dt / tau);
    double proposed = openFraction + alpha * (command - openFraction);
    double maximumDelta = maxLiftRatePerSec > 0.0 ? maxLiftRatePerSec * dt : Double.POSITIVE_INFINITY;
    proposed = openFraction + Math.max(-maximumDelta, Math.min(maximumDelta, proposed - openFraction));
    proposed = Math.max(0.0, Math.min(1.0, proposed));
    if (command == 0.0 && proposed <= seatToleranceFraction && openFraction <= maximumDelta) {
      proposed = 0.0;
    }
    if (!liftMotionAvailable) {
      proposed = openFraction;
    }
    applyOpeningToCv(proposed, kb);
    super.runTransient(dt, id);
    boolean nowOpen = proposed > 0.0;
    if (nowOpen) {
      timeSinceOpenSec = previousOpen ? timeSinceOpenSec + dt : 0.0;
      timeSinceCloseSec = 0.0;
      if (!previousOpen) {
        openingCycleCount++;
      }
    } else {
      timeSinceCloseSec = previousOpen ? 0.0 : timeSinceCloseSec + dt;
      timeSinceOpenSec = 0.0;
      if (previousOpen) {
        reseatCycleCount++;
      }
    }
    openFraction = proposed;
    wasOpenLastStep = demand;
    lastCommandedLiftFraction = command;
    lastBackpressureFactor = kb;
    responseElapsedSeconds += dt;
    increaseTime(dt);
    lastResponseStepId = id;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != SafetyReliefValve.class) {
      return "relief-valve subclass must extend the snapshot for its own mutable state";
    }
    try {
      validateSelfAction();
    } catch (IllegalStateException issue) {
      return issue.getMessage();
    }
    return getValveTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public TransientState captureTransientState() {
    String issue = getTransientStateCoverageIssue();
    if (issue != null) {
      throw new IllegalStateException("Cannot capture relief valve '" + getName() + "': " + issue);
    }
    return new ReliefTransientState(this);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreTransientStepState(TransientState snapshot) {
    if (!(snapshot instanceof ReliefTransientState)) {
      throw new IllegalArgumentException("Relief valve requires its owned snapshot");
    }
    super.restoreTransientStepState(snapshot);
    ReliefTransientState state = (ReliefTransientState) snapshot;
    double[] v = state.values;
    setPressureBar = v[0];
    overpressureFrac = v[1];
    blowdownFrac = v[2];
    ratedCv = v[3];
    kd = v[4];
    kbMax = v[5];
    backpressureSensitivity = v[6];
    minStableOpenFrac = v[7];
    openFraction = v[8];
    timeSinceOpenSec = v[9];
    timeSinceCloseSec = v[10];
    tauOpenSec = v[11];
    tauCloseSec = v[12];
    minOpenTimeSec = v[13];
    minCloseTimeSec = v[14];
    maxLiftRatePerSec = v[15];
    seatToleranceFraction = v[16];
    initialLiftFraction = v[17];
    responseElapsedSeconds = v[18];
    lastCommandedLiftFraction = v[19];
    lastBackpressureFactor = v[20];
    wasOpenLastStep = state.pressureDemand;
    liftMotionAvailable = state.motionAvailable;
    valveType = state.valveType;
    openingLaw = state.openingLaw;
    lastResponseStepId = state.lastStep;
    openingCycleCount = state.openings;
    reseatCycleCount = state.reseats;
  }

  /**
   * Bounded checkpoint of parent hydraulics and every relief-owned scalar configuration/response field.
   *
   * @author Even Solbraa
   * @version 1.0
   */
  public static final class ReliefTransientState extends TransientState {
    private static final long serialVersionUID = 1L;
    private final double[] values;
    private final boolean pressureDemand;
    private final boolean motionAvailable;
    private final ValveType valveType;
    private final OpeningLaw openingLaw;
    private final UUID lastStep;
    private final long openings;
    private final long reseats;

    /** @param source actual relief valve being checkpointed */
    private ReliefTransientState(SafetyReliefValve source) {
      super(source);
      values = new double[] {source.setPressureBar, source.overpressureFrac, source.blowdownFrac, source.ratedCv,
          source.kd, source.kbMax, source.backpressureSensitivity, source.minStableOpenFrac, source.openFraction,
          source.timeSinceOpenSec, source.timeSinceCloseSec, source.tauOpenSec, source.tauCloseSec,
          source.minOpenTimeSec, source.minCloseTimeSec, source.maxLiftRatePerSec, source.seatToleranceFraction,
          source.initialLiftFraction, source.responseElapsedSeconds, source.lastCommandedLiftFraction,
          source.lastBackpressureFactor};
      pressureDemand = source.wasOpenLastStep;
      motionAvailable = source.liftMotionAvailable;
      valveType = source.valveType;
      openingLaw = source.openingLaw;
      lastStep = source.lastResponseStepId;
      openings = source.openingCycleCount;
      reseats = source.reseatCycleCount;
    }
  }

  /**
   * Configures a synthetic conventional ideal-pop valve.
   * 
   *
   * @param psetBar set pressure, bara
   *
   * @param overFrac full-lift overpressure fraction; not the SNAP pop threshold
   *
   * @param blowFrac reseat blowdown fraction
   *
   * @param cvRated full-lift synthetic Cv (US), before Kd/backpressure correction
   *
   * @return this valve
   */
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

  /**
   * Configures a synthetic balanced two-leg modulating valve.
   * 
   *
   * @param psetBar initial opening pressure, bara
   *
   * @param overFrac rising full-lift overpressure fraction
   *
   * @param blowFrac falling-leg reseat blowdown fraction
   *
   * @param cvRated full-lift synthetic Cv (US)
   *
   * @return this valve
   */
  public SafetyReliefValve configureBalancedModulating(double psetBar, double overFrac, double blowFrac,
      double cvRated) {
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

  /** @return declared full-lift pressure, bara; SNAP ideal demand starts at set pressure */
  public double getRelievingPressureBar() {
    return relievingPressureBar();
  }

  /** @return declared pressure-demand reseat threshold, bara */
  public double getReseatPressureBar() {
    return reseatPressureBar();
  }

  /**
   * @param value scalar to validate
   * @param name parameter name
   * @return finite positive value
   */
  private static double positive(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be finite and positive");
    }
    return value;
  }

  /**
   * @param value scalar to validate
   * @param name parameter name
   * @return finite nonnegative value
   */
  private static double nonNegative(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
    return value;
  }

  /**
   *
   * @param value scalar to validate
   * @param name parameter name
   * @param allowOne whether 1 is valid
   * @return finite bounded fraction
   */
  private static double fraction(double value, String name, boolean allowOne) {
    nonNegative(value, name);
    if (value > 1.0 || (!allowOne && value == 1.0)) {
      throw new IllegalArgumentException(name + " exceeds its fraction domain");
    }
    return value;
  }

  /**
   * Migrates UID1 legacy streams explicitly instead of interpreting absent motion state as a failed valve.
   *
   * <p>
   * Legacy streams retain their actual lift/latch/dwell and scalar settings. New response counters/time start a
   * documented epoch, motion is available, the seating cutoff defaults to 1e-8, and parent Cv is normalized to the
   * corrected single-lift basis. This is a model migration, not exact legacy numerical replay. Current-schema streams
   * preserve their complete response state.
   * </p>
   *
   *
   * @param input serialized native graph
   *
   * @throws IOException if the stream is invalid or has an unsupported schema
   *
   * @throws ClassNotFoundException if a referenced native class is unavailable
   */
  private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
    input.defaultReadObject();
    if (reliefResponseSchemaVersion != 0 && reliefResponseSchemaVersion != 1) {
      throw new InvalidObjectException("Unsupported SafetyReliefValve response schema " + reliefResponseSchemaVersion);
    }
    if (reliefResponseSchemaVersion == 0) {
      liftMotionAvailable = true;
      seatToleranceFraction = 1.0e-8;
      initialLiftFraction = openFraction;
      responseElapsedSeconds = 0.0;
      openingCycleCount = 0L;
      reseatCycleCount = 0L;
      lastCommandedLiftFraction = openFraction;
      lastResponseStepId = null;
      lastBackpressureFactor = 1.0;
      if (getInletStream() != null && getOutletStream() != null) {
        double upstream = getInletStream().getPressure("bara");
        double downstream = getOutletStream().getPressure("bara");
        if (Double.isFinite(upstream) && upstream > 0.0 && Double.isFinite(downstream) && downstream > 0.0) {
          lastBackpressureFactor = capacityBackpressureFactor(upstream, downstream);
        }
      }
      applyOpeningToCv(openFraction, lastBackpressureFactor);
      reliefResponseSchemaVersion = 1;
    }
    try {
      positive(setPressureBar, "setPressureBar");
      nonNegative(overpressureFrac, "overpressureFraction");
      fraction(blowdownFrac, "blowdownFraction", false);
      nonNegative(ratedCv, "ratedCv");
      fraction(kd, "kd", true);
      fraction(kbMax, "kbMax", true);
      nonNegative(backpressureSensitivity, "backpressureSensitivity");
      fraction(minStableOpenFrac, "minimumStableCommand", true);
      fraction(openFraction, "openFraction", true);
      fraction(initialLiftFraction, "initialLiftFraction", true);
      fraction(seatToleranceFraction, "seatToleranceFraction", false);
      nonNegative(responseElapsedSeconds, "responseElapsedSeconds");
      nonNegative(tauOpenSec, "tauOpenSec");
      nonNegative(tauCloseSec, "tauCloseSec");
      nonNegative(minOpenTimeSec, "minOpenTimeSec");
      nonNegative(minCloseTimeSec, "minCloseTimeSec");
      nonNegative(maxLiftRatePerSec, "maxLiftRatePerSec");
      nonNegative(timeSinceOpenSec, "timeSinceOpenSec");
      nonNegative(timeSinceCloseSec, "timeSinceCloseSec");
      Objects.requireNonNull(valveType, "valveType");
      Objects.requireNonNull(openingLaw, "openingLaw");
      if (openingCycleCount < 0L || reseatCycleCount < 0L) {
        throw new IllegalArgumentException("Negative cycle count");
      }
    } catch (RuntimeException failure) {
      InvalidObjectException invalid = new InvalidObjectException(
          "Invalid SafetyReliefValve serialized response state");
      invalid.initCause(failure);
      throw invalid;
    }
  }
}
