/*
 * StatisticsInterface.java
 *
 * Created on 30. januar 2001, 18:25
 */

package neqsim.statistics.parameterfitting;

/**
 * StatisticsInterface interface.
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface StatisticsInterface {
  /**
   * createNewRandomClass.
   *
   * @return a {@link neqsim.statistics.parameterfitting.StatisticsBaseClass} object
   */
  public StatisticsBaseClass createNewRandomClass();

  /**
   * solve.
   */
  public void solve();

  /**
   * init.
   */
  public void init();

  /**
   * getSampleSet.
   *
   * @return a {@link neqsim.statistics.parameterfitting.SampleSet} object
   */
  public SampleSet getSampleSet();

  /**
   * displayResult.
   */
  public void displayResult();

  /**
   * displayCurveFit.
   */
  public void displayCurveFit();

  /**
   * writeToTextFile.
   *
   * @param name a {@link java.lang.String} object
   */
  public void writeToTextFile(String name);

  /**
   * getNumberOfTuningParameters.
   *
   * @return a int
   */
  public int getNumberOfTuningParameters();

  /**
   * setNumberOfTuningParameters.
   *
   * @param numberOfTuningParameters a int
   */
  public void setNumberOfTuningParameters(int numberOfTuningParameters);

  /**
   * runMonteCarloSimulation.
   *
   * @param numberOfRuns a int
   */
  public void runMonteCarloSimulation(int numberOfRuns);
}
