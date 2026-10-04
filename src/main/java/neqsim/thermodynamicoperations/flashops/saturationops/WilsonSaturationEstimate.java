package neqsim.thermodynamicoperations.flashops.saturationops;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/** Initial estimates only; saturation results must still satisfy the EOS equilibrium checks. */
final class WilsonSaturationEstimate {
  private static final double TRACE_MOLE_FRACTION = 1e-20;

  private WilsonSaturationEstimate() {
  }

  /**
   * Estimate a nontrivial starting temperature; the EOS still determines the final equilibrium.
   *
   * @param system fluid to estimate
   * @param bubble true for a bubble point, false for a dew point
   * @return Wilson saturation estimate in kelvin, or NaN when no valid bracket is available
   */
  static double temperature(SystemInterface system, boolean bubble) {
    double low = Double.POSITIVE_INFINITY;
    double high = 0.0;
    double pressure = system.getPressure();
    if (!Double.isFinite(pressure) || pressure <= 0.0) {
      return Double.NaN;
    }
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      ComponentInterface component = system.getPhase(0).getComponent(i);
      if (component.getz() <= TRACE_MOLE_FRACTION) {
        continue;
      }
      if (component.isIsIon() || component.getTC() <= 0.0 || component.getPC() <= 0.0
          || !Double.isFinite(component.getTC()) || !Double.isFinite(component.getPC())
          || !Double.isFinite(component.getAcentricFactor()) || component.getAcentricFactor() <= -1.0) {
        return Double.NaN;
      }
      low = Math.min(low, 0.1 * component.getTC());
      high = Math.max(high, 2.0 * component.getTC());
    }
    if (!(low < high) || !(phaseSum(system, low, bubble) < 1.0) || !(phaseSum(system, high, bubble) > 1.0)) {
      return Double.NaN;
    }
    for (int iteration = 0; iteration < 80; iteration++) {
      double middle = 0.5 * (low + high);
      if (phaseSum(system, middle, bubble) > 1.0) {
        high = middle;
      } else {
        low = middle;
      }
    }
    return 0.5 * (low + high);
  }

  /**
   * Evaluate the incipient-vapor sum using Wilson K values.
   *
   * @param system fluid to estimate
   * @param bubble true for a bubble point
   * @param temperature trial temperature in kelvin
   * @return vapor sum or reciprocal liquid sum
   */
  private static double phaseSum(SystemInterface system, double temperature, boolean bubble) {
    double sum = 0.0;
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      ComponentInterface component = system.getPhase(0).getComponent(i);
      if (component.getz() > TRACE_MOLE_FRACTION) {
        double k = component.getPC() / system.getPressure()
            * Math.exp(5.373 * (1.0 + component.getAcentricFactor()) * (1.0 - component.getTC() / temperature));
        sum += bubble ? component.getz() * k : component.getz() / k;
      }
    }
    return bubble ? sum : 1.0 / sum;
  }

  /**
   * Estimate bubble pressure using Wilson K values.
   *
   * @param system fluid to estimate
   * @return pressure in bara, or NaN for invalid component data
   */
  static double bubblePressure(SystemInterface system) {
    double pressure = 0.0;
    for (int i = 0; i < system.getNumberOfComponents(); i++) {
      ComponentInterface component = system.getPhase(0).getComponent(i);
      if (component.getz() <= TRACE_MOLE_FRACTION) {
        continue;
      }
      if (component.isIsIon() || component.getTC() <= 0.0 || component.getPC() <= 0.0) {
        return Double.NaN;
      }
      pressure += component.getz() * component.getPC() * Math
          .exp(5.373 * (1.0 + component.getAcentricFactor()) * (1.0 - component.getTC() / system.getTemperature()));
    }
    return pressure;
  }
}
