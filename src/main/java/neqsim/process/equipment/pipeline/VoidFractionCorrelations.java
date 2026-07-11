package neqsim.process.equipment.pipeline;

/**
 * Two-phase void-fraction (gas holdup) correlations.
 *
 * <p>
 * Provides drift-flux style correlations for the in-situ gas void fraction $\alpha$ (and hence liquid holdup $H_L = 1 -
 * \alpha$) from superficial velocities and fluid properties. These are used by multiphase pipe models such as
 * {@link PipeGray} as an alternative holdup closure.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class VoidFractionCorrelations {

  /** Acceleration of gravity in m/s2. */
  private static final double GRAVITY = 9.81;

  private VoidFractionCorrelations() {
    // Utility class
  }

  /**
   * Woldesemayat and Ghajar (2007) void-fraction correlation.
   *
   * <p>
   * A flow-pattern-independent drift-flux correlation valid for horizontal to vertical pipes:
   * </p>
   *
   * <p>
   * $$ \alpha = \frac{v_{sg}}{v_{sg}\left[1 + \left(\frac{v_{sl}}{v_{sg}}\right)^{(\rho_g/\rho_l)^{0.1}}\right] +
   * 2.9\left[\frac{gD\sigma(1+\cos\theta)(\rho_l-\rho_g)}{\rho_l^2}\right]^{0.25}(1.22 + 1.22\sin\theta)^{P_{atm}/P}}
   * $$
   * </p>
   *
   * @param vsg superficial gas velocity (m/s), positive
   * @param vsl superficial liquid velocity (m/s), non-negative
   * @param rhoL liquid density (kg/m3)
   * @param rhoG gas density (kg/m3)
   * @param sigma gas-liquid interfacial tension (N/m)
   * @param diameter pipe inner diameter (m)
   * @param inclinationDeg pipe inclination from horizontal (degrees; 90 for a vertical well)
   * @param pressureBara system pressure (bara)
   * @return gas void fraction $\alpha$ in the range (0, 1)
   */
  public static double woldesemayatGhajar(double vsg, double vsl, double rhoL, double rhoG, double sigma,
      double diameter, double inclinationDeg, double pressureBara) {
    if (vsg <= 0.0) {
      return 0.0;
    }
    if (vsl <= 0.0) {
      return 1.0;
    }
    double theta = Math.toRadians(inclinationDeg);
    double velRatio = vsl / vsg;
    double densExp = Math.pow(rhoG / rhoL, 0.1);
    double distribution = vsg * (1.0 + Math.pow(velRatio, densExp));

    double driftGroup = GRAVITY * diameter * sigma * (1.0 + Math.cos(theta)) * (rhoL - rhoG) / (rhoL * rhoL);
    if (driftGroup < 0.0) {
      driftGroup = 0.0;
    }
    double pAtm = 1.01325;
    double driftVelocity = 2.9 * Math.pow(driftGroup, 0.25)
        * Math.pow(1.22 + 1.22 * Math.sin(theta), pAtm / pressureBara);

    double alpha = vsg / (distribution + driftVelocity);
    if (alpha < 0.0) {
      alpha = 0.0;
    }
    if (alpha > 1.0) {
      alpha = 1.0;
    }
    return alpha;
  }
}
