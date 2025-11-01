package neqsim.process.measurementdevice;

import java.util.List;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmEvent;
import neqsim.process.alarm.AlarmState;
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

  /**
   * Associates an alarm configuration with the measurement device.
   *
   * @param alarmConfig configuration to apply, or {@code null} to disable alarms
   */
  public void setAlarmConfig(AlarmConfig alarmConfig);

  /**
   * Returns the alarm configuration, or {@code null} if alarms are disabled.
   *
   * @return alarm configuration
   */
  public AlarmConfig getAlarmConfig();

  /**
   * Returns the mutable alarm state for the device.
   *
   * @return alarm state
   */
  public AlarmState getAlarmState();

  /**
   * Evaluates the alarm state using the supplied measurement value.
   *
   * @param measuredValue measured value
   * @param dt simulation time step
   * @param time current simulation time
   * @return events generated during the evaluation
   */
  public List<AlarmEvent> evaluateAlarm(double measuredValue, double dt, double time);

  /**
   * Acknowledges the currently active alarm if one exists.
   *
   * @param time simulation time of the acknowledgement
   * @return acknowledgement event, or {@code null} if nothing was acknowledged
   */
  public AlarmEvent acknowledgeAlarm(double time);

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();
}
