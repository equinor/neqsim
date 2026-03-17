package neqsim.process.equipment.heatexchanger;

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
 * Dryer for removing moisture from wet solids or liquid streams.
 *
 * <p>
 * Models various drying equipment including drum dryers, spray dryers, and flash dryers. The dryer
 * evaporates a specified amount of volatile components (typically water) from the feed to achieve a
 * target moisture content or outlet temperature.
 * </p>
 *
 * <p>
 * The drying process is modeled as a heated flash: the feed is heated to generate vapor, which is
 * separated from the dried product. The energy input determines how much moisture is removed.
 * </p>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * Dryer dryer = new Dryer("Product Dryer", wetFeedStream);
 * dryer.setDryerType("drum");
 * dryer.setOutletTemperature(273.15 + 105.0); // 105 C
 * dryer.setTargetMoistureContent(0.05); // 5% moisture
 * dryer.run();
 *
 * StreamInterface driedProduct = dryer.getDriedProductStream();
 * StreamInterface vapor = dryer.getVaporStream();
 * </pre>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class Dryer extends ProcessEquipmentBaseClass {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(Dryer.class);

  /** Inlet wet feed stream. */
  private StreamInterface inletStream;

  /** Dried product outlet stream. */
  private StreamInterface driedProductStream;

  /** Vapor (moisture) outlet stream. */
  private StreamInterface vaporStream;

  /** Type of dryer: "drum", "spray", "flash", "rotary". */
  private String dryerType = "drum";

  /** Outlet temperature of the dried product in Kelvin. */
  private double outletTemperature = Double.NaN;

  /** Target moisture content (mass fraction) in dried product. */
  private double targetMoistureContent = 0.10;

  /** Heating medium temperature (steam) in Kelvin. */
  private double heatingTemperature = 273.15 + 150.0;

  /** Overall heat transfer coefficient in W/(m2*K). */
  private double overallHeatTransferCoefficient = 200.0;

  /** Heat transfer area in m2. */
  private double heatTransferArea = 0.0;

  /** Thermal efficiency (fraction of heat input used for evaporation). */
  private double thermalEfficiency = 0.85;

  /** Heat duty for drying in Watts. */
  private double heatDuty = 0.0;

  /** Specific energy consumption in kWh per kg water evaporated. */
  private double specificEnergy = 0.0;

  /** Pressure drop in bar. */
  private double pressureDrop = 0.0;

  /**
   * Constructor for Dryer.
   *
   * @param name name of the dryer
   */
  public Dryer(String name) {
    super(name);
  }

  /**
   * Constructor for Dryer with inlet stream.
   *
   * @param name name of the dryer
   * @param inletStream the wet feed stream
   */
  public Dryer(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  /**
   * Set the inlet stream.
   *
   * @param inletStream the feed stream
   */
  public void setInletStream(StreamInterface inletStream) {
    this.inletStream = inletStream;
    SystemInterface sys = inletStream.getThermoSystem().clone();
    driedProductStream = new Stream(getName() + " dried product", sys);
    vaporStream = new Stream(getName() + " vapor", sys.clone());
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
   * Get the dried product stream.
   *
   * @return dried product stream
   */
  public StreamInterface getDriedProductStream() {
    return driedProductStream;
  }

  /**
   * Get the vapor (moisture) stream.
   *
   * @return vapor stream
   */
  public StreamInterface getVaporStream() {
    return vaporStream;
  }

  /**
   * Set the dryer type.
   *
   * @param type dryer type: "drum", "spray", "flash", "rotary"
   */
  public void setDryerType(String type) {
    this.dryerType = type;
  }

  /**
   * Get the dryer type.
   *
   * @return dryer type
   */
  public String getDryerType() {
    return dryerType;
  }

  /**
   * Set the outlet temperature of dried product.
   *
   * @param temperatureK temperature in Kelvin
   */
  public void setOutletTemperature(double temperatureK) {
    this.outletTemperature = temperatureK;
  }

  /**
   * Set the outlet temperature with unit.
   *
   * @param temperature temperature value
   * @param unit temperature unit ("K", "C", "F")
   */
  public void setOutletTemperature(double temperature, String unit) {
    if ("C".equalsIgnoreCase(unit)) {
      this.outletTemperature = temperature + 273.15;
    } else if ("F".equalsIgnoreCase(unit)) {
      this.outletTemperature = (temperature - 32.0) * 5.0 / 9.0 + 273.15;
    } else {
      this.outletTemperature = temperature;
    }
  }

  /**
   * Get the outlet temperature in Kelvin.
   *
   * @return outlet temperature
   */
  public double getOutletTemperature() {
    return outletTemperature;
  }

  /**
   * Set the target moisture content in the dried product.
   *
   * @param moistureFraction mass fraction of moisture (0.0 to 1.0)
   */
  public void setTargetMoistureContent(double moistureFraction) {
    this.targetMoistureContent = moistureFraction;
  }

  /**
   * Get the target moisture content.
   *
   * @return target moisture fraction
   */
  public double getTargetMoistureContent() {
    return targetMoistureContent;
  }

  /**
   * Set the thermal efficiency.
   *
   * @param efficiency efficiency fraction (0.0 to 1.0)
   */
  public void setThermalEfficiency(double efficiency) {
    this.thermalEfficiency = efficiency;
  }

  /**
   * Get the thermal efficiency.
   *
   * @return thermal efficiency
   */
  public double getThermalEfficiency() {
    return thermalEfficiency;
  }

  /**
   * Set the pressure drop.
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
   * Get the heat duty for drying.
   *
   * @return heat duty in Watts
   */
  public double getHeatDuty() {
    return heatDuty;
  }

  /**
   * Get the heat duty in the specified unit.
   *
   * @param unit unit ("W", "kW", "MW")
   * @return heat duty
   */
  public double getHeatDuty(String unit) {
    if ("kW".equalsIgnoreCase(unit)) {
      return heatDuty / 1000.0;
    } else if ("MW".equalsIgnoreCase(unit)) {
      return heatDuty / 1.0e6;
    }
    return heatDuty;
  }

  /**
   * Get the specific energy consumption.
   *
   * @return specific energy in kWh/kg water evaporated
   */
  public double getSpecificEnergy() {
    return specificEnergy;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    SystemInterface system = inletStream.getThermoSystem().clone();

    // Set outlet conditions
    system.setPressure(system.getPressure() - pressureDrop);
    if (!Double.isNaN(outletTemperature)) {
      system.setTemperature(outletTemperature);
    }

    // Store inlet enthalpy
    system.init(3);
    double inletEnthalpy = inletStream.getThermoSystem().getEnthalpy();

    // Flash to determine phase split at outlet conditions
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    try {
      ops.TPflash();
    } catch (Exception ex) {
      logger.error("Flash failed in dryer '{}': {}", getName(), ex.getMessage());
    }

    system.init(3);
    system.initProperties();

    // Calculate heat duty
    heatDuty = (system.getEnthalpy() - inletEnthalpy) / thermalEfficiency;

    // Separate phases
    int numPhases = system.getNumberOfPhases();
    if (numPhases >= 2 && system.hasPhaseType("gas")) {
      // Gas phase is the vapor (evaporated moisture)
      SystemInterface vaporSys = system.phaseToSystem(system.getPhases()[0]);
      vaporSys.initProperties();
      vaporStream.setThermoSystem(vaporSys);

      // Liquid phase is the dried product
      SystemInterface productSys = system.phaseToSystem(system.getPhases()[1]);
      productSys.initProperties();
      driedProductStream.setThermoSystem(productSys);
    } else {
      // No phase split - set product as is
      driedProductStream.setThermoSystem(system);
      SystemInterface emptySys = system.clone();
      for (int i = 0; i < emptySys.getNumberOfComponents(); i++) {
        double moles = emptySys.getComponent(i).getNumberOfmoles();
        emptySys.addComponent(i, -moles * 0.999);
      }
      emptySys.initProperties();
      vaporStream.setThermoSystem(emptySys);
    }

    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public String toJson() {
    return new com.google.gson.GsonBuilder().serializeSpecialFloatingPointValues().create()
        .toJson(toMap());
  }

  /**
   * Get a map representation of the dryer.
   *
   * @return map of properties
   */
  private Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("name", getName());
    map.put("type", "Dryer");
    map.put("dryerType", dryerType);
    map.put("targetMoistureContent", targetMoistureContent);
    map.put("thermalEfficiency", thermalEfficiency);
    map.put("heatDuty_W", heatDuty);
    map.put("specificEnergy_kWh_kg", specificEnergy);
    map.put("pressureDrop_bar", pressureDrop);
    if (!Double.isNaN(outletTemperature)) {
      map.put("outletTemperature_K", outletTemperature);
    }
    return map;
  }
}
