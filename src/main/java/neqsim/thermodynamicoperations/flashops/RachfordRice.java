/*
 * RachfordRice.java
 *
 * Created on 1.april 2024
 */
package neqsim.thermodynamicoperations.flashops;

import static neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
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
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(RachfordRice.class);
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;
  private double[] beta = new double[2];
  private static volatile String method = "Nielsen2023"; // alternative use Nielsen2023 or
  // Michelsen2001
  // Work arrays reused across iterations
  private double[] c = new double[0];
  private double[] d = new double[0];

  /**
   * Getter for the field <code>method</code>.
   *
   * @return a {@link java.lang.String} object
   */
  public static String getMethod() {
    return RachfordRice.method;
  }

  /**
   * Setter for the field <code>method</code>.
   *
   * @param method a {@link java.lang.String} object
   */
  public static void setMethod(String method) {
    RachfordRice.method = method;
  }

  /**
   * calcBeta. For gas liquid systems. Method used is defined in method String variable
   *
   * @param K equilibrium-ratio array; this method does not modify the caller's array
   * @param z an array of type double
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double calcBeta(double[] K, double[] z)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    if (method.equals("Michelsen2001")) {
      return calcBetaMichelsen2001(K, z);
    } else if (method.equals("Nielsen2023")) {
      return calcBetaNielsen2023(K, z);
    } else {
      return calcBetaMichelsen2001(K, z);
    }
  }

  /**
   * calcBeta. For gas liquid systems. Method based on Michelsen Mollerup, 2001
   *
   * @param K equilibrium-ratio array; this method does not modify the caller's array
   * @param z an array of type double
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double calcBetaMichelsen2001(double[] K, double[] z)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    int i;
    double midler = 0;
    double minBeta = phaseFractionMinimumLimit;
    double maxBeta = 1.0 - phaseFractionMinimumLimit;
    double g0 = -1.0;
    double g1 = 1.0;

    // Every component with a nonzero feed contributes to the phase inventory, including K = 0.
    for (i = 0; i < K.length; i++) {
      if (z[i] == 0.0) {
        continue;
      }
      if (K[i] > 1.0) {
        midler = (z[i] - 1.0 / K[i]) / (1.0 - 1.0 / K[i]);
        minBeta = Math.max(minBeta, midler);
      } else if (K[i] < 1.0) {
        midler = (1.0 - z[i]) / (1.0 - K[i]);
        maxBeta = Math.min(maxBeta, midler);
      }
      g0 += z[i] * K[i];
      g1 += -z[i] / K[i];
    }

    // logger.debug("Max beta " + maxBeta + " min beta " + minBeta);

    if (g0 < 0) {
      return phaseFractionMinimumLimit;
    }
    if (g1 > 0) {
      return 1.0 - phaseFractionMinimumLimit;
    }

    double nybeta = (minBeta + maxBeta) / 2.0;
    double gtest = 0.0;
    for (i = 0; i < K.length; i++) {
      if (z[i] == 0.0) {
        continue;
      }
      gtest += z[i] * equilibriumRatioTerm(K[i], nybeta, 1.0 - nybeta);
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
          if (z[i] == 0.0) {
            continue;
          }
          double ratio = equilibriumRatioTerm(K[i], nybeta, 1.0 - nybeta);
          deriv -= z[i] * ratio * ratio;
          gbeta += z[i] * ratio;
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
          if (z[i] == 0.0) {
            continue;
          }
          double ratio = equilibriumRatioTerm(K[i], 1.0 - betal, betal);
          deriv += z[i] * ratio * ratio;
          gbeta += z[i] * ratio;
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
    } while (Math.abs(step) >= 1.0e-11 && iterations < maxIterations);
    if (nybeta <= phaseFractionMinimumLimit) {
      nybeta = phaseFractionMinimumLimit;
    } else if (nybeta >= 1.0 - phaseFractionMinimumLimit) {
      nybeta = 1.0 - phaseFractionMinimumLimit;
    }
    beta[0] = nybeta;
    beta[1] = 1.0 - nybeta;

    if (iterations >= maxIterations) {
      logger.debug("error " + beta[1]);
      logger.debug("gbeta " + gbeta);
      logger.debug("K " + Arrays.toString(K));
      logger.debug("z " + Arrays.toString(z));
      throw new neqsim.util.exception.TooManyIterationsException(new RachfordRice(), "calcBetaMichelsen2001",
          maxIterations);
    }
    if (Double.isNaN(nybeta)) {
      throw new neqsim.util.exception.IsNaNException(new RachfordRice(), "calcBetaMichelsen2001", "beta");
    }
    return nybeta;
  }

  /**
   * calcBetaNielsen2023. For gas liquid systems. Method based on Avoiding round-off error in the Rachford–Rice
   * equation, Nielsen, Lia, 2023
   *
   * @param K equilibrium-ratio array; this method does not modify the caller's array
   * @param z an array of type double
   * @return Beta Mole fraction of gas phase
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public double calcBetaNielsen2023(double[] K, double[] z)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    double g0 = -1.0;
    double g1 = 1.0;

    // Every component with a nonzero feed contributes to the phase inventory, including K = 0.
    for (int i = 0; i < K.length; i++) {
      if (z[i] == 0.0) {
        continue;
      }
      g0 += z[i] * K[i];
      g1 += -z[i] / K[i];
    }

    if (g0 < 0) {
      return phaseFractionMinimumLimit;
    }
    if (g1 > 0) {
      return 1.0 - phaseFractionMinimumLimit;
    }

    double V = 0.5;
    double h = 0.0;

    for (int i = 0; i < K.length; i++) {
      if (z[i] == 0.0) {
        continue;
      }
      h += z[i] * equilibriumRatioTerm(K[i], V, 1.0 - V);
    }
    double[] workK = K;
    if (h > 0) {
      workK = K.clone();
      for (int i = 0; i < workK.length; i++) {
        if (z[i] == 0.0) {
          continue;
        }
        workK[i] = 1.0 / workK[i];
      }
    }

    double Kmax = 0.0;
    double Kmin = Double.MAX_VALUE;
    boolean foundFeed = false;

    for (int i = 0; i < workK.length; i++) {
      if (z[i] == 0.0) {
        continue;
      }
      if (!foundFeed) {
        Kmax = workK[i];
        Kmin = workK[i];
        foundFeed = true;
      } else {
        if (workK[i] < Kmin) {
          Kmin = workK[i];
        } else if (workK[i] > Kmax) {
          Kmax = workK[i];
        }
      }
    }

    // If the feed is empty, return single phase
    if (!foundFeed) {
      return phaseFractionMinimumLimit;
    }

    double alphaMin = 1.0 / (1.0 - Kmax);
    double alphaMax = 1.0 / (1.0 - Kmin);

    double alpha = V;

    double a = (alpha - alphaMin) / (alphaMax - alpha);
    double b = 1.0 / (alpha - alphaMin);

    if (c.length != workK.length) {
      c = new double[workK.length];
      d = new double[workK.length];
    }
    for (int i = 0; i < workK.length; i++) {
      if (z[i] == 0.0) {
        c[i] = 0.0;
        d[i] = 0.0;
        continue;
      }
      c[i] = 1.0 / (1.0 - workK[i]);
      d[i] = (alphaMin - c[i]) / (alphaMax - alphaMin);
    }

    double hb = 0.0;
    double amax = alpha;
    double bmax = 1e20;
    double amin = 0;
    double bmin = 1.0 / (alphaMax - alphaMin);
    int iter = 0;
    int maxIterations = 300;
    double funk = 0.0;
    double tol = 1e-10;
    do {
      iter++;
      funk = 0.0;
      double funkder = 0.0;
      hb = 0.0;
      double hbder = 0.0;
      for (int i = 0; i < workK.length; i++) {
        if (z[i] == 0.0) {
          continue;
        }
        // Divide the transformed terms before squaring; K = 1 then contributes exactly zero.
        if (d[i] == 0.0) {
          funk -= z[i] * (1.0 + a);
          funkder -= z[i];
        } else if (d[i] == -1.0) {
          funk += z[i] * a * (1.0 + a);
          funkder += z[i] * (1.0 + 2.0 * a);
        } else {
          double inverseDenominator = 1.0 / (d[i] + a / (1.0 + a));
          funk -= z[i] * a * inverseDenominator;
          double derivativeRatio = inverseDenominator / (1.0 + a);
          funkder -= z[i] * (inverseDenominator - a * derivativeRatio * derivativeRatio);
        }
        double inverseB = 1.0 / b;
        double inverseDenominator = 1.0 / (inverseB + (alphaMin - c[i]));
        hb += z[i] * inverseDenominator;
        double derivativeRatio = inverseB * inverseDenominator;
        hbder += z[i] * derivativeRatio * derivativeRatio;
      }
      if (Math.abs(funk) < tol && Math.abs(hb) < tol) {
        break;
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
    } while (iter < maxIterations);

    V = -((1 / b) / a - alphaMax);

    if (h > 0) {
      V = 1 - V;
    }

    if (V <= phaseFractionMinimumLimit) {
      V = phaseFractionMinimumLimit;
    } else if (V >= 1.0 - phaseFractionMinimumLimit) {
      V = 1.0 - phaseFractionMinimumLimit;
    }

    beta[0] = V;
    beta[1] = 1.0 - V;

    if (Math.abs(funk) >= tol || Math.abs(hb) >= tol) {
      logger.error("Rachford rice did not coverge afer " + maxIterations + " iterations");
      logger.debug("K " + Arrays.toString(K));
      logger.debug("z " + Arrays.toString(z));

      throw new neqsim.util.exception.TooManyIterationsException(new RachfordRice(), "calcBetaNielsen2023",
          maxIterations);
    }
    if (Double.isNaN(V)) {
      throw new neqsim.util.exception.IsNaNException(new RachfordRice(), "calcBetaNielsen2023", "beta");
    }

    return V;
  }

  /**
   * Evaluates (K - 1) / (liquid + vapor K) without overflowing for large K.
   *
   * @param equilibriumRatio component equilibrium ratio
   * @param vapor vapor fraction
   * @param liquid liquid fraction, supplied separately to preserve endpoint precision
   * @return the component's unweighted Rachford-Rice contribution
   */
  private static double equilibriumRatioTerm(double equilibriumRatio, double vapor, double liquid) {
    if (equilibriumRatio >= 1.0) {
      double inverseRatio = 1.0 / equilibriumRatio;
      return (1.0 - inverseRatio) / (vapor + liquid * inverseRatio);
    }
    return (equilibriumRatio - 1.0) / (liquid + vapor * equilibriumRatio);
  }

  /**
   * Getter for the field <code>beta</code>.
   *
   * @return an array of type double
   */
  public double[] getBeta() {
    return beta;
  }

  /**
   * calcBetaS.
   *
   * @param system a {@link neqsim.thermo.system.SystemInterface} object
   * @return a double
   * @throws neqsim.util.exception.IsNaNException if any.
   * @throws neqsim.util.exception.TooManyIterationsException if any.
   */
  public final double calcBetaS(SystemInterface system)
      throws neqsim.util.exception.IsNaNException, neqsim.util.exception.TooManyIterationsException {
    ComponentInterface[] compArray = system.getPhase(0).getComponents();

    int i;
    double midler = 0;
    double minBeta = phaseFractionMinimumLimit;
    double maxBeta = 1.0 - phaseFractionMinimumLimit;
    double g0 = -1.0;
    double g1 = 1.0;

    for (i = 0; i < system.getNumberOfComponents(); i++) {
      if (compArray[i].getz() == 0.0) {
        continue;
      }
      if (compArray[i].getK() > 1.0) {
        midler = (compArray[i].getz() - 1.0 / compArray[i].getK()) / (1.0 - 1.0 / compArray[i].getK());
        minBeta = Math.max(minBeta, midler);
      } else if (compArray[i].getK() < 1.0) {
        midler = (1.0 - compArray[i].getz()) / (1.0 - compArray[i].getK());
        maxBeta = Math.min(maxBeta, midler);
      }
      g0 += compArray[i].getz() * compArray[i].getK();
      g1 += -compArray[i].getz() / compArray[i].getK();
    }

    if (g0 < 0) {
      this.beta[1] = 1.0 - phaseFractionMinimumLimit;
      this.beta[0] = phaseFractionMinimumLimit;
      return this.beta[0];
    }
    if (g1 > 0) {
      this.beta[1] = phaseFractionMinimumLimit;
      this.beta[0] = 1.0 - phaseFractionMinimumLimit;
      return this.beta[0];
    }

    double nybeta = (minBeta + maxBeta) / 2.0;

    double gtest = 0.0;
    for (i = 0; i < system.getNumberOfComponents(); i++) {
      if (compArray[i].getz() == 0.0) {
        continue;
      }
      gtest += compArray[i].getz() * equilibriumRatioTerm(compArray[i].getK(), nybeta, 1.0 - nybeta);
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
          if (compArray[i].getz() == 0.0) {
            continue;
          }
          double ratio = equilibriumRatioTerm(compArray[i].getK(), nybeta, 1.0 - nybeta);
          deriv -= compArray[i].getz() * ratio * ratio;
          gbeta += compArray[i].getz() * ratio;
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
          if (compArray[i].getz() == 0.0) {
            continue;
          }
          double ratio = equilibriumRatioTerm(compArray[i].getK(), 1.0 - betal, betal);
          deriv += compArray[i].getz() * ratio * ratio;
          gbeta += compArray[i].getz() * ratio;
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

    if (nybeta <= phaseFractionMinimumLimit) {
      // this.phase = 1;
      nybeta = phaseFractionMinimumLimit;
    } else if (nybeta >= 1.0 - phaseFractionMinimumLimit) {
      // this.phase = 0;
      nybeta = 1.0 - phaseFractionMinimumLimit;
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
