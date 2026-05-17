/*
 * LevenbergMarquardt.java
 *
 * Created on 22. januar 2001, 23:00
 */

package neqsim.statistics.parameterfitting.nonlinearparameterfitting;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import Jama.Matrix;
import neqsim.statistics.parameterfitting.StatisticsBaseClass;
import neqsim.statistics.parameterfitting.nonlinearparameterfitting.LevenbergMarquardtResult.ConvergenceReason;
import neqsim.util.ExcludeFromJacocoGeneratedReport;

/**
 * <p>
 * LevenbergMarquardt class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class LevenbergMarquardt extends StatisticsBaseClass {
  /** Logger object for class. */
  static Logger logger = LogManager.getLogger(LevenbergMarquardt.class);

  /** Minimum number of LM iterations retained for backward-compatible solver behavior. */
  private static final int MINIMUM_NUMBER_OF_ITERATIONS = 5;

  /** Chi-square convergence tolerance. */
  private static final double CHI_SQUARE_TOLERANCE = 1.0e-6;

  /** Weighted gradient norm convergence tolerance. */
  private static final double GRADIENT_TOLERANCE = 1.0e-6;

  double oldChiSquare = 1e100;
  double newChiSquare = 0;
  Matrix parameterStdDevMatrix;
  Matrix parameterUncertaintyMatrix;
  boolean solved = false;
  private int maxNumberOfIterations = 50;
  private LevenbergMarquardtResult result = LevenbergMarquardtResult.notRun();

  /**
   * <p>
   * Constructor for LevenbergMarquardt.
   * </p>
   */
  public LevenbergMarquardt() {
    result = LevenbergMarquardtResult.notRun();
  }

  /** {@inheritDoc} */
  @Override
  public LevenbergMarquardt clone() {
    LevenbergMarquardt clonedClass = null;
    try {
      clonedClass = (LevenbergMarquardt) super.clone();
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }

    return clonedClass;
  }

  /** {@inheritDoc} */
  @Override
  public void init() {
    chiSquare = calcChiSquare();
    logger.debug("Chi square: {}", chiSquare);
    dyda = calcDerivatives();
    beta = calcBetaMatrix();
    alpha = calcAlphaMatrix();
  }

  /** {@inheritDoc} */
  @Override
  public void solve() {
    setFittingParameters(sampleSet.getSample(0).getFunction().getFittingParams());
    Matrix betaMatrix;
    Matrix newParameters = null;
    int n = 0;
    ConvergenceReason convergenceReason = ConvergenceReason.MAX_ITERATIONS_REACHED;
    oldChiSquare = 1e100;
    newChiSquare = Double.NaN;
    init();
    oldChiSquare = chiSquare;
    while (true) {
      betaMatrix = new Matrix(beta, 1).transpose();
      double gradientNorm = betaMatrix.norm2();
      if (n >= MINIMUM_NUMBER_OF_ITERATIONS && Math.abs(chiSquare) <= CHI_SQUARE_TOLERANCE) {
        convergenceReason = ConvergenceReason.CHI_SQUARE_TOLERANCE;
        break;
      }
      if (n >= MINIMUM_NUMBER_OF_ITERATIONS && gradientNorm <= GRADIENT_TOLERANCE) {
        convergenceReason = ConvergenceReason.GRADIENT_TOLERANCE;
        break;
      }
      if (n >= maxNumberOfIterations && n >= MINIMUM_NUMBER_OF_ITERATIONS) {
        break;
      }

      n++;
      Matrix alphaMatrix = new Matrix(alpha);
      Matrix solvedMatrix;
      try {
        solvedMatrix = alphaMatrix.solve(betaMatrix);
      } catch (RuntimeException ex) {
        logger.warn("Levenberg-Marquardt stopped because the normal matrix could not be solved",
            ex);
        convergenceReason = ConvergenceReason.SINGULAR_MATRIX;
        break;
      }

      Matrix oldParameters =
          new Matrix(sampleSet.getSample(0).getFunction().getFittingParams(), 1).copy();
      newParameters = oldParameters.copy().plus(solvedMatrix.transpose());
      // Matrix diffMat = newParameters.copy().minus(oldParameters);
      this.checkBounds(newParameters);
      this.setFittingParameters(newParameters.copy().getArray()[0]);
      newChiSquare = calcChiSquare();
      if (newChiSquare >= oldChiSquare || Double.isNaN(newChiSquare)
          || Double.isInfinite(newChiSquare)) {
        newChiSquare = oldChiSquare;
        multiFactor *= 10.0;
        this.setFittingParameters(oldParameters.getArray()[0]);
      } else {
        multiFactor /= 10.0;
        oldChiSquare = newChiSquare;
      }
      logger.debug("LM iteration {} chi-square {} damping {}", n, newChiSquare, multiFactor);
      init();
    }
    updateParameterStatistics();
    result = new LevenbergMarquardtResult(convergenceReason, n, chiSquare, getGradientNorm(),
        coVarianceMatrix, parameterCorrelationMatrix, parameterStandardDeviation);
    solved = result.isConverged();
    logger.info("Levenberg-Marquardt stopped after {} iterations with reason {} and chi-square {}",
        n, convergenceReason, chiSquare);
  }

  /**
   * Updates covariance, correlation and standard-error fields from the final Jacobian.
   */
  private void updateParameterStatistics() {
    double oldMultiFactor = multiFactor;
    try {
      multiFactor = 0.0;
      alpha = calcAlphaMatrix();
      coVarianceMatrix = new Matrix(alpha).inverse();
      calcParameterStandardDeviation();
      calcCorrelationMatrix();
    } catch (RuntimeException ex) {
      coVarianceMatrix = null;
      parameterStandardDeviation = null;
      parameterCorrelationMatrix = null;
      logger.warn("Could not calculate Levenberg-Marquardt covariance information", ex);
    } finally {
      multiFactor = oldMultiFactor;
    }
  }

  /**
   * Returns the current weighted gradient norm.
   *
   * @return norm of the weighted gradient vector
   */
  private double getGradientNorm() {
    if (beta == null) {
      return Double.NaN;
    }
    return new Matrix(beta, 1).norm2();
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
    /*
     * LevenbergMarquardt optim = new LevenbergMarquardt(); TestFunction testFunction = new
     * TestFunction(); // optim.setFunction(testFunction);
     *
     * SampleValue[] sample = new SampleValue[3]; double sample1[] = { 6 }; sample[0] = new
     * SampleValue(8.5,0.1,sample1); double sample2[] = { 4 }; sample[1] = new
     * SampleValue(5.5,0.1,sample2); double sample3[] = { 4 }; sample[2] = new
     * SampleValue(5.51,0.1,sample3);
     *
     * SampleSet sampleSet = new SampleSet(sample); sampleSet =
     * sampleSet.createNewNormalDistributedSet(); optim.setSampleSet(sampleSet); optim.solve();
     * optim.runMonteCarloSimulation();
     */
  }

  /**
   * <p>
   * Getter for the field <code>maxNumberOfIterations</code>.
   * </p>
   *
   * @return the maxNumberOfIterations
   */
  public int getMaxNumberOfIterations() {
    return maxNumberOfIterations;
  }

  /**
   * <p>
   * Setter for the field <code>maxNumberOfIterations</code>.
   * </p>
   *
   * @param maxNumberOfIterations the maxNumberOfIterations to set
   */
  public void setMaxNumberOfIterations(int maxNumberOfIterations) {
    this.maxNumberOfIterations = maxNumberOfIterations;
  }

  /**
   * Returns the result from the most recent optimization run.
   *
   * @return immutable optimization result
   */
  public LevenbergMarquardtResult getResult() {
    return result;
  }

  /**
   * Returns whether the most recent optimization converged successfully.
   *
   * @return true if the most recent result converged
   */
  public boolean isSolved() {
    return solved;
  }
}
