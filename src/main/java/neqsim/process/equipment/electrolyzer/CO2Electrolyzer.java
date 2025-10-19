package neqsim.process.equipment.electrolyzer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.process.equipment.ProcessEquipmentBaseClass;
import neqsim.process.equipment.reactor.GibbsReactor;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.Fluid;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;

/**
 * Electrochemical reactor converting CO₂ rich feeds to value added products.
 */
public class CO2Electrolyzer extends ProcessEquipmentBaseClass {
  private static final long serialVersionUID = 1000L;
  private static final Logger logger = LogManager.getLogger(CO2Electrolyzer.class);
  private static final double FARADAY_CONSTANT = 96485.3329; // C/mol e-

  private StreamInterface inletStream;
  private Stream gasProductStream;
  private Stream liquidProductStream;
  private GibbsReactor gibbsReactor;

  private final Map<String, Double> gasSelectivities = new LinkedHashMap<>();
  private final Map<String, Double> liquidSelectivities = new LinkedHashMap<>();
  private final Map<String, Double> faradaicEfficiencies = new HashMap<>();
  private final Map<String, Double> electronsPerProduct = new HashMap<>();

  private String co2ComponentName = "CO2";
  private double co2Conversion = 0.0;
  private double cellVoltage = 2.7; // Volt
  private double currentEfficiency = 0.95; // Fraction
  private boolean useSelectivityModel = true;

  /**
   * Create an empty CO2Electrolyzer.
   *
   * @param name unit name
   */
  public CO2Electrolyzer(String name) {
    super(name);
    initializeDefaultElectronNumbers();
  }

  /**
   * Create a CO2Electrolyzer tied to an inlet stream.
   *
   * @param name unit name
   * @param inlet inlet stream
   */
  public CO2Electrolyzer(String name, StreamInterface inlet) {
    this(name);
    setInletStream(inlet);
  }

  private void initializeDefaultElectronNumbers() {
    electronsPerProduct.put("CO", 2.0);
    electronsPerProduct.put("carbon monoxide", 2.0);
    electronsPerProduct.put("formic acid", 2.0);
    electronsPerProduct.put("formate", 2.0);
    electronsPerProduct.put("methanol", 6.0);
    electronsPerProduct.put("ethanol", 12.0);
    electronsPerProduct.put("propane", 20.0);
    electronsPerProduct.put("methane", 8.0);
    electronsPerProduct.put("ethylene", 12.0);
    electronsPerProduct.put("hydrogen", 2.0);
    electronsPerProduct.put("H2", 2.0);
  }

  /**
   * Attach the inlet stream and create product streams.
   *
   * @param inlet inlet stream
   */
  public final void setInletStream(StreamInterface inlet) {
    this.inletStream = inlet;
    double temperature = inlet.getTemperature("K");
    double pressure = inlet.getPressure("bara");

    gasProductStream = new Stream(getName() + " gas product",
        createSystem(new LinkedHashMap<>(), temperature, pressure));
    liquidProductStream = new Stream(getName() + " liquid product",
        createSystem(new LinkedHashMap<>(), temperature, pressure));
  }

  /**
   * Gas product stream.
   *
   * @return gas product stream
   */
  public StreamInterface getGasProductStream() {
    return gasProductStream;
  }

  /**
   * Liquid product stream.
   *
   * @return liquid product stream
   */
  public StreamInterface getLiquidProductStream() {
    return liquidProductStream;
  }

  /**
   * Toggle between analytical selectivity split or equilibrium Gibbs reactor.
   *
   * @param useSelectivity true for analytical selectivity calculations
   */
  public void setUseSelectivityModel(boolean useSelectivity) {
    this.useSelectivityModel = useSelectivity;
  }

  /**
   * Selectivity for gas products (moles per mole converted CO₂).
   *
   * @param component component name
   * @param selectivity selectivity value
   */
  public void setGasProductSelectivity(String component, double selectivity) {
    gasSelectivities.put(component, selectivity);
  }

  /**
   * Selectivity for liquid products (moles per mole converted CO₂).
   *
   * @param component component name
   * @param selectivity selectivity value
   */
  public void setLiquidProductSelectivity(String component, double selectivity) {
    liquidSelectivities.put(component, selectivity);
  }

  /**
   * Faradaic efficiency for a product (fraction between 0 and 1).
   *
   * @param component component name
   * @param efficiency faradaic efficiency
   */
  public void setProductFaradaicEfficiency(String component, double efficiency) {
    faradaicEfficiencies.put(component, efficiency);
  }

