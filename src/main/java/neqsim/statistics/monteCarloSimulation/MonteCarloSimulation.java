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
 *
 * @author Even Solbraa
 * @version
 */
public class MonteCarloSimulation {
    private static final long serialVersionUID = 1000;
    StatisticsInterface baseStatClass;
    StatisticsInterface[] statClasses;
    double[][] reportMatrix;
    int numberOfRuns = 50;

    /** Creates new MonteCarloSimultion */
    public MonteCarloSimulation() {}

    public MonteCarloSimulation(StatisticsInterface baseStatClass) {
        this.baseStatClass = baseStatClass;
    }

    public MonteCarloSimulation(StatisticsBaseClass baseStatClass, int numberOfRuns) {
        this.baseStatClass = baseStatClass;
        this.numberOfRuns = numberOfRuns;
    }

    public void setNumberOfRuns(int numberOfRuns) {
        this.numberOfRuns = numberOfRuns;
    }

    public void runSimulation() {
        baseStatClass.init();
        statClasses = new StatisticsInterface[numberOfRuns];
        for (int i = 0; i < numberOfRuns; i++) {
            statClasses[i] = baseStatClass.createNewRandomClass();
            statClasses[i].solve();
        }
        createReportMatrix();
    }

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
