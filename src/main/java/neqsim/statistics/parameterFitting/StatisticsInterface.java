/*
 * StatisticsInterface.java
 *
 * Created on 30. januar 2001, 18:25
 */
package neqsim.statistics.parameterFitting;

/**
 *
 * @author Even Solbraa
 * @version
 */
public interface StatisticsInterface {
    public StatisticsBaseClass createNewRandomClass();

    public void solve();

    public void init();

    public SampleSet getSampleSet();

    public void displayResult();

    public void displayCurveFit();

    public void writeToTextFile(String name);

    public void writeToCdfFile(String name);

    public int getNumberOfTuningParameters();

    public void setNumberOfTuningParameters(int numberOfTuningParameters);

    public void runMonteCarloSimulation(int numerOfRuns);
}
