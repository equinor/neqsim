package neqsim.process.controllerdevice;

import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.util.NamedBaseClass;

/**
 * Transfer function block for representing control dynamics in dynamic simulation. Supports
 * first-order lag, lead-lag, pure dead time (transport delay), and second-order dynamics. These are
 * the fundamental building blocks used in control system design and simulation.
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
 * TransferFunctionBlock leadLag =
 *     new TransferFunctionBlock("FF-comp", TransferFunctionBlock.Type.LEAD_LAG);
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
public class TransferFunctionBlock extends NamedBaseClass implements ControllerDeviceInterface {
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
   * Set the primary lag time constant [s]. For FIRST_ORDER_LAG this is tau. For LEAD_LAG this is
   * tauLag. For SECOND_ORDER this is tau1.
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

  /** {@inheritDoc} */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
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
   * Lead-lag: implemented as a first-order lag plus a derivative lead correction. Discretized as:
   * y(k) = (tauLead/tauLag) * K * u(k) + (1 - tauLead/tauLag) * lagFiltered(K*u).
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
}
