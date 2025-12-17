package neqsim.physicalproperties.methods.liquidphysicalproperties.diffusivity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.physicalproperties.system.PhysicalProperties;
import neqsim.thermo.phase.PhaseInterface;

/**
 * Automatic diffusivity model selector for liquid phases.
 *
 * <p>
 * This class analyzes the fluid composition and conditions to select the most appropriate
 * diffusivity correlation. It considers:
 * </p>
 * <ul>
 * <li>Pressure level (high pressure requires correction)</li>
 * <li>Solvent type (aqueous vs hydrocarbon)</li>
 * <li>Presence of special components (amines, CO2)</li>
 * <li>Temperature range validation</li>
 * </ul>
 *
 * @author Even Solbraa
 * @version 1.0
 */
public class DiffusivityModelSelector {
  /** Logger object for class. */
  private static final Logger logger = LogManager.getLogger(DiffusivityModelSelector.class);

  /** High pressure threshold [bar]. */
  private static final double HIGH_PRESSURE_THRESHOLD = 50.0;

  /** Water mole fraction threshold for aqueous classification. */
  private static final double AQUEOUS_THRESHOLD = 0.5;

  /** Minimum validated temperature [K]. */
  private static final double T_MIN = 200.0;

  /** Maximum validated temperature [K]. */
  private static final double T_MAX = 600.0;

  /**
   * Diffusivity model types.
   */
  public enum DiffusivityModelType {
    /** Siddiqi-Lucas for aqueous systems. */
    SIDDIQI_LUCAS,
    /** Hayduk-Minhas for hydrocarbon systems. */
    HAYDUK_MINHAS,
    /** High-pressure corrected model. */
    HIGH_PRESSURE_CORRECTED,
    /** Specialized amine diffusivity. */
    AMINE,
    /** CO2-water specific correlation. */
    CO2_WATER,
    /** Corresponding states for general use. */
    CORRESPONDING_STATES
  }

  /**
   * Private constructor to prevent instantiation.
   */
  private DiffusivityModelSelector() {}

  /**
   * Select the optimal diffusivity model based on phase composition and conditions.
   *
   * @param phase the phase to analyze
   * @return the recommended diffusivity model type
   */
  public static DiffusivityModelType selectOptimalModel(PhaseInterface phase) {
    double pressure = phase.getPressure();
    double temperature = phase.getTemperature();

    // Validate temperature range
    if (temperature < T_MIN || temperature > T_MAX) {
      logger.warn("Temperature {} K is outside validated range [{}-{}] for diffusivity models",
          temperature, T_MIN, T_MAX);
    }

    // Check for special components
    boolean hasAmines = hasAmineComponents(phase);
    boolean hasCO2 = hasComponent(phase, "CO2");
    boolean isAqueous = isAqueousPhase(phase);
    boolean isHighPressure = pressure > HIGH_PRESSURE_THRESHOLD;

    // Decision tree for model selection
    if (hasAmines && hasCO2) {
      logger.debug("Selected AMINE model for amine-CO2 system");
      return DiffusivityModelType.AMINE;
    }

    if (hasCO2 && isAqueous && !isHighPressure) {
      logger.debug("Selected CO2_WATER model for CO2 in aqueous system");
      return DiffusivityModelType.CO2_WATER;
    }

    if (isHighPressure) {
      logger.debug("Selected HIGH_PRESSURE_CORRECTED model for P = {} bar", pressure);
      return DiffusivityModelType.HIGH_PRESSURE_CORRECTED;
    }

    if (isAqueous) {
      logger.debug("Selected SIDDIQI_LUCAS model for aqueous system");
      return DiffusivityModelType.SIDDIQI_LUCAS;
    }

    // Default: hydrocarbon system
    logger.debug("Selected HAYDUK_MINHAS model for hydrocarbon system");
    return DiffusivityModelType.HAYDUK_MINHAS;
  }

