/*
 * RachfordRice.java
 *
 * Created on 1.april 2024
 */

package neqsim.thermodynamicOperations.flashOps;

/**
 * RachfordRice classes.
 *
 * @author Even Solbraa
 */
public class RachfordRice {
  private static final long serialVersionUID = 1000;

  /**
   * <p>
   * calcBeta. For gas liquid systems.
   * </p>
   * 
   * Method based on Michelsen Mollerup, 2001
   *
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public static double calcBeta2(double[] K, double[] z)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {

    int i;
    double tolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    double midler = 0;
    double minBeta = tolerance;
    double maxBeta = 1.0 - tolerance;
    double g0 = -1.0;
    double g1 = 1.0;

    for (i = 0; i < K.length; i++) {
      midler = (K[i] * z[i] - 1.0) / (K[i] - 1.0);
      if ((midler > minBeta) && (K[i] > 1.0)) {
        minBeta = midler;
      }
      midler = (1.0 - z[i]) / (1.0 - K[i]);
      if ((midler < maxBeta) && (K[i] < 1.0)) {
        maxBeta = midler;
      }
      g0 += z[i] * K[i];
      g1 += -z[i] / K[i];
    }

    if (g0 < 0) {
      return tolerance;
    }
    if (g1 > 0) {
      return 1.0 - tolerance;
    }

    double nybeta = (minBeta + maxBeta) / 2.0;
    double gtest = 0.0;
    for (i = 0; i < K.length; i++) {
      gtest += z[i] * (K[i] - 1.0) / (1.0 - nybeta + nybeta * K[i]);
    }

    if (gtest >= 0) {
      minBeta = nybeta;
    } else {
      maxBeta = nybeta;
    }

    if (gtest < 0) {
      double minold = minBeta;
      minBeta = 1.0 - maxBeta;
      maxBeta = 1.0 - minold;
    }

    int iterations = 0;
    int maxIterations = 300;
    double step = 1.0;
    double gbeta = 0.0;
    double deriv = 0.0;
    double betal = 1.0 - nybeta;
    do {
      iterations++;
      if (gtest >= 0) {
        deriv = 0.0;
        gbeta = 0.0;

        for (i = 0; i < K.length; i++) {
          double temp1 = (K[i] - 1.0);
          double temp2 = 1.0 + temp1 * nybeta;
          deriv += -(z[i] * temp1 * temp1) / (temp2 * temp2);
          gbeta += z[i] * (K[i] - 1.0) / (1.0 + (K[i] - 1.0) * nybeta);
        }

        if (gbeta >= 0) {
          minBeta = nybeta;
        } else {
          maxBeta = nybeta;
        }
        nybeta -= (gbeta / deriv);

        if (nybeta > maxBeta) {
          nybeta = maxBeta;
        }
        if (nybeta < minBeta) {
          nybeta = minBeta;
        }
      } else {
        deriv = 0.0;
        gbeta = 0.0;

        for (i = 0; i < K.length; i++) {
          deriv -= (z[i] * (K[i] - 1.0) * (1.0 - K[i])) / Math.pow((betal + (1 - betal) * K[i]), 2);
          gbeta += z[i] * (K[i] - 1.0) / (betal + (-betal + 1.0) * K[i]);
        }

        if (gbeta < 0) {
          minBeta = betal;
        } else {
          maxBeta = betal;
        }

        betal -= (gbeta / deriv);

        if (betal > maxBeta) {
          betal = maxBeta;
        }
        if (betal < minBeta) {
          betal = minBeta;
        }
        nybeta = 1.0 - betal;
      }
      step = gbeta / deriv;
    } while (Math.abs(step) >= 1.0e-10 && iterations < maxIterations);
    if (nybeta <= tolerance) {
      nybeta = tolerance;
    } else if (nybeta >= 1.0 - tolerance) {
      nybeta = 1.0 - tolerance;
    }

    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(new RachfordRice(), "calcBeta",
          maxIterations);
    }
    if (Double.isNaN(nybeta)) {
      throw new neqsim.util.exception.IsNaNException(new RachfordRice(), "calcBeta", "beta");
    }
    return nybeta;
  }

  /**
   * <p>
   * calcBeta. For gas liquid systems. Method based on Avoiding round-off error in the Rachford–Rice
   * equation, Nielsen, Lia, 2023
   * </p>
   *
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public static double calcBeta(double[] K, double[] z) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {

    double tolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    double g0 = -1.0;
    double g1 = 1.0;

    for (int i = 0; i < K.length; i++) {
      g0 += z[i] * K[i];
      g1 += -z[i] / K[i];
    }

    if (g0 < 0) {
      return tolerance;
    }
    if (g1 > 0) {
      return 1.0 - tolerance;
    }

    double V = 0.5;
    double h = 0.0;

    for (int i = 0; i < K.length; i++) {
      h += z[i] * (K[i] - 1.0) / (1.0 + V * (K[i] - 1.0));
    }
    if (h > 0) {
      for (int i = 0; i < K.length; i++) {
        K[i] = 1.0 / K[i];
      }
    }

    double Kmax = K[0];
    double Kmin = K[0];

    for (int i = 1; i < K.length; i++) {
      if (K[i] < Kmin) {
        Kmin = K[i];
      } else if (K[i] > Kmax) {
        Kmax = K[i];
      }
    }

    double alphaMin = 1.0 / (1.0 - Kmax);
    double alphaMax = 1.0 / (1.0 - Kmin);

    double alpha = V;

    double a = (alpha - alphaMin) / (alphaMax - alpha);
    double b = 1.0 / (alpha - alphaMin);

    double[] c = new double[K.length];
    double[] d = new double[K.length];
    for (int i = 0; i < K.length; i++) {
      c[i] = 1.0 / (1.0 - K[i]);
      d[i] = (alphaMin - c[i]) / (alphaMax - alphaMin);
    }

    double hb = 0.0;
    double amax = alpha;
    double bmax = 1e20;
    double amin = 0;
    double bmin = 1.0 / (alphaMax - alphaMin);
    int iter = 0;
    int maxIterations = 300;
    do {
      iter++;
      double funk = 0;
      double funkder = 0.0;
      hb = 0.0;
      double hbder = 0.0;
      for (int i = 0; i < K.length; i++) {
        funk -= z[i] * a * (1.0 + a) / (d[i] + a * (1.0 + d[i]));
        funkder -=
            z[i] * (a * a + (1.0 + a) * (1.0 + a) * d[i]) / Math.pow(d[i] + a * (1.0 + d[i]), 2.0);
        hb += z[i] * b / (1.0 + b * (alphaMin - c[i]));
        hbder += z[i] / Math.pow(1.0 + b * (alphaMin - c[i]), 2.0);
      }
      if (funk > 0) {
        amax = a;
      } else {
        amin = a;
      }
      if (hb > 0) {
        bmax = b;
      } else {
        bmin = b;
      }
      a = a - funk / funkder;
      if (a > amax || a < amin) {
        a = (amax + amin) / 2.0;
      }
      b = b - hb / hbder;
      if (b > bmax || b < bmin) {
        b = (bmax + bmin) / 2.0;
      }
    } while (Math.abs(hb) > 1e-10 && iter < maxIterations);

    V = -((1 / b) / a - alphaMax);

    if (h > 0) {
      V = 1 - V;
    }

    if (V <= tolerance) {
      V = tolerance;
    } else if (V >= 1.0 - tolerance) {
      V = 1.0 - tolerance;
    }

    if (iter >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(new RachfordRice(), "calcBeta",
          maxIterations);
    }
    if (Double.isNaN(V)) {
      throw new neqsim.util.exception.IsNaNException(new RachfordRice(), "calcBeta", "beta");
    }

    return V;
  }

}
