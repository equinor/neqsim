/*
 * TDMAsolve.java
 *
 * Created on 4. desember 2000, 22:34
 */

package neqsim.mathlib.generalmath;

/**
 * <p>
 * TDMAsolve class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public final class TDMAsolve {
  /**
   * Dummy constructor, not for use. Class is to be considered static.
   */
  private TDMAsolve() {}

  /**
   * <p>
   * solve.
   * </p>
   *
   * @param a an array of type double
   * @param b an array of type double
   * @param c an array of type double
   * @param r an array of type double
   * @return an array of type double
   */
  public static double[] solve(double[] a, double[] b, double[] c, double[] r) {
    int length = a.length;
    double[] u = new double[length];
    double bet = 0;
    double[] gam = new double[length];

    bet = b[0];
    u[0] = r[0] / bet;

    for (int j = 1; j < length; j++) {
      gam[j] = c[j - 1] / bet;
      bet = b[j] - a[j] * gam[j];
      u[j] = (r[j] - a[j] * u[j - 1]) / bet;
    }

    for (int j = (length - 2); j >= 0; j--) {
      u[j] -= gam[j + 1] * u[j + 1];
    }
    return u;
  }
}
