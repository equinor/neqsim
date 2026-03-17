package neqsim.process.equipment.separator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Solids separator for bio-processing applications.
 *
 * <p>
 * Separates a feed stream into a solids-rich (cake/retentate) stream and a liquid-clear
 * (filtrate/permeate) stream. The separation is based on component-specific split fractions that
 * define what fraction of each component goes to the solids outlet.
 * </p>
 *
 * <p>
 * This is the base class for various solids-liquid separation equipment such as centrifuges, rotary
 * vacuum filters, pressure filters, and screw presses. Subclasses can override the defaults for
 * energy consumption and efficiency.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * SolidsSeparator centrifuge = new SolidsSeparator("Centrifuge", feedStream);
 * centrifuge.setSolidsSplitFraction("cell_mass", 0.99); // 99% recovery
 * centrifuge.setSolidsSplitFraction("fiber", 0.95);
 * centrifuge.setMoistureContent(0.40); // 40% moisture in cake
 * centrifuge.run();
 *
 * StreamInterface cake = centrifuge.getSolidsOutStream();
 * StreamInterface filtrate = centrifuge.getLiquidOutStream();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class SolidsSeparator extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(SolidsSeparator.class);

  /** Inlet feed stream. */
  protected StreamInterface inletStream;

  /** Solids-rich outlet stream (cake/retentate). */
  protected StreamInterface solidsOutStream;

  /** Liquid-clear outlet stream (filtrate/permeate). */
  protected StreamInterface liquidOutStream;

  /**
   * Component-specific split fraction to solids outlet. Keys are component names, values are
   * fractions (0-1) going to solids outlet. Components not listed default to
   * {@link #defaultSolidsSplit}.
   */
  private Map<String, Double> solidsSplitFractions = new LinkedHashMap<String, Double>();

  /** Default split fraction to solids outlet for components not explicitly specified. */
  private double defaultSolidsSplit = 0.0;

  /** Target moisture content (mass fraction) in the solids cake. */
  private double moistureContent = 0.50;

  /** Pressure drop across the separator in bar. */
  private double pressureDrop = 0.0;

  /** Specific energy consumption in kWh per m3 of feed. */
  private double specificEnergy = 5.0;

  /** Calculated power consumption in kW. */
  private double powerConsumption = 0.0;

  /** Equipment type string for reporting. */
  protected String equipmentType = "SolidsSeparator";

  /**
   * Constructor for SolidsSeparator.
   *
   * @param name name of the separator
   */
  public SolidsSeparator(String name) {
    super(name);
  }

  /**
   * Constructor for SolidsSeparator with inlet stream.
   *
   * @param name name of the separator
   * @param inletStream the feed stream to separate
   */
  public SolidsSeparator(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Set the inlet feed stream.
   *
   * @param inletStream the feed stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;

    // Initialize outlet streams from inlet
    SystemInterface solidsSys = inletStream.getThermoSystem().clone();
    solidsOutStream = new Stream(getName() + " solids out", solidsSys);

    SystemInterface liquidSys = inletStream.getThermoSystem().clone();
    liquidOutStream = new Stream(getName() + " liquid out", liquidSys);
  }

  /**
   * Get the inlet stream.
   *
   * @return inlet stream
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Get the solids-rich outlet stream.
   *
   * @return solids outlet stream
   */
  public StreamInterface getSolidsOutStream() {
    return solidsOutStream;
  }

  /**
   * Get the liquid-clear outlet stream.
   *
   * @return liquid outlet stream
   */
  public StreamInterface getLiquidOutStream() {
    return liquidOutStream;
  }

  /**
   * Set the fraction of a specific component going to the solids outlet.
   *
   * @param componentName name of the component
   * @param fraction fraction going to solids (0.0 to 1.0)
   */
  public void setSolidsSplitFraction(String componentName, double fraction) {
    if (fraction < 0.0 || fraction > 1.0) {
      throw new IllegalArgumentException("Split fraction must be between 0 and 1, got " + fraction);
    }
    solidsSplitFractions.put(componentName, fraction);
  }

  /**
   * Get the solids split fraction for a component.
   *
   * @param componentName component name
   * @return split fraction (0.0 to 1.0)
   */
  public double getSolidsSplitFraction(String componentName) {
    Double frac = solidsSplitFractions.get(componentName);
    return frac != null ? frac : defaultSolidsSplit;
  }

  /**
   * Set the default split fraction for components not explicitly specified.
   *
   * @param fraction default fraction to solids (0.0 to 1.0)
   */
  public void setDefaultSolidsSplit(double fraction) {
    this.defaultSolidsSplit = fraction;
  }

  /**
   * Get the default solids split fraction.
   *
   * @return default split fraction
   */
  public double getDefaultSolidsSplit() {
    return defaultSolidsSplit;
  }

  /**
   * Set the target moisture content of the solids cake.
   *
   * @param moistureFraction mass fraction of liquid in cake (0.0 to 1.0)
   */
  public void setMoistureContent(double moistureFraction) {
    this.moistureContent = moistureFraction;
  }

  /**
   * Get the moisture content of the cake.
   *
   * @return moisture mass fraction
   */
  public double getMoistureContent() {
    return moistureContent;
  }

  /**
   * Set the pressure drop across the separator.
   *
   * @param dP pressure drop in bar
   */
  public void setPressureDrop(double dP) {
    this.pressureDrop = dP;
  }

  /**
   * Get the pressure drop.
   *
   * @return pressure drop in bar
   */
  public double getPressureDrop() {
    return pressureDrop;
  }

  /**
   * Set specific energy consumption.
   *
   * @param energy specific energy in kWh/m3
   */
  public void setSpecificEnergy(double energy) {
    this.specificEnergy = energy;
  }

  /**
   * Get the specific energy consumption.
   *
   * @return energy in kWh/m3
   */
  public double getSpecificEnergy() {
    return specificEnergy;
  }

  /**
   * Get the calculated power consumption in kW.
   *
   * @return power in kW
   */
  public double getPowerConsumption() {
    return powerConsumption;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface feedSystem = inletStream.getThermoSystem().clone();

    // Create two output systems - initially clone the feed
    SystemInterface solidsSys = feedSystem.clone();
    SystemInterface liquidSys = feedSystem.clone();

    int numComponents = feedSystem.getNumberOfComponents();

    // Apply component-wise split
    for (int i = 0; i < numComponents; i++) {
      String compName = feedSystem.getComponent(i).getComponentName();
      double totalMoles = feedSystem.getComponent(i).getNumberOfmoles();

      // Determine split fraction for this component
      Double specifiedFrac = solidsSplitFractions.get(compName);
      double solidsFrac = specifiedFrac != null ? specifiedFrac : defaultSolidsSplit;

      double solidsMoles = totalMoles * solidsFrac;
      double liquidMoles = totalMoles * (1.0 - solidsFrac);

      // Adjust the solids system: set to solidsMoles
      // We subtract the difference from the cloned system
      double solidsCurrentMoles = solidsSys.getComponent(i).getNumberOfmoles();
      solidsSys.addComponent(i, solidsMoles - solidsCurrentMoles);

      // Adjust the liquid system: set to liquidMoles
      double liquidCurrentMoles = liquidSys.getComponent(i).getNumberOfmoles();
      liquidSys.addComponent(i, liquidMoles - liquidCurrentMoles);
    }

    // Set pressures
    double outPressure = feedSystem.getPressure() - pressureDrop;
    solidsSys.setPressure(outPressure);
    liquidSys.setPressure(outPressure);
    solidsSys.setTemperature(feedSystem.getTemperature());
    liquidSys.setTemperature(feedSystem.getTemperature());

    // Flash both outlet systems
    try {
      ThermodynamicOperations solidsOps = new ThermodynamicOperations(solidsSys);
      solidsOps.TPflash();
      solidsSys.initProperties();
    } catch (Exception ex) {
      logger.warn("Flash failed for solids outlet: {}", ex.getMessage());
    }

    try {
      ThermodynamicOperations liquidOps = new ThermodynamicOperations(liquidSys);
      liquidOps.TPflash();
      liquidSys.initProperties();
    } catch (Exception ex) {
      logger.warn("Flash failed for liquid outlet: {}", ex.getMessage());
    }

    // Estimate power consumption
    try {
      double feedVolumeRate = feedSystem.getFlowRate("m3/hr");
      powerConsumption = specificEnergy * feedVolumeRate;
    } catch (Exception ex) {
      powerConsumption = 0.0;
    }

    solidsOutStream.setThermoSystem(solidsSys);
    liquidOutStream.setThermoSystem(liquidSys);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Get a map representation of this separator.
   *
   * @return map of properties
   */
  private Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", getName());
    map.put("type", equipmentType);
    map.put("moistureContent", moistureContent);
    map.put("pressureDrop_bar", pressureDrop);
    map.put("specificEnergy_kWh_m3", specificEnergy);
    map.put("powerConsumption_kW", powerConsumption);
    map.put("solidsSplitFractions", solidsSplitFractions);
    return map;
  }
}
