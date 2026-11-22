package neqsim.process.equipment.valve;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Check valve (non-return valve) that prevents reverse flow.
 *
 * <p>
 * CheckValve is a self-actuating valve that opens when forward differential pressure exceeds the cracking pressure, and
 * closes when flow reverses or stops. It is essential for protecting equipment like pumps and compressors from reverse
 * flow damage.
 * </p>
 *
 * <p>
 * Key features:
 * <ul>
 * <li>Automatic operation based on differential pressure</li>
 * <li>Configurable cracking pressure (minimum ΔP to open)</li>
 * <li>Prevents backflow in piping systems</li>
 * <li>Zero leakage when closed (ideal model)</li>
 * <li>Minimal pressure drop when fully open</li>
 * </ul>
 *
 * <p>
 * Common applications:
 * <ul>
 * <li>Pump discharge protection</li>
 * <li>Compressor discharge protection</li>
 * <li>Parallel equipment isolation</li>
 * <li>Gravity drainage systems</li>
 * <li>Preventing siphoning</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * CheckValve checkValve = new CheckValve("CV-101", pumpDischargeStream);
 * checkValve.setCrackingPressure(0.2); // Opens at 0.2 bar differential
 * checkValve.setCv(250.0); // Flow coefficient when fully open
 * checkValve.run();
 *
 * boolean forwardFlowAvailable = checkValve.isOpen();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class CheckValve extends ThrottlingValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Atomically published opening threshold in bar; configuration and solves still require one coordinated owner. */
  private volatile double crackingPressure = 0.1; // Default 0.1 bar

  /** Indicates if valve is currently open. */
  private volatile boolean isOpen = false;

  /**
   * Constructor for CheckValve.
   *
   * @param name name of the check valve
   */
  public CheckValve(String name) {
    super(name);
    // Check valves are typically fully open or fully closed
    setPercentValveOpening(100.0);
  }

  /**
   * Constructor for CheckValve.
   *
   * @param name name of the check valve
   * @param inStream inlet stream
   */
  public CheckValve(String name, StreamInterface inStream) {
    super(name, inStream);
    setPercentValveOpening(100.0);
  }

  /**
   * Set the cracking pressure - minimum differential pressure to open valve.
   *
   * @param crackingPressure finite nonnegative forward pressure difference in bar
   * @throws IllegalArgumentException if the threshold is negative or non-finite
   */
  public void setCrackingPressure(double crackingPressure) {
    if (!Double.isFinite(crackingPressure) || crackingPressure < 0.0) {
      throw new IllegalArgumentException("Check-valve cracking pressure must be finite and nonnegative in bar");
    }
    this.crackingPressure = crackingPressure;
  }

  /**
   * Get the cracking pressure.
   *
   * @return minimum forward pressure difference in bar
   */
  public double getCrackingPressure() {
    return crackingPressure;
  }

  /**
   * Check if valve is currently open.
   *
   * @return true if valve is open, false if closed
   */
  public boolean isOpen() {
    return isOpen;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  /**
   * Executes the ideal passive pressure gate through the native UUID path.
   *
   * <p>
   * The actual downstream stream supplies the absolute pressure boundary, including after a zero-flow calculation. The
   * cracking pressure is an opening threshold, not an additional vendor pressure-loss correlation. This model does not
   * resolve disc motion, slam, leakage or reverse rotation.
   * </p>
   *
   * @param id calculation identifier
   */
  @Override
  public void run(UUID id) {
    if (!preparePassiveState()) {
      publishClosedState(id, false);
      return;
    }
    super.run(id);
  }

  /**
   * Executes the same gate before any transient hydraulic withdrawal.
   *
   * @param dt finite nonnegative physical timestep in seconds
   * @param id physical-step identifier
   * @throws IllegalArgumentException if the timestep is negative or non-finite
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (!Double.isFinite(dt) || dt < 0.0) {
      throw new IllegalArgumentException("Check-valve timestep must be finite and nonnegative");
    }
    if (!preparePassiveState()) {
      publishClosedState(id, !getCalculateSteadyState());
      if (getCalculateSteadyState()) {
        increaseTime(dt);
      }
      return;
    }
    super.runTransient(dt, id);
  }

  /**
   * Queries native hydraulic capacity with the same passive opening gate and no flash.
   *
   * <p>
   * Valid inlet composition and phase state are retained in a detached normalized intensive workspace. No stream is
   * flashed. This query may update the ideal seat state but does not withdraw mass, advance time or stamp a
   * physical-step identifier. A subsequent native run is required to publish actual port flows and enthalpy.
   * </p>
   *
   * @return forward capacity in mole/sec, or exact zero below the opening threshold
   */
  @Override
  public double calculateMolarFlow() {
    if (!preparePassiveState()) {
      return 0.0;
    }
    SystemInterface intensive = getInletStream().getFluid().clone();
    intensive.setTotalNumberOfMoles(1.0);
    intensive.initProperties();
    Stream workspace = new Stream(getName() + " capacity workspace", intensive);
    double capacity = calculateMolarFlowForStreams(workspace, getOutletStream());
    if (!Double.isFinite(capacity) || capacity < 0.0) {
      throw new IllegalStateException("Native check-valve capacity must be finite and nonnegative");
    }
    return capacity;
  }

  /**
   * Validates configuration and reads current actual port pressures before changing state.
   *
   * @return true when forward differential pressure opens the ideal valve
   * @throws IllegalStateException if the passive model or pressure boundary is invalid
   */
  private boolean preparePassiveState() {
    validatePassiveConfiguration();
    if (getInletStream() == null || getOutletStream() == null || getInletStream().getFluid() == null
        || getOutletStream().getFluid() == null) {
      throw new IllegalStateException("Check valve requires initialized inlet and outlet streams");
    }
    double inletPressure = getInletStream().getPressure("bara");
    double outletPressure = getOutletStream().getPressure("bara");
    if (!Double.isFinite(inletPressure) || inletPressure <= 0.0 || !Double.isFinite(outletPressure)
        || outletPressure <= 0.0) {
      throw new IllegalStateException("Check-valve port pressures must be finite positive bara");
    }
    double betaSum = 0.0;
    for (int i = 0; i < getInletStream().getFluid().getNumberOfPhases(); i++) {
      double beta = getInletStream().getFluid().getPhase(i).getBeta();
      if (!Double.isFinite(beta) || beta < 0.0) {
        throw new IllegalStateException("Check-valve inlet phase fractions must be finite and nonnegative");
      }
      betaSum += beta;
    }
    if (Math.abs(betaSum - 1.0) > 1.0e-8) {
      throw new IllegalStateException("Check-valve inlet phase fractions must sum to one");
    }
    setOutletPressure(outletPressure, "bara");
    double differentialPressure = inletPressure - outletPressure;
    isOpen = differentialPressure > 0.0 && differentialPressure >= crackingPressure;
    setPercentValveOpening(isOpen ? 100.0 : 0.0);
    isActive(true);
    return isOpen;
  }

  /**
   * Rejects incompatible external actuator ownership instead of silently overriding it.
   *
   * @throws IllegalStateException if the ideal passive configuration is unsupported
   */
  private void validatePassiveConfiguration() {
    if (!Double.isFinite(crackingPressure) || crackingPressure < 0.0) {
      throw new IllegalStateException("Check-valve cracking pressure must be finite and nonnegative");
    }
    if (getController() != null || !getControllers().isEmpty() || getTravelModel() != ValveTravelModel.NONE) {
      throw new IllegalStateException("Ideal passive check valve does not support an external controller or actuator");
    }
  }

  /**
   * Publishes exact ideal seating while preserving the downstream pressure and native Cv.
   *
   * @param id calculation identifier
   * @param zeroInletDemand true when the transient upstream withdrawal must also be zero
   */
  private void publishClosedState(UUID id, boolean zeroInletDemand) {
    thermoSystem = getInletStream().getFluid().clone();
    thermoSystem.setPressure(getOutletStream().getPressure("bara"), "bara");
    thermoSystem.setTotalNumberOfMoles(0.0);
    thermoSystem.init(0);
    molarFlow = 0.0;
    if (zeroInletDemand) {
      getInletStream().getFluid().setTotalNumberOfMoles(0.0);
    }
    getOutletStream().setThermoSystem(thermoSystem);
    getOutletStream().setCalculationIdentifier(id);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  protected boolean isZeroFlowPressureBoundaryEnabled() {
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != CheckValve.class) {
      return "check-valve subclass must extend the owned transient snapshot";
    }
    try {
      validatePassiveConfiguration();
    } catch (IllegalStateException ex) {
      return ex.getMessage();
    }
    return getValveTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public TransientState captureTransientState() {
    String issue = getTransientStateCoverageIssue();
    if (issue != null) {
      throw new IllegalStateException("Cannot capture check valve: " + issue);
    }
    return new CheckTransientState(this);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreTransientStepState(TransientState snapshot) {
    if (!(snapshot instanceof CheckTransientState)) {
      throw new IllegalArgumentException("Check valve requires its owned snapshot");
    }
    super.restoreTransientStepState(snapshot);
    CheckTransientState state = (CheckTransientState) snapshot;
    crackingPressure = state.crackingPressure;
    isOpen = state.open;
  }

  /**
   * Adds passive opening state to the existing valve-owned checkpoint.
   *
   * @author NeqSim study validation
   * @version 1.0
   */
  private static final class CheckTransientState extends TransientState {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;
    /** Captured differential opening threshold in bar. */
    private final double crackingPressure;
    /** Captured ideal seat state. */
    private final boolean open;

    /**
     * Captures native and passive state without replacing connected streams.
     *
     * @param source check valve being captured
     */
    private CheckTransientState(CheckValve source) {
      super(source);
      crackingPressure = source.crackingPressure;
      open = source.isOpen;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Returns a string representation specific to check valves.
   * </p>
   */
  @Override
  public String toString() {
    return String.format(java.util.Locale.ROOT, "%s [Check Valve] - State: %s, Cracking P: %.3f bar, Cv: %.1f",
        getName(), isOpen ? "OPEN" : "CLOSED", crackingPressure, getCv());
  }
}
