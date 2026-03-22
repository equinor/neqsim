package neqsim.process.equipment.compressor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.TwoPortEquipment;
import neqsim.process.equipment.capacity.CapacityConstrainedEquipment;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;

/**
 * Composite equipment representing a single compressor stage (train).
 *
 * <p>
 * A CompressorTrain wraps the common pattern of inlet separator (scrubber), compressor, and
 * aftercooler into a single reusable equipment unit. This simplifies process model construction and
 * provides aggregate performance reporting.
 * </p>
 *
 * <h2>Equipment Sequence</h2>
 * <ol>
 * <li>Inlet scrubber (liquid knockout) — optional, enabled by default</li>
 * <li>Compressor stage</li>
 * <li>Aftercooler — optional, enabled by default</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * CompressorTrain train = new CompressorTrain("HP Compressor", feedStream);
 * train.getCompressor().setOutletPressure(85.0);
 * train.getCompressor().setPolytropicEfficiency(0.76);
 * train.getCompressor().setUsePolytropicCalc(true);
 * train.getCooler().setOutTemperature(273.15 + 35.0);
 * train.run();
 *
 * double power = train.getPower("kW");
 * double outTemp = train.getOutletTemperature("C");
 * }</pre>
 *
 * @author NeqSim Development Team
 * @version 1.0
 * @see Compressor
 * @see neqsim.process.design.template.GasCompressionTemplate
 */
public class CompressorTrain extends TwoPortEquipment implements CapacityConstrainedEquipment {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1001L;

  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(CompressorTrain.class);

  /** Inlet scrubber for liquid knockout. */
  private Separator inletScrubber;

  /** The compressor stage. */
  private Compressor compressor;

  /** Aftercooler. */
  private Cooler aftercooler;

  /** Whether to include an inlet scrubber. */
  private boolean useInletScrubber = true;

  /** Whether to include an aftercooler. */
  private boolean useAftercooler = true;

  /** Default aftercooler outlet temperature in Kelvin. */
  private double aftercoolerTemperature = 273.15 + 35.0;

  /**
   * Constructor with name and inlet stream.
   *
   * @param name train name
   * @param inletStream inlet stream
   */
  public CompressorTrain(String name, StreamInterface inletStream) {
    super(name, inletStream);
    buildTrain();
  }

  /**
   * Constructor with name only.
   *
   * @param name train name
   */
  public CompressorTrain(String name) {
    super(name);
  }

  /**
   * Builds the internal equipment chain based on current configuration.
   */
  private void buildTrain() {
    StreamInterface feed = getInletStream();
    if (feed == null) {
      return;
    }

    StreamInterface compressorInlet = feed;

    if (useInletScrubber) {
      inletScrubber = new Separator(getName() + " scrubber", feed);
      compressorInlet = inletScrubber.getGasOutStream();
    }

    compressor = new Compressor(getName() + " compressor", compressorInlet);

    if (useAftercooler) {
      aftercooler = new Cooler(getName() + " aftercooler", compressor.getOutletStream());
      aftercooler.setOutTemperature(aftercoolerTemperature);
      setOutletStream((Stream) aftercooler.getOutletStream());
    } else {
      setOutletStream((Stream) compressor.getOutletStream());
    }
  }

  /** {@inheritDoc} */
  @Override
  public void setInletStream(StreamInterface inStream) {
    super.setInletStream(inStream);
    buildTrain();
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (getInletStream() == null) {
      logger.warn("CompressorTrain '{}' has no inlet stream set.", getName());
      return;
    }

    // Rebuild if needed (e.g., after deserialization)
    if (compressor == null) {
      buildTrain();
    }

    if (useInletScrubber && inletScrubber != null) {
      inletScrubber.run(id);
    }

    compressor.run(id);

    if (useAftercooler && aftercooler != null) {
      aftercooler.run(id);
      outStream = aftercooler.getOutletStream();
    } else {
      outStream = compressor.getOutletStream();
    }

    setCalculationIdentifier(id);
  }

  /**
   * Get the compressor.
   *
   * @return the compressor stage
   */
  public Compressor getCompressor() {
    return compressor;
  }

  /**
   * Get the inlet scrubber.
   *
   * @return the inlet scrubber, or null if not enabled
   */
  public Separator getInletScrubber() {
    return inletScrubber;
  }

  /**
   * Get the aftercooler.
   *
   * @return the aftercooler, or null if not enabled
   */
  public Cooler getAftercooler() {
    return aftercooler;
  }

  /**
   * Set whether to include an inlet scrubber for liquid knockout.
   *
   * @param useInletScrubber true to include inlet scrubber
   */
  public void setUseInletScrubber(boolean useInletScrubber) {
    this.useInletScrubber = useInletScrubber;
    buildTrain();
  }

  /**
   * Set whether to include an aftercooler.
   *
   * @param useAftercooler true to include aftercooler
   */
  public void setUseAftercooler(boolean useAftercooler) {
    this.useAftercooler = useAftercooler;
    buildTrain();
  }

  /**
   * Set the aftercooler outlet temperature.
   *
   * @param temperature temperature in Kelvin
   */
  public void setAftercoolerTemperature(double temperature) {
    this.aftercoolerTemperature = temperature;
    if (aftercooler != null) {
      aftercooler.setOutTemperature(temperature);
    }
  }

