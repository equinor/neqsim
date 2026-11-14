package neqsim.thermo.util.gerg;

import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

/** Safeguarded high-density root finder for multiparameter reference equations of state. */
final class ReferenceEosLiquidDensitySolver {
  private static final double MIN_DENSITY = 0.02;
  private static final double MAX_DENSITY = 60.0;
  private static final double SEARCH_FACTOR = 0.9;
  private static final int MAX_SEARCH_ITERATIONS = 80;
  private static final int MAX_BISECTION_ITERATIONS = 60;
  private static final double CHEBYSHEV_NODE_OFFSET = Math.sqrt(0.5);

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
   * @param pressureFunction pressure in kPa as a function of molar density in mol/L
   * @return liquid molar density in mol/L, or {@link Double#NaN} when no separate liquid root exists
   */
  static double solve(double targetPressure, DoubleUnaryOperator pressureFunction) {
    return solve(targetPressure, pressureFunction, null);
  }

  /**
   * Finds a high-density pressure root and, where the local pressure curve permits, uses a Chebyshev proxy and
   * exact-EOS Newton correction to shorten refinement.
   *
   * @param targetPressure target pressure in kPa
   * @param pressureFunction pressure in kPa as a function of molar density in mol/L
   * @param pressureSlope derivative at the density most recently evaluated by {@code pressureFunction}
   * @return liquid molar density in mol/L, or {@link Double#NaN} when the scan cannot bracket a root
   */
  static double solve(double targetPressure, DoubleUnaryOperator pressureFunction, DoubleSupplier pressureSlope) {
    double upperDensity = MAX_DENSITY;
    double upperResidual = residual(targetPressure, upperDensity, pressureFunction);
    double upperSlope = pressureSlope == null ? Double.NaN : pressureSlope.getAsDouble();
    for (int iteration = 0; iteration < MAX_SEARCH_ITERATIONS; iteration++) {
      double lowerDensity = upperDensity * SEARCH_FACTOR;
      if (lowerDensity < MIN_DENSITY) {
        break;
      }
      double lowerResidual = residual(targetPressure, lowerDensity, pressureFunction);
      double lowerSlope = pressureSlope == null ? Double.NaN : pressureSlope.getAsDouble();
      if (isBracket(lowerResidual, upperResidual)) {
        if (pressureSlope != null) {
          double density = chebyshevRoot(targetPressure, pressureFunction, pressureSlope, lowerDensity, upperDensity,
              lowerResidual, upperResidual);
          if (Double.isFinite(density)) {
            return density;
          }
        }
        return bisect(targetPressure, lowerDensity, upperDensity, pressureFunction);
      }
      // A negative-to-positive slope interval can hide two crossings even if both endpoint pressures are above target.
      if (lowerResidual > 0.0 && upperResidual > 0.0 && lowerSlope < 0.0 && upperSlope > 0.0) {
        double recovered = recoverHiddenRoot(targetPressure, pressureFunction, pressureSlope, lowerDensity,
            upperDensity, lowerSlope, upperSlope);
        if (Double.isFinite(recovered)) {
          return recovered;
        }
      }
      upperDensity = lowerDensity;
      upperResidual = lowerResidual;
      upperSlope = lowerSlope;
    }
    return Double.NaN;
  }

  /**
   * Check for a pressure minimum between two above-target endpoints, using a quadratic Chebyshev proxy for the pressure
   * slope to seed a bracketed search. Any recovered density is checked against the exact EOS.
   *
   * @param targetPressure target pressure in kPa
   * @param pressureFunction exact pressure evaluator
   * @param pressureSlope derivative of the last exact pressure evaluation
   * @param lower lower bracket density in mol/L
   * @param upper upper bracket density in mol/L
   * @param lowerSlope slope at the lower density
   * @param upperSlope slope at the upper density
   * @return recovered positive-slope density root, or NaN
   */
  private static double recoverHiddenRoot(double targetPressure, DoubleUnaryOperator pressureFunction,
      DoubleSupplier pressureSlope, double lower, double upper, double lowerSlope, double upperSlope) {
    double center = 0.5 * (lower + upper);
    double halfWidth = 0.5 * (upper - lower);
    double atCenter = residual(targetPressure, center, pressureFunction);
    double centerSlope = pressureSlope.getAsDouble();
    double linear = 0.5 * (upperSlope - lowerSlope);
    double quadratic = 0.5 * (upperSlope + lowerSlope) - centerSlope;
    double discriminant = linear * linear - 4.0 * quadratic * centerSlope;
    double proxyZero = -2.0 * centerSlope / (linear + Math.sqrt(discriminant));
    double trial = Math.abs(proxyZero) < 1.0 ? center + halfWidth * proxyZero : center;

    double lo = lower;
    double hi = upper;
    for (int iteration = 0; iteration < 32; iteration++) {
      double atTrial = trial == center ? atCenter : residual(targetPressure, trial, pressureFunction);
      double slope = trial == center ? centerSlope : pressureSlope.getAsDouble();
      if (!Double.isFinite(atTrial) || !Double.isFinite(slope)) {
        return Double.NaN;
      }
      if (atTrial <= 0.0) {
        double density = bisect(targetPressure, trial, upper, pressureFunction);
        if (Double.isFinite(density)) {
          double closure = residual(targetPressure, density, pressureFunction);
          if (Math.abs(closure) <= pressureTolerance(targetPressure) && pressureSlope.getAsDouble() > 0.0) {
            return density;
          }
        }
        return Double.NaN;
      }
      if (slope < 0.0) {
        lo = trial;
      } else {
        hi = trial;
      }
      trial = 0.5 * (lo + hi);
      if (trial == lo || trial == hi) {
        break;
      }
    }
    return Double.NaN;
  }

  /**
   * Interpolate the residual on a bracket using five Chebyshev-Lobatto nodes. Require a monotone proxy, use it to seed
   * a safeguarded Newton solve, and accept only a positive-slope root of the original EOS.
   *
   * @param targetPressure target pressure in kPa
   * @param pressureFunction exact pressure evaluator
   * @param pressureSlope derivative of the last exact pressure evaluation
   * @param lower lower bracket density in mol/L
   * @param upper upper bracket density in mol/L
   * @param left exact residual at the lower endpoint
   * @param right exact residual at the upper endpoint
   * @return polished density, or NaN if the proxy or its refinement was unsuitable
   */
  private static double chebyshevRoot(double targetPressure, DoubleUnaryOperator pressureFunction,
      DoubleSupplier pressureSlope, double lower, double upper, double left, double right) {
    double middle = 0.5 * (lower + upper);
    double halfWidth = 0.5 * (upper - lower);
    double nodeOffset = CHEBYSHEV_NODE_OFFSET;
    double innerLeft = residual(targetPressure, middle - halfWidth * nodeOffset, pressureFunction);
    double atMiddle = residual(targetPressure, middle, pressureFunction);
    double innerRight = residual(targetPressure, middle + halfWidth * nodeOffset, pressureFunction);

    // Degree-four cosine transform with half weights at the endpoints.
    double evenEndpoints = 0.5 * (left + right);
    double oddEndpoints = 0.5 * (right - left);
    double innerSum = innerLeft + innerRight;
    double innerDifference = innerRight - innerLeft;
    double c0 = 0.25 * (evenEndpoints + innerSum + atMiddle);
    double c1 = 0.5 * (oddEndpoints + nodeOffset * innerDifference);
    double c2 = 0.5 * (evenEndpoints - atMiddle);
    double c3 = 0.5 * (oddEndpoints - nodeOffset * innerDifference);
    double c4 = 0.25 * (evenEndpoints - innerSum + atMiddle);
    // On [-1, 1], |T'_k| is bounded by k squared. This ensures one root of the proxy.
    if (!(c1 > 4.0 * Math.abs(c2) + 9.0 * Math.abs(c3) + 16.0 * Math.abs(c4))) {
      return Double.NaN;
    }

    double lo = -1.0;
    double hi = 1.0;
    for (int iteration = 0; iteration < 24; iteration++) {
      double x = 0.5 * (lo + hi);
      double x2 = x * x;
      double proxy = c0 + x * (c1 + c3 * (4.0 * x2 - 3.0)) + c2 * (2.0 * x2 - 1.0) + c4 * (8.0 * x2 * (x2 - 1.0) + 1.0);
      if (proxy <= 0.0) {
        lo = x;
      } else {
        hi = x;
      }
    }

    double density = middle + halfWidth * 0.5 * (lo + hi);
    for (int iteration = 0; iteration < 5; iteration++) {
      double value = residual(targetPressure, density, pressureFunction);
      double slope = pressureSlope.getAsDouble();
      if (!Double.isFinite(value) || !Double.isFinite(slope) || slope <= 0.0) {
        return Double.NaN;
      }
      if (Math.abs(value) <= pressureTolerance(targetPressure)) {
        return density;
      }
      if (value < 0.0) {
        lower = density;
      } else {
        upper = density;
      }
      density -= value / slope;
      if (!(density > lower && density < upper)) {
        return Double.NaN;
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

  /** Pressure convergence tolerance in kPa, kept below saturation VU closure tolerances. */
  private static double pressureTolerance(double targetPressure) {
    return Math.max(1.0e-9, Math.abs(targetPressure) * 1.0e-11);
  }
}
