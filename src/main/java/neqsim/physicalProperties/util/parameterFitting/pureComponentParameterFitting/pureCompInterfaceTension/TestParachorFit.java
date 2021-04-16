/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */
package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompInterfaceTension;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestParachorFit extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestParachorFit.class);

    /** Creates new TestAcentric */
    public TestParachorFit() {
    }

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();

        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database
                .getResultSet("SELECT * FROM purecomponentsurfacetension WHERE ComponentName='MEG'");

        try {
            while (dataSet.next()) {
                ParachorFunction function = new ParachorFunction();
                double guess[] = { 207.2 }; // methane
                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkCPAstatoil(280, 0.001);
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.getInterphaseProperties().setInterfacialTensionModel(-10);
                testSystem.useVolumeCorrection(true);
                testSystem.setMixingRule(2);
                testSystem.init(0);
                testSystem.setNumberOfPhases(2);
                testSystem.init(3);
                double sample1[] = { testSystem.getTemperature(), testSystem.getPressure() };
                double standardDeviation1[] = { 0.1, 0.1 };
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("SurfaceTension")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        optim.displayCurveFit();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();

        // optim.writeToTextFile("c:/testFit.txt");

    }
}
