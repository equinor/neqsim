package neqsim.process.controllerdevice;

import java.io.Serializable;

/**
 * Event log entry capturing controller state at a particular simulation time.
 * Each entry stores the measurement, set-point, error and controller response
 * enabling post-run performance analysis.
 */
public class ControllerEvent implements Serializable {
  private static final long serialVersionUID = 1L;

  private final double time;
  private final double measuredValue;
  private final double setPoint;
  private final double error;
  private final double response;

  /**
   * Create a new event log entry.
   *
   * @param time simulation time of the event
   * @param measuredValue current transmitter measurement
   * @param setPoint active controller set-point
   * @param error controller error at this time
   * @param response controller output signal
   */
  public ControllerEvent(double time, double measuredValue, double setPoint, double error,
      double response) {
    this.time = time;
    this.measuredValue = measuredValue;
    this.setPoint = setPoint;
    this.error = error;
    this.response = response;
  }

  /**
   * @return simulation time of the event
   */
  public double getTime() {
    return time;
  }

  /**
   * @return measured process value
   */
  public double getMeasuredValue() {
    return measuredValue;
  }

  /**
   * @return controller set-point
   */
  public double getSetPoint() {
    return setPoint;
  }

  /**
   * @return control error
   */
  public double getError() {
    return error;
  }

  /**
   * @return controller output
   */
  public double getResponse() {
    return response;
  }
}
