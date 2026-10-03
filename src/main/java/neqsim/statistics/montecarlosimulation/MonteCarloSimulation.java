/*
 * MonteCarloSimultion.java
 *
 * Created on 30. januar 2001, 13:06
 */

package neqsim.statistics.montecarlosimulation;

import neqsim.statistics.parameterfitting.StatisticsBaseClass;
import neqsim.statistics.parameterfitting.StatisticsInterface;

/**
 * MonteCarloSimulation class.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class MonteCarloSimulation {
  StatisticsInterface baseStatClass;
  StatisticsInterface[] statClasses;
  double[][] reportMatrix;
  int numberOfRuns = 50;

  /**
   * Constructor for MonteCarloSimulation.
   */
  public MonteCarloSimulation() {
  }

  /**
   * Constructor for MonteCarloSimulation.
   *
   * @param baseStatClass a {@link neqsim.statistics.parameterfitting.StatisticsInterface} object
   */
  public MonteCarloSimulation(StatisticsInterface baseStatClass) {
    this.baseStatClass = baseStatClass;
  }

  /**
   * Constructor for MonteCarloSimulation.
   *
   * @param baseStatClass a {@link neqsim.statistics.parameterfitting.StatisticsBaseClass} object
   * @param numberOfRuns a int
   */
  public MonteCarloSimulation(StatisticsBaseClass baseStatClass, int numberOfRuns) {
    this.baseStatClass = baseStatClass;
    this.numberOfRuns = numberOfRuns;
  }

  /**
   * Setter for the field <code>numberOfRuns</code>.
   *
   * @param numberOfRuns a int
   */
  public void setNumberOfRuns(int numberOfRuns) {
    this.numberOfRuns = numberOfRuns;
  }

  /**
   * runSimulation.
   */
  public void runSimulation() {
    baseStatClass.init();
    statClasses = new StatisticsInterface[numberOfRuns];
    for (int i = 0; i < numberOfRuns; i++) {
      statClasses[i] = baseStatClass.createNewRandomClass();
      statClasses[i].solve();
    }
    createReportMatrix();
  }

  /**
   * Collect fitted parameters from completed runs without printing to standard output.
   *
   * <p>
   * Read the result through {@link #getReportMatrix()}. Row zero contains zero-based run indices; row {@code j + 1}
   * contains fitting parameter {@code j}. The legacy ten-row layout is retained, including unused zero-filled rows.
   * </p>
   */
  public void createReportMatrix() {
    reportMatrix = new double[10][numberOfRuns];
    for (int i = 0; i < numberOfRuns; i++) {
      reportMatrix[0][i] = i;

      for (int j = 0; j < statClasses[0].getSampleSet().getSample(0).getFunction().getNumberOfFittingParams(); j++) {
        reportMatrix[j + 1][i] = statClasses[i].getSampleSet().getSample(0).getFunction().getFittingParams(j);
      }
    }
  }

  /**
   * Return an independent snapshot of the most recently collected report.
   *
   * <p>
   * Call {@link #runSimulation()} first. Each column represents a run. Row zero holds the run index and row
   * {@code j + 1} holds fitting parameter {@code j}; unused rows in the legacy ten-row report are zero-filled. Both the
   * outer array and every row are copied, so changing this snapshot cannot affect subsequent reads.
   * </p>
   *
   * @return a deep copy of the report matrix
   * @throws IllegalStateException if no report has been collected
   */
  public double[][] getReportMatrix() {
    if (reportMatrix == null) {
      throw new IllegalStateException("Run the Monte Carlo simulation before reading its report.");
    }
    double[][] snapshot = new double[reportMatrix.length][];
    for (int row = 0; row < reportMatrix.length; row++) {
      snapshot[row] = reportMatrix[row].clone();
    }
    return snapshot;
  }

}