  /**
   * Set the aftercooler outlet temperature with units.
   *
   * @param temperature temperature value
   * @param unit temperature unit ("C" or "K")
   */
  public void setAftercoolerTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      setAftercoolerTemperature(temperature + 273.15);
    } else {
      setAftercoolerTemperature(temperature);
    }
  }

  /**
   * Get the total power consumption of the compressor stage.
   *
   * @param unit power unit ("W", "kW", or "MW")
   * @return power in the requested unit
   */
  public double getPower(String unit) {
    if (compressor == null) {
      return 0.0;
    }
    return compressor.getPower(unit);
  }

  /**
   * Get the total power consumption in watts.
   *
   * @return power in watts
   */
  public double getPower() {
    if (compressor == null) {
      return 0.0;
    }
    return compressor.getPower();
  }

  /**
   * Get the outlet temperature.
   *
   * @param unit temperature unit ("C" or "K")
   * @return outlet temperature
   */
  public double getOutletTemperature(String unit) {
    StreamInterface outStr = getOutletStream();
    if (outStr == null || outStr.getThermoSystem() == null) {
      return Double.NaN;
    }
    if ("C".equalsIgnoreCase(unit)) {
      return outStr.getTemperature("C");
    }
    return outStr.getTemperature("K");
  }

  /**
   * Get the compressor polytropic efficiency.
   *
   * @return polytropic efficiency (0-1)
   */
  public double getPolytropicEfficiency() {
    if (compressor == null) {
      return 0.0;
    }
    return compressor.getPolytropicEfficiency();
  }

  /**
   * Get the actual compression ratio across the compressor.
   *
   * @return compression ratio (outlet pressure / inlet pressure)
   */
  public double getCompressionRatio() {
    if (compressor == null) {
      return 1.0;
    }
    return compressor.getActualCompressionRatio();
  }

  /**
   * Get the polytropic head in kJ/kg.
   *
   * @return polytropic head
   */
  public double getPolytropicHead() {
    if (compressor == null) {
      return 0.0;
    }
    return compressor.getPolytropicFluidHead();
  }

  /**
   * Check if the compressor is in surge.
   *
   * @return true if operating point is at or below surge line
   */
  public boolean isSurging() {
    if (compressor == null) {
      return false;
    }
    return compressor.getAntiSurge().isSurge();
  }

  /**
   * Get the distance to surge as a fraction.
   *
   * @return surge margin ratio (currentFlow/surgeFlow - 1)
   */
  public double getDistanceToSurge() {
    if (compressor == null) {
      return Double.NaN;
    }
    return compressor.getDistanceToSurge();
  }

  /**
   * Get all internal equipment as an ordered list.
   *
   * @return list of internal equipment (scrubber, compressor, cooler)
   */
  public List<ProcessEquipmentInterface> getInternalEquipment() {
    List<ProcessEquipmentInterface> equipment = new ArrayList<ProcessEquipmentInterface>();
    if (useInletScrubber && inletScrubber != null) {
      equipment.add(inletScrubber);
    }
    if (compressor != null) {
      equipment.add(compressor);
    }
    if (useAftercooler && aftercooler != null) {
      equipment.add(aftercooler);
    }
    return Collections.unmodifiableList(equipment);
  }

  /** {@inheritDoc} */
  @Override
  public Map<String, CapacityConstraint> getCapacityConstraints() {
    if (compressor == null) {
      return Collections.emptyMap();
    }
    return compressor.getCapacityConstraints();
  }

  /** {@inheritDoc} */
  @Override
  public CapacityConstraint getBottleneckConstraint() {
    if (compressor == null) {
      return null;
    }
    return compressor.getBottleneckConstraint();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isCapacityExceeded() {
    if (compressor == null) {
      return false;
    }
    return compressor.isCapacityExceeded();
  }

  /** {@inheritDoc} */
  @Override
  public boolean isHardLimitExceeded() {
    if (compressor == null) {
      return false;
    }
    return compressor.isHardLimitExceeded();
  }

  /** {@inheritDoc} */
  @Override
  public double getMaxUtilization() {
    if (compressor == null) {
      return 0.0;
    }
    return compressor.getMaxUtilization();
  }

  /** {@inheritDoc} */
  @Override
  public void addCapacityConstraint(CapacityConstraint constraint) {
    if (compressor != null) {
      compressor.addCapacityConstraint(constraint);
    }
  }

  /** {@inheritDoc} */
  @Override
  public boolean removeCapacityConstraint(String constraintName) {
    if (compressor != null) {
      return compressor.removeCapacityConstraint(constraintName);
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public void clearCapacityConstraints() {
    if (compressor != null) {
      compressor.clearCapacityConstraints();
    }
  }

  /**
   * Get a summary of the compressor train performance.
   *
   * @return formatted summary string
   */
  public String getPerformanceSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("CompressorTrain: ").append(getName()).append("\n");

    if (compressor != null) {
      sb.append(String.format("  Inlet P: %.2f bara, Outlet P: %.2f bara%n",
          compressor.getInletPressure(), compressor.getOutletPressure()));
      sb.append(String.format("  Compression Ratio: %.3f%n", getCompressionRatio()));
      sb.append(
          String.format("  Polytropic Efficiency: %.1f%%%n", getPolytropicEfficiency() * 100.0));
      sb.append(String.format("  Power: %.1f kW%n", getPower("kW")));
      sb.append(String.format("  Polytropic Head: %.1f kJ/kg%n", getPolytropicHead()));
      sb.append(String.format("  Speed: %.0f RPM%n", compressor.getSpeed()));
    }

    StreamInterface outStr = getOutletStream();
    if (outStr != null && outStr.getThermoSystem() != null) {
      sb.append(String.format("  Outlet T: %.1f C%n", outStr.getTemperature("C")));
    }

    if (isSurging()) {
      sb.append("  ** WARNING: COMPRESSOR IS IN SURGE **\n");
    }

    double surgeMargin = getDistanceToSurge();
    if (!Double.isNaN(surgeMargin)) {
      sb.append(String.format("  Surge Margin: %.1f%%%n", surgeMargin * 100.0));
    }

    return sb.toString();
  }
}
