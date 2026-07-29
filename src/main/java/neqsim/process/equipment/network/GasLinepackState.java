package neqsim.process.equipment.network;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * EOS-based gas inventory for one network pipe at one period boundary.
 */
public class GasLinepackState implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double GAS_CONSTANT = 8.314462618;
  private static final double STANDARD_TEMPERATURE_K = 288.15;
  private static final double STANDARD_PRESSURE_PA = 1.01325e5;

  private final String edgeName;
  private final double massKg;
  private final double molarInventoryMol;
  private final double standardVolumeSm3;
  private final double pressurePa;
  private final double temperatureK;
  private final double compressibilityFactor;
  private final double pipeVolumeM3;
  private final Map<String, Double> componentMoles;
  private final Map<String, Double> componentMolarMassKgMol;
  private final double massBalanceResidualKg;
  private final double maxComponentBalanceResidualMol;

  private GasLinepackState(String edgeName, double massKg, double molarInventoryMol, double pressurePa,
      double temperatureK, double compressibilityFactor, double pipeVolumeM3, Map<String, Double> componentMoles,
      Map<String, Double> componentMolarMassKgMol, double massBalanceResidualKg,
      double maxComponentBalanceResidualMol) {
    this.edgeName = edgeName;
    this.massKg = massKg;
    this.molarInventoryMol = molarInventoryMol;
    this.standardVolumeSm3 = molarInventoryMol * GAS_CONSTANT * STANDARD_TEMPERATURE_K / STANDARD_PRESSURE_PA;
    this.pressurePa = pressurePa;
    this.temperatureK = temperatureK;
    this.compressibilityFactor = compressibilityFactor;
    this.pipeVolumeM3 = pipeVolumeM3;
    this.componentMoles = new LinkedHashMap<String, Double>(componentMoles);
    this.componentMolarMassKgMol = new LinkedHashMap<String, Double>(componentMolarMassKgMol);
    this.massBalanceResidualKg = massBalanceResidualKg;
    this.maxComponentBalanceResidualMol = maxComponentBalanceResidualMol;
  }

  /**
   * Calculate linepack from a solved network edge.
   *
   * @param network solved network
   * @param edgeName edge name
   * @return EOS-based linepack state
   */
  public static GasLinepackState fromSolvedState(LoopedPipeNetwork network, String edgeName) {
    LoopedPipeNetwork.NetworkPipe edge = network.getPipe(edgeName);
    double volume = Math.PI * edge.getDiameter() * edge.getDiameter() / 4.0 * edge.getLength();
    if (!(volume > 0.0)) {
      throw new IllegalArgumentException("Linepack requires positive pipe volume");
    }
    boolean forward = edge.getFlowRate() >= 0.0;
    String upstreamName = forward ? edge.getFromNode() : edge.getToNode();
    SystemInterface inlet = edge.getInletFluid();
    if (inlet == null) {
      inlet = network.getNodeFluid(upstreamName).clone();
    }
    SystemInterface outlet = edge.getOutletFluid();
    double temperature = outlet == null ? inlet.getTemperature()
        : (inlet.getTemperature() + outlet.getTemperature()) / 2.0;
    double pressure = (network.getNode(edge.getFromNode()).getPressure()
        + network.getNode(edge.getToNode()).getPressure()) / 2.0;
    SystemInterface averageFluid = inlet.clone();
    averageFluid.setPressure(pressure / 1.0e5, "bara");
    averageFluid.setTemperature(temperature, "K");
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(averageFluid);
      operations.TPflash();
      averageFluid.initProperties();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Unable to calculate linepack state for edge '" + edgeName + "': " + ex.getMessage(), ex);
    }
    double z = averageFluid.getZ();
    double moles = pressure * volume / (Math.max(z, 1.0e-12) * GAS_CONSTANT * temperature);
    Map<String, Double> componentMoles = new LinkedHashMap<String, Double>();
    Map<String, Double> molarMasses = new LinkedHashMap<String, Double>();
    double[] composition = averageFluid.getMolarComposition();
    for (int index = 0; index < averageFluid.getNumberOfComponents(); index++) {
      ComponentInterface component = averageFluid.getPhase(0).getComponent(index);
      componentMoles.put(component.getComponentName(), moles * composition[index]);
      molarMasses.put(component.getComponentName(), component.getMolarMass());
    }
    double mass = moles * averageFluid.getMolarMass();
    return new GasLinepackState(edgeName, mass, moles, pressure, temperature, z, volume, componentMoles, molarMasses,
        0.0, 0.0);
  }

  /**
   * Advance the component inventory over one period.
   *
   * @param durationSeconds period duration
   * @param inletKgS inlet mass rate
   * @param outletKgS outlet mass rate
   * @param fuelKgS fuel removed from transported inventory
   * @param lossKgS other loss
   * @param inletFluid inlet composition and molar mass
   * @return closing linepack state
   */
  public GasLinepackState advance(double durationSeconds, double inletKgS, double outletKgS, double fuelKgS,
      double lossKgS, SystemInterface inletFluid) {
    if (inletFluid == null) {
      throw new IllegalArgumentException("An inlet fluid is required for component conservation");
    }
    SystemInterface preparedInlet = inletFluid.clone();
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(preparedInlet);
      operations.TPflash();
      preparedInlet.initProperties();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Unable to initialize inlet composition for linepack edge '" + edgeName + "': " + ex.getMessage(), ex);
    }
    Map<String, Double> closing = new LinkedHashMap<String, Double>(componentMoles);
    Map<String, Double> closingMolarMasses = new LinkedHashMap<String, Double>(componentMolarMassKgMol);
    double inletMolarRate = inletKgS / preparedInlet.getMolarMass();
    double totalWithdrawalKgS = outletKgS + fuelKgS + lossKgS;
    double currentMolarMass = massKg / Math.max(molarInventoryMol, 1.0e-30);
    double withdrawalMolarRate = totalWithdrawalKgS / currentMolarMass;
    double[] inletComposition = preparedInlet.getMolarComposition();

    for (int index = 0; index < preparedInlet.getNumberOfComponents(); index++) {
      ComponentInterface component = preparedInlet.getPhase(0).getComponent(index);
      String componentName = component.getComponentName();
      Double existing = closing.get(componentName);
      double inletComponentMoles = durationSeconds * inletMolarRate * inletComposition[index];
      closing.put(componentName, (existing == null ? 0.0 : existing) + inletComponentMoles);
      closingMolarMasses.put(componentName, component.getMolarMass());
    }

    double maxComponentResidual = 0.0;
    for (Map.Entry<String, Double> entry : componentMoles.entrySet()) {
      double moleFraction = entry.getValue() / Math.max(molarInventoryMol, 1.0e-30);
      double withdrawalMoles = durationSeconds * withdrawalMolarRate * moleFraction;
      double newMoles = closing.get(entry.getKey()) - withdrawalMoles;
      if (newMoles < -1.0e-8) {
        throw new IllegalStateException("Linepack withdrawal exceeds component inventory for " + entry.getKey());
      }
      closing.put(entry.getKey(), Math.max(0.0, newMoles));
      double reconstructed = closing.get(entry.getKey()) - entry.getValue()
          - durationSeconds * inletMolarRate * inletComponentFraction(preparedInlet, entry.getKey()) + withdrawalMoles;
      maxComponentResidual = Math.max(maxComponentResidual, Math.abs(reconstructed));
    }

    double closingMoles = 0.0;
    double closingMass = 0.0;
    for (Map.Entry<String, Double> entry : closing.entrySet()) {
      closingMoles += entry.getValue();
      Double molarMass = closingMolarMasses.get(entry.getKey());
      if (molarMass == null) {
        throw new IllegalStateException("Missing molar mass for linepack component " + entry.getKey());
      }
      closingMass += entry.getValue() * molarMass;
    }
    double expectedMass = massKg + durationSeconds * (inletKgS - outletKgS - fuelKgS - lossKgS);
    double massResidual = closingMass - expectedMass;
    double closingPressure = closingMoles * compressibilityFactor * GAS_CONSTANT * temperatureK / pipeVolumeM3;
    return new GasLinepackState(edgeName, closingMass, closingMoles, closingPressure, temperatureK,
        compressibilityFactor, pipeVolumeM3, closing, closingMolarMasses, massResidual, maxComponentResidual);
  }

  private double inletComponentFraction(SystemInterface fluid, String componentName) {
    if (!fluid.hasComponent(componentName)) {
      return 0.0;
    }
    return fluid.getComponent(componentName).getz();
  }

  /** @return edge name */
  public String getEdgeName() {
    return edgeName;
  }

  /** @return inventory mass in kg */
  public double getMassKg() {
    return massKg;
  }

  /** @return inventory amount in mol */
  public double getMolarInventoryMol() {
    return molarInventoryMol;
  }

  /** @return standard volume at 15 C and 1.01325 bara */
  public double getStandardVolumeSm3() {
    return standardVolumeSm3;
  }

  /** @return average absolute pressure in Pa */
  public double getPressurePa() {
    return pressurePa;
  }

  /** @return average temperature in K */
  public double getTemperatureK() {
    return temperatureK;
  }

  /** @return EOS compressibility factor */
  public double getCompressibilityFactor() {
    return compressibilityFactor;
  }

  /** @return pipe internal volume in m3 */
  public double getPipeVolumeM3() {
    return pipeVolumeM3;
  }

  /** @return immutable component inventories in mol */
  public Map<String, Double> getComponentMoles() {
    return Collections.unmodifiableMap(componentMoles);
  }

  /** @return inventory equation residual in kg */
  public double getMassBalanceResidualKg() {
    return massBalanceResidualKg;
  }

  /** @return largest component inventory residual in mol */
  public double getMaxComponentBalanceResidualMol() {
    return maxComponentBalanceResidualMol;
  }
}
