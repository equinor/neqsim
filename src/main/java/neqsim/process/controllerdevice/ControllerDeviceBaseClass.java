/*
 * ControllerDeviceBaseClass.java
 *
 * Created on 10. oktober 2006, 19:59
 */

package neqsim.process.controllerdevice;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.util.NamedBaseClass;

/**
 * Discrete PID controller implementation providing common features for process control in NeqSim.
 * The class supports anti-windup clamping, derivative filtering, gain scheduling, event logging and
 * performance metrics as well as auto-tuning utilities.
 *
 * <p>
 * The controller operates on a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface}
 * transmitter and exposes a standard PID API through
 * {@link neqsim.process.controllerdevice.ControllerDeviceInterface}.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class ControllerDeviceBaseClass extends NamedBaseClass implements ControllerDeviceInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(ControllerDeviceBaseClass.class);

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
  private double derivativeState = 0.0;
  private double derivativeFilterTime = 0.0;
  private double minResponse = Double.NEGATIVE_INFINITY;
  private double maxResponse = Double.POSITIVE_INFINITY;
  boolean isActive = true;
  private NavigableMap<Double, double[]> gainSchedule = new TreeMap<>();
  private java.util.List<ControllerEvent> eventLog = new java.util.ArrayList<>();
  private double totalTime = 0.0;
  private double integralAbsoluteError = 0.0;
  private double lastTimeOutsideBand = 0.0;
  private double settlingTolerance = 0.02;

  /**
   * <p>
   * Constructor for ControllerDeviceBaseClass.
   * </p>
   */
  public ControllerDeviceBaseClass() {
    this("controller");
  }

  /** {@inheritDoc} */
  @Override
  public void setActive(boolean isActive) {
    this.isActive = isActive;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isActive() {
    return isActive;
  }

  /**
   * <p>
   * Constructor for ControllerDeviceBaseClass.
   * </p>
   *
   * @param name Name of PID controller object
   */
  public ControllerDeviceBaseClass(String name) {
    super(name);
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
  public double getMeasuredValue(String unit) {
    if (unit == null || unit.isEmpty() || unit.equals("[?]")) {
      return this.transmitter.getMeasuredValue();
    }
    return this.transmitter.getMeasuredValue(unit);
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * If no engineering unit is configured, the controller falls back to the legacy percent-based
   * error formulation used by earlier NeqSim versions.
   * </p>
   */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    if (!isActive) {
      totalTime += dt;
      response = initResponse;
      calcIdentifier = id;
      return;
    }
    totalTime += dt;
    if (isReverseActing()) {
      propConstant = -1;
    }
    double measurement = getMeasuredValue(unit);
    applyGainSchedule(measurement);
    oldoldError = error;
    oldError = error;

    double band = 0.0;
    double TintIncrement = 0.0;
    double derivative = 0.0;
    double delta = 0.0;

    if (unit == null || unit.isEmpty() || unit.equals("[?]")) {
      double measurementPercent = transmitter.getMeasuredPercentValue();
      double setPointPercent = (controllerSetPoint - transmitter.getMinimumValue())
          / (transmitter.getMaximumValue() - transmitter.getMinimumValue()) * 100.0;
      error = measurementPercent - setPointPercent;
      if (Ti != 0) {
        TintValue = Kp / Ti * error;
      }
      double TderivValue = Kp * Td * ((error - 2 * oldError + oldoldError) / (dt * dt));
      response = initResponse
          + propConstant * ((Kp * (error - oldError) / dt) + TintValue + TderivValue) * dt;
    } else {
      error = measurement - controllerSetPoint;
      integralAbsoluteError += Math.abs(error) * dt;
      band = settlingTolerance * Math.max(Math.abs(controllerSetPoint), 1.0);
      if (Math.abs(error) > band) {
        lastTimeOutsideBand = totalTime;
      }
      TintIncrement = 0.0;
      if (Ti > 0) {
        TintIncrement = Kp / Ti * error * dt;
        TintValue += TintIncrement;
      } else {
        TintValue = 0.0;
      }

      derivative = (error - oldError) / dt;
      if (Td > 0) {
        if (derivativeFilterTime > 0) {
          derivativeState += dt / (derivativeFilterTime + dt) * (derivative - derivativeState);
        } else {
          derivativeState = derivative;
        }
      } else {
        derivativeState = 0.0;
      }

      delta = Kp * (error - oldError) + TintValue + Kp * Td * derivativeState;

      response = initResponse + propConstant * delta;

      if (response > maxResponse) {
        response = maxResponse;
        if (Ti > 0) {
          TintValue -= TintIncrement;
        }
      } else if (response < minResponse) {
        response = minResponse;
        if (Ti > 0) {
          TintValue -= TintIncrement;
        }
      }

      eventLog
          .add(new ControllerEvent(totalTime, measurement, controllerSetPoint, error, response));
    }

    calcIdentifier = id;
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerSetPoint(double signal) {
    this.controllerSetPoint = signal;
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerSetPoint(double signal, String unit) {
    this.controllerSetPoint = signal;
    this.unit = unit;
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
   * {@inheritDoc}
   *
   * <p>
   * Set minimum and maximum controller output for anti-windup handling.
   * </p>
   */
  @Override
  public void setOutputLimits(double min, double max) {
    this.minResponse = min;
    this.maxResponse = max;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * Set derivative filter time constant. Set to zero to disable filtering.
   * </p>
   */
  @Override
  public void setDerivativeFilterTime(double timeConstant) {
    if (timeConstant >= 0) {
      this.derivativeFilterTime = timeConstant;
    } else {
      logger.warn("Negative filter time is not allowed.");
    }
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
    if (Kp >= 0) {
      this.Kp = Kp;
    } else {
      logger.warn("Negative Kp is not allowed. Use setReverseActing.");
    }
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
    if (Ti >= 0) {
      this.Ti = Ti;
    } else {
      logger.warn("Negative Ti is not allowed.");
    }
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
    if (Td >= 0) {
      this.Td = Td;
    } else {
      logger.warn("Negative Td is not allowed.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void autoTune(double ultimateGain, double ultimatePeriod) {
    if (ultimateGain > 0 && ultimatePeriod > 0) {
      setControllerParameters(0.6 * ultimateGain, 0.5 * ultimatePeriod, 0.125 * ultimatePeriod);
    } else {
      logger.warn("Invalid ultimate gain or period for auto tune.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void autoTuneStepResponse(double processGain, double timeConstant, double deadTime) {
    if (processGain != 0.0 && timeConstant > 0 && deadTime > 0) {
      double Kp = 1.2 / processGain * (timeConstant / deadTime);
      double Ti = 2.0 * deadTime;
      double Td = 0.5 * deadTime;
      setControllerParameters(Kp, Ti, Td);
    } else {
      logger.warn("Invalid step response parameters for auto tune.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void addGainSchedulePoint(double processValue, double Kp, double Ti, double Td) {
    gainSchedule.put(processValue, new double[] {Kp, Ti, Td});
  }

  /** {@inheritDoc} */
  @Override
  public java.util.List<ControllerEvent> getEventLog() {
    return eventLog;
  }

  /** {@inheritDoc} */
  @Override
  public void resetEventLog() {
    eventLog.clear();
    totalTime = 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public double getIntegralAbsoluteError() {
    return integralAbsoluteError;
  }

  /** {@inheritDoc} */
  @Override
  public double getSettlingTime() {
    return lastTimeOutsideBand;
  }

  /** {@inheritDoc} */
  @Override
  public void resetPerformanceMetrics() {
    integralAbsoluteError = 0.0;
    lastTimeOutsideBand = 0.0;
    totalTime = 0.0;
  }

  /**
   * Apply gain-scheduled controller parameters based on the current measurement value. The schedule
   * selects the parameter set with the highest threshold not exceeding the measurement.
   *
   * @param measurement current process value
   */
  private void applyGainSchedule(double measurement) {
    if (gainSchedule.isEmpty()) {
      return;
    }
    Map.Entry<Double, double[]> entry = gainSchedule.floorEntry(measurement);
    if (entry != null) {
      double[] params = entry.getValue();
      this.Kp = params[0];
      this.Ti = params[1];
      this.Td = params[2];
    }
  }
}