  /**
   * Electron requirement per mole of product.
   *
   * @param component component name
   * @param electrons electrons per mole
   */
  public void setElectronsPerMoleProduct(String component, double electrons) {
    electronsPerProduct.put(component, electrons);
  }

  /**
   * Overall CO₂ conversion fraction.
   *
   * @param conversion conversion (0-1)
   */
  public void setCO2Conversion(double conversion) {
    this.co2Conversion = conversion;
  }

  /**
   * Set the cell voltage used for duty estimation.
   *
   * @param voltage cell voltage in V
   */
  public void setCellVoltage(double voltage) {
    this.cellVoltage = voltage;
  }

  /**
   * Set the current efficiency (fraction).
   *
   * @param efficiency current efficiency
   */
  public void setCurrentEfficiency(double efficiency) {
    this.currentEfficiency = efficiency;
  }

  /**
   * Set the CO₂ component name.
   *
   * @param componentName component name
   */
  public void setCo2ComponentName(String componentName) {
    this.co2ComponentName = componentName;
  }

  private SystemInterface createSystem(Map<String, Double> componentMoles, double temperature,
      double pressure) {
    SystemInterface system = inletStream != null ? inletStream.getThermoSystem().clone()
        : new Fluid().create2(new String[] {"CO2"}, new double[] {1e-12}, "mole/sec");
    system.setEmptyFluid();
    double totalMoles = 0.0;
    for (Map.Entry<String, Double> entry : componentMoles.entrySet()) {
      double amount = Math.max(entry.getValue(), 0.0);
      try {
        system.addComponent(entry.getKey(), amount, "mole/sec");
      } catch (Exception ex) {
        logger.warn("Unable to add component '{}' to product stream: {}", entry.getKey(),
            ex.getMessage());
      }
      totalMoles += amount;
    }
    system.setTemperature(temperature);
    system.setPressure(pressure);
    if (totalMoles > 0.0) {
      system.init(0);
    }
    return system;
  }

  private double distributeBySelectivity(Map<String, Double> gasMoles, Map<String, Double> liquidMoles) {
    SystemInterface inlet = inletStream.getThermoSystem();
    double co2Flow = getInletComponentFlow(co2ComponentName);

    double convertedCo2 = Math.max(0.0, co2Flow * co2Conversion);
    double unreactedCo2 = Math.max(0.0, co2Flow - convertedCo2);
    if (unreactedCo2 > 0) {
      gasMoles.merge(co2ComponentName, unreactedCo2, Double::sum);
    }

    for (String name : inlet.getComponentNames()) {
      if (name.equalsIgnoreCase(co2ComponentName)) {
        continue;
      }
      ComponentInterface comp = inlet.getComponent(name);
      if (comp == null) {
        continue;
      }
      double flow = comp.getFlowRate("mole/sec");
      if (flow <= 0.0) {
        continue;
      }
      if ("water".equalsIgnoreCase(name)) {
        liquidMoles.merge(name, flow, Double::sum);
      } else {
        gasMoles.merge(name, flow, Double::sum);
      }
    }

    double electronMoles = 0.0;
    for (Map.Entry<String, Double> entry : gasSelectivities.entrySet()) {
      double moles = convertedCo2 * entry.getValue();
      if (moles <= 0.0) {
        continue;
      }
      gasMoles.merge(entry.getKey(), moles, Double::sum);
      electronMoles += calculateElectronUsage(entry.getKey(), moles);
    }
    for (Map.Entry<String, Double> entry : liquidSelectivities.entrySet()) {
      double moles = convertedCo2 * entry.getValue();
      if (moles <= 0.0) {
        continue;
      }
      liquidMoles.merge(entry.getKey(), moles, Double::sum);
      electronMoles += calculateElectronUsage(entry.getKey(), moles);
    }

    return electronMoles;
  }

