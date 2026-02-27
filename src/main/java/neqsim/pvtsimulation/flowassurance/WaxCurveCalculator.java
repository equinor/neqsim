package neqsim.pvtsimulation.flowassurance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Calculator for wax fraction curves with monotonicity enforcement.
 *
 * <p>
 * This class calculates the wax weight fraction as a function of temperature at a given pressure,
 * using NeqSim's thermodynamic wax models. The resulting curve is post-processed to enforce
 * physical monotonicity (wax fraction must increase with decreasing temperature).
 * </p>
 *
 * <p>
 * Key features:
 * </p>
 * <ul>
 * <li>Wax Appearance Temperature (WAT) determination</li>
 * <li>Full wax fraction vs temperature curve</li>
 * <li>Monotonicity enforcement to remove numerical artifacts</li>
 * <li>Multiple pressure evaluation for pipeline conditions</li>
 * </ul>
 *
 * <p>
 * Usage example:
 * </p>
 *
 * <pre>
 * SystemInterface fluid = new SystemSrkEos(273.15 + 80, 100.0);
 * // ... add components including wax-forming n-paraffins ...
 * fluid.setMixingRule("classic");
 *
 * WaxCurveCalculator calc = new WaxCurveCalculator(fluid);
 * calc.setPressure(100.0); // bara
 * calc.setTemperatureRange(-10.0, 80.0, 1.0); // C
 * calc.calculate();
 *
 * double wat = calc.getWaxAppearanceTemperatureC();
 * double[] temps = calc.getTemperaturesC();
 * double[] fractions = calc.getWaxWeightFractions();
 * </pre>
 *
 * @author ESOL
 * @version 1.0
 */
public class WaxCurveCalculator {

  /** Logger for this class. */
  private static final Logger logger = LogManager.getLogger(WaxCurveCalculator.class);

  /** The fluid system to evaluate. */
  private SystemInterface fluid;

  /** Pressure for wax calculations [bara]. */
  private double pressureBara = 100.0;

  /** Temperature range start [C]. */
  private double tempStartC = -10.0;

  /** Temperature range end [C]. */
  private double tempEndC = 80.0;

  /** Temperature step size [C]. */
  private double tempStepC = 1.0;

  /** Calculated temperatures [C]. */
  private double[] temperaturesC;

  /** Raw wax weight fractions (before monotonicity enforcement). */
  private double[] rawWaxFractions;

  /** Monotonicity-enforced wax weight fractions. */
  private double[] waxFractions;

  /** Calculated WAT [C]. */
  private double watC = Double.NaN;

  /** Number of successful calculations. */
  private int successCount = 0;

  /** Number of failed calculations. */
  private int failCount = 0;

  /** Number of monotonicity corrections applied. */
  private int monotonicityCorrections = 0;

  /** Whether to enforce monotonicity. */
  private boolean enforceMonotonicity = true;

  /**
   * Creates a new WaxCurveCalculator.
   *
   * @param fluid the thermodynamic system (must have wax-forming components)
   */
  public WaxCurveCalculator(SystemInterface fluid) {
    this.fluid = fluid;
  }

  /**
   * Sets the pressure for wax calculations.
   *
   * @param pressureBara pressure in bara
   */
  public void setPressure(double pressureBara) {
    this.pressureBara = pressureBara;
  }

  /**
   * Sets the temperature range for the wax curve.
   *
   * @param startC lower temperature [C]
   * @param endC upper temperature [C]
   * @param stepC temperature step [C]
   */
  public void setTemperatureRange(double startC, double endC, double stepC) {
    this.tempStartC = startC;
    this.tempEndC = endC;
    this.tempStepC = Math.abs(stepC);
    if (this.tempStepC < 0.1) {
      this.tempStepC = 0.1;
    }
  }

  /**
   * Sets whether to enforce monotonicity on the wax fraction curve.
   *
   * @param enforce true to enforce monotonicity (default)
   */
  public void setEnforceMonotonicity(boolean enforce) {
    this.enforceMonotonicity = enforce;
  }

