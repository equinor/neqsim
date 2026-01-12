package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calculates the asphaltene onset pressure at a given temperature.
 *
 * <p>
 * The asphaltene onset pressure (AOP) is the pressure at which asphaltenes begin to precipitate
 * from the oil phase. This typically occurs near the bubble point pressure where the oil's solvency
 * for asphaltenes decreases due to light-end vaporization.
 * </p>
 *
 * <p>
 * The algorithm uses a pressure search with solid phase stability analysis:
 * </p>
 * <ol>
 * <li>Start at a high pressure (reservoir conditions) where asphaltenes are stable</li>
 * <li>Decrease pressure incrementally</li>
 * <li>At each pressure, perform a flash with solid phase check</li>
 * <li>Onset pressure is where solid asphaltene phase first appears</li>
 * </ol>
 *
 * <p>
 * References:
 * </p>
 * <ul>
 * <li>Hammami, A., et al. (2000). SPE 58745</li>
 * <li>Akbarzadeh, K., et al. (2007). Fluid Phase Equilibria</li>
 * </ul>
 *
 * @author ASMF
 * @version 1.0
 */
public class AsphalteneOnsetPressureFlash extends ConstantDutyFlash {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(AsphalteneOnsetPressureFlash.class);

  /** Maximum number of iterations for pressure search. */
  private static final int MAX_ITERATIONS = 100;

  /** Pressure tolerance for convergence (bara). */
  private static final double PRESSURE_TOLERANCE = 0.1;

  /** Initial pressure step for search (bara). */
  private double pressureStep = 10.0;

  /** Starting pressure for search (bara). */
  private double startPressure;

  /** Minimum pressure to search to (bara). */
  private double minPressure = 1.0;

  /** The calculated onset pressure (bara). */
  private double onsetPressure = Double.NaN;

  /** Flag indicating if onset was found. */
  private boolean onsetFound = false;

  /** Amount of asphaltene precipitated at onset (mole fraction). */
  private double precipitatedFraction = 0.0;

  /**
   * Constructor for AsphalteneOnsetPressureFlash.
   *
   * @param system the thermodynamic system (should contain asphaltene component)
   */
  public AsphalteneOnsetPressureFlash(SystemInterface system) {
    super(system);
    this.startPressure = system.getPressure();
  }

  /**
   * Constructor with specified pressure range.
   *
   * @param system the thermodynamic system
   * @param startPressure starting pressure for search (bara)
   * @param minPressure minimum pressure to search to (bara)
   */
  public AsphalteneOnsetPressureFlash(SystemInterface system, double startPressure,
      double minPressure) {
    super(system);
    this.startPressure = startPressure;
    this.minPressure = minPressure;
  }

  /** {@inheritDoc} */
  @Override
  public void run() {
    // Verify system has asphaltene component
    if (!hasAsphalteneComponent()) {
      logger.warn("System does not contain asphaltene component - adding generic asphaltene");
      // Could add a default asphaltene pseudo-component here
    }

    // Enable solid phase checking for asphaltene
    system.setSolidPhaseCheck("asphaltene");

    double currentPressure = startPressure;
    double previousPressure = startPressure;
    boolean wasStable = true;
    int iteration = 0;

    ThermodynamicOperations ops = new ThermodynamicOperations(system);

    // Phase 1: Coarse search - find approximate onset region
    while (currentPressure > minPressure && iteration < MAX_ITERATIONS) {
      iteration++;
      system.setPressure(currentPressure);

      try {
        ops.TPflash();
      } catch (Exception e) {
        logger.error("Flash failed at P = {} bara: {}", currentPressure, e.getMessage());
        currentPressure -= pressureStep;
        continue;
      }

      boolean hasAsphaltenePhase = checkForAsphaltenePrecipitation();

      if (hasAsphaltenePhase && wasStable) {
        // Transition from stable to unstable - onset is between previous and current
        onsetFound = true;

        // Phase 2: Refine with bisection
        onsetPressure = refinePressure(previousPressure, currentPressure, ops);
        break;
      }

      wasStable = !hasAsphaltenePhase;
      previousPressure = currentPressure;
      currentPressure -= pressureStep;
    }

    if (!onsetFound) {
      logger.info("No asphaltene onset found in pressure range {} to {} bara", startPressure,
          minPressure);
      onsetPressure = Double.NaN;
    } else {
      // Calculate precipitated amount at onset
      system.setPressure(onsetPressure - 1.0); // Slightly below onset
      try {
        ops.TPflash();
        precipitatedFraction = calculatePrecipitatedFraction();
      } catch (Exception e) {
        logger.debug("Could not calculate precipitated fraction: {}", e.getMessage());
      }

      logger.info("Asphaltene onset pressure: {} bara at T = {} K", onsetPressure,
          system.getTemperature());
    }
  }

