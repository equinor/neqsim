package neqsim.process.equipment.reactor.sulfurrecovery;

import java.util.UUID;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.util.sulfur.SulfurThermodynamics;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/** Internal material-balance and stream helpers for sulfur-recovery equipment. */
final class SulfurProcessUtil {
  /** Sulfur atomic molar mass used by the NeqSim component database [kg/mol]. */
  static final double SULFUR_ATOMIC_MOLAR_MASS_KG_PER_MOL = 0.03206;

  /** S8 molar mass used by the NeqSim component database [kg/mol]. */
  static final double S8_MOLAR_MASS_KG_PER_MOL = 0.25648;

  /** Components required by the core sulfur-recovery models. */
  static final String[] CORE_COMPONENTS = {"H2S", "SO2", "S8", "COS", "CS2", "water",
      "oxygen", "nitrogen", "hydrogen", "CO", "CO2", "methane", "ammonia"};

  /** Utility class. */
  private SulfurProcessUtil() {}

  /** Clone a system and ensure all SRU species are present. */
  static SystemInterface prepareSystem(SystemInterface source) {
    SystemInterface system = source.clone();
    boolean added = false;
    for (String component : CORE_COMPONENTS) {
      if (!system.hasComponent(component)) {
        system.addComponent(component, 1.0e-30);
        added = true;
      }
    }
    if (added) {
      system.createDatabase(true);
    }
    system.init(0);
    return system;
  }

  /** Get overall component moles on the stream flow basis. */
  static double moles(SystemInterface system, String component) {
    if (!system.hasComponent(component)) {
      return 0.0;
    }
    return Math.max(0.0, system.getComponent(component).getNumberOfmoles());
  }

  /** Set overall component moles without allowing negative values. */
  static void setMoles(SystemInterface system, String component, double targetMoles) {
    if (!system.hasComponent(component)) {
      throw new IllegalArgumentException("Component is not present in SRU system: " + component);
    }
    double boundedTarget = Math.max(targetMoles, 1.0e-30);
    system.addComponent(component, boundedTarget - moles(system, component));
  }

  /** Add a component mole increment while preserving non-negativity. */
  static void addMoles(SystemInterface system, String component, double deltaMoles) {
    setMoles(system, component, moles(system, component) + deltaMoles);
  }

