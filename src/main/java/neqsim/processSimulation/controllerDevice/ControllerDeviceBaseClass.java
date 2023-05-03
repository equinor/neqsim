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
  private double Ksp = 1.0;
  private double Tint = 300.0;
  private double TintValue = 0.0;
  private double Tderiv = 300.0;

  /**
   * <p>Constructor for ControllerDeviceBaseClass.</p>
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
    error =
        transmitter.getMeasuredPercentValue() - (controllerSetPoint - transmitter.getMinimumValue())
            / (transmitter.getMaximumValue() - transmitter.getMinimumValue()) * 100;

    TintValue += Ksp / Tint * error * dt;
    double TderivValue = Ksp * Tderiv * (error - oldError) / dt;
    response = initResponse + propConstant * (Ksp * error + TintValue + TderivValue);
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
   * getKsp.
   * </p>
   *
   * @return a double
   */
  public double getKsp() {
    return Ksp;
  }

  /**
   * <p>
   * setKsp.
   * </p>
   *
   * @param Ksp a double
   */
  public void setKsp(double Ksp) {
    this.Ksp = Ksp;
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerParameters(double Ksp, double Ti, double Td) {
    this.setKsp(Ksp);
    this.setTint(Ti);
    this.setTderiv(Td);
  }

  /**
   * <p>
   * getTint.
   * </p>
   *
   * @return a double
   */
  public double getTint() {
    return Tint;
  }

  /**
   * <p>
   * setTint.
   * </p>
   *
   * @param Tint a double
   */
  public void setTint(double Tint) {
    this.Tint = Tint;
  }

  /**
   * <p>
   * getTderiv.
   * </p>
   *
   * @return a double
   */
  public double getTderiv() {
    return Tderiv;
  }

  /**
   * <p>
   * setTderiv.
   * </p>
   *
   * @param Tderiv a double
   */
  public void setTderiv(double Tderiv) {
    this.Tderiv = Tderiv;
  }
}
