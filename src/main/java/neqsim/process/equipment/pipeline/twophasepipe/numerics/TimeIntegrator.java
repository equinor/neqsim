package neqsim.process.equipment.pipeline.twophasepipe.numerics;

import java.io.Serializable;

/**
 * Time integration methods for the two-fluid transient pipe model.
 *
 * <p>
 * Provides explicit time stepping algorithms with CFL-based adaptive time step control. The primary
 * method is the classical 4th-order Runge-Kutta (RK4) scheme.
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
 * The time step is limited by: dt ≤ CFL * dx / (|v| + c) where c is the sound speed and CFL is
 * typically 0.5-0.9 for explicit schemes.
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
     * IMEX (Implicit-Explicit) pressure correction method. Treats transport (advection) explicitly
     * and the pressure wave equation implicitly, removing the acoustic CFL constraint. Allows time
     * steps 10-100x larger than fully explicit schemes for long pipelines. Based on the
     * pressure-correction approach of Harlow and Amsden (1971) adapted for two-fluid models.
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
  public TimeIntegrator() {}

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
    switch (method) {
      case EULER:
        return stepEuler(U, rhs, dt);
      case RK2:
        return stepRK2(U, rhs, dt);
      case RK4:
        return stepRK4(U, rhs, dt);
      case SSP_RK3:
        return stepSSPRK3(U, rhs, dt);
      case IMEX_PRESSURE_CORRECTION:
        return stepIMEXPressureCorrection(U, rhs, dt);
      default:
        return stepRK4(U, rhs, dt);
    }
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
  public double calcTwoFluidTimeStep(double[] gasVelocities, double[] liquidVelocities,
      double[] gasSoundSpeeds, double[] liquidSoundSpeeds, double dx) {
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

  /** Cell size for IMEX pressure solve (m). Set by caller before step. */
  private double imexDx = 1.0;

  /** Pressure boundary (Pa) at outlet for IMEX. Set by caller before step. */
  private double imexOutletPressure = 1e6;

  /** Whether outlet pressure BC is fixed for IMEX. */
  private boolean imexOutletPressureFixed = true;

  /**
   * Set cell properties required for the IMEX pressure correction step. Must be called before
   * stepping with IMEX_PRESSURE_CORRECTION.
   *
   * @param soundSpeeds sound speed per cell (m/s)
   * @param densities mixture density per cell (kg/m3)
   * @param dx cell size (m)
   * @param outletPressure outlet boundary pressure (Pa)
   * @param outletFixed true if outlet pressure is a Dirichlet BC
   */
  public void setIMEXProperties(double[] soundSpeeds, double[] densities, double dx,
      double outletPressure, boolean outletFixed) {
    this.cellSoundSpeeds = soundSpeeds;
    this.cellDensities = densities;
    this.imexDx = dx;
    this.imexOutletPressure = outletPressure;
    this.imexOutletPressureFixed = outletFixed;
  }

  /**
   * IMEX (Implicit-Explicit) pressure correction step.
   *
   * <p>
   * The algorithm follows a two-stage splitting:
   * </p>
   * <ol>
   * <li><b>Predictor (explicit):</b> Advance mass and momentum using the explicit RHS (advection +
   * source terms) to obtain intermediate values U*.</li>
   * <li><b>Pressure correction (implicit):</b> Solve a tridiagonal Helmholtz equation for the
   * pressure correction dp that enforces mass conservation implicitly. The pressure wave equation
   * dp - (c*dt/dx)^2 * d^2(dp)/dx^2 = RHS removes the acoustic CFL constraint.</li>
   * <li><b>Corrector:</b> Update momenta using the pressure correction gradient.</li>
   * </ol>
   *
   * <p>
   * This allows the convective CFL (based on material velocity |v|) to govern the time step instead
   * of the acoustic CFL (based on |v|+c). For typical gas-liquid flows where c=300 m/s and v=5 m/s,
   * this gives a factor of ~60 speedup.
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
      double rho = Math.max(cellDensities[i], 0.1);
      double c = Math.max(cellSoundSpeeds[i], 1.0);

      diag[i] = 1.0 + 2.0 * sig;
      lower[i] = -sig;
      upper[i] = -sig;

      // RHS: pressure correction to absorb the mass residual
      // dp ~ -c^2 * (mass_residual / rho) to restore proper density-pressure coupling
      b[i] = -c * c * massResidual[i] / (rho * imexDx);
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

      // Correct mass equations with implicit pressure contribution
      // The pressure correction modifies the mass flux divergence
      double rho = Math.max(cellDensities[i], 0.1);
      double c = Math.max(cellSoundSpeeds[i], 1.0);
      double massCorrectionTotal = dp[i] * rho / (c * c) * imexDx;

      // Distribute mass correction proportionally to existing phase masses
      double totalMass = 0;
      int nMassEq = Math.min(3, nVars);
      for (int k = 0; k < nMassEq; k++) {
        totalMass += Math.max(Ustar[i][k], 0);
      }
      if (totalMass > 1e-12) {
        for (int k = 0; k < nMassEq; k++) {
          double fraction = Math.max(Ustar[i][k], 0) / totalMass;
          Unew[i][k] = Ustar[i][k] + fraction * massCorrectionTotal;
        }
      }

      // Correct momentum equations: subtract pressure gradient
      // Gas momentum (index 3), Oil momentum (index 4), Water momentum (index 5)
      if (nVars > 3) {
        // Gas momentum correction
        double gasAlpha = (totalMass > 1e-12) ? Math.max(Ustar[i][0], 0) / totalMass : 0.33;
        Unew[i][3] = Ustar[i][3] - dt * gasAlpha * dpdx;
      }
      if (nVars > 4) {
        // Oil momentum correction
        double oilAlpha = (totalMass > 1e-12) ? Math.max(Ustar[i][1], 0) / totalMass : 0.33;
        Unew[i][4] = Ustar[i][4] - dt * oilAlpha * dpdx;
      }
      if (nVars > 5) {
        // Water momentum correction
        double waterAlpha = (totalMass > 1e-12) ? Math.max(Ustar[i][2], 0) / totalMass : 0.33;
        Unew[i][5] = Ustar[i][5] - dt * waterAlpha * dpdx;
      }
    }

    return Unew;
  }

  /**
   * Solve a tridiagonal system Ax = d using the Thomas algorithm (O(n)).
   *
   * <p>
   * The system has the form: a_i * x_{i-1} + b_i * x_i + c_i * x_{i+1} = d_i for i = 0..n-1, where
   * a_0 = 0, c_{n-1} = 0.
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
   * Calculate stable time step for IMEX method. The IMEX scheme removes the acoustic CFL
   * constraint, so the time step is limited only by the convective CFL based on material velocities
   * (not sound speed).
   *
   * <p>
   * dt_IMEX = CFL * dx / max(|v_G|, |v_L|)
   * </p>
   *
   * <p>
   * For typical gas-liquid flows where c=300 m/s and v=5 m/s, this gives a speedup factor of ~60
   * compared to the standard acoustic CFL.
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
