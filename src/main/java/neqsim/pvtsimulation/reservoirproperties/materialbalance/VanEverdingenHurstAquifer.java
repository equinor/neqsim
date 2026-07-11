package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.Serializable;

/**
 * Van Everdingen-Hurst radial aquifer influence functions and water-influx calculation.
 *
 * <p>
 * Implements the constant-terminal-rate dimensionless pressure solution $P_D(t_D)$ of Van Everdingen and Hurst (1949)
 * for radial aquifers, together with the Carter-Tracy (1960) cumulative water-influx method that uses $P_D$ and its
 * time derivative. These influence functions can be exported as an ECLIPSE {@code AQUTAB} include table and provide the
 * cumulative influx $W_e$ consumed by {@link GasMaterialBalance} and {@link OilMaterialBalance}.
 * </p>
 *
 * <h2>Dimensionless pressure</h2>
 *
 * <p>
 * For an infinite-acting radial aquifer the constant-terminal-rate dimensionless pressure is evaluated with the
 * rational polynomial approximations of Edwardson et al. (1962):
 * </p>
 *
 * <ul>
 * <li>$t_D &lt; 0.01$: $P_D = 2\sqrt{t_D/\pi}$</li>
 * <li>$0.01 \le t_D \le 200$: eight-coefficient rational polynomial in $\sqrt{t_D}$</li>
 * <li>$t_D &gt; 200$: semilog line $P_D = \tfrac{1}{2}(\ln t_D + 0.80907)$</li>
 * </ul>
 *
 * <p>
 * For a bounded (no-flow outer boundary) aquifer of dimensionless outer radius $r_{eD}$ the solution transitions to
 * pseudo-steady state; this is approximated as the maximum of the infinite-acting value and the pseudo-steady value
 * $P_D = 2 t_D/(r_{eD}^2 - 1) + \ln r_{eD} - 3/4$.
 * </p>
 *
 * <h2>Units</h2>
 *
 * <p>
 * SI units are used throughout: permeability in m$^2$, time in seconds, viscosity in Pa·s, compressibility in 1/Pa,
 * radius and thickness in m, porosity as a fraction, pressures in bar (for the influx $\Delta p$) and the aquifer
 * constant in reservoir m$^3$ per bar. Dimensionless time and pressure are unitless.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 */