  /**
   * Calculates the wax fraction curve.
   *
   * <p>
   * Evaluates wax weight fraction at each temperature point from high to low temperature. The curve
   * is then optionally post-processed to enforce monotonicity.
   * </p>
   */
  public void calculate() {
    int nPoints = (int) Math.ceil((tempEndC - tempStartC) / tempStepC) + 1;
    nPoints = Math.max(nPoints, 2);

    temperaturesC = new double[nPoints];
    rawWaxFractions = new double[nPoints];
    waxFractions = new double[nPoints];

    successCount = 0;
    failCount = 0;

    // Calculate from high T to low T
    for (int i = 0; i < nPoints; i++) {
      double tempC = tempEndC - i * tempStepC;
      if (tempC < tempStartC) {
        tempC = tempStartC;
      }
      temperaturesC[i] = tempC;

      try {
        SystemInterface tempFluid = fluid.clone();
        tempFluid.setTemperature(tempC + 273.15);
        tempFluid.setPressure(pressureBara);
        tempFluid.setMultiPhaseCheck(true);

        ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
        ops.TPflash();
        tempFluid.initPhysicalProperties();

        // Check for wax phase
        double waxFraction = 0.0;
        for (int p = 0; p < tempFluid.getNumberOfPhases(); p++) {
          String phaseType = tempFluid.getPhase(p).getPhaseTypeName();
          if ("wax".equalsIgnoreCase(phaseType)) {
            // Wax weight fraction of total system
            double waxMoles = tempFluid.getPhase(p).getNumberOfMolesInPhase();
            double waxMW = tempFluid.getPhase(p).getMolarMass();
            double totalMass = 0.0;
            for (int q = 0; q < tempFluid.getNumberOfPhases(); q++) {
              totalMass += tempFluid.getPhase(q).getNumberOfMolesInPhase()
                  * tempFluid.getPhase(q).getMolarMass();
            }
            if (totalMass > 0) {
              waxFraction = (waxMoles * waxMW) / totalMass;
            }
            break;
          }
        }

        rawWaxFractions[i] = waxFraction;
        successCount++;
      } catch (Exception e) {
        logger.debug("Wax flash failed at T={}C, P={}bara: {}", tempC, pressureBara,
            e.getMessage());
        rawWaxFractions[i] = i > 0 ? rawWaxFractions[i - 1] : 0.0;
        failCount++;
      }
    }

    // Apply monotonicity enforcement
    System.arraycopy(rawWaxFractions, 0, waxFractions, 0, nPoints);
    if (enforceMonotonicity) {
      enforceMonotonicity(waxFractions);
    }

    // Determine WAT (first temperature where wax fraction > 0, scanning from high to low T)
    watC = Double.NaN;
    for (int i = 0; i < nPoints; i++) {
      if (waxFractions[i] > 1e-8) {
        // Interpolate WAT between this point and the previous
        if (i > 0 && waxFractions[i - 1] <= 1e-8) {
          double t1 = temperaturesC[i - 1];
          double t2 = temperaturesC[i];
          double f1 = waxFractions[i - 1];
          double f2 = waxFractions[i];
          if (f2 > f1) {
            watC = t1 + (t2 - t1) * (1e-8 - f1) / (f2 - f1);
          } else {
            watC = t2;
          }
        } else {
          watC = temperaturesC[i];
        }
        break;
      }
    }
  }

  /**
   * Enforces monotonicity on the wax fraction array.
   *
   * <p>
   * Since temperatures are stored from high to low, wax fractions should be non-decreasing (index 0
   * = highest T = lowest wax). This method corrects any violations by replacing decreasing values
   * with the previous maximum.
   * </p>
   *
   * @param fractions the wax fraction array to enforce monotonicity on (modified in place)
   */
  private void enforceMonotonicity(double[] fractions) {
    monotonicityCorrections = 0;
    double maxSoFar = 0.0;

    for (int i = 0; i < fractions.length; i++) {
      if (fractions[i] < maxSoFar) {
        fractions[i] = maxSoFar;
        monotonicityCorrections++;
      } else {
        maxSoFar = fractions[i];
      }
    }

    if (monotonicityCorrections > 0) {
      logger.info("Applied {} monotonicity corrections to wax curve", monotonicityCorrections);
    }
  }

  /**
   * Calculates WAT using NeqSim's built-in calcWAT method.
   *
   * @return WAT in Celsius, or NaN if calculation fails
   */
  public double calculateWAT() {
    try {
      SystemInterface tempFluid = fluid.clone();
      tempFluid.setPressure(pressureBara);
      ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
      ops.calcWAT();
      double watK = tempFluid.getTemperature();
      return watK - 273.15;
    } catch (Exception e) {
      logger.warn("WAT calculation failed: {}", e.getMessage());
      return Double.NaN;
    }
  }

