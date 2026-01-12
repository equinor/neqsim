package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calculates the asphaltene onset temperature at a given pressure.
 *
 * <p>
 * The asphaltene onset temperature (AOT) is the temperature at which asphaltenes begin to
 * precipitate from the oil phase at a fixed pressure. This is useful for:
 * </p>
 * <ul>
 * <li>Cold flow scenarios where temperature drops during production</li>
 * <li>Subsea tieback design where ambient temperatures are low</li>
 * <li>Understanding temperature-induced precipitation during processing</li>
 * </ul>
 *
 * <p>
 * Note: Asphaltene precipitation is primarily pressure-driven (near bubble point), but temperature
 * effects can be significant in some fluids, particularly heavy oils.
 * </p>
 *
 * @author ASMF
 * @version 1.0
 */
public class AsphalteneOnsetTemperatureFlash extends ConstantDutyFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AsphalteneOnsetTemperatureFlash.class);

  /** Maximum number of iterations for temperature search. */
  private static final int MAX_ITERATIONS = 100;

  /** Temperature tolerance for convergence (K). */
  private static final double TEMPERATURE_TOLERANCE = 0.5;

  /** Initial temperature step for search (K). */
  private double temperatureStep = 10.0;

  /** Starting temperature for search (K). */
  private double startTemperature;

  /** Minimum temperature to search to (K). */
  private double minTemperature = 273.15;

  /** Maximum temperature to search to (K). */
  private double maxTemperature = 473.15;

  /** The calculated onset temperature (K). */
  private double onsetTemperature = Double.NaN;

  /** Flag indicating if onset was found. */
  private boolean onsetFound = false;

  /** Search direction: true = decreasing temp, false = increasing temp. */
  private boolean searchDecreasing = true;

  /**
   * Constructor for AsphalteneOnsetTemperatureFlash.
   *
   * @param system the thermodynamic system (should contain asphaltene component)
   */
  public AsphalteneOnsetTemperatureFlash(SystemInterface system) {
    super(system);
    this.startTemperature = system.getTemperature();
  }

  /**
   * Constructor with specified temperature range.
   *
   * @param system the thermodynamic system
   * @param startTemperature starting temperature for search (K)
   * @param minTemperature minimum temperature to search to (K)
   * @param maxTemperature maximum temperature to search to (K)
   */
  public AsphalteneOnsetTemperatureFlash(SystemInterface system, double startTemperature,
      double minTemperature, double maxTemperature) {
    super(system);
    this.startTemperature = startTemperature;
    this.minTemperature = minTemperature;
    this.maxTemperature = maxTemperature;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // Enable solid phase checking for asphaltene
    system.setSolidPhaseCheck("asphaltene");

    double currentTemp = startTemperature;
    double previousTemp = startTemperature;
    boolean wasStable = true;
    int iteration = 0;

    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    // Determine search direction based on initial stability
    system.setTemperature(startTemperature);
    try {
      ops.TPflash();
      wasStable = !checkForAsphaltenePrecipitation();
    } catch (Exception e) {
      logger.error("Initial flash failed: {}", e.getMessage());
      return;
    }

    // Phase 1: Coarse search
    double direction = searchDecreasing ? -1.0 : 1.0;
    double limitTemp = searchDecreasing ? minTemperature : maxTemperature;

    while ((searchDecreasing ? currentTemp > limitTemp : currentTemp < limitTemp)
        && iteration < MAX_ITERATIONS) {
      iteration++;
      system.setTemperature(currentTemp);

      try {
        ops.TPflash();
      } catch (Exception e) {
        logger.error("Flash failed at T = {} K: {}", currentTemp, e.getMessage());
        currentTemp += direction * temperatureStep;
        continue;
      }

      boolean hasAsphaltenePhase = checkForAsphaltenePrecipitation();

      if (hasAsphaltenePhase && wasStable) {
        // Transition from stable to unstable
        onsetFound = true;
        onsetTemperature = refineTemperature(previousTemp, currentTemp, ops);
        break;
      } else if (!hasAsphaltenePhase && !wasStable) {
        // Transition from unstable to stable (reverse direction case)
        onsetFound = true;
        onsetTemperature = refineTemperature(currentTemp, previousTemp, ops);
        break;
      }

      wasStable = !hasAsphaltenePhase;
      previousTemp = currentTemp;
      currentTemp += direction * temperatureStep;
    }

    if (!onsetFound) {
      logger.info("No asphaltene onset found in temperature range {} to {} K", startTemperature,
          limitTemp);
      onsetTemperature = Double.NaN;
    } else {
      logger.info("Asphaltene onset temperature: {} K ({} C) at P = {} bara", onsetTemperature,
          onsetTemperature - 273.15, system.getPressure());
    }
  }

  /**
   * Refines the onset temperature using bisection method.
   *
   * @param stableT temperature where stable
   * @param unstableT temperature where unstable
   * @param ops thermodynamic operations object
   * @return refined onset temperature
   */
  private double refineTemperature(double stableT, double unstableT, ThermodynamicOperations ops) {
    double stable = stableT;
    double unstable = unstableT;
    double mid;
    int bisectIter = 0;

    while (Math.abs(stable - unstable) > TEMPERATURE_TOLERANCE && bisectIter < 20) {
      bisectIter++;
      mid = (stable + unstable) / 2.0;
      system.setTemperature(mid);

      try {
        ops.TPflash();
      } catch (Exception e) {
        logger.debug("Flash failed during refinement at T = {}", mid);
        // Assume unstable if flash fails
        unstable = mid;
        continue;
      }

      if (checkForAsphaltenePrecipitation()) {
        unstable = mid;
      } else {
        stable = mid;
      }
    }

    return (stable + unstable) / 2.0;
  }

  /**
   * Checks if asphaltene precipitation has occurred.
   *
   * @return true if solid asphaltene phase exists
   */
  private boolean checkForAsphaltenePrecipitation() {
    // Check for asphaltene phase (PhaseType.ASPHALTENE)
    if (system.hasPhaseType("asphaltene")) {
      try {
        if (system.getPhaseOfType("asphaltene").getNumberOfMolesInPhase() > 1e-10) {
          return true;
        }
      } catch (Exception e) {
        // Phase type check failed
      }
    }

    // Also check for generic solid phase that may contain asphaltene
    if (system.hasPhaseType("solid")) {
      // Verify it contains asphaltene component
      try {
        neqsim.thermo.phase.PhaseInterface solidPhase = system.getPhaseOfType("solid");
        if (solidPhase.getNumberOfMolesInPhase() > 1e-10) {
          // Check if solid phase contains asphaltene component
          for (int i = 0; i < solidPhase.getNumberOfComponents(); i++) {
            String name = solidPhase.getComponent(i).getComponentName().toLowerCase();
            if ((name.contains("asphaltene") || name.contains("asphalten"))
                && solidPhase.getComponent(i).getNumberOfMolesInPhase() > 1e-10) {
              return true;
            }
          }
        }
      } catch (Exception e) {
        // Phase type check failed
      }
    }

    return false;
  }

  /**
   * Gets the calculated asphaltene onset temperature.
   *
   * @return onset temperature in Kelvin, or NaN if not found
   */
  public double getOnsetTemperature() {
    return onsetTemperature;
  }

  /**
   * Gets the onset temperature in specified units.
   *
   * @param unit temperature unit ("K", "C", "F", "R")
   * @return onset temperature in specified units
   */
  public double getOnsetTemperature(String unit) {
    if (Double.isNaN(onsetTemperature)) {
      return Double.NaN;
    }

    switch (unit.toUpperCase()) {
      case "K":
      case "KELVIN":
        return onsetTemperature;
      case "C":
      case "CELSIUS":
        return onsetTemperature - 273.15;
      case "F":
      case "FAHRENHEIT":
        return (onsetTemperature - 273.15) * 9.0 / 5.0 + 32.0;
      case "R":
      case "RANKINE":
        return onsetTemperature * 1.8;
      default:
        return onsetTemperature;
    }
  }

  /**
   * Checks if asphaltene onset was found.
   *
   * @return true if onset temperature was determined
   */
  public boolean isOnsetFound() {
    return onsetFound;
  }

  /**
   * Sets the temperature step for coarse search.
   *
   * @param step temperature step in K
   */
  public void setTemperatureStep(double step) {
    this.temperatureStep = step;
  }

  /**
   * Sets the search direction.
   *
   * @param decreasing true to search towards lower temperatures
   */
  public void setSearchDecreasing(boolean decreasing) {
    this.searchDecreasing = decreasing;
  }

  /** {@inheritDoc} */
  @Override
  public void printToFile(String name) {
    // Not implemented
  }

  /** {@inheritDoc} */
  @Override
  public org.jfree.chart.JFreeChart getJFreeChart(String name) {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public SystemInterface getThermoSystem() {
    return system;
  }
}
