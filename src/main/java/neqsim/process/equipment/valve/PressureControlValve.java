package neqsim.process.equipment.valve;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Pressure control valve (PCV) with an embedded incremental proportional control calculation.
 *
 * <p>
 * Steady evaluation solves the current hydraulics before sampling pressure. Dynamic evaluation samples the pressure
 * state published by the preceding process scan, then applies the command, actuator dynamics, and valve hydraulics
 * once. This explicit one-scan measurement delay prevents hidden algebraic iteration and gives deterministic event
 * order.
 * </p>
 */
public class PressureControlValve extends ControlValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Controlled pressure definition. */
  public enum ControlMode {
    /** Control downstream outlet pressure. */
    DOWNSTREAM,
    /** Control upstream inlet pressure as a back-pressure valve. */
    UPSTREAM,
    /** Control inlet minus outlet differential pressure. */
    DIFFERENTIAL
  }

  private double pressureSetpoint = 20.0;
  private ControlMode controlMode = ControlMode.DOWNSTREAM;
  private double controllerGain = 5.0;
  private double processVariable = 0.0;
  private double controlError = 0.0;
  private boolean autoMode = true;
  /** Last physical-step identity that advanced the embedded control calculation. */
  private UUID lastControlStepId = null;

  /** @param name name of the pressure control valve */
  public PressureControlValve(String name) {
    super(name);
  }

  /**
   * @param name name of the pressure control valve
   * @param inStream inlet stream
   */
  public PressureControlValve(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** @param setpoint pressure setpoint in bara */
  public void setPressureSetpoint(double setpoint) {
    pressureSetpoint = setpoint;
  }

  /** @return pressure setpoint in bara */
  public double getPressureSetpoint() {
    return pressureSetpoint;
  }

  /** @param mode controlled pressure definition */
  public void setControlMode(ControlMode mode) {
    controlMode = mode;
  }

  /** @return controlled pressure definition */
  public ControlMode getControlMode() {
    return controlMode;
  }

  /** @param gain incremental proportional gain in percent opening per bara error */
  public void setControllerGain(double gain) {
    controllerGain = gain;
  }

  /** @return controller gain */
  public double getControllerGain() {
    return controllerGain;
  }

  /** @return sampled process variable in bara */
  public double getProcessVariable() {
    return processVariable;
  }

  /** @return setpoint minus sampled process variable in bara */
  public double getControlError() {
    return controlError;
  }

  /** @param auto true for automatic, false for manual */
  public void setAutoMode(boolean auto) {
    autoMode = auto;
  }

  /** @return true in automatic mode */
  public boolean isAutoMode() {
    return autoMode;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  /**
   * Runs one UUID-aware steady evaluation. The current hydraulic state is solved before sampling the pressure and, when
   * the command advances, solved once more at the new opening.
   *
   * @param id calculation or physical-step identity
   */
  @Override
  public void run(UUID id) {
    if (!autoMode) {
      super.run(id);
      return;
    }
    super.run(id);
    samplePressure();
    if (shouldAdvanceControl(id)) {
      applyControlCommand(false);
      super.run(id);
    }
  }

  /**
   * Runs one transient scan in previous-pressure-command-actuator-hydraulics order.
   *
   * @param dt physical timestep in seconds
   * @param id physical-step identity
   */
  @Override
  public void runTransient(double dt, UUID id) {
    if (getCalculateSteadyState()) {
      super.runTransient(dt, id);
      return;
    }
    if (autoMode && shouldAdvanceControl(id)) {
      samplePressure();
      applyControlCommand(true);
    }
    super.runTransient(dt, id);
  }

  /** {@inheritDoc} */
  @Override
  public void setPhysicalStepIdempotencyEnabled(boolean enabled) {
    super.setPhysicalStepIdempotencyEnabled(enabled);
    if (!enabled) {
      lastControlStepId = null;
    }
  }

  private boolean shouldAdvanceControl(UUID id) {
    boolean advance = !isPhysicalStepIdempotencyEnabled() || id == null || !id.equals(lastControlStepId);
    if (advance && isPhysicalStepIdempotencyEnabled()) {
      lastControlStepId = id;
    }
    return advance;
  }

  private void samplePressure() {
    double inletPressure = getInletStream().getPressure("bara");
    double outletPressure = getOutletStream() == null ? inletPressure : getOutletStream().getPressure("bara");
    switch (controlMode) {
    case UPSTREAM:
      processVariable = inletPressure;
      break;
    case DIFFERENTIAL:
      processVariable = inletPressure - outletPressure;
      break;
    case DOWNSTREAM:
    default:
      processVariable = outletPressure;
      break;
    }
    controlError = pressureSetpoint - processVariable;
  }

  private void applyControlCommand(boolean transientMode) {
    double currentCommand = transientMode ? getTargetPercentValveOpening() : getPercentValveOpening();
    double adjustment = controllerGain * controlError;
    double newCommand = controlMode == ControlMode.DOWNSTREAM ? currentCommand + adjustment
        : currentCommand - adjustment;
    if (transientMode) {
      setTargetPercentValveOpening(newCommand);
    } else {
      setPercentValveOpening(newCommand);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != PressureControlValve.class) {
      return "valve subclass " + getClass().getName() + " must extend the snapshot for subclass-owned mutable state";
    }
    return getValveTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public TransientState captureTransientState() {
    String coverageIssue = getTransientStateCoverageIssue();
    if (coverageIssue != null) {
      throw new IllegalStateException("Cannot capture pressure control valve '" + getName() + "': " + coverageIssue);
    }
    return new PressureControlTransientState(this);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreTransientStepState(TransientState snapshot) {
    if (!(snapshot instanceof PressureControlTransientState)) {
      throw new IllegalArgumentException("Pressure control valve requires a pressure control transient snapshot");
    }
    super.restoreTransientStepState(snapshot);
    PressureControlTransientState state = (PressureControlTransientState) snapshot;
    pressureSetpoint = state.pressureSetpoint;
    controlMode = state.controlMode;
    controllerGain = state.controllerGain;
    processVariable = state.processVariable;
    controlError = state.controlError;
    autoMode = state.autoMode;
    lastControlStepId = state.lastControlStepId;
  }

  /** Serializable checkpoint for embedded pressure-control state. */
  public static final class PressureControlTransientState extends TransientState {
    private static final long serialVersionUID = 1000L;
    private final double pressureSetpoint;
    private final ControlMode controlMode;
    private final double controllerGain;
    private final double processVariable;
    private final double controlError;
    private final boolean autoMode;
    private final UUID lastControlStepId;

    private PressureControlTransientState(PressureControlValve source) {
      super(source);
      pressureSetpoint = source.pressureSetpoint;
      controlMode = source.controlMode;
      controllerGain = source.controllerGain;
      processVariable = source.processVariable;
      controlError = source.controlError;
      autoMode = source.autoMode;
      lastControlStepId = source.lastControlStepId;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format(
        "%s [Pressure Control Valve] - Mode: %s, SP: %.2f bara, PV: %.2f bara, Error: %.2f bara, Opening: %.1f%%",
        getName(), autoMode ? "AUTO" : "MANUAL", pressureSetpoint, processVariable, controlError,
        getPercentValveOpening());
  }
}
