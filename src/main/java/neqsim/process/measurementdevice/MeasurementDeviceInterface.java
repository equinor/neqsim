package neqsim.process.measurementdevice;

import java.util.List;
import neqsim.process.ProcessElementInterface;
import neqsim.process.alarm.AlarmConfig;
import neqsim.process.alarm.AlarmEvent;
import neqsim.process.alarm.AlarmState;
import neqsim.process.measurementdevice.online.OnlineSignal;

/**
 * MeasurementDeviceInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface MeasurementDeviceInterface extends ProcessElementInterface {
  /**
   * displayResult.
   */
  public void displayResult();

  /**
   * getMeasuredValue.
   *
   * @return Get measured value in unit GetUnit()
   */
  public default double getMeasuredValue() {
    return getMeasuredValue(getUnit());
  }

  /**
   * Get Measured value in specified unit.
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public double getMeasuredValue(String unit);

  /**
   * getOnlineSignal.
   *
   * @return a {@link neqsim.process.measurementdevice.online.OnlineSignal} object
   */
  public OnlineSignal getOnlineSignal();

  /**
   * getMeasuredPercentValue.
   *
   * @return a double
   */
  public double getMeasuredPercentValue();

  /**
   * getUnit.
   *
   * @return a {@link java.lang.String} object
   */
  public String getUnit();

  /**
   * setUnit.
   *
   * @param unit a {@link java.lang.String} object
   */
  public void setUnit(String unit);

  /**
   * getMaximumValue.
   *
   * @return a double
   */
  public double getMaximumValue();

  /**
   * setMaximumValue.
   *
   * @param maximumValue a double
   */
  public void setMaximumValue(double maximumValue);

  /**
   * getMinimumValue.
   *
   * @return a double
   */
  public double getMinimumValue();

  /**
   * setMinimumValue.
   *
   * @param minimumValue a double
   */
  public void setMinimumValue(double minimumValue);

  /**
   * isLogging.
   *
   * @return a boolean
   */
  public boolean isLogging();

  /**
   * setLogging.
   *
   * @param logging a boolean
   */
  public void setLogging(boolean logging);

  /**
   * getOnlineValue.
   *
   * @return a double
   */
  public default double getOnlineValue() {
    return getOnlineSignal().getValue();
  }

  /**
   * isOnlineSignal.
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

  /**
   * Returns the instrument tag identifier used for field data integration. The tag maps this instrument to a signal in
   * the plant historian or data source (e.g. "PT-101", "TT-201").
   *
   * @return tag string, or empty string if not set
   */
  public String getTag();

  /**
   * Sets the instrument tag identifier for field data integration.
   *
   * @param tag tag string mapping to a plant historian signal
   */
  public void setTag(String tag);

  /**
   * Returns the role of this instrument in a digital-twin or field data integration context.
   *
   * @return the tag role, defaulting to {@link InstrumentTagRole#VIRTUAL}
   */
  public InstrumentTagRole getTagRole();

  /**
   * Sets the role of this instrument for field data integration.
   *
   * @param role the tag role to assign
   */
  public void setTagRole(InstrumentTagRole role);

  /**
   * Returns the field data value received from the external data source. Only meaningful for
   * {@link InstrumentTagRole#INPUT} and {@link InstrumentTagRole#BENCHMARK} roles.
   *
   * @return the field value, or {@code Double.NaN} if not set
   */
  public double getFieldValue();

  /**
   * Sets the field data value received from the external data source.
   *
   * @param value the field measurement value
   */
  public void setFieldValue(double value);

  /**
   * Returns whether a field data value has been set.
   *
   * @return true if a field value is available
   */
  public boolean hasFieldValue();

  /**
   * Returns the deviation between the model-calculated value and the field value. Computed as
   * {@code getMeasuredValue() - getFieldValue()}. Useful for {@link InstrumentTagRole#BENCHMARK} instruments.
   *
   * @return deviation (model minus field), or {@code Double.NaN} if no field value is set
   */
  public default double getDeviation() {
    if (!hasFieldValue()) {
      return Double.NaN;
    }
    return getMeasuredValue() - getFieldValue();
  }

  /**
   * Returns the relative deviation between model and field as a percentage. Computed as
   * {@code (getMeasuredValue() - getFieldValue()) / getFieldValue() * 100.0}.
   *
   * @return relative deviation in percent, or {@code Double.NaN} if no field value is set or field value is zero
   */
  public default double getRelativeDeviation() {
    if (!hasFieldValue() || getFieldValue() == 0.0) {
      return Double.NaN;
    }
    return (getMeasuredValue() - getFieldValue()) / getFieldValue() * 100.0;
  }

  /**
   * Applies the field value to the connected stream or equipment. Only effective for instruments with role
   * {@link InstrumentTagRole#INPUT}. Subclasses override to push the field value into their specific model property
   * (pressure, temperature, flow, etc.).
   */
  public default void applyFieldValue() {
    // No-op by default; stream-based subclasses override
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o);

  /** {@inheritDoc} */
  @Override
  public int hashCode();
}
