/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.physicalProperties.util.parameterFitting.binaryComponentParameterFitting.binarySystemViscosity.grunbergNissanMethod;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestGrunbergNissanFit {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestGrunbergNissanFit.class);

    /** Creates new TestAcentric */
    public TestGrunbergNissanFit() {}

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database
                .getResultSet("SELECT * FROM binarysystemviscosity WHERE ComponentName1='TEG'");

        try {
            logger.info("adding....");
            while (dataSet.next()) {
                GrunbergNissanFunction function = new GrunbergNissanFunction();
                double guess[] = {0.001};
                function.setInitialGuess(guess);
                SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                double x1 = Double.parseDouble(dataSet.getString("x1"));
                testSystem.addComponent("TEG", x1);
                testSystem.addComponent("water", Double.parseDouble(dataSet.getString("x2")));
                testSystem.createDatabase(true);
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.init(0);
                testSystem.initPhysicalProperties();
                double sample1[] = {x1, testSystem.getTemperature()}; // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature // presure std.dev
                                                     // pressure
                SampleValue sample =
                        new SampleValue(Double.parseDouble(dataSet.getString("Viscosity")),
                                Double.parseDouble(dataSet.getString("StdDev")), sample1,
                                standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }
        //
        // double sample1[] = {0.1};
        // for(int i=0;i<sampleList.size();i++){
        // logger.info("ans: " + ((SampleValue)sampleList.get(i)).getFunction().calcValue(sample1));
        // }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        // optim.displayCurveFit();
        optim.writeToTextFile("c:/testFit.txt");

    }
}
