package neqsim.processSimulation.controllerDevice;

import java.util.UUID;
import neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface;

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
   * @param device a {@link neqsim.processSimulation.measurementDevice.MeasurementDeviceInterface}
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
   * setControllerParameters.
   * </p>
   *
   * @param Ksp a double
   * @param Ti a double
   * @param Td a double
   */
  public void setControllerParameters(double Ksp, double Ti, double Td);

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();
}
