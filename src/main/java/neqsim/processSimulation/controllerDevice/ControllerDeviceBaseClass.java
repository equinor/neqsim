/*
 * ControllerDeviceBaseClass.java
 *
 * Created on 10. oktober 2006, 19:59
 */

package neqsim.processSimulation.controllerDevice;

import java.util.UUID;
import neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface;
import neqsim.util.NamedBaseClass;

/**
 * <p>
 * ControllerDeviceBaseClass class.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class ControllerDeviceBaseClass extends NamedBaseClass implements ControllerDeviceInterface {
  private static final long serialVersionUID = 1000;

  /**
   * Unique identifier of which solve/run call was last called successfully.
   */
  protected UUID calcIdentifier;

  private String unit = "[?]";
  private MeasurementDeviceInterface transmitter = null;
  private double controllerSetPoint = 0.0;
  private double oldError = 0.0;
  private double oldoldError = 0.0;
  private double error = 0.0;
  private double response = 30.0;
  int propConstant = 1;
  private boolean reverseActing = false;
  private double Kp = 1.0;
  private double Ti = 300.0;
  private double Td = 0.0;

  // Internal state of integration contribution
  private double TintValue = 0.0;

  /**
   * <p>
   * Constructor for ControllerDeviceBaseClass.
   * </p>
   */
  public ControllerDeviceBaseClass() {
    super("controller");
  }

  /** {@inheritDoc} */
  @Override
  public void setTransmitter(MeasurementDeviceInterface device) {
    this.transmitter = device;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    return this.transmitter.getMeasuredValue();
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    if (isReverseActing()) {
      propConstant = -1;
    }
    oldoldError = error;
    oldError = error;

    // Error is normalized
    error =
        transmitter.getMeasuredPercentValue() - (controllerSetPoint - transmitter.getMinimumValue())
            / (transmitter.getMaximumValue() - transmitter.getMinimumValue()) * 100;

    if (Ti > 0)
      TintValue += Kp / Ti * error * dt;
    double TderivValue = Kp * Td * (error - oldError) / dt;
    response = initResponse + propConstant * (Kp * error + TintValue + TderivValue);
    // System.out.println("error " + error + " %");
    // error = device.getMeasuredPercentValue()-controlValue;
    // double regulatorSignal = error*1.0;
    calcIdentifier = id;
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerSetPoint(double signal) {
    this.controllerSetPoint = signal;
  }

  /** {@inheritDoc} */
  @Override
  public String getUnit() {
    return unit;
  }

  /** {@inheritDoc} */
  @Override
  public void setUnit(String unit) {
    this.unit = unit;
  }

  /** {@inheritDoc} */
  @Override
  public double getResponse() {
    return response;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isReverseActing() {
    return reverseActing;
  }

  /** {@inheritDoc} */
  @Override
  public void setReverseActing(boolean reverseActing) {
    this.reverseActing = reverseActing;
  }

  /**
   * <p>
   * Get proportional gain of PID controller.
   * </p>
   *
   * @return Proportional gain of PID controller
   */
  public double getKp() {
    return Kp;
  }

  /**
   * <p>
   * Set proportional gain of PID controller.
   * </p>
   *
   * @param Kp Proportional gain of PID controller
   */
  public void setKp(double Kp) {
    this.Kp = Kp;
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerParameters(double Kp, double Ti, double Td) {
    this.setKp(Kp);
    this.setTi(Ti);
    this.setTd(Td);
  }

  /**
   * <p>
   * Get integral time of PID controller.
   * </p>
   *
   * @return Integral time in seconds
   */
  public double getTi() {
    return Ti;
  }

  /**
   * <p>
   * Set integral time of PID controller.
   * </p>
   *
   * @param Ti Integral time in seconds
   */
  public void setTi(double Ti) {
    this.Ti = Ti;
  }

  /**
   * <p>
   * Get derivative time of PID controller.
   * </p>
   *
   * @return Derivative time of controller
   */
  public double getTd() {
    return Td;
  }

  /**
   * <p>
   * Set derivative time of PID controller.
   * </p>
   *
   * @param Td Derivative time in seconds
   */
  public void setTd(double Td) {
    this.Td = Td;
  }
}
