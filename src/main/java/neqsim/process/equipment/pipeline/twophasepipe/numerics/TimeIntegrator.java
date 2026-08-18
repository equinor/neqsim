package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * Time integration methods for the two-fluid transient pipe model.
 *
 * <p>
 * Provides explicit time stepping algorithms with CFL-based adaptive time step control. The primary method is the
 * classical 4th-order Runge-Kutta (RK4) scheme.
 * </p>
 *
 * <h2>RK4 Algorithm</h2>
 * <p>
 * For dU/dt = R(U):
 * </p>
 * <ul>
 * <li>k1 = R(U^n)</li>
 * <li>k2 = R(U^n + 0.5*dt*k1)</li>
 * <li>k3 = R(U^n + 0.5*dt*k2)</li>
 * <li>k4 = R(U^n + dt*k3)</li>
 * <li>U^{n+1} = U^n + dt/6 * (k1 + 2*k2 + 2*k3 + k4)</li>
 * </ul>
 *
 * <h2>CFL Condition</h2>
 * <p>
 * The time step is limited by: dt ≤ CFL * dx / (|v| + c) where c is the sound speed and CFL is typically 0.5-0.9 for
 * explicit schemes.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class TimeIntegrator implements Serializable {

  private static final long serialVersionUID = 1L;

  /**
   * Integration method type.
   */
  public enum Method {
    /** First-order forward Euler. */
    EULER,
    /** Second-order Runge-Kutta (Heun). */
    RK2,
    /** Classical 4th-order Runge-Kutta. */
    RK4,
    /** Strong Stability Preserving RK3. */
    SSP_RK3,
    /**
     * IMEX (Implicit-Explicit) pressure correction method. Treats transport (advection) explicitly and the pressure
     * wave equation implicitly, removing the acoustic CFL constraint. Allows time steps 10-100x larger than fully
     * explicit schemes for long pipelines. Based on the pressure-correction approach of Harlow and Amsden (1971)
     * adapted for two-fluid models.
     */
    IMEX_PRESSURE_CORRECTION
  }

  /** Current integration method. */
  private Method method = Method.RK4;

  /** CFL number for time step control (0 &lt; CFL &lt; 1). */
  private double cflNumber = 0.5;

  /** Minimum allowed time step (s). */
  private double minTimeStep = 1e-6;

  /** Maximum allowed time step (s). */
  private double maxTimeStep = 10.0;

  /** Current simulation time (s). */
  private double currentTime = 0;

  /** Current time step (s). */
  private double currentDt = 0.01;

  /**
   * Interface for the right-hand side function (spatial discretization).
   */
  public interface RHSFunction {
    /**
     * Calculate dU/dt = R(U) for the given state.
     *
     * @param U Conservative variables [nCells][nVars]
     * @param t Current time
     * @return Time derivatives dU/dt [nCells][nVars]
     */
    double[][] evaluate(double[][] U, double t);
  }

  /**
   * Default constructor.
   */
  public TimeIntegrator() {
  }

  /**
   * Constructor with method selection.
   *
   * @param method Integration method
   */
  public TimeIntegrator(Method method) {
    this.method = method;
  }

  /**
   * Advance solution by one time step using selected method.
   *
   * @param U Current state [nCells][nVars]
   * @param rhs Right-hand side function
   * @param dt Time step
   * @return Updated state at t + dt
   */
  public double[][] step(double[][] U, RHSFunction rhs, double dt) {
    double[][] advancedState;
    switch (method) {
    case EULER:
      advancedState = stepEuler(U, rhs, dt);
      break;
    case RK2:
      advancedState = stepRK2(U, rhs, dt);
      break;
    case RK4:
      advancedState = stepRK4(U, rhs, dt);
      break;
    case SSP_RK3:
      advancedState = stepSSPRK3(U, rhs, dt);
      break;
    case IMEX_PRESSURE_CORRECTION:
      if (coupledPressureMomentumEnabled) {
        // The coupled solver supplies the acoustic correction and must not be
        // stacked on the legacy sequential IMEX pressure correction.
        advancedState = stepEuler(U, rhs, dt);
      } else {
        return stepIMEXPressureCorrection(U, rhs, dt);
      }
      break;
    default:
      advancedState = stepRK4(U, rhs, dt);
      break;
    }
    advancedState = applyCoupledPressureMomentumCorrection(advancedState, dt);
    return applyImplicitVoidWaveCorrection(advancedState, dt);
  }

  /**
   * Forward Euler method (first-order).
   *
   * <p>
   * U^{n+1} = U^n + dt * R(U^n)
   * </p>
   *
   * @param U Current state
   * @param rhs Right-hand side function
   * @param dt Time step
   * @return Updated state
   */
  public double[][] stepEuler(double[][] U, RHSFunction rhs, double dt) {
    double[][] dU = rhs.evaluate(U, currentTime);
    return addArrays(U, scaleArray(dU, dt));
  }

  /**
   * Second-order Runge-Kutta (Heun's method).
   *
   * @param U Current state
   * @param rhs Right-hand side function
   * @param dt Time step
   * @return Updated state
   */
  public double[][] stepRK2(double[][] U, RHSFunction rhs, double dt) {
    // k1 = R(U)
    double[][] k1 = rhs.evaluate(U, currentTime);

    // U1 = U + dt * k1
    double[][] U1 = addArrays(U, scaleArray(k1, dt));

    // k2 = R(U1)
    double[][] k2 = rhs.evaluate(U1, currentTime + dt);

    // U^{n+1} = U + 0.5 * dt * (k1 + k2)
    double[][] sum = addArrays(k1, k2);
    return addArrays(U, scaleArray(sum, 0.5 * dt));
  }

  /**
   * Classical 4th-order Runge-Kutta.
   *
   * @param U Current state
   * @param rhs Right-hand side function
   * @param dt Time step
   * @return Updated state
   */
  public double[][] stepRK4(double[][] U, RHSFunction rhs, double dt) {
    int nCells = U.length;
    int nVars = U[0].length;

    // k1 = R(U)
    double[][] k1 = rhs.evaluate(U, currentTime);

    // U1 = U + 0.5 * dt * k1
    double[][] U1 = addArrays(U, scaleArray(k1, 0.5 * dt));

    // k2 = R(U1)
    double[][] k2 = rhs.evaluate(U1, currentTime + 0.5 * dt);

    // U2 = U + 0.5 * dt * k2
    double[][] U2 = addArrays(U, scaleArray(k2, 0.5 * dt));

    // k3 = R(U2)
    double[][] k3 = rhs.evaluate(U2, currentTime + 0.5 * dt);

    // U3 = U + dt * k3
    double[][] U3 = addArrays(U, scaleArray(k3, dt));

    // k4 = R(U3)
    double[][] k4 = rhs.evaluate(U3, currentTime + dt);

    // U^{n+1} = U + dt/6 * (k1 + 2*k2 + 2*k3 + k4)
    double[][] result = new double[nCells][nVars];
    for (int i = 0; i < nCells; i++) {
      for (int j = 0; j < nVars; j++) {
        result[i][j] = U[i][j] + dt / 6.0 * (k1[i][j] + 2 * k2[i][j] + 2 * k3[i][j] + k4[i][j]);
      }
    }

    return result;
  }

  /**
   * Strong Stability Preserving RK3 (Shu-Osher).
   *
   * <p>
   * Maintains TVD property, good for problems with shocks.
   * </p>
   *
   * @param U Current state
   * @param rhs Right-hand side function
   * @param dt Time step
   * @return Updated state
   */
  public double[][] stepSSPRK3(double[][] U, RHSFunction rhs, double dt) {
    // Stage 1: U1 = U + dt * R(U)
    double[][] k1 = rhs.evaluate(U, currentTime);
    double[][] U1 = addArrays(U, scaleArray(k1, dt));

    // Stage 2: U2 = 0.75*U + 0.25*(U1 + dt*R(U1))
    double[][] k2 = rhs.evaluate(U1, currentTime + dt);
    double[][] U1plus = addArrays(U1, scaleArray(k2, dt));
    double[][] U2 = addArrays(scaleArray(U, 0.75), scaleArray(U1plus, 0.25));

    // Stage 3: U^{n+1} = (1/3)*U + (2/3)*(U2 + dt*R(U2))
    double[][] k3 = rhs.evaluate(U2, currentTime + 0.5 * dt);
    double[][] U2plus = addArrays(U2, scaleArray(k3, dt));
    return addArrays(scaleArray(U, 1.0 / 3.0), scaleArray(U2plus, 2.0 / 3.0));
  }

  /**
   * Calculate stable time step based on CFL condition.
   *
   * @param maxWaveSpeed Maximum wave speed in the domain (|v| + c)
   * @param dx Minimum cell size
   * @return Stable time step
   */
  public double calcStableTimeStep(double maxWaveSpeed, double dx) {
    if (maxWaveSpeed < 1e-10) {
      return maxTimeStep;
    }

    double dt = cflNumber * dx / maxWaveSpeed;

    // Apply limits
    dt = Math.max(minTimeStep, Math.min(maxTimeStep, dt));
    currentDt = dt;

    return dt;
  }

  /**
   * Calculate stable time step for two-fluid system.
   *
   * @param gasVelocities Gas velocities at each cell (m/s)
   * @param liquidVelocities Liquid velocities at each cell (m/s)
   * @param gasSoundSpeeds Gas sound speeds at each cell (m/s)
   * @param liquidSoundSpeeds Liquid sound speeds at each cell (m/s)
   * @param dx Cell size (m)
   * @return Stable time step
   */
  public double calcTwoFluidTimeStep(double[] gasVelocities, double[] liquidVelocities, double[] gasSoundSpeeds,
      double[] liquidSoundSpeeds, double dx) {
    double maxSpeed = 1.0; // Minimum to avoid division by zero

    for (int i = 0; i < gasVelocities.length; i++) {
      double gasSpeed = Math.abs(gasVelocities[i]) + gasSoundSpeeds[i];
      double liqSpeed = Math.abs(liquidVelocities[i]) + liquidSoundSpeeds[i];
      maxSpeed = Math.max(maxSpeed, Math.max(gasSpeed, liqSpeed));
    }

    return calcStableTimeStep(maxSpeed, dx);
  }

  /**
   * Advance current time by dt.
   *
   * @param dt Time step taken
   */
  public void advanceTime(double dt) {
    currentTime += dt;
  }

  /**
   * Add two 2D arrays element-wise.
   *
   * @param A First array
   * @param B Second array
   * @return A + B
   */
  private double[][] addArrays(double[][] A, double[][] B) {
    int n = A.length;
    int m = A[0].length;
    double[][] C = new double[n][m];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < m; j++) {
        C[i][j] = A[i][j] + B[i][j];
      }
    }
    return C;
  }

  /**
   * Scale a 2D array by a scalar.
   *
   * @param A Array
   * @param s Scalar
   * @return s * A
   */
  private double[][] scaleArray(double[][] A, double s) {
    int n = A.length;
    int m = A[0].length;
    double[][] B = new double[n][m];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < m; j++) {
        B[i][j] = s * A[i][j];
      }
    }
    return B;
  }

  // Getters and setters

  /**
   * Get integration method.
   *
   * @return Current method
   */
  public Method getMethod() {
    return method;
  }

  /**
   * Set integration method.
   *
   * @param method New method
   */
  public void setMethod(Method method) {
    this.method = method;
  }

  /**
   * Get CFL number.
   *
   * @return CFL number
   */
  public double getCflNumber() {
    return cflNumber;
  }

  /**
   * Set CFL number.
   *
   * @param cflNumber New CFL number (0 &lt; CFL &lt; 1)
   */
  public void setCflNumber(double cflNumber) {
    this.cflNumber = Math.max(0.01, Math.min(0.99, cflNumber));
  }

  /**
   * Get minimum time step.
   *
   * @return Minimum time step (s)
   */
  public double getMinTimeStep() {
    return minTimeStep;
  }

  /**
   * Set minimum time step.
   *
   * @param minTimeStep Minimum time step (s)
   */
  public void setMinTimeStep(double minTimeStep) {
    this.minTimeStep = minTimeStep;
  }

  /**
   * Get maximum time step.
   *
   * @return Maximum time step (s)
   */
  public double getMaxTimeStep() {
    return maxTimeStep;
  }

  /**
   * Set maximum time step.
   *
   * @param maxTimeStep Maximum time step (s)
   */
  public void setMaxTimeStep(double maxTimeStep) {
    this.maxTimeStep = maxTimeStep;
  }

  /**
   * Get current simulation time.
   *
   * @return Current time (s)
   */
  public double getCurrentTime() {
    return currentTime;
  }

  /**
   * Set current simulation time.
   *
   * @param currentTime Current time (s)
   */
  public void setCurrentTime(double currentTime) {
    this.currentTime = currentTime;
  }

  /**
   * Get current time step.
   *
   * @return Current time step (s)
   */
  public double getCurrentDt() {
    return currentDt;
  }

  // ============ IMEX Pressure Correction Method ============

  /** Cell-averaged sound speeds for IMEX pressure solve. Set by caller before step. */
  private double[] cellSoundSpeeds;

  /** Cell-averaged mixture densities for IMEX pressure solve. Set by caller before step. */
  private double[] cellDensities;

  /** Cell cross-sectional areas for IMEX pressure/mass corrections. */
  private double[] cellAreas;

  /** Phase densities for IMEX phase-area momentum correction. */
  private double[] cellGasDensities;
  private double[] cellOilDensities;
  private double[] cellWaterDensities;

  /** Void-wave speeds for the implicit interfacial-pressure correction. */
  private double[] cellVoidWaveSpeeds;

  /** Drift-flux slip coefficients of the interfacial-pressure closure (m2/s2). */
  private double[] cellVoidWaveSlipCoefficients;

  /** Minimum gas-liquid holdup product that admits a drift-flux correction. */
  private static final double MIN_VOID_WAVE_PHASE_PRODUCT = 1.0e-3;

  /** Largest slip change the implicit correction may impose in one step (m/s). */
  private static final double MAX_VOID_WAVE_SLIP_CHANGE = 5.0;

  /** Whether to apply the implicit interfacial-pressure correction after the explicit transport step. */
  private boolean implicitVoidWaveEnabled;

  /** Coupled compressible pressure-momentum correction. */
  private final CoupledPressureMomentumSolver coupledPressureMomentumSolver = new CoupledPressureMomentumSolver();

  /** Whether pressure and phase momenta are corrected in the same accepted step. */
  private boolean coupledPressureMomentumEnabled;

  /** Cell pressure supplied to the coupled correction in Pa. */
  private double[] coupledCellPressure;

  /** Cell lengths supplied to the coupled correction in m. */
  private double[] coupledCellLengths;

  /** Phase sound speeds supplied to the coupled correction in m/s. */
  private double[] coupledGasSoundSpeeds;
  private double[] coupledOilSoundSpeeds;
  private double[] coupledWaterSoundSpeeds;

  /** Most recent coupled pressure-momentum result. */
  private CoupledPressureMomentumSolver.Result lastCoupledPressureMomentumResult;

  /** Cell size for IMEX pressure solve (m). Set by caller before step. */
  private double imexDx = 1.0;

  /** Pressure boundary (Pa) at outlet for IMEX. Set by caller before step. */
  private double imexOutletPressure = 1e6;

  /** Whether outlet pressure BC is fixed for IMEX. */
  private boolean imexOutletPressureFixed = true;

  /**
   * Set cell properties required for the IMEX pressure correction step. Must be called before stepping with
   * IMEX_PRESSURE_CORRECTION.
   *
   * @param soundSpeeds sound speed per cell (m/s)
   * @param densities mixture density per cell (kg/m3)
   * @param dx cell size (m)
   * @param outletPressure outlet boundary pressure (Pa)
   * @param outletFixed true if outlet pressure is a Dirichlet BC
   */
  public void setIMEXProperties(double[] soundSpeeds, double[] densities, double dx, double outletPressure,
      boolean outletFixed) {
    this.cellSoundSpeeds = soundSpeeds;
    this.cellDensities = densities;
    this.cellAreas = null;
    this.cellGasDensities = null;
    this.cellOilDensities = null;
    this.cellWaterDensities = null;
    this.imexDx = dx;
    this.imexOutletPressure = outletPressure;
    this.imexOutletPressureFixed = outletFixed;
  }

  /**
   * Set cell and phase properties required for a dimensionally consistent IMEX pressure correction.
   *
   * @param soundSpeeds mixture sound speed per cell (m/s)
   * @param densities mixture density per cell (kg/m3)
   * @param areas pipe cross-sectional area per cell (m2)
   * @param gasDensities gas density per cell (kg/m3)
   * @param oilDensities oil density per cell (kg/m3)
   * @param waterDensities water density per cell (kg/m3)
   * @param dx cell size (m)
   * @param outletPressure outlet boundary pressure (Pa)
   * @param outletFixed true if outlet pressure is a Dirichlet BC
   */
  public void setIMEXProperties(double[] soundSpeeds, double[] densities, double[] areas, double[] gasDensities,
      double[] oilDensities, double[] waterDensities, double dx, double outletPressure, boolean outletFixed) {
    setIMEXProperties(soundSpeeds, densities, dx, outletPressure, outletFixed);
    this.cellAreas = areas;
    this.cellGasDensities = gasDensities;
    this.cellOilDensities = oilDensities;
    this.cellWaterDensities = waterDensities;
  }

  /**
   * Configure the implicit void-wave correction used by the interfacial-pressure closure.
   *
   * @param voidWaveSpeeds Bestion void-wave speed per cell in m/s
   * @param slipCoefficients drift-flux slip coefficient per cell in m2/s2
   * @param areas pipe cross-sectional area per cell in m2
   * @param gasDensities gas density per cell in kg/m3
   * @param oilDensities oil density per cell in kg/m3
   * @param waterDensities water density per cell in kg/m3
   * @param dx cell size in m
   * @param enabled true to apply the implicit correction
   */
  public void setImplicitVoidWaveProperties(double[] voidWaveSpeeds, double[] slipCoefficients, double[] areas,
      double[] gasDensities, double[] oilDensities, double[] waterDensities, double dx, boolean enabled) {
    this.cellVoidWaveSpeeds = voidWaveSpeeds;
    this.cellVoidWaveSlipCoefficients = slipCoefficients;
    this.cellAreas = areas;
    this.cellGasDensities = gasDensities;
    this.cellOilDensities = oilDensities;
    this.cellWaterDensities = waterDensities;
    this.imexDx = dx;
    this.implicitVoidWaveEnabled = enabled;
  }

  /**
   * Configure the coupled pressure-momentum correction.
   *
   * @param pressure cell pressure in Pa
   * @param areas cell cross-sectional areas in m2
   * @param lengths cell axial lengths in m
   * @param gasDensities gas density in kg/m3
   * @param oilDensities oil density in kg/m3
   * @param waterDensities water density in kg/m3
   * @param gasSoundSpeeds gas sound speed in m/s
   * @param oilSoundSpeeds oil sound speed in m/s
   * @param waterSoundSpeeds water sound speed in m/s
   * @param outletPressure fixed outlet pressure in Pa
   * @param outletFixed true when the outlet pressure is a Dirichlet boundary
   * @param enabled true to apply the coupled correction
   */
  public void setCoupledPressureMomentumProperties(double[] pressure, double[] areas, double[] lengths,
      double[] gasDensities, double[] oilDensities, double[] waterDensities, double[] gasSoundSpeeds,
      double[] oilSoundSpeeds, double[] waterSoundSpeeds, double outletPressure, boolean outletFixed, boolean enabled) {
    this.coupledCellPressure = pressure == null ? null : pressure.clone();
    this.cellAreas = areas == null ? null : areas.clone();
    this.coupledCellLengths = lengths == null ? null : lengths.clone();
    this.cellGasDensities = gasDensities == null ? null : gasDensities.clone();
    this.cellOilDensities = oilDensities == null ? null : oilDensities.clone();
    this.cellWaterDensities = waterDensities == null ? null : waterDensities.clone();
    this.coupledGasSoundSpeeds = gasSoundSpeeds == null ? null : gasSoundSpeeds.clone();
    this.coupledOilSoundSpeeds = oilSoundSpeeds == null ? null : oilSoundSpeeds.clone();
    this.coupledWaterSoundSpeeds = waterSoundSpeeds == null ? null : waterSoundSpeeds.clone();
    this.imexOutletPressure = outletPressure;
    this.imexOutletPressureFixed = outletFixed;
    this.coupledPressureMomentumEnabled = enabled;
    if (!enabled) {
      lastCoupledPressureMomentumResult = null;
    }
  }

  /**
   * Enable or disable the coupled pressure-momentum correction.
   *
   * @param enabled true to apply the configured correction
   */
  public void setCoupledPressureMomentumEnabled(boolean enabled) {
    coupledPressureMomentumEnabled = enabled;
    if (!enabled) {
      lastCoupledPressureMomentumResult = null;
    }
  }

  /** @return true when the coupled pressure-momentum correction is enabled */
  public boolean isCoupledPressureMomentumEnabled() {
    return coupledPressureMomentumEnabled;
  }

  private double[][] applyCoupledPressureMomentumCorrection(double[][] state, double dt) {
    if (!coupledPressureMomentumEnabled) {
      return state;
    }
    lastCoupledPressureMomentumResult = coupledPressureMomentumSolver.correct(state, dt, coupledCellPressure, cellAreas,
        coupledCellLengths, cellGasDensities, cellOilDensities, cellWaterDensities, coupledGasSoundSpeeds,
        coupledOilSoundSpeeds, coupledWaterSoundSpeeds, imexOutletPressure, imexOutletPressureFixed);
    return lastCoupledPressureMomentumResult.getState();
  }

  /** @return true when the most recent coupled correction converged */
  public boolean isCoupledPressureMomentumConverged() {
    return lastCoupledPressureMomentumResult != null && lastCoupledPressureMomentumResult.isConverged();
  }

  /** @return maximum relative cell-volume residual from the latest correction */
  public double getCoupledPressureMomentumVolumeResidual() {
    return lastCoupledPressureMomentumResult == null ? Double.NaN
        : lastCoupledPressureMomentumResult.getMaximumRelativeVolumeResidual();
  }

  /** @return nonlinear iterations used by the latest coupled correction */
  public int getCoupledPressureMomentumIterations() {
    return lastCoupledPressureMomentumResult == null ? 0 : lastCoupledPressureMomentumResult.getIterations();
  }

  /** @return signed gas, oil, and water outlet mass corrections in kg */
  public double[] getCoupledPressureMomentumOutletMassCorrectionKg() {
    return lastCoupledPressureMomentumResult == null ? new double[3]
        : lastCoupledPressureMomentumResult.getOutletBoundaryMassCorrectionKg();
  }

  /** @return corrected pressure from the latest coupled correction, or null */
  public double[] getCoupledPressureMomentumPressure() {
    return lastCoupledPressureMomentumResult == null ? null : lastCoupledPressureMomentumResult.getPressure();
  }

  /** @return corrected gas density from the latest coupled correction, or null */
  public double[] getCoupledPressureMomentumGasDensity() {
    return lastCoupledPressureMomentumResult == null ? null : lastCoupledPressureMomentumResult.getGasDensity();
  }

  /** @return corrected oil density from the latest coupled correction, or null */
  public double[] getCoupledPressureMomentumOilDensity() {
    return lastCoupledPressureMomentumResult == null ? null : lastCoupledPressureMomentumResult.getOilDensity();
  }

  /** @return corrected water density from the latest coupled correction, or null */
  public double[] getCoupledPressureMomentumWaterDensity() {
    return lastCoupledPressureMomentumResult == null ? null : lastCoupledPressureMomentumResult.getWaterDensity();
  }

  /**
   * IMEX (Implicit-Explicit) pressure correction step.
   *
   * <p>
   * The algorithm follows a two-stage splitting:
   * </p>
   * <ol>
   * <li><b>Predictor (explicit):</b> Advance mass and momentum using the explicit RHS (advection + source terms) to
   * obtain intermediate values U*.</li>
   * <li><b>Pressure correction (implicit):</b> Solve a tridiagonal Helmholtz equation for the pressure correction dp.
   * The pressure wave equation dp - (c*dt/dx)^2 * d^2(dp)/dx^2 = RHS removes the acoustic CFL constraint.</li>
   * <li><b>Corrector:</b> Update phase momenta using the pressure correction gradient while leaving the phase masses
   * from the conservative predictor unchanged.</li>
   * </ol>
   *
   * <p>
   * This allows the convective CFL (based on material velocity |v|) to govern the time step instead of the acoustic CFL
   * (based on |v|+c). For typical gas-liquid flows where c=300 m/s and v=5 m/s, this gives a factor of ~60 speedup.
   * </p>
   *
   * @param U Current state [nCells][nVars]
   * @param rhs Right-hand side function (explicit part)
   * @param dt Time step
   * @return Updated state at t + dt
   */
  public double[][] stepIMEXPressureCorrection(double[][] U, RHSFunction rhs, double dt) {
    int nCells = U.length;
    int nVars = U[0].length;

    // Stage 1: Explicit predictor (forward Euler on transport terms)
    double[][] dUdt = rhs.evaluate(U, currentTime);
    double[][] Ustar = new double[nCells][nVars];
    for (int i = 0; i < nCells; i++) {
      for (int j = 0; j < nVars; j++) {
        Ustar[i][j] = U[i][j] + dt * dUdt[i][j];
      }
    }

    // Stage 2: Implicit pressure correction
    // Only proceed if cell properties have been set
    if (cellSoundSpeeds == null || cellDensities == null || cellSoundSpeeds.length != nCells) {
      // Fall back to pure explicit Euler if IMEX properties not configured
      return Ustar;
    }

    // Build coefficient for pressure Helmholtz equation:
    // dp_i - sigma_i * (dp_{i-1} - 2*dp_i + dp_{i+1}) = b_i
    // where sigma_i = (c_i * dt / dx)^2
    //
    // The RHS b_i is derived from the mass imbalance after the explicit step.
    // For each phase mass equation (gas=0, oil=1, water=2), the pressure correction
    // restores the divergence-free condition.
    double[] sigma = new double[nCells];
    for (int i = 0; i < nCells; i++) {
      double c = Math.max(cellSoundSpeeds[i], 1.0);
      sigma[i] = (c * dt / imexDx) * (c * dt / imexDx);
    }

    // Compute mass residual from the predicted state
    // The total mass per length at each cell should be consistent with the pressure.
    // Mass residual: r_i = sum_k (Ustar_k,mass,i - U_k,mass,i) where k = gas, oil, water
    // This should be zero for an incompressible system; non-zero part drives dp
    double[] massResidual = new double[nCells];
    for (int i = 0; i < nCells; i++) {
      // Sum of mass changes over all phases (indices 0, 1, 2)
      int nMassEq = Math.min(3, nVars);
      for (int k = 0; k < nMassEq; k++) {
        massResidual[i] += (Ustar[i][k] - U[i][k]);
      }
    }

    // Build tridiagonal system: A * dp = b
    // (1 + 2*sigma) * dp_i - sigma * dp_{i-1} - sigma * dp_{i+1} = b_i
    double[] lower = new double[nCells]; // sub-diagonal
    double[] diag = new double[nCells]; // main diagonal
    double[] upper = new double[nCells]; // super-diagonal
    double[] b = new double[nCells]; // RHS

    for (int i = 0; i < nCells; i++) {
      double sig = sigma[i];
      double c = Math.max(cellSoundSpeeds[i], 1.0);
      double area = getCellArea(i);

      diag[i] = 1.0 + 2.0 * sig;
      lower[i] = -sig;
      upper[i] = -sig;

      // RHS: pressure correction to absorb the mass residual
      // mass per length = area * density, and density correction is dp / c^2.
      b[i] = -c * c * massResidual[i] / area;
    }

    // Boundary conditions for pressure correction
    // Inlet: Neumann (dp/dx = 0) => dp_0 = dp_1 => fold lower into diagonal
    diag[0] += lower[0]; // absorb ghost cell
    lower[0] = 0.0;

    if (imexOutletPressureFixed) {
      // Outlet: Dirichlet dp = 0 (pressure is already fixed at outlet)
      diag[nCells - 1] = 1.0;
      upper[nCells - 1] = 0.0;
      lower[nCells - 1] = 0.0;
      b[nCells - 1] = 0.0;
    } else {
      // Outlet: Neumann (dp/dx = 0)
      diag[nCells - 1] += upper[nCells - 1];
      upper[nCells - 1] = 0.0;
    }

    // Solve tridiagonal system using Thomas algorithm
    double[] dp = solveTridiagonal(lower, diag, upper, b);

    // Stage 3: Correction — update momenta with pressure correction gradient
    // For each momentum equation: U_mom^{n+1} = Ustar_mom - dt/dx * alpha * A * d(dp)/dx
    // Simplified: momentum correction = -dt/dx * dp_gradient * (mass_fraction)
    double[][] Unew = new double[nCells][nVars];
    for (int i = 0; i < nCells; i++) {
      // Copy predicted state
      for (int j = 0; j < nVars; j++) {
        Unew[i][j] = Ustar[i][j];
      }

      // Compute pressure gradient (central difference, one-sided at boundaries)
      double dpdx;
      if (i == 0) {
        dpdx = (dp[1] - dp[0]) / imexDx;
      } else if (i == nCells - 1) {
        dpdx = (dp[i] - dp[i - 1]) / imexDx;
      } else {
        dpdx = (dp[i + 1] - dp[i - 1]) / (2.0 * imexDx);
      }

      double area = getCellArea(i);

      // Phase masses remain exactly as advanced by the conservative explicit
      // predictor. Pressure correction acts on momenta; directly adding area*dp/c^2
      // to cell mass would be an unbalanced volume source.
      double totalMass = 0;
      int nMassEq = Math.min(3, nVars);
      for (int k = 0; k < nMassEq; k++) {
        totalMass += Math.max(Ustar[i][k], 0);
      }

      // Correct momentum equations using the two-fluid pressure source:
      // d(alpha_k * rho_k * v_k * A)/dt = -alpha_k * A * dp/dx.
      // Gas momentum (index 3), Oil momentum (index 4), Water momentum (index 5)
      double gasAreaFallback = (totalMass > 1e-12 && nMassEq > 0) ? Math.max(Ustar[i][0], 0) / totalMass * area : 0.0;
      double oilAreaFallback = (totalMass > 1e-12 && nMassEq > 1) ? Math.max(Ustar[i][1], 0) / totalMass * area : 0.0;
      double waterAreaFallback = (totalMass > 1e-12 && nMassEq > 2) ? Math.max(Ustar[i][2], 0) / totalMass * area : 0.0;

      double gasArea = (nVars > 3) ? getPhaseArea(i, Ustar[i][0], cellGasDensities, gasAreaFallback) : 0.0;
      double oilArea = (nVars > 4) ? getPhaseArea(i, Ustar[i][1], cellOilDensities, oilAreaFallback) : 0.0;
      double waterArea = (nVars > 5) ? getPhaseArea(i, Ustar[i][2], cellWaterDensities, waterAreaFallback) : 0.0;

      double totalPhaseArea = gasArea + oilArea + waterArea;
      if (totalPhaseArea > area && totalPhaseArea > 1e-12) {
        double areaScale = area / totalPhaseArea;
        gasArea *= areaScale;
        oilArea *= areaScale;
        waterArea *= areaScale;
      }

      if (nVars > 3) {
        Unew[i][3] = Ustar[i][3] - dt * gasArea * dpdx;
      }
      if (nVars > 4) {
        Unew[i][4] = Ustar[i][4] - dt * oilArea * dpdx;
      }
      if (nVars > 5) {
        Unew[i][5] = Ustar[i][5] - dt * waterArea * dpdx;
      }
    }

    return applyImplicitVoidWaveCorrection(Unew, dt);
  }

  /**
   * Advance the linearized void-fraction/slip subsystem with backward Euler.
   *
   * <p>
   * The transported variable is the drift flux {@code q = alphaG * alphaL * (vG - vL)}. Eliminating the implicit
   * void-fraction update gives a Helmholtz equation for {@code q}. Mapping the corrected slip back to gas and liquid
   * momenta leaves every phase mass and the cell total momentum unchanged.
   * </p>
   *
   * @param state state after explicit transport and pressure correction
   * @param dt time step in s
   * @return state with the implicit void-wave momentum correction
   */
  private double[][] applyImplicitVoidWaveCorrection(double[][] state, double dt) {
    int nCells = state.length;
    if (!implicitVoidWaveEnabled || cellVoidWaveSpeeds == null || cellVoidWaveSpeeds.length != nCells || nCells < 2) {
      return state;
    }

    double[] alphaGas = new double[nCells];
    double[] driftFlux = new double[nCells];
    double[] lower = new double[nCells];
    double[] diagonal = new double[nCells];
    double[] upper = new double[nCells];
    double[] rightHandSide = new double[nCells];

    for (int i = 0; i < nCells; i++) {
      double area = getCellArea(i);
      double gasArea = getPhaseArea(i, state[i][0], cellGasDensities, 0.0);
      double oilArea = getPhaseArea(i, state[i][1], cellOilDensities, 0.0);
      double waterArea = getPhaseArea(i, state[i][2], cellWaterDensities, 0.0);
      double liquidArea = Math.max(0.0, Math.min(area, oilArea + waterArea));
      alphaGas[i] = Math.max(0.0, Math.min(1.0, gasArea / area));
      double alphaLiquid = Math.max(0.0, Math.min(1.0, liquidArea / area));
      double gasVelocity = state[i][0] > 1.0e-12 ? state[i][3] / state[i][0] : 0.0;
      double liquidMass = state[i][1] + state[i][2];
      double liquidMomentum = state[i][4] + state[i][5];
      double liquidVelocity = liquidMass > 1.0e-12 ? liquidMomentum / liquidMass : 0.0;
      driftFlux[i] = alphaGas[i] * alphaLiquid * (gasVelocity - liquidVelocity);
    }

    for (int i = 0; i < nCells; i++) {
      double waveSpeed = Math.max(0.0, cellVoidWaveSpeeds[i]);
      double sigma = waveSpeed * waveSpeed * dt * dt / (imexDx * imexDx);
      lower[i] = -sigma;
      diagonal[i] = 1.0 + 2.0 * sigma;
      upper[i] = -sigma;

      double alphaGradient;
      if (i == 0) {
        alphaGradient = (alphaGas[1] - alphaGas[0]) / imexDx;
      } else if (i == nCells - 1) {
        alphaGradient = (alphaGas[i] - alphaGas[i - 1]) / imexDx;
      } else {
        alphaGradient = (alphaGas[i + 1] - alphaGas[i - 1]) / (2.0 * imexDx);
      }
      double slipCoefficient = (cellVoidWaveSlipCoefficients != null && i < cellVoidWaveSlipCoefficients.length)
          ? Math.max(0.0, cellVoidWaveSlipCoefficients[i])
          : 0.0;
      rightHandSide[i] = driftFlux[i] - dt * slipCoefficient * alphaGradient;
    }

    diagonal[0] += lower[0];
    lower[0] = 0.0;
    diagonal[nCells - 1] += upper[nCells - 1];
    upper[nCells - 1] = 0.0;
    double[] correctedDriftFlux = solveTridiagonal(lower, diagonal, upper, rightHandSide);

    for (int i = 0; i < nCells; i++) {
      double gasMass = state[i][0];
      double liquidMass = state[i][1] + state[i][2];
      double totalMass = gasMass + liquidMass;
      double area = getCellArea(i);
      double gasArea = getPhaseArea(i, gasMass, cellGasDensities, 0.0);
      double liquidArea = getPhaseArea(i, state[i][1], cellOilDensities, 0.0)
          + getPhaseArea(i, state[i][2], cellWaterDensities, 0.0);
      double alphaProduct = gasArea / area * Math.max(0.0, Math.min(1.0, liquidArea / area));
      if (gasMass <= 1.0e-12 || liquidMass <= 1.0e-12 || totalMass <= 1.0e-12
          || alphaProduct <= MIN_VOID_WAVE_PHASE_PRODUCT) {
        continue;
      }

      double oldGasVelocity = state[i][3] / gasMass;
      double oldLiquidVelocity = (state[i][4] + state[i][5]) / liquidMass;
      double correctedSlip = correctedDriftFlux[i] / alphaProduct;
      double slipChange = correctedSlip - (oldGasVelocity - oldLiquidVelocity);
      if (!Double.isFinite(slipChange)) {
        continue;
      }
      slipChange = Math.max(-MAX_VOID_WAVE_SLIP_CHANGE, Math.min(MAX_VOID_WAVE_SLIP_CHANGE, slipChange));
      double gasVelocityChange = liquidMass / totalMass * slipChange;
      double liquidVelocityChange = -gasMass / totalMass * slipChange;
      state[i][3] += gasMass * gasVelocityChange;
      state[i][4] += state[i][1] * liquidVelocityChange;
      state[i][5] += state[i][2] * liquidVelocityChange;
    }
    return state;
  }

  /**
   * Get cross-sectional area for a cell, falling back to unit area for legacy API users.
   *
   * @param cellIndex cell index
   * @return cross-sectional area (m2)
   */
  private double getCellArea(int cellIndex) {
    if (cellAreas != null && cellIndex < cellAreas.length && cellAreas[cellIndex] > 1e-12) {
      return cellAreas[cellIndex];
    }
    return 1.0;
  }

  /**
   * Convert phase mass per length to phase area alpha*A using the phase density.
   *
   * @param cellIndex cell index
   * @param massPerLength phase mass per length (kg/m)
   * @param phaseDensities phase densities (kg/m3)
   * @param fallbackArea fallback phase area (m2)
   * @return phase area (m2)
   */
  private double getPhaseArea(int cellIndex, double massPerLength, double[] phaseDensities, double fallbackArea) {
    if (massPerLength <= 0.0) {
      return 0.0;
    }
    if (phaseDensities != null && cellIndex < phaseDensities.length && phaseDensities[cellIndex] > 1e-12) {
      double phaseArea = massPerLength / phaseDensities[cellIndex];
      return Math.max(0.0, Math.min(getCellArea(cellIndex), phaseArea));
    }
    return Math.max(0.0, Math.min(getCellArea(cellIndex), fallbackArea));
  }

  /**
   * Solve a tridiagonal system Ax = d using the Thomas algorithm (O(n)).
   *
   * <p>
   * The system has the form: a_i * x_{i-1} + b_i * x_i + c_i * x_{i+1} = d_i for i = 0..n-1, where a_0 = 0, c_{n-1} =
   * 0.
   * </p>
   *
   * @param a sub-diagonal (a[0] is not used)
   * @param bDiag main diagonal
   * @param c super-diagonal (c[n-1] is not used)
   * @param d right-hand side
   * @return solution vector x
   */
  private double[] solveTridiagonal(double[] a, double[] bDiag, double[] c, double[] d) {
    int n = d.length;
    double[] x = new double[n];

    // Forward sweep
    double[] cPrime = new double[n];
    double[] dPrime = new double[n];

    cPrime[0] = c[0] / bDiag[0];
    dPrime[0] = d[0] / bDiag[0];

    for (int i = 1; i < n; i++) {
      double m = a[i] / (bDiag[i] - a[i] * cPrime[i - 1]);
      cPrime[i] = (i < n - 1) ? c[i] / (bDiag[i] - a[i] * cPrime[i - 1]) : 0;
      dPrime[i] = (d[i] - a[i] * dPrime[i - 1]) / (bDiag[i] - a[i] * cPrime[i - 1]);
    }

    // Back substitution
    x[n - 1] = dPrime[n - 1];
    for (int i = n - 2; i >= 0; i--) {
      x[i] = dPrime[i] - cPrime[i] * x[i + 1];
    }

    return x;
  }

  /**
   * Calculate stable time step for IMEX method. The IMEX scheme removes the acoustic CFL constraint, so the time step
   * is limited only by the convective CFL based on material velocities (not sound speed).
   *
   * <p>
   * dt_IMEX = CFL * dx / max(|v_G|, |v_L|)
   * </p>
   *
   * <p>
   * For typical gas-liquid flows where c=300 m/s and v=5 m/s, this gives a speedup factor of ~60 compared to the
   * standard acoustic CFL.
   * </p>
   *
   * @param gasVelocities gas velocities at each cell (m/s)
   * @param liquidVelocities liquid velocities at each cell (m/s)
   * @param dx cell size (m)
   * @return stable time step (s), typically 10-100x larger than acoustic CFL
   */
  public double calcIMEXTimeStep(double[] gasVelocities, double[] liquidVelocities, double dx) {
    double maxMaterialSpeed = 1.0; // Minimum to avoid division by zero

    for (int i = 0; i < gasVelocities.length; i++) {
      double gasSpeed = Math.abs(gasVelocities[i]);
      double liqSpeed = Math.abs(liquidVelocities[i]);
      maxMaterialSpeed = Math.max(maxMaterialSpeed, Math.max(gasSpeed, liqSpeed));
    }

    double dt = cflNumber * dx / maxMaterialSpeed;
    return Math.max(minTimeStep, Math.min(maxTimeStep, dt));
  }

  /**
   * Reset integrator state.
   */
  public void reset() {
    currentTime = 0;
    currentDt = 0.01;
  }
}
