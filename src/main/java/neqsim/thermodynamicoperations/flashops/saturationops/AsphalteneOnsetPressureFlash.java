package neqsim.thermodynamicoperations.flashops.saturationops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calculates the asphaltene onset pressure at a given temperature.
 *
 * <p>
 * The asphaltene onset pressure (AOP) is the pressure at which asphaltenes begin to precipitate from the oil phase.
 * This typically occurs near the bubble point pressure where the oil's solvency for asphaltenes decreases due to
 * light-end vaporization.
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
  private double pressureStep = 5.0;

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
  public AsphalteneOnsetPressureFlash(SystemInterface system, double startPressure, double minPressure) {
    super(system);
    this.startPressure = startPressure;
    this.minPressure = minPressure;
  }

  /**
   * Search a stable-to-precipitated pressure bracket, including the lower range endpoint.
   *
   * @throws IllegalArgumentException if characterization or search settings are invalid
   * @throws IllegalStateException if the start is precipitated, a flash fails, or the search budget is exhausted
   */
  @Override
  public void run() {
    onsetFound = false;
    onsetPressure = Double.NaN;
    precipitatedFraction = 0.0;
    AsphalteneOnsetFlashSupport.requirePositive(pressureStep, "Pressure step");
    AsphalteneOnsetFlashSupport.requirePositive(startPressure, "Starting pressure");
    AsphalteneOnsetFlashSupport.requirePositive(minPressure, "Minimum pressure");
    if (startPressure < minPressure) {
      throw new IllegalArgumentException("Starting pressure must be at least the minimum pressure");
    }
    AsphalteneOnsetFlashSupport.prepare(system);
    ThermodynamicOperations ops = new ThermodynamicOperations(system);
    double previousPressure = startPressure;
    system.setPressure(previousPressure);
    AsphalteneOnsetFlashSupport.flash(system, ops);
    if (AsphalteneOnsetFlashSupport.hasPrecipitate(system)) {
      throw new IllegalStateException("Asphaltene is already precipitated at the starting pressure; "
          + "choose an undersaturated start to bracket onset");
    }
    for (int iteration = 0; previousPressure > minPressure; iteration++) {
      if (iteration >= MAX_ITERATIONS) {
        throw new IllegalStateException("Asphaltene pressure search exhausted before reaching the minimum pressure");
      }
      double currentPressure = Math.max(minPressure, previousPressure - pressureStep);
      if (currentPressure == previousPressure) {
        throw new IllegalArgumentException("Pressure step is too small to advance the search");
      }
      system.setPressure(currentPressure);
      AsphalteneOnsetFlashSupport.flash(system, ops);
      if (AsphalteneOnsetFlashSupport.hasPrecipitate(system)) {
        double result = refinePressure(previousPressure, currentPressure, ops);
        system.setPressure(result);
        AsphalteneOnsetFlashSupport.flash(system, ops);
        double fraction = calculatePrecipitatedFraction();
        onsetPressure = result;
        precipitatedFraction = fraction;
        onsetFound = true;
        return;
      }
      previousPressure = currentPressure;
    }
    logger.info("No asphaltene onset found in pressure range {} to {} bara", startPressure, minPressure);
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

      AsphalteneOnsetFlashSupport.flash(system, ops);

      if (AsphalteneOnsetFlashSupport.hasPrecipitate(system)) {
        low = mid; // Precipitation at mid, onset is above
      } else {
        high = mid; // Stable at mid, onset is below
      }
    }

    if (high - low > PRESSURE_TOLERANCE) {
      throw new IllegalStateException("Asphaltene pressure bracket did not converge");
    }
    return (high + low) / 2.0;
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
        totalAsphaltene = system.getPhase(0).getComponent(i).getz() * system.getTotalNumberOfMoles();

        if (hasAsphaltenePhase) {
          precipitatedAsphaltene = system.getPhaseOfType("asphaltene").getComponent(i).getNumberOfMolesInPhase();
        } else if (hasLiquidAsphaltenePhase) {
          precipitatedAsphaltene = system.getPhaseOfType("asphaltene liquid").getComponent(i).getNumberOfMolesInPhase();
        } else if (hasSolidPhase) {
          precipitatedAsphaltene = system.getPhaseOfType("solid").getComponent(i).getNumberOfMolesInPhase();
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
   * Gets the fraction of asphaltene precipitated at the reported onset state.
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
    AsphalteneOnsetFlashSupport.requirePositive(step, "Pressure step");
    this.pressureStep = step;
  }

  /**
   * Sets the minimum pressure for search.
   *
   * @param minP minimum pressure in bara
   */
  public void setMinPressure(double minP) {
    AsphalteneOnsetFlashSupport.requirePositive(minP, "Minimum pressure");
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
