/*
 * RachfordRice.java
 *
 * Created on 1.april 2024
 */

package neqsim.thermodynamicoperations.flashops;

import java.io.Serializable;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * RachfordRice classes.
 *
 * @author Even Solbraa
 */
public class RachfordRice implements Serializable {
  static Logger logger = LogManager.getLogger(RachfordRice.class);
  private static final long serialVersionUID = 1000;
  private double[] beta = new double[2];
  private static String method = "Nielsen2023"; // alternative use Nielsen2023 or Michelsen2001

  /**
   * <p>
   * Getter for the field <code>method</code>.
   * </p>
   *
   * @return a {@link java.lang.String} object
   */
  public static String getMethod() {
    return RachfordRice.method;
  }

  /**
   * <p>
   * Setter for the field <code>method</code>.
   * </p>
   *
   * @param method a {@link java.lang.String} object
   */
  public static void setMethod(String method) {
    RachfordRice.method = method;
  }

  /**
   * <p>
   * calcBeta. For gas liquid systems. Method used is defined in method String variable
   * </p>
   *
   * @param K an array of type double
   * @param z an array of type double
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double calcBeta(double[] K, double[] z) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    if (method.equals("Michelsen2001")) {
      return calcBetaMichelsen2001(K, z);
    } else if (method.equals("Nielsen2023")) {
      return calcBetaNielsen2023(K, z);
    } else {
      return calcBetaMichelsen2001(K, z);
    }
  }

  /**
   * <p>
   * calcBeta. For gas liquid systems. Method based on Michelsen Mollerup, 2001
   * </p>
   *
   * @param K an array of type double
   * @param z an array of type double
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double calcBetaMichelsen2001(double[] K, double[] z)
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

    // logger.debug("Max beta " + maxBeta + " min beta " + minBeta);

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
    } while (Math.abs(step) >= 1.0e-11 && (Math.abs(step) >= 1e-9 && iterations < 50)
        && iterations < maxIterations);
    if (nybeta <= tolerance) {
      nybeta = tolerance;
    } else if (nybeta >= 1.0 - tolerance) {
      nybeta = 1.0 - tolerance;
    }
    beta[0] = nybeta;
    beta[1] = 1.0 - nybeta;

    if (iterations >= maxIterations) {
      logger.debug("error " + beta[1]);
      logger.debug("gbeta " + gbeta);
      logger.debug("K " + Arrays.toString(K));
      logger.debug("z " + Arrays.toString(z));
      throw new neqsim.util.exception.TooManyIterationsException(new RachfordRice(),
          "calcBetaMichelsen2001", maxIterations);
    }
    if (Double.isNaN(nybeta)) {
      throw new neqsim.util.exception.IsNaNException(new RachfordRice(), "calcBetaMichelsen2001",
          "beta");
    }
    return nybeta;
  }

  /**
   * <p>
   * calcBetaNielsen2023. For gas liquid systems. Method based on Avoiding round-off error in the
   * Rachford–Rice equation, Nielsen, Lia, 2023
   * </p>
   *
   * @param K an array of type double
   * @param z an array of type double
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double calcBetaNielsen2023(double[] K, double[] z)
      throws neqsim.util.exception.IsNaNException,
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

    beta[0] = V;
    beta[1] = 1.0 - V;

    if (iter >= maxIterations) {
      logger.error("Rachford rice did not coverge afer " + maxIterations + " iterations");
      logger.debug("K " + Arrays.toString(K));
      logger.debug("z " + Arrays.toString(z));

      throw new neqsim.util.exception.TooManyIterationsException(new RachfordRice(),
          "calcBetaNielsen2023", maxIterations);
    }
    if (Double.isNaN(V)) {
      throw new neqsim.util.exception.IsNaNException(new RachfordRice(), "calcBetaNielsen2023",
          "beta");
    }

    return V;
  }

  /**
   * <p>
   * Getter for the field <code>beta</code>.
   * </p>
   *
   * @return an array of type double
   */
  public double[] getBeta() {
    return beta;
  }

  /**
   * <p>
   * calcBetaS.
   * </p>
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public final double calcBetaS(SystemInterface system) throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    ComponentInterface[] compArray = system.getPhase(0).getComponents();

    int i;
    double tolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    double midler = 0;
    double minBeta = tolerance;
    double maxBeta = 1.0 - tolerance;
    double g0 = -1.0;
    double g1 = 1.0;

    for (i = 0; i < system.getNumberOfComponents(); i++) {
      midler = (compArray[i].getK() * compArray[i].getz() - 1.0) / (compArray[i].getK() - 1.0);
      if ((midler > minBeta) && (compArray[i].getK() > 1.0)) {
        minBeta = midler;
      }
      midler = (1.0 - compArray[i].getz()) / (1.0 - compArray[i].getK());
      if ((midler < maxBeta) && (compArray[i].getK() < 1.0)) {
        maxBeta = midler;
      }
      g0 += compArray[i].getz() * compArray[i].getK();
      g1 += -compArray[i].getz() / compArray[i].getK();
    }

    if (g0 < 0) {
      this.beta[1] = 1.0 - tolerance;
      this.beta[0] = tolerance;
      return this.beta[0];
    }
    if (g1 > 0) {
      this.beta[1] = tolerance;
      this.beta[0] = 1.0 - tolerance;
      return this.beta[0];
    }

    double nybeta = (minBeta + maxBeta) / 2.0;

    double gtest = 0.0;
    for (i = 0; i < system.getNumberOfComponents(); i++) {
      gtest += compArray[i].getz() * (compArray[i].getK() - 1.0)
          / (1.0 - nybeta + nybeta * compArray[i].getK());
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
    double deriv = 0.0;
    double gbeta = 0.0;
    double betal = 1.0 - nybeta;

    do {
      iterations++;
      if (gtest >= 0) {
        deriv = 0.0;
        gbeta = 0.0;

        for (i = 0; i < system.getNumberOfComponents(); i++) {
          double temp1 = (compArray[i].getK() - 1.0);
          double temp2 = 1.0 + temp1 * nybeta;
          deriv += -(compArray[i].getz() * temp1 * temp1) / (temp2 * temp2);
          gbeta += compArray[i].getz() * (compArray[i].getK() - 1.0)
              / (1.0 + (compArray[i].getK() - 1.0) * nybeta);
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

        for (i = 0; i < system.getNumberOfComponents(); i++) {
          deriv -= (compArray[i].getz() * (compArray[i].getK() - 1.0) * (1.0 - compArray[i].getK()))
              / Math.pow((betal + (1 - betal) * compArray[i].getK()), 2);
          gbeta += compArray[i].getz() * (compArray[i].getK() - 1.0)
              / (betal + (-betal + 1.0) * compArray[i].getK());
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
    } while (Math.abs(step) >= 1.0e-10 && iterations < maxIterations); // &&

    if (nybeta <= tolerance) {
      // this.phase = 1;
      nybeta = tolerance;
    } else if (nybeta >= 1.0 - tolerance) {
      // this.phase = 0;
      nybeta = 1.0 - tolerance;
      // superheated vapour
    } else {
      // this.phase = 2;
    } // two-phase liquid-gas

    this.beta[0] = nybeta;
    this.beta[1] = 1.0 - nybeta;

    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "calcBetaS", maxIterations);
    }
    if (Double.isNaN(beta[1])) {
      /*
       * for (i = 0; i < numberOfComponents; i++) { System.out.println("K " + compArray[i].getK());
       * System.out.println("z " + compArray[i].getz()); }
       */
      throw new neqsim.util.exception.IsNaNException(this, "calcBetaS", "beta");
    }
    return this.beta[0];
  }
}
