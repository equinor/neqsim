/*
 * NumericalDerivative.java
 *
 * Created on 28. juli 2000, 15:39
 */

package neqsim.statistics.parameterFitting;

/**
 * <p>
 * NumericalDerivative class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class NumericalDerivative implements java.io.Serializable {
  private static final long serialVersionUID = 1000;

  final static double CON = 1.4;
  final static double CON2 = CON * CON;
  final static double BIG = 1.0e30;
  final static int NTAB = 10;
  final static double SAFE = 2.0;

  /**
   * <p>
   * Constructor for NumericalDerivative.
   * </p>
   */
  public NumericalDerivative() {}

  /**
   * <p>
   * calcDerivative.
   * </p>
   *
   * @param system a {@link neqsim.statistics.parameterFitting.StatisticsBaseClass} object
   * @param sampleNumber a int
   * @param parameterNumber a int
   * @return a double
   */
  public static double calcDerivative(StatisticsBaseClass system, int sampleNumber,
      int parameterNumber) {
    double errt, fac, hh, ans, err;
    double h = Math.abs(system.getSampleSet().getSample(sampleNumber).getFunction()
        .getFittingParams(parameterNumber)) / 1.0e3;
    if (h == 0.0) {
      System.out.println("h must be larger than 0!");
      System.out.println("setting it to 1.0e-10");
      h = 1.0e-10;
    }
    double[][] a = new double[NTAB][NTAB];

    hh = h;
    // System.out.println("hh " + hh);
    double oldFittingParam1 = system.getSampleSet().getSample(sampleNumber).getFunction()
        .getFittingParams(parameterNumber);
    system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
        oldFittingParam1 + hh);
    double val1 = system.calcValue(system.getSample(sampleNumber));
    system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
        oldFittingParam1 - hh);
    double val2 = system.calcValue(system.getSample(sampleNumber));
    system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
        oldFittingParam1);

    a[0][0] = (val1 - val2) / (2.0 * hh);
    ans = a[0][0];
    err = BIG;

    for (int i = 1; i <= 0 * NTAB - 1; i++) {
      hh /= CON;

      double oldFittingParam = system.getSampleSet().getSample(sampleNumber).getFunction()
          .getFittingParams(parameterNumber);
      system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
          oldFittingParam + hh);
      val1 = system.calcValue(system.getSample(sampleNumber));
      // system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
      // oldFittingParam);
      system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
          oldFittingParam - hh);
      val2 = system.calcValue(system.getSample(sampleNumber));
      system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
          oldFittingParam);

      a[0][i] = (val1 - val2) / (2.0 * hh);
      fac = CON2;
      for (int j = 1; j <= i; j++) {
        a[j][i] = (a[j - 1][i] * fac - a[j - 1][i - 1]) / (fac - 1.0);
        fac = CON2 * fac;
        errt = Math.max(Math.abs(a[j][i] - a[j - 1][i]), Math.abs(a[j][i] - a[j - 1][i - 1]));
        // System.out.println("errt : " +errt);

        if (errt <= err) {
          err = errt;
          ans = a[j][i];
        }
        // System.out.println("deriv1 " + ans);
      }

      if (Math.abs(a[i][i] - a[i - 1][i - 1]) >= SAFE * err) {
        break;
      }
    }
    system.getSampleSet().getSample(sampleNumber).getFunction().setFittingParams(parameterNumber,
        oldFittingParam1);
    // System.out.println("deriv " + ans);
    // System.out.println("err " + err);
    return ans;
  }
}
