package neqsim.process.ml.controllers;

/**
 * Proportional (P) controller for single setpoint tracking.
 *
 * <p>
 * Simple controller: action = Kp * error
 *
 * @author ESOL
 * @version 1.0
 */
public class ProportionalController implements Controller {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final int errorIndex;
  private final double kp;
  private final double actionMin;
  private final double actionMax;

  /**
   * Create P controller.
   *
   * @param name controller name
   * @param errorIndex index of error in observation array
   * @param kp proportional gain
   * @param actionMin minimum action value
   * @param actionMax maximum action value
   */
  public ProportionalController(String name, int errorIndex, double kp, double actionMin,
      double actionMax) {
    this.name = name;
    this.errorIndex = errorIndex;
    this.kp = kp;
    this.actionMin = actionMin;
    this.actionMax = actionMax;
  }

  @Override
  public double[] computeAction(double[] observation) {
    double error = observation[errorIndex];
    double action = -kp * error; // Negative because error = actual - setpoint
    action = Math.max(actionMin, Math.min(actionMax, action));
    return new double[] {action};
  }

  @Override
  public String getName() {
    return name;
  }
}
