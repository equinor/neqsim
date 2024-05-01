/*
 * RachfordRice.java
 *
 * Created on 1.april 2024
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.ThermodynamicModelSettings;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.exception.IsNaNException;

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
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public static double calcBeta(double[] K, double[] z) throws neqsim.util.exception.IsNaNException,
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

}
