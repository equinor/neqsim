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
    SSP_RK3
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

  /**
   * Reset integrator state.
   */
  public void reset() {
    currentTime = 0;
    currentDt = 0.01;
  }
}