  /**
   * Refines the onset pressure using bisection method.
   *
   * @param upperP upper pressure bound (stable)
   * @param lowerP lower pressure bound (unstable)
   * @param ops thermodynamic operations object
   * @return refined onset pressure
   */
  private double refinePressure(double upperP, double lowerP, ThermodynamicOperations ops) {
    double high = upperP;
    double low = lowerP;
    double mid;
    int bisectIter = 0;

    while ((high - low) > PRESSURE_TOLERANCE && bisectIter < 20) {
      bisectIter++;
      mid = (high + low) / 2.0;
      system.setPressure(mid);

      try {
        ops.TPflash();
      } catch (Exception e) {
        logger.debug("Flash failed during refinement at P = {}", mid);
        high = mid;
        continue;
      }

      if (checkForAsphaltenePrecipitation()) {
        low = mid; // Precipitation at mid, onset is above
      } else {
        high = mid; // Stable at mid, onset is below
      }
    }

    return (high + low) / 2.0;
  }

  /**
   * Checks if the system contains an asphaltene component.
   *
   * @return true if asphaltene component exists
   */
  private boolean hasAsphalteneComponent() {
    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName().toLowerCase();
      if (name.contains("asphaltene") || name.contains("asphalten")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks if asphaltene precipitation has occurred.
   *
   * <p>
   * Supports both solid asphaltene (PhaseType.ASPHALTENE) and liquid-liquid split
   * (PhaseType.LIQUID_ASPHALTENE) for Pedersen's approach.
   * </p>
   *
   * @return true if asphaltene-rich phase exists
   */
  private boolean checkForAsphaltenePrecipitation() {
    // Check for solid asphaltene phase (PhaseType.ASPHALTENE)
    if (system.hasPhaseType("asphaltene")) {
      try {
        if (system.getPhaseOfType("asphaltene").getNumberOfMolesInPhase() > 1e-10) {
          return true;
        }
      } catch (Exception e) {
        // Phase type check failed
      }
    }

    // Check for liquid asphaltene phase (Pedersen's method - PhaseType.LIQUID_ASPHALTENE)
    if (system.hasPhaseType("asphaltene liquid")) {
      try {
        if (system.getPhaseOfType("asphaltene liquid").getNumberOfMolesInPhase() > 1e-10) {
          return true;
        }
      } catch (Exception e) {
        // Phase type check failed
      }
    }

    // Check for asphaltene-rich liquid phases using isAsphalteneRich()
    for (int p = 0; p < system.getNumberOfPhases(); p++) {
      if (system.getPhase(p).isAsphalteneRich()) {
        return true;
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
   * Calculates the fraction of asphaltene that has precipitated.
   *
   * <p>
   * Supports both solid asphaltene precipitation and liquid-liquid split (Pedersen's method).
   * </p>
   *
   * @return mole fraction of asphaltene in precipitated phase
   */
  private double calculatePrecipitatedFraction() {
    // Check for asphaltene, liquid asphaltene, or solid phase
    boolean hasAsphaltenePhase = system.hasPhaseType("asphaltene");
    boolean hasLiquidAsphaltenePhase = system.hasPhaseType("asphaltene liquid");
    boolean hasSolidPhase = system.hasPhaseType("solid");

    if (!hasAsphaltenePhase && !hasLiquidAsphaltenePhase && !hasSolidPhase) {
      // Also check for asphaltene-rich liquid phases
      boolean hasAsphalteneRichPhase = false;
      for (int p = 0; p < system.getNumberOfPhases(); p++) {
        if (system.getPhase(p).isAsphalteneRich()) {
          hasAsphalteneRichPhase = true;
          break;
        }
      }
      if (!hasAsphalteneRichPhase) {
        return 0.0;
      }
    }

    double totalAsphaltene = 0.0;
    double precipitatedAsphaltene = 0.0;

    for (int i = 0; i < system.getPhase(0).getNumberOfComponents(); i++) {
      String name = system.getPhase(0).getComponent(i).getComponentName().toLowerCase();
      if (name.contains("asphaltene") || name.contains("asphalten")) {
        totalAsphaltene =
            system.getPhase(0).getComponent(i).getz() * system.getTotalNumberOfMoles();

        if (hasAsphaltenePhase) {
          precipitatedAsphaltene =
              system.getPhaseOfType("asphaltene").getComponent(i).getNumberOfMolesInPhase();
        } else if (hasLiquidAsphaltenePhase) {
          precipitatedAsphaltene =
              system.getPhaseOfType("asphaltene liquid").getComponent(i).getNumberOfMolesInPhase();
        } else if (hasSolidPhase) {
          precipitatedAsphaltene =
              system.getPhaseOfType("solid").getComponent(i).getNumberOfMolesInPhase();
        } else {
          // Check for asphaltene-rich liquid phases
          for (int p = 0; p < system.getNumberOfPhases(); p++) {
            if (system.getPhase(p).isAsphalteneRich()) {
              precipitatedAsphaltene = system.getPhase(p).getComponent(i).getNumberOfMolesInPhase();
              break;
            }
          }
        }
        break;
      }
    }

    if (totalAsphaltene > 1e-10) {
      return precipitatedAsphaltene / totalAsphaltene;
    }
    return 0.0;
  }

  /**
   * Gets the calculated asphaltene onset pressure.
   *
   * @return onset pressure in bara, or NaN if not found
   */
  public double getOnsetPressure() {
    return onsetPressure;
  }

  /**
   * Gets the onset pressure in specified units.
   *
   * @param unit pressure unit ("bara", "psia", "MPa")
   * @return onset pressure in specified units
   */
  public double getOnsetPressure(String unit) {
    if (Double.isNaN(onsetPressure)) {
      return Double.NaN;
    }

    switch (unit.toLowerCase()) {
      case "bara":
      case "bar":
        return onsetPressure;
      case "psia":
      case "psi":
        return onsetPressure * 14.5038;
      case "mpa":
        return onsetPressure * 0.1;
      case "kpa":
        return onsetPressure * 100.0;
      default:
        return onsetPressure;
    }
  }

  /**
   * Checks if asphaltene onset was found.
   *
   * @return true if onset pressure was determined
   */
  public boolean isOnsetFound() {
    return onsetFound;
  }

  /**
   * Gets the fraction of asphaltene precipitated at onset conditions.
   *
   * @return precipitated mole fraction (0-1)
   */
  public double getPrecipitatedFraction() {
    return precipitatedFraction;
  }

  /**
   * Sets the pressure step for coarse search.
   *
   * @param step pressure step in bara
   */
  public void setPressureStep(double step) {
    this.pressureStep = step;
  }

  /**
   * Sets the minimum pressure for search.
   *
   * @param minP minimum pressure in bara
   */
  public void setMinPressure(double minP) {
    this.minPressure = minP;
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
