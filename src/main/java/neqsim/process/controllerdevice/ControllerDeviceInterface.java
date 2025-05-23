package neqsim.process.controllerdevice;

import java.util.UUID;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * <p>
 * ControllerDeviceInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface ControllerDeviceInterface extends java.io.Serializable {
  /**
   * <p>
   * getMeasuredValue.
   * </p>
   *
   * @return a double
   */
  public double getMeasuredValue();

  /**
   * <p>
   * setControllerSetPoint.
   * </p>
   *
   * @param signal a double
   */
  public void setControllerSetPoint(double signal);

  /**
   * <p>
   * getUnit.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public String getUnit();

  /**
   * <p>
   * setUnit.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   */
  public void setUnit(String unit);

  /**
   * <p>
   * setTransmitter.
   * </p>
   *
   * @param device a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface}
   *        object
   */
  public void setTransmitter(MeasurementDeviceInterface device);

  /**
   * <p>
   * runTransient.
   * </p>
   * Calculates controller output. Sets calc identifier UUID.
   *
   * @param initResponse Init value for response calculation
   * @param dt Delta time [s]
   */
  public default void runTransient(double initResponse, double dt) {
    runTransient(initResponse, dt, UUID.randomUUID());
  }

  /**
   * <p>
   * runTransient.
   * </p>
   * Calculates controller output. Sets calc identifier UUID.
   *
   * @param initResponse Init value for response calculation
   * @param dt Delta time [s]
   * @param id Calculation identifier
   */
  public void runTransient(double initResponse, double dt, UUID id);

  /**
   * <p>
   * getResponse.
   * </p>
   *
   * @return a double
   */
  public double getResponse();

  /**
   * <p>
   * isReverseActing.
   * </p>
   *
   * @return a boolean
   */
  public boolean isReverseActing();

  /**
   * <p>
   * setReverseActing.
   * </p>
   *
   * @param reverseActing a boolean
   */
  public void setReverseActing(boolean reverseActing);

  /**
   * <p>
   * Set PID tuning parameters.
   * </p>
   *
   * @param Kp Proportional gain
   * @param Ti Integral time in seconds
   * @param Td Derivative time in seconds
   */
  public void setControllerParameters(double Kp, double Ti, double Td);

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();

  /**
   * <p>
   * setActive.
   * </p>
   * Set if controller is active
   *
   * @param isActive Set true to make controller active.
   */
  public void setActive(boolean isActive);

  /**
   * <p>
   * isActive.
   * </p>
   * Specifies if controller is active
   *
   * @return a boolean
   */
  public boolean isActive();
}
