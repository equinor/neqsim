/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.Procede.CH4CO2WaterMDEA;

import neqsim.util.database.NeqSimDataBase;
import java.sql.*;
import java.util.*;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import org.apache.logging.log4j.*;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestIonicInteractionParameterFittingCH4 extends java.lang.Object {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestIonicInteractionParameterFittingCH4.class);

    /** Creates new TestAcentric */
    public TestIonicInteractionParameterFittingCH4() {
    }

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList sampleList = new ArrayList();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM CO2CH4MDEA");

        double ID, x1, x2, x3, x4, y1, y2, y3, y4, temperature, pressure, loading;

        // double guess[] = {0.0005447481}; //Case I
        // double guess[] = {0.0004929757}; //Case II
        double guess[] = { 0.0004929757, 1e-10 }; // Case II and CO2-CH4 parameter also regressed

        try {
            int i = 0;
            logger.info("adding....");
            while (dataSet.next()) {
                i++;
                IonicInteractionParameterFittingFunctionCH4 function = new IonicInteractionParameterFittingFunctionCH4();
                IonicInteractionParameterFittingFunctionCH4 function1 = new IonicInteractionParameterFittingFunctionCH4(
                        1, 1);

                ID = Integer.parseInt(dataSet.getString("ID"));
                pressure = Double.parseDouble(dataSet.getString("Pressure"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));
                x3 = Double.parseDouble(dataSet.getString("x3"));
                x4 = Double.parseDouble(dataSet.getString("x4"));
                y1 = Double.parseDouble(dataSet.getString("y1"));
                y2 = Double.parseDouble(dataSet.getString("y2"));
                y3 = Double.parseDouble(dataSet.getString("y3"));
                y4 = Double.parseDouble(dataSet.getString("y4"));

                loading = x2 / x4;

                SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
                testSystem.addComponent("methane", x1);
                testSystem.addComponent("CO2", x2);
                testSystem.addComponent("MDEA", x4);
                testSystem.addComponent("water", x3);

                testSystem.chemicalReactionInit();
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);

                double sample1[] = { loading };
                double standardDeviation1[] = { 0.01 };
                SampleValue sample = new SampleValue(pressure, pressure / 100.0, sample1, standardDeviation1);
                function.setInitialGuess(guess);
                sample.setFunction(function);
                sample.setReference("addicks");
                sample.setDescription(Double.toString(ID));
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);

                double sample2[] = { loading };
                double standardDeviation2[] = { 0.01 };
                SampleValue sample3 = new SampleValue(pressure * y2, y2 * pressure / 100.0, sample2,
                        standardDeviation2);
                function1.setInitialGuess(guess);
                sample3.setFunction(function1);
                sample3.setReference("addicks");
                sample3.setDescription(Double.toString(ID));
                sample3.setThermodynamicSystem(testSystem);
                sampleList.add(sample3);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        //
        optim.solve();
        optim.displayCurveFit();
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
