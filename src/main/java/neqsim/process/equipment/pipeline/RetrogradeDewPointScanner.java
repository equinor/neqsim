package neqsim.process.equipment.pipeline;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Direct TP-flash search for the upper (retrograde) and lower hydrocarbon dew-point pressures and the cricondenbar of a
 * gas or gas-condensate composition.
 *
 * <p>
 * The search does not rely on a phase-envelope tracer. It scans pressure downward from a stated maximum with TP
 * flashes, brackets the first two-phase state and bisects on the phase count. It therefore returns the upper dew point
 * of the retrograde region, which is the value that controls whether a pipeline segment or a delivered gas is single
 * phase. The cricondenbar is the maximum of that upper dew pressure over temperature (coarse scan, then golden-section
 * refinement).
 * </p>
 *
 * <p>
 * All methods work on a clone of the supplied fluid, so the caller's fluid state is never changed. All pressures are
 * absolute in bara, all temperatures in kelvin. A result of {@code Double.NaN} means no two-phase state was found in
 * the searched range (for example above the cricondentherm).
 * </p>
 *
 * @author NeqSim development team
 * @version 1.0
 */
public final class RetrogradeDewPointScanner {
  /** Pressure scan step in bar. */
  private static final double SCAN_STEP_BAR = 1.0;
  /** Bisection tolerance in bar. */
  private static final double BISECTION_TOLERANCE_BAR = 1.0e-3;

  /** Utility class; not instantiated. */
  private RetrogradeDewPointScanner() {
  }

  /**
   * Tests whether the fluid is two-phase (both phase fractions positive) at the given state.
   *
   * @param fluid working fluid (mutated)
   * @param ops operations object bound to {@code fluid}
   * @param temperatureK temperature in kelvin
   * @param pressureBara pressure in bara
   * @return true when the TP flash returns two phases with positive phase fractions
   */
  static boolean isTwoPhase(SystemInterface fluid, ThermodynamicOperations ops, double temperatureK,
      double pressureBara) {
    fluid.setTemperature(temperatureK);
    fluid.setPressure(pressureBara);
    ops.TPflash();
    return fluid.getNumberOfPhases() > 1 && fluid.getBeta(0) > 1.0e-12 && fluid.getBeta(1) > 1.0e-12;
  }

  /**
   * Bisects between a two-phase and a single-phase pressure.
   *
   * @param fluid working fluid (mutated)
   * @param ops operations object bound to {@code fluid}
   * @param temperatureK temperature in kelvin
   * @param pressureTwoPhase a two-phase pressure in bara
   * @param pressureSinglePhase a single-phase pressure in bara
   * @return the phase-boundary pressure in bara
   */
  private static double bisect(SystemInterface fluid, ThermodynamicOperations ops, double temperatureK,
      double pressureTwoPhase, double pressureSinglePhase) {
    double pTwo = pressureTwoPhase;
    double pOne = pressureSinglePhase;
    for (int i = 0; i < 60 && Math.abs(pTwo - pOne) > BISECTION_TOLERANCE_BAR; i++) {
      double mid = 0.5 * (pTwo + pOne);
      if (isTwoPhase(fluid, ops, temperatureK, mid)) {
        pTwo = mid;
      } else {
        pOne = mid;
      }
    }
    return 0.5 * (pTwo + pOne);
  }

  /**
   * Returns the upper (retrograde) dew-point pressure of the fluid composition at a temperature.
   *
   * @param fluid fluid with the composition of interest (not modified)
   * @param temperatureK temperature in kelvin
   * @param maxPressureBara upper end of the pressure scan in bara; must be single phase
   * @return upper dew-point pressure in bara, or NaN if the fluid is single phase over the whole range or two-phase
   * already at {@code maxPressureBara}
   */
  public static double upperDewPointPressure(SystemInterface fluid, double temperatureK, double maxPressureBara) {
    SystemInterface work = fluid.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    return upperDewPointPressure(work, ops, temperatureK, maxPressureBara);
  }

