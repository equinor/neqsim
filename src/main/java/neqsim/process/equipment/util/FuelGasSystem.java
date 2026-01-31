package neqsim.process.equipment.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.gson.GsonBuilder;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.heatexchanger.Heater;
import neqsim.process.equipment.separator.Separator;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.process.equipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

/**
 * Models fuel gas systems for process facilities.
 *
 * <p>
 * Fuel gas systems provide conditioned gas for:
 * </p>
 * <ul>
 * <li><b>Gas turbines</b> - Power generation and compressor drivers</li>
 * <li><b>Fired heaters</b> - Process heating</li>
 * <li><b>Flare pilots</b> - Continuous ignition source</li>
 * <li><b>Hot oil heaters</b> - Heat transfer fluid heating</li>
 * <li><b>Regeneration gas heaters</b> - TEG/mol sieve regeneration</li>
 * </ul>
 *
 * <h2>Design Standards</h2>
 * <ul>
 * <li>API 618 - Reciprocating Compressors for Petroleum Industry</li>
 * <li>NORSOK P-002 - Process System Design</li>
 * <li>ISO 21789 - Gas Turbine Applications</li>
 * </ul>
 *
 * <h2>Typical Fuel Gas Specifications</h2>
 * <table border="1">
 * <caption>Fuel gas quality requirements</caption>
 * <tr>
 * <th>Parameter</th>
 * <th>Gas Turbine</th>
 * <th>Fired Heater</th>
 * </tr>
 * <tr>
 * <td>Pressure</td>
 * <td>20-50 barg</td>
 * <td>2-5 barg</td>
 * </tr>
 * <tr>
 * <td>Superheat</td>
 * <td>20-30°C above dew point</td>
 * <td>10°C above dew point</td>
 * </tr>
 * <tr>
 * <td>Wobbe Index</td>
 * <td>±5% of design</td>
 * <td>±10% of design</td>
 * </tr>
 * <tr>
 * <td>H2S</td>
 * <td>&lt;20 ppmv</td>
 * <td>&lt;100 ppmv</td>
 * </tr>
 * <tr>
 * <td>Liquids</td>
 * <td>None</td>
 * <td>Minimal</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class FuelGasSystem extends ProcessEquipmentBaseClass {

  private static final long serialVersionUID = 1001L;
  private static final Logger logger = LogManager.getLogger(FuelGasSystem.class);

  // ============================================================================
  // Fuel Gas Specifications
  // ============================================================================

  /**
   * Fuel gas consumer type with specific requirements.
   */
  public enum ConsumerType {
    /** Gas turbine - stringent requirements. */
    GAS_TURBINE(30.0, 25.0, 20.0, 0.0, "High pressure, superheated"),
    /** Fired heater - moderate requirements. */
    FIRED_HEATER(3.0, 15.0, 100.0, 0.0, "Low pressure, moderate superheat"),
    /** Flare pilot - minimal requirements. */
    FLARE_PILOT(1.5, 10.0, 500.0, 0.0, "Continuous small flow"),
    /** Hot oil heater - similar to fired heater. */
    HOT_OIL_HEATER(3.0, 15.0, 100.0, 0.0, "Low pressure"),
    /** Regeneration gas - may allow some H2S. */
    REGEN_HEATER(2.0, 10.0, 200.0, 0.0, "Lower quality acceptable"),
    /** Incinerator - least stringent. */
    INCINERATOR(1.0, 5.0, 1000.0, 0.0, "Minimal conditioning");

    private final double typicalPressureBarg;
    private final double minSuperheatC;
    private final double maxH2Sppmv;
    private final double maxLiquidsPpmv;
    private final String description;

    ConsumerType(double pressure, double superheat, double h2s, double liquids, String desc) {
      this.typicalPressureBarg = pressure;
      this.minSuperheatC = superheat;
      this.maxH2Sppmv = h2s;
      this.maxLiquidsPpmv = liquids;
      this.description = desc;
    }

    /**
     * Gets typical pressure.
     *
     * @return pressure [barg]
     */
    public double getTypicalPressureBarg() {
      return typicalPressureBarg;
    }

    /**
     * Gets minimum superheat.
     *
     * @return superheat [°C]
     */
    public double getMinSuperheatC() {
      return minSuperheatC;
    }

    /**
     * Gets max H2S.
     *
     * @return H2S [ppmv]
     */
    public double getMaxH2Sppmv() {
      return maxH2Sppmv;
    }

    /**
     * Gets description.
     *
     * @return description
     */
    public String getDescription() {
      return description;
    }
  }

  // ============================================================================
  // System Components
  // ============================================================================

  /** Inlet stream from fuel gas source. */
  private StreamInterface inletStream;

  /** Knockout drum for liquid removal. */
  private Separator knockoutDrum;

  /** Fuel gas heater for superheating. */
  private Heater fuelGasHeater;

  /** Pressure letdown valve. */
  private ThrottlingValve pressureLetdown;

  /** Conditioned fuel gas outlet stream. */
  private StreamInterface outletStream;

  // ============================================================================
  // Operating Parameters
  // ============================================================================

  /** Inlet pressure [barg]. */
  private double inletPressure = 70.0;

  /** Inlet temperature [°C]. */
  private double inletTemperature = 30.0;

  /** Required outlet pressure [barg]. */
  private double outletPressure = 30.0;

  /** Required outlet temperature [°C]. */
  private double outletTemperature = 50.0;

  /** Total fuel gas demand [kg/hr]. */
  private double totalDemand = 1000.0;

  /** Gas dew point at outlet pressure [°C]. */
  private double dewPointAtOutletPressure = 0.0;

  /** Superheat above dew point [°C]. */
  private double superheatC = 0.0;

  /** Wobbe index of fuel gas [MJ/Sm³]. */
  private double wobbeIndex = 50.0;

  /** Lower heating value [MJ/kg]. */
  private double lowerHeatingValueMJkg = 45.0;

  /** Heater duty required [kW]. */
  private double heaterDutyKW = 0.0;

  /** JT cooling across valve [°C]. */
  private double jtCoolingC = 0.0;

  // ============================================================================
  // Consumer Tracking
  // ============================================================================

  /** List of fuel gas consumers. */
  private final List<FuelGasConsumer> consumers = new ArrayList<>();

  // ============================================================================
  // Constructors
  // ============================================================================

  /**
   * Default constructor.
   *
   * @param name system name
   */
  public FuelGasSystem(String name) {
    super(name);
  }

  /**
   * Constructor with inlet stream.
   *
   * @param name system name
   * @param inletStream fuel gas inlet
   */
  public FuelGasSystem(String name, StreamInterface inletStream) {
    super(name);
    setInletStream(inletStream);
  }

  // ============================================================================
  // Configuration
  // ============================================================================

  /**
   * Sets inlet stream.
   *
   * @param stream inlet stream
   */
  public void setInletStream(StreamInterface stream) {
    this.inletStream = stream;
  }

  /**
   * Gets inlet stream.
   *
   * @return inlet stream
   */
  public StreamInterface getInletStream() {
    return inletStream;
  }

  /**
   * Gets outlet stream.
   *
   * @return conditioned fuel gas stream
   */
  public StreamInterface getOutletStream() {
    return outletStream;
  }

  /**
   * Add a fuel gas consumer.
   *
   * @param consumer fuel gas consumer
   */
  public void addConsumer(FuelGasConsumer consumer) {
    consumers.add(consumer);
    updateTotalDemand();
  }

  /**
   * Add consumer by parameters.
   *
   * @param name consumer name
   * @param type consumer type
   * @param demandKgh fuel demand [kg/hr]
   */
  public void addConsumer(String name, ConsumerType type, double demandKgh) {
    consumers.add(new FuelGasConsumer(name, type, demandKgh));
    updateTotalDemand();
  }

  /**
   * Update total demand from consumers.
   */
  private void updateTotalDemand() {
    if (!consumers.isEmpty()) {
      totalDemand = consumers.stream().mapToDouble(FuelGasConsumer::getDemandKgh).sum();
      // Set outlet pressure to most demanding consumer
      double maxPressure = consumers.stream().mapToDouble(c -> c.getType().getTypicalPressureBarg())
          .max().orElse(outletPressure);
      outletPressure = maxPressure;
    }
  }

  // ============================================================================
  // Run Calculation
  // ============================================================================

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    if (inletStream == null || inletStream.getFluid() == null) {
      logger.warn("No inlet stream defined for fuel gas system");
      setCalculationIdentifier(id);
      return;
    }

    // Get inlet conditions
    SystemInterface fluid = inletStream.getFluid();
    inletPressure = fluid.getPressure("barg");
    inletTemperature = fluid.getTemperature("C");

    // Calculate Wobbe index
    // Wobbe = HHV / sqrt(SG)
    double sg = fluid.getMolarMass("kg/mol") / 0.02896; // Relative to air
    double hhvMJm3 = 38.0; // Approximate for natural gas
    if (sg > 0) {
      wobbeIndex = hhvMJm3 / Math.sqrt(sg);
    }

    // Estimate LHV
    lowerHeatingValueMJkg = 45.0; // Typical natural gas

    // Calculate dew point at outlet pressure (simplified)
    // In practice, use flash calculation
    dewPointAtOutletPressure = -10.0 + outletPressure * 0.5; // Rough estimate

    // Calculate JT cooling for pressure letdown
    // Typical JT coefficient for natural gas: ~0.4-0.6 °C/bar
    double jtCoefficient = 0.5;
    double pressureDrop = inletPressure - outletPressure;
    jtCoolingC = jtCoefficient * pressureDrop;

    // Temperature after letdown (before heater)
    double tempAfterLetdown = inletTemperature - jtCoolingC;

    // Required heater outlet temperature
    double requiredTemp = dewPointAtOutletPressure + 25.0; // 25°C superheat
    outletTemperature = Math.max(requiredTemp, tempAfterLetdown + 5.0);

    // Calculate heater duty
    // Q = m * Cp * dT
    double cpKjKgK = 2.5; // Approximate for natural gas
    double tempRise = outletTemperature - tempAfterLetdown;
    if (tempRise > 0) {
      heaterDutyKW = totalDemand * cpKjKgK * tempRise / 3600.0;
    } else {
      heaterDutyKW = 0.0;
    }

    // Calculate superheat
    superheatC = outletTemperature - dewPointAtOutletPressure;

    // Create outlet stream
    outletStream = new Stream(getName() + "_outlet", fluid.clone());
    outletStream.setTemperature(outletTemperature, "C");
    outletStream.setPressure(outletPressure + 1.01325, "bara");
    outletStream.setFlowRate(totalDemand, "kg/hr");
    outletStream.run();

    setCalculationIdentifier(id);
  }

  // ============================================================================
  // Results
  // ============================================================================

  /**
   * Gets heater duty.
   *
   * @return heater duty [kW]
   */
  public double getHeaterDutyKW() {
    return heaterDutyKW;
  }

  /**
   * Gets JT cooling.
   *
   * @return JT cooling [°C]
   */
  public double getJTCooling() {
    return jtCoolingC;
  }

  /**
   * Gets Wobbe index.
   *
   * @return Wobbe index [MJ/Sm³]
   */
  public double getWobbeIndex() {
    return wobbeIndex;
  }

  /**
   * Gets lower heating value.
   *
   * @return LHV [MJ/kg]
   */
  public double getLowerHeatingValue() {
    return lowerHeatingValueMJkg;
  }

  /**
   * Gets dew point.
   *
   * @return dew point [°C]
   */
  public double getDewPoint() {
    return dewPointAtOutletPressure;
  }

  /**
   * Gets superheat.
   *
   * @return superheat above dew point [°C]
   */
  public double getSuperheat() {
    return superheatC;
  }

  /**
   * Checks if superheat is adequate.
   *
   * @param minSuperheat minimum required superheat [°C]
   * @return true if adequate
   */
  public boolean isSuperheatAdequate(double minSuperheat) {
    return superheatC >= minSuperheat;
  }

  /**
   * Calculate thermal power delivered.
   *
   * @return thermal power [MW]
   */
  public double getThermalPowerMW() {
    return totalDemand * lowerHeatingValueMJkg / 3600.0;
  }

  /**
   * Calculate annual fuel consumption.
   *
   * @param operatingHours annual operating hours
   * @return annual consumption [tonnes]
   */
  public double getAnnualConsumptionTonnes(double operatingHours) {
    return totalDemand * operatingHours / 1000.0;
  }

  /**
   * Gets results as JSON.
   *
   * @return JSON string
   */
  public String toJson() {
    Map<String, Object> results = new LinkedHashMap<>();
    results.put("systemName", getName());

    Map<String, Object> inletConditions = new LinkedHashMap<>();
    inletConditions.put("pressureBarg", inletPressure);
    inletConditions.put("temperatureC", inletTemperature);
    inletConditions.put("flowRateKgh", totalDemand);
    results.put("inlet", inletConditions);

    Map<String, Object> outletConditions = new LinkedHashMap<>();
    outletConditions.put("pressureBarg", outletPressure);
    outletConditions.put("temperatureC", outletTemperature);
    outletConditions.put("dewPointC", dewPointAtOutletPressure);
    outletConditions.put("superheatC", superheatC);
    results.put("outlet", outletConditions);

    Map<String, Object> gasProperties = new LinkedHashMap<>();
    gasProperties.put("wobbeIndexMJSm3", wobbeIndex);
    gasProperties.put("lhvMJkg", lowerHeatingValueMJkg);
    gasProperties.put("thermalPowerMW", getThermalPowerMW());
    results.put("gasProperties", gasProperties);

    Map<String, Object> equipmentDuties = new LinkedHashMap<>();
    equipmentDuties.put("heaterDutyKW", heaterDutyKW);
    equipmentDuties.put("jtCoolingC", jtCoolingC);
    results.put("equipmentDuties", equipmentDuties);

    if (!consumers.isEmpty()) {
      List<Map<String, Object>> consumerList = new ArrayList<>();
      for (FuelGasConsumer c : consumers) {
        Map<String, Object> cmap = new LinkedHashMap<>();
        cmap.put("name", c.getName());
        cmap.put("type", c.getType().name());
        cmap.put("demandKgh", c.getDemandKgh());
        cmap.put("thermalPowerMW", c.getDemandKgh() * lowerHeatingValueMJkg / 3600.0);
        consumerList.add(cmap);
      }
      results.put("consumers", consumerList);
    }

    return new GsonBuilder().setPrettyPrinting().create().toJson(results);
  }

  // ============================================================================
  // Getters and Setters
  // ============================================================================

  /**
   * Gets outlet pressure.
   *
   * @return pressure [barg]
   */
  public double getOutletPressure() {
    return outletPressure;
  }

  /**
   * Sets outlet pressure.
   *
   * @param pressure pressure [barg]
   */
  public void setOutletPressure(double pressure) {
    this.outletPressure = pressure;
  }

  /**
   * Gets outlet temperature.
   *
   * @return temperature [°C]
   */
  public double getOutletTemperature() {
    return outletTemperature;
  }

  /**
   * Sets outlet temperature.
   *
   * @param temperature temperature [°C]
   */
  public void setOutletTemperature(double temperature) {
    this.outletTemperature = temperature;
  }

  /**
   * Gets total fuel demand.
   *
   * @return demand [kg/hr]
   */
  public double getTotalDemand() {
    return totalDemand;
  }

  /**
   * Sets total fuel demand.
   *
   * @param demand demand [kg/hr]
   */
  public void setTotalDemand(double demand) {
    this.totalDemand = demand;
  }

  /**
   * Gets consumers list.
   *
   * @return list of consumers
   */
  public List<FuelGasConsumer> getConsumers() {
    return new ArrayList<>(consumers);
  }

  // ============================================================================
  // Inner Classes
  // ============================================================================

  /**
   * Fuel gas consumer (equipment using fuel gas).
   */
  public static class FuelGasConsumer implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final ConsumerType type;
    private double demandKgh;
    private boolean isRunning;
    private double efficiencyPercent;

    /**
     * Creates a fuel gas consumer.
     *
     * @param name consumer name
     * @param type consumer type
     * @param demandKgh fuel demand [kg/hr]
     */
    public FuelGasConsumer(String name, ConsumerType type, double demandKgh) {
      this.name = name;
      this.type = type;
      this.demandKgh = demandKgh;
      this.isRunning = true;
      this.efficiencyPercent = 85.0;
    }

    /**
     * Gets name.
     *
     * @return name
     */
    public String getName() {
      return name;
    }

    /**
     * Gets type.
     *
     * @return consumer type
     */
    public ConsumerType getType() {
      return type;
    }

    /**
     * Gets demand.
     *
     * @return demand [kg/hr]
     */
    public double getDemandKgh() {
      return isRunning ? demandKgh : 0.0;
    }

    /**
     * Sets demand.
     *
     * @param demand demand [kg/hr]
     */
    public void setDemandKgh(double demand) {
      this.demandKgh = demand;
    }

    /**
     * Checks if running.
     *
     * @return true if running
     */
    public boolean isRunning() {
      return isRunning;
    }

    /**
     * Sets running state.
     *
     * @param running true if running
     */
    public void setRunning(boolean running) {
      this.isRunning = running;
    }

    /**
     * Gets efficiency.
     *
     * @return efficiency [%]
     */
    public double getEfficiencyPercent() {
      return efficiencyPercent;
    }

    /**
     * Sets efficiency.
     *
     * @param efficiency efficiency [%]
     */
    public void setEfficiencyPercent(double efficiency) {
      this.efficiencyPercent = efficiency;
    }

    /**
     * Calculate useful thermal output.
     *
     * @param lhvMJkg lower heating value [MJ/kg]
     * @return useful thermal power [kW]
     */
    public double getUsefulThermalPowerKW(double lhvMJkg) {
      return demandKgh * lhvMJkg * efficiencyPercent / 100.0 / 3.6;
    }
  }
}
