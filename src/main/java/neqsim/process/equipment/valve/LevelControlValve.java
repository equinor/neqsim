package neqsim.process.equipment.valve;

import java.util.UUID;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Level control valve (LCV) for automatic level regulation in vessels.
 *
 * <p>
 * The embedded controller uses an incremental proportional command. In transient mode the command is evaluated before
 * the actuator and hydraulic calculation, so configured travel, deadband, stiction, and hysteresis remain active.
 * </p>
 */
public class LevelControlValve extends ControlValve {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Control action enumeration. */
  public enum ControlAction {
    /** Increasing measured level increases the valve opening. */
    DIRECT,
    /** Increasing measured level decreases the valve opening. */
    REVERSE
  }

  private double levelSetpoint = 50.0;
  private double measuredLevel = 50.0;
  private ControlAction controlAction = ControlAction.DIRECT;
  private double controllerGain = 3.0;
  private double controlError = 0.0;
  private boolean autoMode = true;
  private double failSafePosition = 0.0;
  /** Last physical-step identity that advanced the embedded control calculation. */
  private UUID lastControlStepId = null;

  /** @param name name of the level control valve */
  public LevelControlValve(String name) {
    super(name);
  }

  /**
   * @param name name of the level control valve
   * @param inStream inlet stream
   */
  public LevelControlValve(String name, StreamInterface inStream) {
    super(name, inStream);
  }

  /** @param setpoint level setpoint in percent */
  public void setLevelSetpoint(double setpoint) {
    levelSetpoint = Math.max(0.0, Math.min(100.0, setpoint));
  }

  /** @return level setpoint in percent */
  public double getLevelSetpoint() {
    return levelSetpoint;
  }

  /** @param level measured level in percent */
  public void setMeasuredLevel(double level) {
    measuredLevel = Math.max(0.0, Math.min(100.0, level));
  }

  /** @return measured level in percent */
  public double getMeasuredLevel() {
    return measuredLevel;
  }

  /** @param action control action */
  public void setControlAction(ControlAction action) {
    controlAction = action;
  }

  /** @return current control action */
  public ControlAction getControlAction() {
    return controlAction;
  }

  /** @param gain incremental proportional gain in percent opening per percent error */
  public void setControllerGain(double gain) {
    controllerGain = gain;
  }

  /** @return controller gain */
  public double getControllerGain() {
    return controllerGain;
  }

  /** @return control error (setpoint minus measured level) in percent */
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

  /** @param position fail-safe position in percent opening */
  public void setFailSafePosition(double position) {
    failSafePosition = Math.max(0.0, Math.min(100.0, position));
  }

  /** @return fail-safe position in percent opening */
  public double getFailSafePosition() {
    return failSafePosition;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    run(UUID.randomUUID());
  }

  /**
   * Runs one UUID-aware steady evaluation. Repeated evaluations advance the embedded controller once when physical-step
   * idempotency is enabled.
   *
   * @param id calculation or physical-step identity
   */
  @Override
  public void run(UUID id) {
    if (autoMode && shouldAdvanceControl(id)) {
      applyControlCommand(false);
    }
    super.run(id);
  }

  /**
   * Runs one transient scan in measurement-command-actuator-hydraulics order.
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

  private void applyControlCommand(boolean transientMode) {
    controlError = levelSetpoint - measuredLevel;
    double currentCommand = transientMode ? getTargetPercentValveOpening() : getPercentValveOpening();
    double adjustment = controllerGain * controlError;
    double newCommand = controlAction == ControlAction.DIRECT ? currentCommand - adjustment
        : currentCommand + adjustment;
    if (transientMode) {
      setTargetPercentValveOpening(newCommand);
    } else {
      setPercentValveOpening(newCommand);
    }
  }

  /** {@inheritDoc} */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != LevelControlValve.class) {
      return "valve subclass " + getClass().getName() + " must extend the snapshot for subclass-owned mutable state";
    }
    return getValveTransientStateCoverageIssue();
  }

  /** {@inheritDoc} */
  @Override
  public TransientState captureTransientState() {
    String coverageIssue = getTransientStateCoverageIssue();
    if (coverageIssue != null) {
      throw new IllegalStateException("Cannot capture level control valve '" + getName() + "': " + coverageIssue);
    }
    return new LevelControlTransientState(this);
  }

  /** {@inheritDoc} */
  @Override
  protected void restoreTransientStepState(TransientState snapshot) {
    if (!(snapshot instanceof LevelControlTransientState)) {
      throw new IllegalArgumentException("Level control valve requires a level control transient snapshot");
    }
    super.restoreTransientStepState(snapshot);
    LevelControlTransientState state = (LevelControlTransientState) snapshot;
    levelSetpoint = state.levelSetpoint;
    measuredLevel = state.measuredLevel;
    controlAction = state.controlAction;
    controllerGain = state.controllerGain;
    controlError = state.controlError;
    autoMode = state.autoMode;
    failSafePosition = state.failSafePosition;
    lastControlStepId = state.lastControlStepId;
  }

  /** Serializable checkpoint for embedded level-control state. */
  public static final class LevelControlTransientState extends TransientState {
    private static final long serialVersionUID = 1000L;
    private final double levelSetpoint;
    private final double measuredLevel;
    private final ControlAction controlAction;
    private final double controllerGain;
    private final double controlError;
    private final boolean autoMode;
    private final double failSafePosition;
    private final UUID lastControlStepId;

    private LevelControlTransientState(LevelControlValve source) {
      super(source);
      levelSetpoint = source.levelSetpoint;
      measuredLevel = source.measuredLevel;
      controlAction = source.controlAction;
      controllerGain = source.controllerGain;
      controlError = source.controlError;
      autoMode = source.autoMode;
      failSafePosition = source.failSafePosition;
      lastControlStepId = source.lastControlStepId;
    }
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return String.format(
        "%s [Level Control Valve] - Mode: %s, Action: %s, SP: %.1f%%, PV: %.1f%%, Error: %.1f%%, Opening: %.1f%%",
        getName(), autoMode ? "AUTO" : "MANUAL", controlAction, levelSetpoint, measuredLevel, controlError,
        getPercentValveOpening());
  }
}
