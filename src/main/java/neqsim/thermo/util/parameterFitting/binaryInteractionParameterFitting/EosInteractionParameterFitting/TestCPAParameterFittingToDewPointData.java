/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.EosInteractionParameterFitting;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemGERGwaterEos;
import neqsim.thermo.system.SystemInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestCPAParameterFittingToDewPointData extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestCPAParameterFittingToDewPointData.class);

    /** Creates new TestAcentric */
    public TestCPAParameterFittingToDewPointData() {
    }

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database
                .getResultSet("SELECT * FROM waterdewpointpaper WHERE gascomponent='nitrogen' AND reference='Gil'");

        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 500) {
                p++;
                CPAParameterFittingToDewPointData function = new CPAParameterFittingToDewPointData();

                // SystemInterface testSystem = new
                // SystemSrkCPAstatoil(Double.parseDouble(dataSet.getString("temperature")),
                // Double.parseDouble(dataSet.getString("Pressure"))*10.0);
                SystemInterface testSystem = new SystemGERGwaterEos(
                        Double.parseDouble(dataSet.getString("temperature")),
                        Double.parseDouble(dataSet.getString("Pressure")) * 10.0);
                // SystemInterface testSystem = new SystemSrkEos(290, 1.0);
                double valueppm = Double.parseDouble(dataSet.getString("ywater")) * 1.0e3;
                testSystem.addComponent(dataSet.getString("gascomponent"), 1.0 - valueppm / 1.0e6);
                testSystem.addComponent("water", valueppm / 1.0e6);

//                testSystem.setSolidPhaseCheck(true);
//                testSystem.setHydrateCheck(true);
                testSystem.createDatabase(true);
                testSystem.setMixingRule(8);

                testSystem.init(0);
                double sample1[] = { testSystem.getPressure(), testSystem.getTemperature() }; // temperature

                double standardDeviation1[] = { 0.13 }; // std.dev temperature // presure std.dev pressure
                SampleValue sample = new SampleValue(testSystem.getTemperature(), 1.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setDescription(Double.toString(valueppm));
                sample.setReference(dataSet.getString("reference"));

                double parameterGuess[] = { 0.001 }; // cpa
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        // optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();
    }
}
