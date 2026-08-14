/*
 * ControllerDeviceBaseClass.java
 *
 * Created on 10. oktober 2006, 19:59
 */

package neqsim.process.controllerdevice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.util.NamedBaseClass;

/**
 * Discrete PID controller implementation providing common features for process control in NeqSim. The class supports
 * anti-windup clamping, derivative filtering, gain scheduling, event logging and performance metrics as well as
 * auto-tuning utilities.
 *
 * <p>
 * The controller operates on a {@link neqsim.process.measurementdevice.MeasurementDeviceInterface} transmitter and
 * exposes a standard PID API through {@link neqsim.process.controllerdevice.ControllerDeviceInterface}.
 * </p>
 *
 * @author ESOL
 * @version $Id: $Id
 */
public class ControllerDeviceBaseClass extends NamedBaseClass implements ControllerDeviceInterface,
    TransientStateParticipant<ControllerDeviceBaseClass.ControllerTransientState> {
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
  private StepResponseTuningMethod stepResponseTuningMethod = StepResponseTuningMethod.CLASSIC;
  // Internal state of integration contribution
  private double TintValue = 0.0;
  private double derivativeState = 0.0;
  // Previous measurement and setpoint, used by the engineering-unit 2-DOF velocity form so a
  // setpoint step produces only the weighted (b) proportional kick. NaN until the first step.
  private double oldMeasurement = Double.NaN;
  private double oldControllerSetPoint = Double.NaN;
  private double derivativeFilterTime = 0.0;
  private double minResponse = Double.NEGATIVE_INFINITY;
  private double maxResponse = Double.POSITIVE_INFINITY;
  boolean isActive = true;
  private ControllerMode mode = ControllerMode.AUTO;
  private double manualOutput = 30.0;
  private boolean bumplessTransferPending = false;
  private NavigableMap<Double, double[]> gainSchedule = new TreeMap<>();
  private java.util.List<ControllerEvent> eventLog = new java.util.ArrayList<>();
  private double totalTime = 0.0;
  private double integralAbsoluteError = 0.0;
  private double lastTimeOutsideBand = 0.0;
  private double settlingTolerance = 0.02;
  private double setpointWeight = 1.0;
  private double deadBand = 0.0;
  private neqsim.process.equipment.iec81346.ReferenceDesignation referenceDesignation = new neqsim.process.equipment.iec81346.ReferenceDesignation();
  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  /**
   * Constructor for ControllerDeviceBaseClass.
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
   * Constructor for ControllerDeviceBaseClass.
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
   * If no engineering unit is configured, the controller falls back to the legacy percent-based error formulation used
   * by earlier NeqSim versions.
   * </p>
   */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    if (hasRunTransient(id)) {
      return;
    }
    if (!isActive) {
      totalTime += dt;
      response = initResponse;
      calcIdentifier = id;
      return;
    }

    // Handle MANUAL mode: bypass PID, use manual output, track errors for future transfer
    if (mode == ControllerMode.MANUAL) {
      totalTime += dt;
      response = manualOutput;
      double measurement = getMeasuredValue(unit);
      oldoldError = oldError;
      oldError = error;
      error = measurement - controllerSetPoint;
      // Track measurement/setpoint in MANUAL so the return to AUTO is bumpless in the velocity form.
      oldMeasurement = measurement;
      oldControllerSetPoint = controllerSetPoint;
      eventLog.add(new ControllerEvent(totalTime, measurement, controllerSetPoint, error, response));
      calcIdentifier = id;
      return;
    }

    totalTime += dt;
    if (isReverseActing()) {
      propConstant = -1;
    }
    double measurement = getMeasuredValue(unit);
    applyGainSchedule(measurement);
    oldoldError = oldError;
    oldError = error;

    // Perform bumpless transfer back-calculation when switching from MANUAL to AUTO
    if (bumplessTransferPending) {
      error = measurement - controllerSetPoint;
      oldError = error;
      oldoldError = error;
      derivativeState = 0.0;
      if (propConstant != 0) {
        TintValue = (manualOutput - initResponse) / propConstant;
      }
      bumplessTransferPending = false;
    }

    double band = 0.0;
    double TintIncrement = 0.0;
    double derivative = 0.0;
    double delta = 0.0;

    boolean usesDefaultUnit = unit == null || unit.isEmpty() || unit.equals("[?]");

    if (usesDefaultUnit) {
      double measurementPercent = transmitter.getMeasuredPercentValue();
      double setPointPercent = (controllerSetPoint - transmitter.getMinimumValue())
          / (transmitter.getMaximumValue() - transmitter.getMinimumValue()) * 100.0;
      error = measurementPercent - setPointPercent;
      if (deadBand > 0.0 && Math.abs(error) <= deadBand) {
        // Within deadband: freeze controller output (hold last valve position).
        response = initResponse;
      } else {
        if (Ti != 0) {
          TintValue = Kp / Ti * error;
        }
        double TderivValue = Kp * Td * ((error - 2 * oldError + oldoldError) / (dt * dt));
        response = initResponse + propConstant * ((Kp * (error - oldError) / dt) + TintValue + TderivValue) * dt;
      }
    } else {
      error = measurement - controllerSetPoint;
      // 2-DOF velocity-form PID: the proportional term acts on the increment of
      // (measurement - b * setpoint) while the integral acts on the full error. Storing the
      // previous measurement and setpoint means a pure setpoint step contributes only
      // -b * Kp * dSetpoint to the proportional kick (b = 0 removes the kick entirely).
      if (Double.isNaN(oldMeasurement)) {
        oldMeasurement = measurement;
        oldControllerSetPoint = controllerSetPoint;
      }
      double propStep = (measurement - oldMeasurement) - setpointWeight * (controllerSetPoint - oldControllerSetPoint);
      integralAbsoluteError += Math.abs(error) * dt;
      band = settlingTolerance * Math.max(Math.abs(controllerSetPoint), 1.0);
      if (Math.abs(error) > band) {
        lastTimeOutsideBand = totalTime;
      }
      if (deadBand > 0.0 && Math.abs(error) <= deadBand) {
        // Within deadband: freeze controller output and integral (hold last valve position).
        response = initResponse;
      } else {
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

        delta = Kp * propStep + TintValue + Kp * Td * derivativeState;

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
      }
      // Advance the velocity-form history for the next step (also after a frozen deadband step).
      oldMeasurement = measurement;
      oldControllerSetPoint = controllerSetPoint;
    }

    eventLog.add(new ControllerEvent(totalTime, measurement, controllerSetPoint, error, response));
    calcIdentifier = id;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasRunTransient(UUID id) {
    return id != null && id.equals(calcIdentifier);
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
  public double getControllerSetPoint() {
    return controllerSetPoint;
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

  /**
   * Gets the IEC 81346 reference designation for this controller.
   *
   * @return the reference designation object
   */
  public neqsim.process.equipment.iec81346.ReferenceDesignation getReferenceDesignation() {
    return referenceDesignation;
  }

  /**
   * Sets the IEC 81346 reference designation for this controller.
   *
   * @param referenceDesignation the reference designation to set
   */
  public void setReferenceDesignation(neqsim.process.equipment.iec81346.ReferenceDesignation referenceDesignation) {
    this.referenceDesignation = referenceDesignation != null ? referenceDesignation
        : new neqsim.process.equipment.iec81346.ReferenceDesignation();
  }

  /**
   * Gets the IEC 81346 reference designation string for this controller. Returns an empty string if no designation has
   * been set.
   *
   * @return the reference designation string (e.g. "=A1.S1")
   */
  public String getReferenceDesignationString() {
    if (referenceDesignation == null) {
      return "";
    }
    return referenceDesignation.toReferenceDesignationString();
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
   * Get proportional gain of PID controller.
   *
   * @return Proportional gain of PID controller
   */
  public double getKp() {
    return Kp;
  }

  /**
   * Set proportional gain of PID controller.
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
   * Get integral time of PID controller.
   *
   * @return Integral time in seconds
   */
  public double getTi() {
    return Ti;
  }

  /**
   * Set integral time of PID controller.
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
   * Get derivative time of PID controller.
   *
   * @return Derivative time of controller
   */
  public double getTd() {
    return Td;
  }

  /**
   * Set derivative time of PID controller.
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

  @Override
  public void setStepResponseTuningMethod(StepResponseTuningMethod method) {
    if (method == null) {
      this.stepResponseTuningMethod = StepResponseTuningMethod.CLASSIC;
    } else {
      this.stepResponseTuningMethod = method;
    }
  }

  @Override
  public StepResponseTuningMethod getStepResponseTuningMethod() {
    return stepResponseTuningMethod;
  }

  /** {@inheritDoc} */
  @Override
  public void autoTune(double ultimateGain, double ultimatePeriod) {
    autoTune(ultimateGain, ultimatePeriod, true);
  }

  /** {@inheritDoc} */
  @Override
  public void autoTune(double ultimateGain, double ultimatePeriod, boolean tuneDerivative) {
    if (ultimateGain > 0 && ultimatePeriod > 0) {
      double kp = 0.6 * ultimateGain;
      double ti = 0.5 * ultimatePeriod;
      double td = tuneDerivative ? 0.125 * ultimatePeriod : 0.0;
      setControllerParameters(kp, ti, td);
    } else {
      logger.warn("Invalid ultimate gain or period for auto tune.");
    }
  }

  /** {@inheritDoc} */
  @Override
  public void autoTuneStepResponse(double processGain, double timeConstant, double deadTime) {
    autoTuneStepResponse(processGain, timeConstant, deadTime, true);
  }

  /** {@inheritDoc} */
  @Override
  public void autoTuneStepResponse(double processGain, double timeConstant, double deadTime, boolean tuneDerivative) {
    if (processGain == 0.0 || timeConstant <= 0.0) {
      logger.warn("Invalid step response parameters for auto tune.");
      return;
    }

    double kp;
    double ti;
    double td = 0.0;

    if (stepResponseTuningMethod == StepResponseTuningMethod.SIMC) {
      double theta = Math.max(deadTime, 1.0e-6);
      double lambda = Math.max(theta, timeConstant / 4.0);

      if (tuneDerivative) {
        double halfTheta = 0.5 * theta;
        kp = (timeConstant + halfTheta) / (lambda + halfTheta) / processGain;
        ti = timeConstant + halfTheta;
        double denominator = 2.0 * timeConstant + theta;
        td = denominator > 0.0 ? timeConstant * theta / denominator : 0.0;
      } else {
        kp = timeConstant / (lambda + theta) / processGain;
        ti = Math.min(timeConstant, 4.0 * (lambda + theta));
      }
    } else {
      double theta = deadTime;
      if (theta <= 0.0) {
        logger.warn("Invalid dead time for classic step response auto tune.");
        return;
      }
      kp = 1.2 / processGain * (timeConstant / theta);
      ti = 2.0 * theta;
      td = tuneDerivative ? 0.5 * theta : 0.0;
    }

    setControllerParameters(kp, ti, td);
  }

  /** {@inheritDoc} */
  @Override
  public boolean autoTuneFromEventLog() {
    return autoTuneFromEventLog(true);
  }

  /** {@inheritDoc} */
  @Override
  public boolean autoTuneFromEventLog(boolean tuneDerivative) {
    if (eventLog.size() < 5) {
      logger.warn("Insufficient controller events for auto tuning.");
      return false;
    }

    ControllerEvent first = eventLog.get(0);
    double initialMeasurement = first.getMeasuredValue();
    double initialResponse = first.getResponse();
    double initialTime = first.getTime();

    int sampleCount = Math.min(5, eventLog.size());
    double finalMeasurement = averageOfLast(sampleCount, ControllerEvent::getMeasuredValue);
    double finalResponse = averageOfLast(sampleCount, ControllerEvent::getResponse);

    double measurementChange = finalMeasurement - initialMeasurement;
    double responseChange = finalResponse - initialResponse;

    if (Math.abs(measurementChange) < 1e-9) {
      logger.warn("Measured value change too small for auto tuning.");
      return false;
    }

    if (Math.abs(responseChange) < 1e-9) {
      logger.warn("Controller output change too small for auto tuning.");
      return false;
    }

    double processGain = measurementChange / responseChange;
    if (!Double.isFinite(processGain) || processGain == 0.0) {
      logger.warn("Invalid process gain estimated from event log.");
      return false;
    }

    boolean positiveChange = measurementChange >= 0.0;
    double startThreshold = initialMeasurement + 0.02 * measurementChange;
    double threshold63 = initialMeasurement + 0.632 * measurementChange;

    double tStart = Double.NaN;
    double t63 = Double.NaN;

    for (ControllerEvent event : eventLog) {
      double value = event.getMeasuredValue();
      if (Double.isNaN(tStart)) {
        if ((positiveChange && value >= startThreshold) || (!positiveChange && value <= startThreshold)) {
          tStart = event.getTime();
        }
      }
      if (Double.isNaN(t63)) {
        if ((positiveChange && value >= threshold63) || (!positiveChange && value <= threshold63)) {
          t63 = event.getTime();
        }
      }
      if (!Double.isNaN(tStart) && !Double.isNaN(t63)) {
        break;
      }
    }

    if (Double.isNaN(tStart)) {
      logger.warn("Unable to determine response start time for auto tuning.");
      return false;
    }

    if (Double.isNaN(t63) || t63 <= tStart) {
      logger.warn("Unable to determine process time constant for auto tuning.");
      return false;
    }

    double deadTime = Math.max(0.0, tStart - initialTime);
    double timeConstant = Math.max(t63 - tStart, 1e-6);

    double adjustedDeadTime = deadTime;
    if (adjustedDeadTime < 1e-6) {
      adjustedDeadTime = 1e-6;
    }

    autoTuneStepResponse(processGain, timeConstant, adjustedDeadTime, tuneDerivative);
    TintValue = 0.0;
    derivativeState = 0.0;
    logger.info("Auto tuned PID from event log: Kp={}, Ti={}, Td={}", Kp, Ti, Td);
    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void addGainSchedulePoint(double processValue, double Kp, double Ti, double Td) {
    gainSchedule.put(processValue, new double[] { Kp, Ti, Td });
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

  /** {@inheritDoc} */
  @Override
  public ControllerMode getMode() {
    return mode;
  }

  /**
   * {@inheritDoc}
   *
   * <p>
   * When switching from MANUAL to AUTO, a bumpless transfer is scheduled so that the controller output does not jump on
   * the next {@code runTransient} call. The integral state is back-calculated to match the current manual output. When
   * switching from AUTO to MANUAL, the current PID output is captured as the manual output value.
   * </p>
   */
  @Override
  public void setMode(ControllerMode newMode) {
    if (newMode == null || newMode == mode) {
      return;
    }
    if (newMode == ControllerMode.AUTO && mode == ControllerMode.MANUAL) {
      bumplessTransferPending = true;
    }
    if (newMode == ControllerMode.MANUAL) {
      manualOutput = response;
    }
    this.mode = newMode;
  }

  /** {@inheritDoc} */
  @Override
  public double getManualOutput() {
    return manualOutput;
  }

  /** {@inheritDoc} */
  @Override
  public void setManualOutput(double output) {
    this.manualOutput = output;
    if (mode == ControllerMode.MANUAL) {
      response = output;
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setSetpointWeight(double b) {
    this.setpointWeight = Math.max(0.0, Math.min(1.0, b));
  }

  /** {@inheritDoc} */
  @Override
  public double getSetpointWeight() {
    return setpointWeight;
  }

  /** {@inheritDoc} */
  @Override
  public void setDeadBand(double deadBand) {
    this.deadBand = Math.max(0.0, deadBand);
  }

  /** {@inheritDoc} */
  @Override
  public double getDeadBand() {
    return deadBand;
  }

  /**
   * Apply gain-scheduled controller parameters based on the current measurement value. The schedule selects the
   * parameter set with the highest threshold not exceeding the measurement.
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

  /**
   * Calculate the average value of the {@link ControllerEvent} properties for the last entries in the event log.
   *
   * @param count number of samples to include in the average
   * @param extractor function returning the value to average from the event
   * @return average of the selected event property
   */
  private double averageOfLast(int count, ToDoubleFunction<ControllerEvent> extractor) {
    if (eventLog.isEmpty()) {
      return 0.0;
    }
    int startIndex = Math.max(0, eventLog.size() - count);
    double sum = 0.0;
    int actualCount = 0;
    for (int i = startIndex; i < eventLog.size(); i++) {
      sum += extractor.applyAsDouble(eventLog.get(i));
      actualCount++;
    }
    return actualCount > 0 ? sum / actualCount : 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "controller:" + transientStateParticipantId;
  }

  /**
   * The base snapshot is complete only when the concrete controller explicitly qualifies all subclass-owned state.
   *
   * @return blocking diagnostic for an unqualified subclass, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (!hasCompleteControllerTransientStateCoverage()) {
      return "controller subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return null;
  }

  /**
   * Reports whether this concrete controller extends the base snapshot for all subclass-owned mutable state.
   *
   * <p>
   * Subclasses remain fail-closed unless they override this method together with
   * {@link #captureControllerSubclassTransientState()} and
   * {@link #restoreControllerSubclassTransientState(Serializable)}.
   * </p>
   *
   * @return {@code true} only when the concrete class has complete transaction coverage
   */
  protected boolean hasCompleteControllerTransientStateCoverage() {
    return getClass() == ControllerDeviceBaseClass.class;
  }

  /**
   * Captures controller-specific state appended to the base PID snapshot.
   *
   * @return serializable subclass state, or {@code null} for the concrete base controller
   */
  protected Serializable captureControllerSubclassTransientState() {
    return null;
  }

  /**
   * Restores controller-specific state after the base PID fields have been restored.
   *
   * @param state captured subclass state
   */
  protected void restoreControllerSubclassTransientState(Serializable state) {
    if (state != null) {
      throw new IllegalArgumentException("Concrete base controller cannot restore non-null subclass transient state");
    }
  }

  /** {@inheritDoc} */
  @Override
  public ControllerTransientState captureTransientState() {
    return new ControllerTransientState(getTransientStateIdentity(), getName(), calcIdentifier, unit, transmitter,
        controllerSetPoint, oldError, oldoldError, error, response, propConstant, reverseActing, Kp, Ti, Td,
        stepResponseTuningMethod, TintValue, derivativeState, oldMeasurement, oldControllerSetPoint,
        derivativeFilterTime, minResponse, maxResponse, isActive, mode, manualOutput, bumplessTransferPending,
        copyGainSchedule(gainSchedule), new ArrayList<ControllerEvent>(eventLog), totalTime, integralAbsoluteError,
        lastTimeOutsideBand, settlingTolerance, setpointWeight, deadBand, referenceDesignation,
        copyReferenceDesignation(referenceDesignation), captureControllerSubclassTransientState());
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(ControllerTransientState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Controller transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Controller transient snapshot identity does not match " + getTransientStateIdentity());
    }
    setName(snapshot.name);
    calcIdentifier = snapshot.calcIdentifier;
    unit = snapshot.unit;
    transmitter = snapshot.transmitter;
    controllerSetPoint = snapshot.controllerSetPoint;
    oldError = snapshot.oldError;
    oldoldError = snapshot.oldoldError;
    error = snapshot.error;
    response = snapshot.response;
    propConstant = snapshot.propConstant;
    reverseActing = snapshot.reverseActing;
    Kp = snapshot.kp;
    Ti = snapshot.ti;
    Td = snapshot.td;
    stepResponseTuningMethod = snapshot.stepResponseTuningMethod;
    TintValue = snapshot.tintValue;
    derivativeState = snapshot.derivativeState;
    oldMeasurement = snapshot.oldMeasurement;
    oldControllerSetPoint = snapshot.oldControllerSetPoint;
    derivativeFilterTime = snapshot.derivativeFilterTime;
    minResponse = snapshot.minResponse;
    maxResponse = snapshot.maxResponse;
    isActive = snapshot.active;
    mode = snapshot.mode;
    manualOutput = snapshot.manualOutput;
    bumplessTransferPending = snapshot.bumplessTransferPending;
    gainSchedule = copyGainSchedule(snapshot.gainSchedule);
    eventLog = new ArrayList<ControllerEvent>(snapshot.eventLog);
    totalTime = snapshot.totalTime;
    integralAbsoluteError = snapshot.integralAbsoluteError;
    lastTimeOutsideBand = snapshot.lastTimeOutsideBand;
    settlingTolerance = snapshot.settlingTolerance;
    setpointWeight = snapshot.setpointWeight;
    deadBand = snapshot.deadBand;
    referenceDesignation = snapshot.referenceDesignation;
    restoreReferenceDesignation(referenceDesignation, snapshot.referenceDesignationState);
    restoreControllerSubclassTransientState(snapshot.subclassState);
  }

  /**
   * Copies gain-schedule arrays so later tuning changes cannot mutate a captured rollback point.
   *
   * @param source source schedule
   * @return independent schedule copy
   */
  private static NavigableMap<Double, double[]> copyGainSchedule(NavigableMap<Double, double[]> source) {
    NavigableMap<Double, double[]> copy = new TreeMap<Double, double[]>();
    for (Map.Entry<Double, double[]> entry : source.entrySet()) {
      copy.put(entry.getKey(), entry.getValue() == null ? null : entry.getValue().clone());
    }
    return copy;
  }

  /**
   * Copies the mutable IEC 81346 designation value while keeping its original binding separately.
   *
   * @param source designation to copy
   * @return independent value copy, or {@code null}
   */
  private static neqsim.process.equipment.iec81346.ReferenceDesignation copyReferenceDesignation(
      neqsim.process.equipment.iec81346.ReferenceDesignation source) {
    if (source == null) {
      return null;
    }
    return new neqsim.process.equipment.iec81346.ReferenceDesignation(source.getFunctionDesignation(),
        source.getProductDesignation(), source.getLocationDesignation(), source.getLetterCode(),
        source.getSequenceNumber());
  }

  /**
   * Restores a designation in place so rollback retains the pre-trial binding identity.
   *
   * @param target original bound designation
   * @param state captured designation value
   */
  private static void restoreReferenceDesignation(neqsim.process.equipment.iec81346.ReferenceDesignation target,
      neqsim.process.equipment.iec81346.ReferenceDesignation state) {
    if (target == null || state == null) {
      return;
    }
    target.setFunctionDesignation(state.getFunctionDesignation());
    target.setProductDesignation(state.getProductDesignation());
    target.setLocationDesignation(state.getLocationDesignation());
    target.setLetterCode(state.getLetterCode());
    target.setSequenceNumber(state.getSequenceNumber());
  }

  /** Immutable snapshot of every base PID field mutated by stepping or supported operational setters. */
  public static final class ControllerTransientState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final String name;
    private final UUID calcIdentifier;
    private final String unit;
    private final MeasurementDeviceInterface transmitter;
    private final double controllerSetPoint;
    private final double oldError;
    private final double oldoldError;
    private final double error;
    private final double response;
    private final int propConstant;
    private final boolean reverseActing;
    private final double kp;
    private final double ti;
    private final double td;
    private final StepResponseTuningMethod stepResponseTuningMethod;
    private final double tintValue;
    private final double derivativeState;
    private final double oldMeasurement;
    private final double oldControllerSetPoint;
    private final double derivativeFilterTime;
    private final double minResponse;
    private final double maxResponse;
    private final boolean active;
    private final ControllerMode mode;
    private final double manualOutput;
    private final boolean bumplessTransferPending;
    private final NavigableMap<Double, double[]> gainSchedule;
    private final java.util.List<ControllerEvent> eventLog;
    private final double totalTime;
    private final double integralAbsoluteError;
    private final double lastTimeOutsideBand;
    private final double settlingTolerance;
    private final double setpointWeight;
    private final double deadBand;
    private final neqsim.process.equipment.iec81346.ReferenceDesignation referenceDesignation;
    private final neqsim.process.equipment.iec81346.ReferenceDesignation referenceDesignationState;
    private final Serializable subclassState;

    private ControllerTransientState(String stateIdentity, String name, UUID calcIdentifier, String unit,
        MeasurementDeviceInterface transmitter, double controllerSetPoint, double oldError, double oldoldError,
        double error, double response, int propConstant, boolean reverseActing, double kp, double ti, double td,
        StepResponseTuningMethod stepResponseTuningMethod, double tintValue, double derivativeState,
        double oldMeasurement, double oldControllerSetPoint, double derivativeFilterTime, double minResponse,
        double maxResponse, boolean active, ControllerMode mode, double manualOutput, boolean bumplessTransferPending,
        NavigableMap<Double, double[]> gainSchedule, java.util.List<ControllerEvent> eventLog, double totalTime,
        double integralAbsoluteError, double lastTimeOutsideBand, double settlingTolerance, double setpointWeight,
        double deadBand, neqsim.process.equipment.iec81346.ReferenceDesignation referenceDesignation,
        neqsim.process.equipment.iec81346.ReferenceDesignation referenceDesignationState, Serializable subclassState) {
      this.stateIdentity = stateIdentity;
      this.name = name;
      this.calcIdentifier = calcIdentifier;
      this.unit = unit;
      this.transmitter = transmitter;
      this.controllerSetPoint = controllerSetPoint;
      this.oldError = oldError;
      this.oldoldError = oldoldError;
      this.error = error;
      this.response = response;
      this.propConstant = propConstant;
      this.reverseActing = reverseActing;
      this.kp = kp;
      this.ti = ti;
      this.td = td;
      this.stepResponseTuningMethod = stepResponseTuningMethod;
      this.tintValue = tintValue;
      this.derivativeState = derivativeState;
      this.oldMeasurement = oldMeasurement;
      this.oldControllerSetPoint = oldControllerSetPoint;
      this.derivativeFilterTime = derivativeFilterTime;
      this.minResponse = minResponse;
      this.maxResponse = maxResponse;
      this.active = active;
      this.mode = mode;
      this.manualOutput = manualOutput;
      this.bumplessTransferPending = bumplessTransferPending;
      this.gainSchedule = gainSchedule;
      this.eventLog = eventLog;
      this.totalTime = totalTime;
      this.integralAbsoluteError = integralAbsoluteError;
      this.lastTimeOutsideBand = lastTimeOutsideBand;
      this.settlingTolerance = settlingTolerance;
      this.setpointWeight = setpointWeight;
      this.deadBand = deadBand;
      this.referenceDesignation = referenceDesignation;
      this.referenceDesignationState = referenceDesignationState;
      this.subclassState = subclassState;
    }
  }

}
