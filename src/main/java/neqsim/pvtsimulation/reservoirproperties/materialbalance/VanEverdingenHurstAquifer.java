package neqsim.pvtsimulation.reservoirproperties.materialbalance;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

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
 * For an infinite-acting radial aquifer the constant-terminal-rate dimensionless pressure and its time derivative are
 * integrated from the positive radial diffusion spectrum. Positive log-frequency quadrature with bounded endpoint
 * truncation avoids pressure jumps and finite-difference artifacts at small times. No EOS or history parameters are
 * fitted by this numerical kernel.
 * </p>
 *
 * <p>
 * For a bounded (no-flow outer boundary) aquifer of dimensionless outer radius $r_{eD}$ the solution transitions to
 * pseudo-steady state; this is approximated as the maximum of the infinite-acting value and the pseudo-steady value
 * $P_D = 2 t_D/(r_{eD}^2 - 1) + \ln r_{eD} - 3/4$. This legacy bounded policy is not a qualified finite-annulus
 * transient solution: it can have a nonzero right limit at time zero and an inaccurate finite-radius intercept. Its
 * derivative selects the active branch; it does not establish bounded radial accuracy. The infinite-acting correction
 * does not resolve that separate physical-domain limitation.
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

  /** Prevents construction of the static aquifer utility. */
  private VanEverdingenHurstAquifer() {
    // Utility class
  }

  /**
   * Constant-terminal-rate dimensionless pressure for a radial aquifer.
   *
   * @param tD finite nonnegative dimensionless time; zero is the bookkeeping origin
   * @param reD dimensionless outer radius; use {@code Double.POSITIVE_INFINITY} or a non-positive value for an
   * infinite-acting aquifer
   * @return dimensionless pressure $P_D$
   * @throws IllegalArgumentException if time is negative or non-finite, or the radius argument is NaN
   */
  public static double dimensionlessPressure(double tD, double reD) {
    return influence(tD, reD)[0];
  }

  /**
   * Infinite-acting constant-terminal-rate dimensionless pressure from the radial diffusion spectrum.
   *
   * @param tD finite nonnegative dimensionless time; zero is the bookkeeping origin
   * @return dimensionless pressure $P_D$
   */
  static double infiniteActingPd(double tD) {
    return RadialInfiniteInfluence.evaluate(tD)[0];
  }

  /**
   * Time derivative of the selected dimensionless pressure branch, $dP_D/dt_D$.
   *
   * <p>
   * Uses the analytic derivative of the same infinite spectral approximation, or the active legacy bounded branch. At a
   * bounded-policy crossing the derivative of the selected branch is returned; that approximate maximum is not
   * differentiable at a genuine kink. Time zero returns zero as a bookkeeping convention, not as its physical
   * right-derivative limit. Used by the Carter-Tracy influx method.
   * </p>
   *
   * @param tD finite nonnegative dimensionless time; zero is the bookkeeping origin
   * @param reD dimensionless outer radius (infinite if non-positive or infinite)
   * @return derivative $dP_D/dt_D$
   * @throws IllegalArgumentException if time is negative or non-finite, or the radius argument is NaN
   */
  public static double dimensionlessPressureDerivative(double tD, double reD) {
    return influence(tD, reD)[1];
  }

  /**
   * Evaluates pressure and derivative together, preserving the explicitly unqualified bounded maximum policy.
   *
   * @param time finite nonnegative dimensionless time
   * @param radius legacy dimensionless outer-radius argument
   * @return private pressure/derivative pair
   */
  private static double[] influence(double time, double radius) {
    if (Double.isNaN(radius)) {
      throw new IllegalArgumentException("Aquifer outer-radius argument must not be NaN");
    }
    double[] result = RadialInfiniteInfluence.evaluate(time);
    if (time == 0.0 || radius <= 0.0 || Double.isInfinite(radius)) {
      return result;
    }
    double denominator = radius * radius - 1.0;
    if (denominator > 1.0e-30) {
      double bounded = 2.0 * time / denominator + Math.log(radius) - 0.75;
      if (bounded > result[0]) {
        result[0] = bounded;
        result[1] = 2.0 / denominator;
      }
    }
    return result;
  }

  /**
   * Dimensionless time for a radial aquifer.
   *
   * <p>
   * $$ t_D = \frac{k\,t}{\phi\,\mu\,c_t\,r_i^2} $$
   * </p>
   *
   * @param permeability finite nonnegative aquifer permeability (m^2); zero gives zero time
   * @param time finite nonnegative elapsed time (s); zero gives zero dimensionless time
   * @param porosity finite porosity in (0, 1]
   * @param viscosity finite positive water viscosity (Pa*s)
   * @param totalCompressibility finite positive total aquifer compressibility (1/Pa), not 1/bar
   * @param reservoirRadius finite positive reservoir/aquifer inner radius (m), not outer radius
   * @return dimensionless time $t_D$
   * @throws IllegalArgumentException if a physical input is outside its stated domain
   * @throws ArithmeticException if a strictly positive result overflows or underflows to zero
   */
  public static double dimensionlessTime(double permeability, double time, double porosity, double viscosity,
      double totalCompressibility, double reservoirRadius) {
    if (!Double.isFinite(permeability) || permeability < 0 || !Double.isFinite(time) || time < 0
        || !Double.isFinite(porosity) || porosity <= 0 || porosity > 1 || !Double.isFinite(viscosity) || viscosity <= 0
        || !Double.isFinite(totalCompressibility) || totalCompressibility <= 0 || !Double.isFinite(reservoirRadius)
        || reservoirRadius <= 0) {
      throw new IllegalArgumentException(
          "Aquifer time requires finite nonnegative permeability/time, porosity in (0,1],"
              + " and positive viscosity, compressibility (1/Pa) and inner radius");
    }
    if (permeability == 0 || time == 0) {
      return 0;
    }
    double numerator = permeability * time;
    double first = porosity * viscosity;
    double second = first * totalCompressibility;
    double third = second * reservoirRadius;
    double denominator = third * reservoirRadius;
    double result = numerator / denominator;
    if (normalPositive(numerator) && normalPositive(first) && normalPositive(second) && normalPositive(third)
        && normalPositive(denominator) && normalPositive(result) && result < 0.5 * Double.MAX_VALUE) {
      return result;
    }
    return scaledPhysicalRatio(new double[] {permeability, time},
        new double[] {porosity, viscosity, totalCompressibility, reservoirRadius, reservoirRadius});
  }

  /**
   * Van Everdingen-Hurst aquifer constant.
   *
   * <p>
   * $$ U = 2\pi\,f\,\phi\,c_t\,h\,r_i^2 $$
   * </p>
   *
   * <p>
   * with the encroachment fraction $f = \theta/360$. With compressibility supplied in 1/bar the constant has units of
   * reservoir m$^3$ per bar.
   * </p>
   *
   * @param porosity finite porosity in [0, 1]; zero gives zero support
   * @param totalCompressibility finite nonnegative total aquifer compressibility (1/bar), not 1/Pa
   * @param thickness finite nonnegative aquifer thickness (m)
   * @param reservoirRadius finite positive reservoir/aquifer inner radius (m), not outer radius
   * @param encroachmentAngleDeg finite encroachment angle in [0, 360] degrees
   * @return aquifer constant $U$ (reservoir m^3 per bar)
   * @throws IllegalArgumentException if a physical input is outside its stated domain
   * @throws ArithmeticException if a strictly positive result overflows or underflows to zero
   */
  public static double aquiferConstant(double porosity, double totalCompressibility, double thickness,
      double reservoirRadius, double encroachmentAngleDeg) {
    if (!Double.isFinite(porosity) || porosity < 0 || porosity > 1 || !Double.isFinite(totalCompressibility)
        || totalCompressibility < 0 || !Double.isFinite(thickness) || thickness < 0 || !Double.isFinite(reservoirRadius)
        || reservoirRadius <= 0 || !Double.isFinite(encroachmentAngleDeg) || encroachmentAngleDeg < 0
        || encroachmentAngleDeg > 360) {
      throw new IllegalArgumentException(
          "Aquifer storage requires finite porosity in [0,1], nonnegative compressibility"
              + " (1/bar)/thickness, positive inner radius and angle in [0,360]");
    }
    if (porosity == 0 || totalCompressibility == 0 || thickness == 0 || encroachmentAngleDeg == 0) {
      return 0;
    }
    double fraction = encroachmentAngleDeg / 360.0;
    double first = 2.0 * Math.PI * fraction;
    double second = first * porosity;
    double third = second * totalCompressibility;
    double fourth = third * thickness;
    double fifth = fourth * reservoirRadius;
    double result = fifth * reservoirRadius;
    if (normalPositive(fraction) && normalPositive(first) && normalPositive(second) && normalPositive(third)
        && normalPositive(fourth) && normalPositive(fifth) && normalPositive(result)
        && result < 0.5 * Double.MAX_VALUE) {
      return result;
    }
    return scaledPhysicalRatio(new double[] {2.0 * Math.PI, encroachmentAngleDeg, porosity, totalCompressibility,
        thickness, reservoirRadius, reservoirRadius}, new double[] {360.0});
  }

  /**
   * Identifies safe ordinary intermediate products without changing their evaluation order.
   *
   * @param value computed positive intermediate
   * @return whether the value is finite and normal
   */
  private static boolean normalPositive(double value) {
    return Double.isFinite(value) && value >= Double.MIN_NORMAL;
  }

  /**
   * Scales a physical conversion only when its ordinary evaluation risks intermediate exponent loss.
   *
   * <p>
   * Callers validate positive finite factors and handle physical zeros first. At most seven numerator or five
   * denominator factors keep the normalized mantissa representable even for subnormal inputs. The Carter-Tracy
   * recurrence does not call this helper or allocate these factor arrays.
   * </p>
   *
   * @param numerator strictly positive finite numerator factors
   * @param denominator strictly positive finite denominator factors
   * @return positive finite rounded conversion, including representable subnormal results
   * @throws ArithmeticException if the final positive conversion is unrepresentable
   */
  private static double scaledPhysicalRatio(double[] numerator, double[] denominator) {
    double mantissa = 1;
    int exponent = 0;
    for (double factor : numerator) {
      int power = Math.getExponent(factor);
      mantissa *= Math.scalb(factor, -power);
      exponent += power;
    }
    for (double factor : denominator) {
      int power = Math.getExponent(factor);
      mantissa /= Math.scalb(factor, -power);
      exponent -= power;
    }
    double result = Math.scalb(mantissa, exponent);
    if (result <= Double.MIN_NORMAL || result >= 0.5 * Double.MAX_VALUE || !Double.isFinite(result)) {
      result = roundedPhysicalRatio(numerator, denominator);
    }
    if (!Double.isFinite(result) || result == 0) {
      throw new ArithmeticException("Positive aquifer physical conversion is unrepresentable");
    }
    return result;
  }

  /**
   * Resolves final representability near the subnormal and overflow boundaries from exact binary factors.
   *
   * <p>
   * Floating scaled mantissas alone can round a value across the half-minimum or overflow threshold. Exact products and
   * integer division in units of the local binary ULP resolve remainder and tie parity without an approximate decimal
   * quotient. Rounded counts are at most 2^53, exactly representable as doubles. This rare path is not used for
   * ordinary conversions or Carter-Tracy steps.
   * </p>
   *
   * @param numerator strictly positive finite numerator factors
   * @param denominator strictly positive finite denominator factors
   * @return correctly classified final double conversion
   */
  private static double roundedPhysicalRatio(double[] numerator, double[] denominator) {
    BigDecimal top = BigDecimal.ONE;
    BigDecimal bottom = BigDecimal.ONE;
    for (double factor : numerator) {
      top = top.multiply(new BigDecimal(factor));
    }
    for (double factor : denominator) {
      bottom = bottom.multiply(new BigDecimal(factor));
    }
    BigDecimal overflow = new BigDecimal(Double.MAX_VALUE).add(new BigDecimal(Math.scalb(1.0, 970)));
    if (top.compareTo(bottom.multiply(overflow)) >= 0) {
      return Double.POSITIVE_INFINITY;
    }
    int shift;
    if (top.compareTo(bottom.multiply(new BigDecimal(2.0 * Double.MIN_NORMAL))) < 0) {
      shift = -1074;
    } else if (top.compareTo(bottom.multiply(new BigDecimal(Math.scalb(1.0, 1023)))) >= 0) {
      shift = 971;
    } else {
      shift = 970;
    }
    BigDecimal quantum = new BigDecimal(Math.scalb(1.0, shift));
    BigDecimal units = top.divide(bottom.multiply(quantum), 0, RoundingMode.HALF_EVEN);
    return Math.scalb(units.doubleValue(), shift);
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
   * where $\Delta p_n = p_i - p_n$ is the total pressure drop at the reservoir-aquifer boundary. Evaluation is
   * normalized by positive pressure and uses exponent-scaled products, without an absolute small-denominator freeze.
   * The existing projection to nonnegative cumulative influx is retained: negative total drops may decrease influx to
   * zero, but do not create a negative cumulative water credit. This is not a water-mixing or recharge model. The
   * bounded-radius influence remains the explicitly unqualified legacy policy described above.
   * </p>
   *
   * @param tD nonempty finite nonnegative dimensionless times, strictly increasing; first entry may be zero
   * @param deltaP finite signed total pressure drops $p_i - p$ at each time (bar), same length as times
   * @param aquiferConstant finite nonnegative aquifer constant $U$ (reservoir m^3 per bar); zero gives zero influx
   * @param reD dimensionless outer radius (infinite if non-positive or infinite)
   * @return array of cumulative water influx at each time (reservoir m^3), initially zero
   * @throws IllegalArgumentException if history shape, ordering, finiteness, support or radius arguments are invalid
   * @throws ArithmeticException if the selected influence or a transition cannot be represented finitely
   */
  public static double[] cumulativeInfluxCarterTracy(double[] tD, double[] deltaP, double aquiferConstant, double reD) {
    if (tD == null || deltaP == null || tD.length != deltaP.length || tD.length == 0) {
      throw new IllegalArgumentException("tD and deltaP must be nonempty and of equal length");
    }
    if (!Double.isFinite(aquiferConstant) || aquiferConstant < 0.0 || Double.isNaN(reD)) {
      throw new IllegalArgumentException("Aquifer constant must be finite and nonnegative; radius must not be NaN");
    }
    for (int i = 0; i < tD.length; i++) {
      if (!Double.isFinite(tD[i]) || tD[i] < 0.0 || (i > 0 && tD[i] <= tD[i - 1]) || !Double.isFinite(deltaP[i])) {
        throw new IllegalArgumentException("Times must be finite, nonnegative and strictly increasing; drops finite");
      }
    }
    double[] we = new double[tD.length];
    if (aquiferConstant == 0.0) {
      return we;
    }
    for (int i = 1; i < tD.length; i++) {
      double[] response = influence(tD[i], reD);
      double pd = response[0];
      double pdPrime = response[1];
      if (!Double.isFinite(pd) || pd <= 0.0 || !Double.isFinite(pdPrime)) {
        throw new ArithmeticException("Carter-Tracy influence is not finite and positive at index " + i);
      }
      // Normalize the SAME recurrence by P. Multiplying time by its derivative first avoids tiny-time overflow.
      double elasticity = (tD[i] * pdPrime) / pd;
      double denominator = 1.0 - (tD[i - 1] / tD[i]) * elasticity;
      double retained = (1.0 - elasticity) / denominator;
      if (denominator == 0.0 || !Double.isFinite(retained)) {
        throw new ArithmeticException("Carter-Tracy transition is singular or unrepresentable at index " + i);
      }
      double next = retained * we[i - 1]
          + scaledProductRatio(tD[i] - tD[i - 1], aquiferConstant, deltaP[i], pd, denominator);
      // Preserve the existing nonnegative cumulative-influx projection, including pressure recovery.
      if (next < 0.0) {
        next = 0.0;
      }
      if (!Double.isFinite(next)) {
        throw new ArithmeticException("Carter-Tracy cumulative influx is unrepresentable at index " + i);
      }
      we[i] = next;
    }
    return we;
  }

  /**
   * Evaluates three numerator and two denominator factors without avoidable intermediate exponent loss.
   *
   * @param first finite first factor
   * @param second finite second factor
   * @param third finite third factor
   * @param pressure finite positive pressure denominator
   * @param normalizedDenominator finite nonzero normalized transition denominator
   * @return scaled quotient; underflow/overflow occurs only at final rescaling
   */
  private static double scaledProductRatio(double first, double second, double third, double pressure,
      double normalizedDenominator) {
    if (first == 0.0 || second == 0.0 || third == 0.0) {
      return 0.0;
    }
    int firstExponent = Math.getExponent(first);
    int secondExponent = Math.getExponent(second);
    int thirdExponent = Math.getExponent(third);
    int pressureExponent = Math.getExponent(pressure);
    int denominatorExponent = Math.getExponent(normalizedDenominator);
    double mantissa = Math.scalb(first, -firstExponent) * Math.scalb(second, -secondExponent)
        * Math.scalb(third, -thirdExponent) / Math.scalb(pressure, -pressureExponent)
        / Math.scalb(normalizedDenominator, -denominatorExponent);
    return Math.scalb(mantissa,
        firstExponent + secondExponent + thirdExponent - pressureExponent - denominatorExponent);
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
