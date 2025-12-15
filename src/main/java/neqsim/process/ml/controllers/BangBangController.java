package neqsim.process.ml.controllers;

/**
 * Bang-bang (on-off) controller with hysteresis.
 *
 * <p>
 * Simple threshold-based control with deadband to prevent oscillation.
 *
 * @author ESOL
 * @version 1.0
 */
public class BangBangController implements Controller {
  private static final long serialVersionUID = 1000L;

  private final String name;
  private final int valueIndex;
  private final double setpoint;
  private final double deadband;
  private final double lowAction;
  private final double highAction;

  private double currentAction;

  /**
   * Create bang-bang controller.
   *
   * @param name controller name
   * @param valueIndex index of controlled variable in observation
   * @param setpoint target setpoint
   * @param deadband hysteresis band (±deadband around setpoint)
   * @param lowAction action when below setpoint
   * @param highAction action when above setpoint
   */
  public BangBangController(String name, int valueIndex, double setpoint, double deadband,
      double lowAction, double highAction) {
    this.name = name;
    this.valueIndex = valueIndex;
    this.setpoint = setpoint;
    this.deadband = deadband;
    this.lowAction = lowAction;
    this.highAction = highAction;
    this.currentAction = (lowAction + highAction) / 2.0;
  }

  @Override
  public double[] computeAction(double[] observation) {
    double value = observation[valueIndex];

    if (value < setpoint - deadband) {
      currentAction = lowAction;
    } else if (value > setpoint + deadband) {
      currentAction = highAction;
    }
    // Within deadband: maintain previous action

    return new double[] {currentAction};
  }

  @Override
  public void reset() {
    currentAction = (lowAction + highAction) / 2.0;
  }

  @Override
  public String getName() {
    return name;
  }
}