  /**
   * Calculates wax fraction at multiple pressures for a pipeline profile.
   *
   * @param pressuresBara array of pressures [bara]
   * @param temperatureC temperature [C]
   * @return map of pressure to wax weight fraction
   */
  public Map<Double, Double> calculateAtMultiplePressures(double[] pressuresBara,
      double temperatureC) {
    Map<Double, Double> results = new LinkedHashMap<Double, Double>();

    for (double pressure : pressuresBara) {
      try {
        SystemInterface tempFluid = fluid.clone();
        tempFluid.setTemperature(temperatureC + 273.15);
        tempFluid.setPressure(pressure);
        tempFluid.setMultiPhaseCheck(true);

        ThermodynamicOperations ops = new ThermodynamicOperations(tempFluid);
        ops.TPflash();

        double waxFraction = 0.0;
        for (int p = 0; p < tempFluid.getNumberOfPhases(); p++) {
          String phaseType = tempFluid.getPhase(p).getPhaseTypeName();
          if ("wax".equalsIgnoreCase(phaseType)) {
            double waxMoles = tempFluid.getPhase(p).getNumberOfMolesInPhase();
            double waxMW = tempFluid.getPhase(p).getMolarMass();
            double totalMass = 0.0;
            for (int q = 0; q < tempFluid.getNumberOfPhases(); q++) {
              totalMass += tempFluid.getPhase(q).getNumberOfMolesInPhase()
                  * tempFluid.getPhase(q).getMolarMass();
            }
            if (totalMass > 0) {
              waxFraction = (waxMoles * waxMW) / totalMass;
            }
            break;
          }
        }
        results.put(pressure, waxFraction);
      } catch (Exception e) {
        results.put(pressure, Double.NaN);
      }
    }

    return results;
  }

  // ============================================================================
  // Static Monotonicity Utility Methods
  // ============================================================================

  /**
   * Enforces monotonically non-decreasing values on an array.
   *
   * <p>
   * Each element is replaced with the running maximum. Use this for wax fractions that should
   * increase with decreasing temperature when temperatures are sorted descending.
   * </p>
   *
   * @param values array of values to enforce (modified in place)
   * @return number of corrections made
   */
  public static int enforceNonDecreasing(double[] values) {
    int corrections = 0;
    double maxSoFar = values.length > 0 ? values[0] : 0.0;

    for (int i = 1; i < values.length; i++) {
      if (values[i] < maxSoFar) {
        values[i] = maxSoFar;
        corrections++;
      } else {
        maxSoFar = values[i];
      }
    }
    return corrections;
  }

  /**
   * Enforces monotonically non-increasing values on an array.
   *
   * <p>
   * Each element is replaced with the running minimum. Use this for properties that should decrease
   * with a monotonic independent variable.
   * </p>
   *
   * @param values array of values to enforce (modified in place)
   * @return number of corrections made
   */
  public static int enforceNonIncreasing(double[] values) {
    int corrections = 0;
    double minSoFar = values.length > 0 ? values[0] : 0.0;

    for (int i = 1; i < values.length; i++) {
      if (values[i] > minSoFar) {
        values[i] = minSoFar;
        corrections++;
      } else {
        minSoFar = values[i];
      }
    }
    return corrections;
  }

  /**
   * Counts the number of monotonicity violations in an array.
   *
   * @param values the array to check
   * @param nonDecreasing true to check for non-decreasing, false for non-increasing
   * @return number of violations
   */
  public static int countMonotonicityViolations(double[] values, boolean nonDecreasing) {
    int violations = 0;
    for (int i = 1; i < values.length; i++) {
      if (nonDecreasing && values[i] < values[i - 1]) {
        violations++;
      } else if (!nonDecreasing && values[i] > values[i - 1]) {
        violations++;
      }
    }
    return violations;
  }

  // ============================================================================
  // Getters
  // ============================================================================

  /**
   * Gets the calculated temperatures in Celsius.
   *
   * @return temperature array [C]
   */
  public double[] getTemperaturesC() {
    return temperaturesC != null ? Arrays.copyOf(temperaturesC, temperaturesC.length) : null;
  }

  /**
   * Gets the raw (unprocessed) wax weight fractions.
   *
   * @return raw wax fraction array
   */
  public double[] getRawWaxFractions() {
    return rawWaxFractions != null ? Arrays.copyOf(rawWaxFractions, rawWaxFractions.length) : null;
  }

  /**
   * Gets the monotonicity-enforced wax weight fractions.
   *
   * @return enforced wax fraction array
   */
  public double[] getWaxWeightFractions() {
    return waxFractions != null ? Arrays.copyOf(waxFractions, waxFractions.length) : null;
  }

  /**
   * Gets the calculated Wax Appearance Temperature.
   *
   * @return WAT in Celsius, or NaN if not determined
   */
  public double getWaxAppearanceTemperatureC() {
    return watC;
  }

  /**
   * Gets the number of successful flash calculations.
   *
   * @return success count
   */
  public int getSuccessCount() {
    return successCount;
  }

  /**
   * Gets the number of failed flash calculations.
   *
   * @return fail count
   */
  public int getFailCount() {
    return failCount;
  }

  /**
   * Gets the number of monotonicity corrections applied.
   *
   * @return correction count
   */
  public int getMonotonicityCorrections() {
    return monotonicityCorrections;
  }

  /**
   * Gets the pressure used for calculations.
   *
   * @return pressure [bara]
   */
  public double getPressureBara() {
    return pressureBara;
  }
}
