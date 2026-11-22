package neqsim.process.fielddevelopment.integrated;

import java.io.Serializable;

/**
 * Rawlins-Schellhardt gas-well deliverability {@code q = C (pr^2 - pwh^2)^n} fitted from field rows.
 *
 * <p>
 * The relation lumps inflow and tubing between reservoir and wellhead, which is what a wellhead-pressure history
 * contains. Fit it only on rows where the well is on stream with the choke wide open, otherwise the rate reflects the
 * choke setting rather than deliverability. Check the fit on a later hold-out period before trusting a forecast; an
 * exponent {@code n} between 0.5 and 1.0 is physical.
 * </p>
 *
 * @author NeqSim
 * @version 1.0
 * @see WellDeliverabilityCurve#fromRawlinsSchellhardt(RawlinsSchellhardtFit, double, double, int)
 */
public final class RawlinsSchellhardtFit implements Serializable {
  private static final long serialVersionUID = 1000L;
  private static final double P_ATM = 1.01325;

  private final double coefficient;
  private final double exponent;

  /**
   * Creates a deliverability law.
   *
   * @param coefficient C, in rate units per bar^(2n)
   * @param exponent n, 0.5 to 1.0
   */
  public RawlinsSchellhardtFit(double coefficient, double exponent) {
    if (coefficient <= 0.0 || exponent <= 0.0) {
      throw new IllegalArgumentException("coefficient and exponent must be positive");
    }
    this.coefficient = coefficient;
    this.exponent = exponent;
  }

  /**
   * Fits C and n by least squares on log(q) versus log(pr^2 - pwh^2).
   *
   * @param reservoirPressureBara reservoir pressure in bara for each row
   * @param wellheadPressureBarg flowing wellhead pressure in barg for each row
   * @param rate surface rate for each row
   * @return the fitted law
   */
  public static RawlinsSchellhardtFit fit(double[] reservoirPressureBara, double[] wellheadPressureBarg,
      double[] rate) {
    int count = 0;
    double sx = 0.0;
    double sy = 0.0;
    double sxx = 0.0;
    double sxy = 0.0;
    for (int i = 0; i < rate.length; i++) {
      double dp2 = reservoirPressureBara[i] * reservoirPressureBara[i]
          - (wellheadPressureBarg[i] + P_ATM) * (wellheadPressureBarg[i] + P_ATM);
      if (dp2 > 0.0 && rate[i] > 0.0) {
        double x = Math.log(dp2);
        double y = Math.log(rate[i]);
        sx += x;
        sy += y;
        sxx += x * x;
        sxy += x * y;
        count++;
      }
    }
    if (count < 3) {
      throw new IllegalArgumentException("need at least three rows with positive drawdown and rate");
    }
    double n = (count * sxy - sx * sy) / (count * sxx - sx * sx);
    double lnC = (sy - n * sx) / count;
    return new RawlinsSchellhardtFit(Math.exp(lnC), n);
  }

  /**
   * Returns the coefficient C.
   *
   * @return coefficient
   */
  public double getCoefficient() {
    return coefficient;
  }

  /**
   * Returns the exponent n.
   *
   * @return exponent
   */
  public double getExponent() {
    return exponent;
  }

  /**
   * Rate at given reservoir and wellhead pressures.
   *
   * @param reservoirPressureBara reservoir pressure in bara
   * @param wellheadPressureBarg flowing wellhead pressure in barg
   * @return rate in the unit of C (zero when there is no drawdown)
   */
  public double rate(double reservoirPressureBara, double wellheadPressureBarg) {
    double dp2 = reservoirPressureBara * reservoirPressureBara
        - (wellheadPressureBarg + P_ATM) * (wellheadPressureBarg + P_ATM);
    return dp2 <= 0.0 ? 0.0 : coefficient * Math.pow(dp2, exponent);
  }

  /**
   * Flowing wellhead pressure needed to deliver a given rate.
   *
   * @param reservoirPressureBara reservoir pressure in bara
   * @param rate target rate in the unit of C
   * @return wellhead pressure in barg (may be negative if the rate is not reachable)
   */
  public double wellheadPressureFor(double reservoirPressureBara, double rate) {
    double dp2 = Math.pow(rate / coefficient, 1.0 / exponent);
    double v = reservoirPressureBara * reservoirPressureBara - dp2;
    return Math.sqrt(Math.max(v, 0.0)) - P_ATM;
  }
}
