package neqsim.process.equipment.reactor;

import java.util.LinkedHashMap;
import java.util.Map;
import neqsim.process.equipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * Utility methods shared by hydrogen-production reactor screening models.
 *
 * <p>
 * The methods in this class keep component handling and simple syngas metrics consistent across
 * SMR, ATR, and POX unit models. They intentionally avoid route-specific assumptions except for
 * common syngas component names and lower-heating-value constants.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
final class HydrogenProductionUtils {
  /** Molar lower heating value of hydrogen in MJ per normal cubic metre. */
  private static final double H2_LHV_MJ_PER_NM3 = 10.78;

  /** Molar lower heating value of carbon monoxide in MJ per normal cubic metre. */
  private static final double CO_LHV_MJ_PER_NM3 = 12.63;

  /** Molar lower heating value of methane in MJ per normal cubic metre. */
  private static final double CH4_LHV_MJ_PER_NM3 = 35.88;

  /** Small positive mole amount used to seed products for Gibbs calculations. */
  private static final double TRACE_MOLES = 1.0e-20;

  /** Common syngas species required by the equilibrium and reporting models. */
  private static final String[] SYNGAS_COMPONENTS =
      {"methane", "water", "oxygen", "hydrogen", "CO", "CO2", "nitrogen"};

  /**
   * Private constructor for utility class.
   */
  private HydrogenProductionUtils() {}

  /**
   * Ensures common syngas components exist in a thermodynamic system.
   *
   * @param system thermodynamic system to update
   */
  static void ensureSyngasComponents(SystemInterface system) {
    for (String componentName : SYNGAS_COMPONENTS) {
      ensureComponent(system, componentName);
    }
    system.createDatabase(true);
    system.init(0);
    system.init(3);
  }

  /**
   * Ensures a component exists in a thermodynamic system.
   *
   * @param system thermodynamic system to update
   * @param componentName NeqSim component name
   */
  static void ensureComponent(SystemInterface system, String componentName) {
    if (!system.hasComponent(componentName)) {
      system.addComponent(componentName, TRACE_MOLES, "mole/sec");
    }
  }

  /**
   * Creates a configured Gibbs reactor for syngas equilibrium work.
   *
   * @param name reactor name
   * @param inletStream reactor inlet stream
   * @param energyMode Gibbs reactor energy mode
   * @return configured reactor
   */
  static GibbsReactor createSyngasGibbsReactor(String name, StreamInterface inletStream,
      GibbsReactor.EnergyMode energyMode) {
    GibbsReactor reactor = new GibbsReactor(name, inletStream);
    reactor.setUseAllDatabaseSpecies(false);
    reactor.setDampingComposition(1.0e-4);
    reactor.setMaxIterations(6000);
    reactor.setConvergenceTolerance(1.0e-6);
    reactor.setEnergyMode(energyMode);
    if (inletStream.getThermoSystem().hasComponent("nitrogen")) {
      reactor.setComponentAsInert("nitrogen");
    }
    return reactor;
  }

  /**
   * Gets the amount of a component in a system.
   *
   * @param system thermodynamic system
   * @param componentName component name
   * @return component amount in the stream basis, or zero if absent
   */
  static double getComponentMoles(SystemInterface system, String componentName) {
    try {
      if (system.hasComponent(componentName)) {
        return Math.max(0.0, system.getComponent(componentName).getNumberOfmoles());
      }
    } catch (Exception ex) {
      return 0.0;
    }
    return 0.0;
  }

  /**
   * Gets the mole fraction of a component.
   *
   * @param system thermodynamic system
   * @param componentName component name
   * @return component mole fraction, or zero if absent
   */
  static double getMoleFraction(SystemInterface system, String componentName) {
    try {
      if (system.hasComponent(componentName)) {
        return Math.max(0.0, system.getPhase(0).getComponent(componentName).getz());
      }
    } catch (Exception ex) {
      return 0.0;
    }
    return 0.0;
  }

  /**
   * Calculates methane conversion between two systems.
   *
   * @param inletSystem inlet thermodynamic system
   * @param outletSystem outlet thermodynamic system
   * @return methane conversion fraction between zero and one
   */
  static double calculateMethaneConversion(SystemInterface inletSystem,
      SystemInterface outletSystem) {
    double methaneIn = getComponentMoles(inletSystem, "methane");
    if (methaneIn <= 0.0) {
      return 0.0;
    }
    double methaneOut = getComponentMoles(outletSystem, "methane");
    return clamp((methaneIn - methaneOut) / methaneIn, 0.0, 1.0);
  }

  /**
   * Calculates the steam-to-carbon ratio for a hydrocarbon reforming feed.
   *
   * @param system thermodynamic system
   * @return water-to-methane molar ratio
   */
  static double calculateSteamToCarbonRatio(SystemInterface system) {
    double methane = getComponentMoles(system, "methane");
    if (methane <= 0.0) {
      return Double.NaN;
    }
    return getComponentMoles(system, "water") / methane;
  }

  /**
   * Calculates the oxygen-to-carbon ratio for an oxygen-blown syngas feed.
   *
   * @param system thermodynamic system
   * @return oxygen-to-methane molar ratio
   */
  static double calculateOxygenToCarbonRatio(SystemInterface system) {
    double methane = getComponentMoles(system, "methane");
    if (methane <= 0.0) {
      return Double.NaN;
    }
    return getComponentMoles(system, "oxygen") / methane;
  }

  /**
   * Estimates dry syngas lower heating value from H2, CO, and CH4 fractions.
   *
   * @param system syngas thermodynamic system
   * @return lower heating value in MJ/Nm3
   */
  static double estimateDrySyngasLhvMjPerNm3(SystemInterface system) {
    double h2 = getMoleFraction(system, "hydrogen");
    double co = getMoleFraction(system, "CO");
    double ch4 = getMoleFraction(system, "methane");
    double water = getMoleFraction(system, "water");
    double dryFactor = Math.max(1.0e-12, 1.0 - water);
    return (h2 * H2_LHV_MJ_PER_NM3 + co * CO_LHV_MJ_PER_NM3 + ch4 * CH4_LHV_MJ_PER_NM3) / dryFactor;
  }

  /**
   * Extracts a compact syngas composition map.
   *
   * @param system thermodynamic system
   * @return ordered component mole-fraction map
   */
  static Map<String, Double> extractSyngasComposition(SystemInterface system) {
    Map<String, Double> composition = new LinkedHashMap<String, Double>();
    for (String componentName : SYNGAS_COMPONENTS) {
      double fraction = getMoleFraction(system, componentName);
      if (fraction > 1.0e-10) {
        composition.put(componentName, fraction);
      }
    }
    return composition;
  }

  /**
   * Bounds a value to a closed interval.
   *
   * @param value value to bound
   * @param minimum lower bound
   * @param maximum upper bound
   * @return bounded value
   */
  static double clamp(double value, double minimum, double maximum) {
    return Math.max(minimum, Math.min(maximum, value));
  }
}
