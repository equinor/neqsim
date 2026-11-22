package neqsim.thermo.phase;

/**
 * Pressure bracket for distinct pure-fluid roots of the ordinary SRK and PR equations.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
public final class CubicPureSaturation {
  /** Prevents construction. */
  private CubicPureSaturation() {
  }

  /**
   * Evaluates the pressure separating two mechanically stable roots with equal fugacity.
   *
   * @param phase initialized ordinary cubic pure phase
   * @return saturation pressure in bara
   * @throws IllegalArgumentException if the phase is not an ordinary pure SRK or PR phase
   * @throws IllegalStateException if distinct subcritical roots cannot be bracketed
   */
  public static double pressure(PhaseEos phase) {
    if (phase.getNumberOfComponents() != 1
        || !(phase.getClass() == PhaseSrkEos.class || phase.getClass() == PhasePrEos.class)) {
      throw new IllegalArgumentException("Pure saturation requires an ordinary SRK or PR phase");
    }
    double n = phase.getNumberOfMolesInPhase();
    double a = phase.getA() / n / n;
    double b = phase.getB() / n;
    double rt = neqsim.thermo.ThermodynamicConstantsInterface.R * phase.getTemperature();
    double e = phase.delta1 + phase.delta2;
    double f = phase.delta1 * phase.delta2;
    double strength = a / (rt * b);
    // The minimum required attraction separates the two mechanical spinodals.
    double left = 1.0e-12;
    double right = 1.0 - 1.0e-12;
    for (int i = 0; i < 100; i++) {
      double u = left + (right - left) / 3.0;
      double v = right - (right - left) / 3.0;
      if (requiredAttraction(u, e, f) < requiredAttraction(v, e, f)) {
        right = v;
      } else {
        left = u;
      }
    }
    double center = (left + right) / 2.0;
    if (!Double.isFinite(strength) || strength <= requiredAttraction(center, e, f)) {
      throw new IllegalStateException("No distinct subcritical cubic roots");
    }
    double yGas = spinodal(1.0e-14, center, strength, e, f);
    double yLiquid = spinodal(center, 1.0 - 1.0e-14, strength, e, f);
    double maximum = pressureAt(yGas, strength, e, f) * rt / b;
    double minimum = pressureAt(yLiquid, strength, e, f) * rt / b;
    double inset = (maximum - minimum) * 1.0e-7;
    double low = minimum > 0.0 ? minimum + inset : maximum * 1.0e-18;
    double high = maximum - inset;
    double lowResidual = residual(phase, strength, b, rt, low, yGas, yLiquid);
    double highResidual = residual(phase, strength, b, rt, high, yGas, yLiquid);
    if (!(low > 0.0 && high > low && lowResidual * highResidual < 0.0)) {
      throw new IllegalStateException("Unable to bracket distinct-root cubic saturation");
    }
    for (int i = 0; i < 100; i++) {
      double mid = (low + high) / 2.0;
      double value = residual(phase, strength, b, rt, mid, yGas, yLiquid);
      if (value * lowResidual > 0.0) {
        low = mid;
        lowResidual = value;
      } else {
        high = mid;
      }
      if (high - low <= 1.0e-12 * mid) {
        return (low + high) / 2.0;
      }
    }
    throw new IllegalStateException("Pure cubic saturation did not converge");
  }

  /**
   * Returns the attraction needed for zero pressure slope at reduced density y=b/v.
   *
   * @param y reduced density
   * @param e sum of cubic shape constants
   * @param f product of cubic shape constants
   * @return dimensionless attraction
   */
  private static double requiredAttraction(double y, double e, double f) {
    double denominator = 1.0 + e * y + f * y * y;
    return denominator * denominator / ((1.0 - y) * (1.0 - y) * (2.0 * y + e * y * y));
  }

  /**
   * Bisects one spinodal on either side of the critical reduced density.
   *
   * @param low lower density
   * @param high upper density
   * @param strength dimensionless attraction
   * @param e sum of cubic shape constants
   * @param f product of cubic shape constants
   * @return spinodal reduced density
   */
  private static double spinodal(double low, double high, double strength, double e, double f) {
    double initial = requiredAttraction(low, e, f) - strength;
    for (int i = 0; i < 100; i++) {
      double mid = (low + high) / 2.0;
      // Adjacent representable endpoints cannot shrink further; the remaining sweeps are identical.
      if (mid == low || mid == high) {
        return mid;
      }
      if ((requiredAttraction(mid, e, f) - strength) * initial > 0.0) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return (low + high) / 2.0;
  }

  /**
   * Computes pressure scaled by RT/b.
   *
   * @param y reduced density
   * @param strength attraction scaled by RTb
   * @param e sum of cubic shape constants
   * @param f product of cubic shape constants
   * @return reduced pressure
   */
  private static double pressureAt(double y, double strength, double e, double f) {
    return y / (1.0 - y) - strength * y * y / (1.0 + e * y + f * y * y);
  }

  /**
   * Bisects a stable density branch; unlike Cardano roots this retains dilute liquid-root precision.
   *
   * @param low lower density
   * @param high upper density
   * @param target pressure scaled by RT/b
   * @param strength attraction scaled by RTb
   * @param e sum of cubic shape constants
   * @param f product of cubic shape constants
   * @return density on the selected stable branch
   */
  private static double density(double low, double high, double target, double strength, double e, double f) {
    for (int i = 0; i < 100; i++) {
      double mid = (low + high) / 2.0;
      // Adjacent representable endpoints cannot shrink further; the remaining sweeps are identical.
      if (mid == low || mid == high) {
        return mid;
      }
      if (pressureAt(mid, strength, e, f) < target) {
        low = mid;
      } else {
        high = mid;
      }
    }
    return (low + high) / 2.0;
  }

  /**
   * Compares fugacities on the two stable density branches without mutating a phase.
   *
   * @param phase ordinary cubic phase supplying shape constants
   * @param strength attraction scaled by RTb
   * @param b molar covolume
   * @param rt gas constant times temperature
   * @param pressure trial pressure
   * @param yGas gas spinodal density
   * @param yLiquid liquid spinodal density
   * @return log vapor fugacity minus log liquid fugacity
   */
  private static double residual(PhaseEos phase, double strength, double b, double rt, double pressure, double yGas,
      double yLiquid) {
    double bb = b * pressure / rt;
    double e = phase.delta1 + phase.delta2;
    double f = phase.delta1 * phase.delta2;
    double liquid = bb / density(yLiquid, 1.0 - 1.0e-14, bb, strength, e, f);
    double vapor = bb / density(0.0, yGas, bb, strength, e, f);
    if (!(liquid > bb && vapor - liquid > 1.0e-7 * vapor)) {
      throw new IllegalStateException("Saturation trial requires distinct physical cubic roots");
    }
    double value = vapor - liquid - Math.log((vapor - bb) / (liquid - bb))
        - strength / (phase.delta1 - phase.delta2) * Math.log((vapor + phase.delta1 * bb) * (liquid + phase.delta2 * bb)
            / ((vapor + phase.delta2 * bb) * (liquid + phase.delta1 * bb)));
    if (!Double.isFinite(value)) {
      throw new IllegalStateException("Nonfinite cubic saturation residual");
    }
    return value;
  }
}