  /**
   * Upper dew-point search on a caller-owned working fluid (no clone; the fluid is mutated).
   *
   * @param work working fluid with the composition of interest (mutated)
   * @param ops operations object bound to {@code work}
   * @param temperatureK temperature in kelvin
   * @param maxPressureBara upper end of the pressure scan in bara
   * @return upper dew-point pressure in bara, or NaN if none was found
   */
  static double upperDewPointPressure(SystemInterface work, ThermodynamicOperations ops, double temperatureK,
      double maxPressureBara) {
    if (isTwoPhase(work, ops, temperatureK, maxPressureBara)) {
      return Double.NaN;
    }
    double pHigh = maxPressureBara;
    int steps = (int) Math.floor((maxPressureBara - 1.0) / SCAN_STEP_BAR);
    for (int s = 1; s <= steps; s++) {
      double p = maxPressureBara - s * SCAN_STEP_BAR;
      if (isTwoPhase(work, ops, temperatureK, p)) {
        return bisect(work, ops, temperatureK, p, pHigh);
      }
      pHigh = p;
    }
    return Double.NaN;
  }

  /**
   * Returns the lower dew-point pressure of the fluid at a temperature (below the retrograde region).
   *
   * @param fluid fluid with the composition of interest (not modified)
   * @param temperatureK temperature in kelvin
   * @param upperDewPointBara the upper dew pressure at the same temperature, in bara
   * @return lower dew-point pressure in bara, or NaN if none was found above 0.5 bara
   */
  public static double lowerDewPointPressure(SystemInterface fluid, double temperatureK, double upperDewPointBara) {
    if (Double.isNaN(upperDewPointBara)) {
      return Double.NaN;
    }
    SystemInterface work = fluid.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    double pLow = 0.5;
    if (isTwoPhase(work, ops, temperatureK, pLow)) {
      return Double.NaN;
    }
    int steps = (int) Math.ceil((upperDewPointBara - 1.0) / 0.5);
    for (int s = 0; s < steps; s++) {
      double p = 1.0 + 0.5 * s;
      if (isTwoPhase(work, ops, temperatureK, p)) {
        return bisect(work, ops, temperatureK, p, pLow);
      }
      pLow = p;
    }
    return Double.NaN;
  }

  /**
   * Finds the cricondenbar by maximising the upper dew-point pressure over temperature.
   *
   * @param fluid fluid with the composition of interest (not modified)
   * @param minTemperatureK lower end of the temperature search in kelvin
   * @param maxTemperatureK upper end of the temperature search in kelvin
   * @param maxPressureBara upper end of the pressure scan in bara; must be above the cricondenbar
   * @return array {cricondenbar pressure in bara, temperature in kelvin}; NaN entries if no dew point exists
   */
  public static double[] cricondenbar(SystemInterface fluid, double minTemperatureK, double maxTemperatureK,
      double maxPressureBara) {
    SystemInterface work = fluid.clone();
    ThermodynamicOperations ops = new ThermodynamicOperations(work);
    double bestP = Double.NaN;
    double bestT = Double.NaN;
    double step = Math.max(1.0, (maxTemperatureK - minTemperatureK) / 16.0);
    int count = (int) Math.floor((maxTemperatureK - minTemperatureK) / step + 1.0e-9);
    for (int s = 0; s <= count; s++) {
      double t = minTemperatureK + s * step;
      double p = upperDewPointPressure(work, ops, t, maxPressureBara);
      if (!Double.isNaN(p) && (Double.isNaN(bestP) || p > bestP)) {
        bestP = p;
        bestT = t;
      }
    }
    if (Double.isNaN(bestP)) {
      return new double[] {Double.NaN, Double.NaN};
    }
    double a = Math.max(minTemperatureK, bestT - step);
    double b = Math.min(maxTemperatureK, bestT + step);
    double ceiling = bestP + 5.0;
    for (int i = 0; i < 25; i++) {
      double t1 = a + 0.381966 * (b - a);
      double t2 = a + 0.618034 * (b - a);
      double p1 = upperDewPointPressure(work, ops, t1, ceiling);
      double p2 = upperDewPointPressure(work, ops, t2, ceiling);
      if (Double.isNaN(p1)) {
        p1 = 0.0;
      }
      if (Double.isNaN(p2)) {
        p2 = 0.0;
      }
      if (p1 > p2) {
        b = t2;
      } else {
        a = t1;
      }
    }
    double tOpt = 0.5 * (a + b);
    double pOpt = upperDewPointPressure(work, ops, tOpt, ceiling);
    if (Double.isNaN(pOpt) || pOpt < bestP) {
      return new double[] {bestP, bestT};
    }
    return new double[] {pOpt, tOpt};
  }
}
