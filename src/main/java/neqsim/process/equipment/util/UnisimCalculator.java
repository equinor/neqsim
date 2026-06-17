package neqsim.process.equipment.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Generic UniSim calculation block used when importing UniSim operations that carry process stream
 * topology but do not have a one-to-one physical NeqSim equivalent.
 *
 * <p>
 * The block behaves as a deterministic pass-through by default: the first connected inlet stream is
 * cloned to one outlet stream and optional outlet specifications are applied. This is useful for
 * UniSim balance blocks, virtual streams, template-interface placeholders, and lightweight
 * calculator blocks where preserving connectivity is more important than silently dropping the
 * operation.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
public class UnisimCalculator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Inlet streams connected to the imported UniSim operation. */
  private final List<StreamInterface> inletStreams = new ArrayList<StreamInterface>();

  /** Outlet stream exposed to downstream NeqSim equipment. */
  private StreamInterface outletStream;

  /** UniSim operation type that created this block. */
  private String sourceOperationType = "";

  /** Description of the current calculation mode. */
  private String calculationMode = "passThrough";

  /** Optional outlet flow rate value. */
  private double outletFlowRate;

  /** Unit for the optional outlet flow rate value. */
  private String outletFlowRateUnit = "kg/hr";

  /** Whether an outlet flow rate override has been specified. */
  private boolean outletFlowRateSpecified = false;

  /** Optional outlet pressure value. */
  private double outletPressure;

  /** Unit for the optional outlet pressure value. */
  private String outletPressureUnit = "bara";

  /** Whether an outlet pressure override has been specified. */
  private boolean outletPressureSpecified = false;

  /** Optional outlet temperature value. */
  private double outletTemperature;

  /** Unit for the optional outlet temperature value. */
  private String outletTemperatureUnit = "K";

  /** Whether an outlet temperature override has been specified. */
  private boolean outletTemperatureSpecified = false;

  /** Optional outlet molar composition value. */
  private double[] outletMolarComposition;

  /** Whether an outlet molar composition override has been specified. */
  private boolean outletMolarCompositionSpecified = false;

  /**
   * Creates an empty UniSim calculator block.
   *
   * @param name name of the imported UniSim calculation block
   */
  public UnisimCalculator(String name) {
    super(name);
  }

  /**
   * Creates a UniSim calculator block with one inlet stream.
   *
   * @param name name of the imported UniSim calculation block
   * @param inletStream inlet stream used as the pass-through reference
   * @throws IllegalArgumentException if the inlet stream is null
   */
  public UnisimCalculator(String name, StreamInterface inletStream) {
    this(name);
    setInletStream(inletStream);
  }

  /**
   * Sets the single inlet stream, replacing any previously connected inlet streams.
   *
   * @param inletStream inlet stream used as the pass-through reference
   * @throws IllegalArgumentException if the inlet stream is null
   */
  public void setInletStream(StreamInterface inletStream) {
    inletStreams.clear();
    addStream(inletStream);
  }

  /**
   * Adds an inlet stream. The first inlet is used as the pass-through reference.
   *
   * @param inletStream inlet stream to add
   * @throws IllegalArgumentException if the inlet stream is null
   */
  public void addStream(StreamInterface inletStream) {
    if (inletStream == null) {
      throw new IllegalArgumentException("Inlet stream cannot be null");
    }
    inletStreams.add(inletStream);
    if (outletStream == null) {
      outletStream = createOutletStream(inletStream);
    }
  }

  /**
   * Gets the first inlet stream.
   *
   * @return first inlet stream, or null if no inlet is connected
   */
  public StreamInterface getInletStream() {
    return inletStreams.isEmpty() ? null : inletStreams.get(0);
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getInletStreams() {
    return Collections.unmodifiableList(inletStreams);
  }

  /**
   * Sets an explicit outlet stream.
   *
   * @param outletStream outlet stream exposed to downstream equipment
   */
  public void setOutletStream(StreamInterface outletStream) {
    this.outletStream = outletStream;
  }

  /**
   * Gets the outlet stream.
   *
   * @return outlet stream, or null if the block has not been connected
   */
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * Gets the outlet stream using the shorter NeqSim utility naming convention.
   *
   * @return outlet stream, or null if the block has not been connected
   */
  public StreamInterface getOutStream() {
    return getOutletStream();
  }

  /** {@inheritDoc} */
  @Override
  public List<StreamInterface> getOutletStreams() {
    if (outletStream == null) {
      return Collections.emptyList();
    }
    return Collections.singletonList(outletStream);
  }

  /**
   * Sets the UniSim operation type that created this calculator block.
   *
   * @param sourceOperationType UniSim operation type, for example {@code balanceop}
   */
  public void setSourceOperationType(String sourceOperationType) {
    this.sourceOperationType = sourceOperationType == null ? "" : sourceOperationType;
  }

  /**
   * Gets the UniSim operation type that created this calculator block.
   *
   * @return UniSim operation type, or an empty string if unset
   */
  public String getSourceOperationType() {
    return sourceOperationType;
  }

  /**
   * Sets the calculation mode label.
   *
   * @param calculationMode calculation mode label, for example {@code passThrough}
   */
  public void setCalculationMode(String calculationMode) {
    this.calculationMode = calculationMode == null ? "passThrough" : calculationMode;
  }

  /**
   * Gets the calculation mode label.
   *
   * @return calculation mode label
   */
  public String getCalculationMode() {
    return calculationMode;
  }

  /**
   * Sets an outlet flow rate override.
   *
   * @param flowRate outlet flow rate value
   * @param unit unit of the flow rate value
   */
  public void setOutletFlowRate(double flowRate, String unit) {
    outletFlowRate = flowRate;
    outletFlowRateUnit = unit;
    outletFlowRateSpecified = true;
  }

  /**
   * Sets an outlet flow rate override using the generic stream-style setter name.
   *
   * @param flowRate outlet flow rate value
   * @param unit unit of the flow rate value
   */
  public void setFlowRate(double flowRate, String unit) {
    setOutletFlowRate(flowRate, unit);
  }

  /**
   * Sets an outlet pressure override in bara.
   *
   * @param pressure outlet pressure in bara
   */
  public void setOutletPressure(double pressure) {
    setOutletPressure(pressure, "bara");
  }

  /**
   * Sets an outlet pressure override.
   *
   * @param pressure outlet pressure value
   * @param unit unit of the pressure value
   */
  public void setOutletPressure(double pressure, String unit) {
    outletPressure = pressure;
    outletPressureUnit = unit;
    outletPressureSpecified = true;
  }

  /**
   * Sets an outlet pressure override using the generic stream-style setter name.
   *
   * @param pressure outlet pressure value
   * @param unit unit of the pressure value
   */
  public void setPressure(double pressure, String unit) {
    setOutletPressure(pressure, unit);
  }

  /**
   * Sets an outlet temperature override in Kelvin.
   *
   * @param temperature outlet temperature in Kelvin
   */
  public void setOutletTemperature(double temperature) {
    setOutletTemperature(temperature, "K");
  }

  /**
   * Sets an outlet temperature override.
   *
   * @param temperature outlet temperature value
   * @param unit unit of the temperature value
   */
  public void setOutletTemperature(double temperature, String unit) {
    outletTemperature = temperature;
    outletTemperatureUnit = unit;
    outletTemperatureSpecified = true;
  }

  /**
   * Sets an outlet temperature override using the generic stream-style setter name.
   *
   * @param temperature outlet temperature value
   * @param unit unit of the temperature value
   */
  public void setTemperature(double temperature, String unit) {
    setOutletTemperature(temperature, unit);
  }

  /**
   * Sets an outlet molar composition override.
   *
   * @param molarComposition component molar fractions in NeqSim component order
   */
  public void setMolarComposition(double[] molarComposition) {
    outletMolarComposition =
        molarComposition == null ? null : Arrays.copyOf(molarComposition, molarComposition.length);
    outletMolarCompositionSpecified = molarComposition != null;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    if (outletStream != null) {
      return outletStream.getThermoSystem();
    }
    StreamInterface inletStream = getInletStream();
    return inletStream == null ? null : inletStream.getThermoSystem();
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure() {
    SystemInterface system = getThermoSystem();
    return system == null ? Double.NaN : system.getPressure();
  }

  /** {@inheritDoc} */
  @Override
  public double getPressure(String unit) {
    SystemInterface system = getThermoSystem();
    return system == null ? Double.NaN : system.getPressure(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature() {
    SystemInterface system = getThermoSystem();
    return system == null ? Double.NaN : system.getTemperature();
  }

  /** {@inheritDoc} */
  @Override
  public double getTemperature(String unit) {
    SystemInterface system = getThermoSystem();
    return system == null ? Double.NaN : system.getTemperature(unit);
  }

  /** {@inheritDoc} */
  @Override
  public double getMassBalance() {
    StreamInterface inletStream = getInletStream();
    if (inletStream == null || outletStream == null) {
      return 0.0;
    }
    return inletStream.getFlowRate("kg/sec") - outletStream.getFlowRate("kg/sec");
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    StreamInterface inletStream = getInletStream();
    if (inletStream != null) {
      if (outletStream == null) {
        outletStream = createOutletStream(inletStream);
      } else {
        outletStream.setThermoSystem(inletStream.getThermoSystem().clone());
      }
    }
    if (outletStream != null) {
      applyOutletSpecifications();
      outletStream.run(id);
      isSolved = outletStream.solved();
    } else {
      isSolved = true;
    }
    setCalculationIdentifier(id);
  }

  /**
   * Creates a new outlet stream from an inlet stream.
   *
   * @param inletStream inlet stream to clone
   * @return outlet stream with a cloned thermodynamic system
   */
  private StreamInterface createOutletStream(StreamInterface inletStream) {
    return new Stream(getName() + " outlet", inletStream.getThermoSystem().clone());
  }

  /**
   * Applies configured outlet specifications before flashing the outlet stream.
   */
  private void applyOutletSpecifications() {
    if (outletFlowRateSpecified) {
      outletStream.setFlowRate(outletFlowRate, outletFlowRateUnit);
    }
    if (outletPressureSpecified) {
      outletStream.setPressure(outletPressure, outletPressureUnit);
    }
    if (outletTemperatureSpecified) {
      outletStream.setTemperature(outletTemperature, outletTemperatureUnit);
    }
    if (outletMolarCompositionSpecified) {
      outletStream.getThermoSystem().setMolarComposition(outletMolarComposition);
    }
  }
}
