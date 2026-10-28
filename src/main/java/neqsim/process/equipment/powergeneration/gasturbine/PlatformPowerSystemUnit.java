package neqsim.process.equipment.powergeneration.gasturbine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import neqsim.process.automation.AutomationAddressable;
import neqsim.process.automation.SimulationVariable;
import neqsim.process.automation.SimulationVariable.VariableType;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.stream.EnergyBus;
import neqsim.process.equipment.stream.EnergyPort;
import neqsim.process.equipment.stream.EnergyPortDirection;
import neqsim.process.equipment.stream.EnergyPortMode;
import neqsim.process.equipment.stream.EnergyType;
import neqsim.util.validation.ValidationResult;

/**
 * Process unit that puts a {@link PlatformPowerSystem} into a flowsheet, on an electrical {@link EnergyBus}, and into
 * {@link neqsim.process.automation.ProcessAutomation}. It is the adapter that lets
 * {@link neqsim.process.equipment.energy.CoupledProcessEnergySolver} iterate a running process model together with the
 * site-rated gas-turbine generators, shore power and bottoming cycle.
 *
 * <p>
 * The unit offers the capacity of the power system at the ambient temperature to the bus as a <em>balancing</em>
 * generator ({@link EnergyPortMode#BALANCE}): the bus then asks for the generation that the consumers on the bus
 * request, up to the capacity, and reports unmet demand as a shortage. After the bus is solved the unit evaluates the
 * power system at the allocated generation ({@link PlatformPowerSystem#evaluate}) to give fuel, CO2, N-1 margin and the
 * heat balance. Without a bus the unit uses a specified demand ({@link #setElectricDemand(double, String)}).
 * </p>
 *
 * <pre>{@code
 * EnergyBus bus = new EnergyBus("Platform A 66 kV", EnergyType.ELECTRICAL);
 * PlatformPowerSystemUnit power = new PlatformPowerSystemUnit("Platform A power", platformPower);
 * power.connectToBus(bus);
 * // consumers (motors, drive trains) and an EnergyNetworkSolver on the same bus are added to the ProcessSystem
 * power.setAmbientTemperature(25.0, "C");
 * new CoupledProcessEnergySolver(process).solve();
 * PlatformPowerSystem.Result r = power.getLastResult();
 * }</pre>
 *
 * <h2>Automation addresses</h2>
 *
 * <table>
 * <caption>Power-system automation addresses</caption>
 * <tr>
 * <th>Address (after the unit name)</th>
 * <th>Type</th>
 * <th>Unit</th>
 * </tr>
 * <tr>
 * <td>ambientTemperature, electricDemand, heatDemand</td>
 * <td>input</td>
 * <td>C, MW, MW (the electric demand is used only when the unit is not on a bus)</td>
 * </tr>
 * <tr>
 * <td>availablePower (capacity at the ambient temperature, no run needed), demand, deficit, n1Margin, shorePower,
 * gasTurbinePower, fuel, co2, whruDuty, heatShortfall, utilisation, capacityLimited</td>
 * <td>output</td>
 * <td>MW, kg/hr for fuel and co2, fraction or flag otherwise</td>
 * </tr>
 * <tr>
 * <td>generator.NAME.availablePower, generator.NAME.loadFraction</td>
 * <td>output</td>
 * <td>MW, fraction</td>
 * </tr>
 * </table>
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class PlatformPowerSystemUnit extends ProcessEquipmentBaseClass implements AutomationAddressable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  /** Name of the electrical balancing port. */
  public static final String SUPPLY_PORT = "electricalSupply";

  private final PlatformPowerSystem powerSystem;
  private double ambientTemperatureK = 288.15;
  private double electricDemandW = 0.0;
  private double heatDemandW = 0.0;
  private PlatformPowerSystem.Result lastResult = null;

  /**
   * Constructor.
   *
   * @param name unit name
   * @param powerSystem the power system to run
   * @throws IllegalArgumentException if the power system is null
   */
  public PlatformPowerSystemUnit(String name, PlatformPowerSystem powerSystem) {
    super(name);
    if (powerSystem == null) {
      throw new IllegalArgumentException("a power system is required");
    }
    this.powerSystem = powerSystem;
    registerEnergyPort(SUPPLY_PORT, EnergyType.ELECTRICAL, EnergyPortDirection.OUTPUT, EnergyPortMode.BALANCE);
  }

  /**
   * Get the wrapped power system.
   *
   * @return the power system
   */
  public PlatformPowerSystem getPowerSystem() {
    return powerSystem;
  }

  /**
   * Connect the supply port to an electrical bus as a balancing generator.
   *
   * @param bus the electrical bus
   */
  public void connectToBus(EnergyBus bus) {
    connectEnergyStream(SUPPLY_PORT, bus, EnergyPortMode.BALANCE);
  }

  /**
   * Set the ambient air temperature of the generators.
   *
   * @param temperature ambient temperature
   * @param unit "C" or "K"
   * @throws IllegalArgumentException if the unit is not C or K
   */
  public void setAmbientTemperature(double temperature, String unit) {
    this.ambientTemperatureK = AutomationAddressable.temperatureToKelvin(temperature, unit);
  }

  /**
   * Get the ambient air temperature.
   *
   * @param unit "C" or "K"
   * @return the ambient temperature
   * @throws IllegalArgumentException if the unit is not C or K
   */
  public double getAmbientTemperature(String unit) {
    return AutomationAddressable.temperatureFromKelvin(ambientTemperatureK, unit);
  }

  /**
   * Set the electric demand used when the unit is not connected to a bus.
   *
   * @param power electric demand
   * @param unit "W", "kW" or "MW"
   * @throws IllegalArgumentException if the power is negative or the unit is not supported
   */
  public void setElectricDemand(double power, String unit) {
    if (!(power >= 0.0)) {
      throw new IllegalArgumentException("electric demand must not be negative");
    }
    this.electricDemandW = AutomationAddressable.powerToWatts(power, unit);
  }

  /**
   * Set the process heat demand that the waste-heat recovery should cover.
   *
   * @param power heat demand
   * @param unit "W", "kW" or "MW"
   * @throws IllegalArgumentException if the power is negative or the unit is not supported
   */
  public void setHeatDemand(double power, String unit) {
    if (!(power >= 0.0)) {
      throw new IllegalArgumentException("heat demand must not be negative");
    }
    this.heatDemandW = AutomationAddressable.powerToWatts(power, unit);
  }

  /**
   * Capacity of the generators and the import limit at the present ambient temperature.
   *
   * @return capacity at the bus [W]
   */
  public double getAvailablePowerW() {
    return powerSystem.getGeneratorCapacityW(ambientTemperatureK) + powerSystem.getPowerFromShoreLimitW();
  }

  /**
   * Get the result of the latest run.
   *
   * @return the dispatch result, or null before the first run
   */
  public PlatformPowerSystem.Result getLastResult() {
    return lastResult;
  }

  /** {@inheritDoc} */
  @Override
  public void run(UUID id) {
    EnergyPort port = getEnergyPort(SUPPLY_PORT);
    double demand = electricDemandW;
    if (port.isConnected() && port.getEnergyStream() instanceof EnergyBus) {
      EnergyBus bus = (EnergyBus) port.getEnergyStream();
      port.setBalanceLimits(getAvailablePowerW(), 0.0);
      if (!bus.hasSolution()) {
        bus.solveBalance();
      }
      demand = Math.max(0.0, port.getDuty());
    }
    lastResult = powerSystem.evaluate(ambientTemperatureK, demand, heatDemandW);
    setCalculationIdentifier(id);
  }

  /** {@inheritDoc} */
  @Override
  public ValidationResult validateSetup() {
    ValidationResult result = new ValidationResult(getName());
    if (powerSystem.getGenerators().isEmpty() && powerSystem.getPowerFromShoreLimitW() <= 0.0) {
      result.addError("power", "The power system has no generator and no import limit",
          "Add a generator with PlatformPowerSystem.addGenerator or set a shore limit");
    }
    if (!(getEnergyPort(SUPPLY_PORT).getEnergyStream() instanceof EnergyBus)) {
      result.addWarning("energy", "The supply port is not connected to an energy bus",
          "Call connectToBus(bus), or set the electric demand with setElectricDemand");
    }
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public List<SimulationVariable> getAutomationVariables(String unitAddress) {
    List<SimulationVariable> vars = new ArrayList<SimulationVariable>();
    String p = unitAddress + ".";
    vars.add(new SimulationVariable(p + "ambientTemperature", "ambientTemperature", VariableType.INPUT, "C",
        "Ambient air temperature of the generators").withBounds(Double.valueOf(-80.0), Double.valueOf(80.0))
        .withUnitFamily("temperature"));
    vars.add(new SimulationVariable(p + "electricDemand", "electricDemand", VariableType.INPUT, "MW",
        "Electric demand when the unit is not on a bus").withBounds(Double.valueOf(0.0), null).withUnitFamily("power"));
    vars.add(new SimulationVariable(p + "heatDemand", "heatDemand", VariableType.INPUT, "MW",
        "Process heat demand for the waste-heat recovery").withBounds(Double.valueOf(0.0), null)
        .withUnitFamily("power"));
    String[] powers = {"availablePower", "demand", "deficit", "n1Margin", "shorePower", "gasTurbinePower", "whruDuty",
        "heatShortfall"};
    for (String o : powers) {
      vars.add(
          new SimulationVariable(p + o, o, VariableType.OUTPUT, "MW", "Power system " + o).withUnitFamily("power"));
    }
    vars.add(new SimulationVariable(p + "fuel", "fuel", VariableType.OUTPUT, "kg/hr", "Generator fuel flow"));
    vars.add(new SimulationVariable(p + "co2", "co2", VariableType.OUTPUT, "kg/hr", "Generator CO2 emission"));
    vars.add(new SimulationVariable(p + "utilisation", "utilisation", VariableType.OUTPUT, "-",
        "Demand over generator plus import capacity"));
    vars.add(new SimulationVariable(p + "capacityLimited", "capacityLimited", VariableType.OUTPUT, "-",
        "1 when the demand cannot be covered"));
    for (GasTurbineUnit g : powerSystem.getGenerators()) {
      String m = p + "generator." + g.getName() + ".";
      vars.add(new SimulationVariable(m + "availablePower", "generator." + g.getName() + ".availablePower",
          VariableType.OUTPUT, "MW", "Site-rated shaft power at the ambient temperature").withUnitFamily("power"));
      vars.add(new SimulationVariable(m + "loadFraction", "generator." + g.getName() + ".loadFraction",
          VariableType.OUTPUT, "-", "Load fraction of the site rating"));
    }
    return vars;
  }

  /** {@inheritDoc} */
  @Override
  public double getAutomationValue(String property, String unitOfMeasure) {
    String mw = unitOfMeasure == null || unitOfMeasure.trim().isEmpty() ? "MW" : unitOfMeasure;
    switch (property) {
    case "ambientTemperature":
      return AutomationAddressable.temperatureFromKelvin(ambientTemperatureK, unitOfMeasure);
    case "electricDemand":
      return AutomationAddressable.powerFromWatts(electricDemandW, mw);
    case "heatDemand":
      return AutomationAddressable.powerFromWatts(heatDemandW, mw);
    case "availablePower":
      return AutomationAddressable.powerFromWatts(getAvailablePowerW(), mw);
    default:
      break;
    }
    if (property.startsWith("generator.")) {
      return getGeneratorValue(property, mw);
    }
    if (lastResult == null) {
      return Double.NaN;
    }
    switch (property) {
    case "demand":
      return AutomationAddressable.powerFromWatts(lastResult.getDemandW(), mw);
    case "deficit":
      return AutomationAddressable.powerFromWatts(lastResult.getDeficitW(), mw);
    case "n1Margin":
      return AutomationAddressable.powerFromWatts(lastResult.getN1MarginW(), mw);
    case "shorePower":
      return AutomationAddressable.powerFromWatts(lastResult.getShorePowerW(), mw);
    case "gasTurbinePower":
      return AutomationAddressable.powerFromWatts(lastResult.getGasTurbineElectricW(), mw);
    case "whruDuty":
      return AutomationAddressable.powerFromWatts(lastResult.getWhruDutyW(), mw);
    case "heatShortfall":
      return AutomationAddressable.powerFromWatts(lastResult.getHeatShortfallW(), mw);
    case "fuel":
      return lastResult.getFuelKgPerHr();
    case "co2":
      return lastResult.getCo2KgPerHr();
    case "utilisation":
      return lastResult.getUtilisation();
    case "capacityLimited":
      return lastResult.isCapacityLimited() ? 1.0 : 0.0;
    default:
      throw new IllegalArgumentException("Unknown property '" + property + "' for power system " + getName());
    }
  }

  /**
   * Read a per-generator value.
   *
   * @param property property of the form {@code generator.NAME.quantity}
   * @param mw power unit
   * @return the value, NaN before the first run for the load fraction
   * @throws IllegalArgumentException if the generator or quantity is unknown
   */
  private double getGeneratorValue(String property, String mw) {
    int last = property.lastIndexOf('.');
    String genName = property.substring("generator.".length(), last);
    String quantity = property.substring(last + 1);
    List<GasTurbineUnit> gens = powerSystem.getGenerators();
    for (int i = 0; i < gens.size(); i++) {
      if (gens.get(i).getName().equals(genName)) {
        if ("availablePower".equals(quantity)) {
          return AutomationAddressable.powerFromWatts(gens.get(i).getAvailablePowerAtAmbient(ambientTemperatureK), mw);
        } else if ("loadFraction".equals(quantity)) {
          return lastResult == null ? Double.NaN : lastResult.getUnitLoadFraction(i);
        }
        throw new IllegalArgumentException("Unknown generator quantity '" + quantity + "' for " + getName());
      }
    }
    throw new IllegalArgumentException("'" + genName + "' is not a generator of power system " + getName());
  }

  /** {@inheritDoc} */
  @Override
  public void setAutomationValue(String property, double value, String unitOfMeasure) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("value must be finite");
    }
    String mw = unitOfMeasure == null || unitOfMeasure.trim().isEmpty() ? "MW" : unitOfMeasure;
    switch (property) {
    case "ambientTemperature":
      double kelvin = AutomationAddressable.temperatureToKelvin(value, unitOfMeasure);
      if (kelvin < 193.15 || kelvin > 353.15) {
        throw new IllegalArgumentException("ambient temperature must be between -80 and 80 C");
      }
      ambientTemperatureK = kelvin;
      break;
    case "electricDemand":
      setElectricDemand(value, mw);
      break;
    case "heatDemand":
      setHeatDemand(value, mw);
      break;
    default:
      throw new IllegalArgumentException(
          "Property '" + property + "' of power system " + getName() + " is unknown or read-only");
    }
  }
}
