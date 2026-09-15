package neqsim.thermodynamicoperations.phaseenvelopeops.multicomponentenvelopeops;

import java.io.Serializable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Robust two-phase boundary search used to obtain the cricondenbar and cricondentherm without relying on envelope
 * continuation.
 *
 * <p>
 * {@link PTphaseEnvelope} reports the highest pressure and highest temperature it happens to visit while marching along
 * the saturation curve. When the continuation terminates early - which happens for gases with several trace heavy
 * components - the reported cricondenbar and cricondentherm are simply the endpoints of a partial trace, and nothing in
 * the result marks them as incomplete. For a rich natural gas this can under-report the cricondenbar by a factor of
 * two, which silently invalidates any dense-phase transport design built on it.
 * </p>
 *
 * <p>
 * This class instead brackets the two-phase region directly with flash calculations on a temperature-pressure grid and
 * refines each boundary by bisection. It is slower than continuation but does not depend on a path being traceable, so
 * it is suitable both as a primary calculation and as a cross-check of a continuation result via
 * {@link #continuationLooksTruncated(double, double)}.
 * </p>
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public class RobustPhaseEnvelope implements Serializable {
  /** Serialization version. */
  private static final long serialVersionUID = 1000L;

  /** Class logger. */
  private static final Logger logger = LogManager.getLogger(RobustPhaseEnvelope.class);

  /** Working copy of the fluid. */
  private final SystemInterface system;

  /** Flash operations bound to the working fluid. */
  private final transient ThermodynamicOperations ops;

  /** Lowest temperature scanned, in Kelvin. */
  private double minTemperature = 150.0;

  /** Highest temperature scanned, in Kelvin. */
  private double maxTemperature = 500.0;

  /** Lowest pressure scanned, in bara. */
  private double minPressure = 1.0;

  /** Highest pressure scanned, in bara. */
  private double maxPressure = 400.0;

  /** Number of temperature grid points. */
  private int temperatureSteps = 60;

  /** Number of pressure grid points per temperature. */
  private int pressureSteps = 60;

  /** Bisection iterations used to refine each boundary point. */
  private int refinementIterations = 24;

  /** Cricondenbar temperature in Kelvin. */
  private double cricondenbarTemperature = Double.NaN;

  /** Cricondenbar pressure in bara. */
  private double cricondenbarPressure = Double.NaN;

  /** Cricondentherm temperature in Kelvin. */
  private double cricondenthermTemperature = Double.NaN;

  /** Cricondentherm pressure in bara. */
  private double cricondenthermPressure = Double.NaN;

  /** Upper (dew) boundary temperatures in Kelvin. */
  private double[] dewTemperatures = new double[0];

  /** Upper (dew) boundary pressures in bara. */
  private double[] dewPressures = new double[0];

  /** Lower boundary temperatures in Kelvin. */
  private double[] lowerTemperatures = new double[0];

  /** Lower boundary pressures in bara. */
  private double[] lowerPressures = new double[0];

  /** Whether a two-phase region was found at all. */
  private boolean twoPhaseRegionFound = false;

  /**
   * Creates a boundary search for a fluid. The fluid is cloned, so the caller's system is not modified.
   *
   * @param system fluid to analyse; must contain at least one component
   */
  public RobustPhaseEnvelope(SystemInterface system) {
    if (system == null) {
      throw new IllegalArgumentException("system cannot be null");
    }
    this.system = system.clone();
    this.ops = new ThermodynamicOperations(this.system);
  }

  /**
   * Sets the temperature scan range.
   *
   * @param minTemperatureK lowest temperature in Kelvin, greater than zero
   * @param maxTemperatureK highest temperature in Kelvin, greater than minTemperatureK
   * @return this object
   */
  public RobustPhaseEnvelope setTemperatureRange(double minTemperatureK, double maxTemperatureK) {
    if (minTemperatureK <= 0.0 || maxTemperatureK <= minTemperatureK) {
      throw new IllegalArgumentException("require 0 < minTemperatureK < maxTemperatureK");
    }
    this.minTemperature = minTemperatureK;
    this.maxTemperature = maxTemperatureK;
    return this;
  }

  /**
   * Sets the pressure scan range.
   *
   * @param minPressureBara lowest pressure in bara, greater than zero
   * @param maxPressureBara highest pressure in bara, greater than minPressureBara
   * @return this object
   */
  public RobustPhaseEnvelope setPressureRange(double minPressureBara, double maxPressureBara) {
    if (minPressureBara <= 0.0 || maxPressureBara <= minPressureBara) {
      throw new IllegalArgumentException("require 0 < minPressureBara < maxPressureBara");
    }
    this.minPressure = minPressureBara;
    this.maxPressure = maxPressureBara;
    return this;
  }

  /**
   * Sets the grid resolution.
   *
   * @param temperaturePoints number of temperature points, at least 5
   * @param pressurePoints number of pressure points per temperature, at least 5
   * @return this object
   */
  public RobustPhaseEnvelope setResolution(int temperaturePoints, int pressurePoints) {
    if (temperaturePoints < 5 || pressurePoints < 5) {
      throw new IllegalArgumentException("resolution must be at least 5 points in each direction");
    }
    this.temperatureSteps = temperaturePoints;
    this.pressureSteps = pressurePoints;
    return this;
  }

  /**
   * Sets the number of bisection iterations used to refine each boundary point.
   *
   * @param iterations refinement iterations, at least 1
   * @return this object
   */
  public RobustPhaseEnvelope setRefinementIterations(int iterations) {
    if (iterations < 1) {
      throw new IllegalArgumentException("iterations must be at least 1");
    }
    this.refinementIterations = iterations;
    return this;
  }

  /**
   * Returns whether the fluid is two-phase at the given state.
   *
   * @param temperatureK temperature in Kelvin
   * @param pressureBara pressure in bara
   * @return true when the flash converges to more than one phase
   */
  private boolean isTwoPhase(double temperatureK, double pressureBara) {
    try {
      system.setTemperature(temperatureK);
      system.setPressure(pressureBara);
      ops.TPflash();
      return system.getNumberOfPhases() > 1;
    } catch (Exception ex) {
      logger.debug("flash failed at T={} K, P={} bara: {}", temperatureK, pressureBara, ex.getMessage());
      return false;
    }
  }

  /**
   * Refines a boundary pressure between a known two-phase and a known single-phase pressure.
   *
   * @param temperatureK temperature in Kelvin
   * @param twoPhasePressure pressure in bara known to be inside the two-phase region
   * @param singlePhasePressure pressure in bara known to be outside the two-phase region
   * @return refined boundary pressure in bara
   */
  private double refinePressure(double temperatureK, double twoPhasePressure, double singlePhasePressure) {
    double inside = twoPhasePressure;
    double outside = singlePhasePressure;
    for (int i = 0; i < refinementIterations; i++) {
      double mid = 0.5 * (inside + outside);
      if (isTwoPhase(temperatureK, mid)) {
        inside = mid;
      } else {
        outside = mid;
      }
    }
    return 0.5 * (inside + outside);
  }

  /**
   * Finds the upper two-phase boundary pressure at a temperature.
   *
   * @param temperatureK temperature in Kelvin
   * @return upper boundary pressure in bara, or NaN when the fluid is single-phase at every scanned pressure
   */
  private double upperBoundaryPressure(double temperatureK) {
    double step = (maxPressure - minPressure) / (pressureSteps - 1.0);
    double highestTwoPhase = Double.NaN;
    double lowestSinglePhaseAbove = Double.NaN;
    for (int i = 0; i < pressureSteps; i++) {
      double pressure = minPressure + i * step;
      if (isTwoPhase(temperatureK, pressure)) {
        highestTwoPhase = pressure;
        lowestSinglePhaseAbove = Double.NaN;
      } else if (!Double.isNaN(highestTwoPhase) && Double.isNaN(lowestSinglePhaseAbove)) {
        lowestSinglePhaseAbove = pressure;
      }
    }
    if (Double.isNaN(highestTwoPhase)) {
      return Double.NaN;
    }
    if (Double.isNaN(lowestSinglePhaseAbove)) {
      return highestTwoPhase;
    }
    return refinePressure(temperatureK, highestTwoPhase, lowestSinglePhaseAbove);
  }

  /**
   * Finds the lower two-phase boundary pressure at a temperature.
   *
   * @param temperatureK temperature in Kelvin
   * @return lower boundary pressure in bara, or NaN when the fluid is single-phase at every scanned pressure
   */
  private double lowerBoundaryPressure(double temperatureK) {
    double step = (maxPressure - minPressure) / (pressureSteps - 1.0);
    double lowestTwoPhase = Double.NaN;
    double highestSinglePhaseBelow = Double.NaN;
    for (int i = 0; i < pressureSteps; i++) {
      double pressure = minPressure + i * step;
      if (isTwoPhase(temperatureK, pressure)) {
        lowestTwoPhase = pressure;
        break;
      }
      highestSinglePhaseBelow = pressure;
    }
    if (Double.isNaN(lowestTwoPhase)) {
      return Double.NaN;
    }
    if (Double.isNaN(highestSinglePhaseBelow)) {
      return lowestTwoPhase;
    }
    return refinePressure(temperatureK, lowestTwoPhase, highestSinglePhaseBelow);
  }

  /**
   * Runs the boundary search.
   *
   * @return this object
   */
  public RobustPhaseEnvelope calculate() {
    double step = (maxTemperature - minTemperature) / (temperatureSteps - 1.0);
    double[] tUpper = new double[temperatureSteps];
    double[] pUpper = new double[temperatureSteps];
    double[] tLower = new double[temperatureSteps];
    double[] pLower = new double[temperatureSteps];
    int nUpper = 0;
    int nLower = 0;
    double bestPressure = Double.NEGATIVE_INFINITY;
    double bestPressureTemperature = Double.NaN;
    double highestTwoPhaseTemperature = Double.NaN;
    double highestTwoPhaseTemperaturePressure = Double.NaN;
    double lowestSinglePhaseTemperatureAbove = Double.NaN;

    for (int i = 0; i < temperatureSteps; i++) {
      double temperature = minTemperature + i * step;
      double upper = upperBoundaryPressure(temperature);
      if (Double.isNaN(upper)) {
        if (!Double.isNaN(highestTwoPhaseTemperature) && Double.isNaN(lowestSinglePhaseTemperatureAbove)) {
          lowestSinglePhaseTemperatureAbove = temperature;
        }
        continue;
      }
      twoPhaseRegionFound = true;
      tUpper[nUpper] = temperature;
      pUpper[nUpper] = upper;
      nUpper++;
      double lower = lowerBoundaryPressure(temperature);
      if (!Double.isNaN(lower)) {
        tLower[nLower] = temperature;
        pLower[nLower] = lower;
        nLower++;
      }
      if (upper > bestPressure) {
        bestPressure = upper;
        bestPressureTemperature = temperature;
      }
      highestTwoPhaseTemperature = temperature;
      highestTwoPhaseTemperaturePressure = upper;
      lowestSinglePhaseTemperatureAbove = Double.NaN;
    }

    if (!twoPhaseRegionFound) {
      logger.warn("no two-phase region found in T=[{}, {}] K, P=[{}, {}] bara", minTemperature, maxTemperature,
          minPressure, maxPressure);
      return this;
    }

    dewTemperatures = copyOf(tUpper, nUpper);
    dewPressures = copyOf(pUpper, nUpper);
    lowerTemperatures = copyOf(tLower, nLower);
    lowerPressures = copyOf(pLower, nLower);

    refineCricondenbar(bestPressureTemperature, step);
    refineCricondentherm(highestTwoPhaseTemperature, highestTwoPhaseTemperaturePressure,
        lowestSinglePhaseTemperatureAbove);
    return this;
  }

  /**
   * Refines the cricondenbar by resampling the upper boundary around the coarse maximum.
   *
   * @param coarseTemperature temperature in Kelvin of the coarse maximum
   * @param coarseStep temperature grid spacing in Kelvin
   */
  private void refineCricondenbar(double coarseTemperature, double coarseStep) {
    double low = Math.max(minTemperature, coarseTemperature - coarseStep);
    double high = Math.min(maxTemperature, coarseTemperature + coarseStep);
    double bestTemperature = coarseTemperature;
    double bestPressure = upperBoundaryPressure(coarseTemperature);
    int samples = 12;
    for (int i = 0; i <= samples; i++) {
      double temperature = low + (high - low) * i / samples;
      double pressure = upperBoundaryPressure(temperature);
      if (!Double.isNaN(pressure) && pressure > bestPressure) {
        bestPressure = pressure;
        bestTemperature = temperature;
      }
    }
    cricondenbarTemperature = bestTemperature;
    cricondenbarPressure = bestPressure;
  }

  /**
   * Refines the cricondentherm by bisecting in temperature between the highest two-phase temperature and the first
   * single-phase temperature above it.
   *
   * @param highestTwoPhaseTemperature highest temperature in Kelvin with a two-phase window
   * @param pressureAtHighest upper boundary pressure in bara at that temperature
   * @param firstSinglePhaseTemperature first scanned temperature in Kelvin without a two-phase window, or NaN when the
   * scan ended inside the two-phase region
   */
  private void refineCricondentherm(double highestTwoPhaseTemperature, double pressureAtHighest,
      double firstSinglePhaseTemperature) {
    if (Double.isNaN(firstSinglePhaseTemperature)) {
      cricondenthermTemperature = highestTwoPhaseTemperature;
      cricondenthermPressure = pressureAtHighest;
      logger.warn("two-phase region extends to the top of the temperature scan ({} K); "
          + "widen the range for a valid cricondentherm", maxTemperature);
      return;
    }
    double inside = highestTwoPhaseTemperature;
    double outside = firstSinglePhaseTemperature;
    double insidePressure = pressureAtHighest;
    for (int i = 0; i < refinementIterations; i++) {
      double mid = 0.5 * (inside + outside);
      double pressure = upperBoundaryPressure(mid);
      if (Double.isNaN(pressure)) {
        outside = mid;
      } else {
        inside = mid;
        insidePressure = pressure;
      }
    }
    cricondenthermTemperature = inside;
    cricondenthermPressure = insidePressure;
  }

  /**
   * Copies the first n elements of an array.
   *
   * @param source source array
   * @param n number of elements to copy
   * @return new array of length n
   */
  private static double[] copyOf(double[] source, int n) {
    double[] out = new double[n];
    System.arraycopy(source, 0, out, 0, n);
    return out;
  }

  /**
   * Compares a continuation-derived cricondenbar with this calculation.
   *
   * <p>
   * A continuation trace that terminated early reports the endpoint of a partial curve. Because that endpoint can only
   * under-report the true maximum pressure, a continuation value materially below this calculation indicates
   * truncation.
   * </p>
   *
   * @param continuationCricondenbarBara cricondenbar in bara reported by envelope continuation
   * @param relativeTolerance allowed relative shortfall, for example 0.05 for five percent
   * @return true when the continuation value is below the robust value by more than the tolerance
   */
  public boolean continuationLooksTruncated(double continuationCricondenbarBara, double relativeTolerance) {
    if (!isValid()) {
      return false;
    }
    double shortfall = (cricondenbarPressure - continuationCricondenbarBara) / cricondenbarPressure;
    return shortfall > relativeTolerance;
  }

  /**
   * Returns whether a usable two-phase boundary was found.
   *
   * @return true when the cricondenbar and cricondentherm are finite
   */
  public boolean isValid() {
    return twoPhaseRegionFound && Double.isFinite(cricondenbarPressure) && Double.isFinite(cricondenthermTemperature);
  }

  /**
   * Returns the cricondenbar pressure.
   *
   * @return cricondenbar pressure in bara, NaN when no two-phase region was found
   */
  public double getCricondenbarPressure() {
    return cricondenbarPressure;
  }

  /**
   * Returns the temperature at the cricondenbar.
   *
   * @return temperature in Kelvin, NaN when no two-phase region was found
   */
  public double getCricondenbarTemperature() {
    return cricondenbarTemperature;
  }

  /**
   * Returns the cricondentherm temperature.
   *
   * @return cricondentherm temperature in Kelvin, NaN when no two-phase region was found
   */
  public double getCricondenthermTemperature() {
    return cricondenthermTemperature;
  }

  /**
   * Returns the pressure at the cricondentherm.
   *
   * @return pressure in bara, NaN when no two-phase region was found
   */
  public double getCricondenthermPressure() {
    return cricondenthermPressure;
  }

  /**
   * Returns the upper two-phase boundary temperatures.
   *
   * @return temperatures in Kelvin
   */
  public double[] getDewTemperatures() {
    return dewTemperatures.clone();
  }

  /**
   * Returns the upper two-phase boundary pressures.
   *
   * @return pressures in bara
   */
  public double[] getDewPressures() {
    return dewPressures.clone();
  }

  /**
   * Returns the lower two-phase boundary temperatures.
   *
   * @return temperatures in Kelvin
   */
  public double[] getLowerBoundaryTemperatures() {
    return lowerTemperatures.clone();
  }

  /**
   * Returns the lower two-phase boundary pressures.
   *
   * @return pressures in bara
   */
  public double[] getLowerBoundaryPressures() {
    return lowerPressures.clone();
  }

  /**
   * Returns a JSON summary of the calculation.
   *
   * @return JSON string
   */
  public String toJson() {
    StringBuilder builder = new StringBuilder();
    builder.append("{\"valid\":").append(isValid());
    builder.append(",\"cricondenbarPressureBara\":").append(cricondenbarPressure);
    builder.append(",\"cricondenbarTemperatureK\":").append(cricondenbarTemperature);
    builder.append(",\"cricondenthermTemperatureK\":").append(cricondenthermTemperature);
    builder.append(",\"cricondenthermPressureBara\":").append(cricondenthermPressure);
    builder.append(",\"boundaryPoints\":").append(dewPressures.length);
    builder.append("}");
    return builder.toString();
  }
}