  /**
   * Create a diffusivity model instance based on the selected type.
   *
   * @param liquidPhase the physical properties object
   * @param modelType the model type to create
   * @return the diffusivity model instance
   */
  public static Diffusivity createModel(PhysicalProperties liquidPhase,
      DiffusivityModelType modelType) {
    switch (modelType) {
      case SIDDIQI_LUCAS:
        return new SiddiqiLucasMethod(liquidPhase);
      case HAYDUK_MINHAS:
        return new HaydukMinhasDiffusivity(liquidPhase);
      case HIGH_PRESSURE_CORRECTED:
        return new HighPressureDiffusivity(liquidPhase);
      case AMINE:
        return new AmineDiffusivity(liquidPhase);
      case CO2_WATER:
        return new CO2water(liquidPhase);
      case CORRESPONDING_STATES:
      default:
        return new SiddiqiLucasMethod(liquidPhase);
    }
  }

  /**
   * Create an auto-selected diffusivity model based on phase analysis.
   *
   * @param liquidPhase the physical properties object
   * @return the automatically selected diffusivity model
   */
  public static Diffusivity createAutoSelectedModel(PhysicalProperties liquidPhase) {
    DiffusivityModelType modelType = selectOptimalModel(liquidPhase.getPhase());
    logger.info("Auto-selected diffusivity model: {}", modelType);
    return createModel(liquidPhase, modelType);
  }

  /**
   * Check if the phase is predominantly aqueous.
   *
   * @param phase the phase to check
   * @return true if water mole fraction exceeds threshold
   */
  private static boolean isAqueousPhase(PhaseInterface phase) {
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      String name = phase.getComponent(i).getComponentName().toLowerCase();
      if (name.equals("water") || name.equals("h2o")) {
        return phase.getComponent(i).getx() > AQUEOUS_THRESHOLD;
      }
    }
    return false;
  }

  /**
   * Check if the phase contains a specific component.
   *
   * @param phase the phase to check
   * @param componentName the component name to find
   * @return true if component is present with non-zero mole fraction
   */
  private static boolean hasComponent(PhaseInterface phase, String componentName) {
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      String name = phase.getComponent(i).getComponentName();
      if (name.equalsIgnoreCase(componentName) && phase.getComponent(i).getx() > 1e-10) {
        return true;
      }
    }
    return false;
  }

  /**
   * Check if the phase contains amine components.
   *
   * @param phase the phase to check
   * @return true if amines are present
   */
  private static boolean hasAmineComponents(PhaseInterface phase) {
    String[] amineNames = {"MDEA", "MDEA+", "MEA", "MEA+", "DEA", "DEA+", "MAPA", "Piperazine"};
    for (int i = 0; i < phase.getNumberOfComponents(); i++) {
      String name = phase.getComponent(i).getComponentName();
      for (String amine : amineNames) {
        if (name.equalsIgnoreCase(amine) && phase.getComponent(i).getx() > 1e-10) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Get a description of why a particular model was selected.
   *
   * @param phase the phase analyzed
   * @return human-readable explanation of model selection
   */
  public static String getModelSelectionReason(PhaseInterface phase) {
    DiffusivityModelType modelType = selectOptimalModel(phase);
    double pressure = phase.getPressure();

    switch (modelType) {
      case AMINE:
        return "Amine diffusivity model selected due to presence of amine components with CO2.";
      case CO2_WATER:
        return "CO2-water model selected for CO2 dissolution in aqueous phase.";
      case HIGH_PRESSURE_CORRECTED:
        return String.format(
            "High-pressure corrected model selected due to pressure (%.1f bar) exceeding %.1f bar threshold.",
            pressure, HIGH_PRESSURE_THRESHOLD);
      case SIDDIQI_LUCAS:
        return "Siddiqi-Lucas model selected for aqueous liquid phase (water > 50 mol%).";
      case HAYDUK_MINHAS:
        return "Hayduk-Minhas model selected for hydrocarbon liquid phase.";
      default:
        return "Default corresponding states model selected.";
    }
  }
}
