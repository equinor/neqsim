package neqsim.process.equipment.separator.entrainment;

import java.io.Serializable;

/**
 * Calculates terminal settling velocity for droplets/bubbles using the Schiller-Naumann drag
 * correlation, which covers the full range from Stokes (creeping) flow through the intermediate
 * regime to the Newton (turbulent) regime.
 *
 * <p>
 * The Schiller-Naumann correlation provides a smooth transition across flow regimes (Schiller and
 * Naumann, 1935):
 * </p>
 *
 * $$ C_D = \frac{24}{Re}\left(1 + 0.15 Re^{0.687}\right) \quad \text{for } Re &lt; 1000 $$
 *
 * $$ C_D = 0.44 \quad \text{for } Re \geq 1000 $$
 *
 * <p>
 * The settling velocity is found by iterating the force balance:
 * </p>
 *
 * $$ v_t = \sqrt{\frac{4 g d_p |\Delta\rho|}{3 C_D \rho_c}} $$
 *
 * <p>
 * For the Stokes regime ($Re \ll 1$), this reduces to:
 * </p>
 *
 * $$ v_t = \frac{d_p^2 |\Delta\rho| g}{18 \mu_c} $$
 *
 * <p>
 * References: Schiller, L. and Naumann, Z. (1935), "A drag coefficient correlation", <i>Zeitschrift
 * des Vereines Deutscher Ingenieure</i>, 77, 318-320. Clift, R., Grace, J.R., and Weber, M.E.
 * (1978), <i>Bubbles, Drops and Particles</i>, Academic Press, New York.
 * </p>
 *
 * @author NeqSim team
 * @version 1.0
 */
public class DropletSettlingCalculator implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000L;

  /** Gravitational acceleration [m/s2]. */
  private static final double G = 9.81;

  /** Maximum iterations for velocity convergence. */
  private static final int MAX_ITERATIONS = 50;

  /** Convergence tolerance for velocity iteration. */
  private static final double TOLERANCE = 1e-6;

  /**
   * Calculates the terminal settling velocity of a single droplet or bubble using the
   * Schiller-Naumann drag correlation.
   *
   * <p>
   * Positive velocity means downward (settling), applicable when dispersed phase is heavier.
   * Negative velocity means upward (buoyant rise), applicable for bubbles in liquid.
   * </p>
   *
   * @param dropletDiameter diameter of the droplet or bubble [m]
   * @param continuousDensity density of the continuous phase [kg/m3]
   * @param dispersedDensity density of the dispersed phase [kg/m3]
   * @param continuousViscosity dynamic viscosity of the continuous phase [Pa.s]
   * @return terminal settling velocity [m/s], positive = downward, negative = upward
   */
  public static double calcTerminalVelocity(double dropletDiameter, double continuousDensity,
      double dispersedDensity, double continuousViscosity) {
    if (dropletDiameter <= 0 || continuousDensity <= 0 || continuousViscosity <= 0) {
      return 0.0;
    }

    double deltaRho = Math.abs(dispersedDensity - continuousDensity);
    if (deltaRho < 1e-6) {
      return 0.0;
    }

    // Start with Stokes velocity as initial guess
    double vStokes =
        dropletDiameter * dropletDiameter * deltaRho * G / (18.0 * continuousViscosity);
    double velocity = vStokes;

    // Iterate to converge on velocity (needed for intermediate/Newton regime)
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double re = calcReynolds(dropletDiameter, velocity, continuousDensity, continuousViscosity);
      double cd = calcDragCoefficient(re);
      double vNew =
          Math.sqrt(4.0 * G * dropletDiameter * deltaRho / (3.0 * cd * continuousDensity));

      if (Math.abs(vNew - velocity) / (velocity + 1e-30) < TOLERANCE) {
        velocity = vNew;
        break;
      }
      velocity = 0.5 * (velocity + vNew); // Under-relaxation for stability
    }

    // Sign convention: positive = settling (dispersed heavier), negative = rising (dispersed
    // lighter)
    return (dispersedDensity > continuousDensity) ? velocity : -velocity;
  }

  /**
   * Calculates the drag coefficient using the Schiller-Naumann correlation.
   *
   * @param re particle Reynolds number
   * @return drag coefficient C_D
   */
  public static double calcDragCoefficient(double re) {
    if (re <= 0.0) {
      return 1e10; // Very large to give zero velocity
    }
    if (re < 1000.0) {
      return (24.0 / re) * (1.0 + 0.15 * Math.pow(re, 0.687));
    } else {
      return 0.44; // Newton regime
    }
  }

  /**
   * Calculates the particle Reynolds number.
   *
   * @param diameter droplet diameter [m]
   * @param velocity settling velocity [m/s]
   * @param continuousDensity density of continuous phase [kg/m3]
   * @param continuousViscosity dynamic viscosity of continuous phase [Pa.s]
   * @return Reynolds number
   */
  public static double calcReynolds(double diameter, double velocity, double continuousDensity,
      double continuousViscosity) {
    if (continuousViscosity <= 0.0) {
      return 0.0;
    }
    return continuousDensity * Math.abs(velocity) * diameter / continuousViscosity;
  }

  /**
   * Calculates the critical (cut) diameter for gravity separation in a given geometry.
   *
   * <p>
   * This is the smallest droplet that can settle across the available height within the available
   * residence time. In the Stokes regime:
   * </p>
   *
   * $$ d_{cut} = \sqrt{\frac{18 \mu_c H}{|\Delta\rho| g t_{res}}} $$
   *
   * <p>
   * For higher Reynolds numbers, the critical diameter is found iteratively.
   * </p>
   *
   * @param availableHeight height available for settling [m]
   * @param residenceTime gas or liquid residence time in the section [s]
   * @param continuousDensity density of continuous phase [kg/m3]
   * @param dispersedDensity density of dispersed phase [kg/m3]
   * @param continuousViscosity dynamic viscosity of continuous phase [Pa.s]
   * @return critical (cut) diameter [m]
   */
  public static double calcCriticalDiameter(double availableHeight, double residenceTime,
      double continuousDensity, double dispersedDensity, double continuousViscosity) {
    if (residenceTime <= 0 || availableHeight <= 0) {
      return Double.MAX_VALUE;
    }
    double requiredVelocity = availableHeight / residenceTime;
    double deltaRho = Math.abs(dispersedDensity - continuousDensity);
    if (deltaRho < 1e-6) {
      return Double.MAX_VALUE;
    }

    // Stokes estimate as initial guess
    double dCut = Math.sqrt(18.0 * continuousViscosity * requiredVelocity / (deltaRho * G));

    // Iterate for non-Stokes regime
    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double vt = Math.abs(
          calcTerminalVelocity(dCut, continuousDensity, dispersedDensity, continuousViscosity));
      if (vt <= 0) {
        break;
      }
      double ratio = requiredVelocity / vt;
      // Adjust diameter: in Stokes regime, v ~ d^2, so d ~ sqrt(ratio) * d
      double dNew = dCut * Math.sqrt(ratio);
      if (Math.abs(dNew - dCut) / (dCut + 1e-30) < TOLERANCE) {
        dCut = dNew;
        break;
      }
      dCut = 0.5 * (dCut + dNew);
    }
    return dCut;
  }
}
