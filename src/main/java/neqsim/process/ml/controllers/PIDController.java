package neqsim.process.ml.controllers;

/**
 * PID controller for setpoint tracking with integral and derivative action.
 *
 * <p>
 * action = Kp * error + Ki * integral(error) + Kd * d(error)/dt
 *
 * @author ESOL
 * @version 1.0
 */
public class PIDController implements Controller {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final int errorIndex;
  private final double kp;
  private final double ki;
  private final double kd;
  private final double actionMin;
  private final double actionMax;
  private final double dt;

  // State
  private double integral = 0.0;
  private double previousError = 0.0;
  private boolean firstStep = true;

  /**
   * Create PID controller.
   *
   * @param name controller name
   * @param errorIndex index of error in observation array
   * @param kp proportional gain
   * @param ki integral gain
   * @param kd derivative gain
   * @param actionMin minimum action value
   * @param actionMax maximum action value
   * @param dt time step for integration
   */
  public PIDController(String name, int errorIndex, double kp, double ki, double kd,
      double actionMin, double actionMax, double dt) {
    this.name = name;
    this.errorIndex = errorIndex;
    this.kp = kp;
    this.ki = ki;
    this.kd = kd;
    this.actionMin = actionMin;
    this.actionMax = actionMax;
    this.dt = dt;
  }

  @Override
  public double[] computeAction(double[] observation) {
    double error = observation[errorIndex];

    // Proportional term
    double pTerm = -kp * error;

    // Integral term (with anti-windup)
    integral += error * dt;
    integral = Math.max(-1.0 / ki, Math.min(1.0 / ki, integral)); // Anti-windup
    double iTerm = -ki * integral;

    // Derivative term
    double dTerm = 0.0;
    if (!firstStep) {
      double derivative = (error - previousError) / dt;
      dTerm = -kd * derivative;
    }
    previousError = error;
    firstStep = false;

    // Combine and clamp
    double action = pTerm + iTerm + dTerm;
    action = Math.max(actionMin, Math.min(actionMax, action));

    return new double[] {action};
  }

  @Override
  public void reset() {
    integral = 0.0;
    previousError = 0.0;
    firstStep = true;
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * Get current integral value.
   *
   * @return integral
   */
  public double getIntegral() {
    return integral;
  }
}
