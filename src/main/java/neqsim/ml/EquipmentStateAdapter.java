package neqsim.ml;

import java.io.Serializable;
import java.util.function.Function;
import neqsim.process.equipment.ProcessEquipmentInterface;
import neqsim.process.equipment.separator.SeparatorInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Adapter for extracting StateVectors from process equipment.
 *
 * <p>
 * Provides standardized state extraction from various equipment types without modifying core
 * classes. Supports custom extractors for specialized equipment.
 *
 * @author ESOL
 * @version 1.0
 */
public class EquipmentStateAdapter implements Serializable {
  private static final long serialVersionUID = 1000L;

  private final ProcessEquipmentInterface equipment;
  private Function<ProcessEquipmentInterface, StateVector> customExtractor;

  /**
   * Create adapter for equipment.
   *
   * @param equipment the process equipment
   */
  public EquipmentStateAdapter(ProcessEquipmentInterface equipment) {
    this.equipment = equipment;
  }

  /**
   * Set a custom state extractor.
   *
   * @param extractor function to extract state
   * @return this adapter for chaining
   */
  public EquipmentStateAdapter setCustomExtractor(
      Function<ProcessEquipmentInterface, StateVector> extractor) {
    this.customExtractor = extractor;
    return this;
  }

  /**
   * Extract state vector from equipment.
   *
   * @return current state vector
   */
  public StateVector getStateVector() {
    if (customExtractor != null) {
      return customExtractor.apply(equipment);
    }

    // Use type-specific extraction
    if (equipment instanceof SeparatorInterface) {
      return extractSeparatorState((SeparatorInterface) equipment);
    }

    // Default: extract basic thermodynamic state
    return extractBasicState(equipment);
  }

  /**
   * Extract state from a separator.
   *
   * @param separator the separator
   * @return state vector
   */
  private StateVector extractSeparatorState(SeparatorInterface separator) {
    StateVector state = new StateVector();
    SystemInterface fluid = separator.getThermoSystem();

    if (fluid != null) {
      // Basic thermodynamic state
      state.add("pressure", fluid.getPressure("bar"), 0.0, 200.0, "bar");
      state.add("temperature", fluid.getTemperature("K"), 200.0, 500.0, "K");

      // Phase properties
      if (fluid.hasPhaseType("gas")) {
        state.add("gas_density", fluid.getPhase("gas").getDensity("kg/m3"), 0.0, 300.0, "kg/m3");
        state.add("gas_viscosity", fluid.getPhase("gas").getViscosity("kg/msec") * 1e6, 0.0, 50.0,
            "microPa.s");
        state.add("gas_molar_mass", fluid.getPhase("gas").getMolarMass() * 1000, 0.0, 100.0,
            "g/mol");
      }

      if (fluid.hasPhaseType("oil")) {
        state.add("oil_density", fluid.getPhase("oil").getDensity("kg/m3"), 400.0, 1000.0, "kg/m3");
        state.add("oil_viscosity", fluid.getPhase("oil").getViscosity("kg/msec") * 1e3, 0.0, 1000.0,
            "mPa.s");
        state.add("oil_molar_mass", fluid.getPhase("oil").getMolarMass() * 1000, 0.0, 500.0,
            "g/mol");
      }

      if (fluid.hasPhaseType("aqueous")) {
        state.add("water_density", fluid.getPhase("aqueous").getDensity("kg/m3"), 900.0, 1100.0,
            "kg/m3");
      }

      // Phase fractions
      state.add("gas_fraction", fluid.hasPhaseType("gas") ? fluid.getPhase("gas").getBeta() : 0.0,
          0.0, 1.0, "mole_frac");
      state.add("liquid_fraction",
          fluid.hasPhaseType("oil") ? fluid.getPhase("oil").getBeta() : 0.0, 0.0, 1.0, "mole_frac");
    }

    return state;
  }

  /**
   * Extract basic state from any equipment.
   *
   * @param equip the equipment
   * @return state vector
   */
  private StateVector extractBasicState(ProcessEquipmentInterface equip) {
    StateVector state = new StateVector();

    // Try to get fluid
    SystemInterface fluid = equip.getFluid();
    if (fluid != null) {
      state.add("pressure", fluid.getPressure("bar"), 0.0, 200.0, "bar");
      state.add("temperature", fluid.getTemperature("K"), 200.0, 500.0, "K");

      if (fluid.getNumberOfPhases() > 0) {
        state.add("density", fluid.getDensity("kg/m3"), 0.0, 1500.0, "kg/m3");
        state.add("molar_mass", fluid.getMolarMass() * 1000, 0.0, 500.0, "g/mol");
        state.add("enthalpy", fluid.getEnthalpy("kJ/kg"), -1000.0, 1000.0, "kJ/kg");
      }
    }

    return state;
  }

  /**
   * Create adapter for separator with full state extraction.
   *
   * @param separator the separator
   * @return configured adapter
   */
  public static EquipmentStateAdapter forSeparator(SeparatorInterface separator) {
    return new EquipmentStateAdapter((ProcessEquipmentInterface) separator);
  }

  /**
   * Get the wrapped equipment.
   *
   * @return the equipment
   */
  public ProcessEquipmentInterface getEquipment() {
    return equipment;
  }
}
