package neqsim.thermo.util.gerg;

import java.util.function.DoubleUnaryOperator;

/** Safeguarded high-density root finder for multiparameter reference equations of state. */
final class ReferenceEosLiquidDensitySolver {
  private static final double MIN_DENSITY = 0.02;
  private static final double MAX_DENSITY = 60.0;
  private static final double SEARCH_FACTOR = 0.9;
  private static final int MAX_SEARCH_ITERATIONS = 80;
  private static final int MAX_BISECTION_ITERATIONS = 60;

  /** Utility class. */
  private ReferenceEosLiquidDensitySolver() {
  }

  /**
   * Find the highest mechanically stable density root at the requested pressure.
   *
   * <p>
   * The native GERG-style Newton solver can jump from a liquid initial estimate to the vapor root when the isotherm
   * passes through a mechanically unstable interval. Searching from high density downwards locates the first
   * positive-slope pressure crossing, after which bisection keeps every iterate inside the liquid-root bracket.
   * </p>
   *
   * @param targetPressure target pressure in kPa
   * @param initialDensity previous liquid molar density in mol/L, or {@link Double#NaN}
   * @param pressureFunction pressure in kPa as a function of molar density in mol/L
   * @return liquid molar density in mol/L, or {@link Double#NaN} when no separate liquid root exists
   */
  static double solve(double targetPressure, double initialDensity, DoubleUnaryOperator pressureFunction) {
    if (Double.isFinite(initialDensity) && initialDensity > MIN_DENSITY && initialDensity < MAX_DENSITY) {
      double warmRoot = bracketAroundInitialGuess(targetPressure, initialDensity, pressureFunction);
      if (Double.isFinite(warmRoot)) {
        return warmRoot;
      }
    }

    double upperDensity = MAX_DENSITY;
    double upperResidual = residual(targetPressure, upperDensity, pressureFunction);
    for (int iteration = 0; iteration < MAX_SEARCH_ITERATIONS; iteration++) {
      double lowerDensity = upperDensity * SEARCH_FACTOR;
      if (lowerDensity < MIN_DENSITY) {
        break;
      }
      double lowerResidual = residual(targetPressure, lowerDensity, pressureFunction);
      if (isBracket(lowerResidual, upperResidual)) {
        return bisect(targetPressure, lowerDensity, upperDensity, pressureFunction);
      }
      upperDensity = lowerDensity;
      upperResidual = lowerResidual;
    }
    return Double.NaN;
  }

  /** Try to bracket a changed state near the preceding liquid-density root. */
  private static double bracketAroundInitialGuess(double targetPressure, double initialDensity,
      DoubleUnaryOperator pressureFunction) {
    double residualAtGuess = residual(targetPressure, initialDensity, pressureFunction);
    if (!Double.isFinite(residualAtGuess)) {
      return Double.NaN;
    }
    if (Math.abs(residualAtGuess) <= pressureTolerance(targetPressure)) {
      return initialDensity;
    }

    double lowerDensity = initialDensity;
    double upperDensity = initialDensity;
    double lowerResidual = residualAtGuess;
    double upperResidual = residualAtGuess;
    for (int iteration = 0; iteration < 30; iteration++) {
      if (lowerResidual > 0.0 && lowerDensity > MIN_DENSITY) {
        lowerDensity = Math.max(MIN_DENSITY, lowerDensity * SEARCH_FACTOR);
        lowerResidual = residual(targetPressure, lowerDensity, pressureFunction);
      }
      if (upperResidual <= 0.0 && upperDensity < MAX_DENSITY) {
        upperDensity = Math.min(MAX_DENSITY, upperDensity / SEARCH_FACTOR);
        upperResidual = residual(targetPressure, upperDensity, pressureFunction);
      }
      if (isBracket(lowerResidual, upperResidual)) {
        return bisect(targetPressure, lowerDensity, upperDensity, pressureFunction);
      }
    }
    return Double.NaN;
  }

  /** Refine a positive-slope pressure crossing by bisection. */
  private static double bisect(double targetPressure, double lowerDensity, double upperDensity,
      DoubleUnaryOperator pressureFunction) {
    double lowerResidual = residual(targetPressure, lowerDensity, pressureFunction);
    for (int iteration = 0; iteration < MAX_BISECTION_ITERATIONS; iteration++) {
      double middleDensity = 0.5 * (lowerDensity + upperDensity);
      double middleResidual = residual(targetPressure, middleDensity, pressureFunction);
      if (!Double.isFinite(middleResidual)) {
        return Double.NaN;
      }
      if (Math.abs(middleResidual) <= pressureTolerance(targetPressure)) {
        return middleDensity;
      }
      if (middleResidual <= 0.0) {
        lowerDensity = middleDensity;
        lowerResidual = middleResidual;
      } else {
        upperDensity = middleDensity;
      }
    }
    double density = 0.5 * (lowerDensity + upperDensity);
    double finalResidual = residual(targetPressure, density, pressureFunction);
    return Double.isFinite(lowerResidual) && Math.abs(finalResidual) <= 10.0 * pressureTolerance(targetPressure)
        ? density
        : Double.NaN;
  }

  /** Calculate pressure residual at a trial density. */
  private static double residual(double targetPressure, double density, DoubleUnaryOperator pressureFunction) {
    return pressureFunction.applyAsDouble(density) - targetPressure;
  }

  /** Determine whether endpoints bracket a positive-slope root. */
  private static boolean isBracket(double lowerResidual, double upperResidual) {
    return Double.isFinite(lowerResidual) && Double.isFinite(upperResidual) && lowerResidual <= 0.0
        && upperResidual > 0.0;
  }

  /** Pressure convergence tolerance in kPa. */
  private static double pressureTolerance(double targetPressure) {
    return Math.max(1.0e-6, Math.abs(targetPressure) * 1.0e-8);
  }
}
