package neqsim.process.controllerdevice;

import java.io.Serializable;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.dynamics.TransientStateParticipant;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.util.NamedBaseClass;

/**
 * Transfer function block for representing control dynamics in dynamic simulation. Supports first-order lag, lead-lag,
 * pure dead time (transport delay), and second-order dynamics. These are the fundamental building blocks used in
 * control system design and simulation.
 *
 * <p>
 * The transfer function is represented in the Laplace domain as:
 * </p>
 *
 * <ul>
 * <li><b>First-order lag:</b> G(s) = K / (tau*s + 1)</li>
 * <li><b>Lead-lag:</b> G(s) = K * (tauLead*s + 1) / (tauLag*s + 1)</li>
 * <li><b>Dead time:</b> G(s) = K * exp(-theta*s)</li>
 * <li><b>Second-order:</b> G(s) = K / (tau1*s + 1)(tau2*s + 1)</li>
 * </ul>
 *
 * <p>
 * Example — lead-lag compensator for a feedforward signal:
 * </p>
 *
 * <pre>
 * TransferFunctionBlock leadLag = new TransferFunctionBlock("FF-comp", TransferFunctionBlock.Type.LEAD_LAG);
 * leadLag.setGain(1.0);
 * leadLag.setLeadTime(30.0); // 30 seconds lead
 * leadLag.setLagTime(120.0); // 120 seconds lag
 * leadLag.setTransmitter(flowTransmitter);
 * process.add(leadLag);
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class TransferFunctionBlock extends NamedBaseClass
    implements ControllerDeviceInterface, TransientStateParticipant<TransferFunctionBlock.TransferFunctionState> {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(TransferFunctionBlock.class);

  /**
   * Transfer function types supported by this block.
   */
  public enum Type {
    /** First-order lag: G(s) = K / (tau*s + 1). */
    FIRST_ORDER_LAG,
    /** Lead-lag compensator: G(s) = K * (tauLead*s + 1) / (tauLag*s + 1). */
    LEAD_LAG,
    /** Pure dead time (transport delay): G(s) = K * exp(-theta*s). */
    DEAD_TIME,
    /** Second-order system: G(s) = K / (tau1*s + 1)(tau2*s + 1). */
    SECOND_ORDER
  }

  /** Transfer function type. */
  private final Type type;

  /** Static gain K. */
  private double gain = 1.0;

  /** First time constant (tau for first-order, tauLag for lead-lag, tau1 for second-order) [s]. */
  private double lagTime = 60.0;

  /** Lead time constant (tauLead for lead-lag) [s]. */
  private double leadTime = 0.0;

  /** Second time constant (tau2 for second-order) [s]. */
  private double lagTime2 = 0.0;

  /** Dead time (transport delay) theta [s]. */
  private double deadTime = 0.0;

  /** Input bias (steady-state input value). */
  private double inputBias = 0.0;

  /** Output bias (steady-state output value). */
  private double outputBias = 0.0;

  // --- Internal states ---

  /** State for first-order lag filter (or lag portion of lead-lag). */
  private double state1 = 0.0;

  /** State for second time constant (second-order only). */
  private double state2 = 0.0;

  /** Circular buffer for dead time implementation. */
  private double[] deadTimeBuffer = null;

  /** Write index for dead time buffer. */
  private int deadTimeWriteIndex = 0;

  /** Flag indicating whether internal states have been initialized. */
  private boolean initialized = false;

  /** Current output value. */
  private double output = 0.0;

  /** Attached transmitter providing the input signal. */
  private MeasurementDeviceInterface transmitter;

  /** Engineering unit. */
  private String unit = "[?]";

  /** Whether block is active. */
  private boolean isActive = true;

  /** UUID from last calculation. */
  protected UUID calcIdentifier;

  /** Persistent identity used only for transient transaction provenance. */
  private String transientStateParticipantId = UUID.randomUUID().toString();

  /**
   * Constructor for TransferFunctionBlock.
   *
   * @param name identifier for this block
   * @param type the type of transfer function
   */
  public TransferFunctionBlock(String name, Type type) {
    super(name);
    this.type = type;
  }

  /**
   * Get the transfer function type.
   *
   * @return the type
   */
  public Type getType() {
    return type;
  }

  /**
   * Set the static gain K.
   *
   * @param gain the gain value
   */
  public void setGain(double gain) {
    this.gain = gain;
  }

  /**
   * Get the static gain K.
   *
   * @return the gain
   */
  public double getGain() {
    return gain;
  }

  /**
   * Set the primary lag time constant [s]. For FIRST_ORDER_LAG this is tau. For LEAD_LAG this is tauLag. For
   * SECOND_ORDER this is tau1.
   *
   * @param lagTime time constant in seconds (must be positive)
   */
  public void setLagTime(double lagTime) {
    if (lagTime > 0) {
      this.lagTime = lagTime;
    } else {
      logger.warn("Lag time must be positive, got: " + lagTime);
    }
  }

  /**
   * Get the primary lag time constant [s].
   *
   * @return lag time in seconds
   */
  public double getLagTime() {
    return lagTime;
  }

  /**
   * Set the lead time constant for LEAD_LAG type [s].
   *
   * @param leadTime time constant in seconds (must be non-negative)
   */
  public void setLeadTime(double leadTime) {
    if (leadTime >= 0) {
      this.leadTime = leadTime;
    } else {
      logger.warn("Lead time must be non-negative, got: " + leadTime);
    }
  }

  /**
   * Get the lead time constant [s].
   *
   * @return lead time in seconds
   */
  public double getLeadTime() {
    return leadTime;
  }

  /**
   * Set the second lag time constant for SECOND_ORDER type [s].
   *
   * @param lagTime2 time constant in seconds (must be positive)
   */
  public void setLagTime2(double lagTime2) {
    if (lagTime2 > 0) {
      this.lagTime2 = lagTime2;
    } else {
      logger.warn("Second lag time must be positive, got: " + lagTime2);
    }
  }

  /**
   * Get the second lag time constant [s].
   *
   * @return second lag time in seconds
   */
  public double getLagTime2() {
    return lagTime2;
  }

  /**
   * Set the dead time (transport delay) [s].
   *
   * @param deadTime delay in seconds (must be non-negative)
   */
  public void setDeadTime(double deadTime) {
    if (deadTime >= 0) {
      this.deadTime = deadTime;
      this.deadTimeBuffer = null; // Force re-initialization
      this.initialized = false;
    } else {
      logger.warn("Dead time must be non-negative, got: " + deadTime);
    }
  }

  /**
   * Get the dead time [s].
   *
   * @return dead time in seconds
   */
  public double getDeadTime() {
    return deadTime;
  }

  /**
   * Set the input bias (steady-state input around which the transfer function is linearized).
   *
   * @param inputBias the bias value
   */
  public void setInputBias(double inputBias) {
    this.inputBias = inputBias;
  }

  /**
   * Get the input bias.
   *
   * @return the input bias
   */
  public double getInputBias() {
    return inputBias;
  }

  /**
   * Set the output bias (steady-state output value).
   *
   * @param outputBias the bias value
   */
  public void setOutputBias(double outputBias) {
    this.outputBias = outputBias;
  }

  /**
   * Get the output bias.
   *
   * @return the output bias
   */
  public double getOutputBias() {
    return outputBias;
  }

  /**
   * Get the current output value.
   *
   * @return the output
   */
  public double getOutput() {
    return output;
  }

  /**
   * Reset internal states to initial conditions.
   */
  public void reset() {
    state1 = 0.0;
    state2 = 0.0;
    initialized = false;
    deadTimeBuffer = null;
    deadTimeWriteIndex = 0;
    output = outputBias;
    calcIdentifier = null;
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    if (transmitter != null) {
      return transmitter.getMeasuredValue();
    }
    return 0.0;
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerSetPoint(double signal) {
    // Transfer functions don't have set points — interpret as input bias
    this.inputBias = signal;
  }

  /** {@inheritDoc} */
  @Override
  public double getControllerSetPoint() {
    return inputBias;
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
  public void setTransmitter(MeasurementDeviceInterface device) {
    this.transmitter = device;
  }

  /**
   * Get the transmitter currently bound to this block.
   *
   * @return bound transmitter, or {@code null} when the transient input is supplied directly
   */
  public MeasurementDeviceInterface getTransmitter() {
    return transmitter;
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    if (hasRunTransient(id)) {
      return;
    }
    if (!isActive) {
      output = initResponse;
      calcIdentifier = id;
      return;
    }

    double rawInput;
    if (transmitter != null) {
      rawInput = transmitter.getMeasuredValue();
    } else {
      rawInput = initResponse;
    }

    double u = rawInput - inputBias; // deviation variable

    if (!initialized) {
      initializeStates(u, dt);
    }

    double y;
    switch (type) {
    case FIRST_ORDER_LAG:
      y = computeFirstOrderLag(u, dt);
      break;
    case LEAD_LAG:
      y = computeLeadLag(u, dt);
      break;
    case DEAD_TIME:
      y = computeDeadTime(u, dt);
      break;
    case SECOND_ORDER:
      y = computeSecondOrder(u, dt);
      break;
    default:
      y = gain * u;
    }

    output = y + outputBias;
    calcIdentifier = id;
  }

  /** {@inheritDoc} */
  @Override
  public boolean hasRunTransient(UUID id) {
    return id != null && id.equals(calcIdentifier);
  }

  /** {@inheritDoc} */
  @Override
  public double getResponse() {
    return output;
  }

  /** {@inheritDoc} */
  @Override
  public boolean isReverseActing() {
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void setReverseActing(boolean reverseActing) {
    // Not applicable for transfer function blocks
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerParameters(double Kp, double Ti, double Td) {
    // Map to transfer function parameters: Kp=gain, Ti=lagTime, Td=leadTime
    this.gain = Kp;
    if (Ti > 0) {
      this.lagTime = Ti;
    }
    if (Td >= 0) {
      this.leadTime = Td;
    }
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

  /** {@inheritDoc} */
  @Override
  public String getTransientStateIdentity() {
    if (transientStateParticipantId == null || transientStateParticipantId.trim().isEmpty()) {
      transientStateParticipantId = UUID.randomUUID().toString();
    }
    return "controller:transfer-function:" + transientStateParticipantId;
  }

  /**
   * The snapshot is complete only for this concrete transfer-function implementation.
   *
   * @return blocking diagnostic for subclasses, otherwise {@code null}
   */
  @Override
  public String getTransientStateCoverageIssue() {
    if (getClass() != TransferFunctionBlock.class) {
      return "transfer-function subclass " + getClass().getName()
          + " must provide a snapshot that includes subclass-owned mutable state";
    }
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public TransferFunctionState captureTransientState() {
    return new TransferFunctionState(getTransientStateIdentity(), getName(), gain, lagTime, leadTime, lagTime2,
        deadTime, inputBias, outputBias, state1, state2, deadTimeBuffer == null ? null : deadTimeBuffer.clone(),
        deadTimeWriteIndex, initialized, output, transmitter, unit, isActive, calcIdentifier);
  }

  /** {@inheritDoc} */
  @Override
  public void restoreTransientState(TransferFunctionState snapshot) {
    if (snapshot == null) {
      throw new IllegalArgumentException("Transfer-function transient snapshot cannot be null");
    }
    if (!getTransientStateIdentity().equals(snapshot.stateIdentity)) {
      throw new IllegalArgumentException(
          "Transfer-function snapshot identity does not match " + getTransientStateIdentity());
    }
    setName(snapshot.name);
    gain = snapshot.gain;
    lagTime = snapshot.lagTime;
    leadTime = snapshot.leadTime;
    lagTime2 = snapshot.lagTime2;
    deadTime = snapshot.deadTime;
    inputBias = snapshot.inputBias;
    outputBias = snapshot.outputBias;
    state1 = snapshot.state1;
    state2 = snapshot.state2;
    deadTimeBuffer = snapshot.deadTimeBuffer == null ? null : snapshot.deadTimeBuffer.clone();
    deadTimeWriteIndex = snapshot.deadTimeWriteIndex;
    initialized = snapshot.initialized;
    output = snapshot.output;
    transmitter = snapshot.transmitter;
    unit = snapshot.unit;
    isActive = snapshot.active;
    calcIdentifier = snapshot.calcIdentifier;
  }

  // --- Private computation methods ---

  private void initializeStates(double u, double dt) {
    // Initialize states for bumpless start (assume steady state)
    state1 = gain * u;
    state2 = gain * u;

    // Initialize dead time buffer if needed
    if (deadTime > 0 && dt > 0) {
      int bufferSize = Math.max(1, (int) Math.ceil(deadTime / dt));
      deadTimeBuffer = new double[bufferSize];
      double steadyStateValue = gain * u;
      for (int i = 0; i < bufferSize; i++) {
        deadTimeBuffer[i] = steadyStateValue;
      }
      deadTimeWriteIndex = 0;
    }

    initialized = true;
  }

  /**
   * First-order lag: y(k) = alpha * y(k-1) + (1 - alpha) * K * u(k) where alpha = tau / (tau + dt).
   *
   * @param u the input signal value
   * @param dt the time step in seconds
   * @return the filtered output value
   */
  private double computeFirstOrderLag(double u, double dt) {
    double alpha = lagTime / (lagTime + dt);
    state1 = alpha * state1 + (1.0 - alpha) * gain * u;

    // Apply dead time if configured
    if (deadTime > 0 && deadTimeBuffer != null) {
      return applyDeadTime(state1, dt);
    }
    return state1;
  }

  /**
   * Lead-lag: implemented as a first-order lag plus a derivative lead correction. Discretized as: y(k) =
   * (tauLead/tauLag) * K * u(k) + (1 - tauLead/tauLag) * lagFiltered(K*u).
   *
   * @param u the input signal value
   * @param dt the time step in seconds
   * @return the lead-lag filtered output value
   */
  private double computeLeadLag(double u, double dt) {
    double alpha = lagTime / (lagTime + dt);
    double ku = gain * u;

    // Lag filter on the input
    state1 = alpha * state1 + (1.0 - alpha) * ku;

    double y;
    if (lagTime > 0) {
      double ratio = leadTime / lagTime;
      y = ratio * ku + (1.0 - ratio) * state1;
    } else {
      y = ku;
    }

    // Apply dead time if configured
    if (deadTime > 0 && deadTimeBuffer != null) {
      return applyDeadTime(y, dt);
    }
    return y;
  }

  /**
   * Pure dead time: stores the input in a circular buffer and reads out the delayed value.
   *
   * @param u the input signal value
   * @param dt the time step in seconds
   * @return the delayed output value
   */
  private double computeDeadTime(double u, double dt) {
    if (deadTimeBuffer == null || deadTimeBuffer.length == 0) {
      return gain * u;
    }

    // Read the oldest value (delayed output)
    double delayedOutput = deadTimeBuffer[deadTimeWriteIndex];

    // Write current value
    deadTimeBuffer[deadTimeWriteIndex] = gain * u;
    deadTimeWriteIndex = (deadTimeWriteIndex + 1) % deadTimeBuffer.length;

    return delayedOutput;
  }

  /**
   * Second-order system: cascade of two first-order lags. G(s) = K / (tau1*s + 1)(tau2*s + 1)
   *
   * @param u the input signal value
   * @param dt the time step in seconds
   * @return the second-order filtered output value
   */
  private double computeSecondOrder(double u, double dt) {
    // First lag
    double alpha1 = lagTime / (lagTime + dt);
    state1 = alpha1 * state1 + (1.0 - alpha1) * gain * u;

    // Second lag
    double effectiveTau2 = lagTime2 > 0 ? lagTime2 : lagTime;
    double alpha2 = effectiveTau2 / (effectiveTau2 + dt);
    state2 = alpha2 * state2 + (1.0 - alpha2) * state1;

    // Apply dead time if configured
    if (deadTime > 0 && deadTimeBuffer != null) {
      return applyDeadTime(state2, dt);
    }
    return state2;
  }

  /**
   * Apply dead time via circular buffer.
   *
   * @param currentValue the current signal value to delay
   * @param dt the time step in seconds
   * @return the delayed output value from the circular buffer
   */
  private double applyDeadTime(double currentValue, double dt) {
    if (deadTimeBuffer == null || deadTimeBuffer.length == 0) {
      return currentValue;
    }
    double delayedOutput = deadTimeBuffer[deadTimeWriteIndex];
    deadTimeBuffer[deadTimeWriteIndex] = currentValue;
    deadTimeWriteIndex = (deadTimeWriteIndex + 1) % deadTimeBuffer.length;
    return delayedOutput;
  }

  /** Immutable rollback point for transfer-function configuration, bindings, and dynamic state. */
  public static final class TransferFunctionState implements Serializable {
    private static final long serialVersionUID = 1000L;

    private final String stateIdentity;
    private final String name;
    private final double gain;
    private final double lagTime;
    private final double leadTime;
    private final double lagTime2;
    private final double deadTime;
    private final double inputBias;
    private final double outputBias;
    private final double state1;
    private final double state2;
    private final double[] deadTimeBuffer;
    private final int deadTimeWriteIndex;
    private final boolean initialized;
    private final double output;
    private final MeasurementDeviceInterface transmitter;
    private final String unit;
    private final boolean active;
    private final UUID calcIdentifier;

    private TransferFunctionState(String stateIdentity, String name, double gain, double lagTime, double leadTime,
        double lagTime2, double deadTime, double inputBias, double outputBias, double state1, double state2,
        double[] deadTimeBuffer, int deadTimeWriteIndex, boolean initialized, double output,
        MeasurementDeviceInterface transmitter, String unit, boolean active, UUID calcIdentifier) {
      this.stateIdentity = stateIdentity;
      this.name = name;
      this.gain = gain;
      this.lagTime = lagTime;
      this.leadTime = leadTime;
      this.lagTime2 = lagTime2;
      this.deadTime = deadTime;
      this.inputBias = inputBias;
      this.outputBias = outputBias;
      this.state1 = state1;
      this.state2 = state2;
      this.deadTimeBuffer = deadTimeBuffer;
      this.deadTimeWriteIndex = deadTimeWriteIndex;
      this.initialized = initialized;
      this.output = output;
      this.transmitter = transmitter;
      this.unit = unit;
      this.active = active;
      this.calcIdentifier = calcIdentifier;
    }
  }
}
