/*
 * MonteCarloSimultion.java
 *
 * Created on 30. januar 2001, 13:06
 */

package neqsim.statistics.monteCarloSimulation;

import Jama.Matrix;
import neqsim.statistics.parameterFitting.StatisticsBaseClass;
import neqsim.statistics.parameterFitting.StatisticsInterface;

/**
 * <p>
 * MonteCarloSimulation class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class MonteCarloSimulation {

    private static final long serialVersionUID = 1000;
    StatisticsInterface baseStatClass;
    StatisticsInterface[] statClasses;
    double[][] reportMatrix;
    int numberOfRuns = 50;

    /**
     * <p>Constructor for MonteCarloSimulation.</p>
     */
    public MonteCarloSimulation() {}

    /**
     * <p>
     * Constructor for MonteCarloSimulation.
     * </p>
     *
     * @param baseStatClass a {@link neqsim.statistics.parameterFitting.StatisticsInterface} object
     */
    public MonteCarloSimulation(StatisticsInterface baseStatClass) {
        this.baseStatClass = baseStatClass;
    }

    /**
     * <p>
     * Constructor for MonteCarloSimulation.
     * </p>
     *
     * @param baseStatClass a {@link neqsim.statistics.parameterFitting.StatisticsBaseClass} object
     * @param numberOfRuns a int
     */
    public MonteCarloSimulation(StatisticsBaseClass baseStatClass, int numberOfRuns) {
        this.baseStatClass = baseStatClass;
        this.numberOfRuns = numberOfRuns;
    }

    /**
     * <p>
     * Setter for the field <code>numberOfRuns</code>.
     * </p>
     *
     * @param numberOfRuns a int
     */
    public void setNumberOfRuns(int numberOfRuns) {
        this.numberOfRuns = numberOfRuns;
    }

    /**
     * <p>
     * runSimulation.
     * </p>
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
     * <p>
     * createReportMatrix.
     * </p>
     */
    public void createReportMatrix() {
        reportMatrix = new double[10][numberOfRuns];
        for (int i = 0; i < numberOfRuns; i++) {
            reportMatrix[0][i] = i;

            for (int j = 0; j < statClasses[0].getSampleSet().getSample(0).getFunction()
                    .getNumberOfFittingParams(); j++) {
                reportMatrix[j + 1][i] = statClasses[i].getSampleSet().getSample(0).getFunction()
                        .getFittingParams(j);
            }
        }

        Matrix report = new Matrix(reportMatrix);// .print(10,2);
        report.print(10, 17);
    }

}