  private double runGibbsCalculation(Map<String, Double> gasMoles, Map<String, Double> liquidMoles,
      UUID id) {
    SystemInterface reactionFeed = inletStream.getThermoSystem().clone();
    ensureSpeciesPresent(reactionFeed);
    reactionFeed.setTemperature(inletStream.getTemperature("K"));
    reactionFeed.setPressure(inletStream.getPressure("bara"));

    Stream internalFeed = new Stream(getName() + " internal feed", reactionFeed);
    Stream internalOut = internalFeed.clone(getName() + " internal outlet");

    if (gibbsReactor == null) {
      gibbsReactor = new GibbsReactor(getName() + " Gibbs reactor");
    }
    gibbsReactor.setInletStream(internalFeed);
    gibbsReactor.setOutletStream(internalOut);
    gibbsReactor.setUseAllDatabaseSpecies(true);
    gibbsReactor.run(id);

    SystemInterface outletSystem = gibbsReactor.getOutletStream().getThermoSystem().clone();
    outletSystem.init(0);

    collectPhaseMoles(outletSystem, PhaseType.GAS, gasMoles);
    collectPhaseMoles(outletSystem, PhaseType.LIQUID, liquidMoles);
    collectPhaseMoles(outletSystem, PhaseType.AQUEOUS, liquidMoles);

    double electronMoles = 0.0;
    for (String product : electronsPerProduct.keySet()) {
      double outletMoles = gasMoles.getOrDefault(product, 0.0)
          + liquidMoles.getOrDefault(product, 0.0);
      double produced = Math.max(0.0, outletMoles - getInletComponentFlow(product));
      if (produced > 0.0) {
        electronMoles += calculateElectronUsage(product, produced);
      }
    }
    return electronMoles;
  }

  private void collectPhaseMoles(SystemInterface system, PhaseType phaseType,
      Map<String, Double> accumulator) {
    if (!system.hasPhaseType(phaseType)) {
      return;
    }
    PhaseInterface phase = system.getPhase(phaseType);
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      ComponentInterface component = phase.getComponent(i);
      double flow = component.getFlowRate("mole/sec");
      if (flow <= 0.0) {
        continue;
      }
      accumulator.merge(component.getComponentName(), flow, Double::sum);
    }
  }

  private void ensureSpeciesPresent(SystemInterface system) {
    Set<String> required = new HashSet<>();
    required.addAll(gasSelectivities.keySet());
    required.addAll(liquidSelectivities.keySet());
    required.addAll(faradaicEfficiencies.keySet());
    required.addAll(electronsPerProduct.keySet());
    for (String component : required) {
      if (component == null || component.isEmpty()) {
        continue;
      }
      if (system.getComponent(component) == null) {
        try {
          system.addComponent(component, 1e-9, "mole/sec");
        } catch (Exception e) {
          logger.warn("Unable to add component '{}' to Gibbs reactor feed: {}", component,
              e.getMessage());
        }
      }
    }
  }

  private double calculateElectronUsage(String component, double productMoles) {
    double electrons = electronsPerProduct.getOrDefault(component, 0.0);
    if (electrons <= 0.0) {
      return 0.0;
    }
    double faradaic = faradaicEfficiencies.getOrDefault(component, 1.0);
    if (faradaic <= 0.0) {
      faradaic = 1.0;
    }
    return productMoles * electrons / faradaic;
  }

  private double getInletComponentFlow(String componentName) {
    if (inletStream == null) {
      return 0.0;
    }
    ComponentInterface component = inletStream.getThermoSystem().getComponent(componentName);
    return component != null ? component.getFlowRate("mole/sec") : 0.0;
  }

  private void updateProductStream(Stream productStream, Map<String, Double> componentMoles,
      double temperature, double pressure, UUID id) {
    SystemInterface system = createSystem(componentMoles, temperature, pressure);
    productStream.setThermoSystem(system);
    productStream.setTemperature(temperature, "K");
    productStream.setPressure(pressure, "bara");
    productStream.run(id);
  }

  private void updateEnergyConsumption(double electronMolesPerSecond) {
    if (electronMolesPerSecond <= 0.0) {
      energyStream.setDuty(0.0);
      setEnergyStream(true);
      return;
    }
    double effectiveElectrons = electronMolesPerSecond / Math.max(currentEfficiency, 1e-6);
    double current = effectiveElectrons * FARADAY_CONSTANT;
    double power = current * cellVoltage;
    energyStream.setDuty(power);
    setEnergyStream(true);
  }

  @Override
  public void run(UUID id) {
    if (inletStream == null) {
      throw new IllegalStateException("Inlet stream must be set before running CO2Electrolyzer");
    }

    double temperature = inletStream.getTemperature("K");
    double pressure = inletStream.getPressure("bara");

    Map<String, Double> gasMoles = new LinkedHashMap<>();
    Map<String, Double> liquidMoles = new LinkedHashMap<>();
    double electronMoles;

    if (useSelectivityModel) {
      electronMoles = distributeBySelectivity(gasMoles, liquidMoles);
    } else {
      electronMoles = runGibbsCalculation(gasMoles, liquidMoles, id);
    }

    updateProductStream(gasProductStream, gasMoles, temperature, pressure, id);
    updateProductStream(liquidProductStream, liquidMoles, temperature, pressure, id);
    updateEnergyConsumption(electronMoles);
  }
}
