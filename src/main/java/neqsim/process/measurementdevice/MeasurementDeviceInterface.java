package neqsim.process.measurementdevice;

import neqsim.process.measurementdevice.online.OnlineSignal;
import neqsim.util.NamedInterface;

/**
 * <p>
 * MeasurementDeviceInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface MeasurementDeviceInterface extends NamedInterface, java.io.Serializable {
  /**
   * <p>
   * displayResult.
   * </p>
   */
  public void displayResult();

  /**
   * <p>
   * getMeasuredValue.
   * </p>
   *
   * @return Get measured value in unit GetUnit()
   */
  public default double getMeasuredValue() {
    return getMeasuredValue(getUnit());
  }

  /**
   * <p>
   * Get Measured value in specified unit.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getMeasuredValue(String unit);

  /**
   * <p>
   * getOnlineSignal.
   * </p>
   *
   * @return a {@link neqsim.process.measurementdevice.online.OnlineSignal} object
   */
  public OnlineSignal getOnlineSignal();

  /**
   * <p>
   * getMeasuredPercentValue.
   * </p>
   *
   * @return a double
   */
  public double getMeasuredPercentValue();

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
   * getMaximumValue.
   * </p>
   *
   * @return a double
   */
  public double getMaximumValue();

  /**
   * <p>
   * setMaximumValue.
   * </p>
   *
   * @param maximumValue a double
   */
  public void setMaximumValue(double maximumValue);

  /**
   * <p>
   * getMinimumValue.
   * </p>
   *
   * @return a double
   */
  public double getMinimumValue();

  /**
   * <p>
   * setMinimumValue.
   * </p>
   *
   * @param minimumValue a double
   */
  public void setMinimumValue(double minimumValue);

  /**
   * <p>
   * isLogging.
   * </p>
   *
   * @return a boolean
   */
  public boolean isLogging();

  /**
   * <p>
   * setLogging.
   * </p>
   *
   * @param logging a boolean
   */
  public void setLogging(boolean logging);

  /**
   * <p>
   * getOnlineValue.
   * </p>
   *
   * @return a double
   */
  public default double getOnlineValue() {
    return getOnlineSignal().getValue();
  }

  /**
   * <p>
   * isOnlineSignal.
   * </p>
   *
   * @return a boolean
   */
  public boolean isOnlineSignal();

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();
}
