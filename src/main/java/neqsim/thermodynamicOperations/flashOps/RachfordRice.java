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
  public static double calcBeta(SystemInterface fluidinp)
      throws neqsim.util.exception.IsNaNException,
      neqsim.util.exception.TooManyIterationsException {
    SystemInterface fluid = fluidinp;

    ComponentInterface[] compArray = fluid.getPhase(0).getComponents();

    int i;
    double tolerance = neqsim.thermo.ThermodynamicModelSettings.phaseFractionMinimumLimit;


    double nybeta = fluid.getBeta(0);
    double midler = 0;
    double minBeta = tolerance;
    double maxBeta = 1.0 - tolerance;
    double g0 = -1.0;
    double g1 = 1.0;

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
      fluid.setBeta(tolerance);
      return tolerance;
    }
    if (g1 > 0) {
      fluid.setBeta(1.0 - tolerance);
      return 1.0 - tolerance;
    }

    nybeta = (minBeta + maxBeta) / 2.0;
    double gtest = 0.0;
    for (i = 0; i < fluid.getNumberOfComponents(); i++) {
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
    double gbeta = 0.0;
    double deriv = 0.0;
    double betal = 1.0 - nybeta;
    do {
      iterations++;
      if (gtest >= 0) {
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

        if (nybeta > maxBeta) {
          nybeta = maxBeta;
        }
        if (nybeta < minBeta) {
          nybeta = minBeta;
        }
      } else {
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
        nybeta = 1.0 - betal;
      }
      step = gbeta / deriv;
    } while (Math.abs(step) >= 1.0e-10 && iterations < maxIterations);
    if (nybeta <= tolerance) {
      nybeta = tolerance;
    } else if (nybeta >= 1.0 - tolerance) {
      nybeta = 1.0 - tolerance;
    }

    fluid.setBeta(nybeta);

    if (iterations >= maxIterations) {
      throw new neqsim.util.exception.TooManyIterationsException(fluid, "calcBeta", maxIterations);
    }
    if (Double.isNaN(nybeta)) {
      throw new neqsim.util.exception.IsNaNException(fluid, "calcBeta", "beta");
    }
    return nybeta;
  }

}
