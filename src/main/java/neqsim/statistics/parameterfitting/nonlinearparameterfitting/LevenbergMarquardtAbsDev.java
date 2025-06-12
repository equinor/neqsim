/*
 * LevenbergMarquardtAbsDev.java
 *
 * Created on 22. januar 2001, 23:00
 */

package neqsim.statistics.parameterfitting.nonlinearparameterfitting;

/**
 * <p>
 * LevenbergMarquardtAbsDev class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LevenbergMarquardtAbsDev extends LevenbergMarquardt {
  /**
   * <p>
   * Constructor for LevenbergMarquardtAbsDev.
   * </p>
   */
  public LevenbergMarquardtAbsDev() {}

  /** {@inheritDoc} */
  @Override
  public LevenbergMarquardtAbsDev clone() {
    LevenbergMarquardtAbsDev clonedClass = null;
    try {
      clonedClass = (LevenbergMarquardtAbsDev) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedClass;
  }

  /** {@inheritDoc} */
  @Override
  public double calcChiSquare() {
    double chiSquare = 0;
    for (int i = 0; i < sampleSet.getLength(); i++) {
      chiSquare += Math
          .abs((sampleSet.getSample(i).getSampleValue() - this.calcValue(sampleSet.getSample(i))))
          / sampleSet.getSample(i).getStandardDeviation();
    }
    return chiSquare;
  }

  /** {@inheritDoc} */
  @Override
  public double[][] calcAlphaMatrix() {
    double[][] alpha = new double[sampleSet.getSample(0).getFunction()
        .getFittingParams().length][sampleSet.getSample(0).getFunction().getFittingParams().length];
    for (int i = 0; i < alpha.length; i++) {
      for (int j = 0; j < alpha[0].length; j++) {
        alpha[i][j] = 0.0;
        for (int k = 0; k < sampleSet.getLength(); k++) {
          alpha[i][j] += (dyda[k][i] * dyda[k][j]) / sampleSet.getSample(k).getStandardDeviation();
        }
        if (i == j) {
          alpha[i][j] *= (1.0 + multiFactor);
        }
      }
    }
    return alpha;
  }

  /** {@inheritDoc} */
  @Override
  public double[] calcBetaMatrix() {
    double[] beta = new double[sampleSet.getSample(0).getFunction().getFittingParams().length];
    for (int i = 0; i < beta.length; i++) {
      beta[i] = 0.0;
      for (int j = 0; j < sampleSet.getLength(); j++) {
        int k =
            (sampleSet.getSample(i).getSampleValue() - this.calcValue(sampleSet.getSample(i))) > 0
                ? 1
                : -1;
        beta[i] += k
            * Math
                .abs((sampleSet.getSample(j).getSampleValue() - calcValue(sampleSet.getSample(j))))
            / sampleSet.getSample(j).getStandardDeviation() * dyda[j][i];
      }
    }
    return beta;
  }
}