  /** Run a TP flash and initialize all properties. */
  static void flash(SystemInterface system, String equipmentName) {
    try {
      ThermodynamicOperations operations = new ThermodynamicOperations(system);
      operations.TPflash();
      system.initProperties();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Thermodynamic flash failed in sulfur-recovery equipment '" + equipmentName + "'",
          ex);
    }
  }

  /** Put a calculated system on an outlet stream and run it. */
  static void updateOutlet(StreamInterface outlet, SystemInterface system, UUID id) {
    outlet.setThermoSystem(system);
    outlet.run(id);
  }

  /** Create a stream containing only one component from a reference system. */
  static StreamInterface createSingleComponentStream(String name, SystemInterface reference,
      String component, double componentMoles, double temperatureK, double pressureBar, UUID id) {
    SystemInterface product = prepareSystem(reference);
    for (int i = 0; i < product.getNumberOfComponents(); i++) {
      setMoles(product, product.getComponent(i).getComponentName(), 1.0e-30);
    }
    setMoles(product, component, Math.max(componentMoles, 1.0e-30));
    product.setTemperature(temperatureK);
    product.setPressure(Math.max(pressureBar, 0.1));
    flash(product, name);
    Stream stream = new Stream(name, product);
    stream.run(id);
    return stream;
  }

  /** Create a scaled clone preserving temperature, pressure, and composition ratios. */
  static SystemInterface scaledClone(SystemInterface source, double scale) {
    SystemInterface scaled = prepareSystem(source);
    double boundedScale = Math.max(scale, 0.0);
    for (int i = 0; i < scaled.getNumberOfComponents(); i++) {
      String component = scaled.getComponent(i).getComponentName();
      setMoles(scaled, component, moles(scaled, component) * boundedScale);
    }
    scaled.init(0);
    return scaled;
  }

  /** Add all component moles from one compatible system into another. */
  static void addSystem(SystemInterface target, SystemInterface addition) {
    for (int i = 0; i < addition.getNumberOfComponents(); i++) {
      String component = addition.getComponent(i).getComponentName();
      if (target.hasComponent(component)) {
        addMoles(target, component, moles(addition, component));
      }
    }
    target.init(0);
  }

  /** Create and run a stream at a specified temperature. */
  static StreamInterface createConditionedStream(String name, StreamInterface source,
      double temperatureK, UUID id) {
    SystemInterface conditioned = prepareSystem(source.getThermoSystem());
    conditioned.setTemperature(temperatureK);
    flash(conditioned, name);
    Stream stream = new Stream(name, conditioned);
    stream.run(id);
    return stream;
  }

  /** Calculate sulfur atom moles represented by all supported sulfur species. */
  static double sulfurAtomMoles(SystemInterface system) {
    return moles(system, "H2S") + moles(system, "SO2") + moles(system, "COS")
        + 2.0 * moles(system, "CS2") + 8.0 * moles(system, "S8");
  }

  /** Calculate the H2S/SO2 molar ratio, returning infinity when SO2 is absent. */
  static double h2sToSo2Ratio(SystemInterface system) {
    double so2 = moles(system, "SO2");
    if (so2 <= 1.0e-20) {
      return Double.POSITIVE_INFINITY;
    }
    return moles(system, "H2S") / so2;
  }

  /**
   * Convert an S8-equivalent atom inventory to mixed-allotrope molecular partial pressure.
   *
   * @param temperatureK gas temperature [K]
   * @param totalPressureBar total process pressure [bar]
   * @param nonSulfurMoles moles of all non-elemental-sulfur species
   * @param s8EquivalentMoles elemental-sulfur atom inventory divided by eight
   * @return total molecular partial pressure of S2-S8 [bar]
   */
  static double calculateElementalSulfurVapourPressureBar(double temperatureK,
      double totalPressureBar, double nonSulfurMoles, double s8EquivalentMoles) {
    if (s8EquivalentMoles <= 1.0e-30) {
      return 1.0e-12;
    }
    double pressure = Math.max(totalPressureBar, 1.0e-12);
    double sulfurPressure = pressure * s8EquivalentMoles
        / Math.max(nonSulfurMoles + s8EquivalentMoles, 1.0e-30);
    for (int iteration = 0; iteration < 60; iteration++) {
      double meanAtoms = SulfurThermodynamics.calculateMeanSulfurAtomsPerMolecule(
          temperatureK, Math.max(sulfurPressure, 1.0e-12));
      double sulfurMolecules = 8.0 * s8EquivalentMoles / meanAtoms;
      double updatedPressure = pressure * sulfurMolecules
          / Math.max(nonSulfurMoles + sulfurMolecules, 1.0e-30);
      if (Math.abs(updatedPressure - sulfurPressure) <= 1.0e-12 * pressure) {
        sulfurPressure = updatedPressure;
        break;
      }
      sulfurPressure = 0.5 * (sulfurPressure + updatedPressure);
    }
    return SulfurProcessUtil.clamp(sulfurPressure, 1.0e-12, 0.999999 * pressure);
  }

  /** Calculate sulfur dew point from an S8-equivalent atom inventory. */
  static double calculateSulfurDewPointK(double totalPressureBar, double nonSulfurMoles,
      double s8EquivalentMoles) {
    double lowerTemperature = 312.15;
    double upperTemperature = 1308.15;
    if (s8EquivalentMoles <= 1.0e-30) {
      return lowerTemperature;
    }
    double lowerResidual = calculateElementalSulfurVapourPressureBar(lowerTemperature,
        totalPressureBar, nonSulfurMoles, s8EquivalentMoles)
        - SulfurThermodynamics.calculateVapourPressureBar(lowerTemperature);
    if (lowerResidual <= 0.0) {
      return lowerTemperature;
    }
    double upperResidual = calculateElementalSulfurVapourPressureBar(upperTemperature,
        totalPressureBar, nonSulfurMoles, s8EquivalentMoles)
        - SulfurThermodynamics.calculateVapourPressureBar(upperTemperature);
    if (upperResidual >= 0.0) {
      return upperTemperature;
    }
    for (int iteration = 0; iteration < 100; iteration++) {
      double trialTemperature = 0.5 * (lowerTemperature + upperTemperature);
      double residual = calculateElementalSulfurVapourPressureBar(trialTemperature,
          totalPressureBar, nonSulfurMoles, s8EquivalentMoles)
          - SulfurThermodynamics.calculateVapourPressureBar(trialTemperature);
      if (residual > 0.0) {
        lowerTemperature = trialTemperature;
      } else {
        upperTemperature = trialTemperature;
      }
    }
    return 0.5 * (lowerTemperature + upperTemperature);
  }

  /** Clamp a value to a closed interval. */
  static double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
