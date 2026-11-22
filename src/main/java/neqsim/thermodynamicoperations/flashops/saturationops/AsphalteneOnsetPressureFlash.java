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
 * <li>Optionally (default on) the search continues below the upper onset to find the lower onset, where the
 * precipitate re-dissolves (normally below the bubble point as gas leaves the oil)</li>
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
  private volatile double pressureTolerance = 0.1;

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

  /** Lower onset pressure where the precipitate re-dissolves (bara), NaN if not found. */
  private double lowerOnsetPressure = Double.NaN;

  /** Whether the search continues below the upper onset to locate the lower onset. */
  private boolean searchLowerOnset = true;

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
    lowerOnsetPressure = Double.NaN;
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
        onsetPressure = result;
        onsetFound = true;
        if (searchLowerOnset) {
          lowerOnsetPressure = searchLowerOnset(currentPressure, ops);
        }
        system.setPressure(result);
        AsphalteneOnsetFlashSupport.flash(system, ops);
        // Report the amount just below the onset, where the precipitate is present.
        system.setPressure(Math.max(minPressure, result - pressureTolerance));
        AsphalteneOnsetFlashSupport.flash(system, ops);
        precipitatedFraction = calculatePrecipitatedFraction();
        system.setPressure(result);
        AsphalteneOnsetFlashSupport.flash(system, ops);
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

    while ((high - low) > pressureTolerance && bisectIter < 60) {
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

    if (high - low > pressureTolerance) {
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
    return AsphalteneOnsetFlashSupport.precipitatedFraction(system);
  }

  /**
   * Continues the pressure search below the upper onset to find the lower onset, where the precipitate re-dissolves.
   *
   * @param unstablePressure a pressure known to show precipitate (bara)
   * @param ops operations bound to the system
   * @return lower onset pressure in bara, or NaN if the precipitate persists down to the minimum pressure
   */
  private double searchLowerOnset(double unstablePressure, ThermodynamicOperations ops) {
    double previous = unstablePressure;
    for (int iteration = 0; previous > minPressure && iteration < MAX_ITERATIONS; iteration++) {
      double current = Math.max(minPressure, previous - pressureStep);
      if (current == previous) {
        break;
      }
      system.setPressure(current);
      AsphalteneOnsetFlashSupport.flash(system, ops);
      if (!AsphalteneOnsetFlashSupport.hasPrecipitate(system)) {
        double unstable = previous;
        double stable = current;
        for (int i = 0; i < 60 && unstable - stable > pressureTolerance; i++) {
          double mid = 0.5 * (unstable + stable);
          system.setPressure(mid);
          AsphalteneOnsetFlashSupport.flash(system, ops);
          if (AsphalteneOnsetFlashSupport.hasPrecipitate(system)) {
            unstable = mid;
          } else {
            stable = mid;
          }
        }
        return 0.5 * (unstable + stable);
      }
      previous = current;
    }
    return Double.NaN;
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
   * @param unit pressure unit: bara/bar, barg, psia, psig, MPa, kPa, Pa or atm (case-insensitive)
   * @return onset pressure in specified units
   * @throws IllegalArgumentException for an unknown unit
   */
  public double getOnsetPressure(String unit) {
    if (Double.isNaN(onsetPressure)) {
      return Double.NaN;
    }

    return convertPressure(onsetPressure, unit);
  }

  /**
   * Converts a pressure in bara to the requested unit.
   *
   * @param bara pressure in bara
   * @param unit target unit: bara/bar, barg, psia, psig, MPa, kPa, Pa or atm (case-insensitive)
   * @return converted value (NaN stays NaN)
   * @throws IllegalArgumentException for an unknown unit
   */
  static double convertPressure(double bara, String unit) {
    if (Double.isNaN(bara)) {
      return Double.NaN;
    }
    if (unit == null) {
      throw new IllegalArgumentException("Pressure unit must not be null");
    }
    switch (unit.trim().toLowerCase(java.util.Locale.ROOT)) {
      case "bara":
      case "bar":
        return bara;
      case "barg":
        return bara - 1.01325;
      case "psia":
        return bara * 14.503773773;
      case "psig":
        return bara * 14.503773773 - 14.695948775;
      case "mpa":
        return bara * 0.1;
      case "kpa":
        return bara * 100.0;
      case "pa":
        return bara * 1.0e5;
      case "atm":
        return bara / 1.01325;
      default:
        throw new IllegalArgumentException("Unsupported pressure unit '" + unit
            + "'; use bara, barg, psia, psig, MPa, kPa, Pa or atm (plain 'psi' is ambiguous)");
    }
  }

  /**
   * Gets the lower onset pressure, where the precipitate re-dissolves on further depressurisation.
   *
   * @return lower onset pressure in bara, or NaN if not found or not searched
   */
  public double getLowerOnsetPressure() {
    return lowerOnsetPressure;
  }

  /**
   * Gets the lower onset pressure in the requested unit.
   *
   * @param unit pressure unit (see {@link #getOnsetPressure(String)})
   * @return lower onset pressure, or NaN
   */
  public double getLowerOnsetPressure(String unit) {
    return convertPressure(lowerOnsetPressure, unit);
  }

  /**
   * Sets whether the search continues below the upper onset to find the lower onset (default true).
   *
   * @param search true to search for the lower onset
   */
  public void setSearchLowerOnset(boolean search) {
    this.searchLowerOnset = search;
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
   * Sets the bisection tolerance of the onset pressure.
   *
   * @param tolerance tolerance in bar
   */
  public void setPressureTolerance(double tolerance) {
    AsphalteneOnsetFlashSupport.requirePositive(tolerance, "Pressure tolerance");
    this.pressureTolerance = tolerance;
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
