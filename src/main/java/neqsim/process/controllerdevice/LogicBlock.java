package neqsim.process.controllerdevice;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import neqsim.process.measurementdevice.MeasurementDeviceInterface;
import neqsim.util.NamedBaseClass;

/**
 * Logic operator block for control system simulation in dynamic mode. Supports AND, OR, NOT, NAND,
 * NOR, and XOR operations on boolean input signals. Each input signal is derived from a
 * {@link MeasurementDeviceInterface} by comparing the measured value against a configurable
 * threshold.
 *
 * <p>
 * The block evaluates its logic expression each transient step and produces a boolean output
 * accessible via {@link #getOutput()} (returns 1.0 for true, 0.0 for false). This output can drive
 * downstream equipment such as on/off valves, emergency shutdown sequences, or alarm interlocks.
 * </p>
 *
 * <p>
 * Example — interlock requiring high pressure AND high temperature:
 * </p>
 *
 * <pre>
 * LogicBlock esd = new LogicBlock("ESD-001", LogicBlock.Operator.AND);
 * esd.addInput(pressureTransmitter, 120.0, LogicBlock.Comparator.GREATER_THAN);
 * esd.addInput(temperatureTransmitter, 85.0, LogicBlock.Comparator.GREATER_THAN);
 * process.add(esd);
 * // During dynamic: esd.getOutput() returns 1.0 when both conditions are met
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class LogicBlock extends NamedBaseClass implements ControllerDeviceInterface {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /**
   * Logical operators supported by this block.
   */
  public enum Operator {
    /** Logical AND — output is true only when all inputs are true. */
    AND,
    /** Logical OR — output is true when any input is true. */
    OR,
    /** Logical NOT — inverts a single input. Only the first input is used. */
    NOT,
    /** Logical NAND — inverted AND. */
    NAND,
    /** Logical NOR — inverted OR. */
    NOR,
    /** Logical XOR — exclusive OR, true when an odd number of inputs are true. */
    XOR
  }

  /**
   * Comparison operators used to convert a continuous signal to a boolean.
   */
  public enum Comparator {
    /** True when measured value &gt; threshold. */
    GREATER_THAN,
    /** True when measured value &gt;= threshold. */
    GREATER_EQUAL,
    /** True when measured value &lt; threshold. */
    LESS_THAN,
    /** True when measured value &lt;= threshold. */
    LESS_EQUAL,
    /** True when measured value equals threshold (within tolerance). */
    EQUAL
  }

  /** Logic operator for this block. */
  private final Operator operator;

  /** Input definitions. */
  private final List<LogicInput> inputs = new ArrayList<>();

  /** Current output state: 1.0 = true, 0.0 = false. */
  private double output = 0.0;

  /** Whether this block is active in the simulation. */
  private boolean isActive = true;

  /** Controller unit string. */
  private String unit = "[bool]";

  /** Tolerance for EQUAL comparison. */
  private double equalityTolerance = 1e-6;

  /** UUID from last calculation. */
  protected UUID calcIdentifier;

  /**
   * Constructor for LogicBlock.
   *
   * @param name identifier for this logic block
   * @param operator the logical operation to perform
   */
  public LogicBlock(String name, Operator operator) {
    super(name);
    this.operator = operator;
  }

  /**
   * Add an input signal from a measurement device with a comparison threshold.
   *
   * @param device the measurement device providing the signal
   * @param threshold the threshold value for comparison
   * @param comparator how to compare the measurement against the threshold
   */
  public void addInput(MeasurementDeviceInterface device, double threshold,
      Comparator comparator) {
    inputs.add(new LogicInput(device, threshold, comparator));
  }

  /**
   * Add a boolean input that is always true or false (for testing or fixed interlocks).
   *
   * @param fixedValue true or false
   */
  public void addFixedInput(boolean fixedValue) {
    inputs.add(new FixedLogicInput(fixedValue));
  }

  /**
   * Add a chained input from another LogicBlock's output (for composite logic).
   *
   * @param upstreamBlock the upstream logic block whose output becomes this input
   */
  public void addInput(LogicBlock upstreamBlock) {
    inputs.add(new ChainedLogicInput(upstreamBlock));
  }

  /**
   * Get the current output of this logic block.
   *
   * @return 1.0 if the logic condition is true, 0.0 if false
   */
  public double getOutput() {
    return output;
  }

  /**
   * Get the current output as a boolean.
   *
   * @return true if the logic condition is met
   */
  public boolean getOutputBoolean() {
    return output > 0.5;
  }

  /**
   * Get the logical operator used by this block.
   *
   * @return the operator
   */
  public Operator getOperator() {
    return operator;
  }

  /**
   * Get the list of inputs (unmodifiable).
   *
   * @return list of logic inputs
   */
  public List<LogicInput> getInputs() {
    return Collections.unmodifiableList(inputs);
  }

  /**
   * Set the tolerance for EQUAL comparisons.
   *
   * @param tolerance the equality tolerance (must be non-negative)
   */
  public void setEqualityTolerance(double tolerance) {
    if (tolerance >= 0) {
      this.equalityTolerance = tolerance;
    }
  }

  /** {@inheritDoc} */
  @Override
  public double getMeasuredValue() {
    return output;
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerSetPoint(double signal) {
    // Not applicable for logic blocks — ignored
  }

  /** {@inheritDoc} */
  @Override
  public double getControllerSetPoint() {
    return 0.0;
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
    // For logic blocks, use addInput() instead
  }

  /** {@inheritDoc} */
  @Override
  public void runTransient(double initResponse, double dt, UUID id) {
    if (!isActive) {
      calcIdentifier = id;
      return;
    }

    // Evaluate each input to a boolean
    List<Boolean> boolInputs = new ArrayList<>();
    for (LogicInput input : inputs) {
      boolInputs.add(input.evaluate(equalityTolerance));
    }

    boolean result;
    switch (operator) {
      case AND:
        result = evaluateAnd(boolInputs);
        break;
      case OR:
        result = evaluateOr(boolInputs);
        break;
      case NOT:
        result = evaluateNot(boolInputs);
        break;
      case NAND:
        result = !evaluateAnd(boolInputs);
        break;
      case NOR:
        result = !evaluateOr(boolInputs);
        break;
      case XOR:
        result = evaluateXor(boolInputs);
        break;
      default:
        result = false;
    }

    output = result ? 1.0 : 0.0;
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
    // Not applicable for logic blocks
  }

  /** {@inheritDoc} */
  @Override
  public void setControllerParameters(double Kp, double Ti, double Td) {
    // Not applicable for logic blocks
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

  private boolean evaluateAnd(List<Boolean> values) {
    if (values.isEmpty()) {
      return false;
    }
    for (Boolean b : values) {
      if (!b) {
        return false;
      }
    }
    return true;
  }

  private boolean evaluateOr(List<Boolean> values) {
    for (Boolean b : values) {
      if (b) {
        return true;
      }
    }
    return false;
  }

  private boolean evaluateNot(List<Boolean> values) {
    if (values.isEmpty()) {
      return true;
    }
    return !values.get(0);
  }

  private boolean evaluateXor(List<Boolean> values) {
    int trueCount = 0;
    for (Boolean b : values) {
      if (b) {
        trueCount++;
      }
    }
    return (trueCount % 2) == 1;
  }

  // --- Input types ---

  /**
   * Input definition based on a measurement device and threshold comparison.
   */
  public static class LogicInput implements Serializable {
    private static final long serialVersionUID = 1L;
    /** The measurement device. */
    protected final MeasurementDeviceInterface device;
    /** The threshold value. */
    protected final double threshold;
    /** The comparator. */
    protected final Comparator comparator;

    /**
     * Create a logic input.
     *
     * @param device the measurement device
     * @param threshold the threshold value
     * @param comparator how to compare
     */
    public LogicInput(MeasurementDeviceInterface device, double threshold,
        Comparator comparator) {
      this.device = device;
      this.threshold = threshold;
      this.comparator = comparator;
    }

    /**
     * Evaluate this input to a boolean.
     *
     * @param equalityTolerance tolerance for EQUAL comparison
     * @return true if the condition is met
     */
    public boolean evaluate(double equalityTolerance) {
      double value = device.getMeasuredValue();
      switch (comparator) {
        case GREATER_THAN:
          return value > threshold;
        case GREATER_EQUAL:
          return value >= threshold;
        case LESS_THAN:
          return value < threshold;
        case LESS_EQUAL:
          return value <= threshold;
        case EQUAL:
          return Math.abs(value - threshold) <= equalityTolerance;
        default:
          return false;
      }
    }

    /**
     * Get the measurement device.
     *
     * @return the device
     */
    public MeasurementDeviceInterface getDevice() {
      return device;
    }

    /**
     * Get the threshold.
     *
     * @return the threshold
     */
    public double getThreshold() {
      return threshold;
    }

    /**
     * Get the comparator.
     *
     * @return the comparator
     */
    public Comparator getComparator() {
      return comparator;
    }
  }

  /**
   * Fixed boolean input for testing or permanent interlocks.
   */
  private static class FixedLogicInput extends LogicInput {
    private static final long serialVersionUID = 1L;
    private final boolean fixedValue;

    FixedLogicInput(boolean fixedValue) {
      super(null, 0.0, Comparator.GREATER_THAN);
      this.fixedValue = fixedValue;
    }

    @Override
    public boolean evaluate(double equalityTolerance) {
      return fixedValue;
    }
  }

  /**
   * Chained input from another LogicBlock.
   */
  private static class ChainedLogicInput extends LogicInput {
    private static final long serialVersionUID = 1L;
    private final LogicBlock upstream;

    ChainedLogicInput(LogicBlock upstream) {
      super(null, 0.5, Comparator.GREATER_THAN);
      this.upstream = upstream;
    }

    @Override
    public boolean evaluate(double equalityTolerance) {
      return upstream.getOutputBoolean();
    }
  }
}
