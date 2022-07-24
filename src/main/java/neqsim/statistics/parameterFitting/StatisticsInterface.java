/*
 * StatisticsInterface.java
 *
 * Created on 30. januar 2001, 18:25
 */

package neqsim.statistics.parameterFitting;

/**
 * <p>
 * StatisticsInterface interface.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public interface StatisticsInterface {
  /**
   * <p>
   * createNewRandomClass.
   * </p>
   *
   * @return a {@link neqsim.statistics.parameterFitting.StatisticsBaseClass} object
   */
  public StatisticsBaseClass createNewRandomClass();

  /**
   * <p>
   * solve.
   * </p>
   */
  public void solve();

  /**
   * <p>
   * init.
   * </p>
   */
  public void init();

  /**
   * <p>
   * getSampleSet.
   * </p>
   *
   * @return a {@link neqsim.statistics.parameterFitting.SampleSet} object
   */
  public SampleSet getSampleSet();

  /**
   * <p>
   * displayResult.
   * </p>
   */
  public void displayResult();

  /**
   * <p>
   * displayCurveFit.
   * </p>
   */
  public void displayCurveFit();

  /**
   * <p>
   * writeToTextFile.
   * </p>
   *
   * @param name a {@link java.lang.String} object
   */
  public void writeToTextFile(String name);

  /**
   * <p>
   * getNumberOfTuningParameters.
   * </p>
   *
   * @return a int
   */
  public int getNumberOfTuningParameters();

  /**
   * <p>
   * setNumberOfTuningParameters.
   * </p>
   *
   * @param numberOfTuningParameters a int
   */
  public void setNumberOfTuningParameters(int numberOfTuningParameters);

  /**
   * <p>
   * runMonteCarloSimulation.
   * </p>
   *
   * @param numberOfRuns a int
   */
  public void runMonteCarloSimulation(int numberOfRuns);
}
