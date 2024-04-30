/*
 * RachfordRice.java
 *
 * Created on 1.april 2024
 */

package neqsim.thermodynamicOperations.flashOps;

import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.system.SystemInterface;

/**
 * RachfordRice classes.
 *
 * @author Even Solbraa
 */
public class RachfordRice {
  private static final long serialVersionUID = 1000;
  double[] beta = new double[2];
  SystemInterface fluid = null;

  public RachfordRice(SystemInterface fluid) {
    this.fluid = fluid;
  }

  public final double calcBeta() throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    ComponentInterface[] compArray = fluid.getPhase(0).getComponents();

    int i;
    double tolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;
    double deriv = 0.0;
    double gbeta = 0.0;
    double betal = 0;
    double nybeta = 0;

    double midler = 0;
    double minBeta = tolerance;
    double maxBeta = 1.0 - tolerance;
    double g0 = -1.0;
    double g1 = 1.0;


    beta[0] = 0.5;
    beta[1] = 0.5;
    nybeta = fluid.getBeta(0);
    betal = 1.0 - nybeta;

    for (i = 0; i < fluid.getNumberOfComponents(); i++) {
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

    nybeta = (minBeta + maxBeta) / 2.0;
    // System.out.println("guessed beta: " + nybeta + " maxbeta: " +maxBeta + "
    // minbeta: " +minBeta );
    betal = 1.0 - nybeta;

    // ' *l = 1.0-nybeta;
    double gtest = 0.0;
    for (i = 0; i < fluid.getNumberOfComponents(); i++) {
      gtest += compArray[i].getz() * (compArray[i].getK() - 1.0)
          / (1.0 - nybeta + nybeta * compArray[i].getK()); // beta
                                                           // =
                                                           // nybeta
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
    // System.out.println("gtest: " + gtest);
    double step = 1.0;
    do {
      iterations++;
      if (gtest >= 0) {
        // oldbeta = nybeta;
        deriv = 0.0;
        gbeta = 0.0;

        for (i = 0; i < fluid.getNumberOfComponents(); i++) {
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

        // System.out.println("beta: " + maxBeta);
        if (nybeta > maxBeta) {
          nybeta = maxBeta;
        }
        if (nybeta < minBeta) {
          nybeta = minBeta;
        }

        /*
         * if ((nybeta > maxBeta) || (nybeta < minBeta)) { // nybeta = 0.5 * (maxBeta + minBeta);
         * gbeta = 1.0; }
         */
      } else {
        // oldbeta = betal;
        deriv = 0.0;
        gbeta = 0.0;

        for (i = 0; i < fluid.getNumberOfComponents(); i++) {
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

        /*
         * if ((betal > maxBeta) || (betal < minBeta)) { gbeta = 1.0; { betal = 0.5 * (maxBeta +
         * minBeta); } }
         */
        nybeta = 1.0 - betal;
      }
      step = gbeta / deriv;
      // System.out.println("step : " + step);
    } while (Math.abs(step) >= 1.0e-10 && iterations < maxIterations); // &&
    // (Math.abs(nybeta)-Math.abs(maxBeta))>0.1);

    // System.out.println("beta: " + nybeta + " iterations: " + iterations);
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
    fluid.setBeta(fluid.getPhaseIndex(0), nybeta);
    fluid.setBeta(fluid.getPhaseIndex(1), 1.0 - nybeta);

    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(this, "calcBeta", maxIterations);
    }
    if (Double.isNaN(beta[1])) {
      /*
       * for (i = 0; i < numberOfComponents; i++) { System.out.println("K " + compArray[i].getK());
       * System.out.println("z " + compArray[i].getz()); }
       */
      throw new neqsim.util.exception.IsNaNException(this, "calcBeta", "beta");
    }
    return this.beta[0];
  }

}
