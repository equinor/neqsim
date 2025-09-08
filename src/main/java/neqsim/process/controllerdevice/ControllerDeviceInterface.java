package neqsim.process.controllerdevice;

import java.util.List;
import java.util.UUID;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;

/**
 * General contract for feedback controllers operating on measurement devices in
 * NeqSim. Implementations typically provide proportional-integral-derivative
 * (PID) control but the API is prepared for other regulators. The interface
 * exposes unit-aware set points and measurements, tuning parameters, auto
 * tuning hooks, gain scheduling and performance logging features.
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
   * getMeasuredValue.
   * </p>
   *
   * @param unit a {@link java.lang.String} object
   * @return a double
   */
  public default double getMeasuredValue(String unit) {
    return getMeasuredValue();
  }

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
   * setControllerSetPoint.
   * </p>
   *
   * @param signal a double
   * @param unit a {@link java.lang.String} object
   */
  public default void setControllerSetPoint(double signal, String unit) {
    setUnit(unit);
    setControllerSetPoint(signal);
  }

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

  /**
   * <p>
   * Set minimum and maximum controller output for anti-windup handling.
   * </p>
   *
   * @param min Minimum controller response
   * @param max Maximum controller response
   */
  public default void setOutputLimits(double min, double max) {}

  /**
   * <p>
   * Set derivative filter time constant. Set to zero to disable filtering.
   * </p>
   *
   * @param timeConstant Filter time constant in seconds
   */
  public default void setDerivativeFilterTime(double timeConstant) {}

  /**
   * <p>
   * Auto tune controller using ultimate gain and period from a closed-loop test.
   * </p>
   *
   * @param ultimateGain Ultimate gain where oscillations start
   * @param ultimatePeriod Ultimate period of sustained oscillations [s]
   */
  public default void autoTune(double ultimateGain, double ultimatePeriod) {}

  /**
   * <p>
   * Auto tune controller from an open-loop step response using process gain, time constant and
   * dead time.
   * </p>
   *
   * @param processGain Process gain from step response
   * @param timeConstant Process time constant [s]
   * @param deadTime Process dead time [s]
   */
  public default void autoTuneStepResponse(double processGain, double timeConstant,
      double deadTime) {}

  /**
   * <p>
   * Add a gain schedule point that switches controller parameters when the measured value exceeds
   * the specified threshold.
   * </p>
   *
   * @param processValue Measurement threshold for parameter set
   * @param Kp Proportional gain at this operating point
   * @param Ti Integral time [s] at this operating point
   * @param Td Derivative time [s] at this operating point
   */
  public default void addGainSchedulePoint(double processValue, double Kp, double Ti, double Td) {}

  /**
   * <p>
   * Retrieve the controller event log.
   * </p>
   *
   * @return list of controller events
   */
  public default List<ControllerEvent> getEventLog() {
    return java.util.Collections.emptyList();
  }

  /**
   * <p>
   * Reset the controller event log.
   * </p>
   */
  public default void resetEventLog() {}

  /**
   * <p>
   * Get the integral of absolute error accumulated during the simulation.
   * </p>
   *
   * @return integral of absolute error
   */
  public default double getIntegralAbsoluteError() {
    return 0.0;
  }

  /**
   * <p>
   * Get the settling time computed from the event log.
   * </p>
   *
   * @return settling time in seconds
   */
  public default double getSettlingTime() {
    return 0.0;
  }

  /**
   * <p>
   * Reset accumulated performance metrics.
   * </p>
   */
  public default void resetPerformanceMetrics() {}

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