public final class VanEverdingenHurstAquifer implements Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1L;

  private VanEverdingenHurstAquifer() {
    // Utility class
  }

  /**
   * Constant-terminal-rate dimensionless pressure for a radial aquifer.
   *
   * @param tD dimensionless time (must be positive)
   * @param reD dimensionless outer radius; use {@code Double.POSITIVE_INFINITY} or a non-positive value for an
   * infinite-acting aquifer
   * @return dimensionless pressure $P_D$
   */
  public static double dimensionlessPressure(double tD, double reD) {
    if (tD <= 0.0) {
      return 0.0;
    }
    double pdInfinite = infiniteActingPd(tD);
    if (reD <= 0.0 || Double.isInfinite(reD)) {
      return pdInfinite;
    }
    // Bounded aquifer: pseudo-steady-state dimensionless pressure.
    double denom = reD * reD - 1.0;
    if (denom <= 1.0e-30) {
      return pdInfinite;
    }
    double pdPss = 2.0 * tD / denom + Math.log(reD) - 0.75;
    // Infinite-acting until the boundary is felt, then pseudo-steady state dominates.
    return Math.max(pdInfinite, pdPss);
  }

  /**
   * Infinite-acting constant-terminal-rate dimensionless pressure (Edwardson et al., 1962).
   *
   * @param tD dimensionless time (must be positive)
   * @return dimensionless pressure $P_D$
   */
  static double infiniteActingPd(double tD) {
    if (tD <= 0.0) {
      return 0.0;
    }
    if (tD < 0.01) {
      return 2.0 * Math.sqrt(tD / Math.PI);
    }
    if (tD <= 200.0) {
      double sqrtT = Math.sqrt(tD);
      double numerator = 370.529 * sqrtT + 137.582 * tD + 5.69549 * tD * sqrtT;
      double denominator = 328.834 + 265.488 * sqrtT + 45.2157 * tD + tD * sqrtT;
      return numerator / denominator;
    }
    // Late-time infinite-acting semilog approximation.
    return 0.5 * (Math.log(tD) + 0.80907);
  }

  /**
   * Time derivative of the infinite-acting dimensionless pressure, $dP_D/dt_D$.
   *
   * <p>
   * Evaluated numerically with a central difference; used by the Carter-Tracy influx method.
   * </p>
   *
   * @param tD dimensionless time (must be positive)
   * @param reD dimensionless outer radius (infinite if non-positive or infinite)
   * @return derivative $dP_D/dt_D$
   */
  public static double dimensionlessPressureDerivative(double tD, double reD) {
    if (tD <= 0.0) {
      return 0.0;
    }
    double h = Math.max(1.0e-4, tD * 1.0e-4);
    double forward = dimensionlessPressure(tD + h, reD);
    double backward = dimensionlessPressure(Math.max(1.0e-12, tD - h), reD);
    return (forward - backward) / (2.0 * h);
  }

  /**
   * Dimensionless time for a radial aquifer.
   *
   * <p>
   * $$ t_D = \frac{k\,t}{\phi\,\mu\,c_t\,r_e^2} $$
   * </p>
   *
   * @param permeability aquifer permeability (m^2)
   * @param time time (s)
   * @param porosity porosity (fraction)
   * @param viscosity water viscosity (Pa*s)
   * @param totalCompressibility total aquifer compressibility (1/Pa)
   * @param reservoirRadius equivalent reservoir/aquifer inner radius (m)
   * @return dimensionless time $t_D$
   */
  public static double dimensionlessTime(double permeability, double time, double porosity, double viscosity,
      double totalCompressibility, double reservoirRadius) {
    double denom = porosity * viscosity * totalCompressibility * reservoirRadius * reservoirRadius;
    if (denom <= 0.0) {
      throw new IllegalArgumentException("porosity, viscosity, compressibility and radius must be positive");
    }
    return permeability * time / denom;
  }

  /**
   * Van Everdingen-Hurst aquifer constant.
   *
   * <p>
   * $$ U = 2\pi\,f\,\phi\,c_t\,h\,r_e^2 $$
   * </p>
   *
   * <p>
   * with the encroachment fraction $f = \theta/360$. With compressibility supplied in 1/bar the constant has units of
   * reservoir m$^3$ per bar.
   * </p>
   *
   * @param porosity porosity (fraction)
   * @param totalCompressibility total aquifer compressibility (1/bar)
   * @param thickness aquifer thickness (m)
   * @param reservoirRadius equivalent reservoir/aquifer inner radius (m)
   * @param encroachmentAngleDeg encroachment angle (degrees, 0-360)
   * @return aquifer constant $U$ (reservoir m^3 per bar)
   */
  public static double aquiferConstant(double porosity, double totalCompressibility, double thickness,
      double reservoirRadius, double encroachmentAngleDeg) {
    double f = encroachmentAngleDeg / 360.0;
    return 2.0 * Math.PI * f * porosity * totalCompressibility * thickness * reservoirRadius * reservoirRadius;
  }

  /**
   * Cumulative water influx history by the Carter-Tracy (1960) method.
   *
   * <p>
   * Marches the aquifer influx forward without superposition using the recurrence
   * </p>
   *
   * <p>
   * $$ W_{e,n} = W_{e,n-1} + (t_{Dn} - t_{Dn-1})\, \frac{U\,\Delta p_n - W_{e,n-1}\,P_D'(t_{Dn})}{P_D(t_{Dn}) -
   * t_{Dn-1} \,P_D'(t_{Dn})} $$
   * </p>
   *
   * <p>
   * where $\Delta p_n = p_i - p_n$ is the total pressure drop at the reservoir-aquifer boundary.
   * </p>
   *
   * @param tD array of dimensionless times (monotonically increasing, first entry may be 0)
   * @param deltaP array of total pressure drops $p_i - p$ at each time (bar)
   * @param aquiferConstant Van Everdingen-Hurst aquifer constant $U$ (reservoir m^3 per bar)
   * @param reD dimensionless outer radius (infinite if non-positive or infinite)
   * @return array of cumulative water influx at each time (reservoir m^3)
   */
  public static double[] cumulativeInfluxCarterTracy(double[] tD, double[] deltaP, double aquiferConstant, double reD) {
    if (tD == null || deltaP == null || tD.length != deltaP.length) {
      throw new IllegalArgumentException("tD and deltaP must be non-null and of equal length");
    }
    int n = tD.length;
    double[] we = new double[n];
    we[0] = 0.0;
    for (int i = 1; i < n; i++) {
      double pd = dimensionlessPressure(tD[i], reD);
      double pdPrime = dimensionlessPressureDerivative(tD[i], reD);
      double denom = pd - tD[i - 1] * pdPrime;
      if (Math.abs(denom) < 1.0e-30) {
        we[i] = we[i - 1];
        continue;
      }
      double increment = (tD[i] - tD[i - 1]) * (aquiferConstant * deltaP[i] - we[i - 1] * pdPrime) / denom;
      we[i] = we[i - 1] + increment;
      if (we[i] < 0.0) {
        we[i] = 0.0;
      }
    }
    return we;
  }

  /**
   * Build an influence table of dimensionless pressure versus dimensionless time.
   *
   * @param tD array of dimensionless times
   * @param reD dimensionless outer radius (infinite if non-positive or infinite)
   * @return array of dimensionless pressures aligned with {@code tD}
   */
  public static double[] influenceTable(double[] tD, double reD) {
    if (tD == null) {
      throw new IllegalArgumentException("tD must not be null");
    }
    double[] pd = new double[tD.length];
    for (int i = 0; i < tD.length; i++) {
      pd[i] = dimensionlessPressure(tD[i], reD);
    }
    return pd;
  }

  /**
   * Export an ECLIPSE {@code AQUTAB} include file body for a Carter-Tracy aquifer influence table.
   *
   * <p>
   * The returned text contains the {@code AQUTAB} keyword followed by a single table of ($t_D$, $P_D$) rows terminated
   * with a slash, suitable for inclusion in an ECLIPSE data deck.
   * </p>
   *
   * @param tD array of dimensionless times (should start above zero)
   * @param reD dimensionless outer radius (infinite if non-positive or infinite)
   * @return ECLIPSE {@code AQUTAB} include-file text
   */
  public static String exportAqutab(double[] tD, double reD) {
    if (tD == null || tD.length == 0) {
      throw new IllegalArgumentException("tD must be non-null and non-empty");
    }
    StringBuilder sb = new StringBuilder();
    sb.append("AQUTAB\n");
    sb.append("-- Constant-terminal-rate influence function (Van Everdingen-Hurst)\n");
    sb.append("--    tD            PD\n");
    for (int i = 0; i < tD.length; i++) {
      double pd = dimensionlessPressure(tD[i], reD);
      sb.append(String.format(java.util.Locale.ROOT, "  %12.6f  %12.6f%n", tD[i], pd));
    }
    sb.append("/\n");
    return sb.toString();
  }
}
