/*
 * newtonRhapson.java
 *
 * Created on 15. juli 2000, 17:43
 */

package neqsim.mathlib.nonlinearsolver;

import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * newtonRhapson class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NewtonRhapson implements java.io.Serializable {
  /** Serialization version UID. */
  private static final long serialVersionUID = 1000;

  int order;
  double[] polyConstants;
  double funkVal = 0;

  /** Convergence tolerance. */
  private static final double EPS = 1e-10;

  double derivVal = 0;

  double dubDerivVal = 0;

  private int maxIterations = 500;
  double xNew = 0;

  double xNew2;

  double x = 0;

  /**
   * <p>
   * Constructor for newtonRhapson.
   * </p>
   */
  public NewtonRhapson() {}

  /**
   * <p>
   * Setter for the field <code>order</code>.
   * </p>
   *
   * @param o a int
   */
  public void setOrder(int o) {
    order = o;
    polyConstants = new double[order + 1];
  }

  /**
   * <p>
   * setConstants.
   * </p>
   *
   * @param constants an array of type double
   */
  public void setConstants(double[] constants) {
    System.arraycopy(constants, 0, polyConstants, 0, constants.length);
  }

  /**
   * <p>
   * funkValue.
   * </p>
   *
   * @param x a double
   * @return a double
   */
  public double funkValue(double x) {
    funkVal = 0;

    for (int i = 0; i < polyConstants.length; i++) {
      funkVal += polyConstants[i] * Math.pow(x, order - i);
    }
    return funkVal;
  }

  /**
   * <p>
   * derivValue.
   * </p>
   *
   * @param x a double
   * @return a double
   */
  public double derivValue(double x) {
    derivVal = 0;

    for (int i = 0; i < polyConstants.length - 1; i++) {
      derivVal += (order - i) * polyConstants[i] * Math.pow(x, order - 1 - i);
    }

    return derivVal;
  }

  /**
   * Set the maximum number of iterations.
   *
   * @param maxIterations the maximum number of iterations
   */
  public void setMaxIterations(int maxIterations) {
    this.maxIterations = maxIterations;
  }

  /**
   * <p>
   * dubDerivValue.
   * </p>
   *
   * @param x a double
   * @return a double
   */
  public double dubDerivValue(double x) {
    dubDerivVal = 0;

    for (int i = 0; i < polyConstants.length - 2; i++) {
      dubDerivVal += (order - 1 - i) * (order - i) * polyConstants[i] * Math.pow(x, order - 2 - i);
    }

    return dubDerivVal;
  }

  /**
   * <p>
   * solve.
   * </p>
   *
   * @param xin a double
   * @return a double
   */
  public double solve(double xin) {
    int iterations = 0;

    x = xin;
    xNew = x;
    xNew2 = x;

    do {
      // System.out.println("x " + xNew);
      iterations++;

      // System.out.println("Z : " + x);
      if (Math.abs(funkValue(xNew)) < Math.abs(funkValue(xNew2))) {
        x = xNew;
      } else {
        x = xNew2;
      }
      if (derivValue(x) * derivValue(x) - 2.0 * funkValue(x) * dubDerivValue(x) > 0) {
        xNew = x - derivValue(x) / dubDerivValue(x)
            + Math.sqrt(derivValue(x) * derivValue(x) - 2 * funkValue(x) * dubDerivValue(x))
                / dubDerivValue(x);
        xNew2 = x - derivValue(x) / dubDerivValue(x)
            - Math.sqrt(derivValue(x) * derivValue(x) - 2 * funkValue(x) * dubDerivValue(x))
                / dubDerivValue(x);
      } else {
        // System.out.println("using first order newton-rhapson...........");
        xNew = x - funkValue(x) / derivValue(x);
        xNew2 = xNew;
      }

      if (xNew < -0.99) {
        xNew = -0.99;
        // System.out.println("x++...........");
      }
      if (xNew > 1.5) {
        xNew = 1;
        // System.out.println("x--...........");
      }
    } while (Math.abs(funkValue(x)) > EPS && iterations <= maxIterations);

    if (iterations == maxIterations) {
      System.out.println("Too many iterations...");
    }
    // System.out.println("iterations in newton-rhapson = " + iterations );

    return xNew;
  }

  /**
   * <p>
   * solve1order.
   * </p>
   *
   * @param xin a double
   * @return a double
   */
  public double solve1order(double xin) {
    int iterations = 0;

    x = xin;
    xNew = x;
    xNew2 = x;

    do {
      iterations++;
      x = xNew;
      xNew = x - funkValue(x) / derivValue(x);
      xNew2 = xNew;
    } while (Math.abs(funkValue(x)) > EPS && iterations <= maxIterations);

    if (iterations == maxIterations) {
      System.out.println("Too many iterations...");
    }
    // System.out.println("iterations in newton-rhapson = " + iterations );

    return xNew;
  }

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @ExcludeFromJacocoGeneratedReport
  public static void main(String[] args) {
    NewtonRhapson test = new NewtonRhapson();
    test.setOrder(3);

    double[] constants = new double[] {-0.003058, -0.01806, -0.266, -0.2999};
    test.setConstants(constants);

    System.out.println("val : " + test.funkValue(-0.0));
    System.out.println("val : " + test.dubDerivValue(-0.3));
    System.out.println("val : " + test.derivValue(-0.3));
    // System.out.println("val : " + test.solve(-0.3));
  }
}
