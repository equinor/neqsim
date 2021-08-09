/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.ionicInteractionCoefficientFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemFurstElectrolyteEos;
import neqsim.thermo.system.SystemInterface;
import neqsim.util.database.NeqSimDataBase;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestIonicInteractionParameterFitting_Sleipnernoacid {

    private static final long serialVersionUID = 1000;
    static Logger logger =
            LogManager.getLogger(TestIonicInteractionParameterFitting_Sleipnernoacid.class);

    /** Creates new TestAcentric */
    public TestIonicInteractionParameterFitting_Sleipnernoacid() {}

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        NeqSimDataBase database = new NeqSimDataBase();
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM CO2KurCor WHERE
        // Reference = 'Rho1997' AND Temperature = 323.15 ");
        ResultSet dataSet = database.getResultSet("SELECT * FROM Sleipner");
        try {
            int i = 0;
            while (dataSet.next()) {
                i++;

                IonicInteractionParameterFittingFunction_Sleipnernoacid function =
                        new IonicInteractionParameterFittingFunction_Sleipnernoacid();

                // double guess[] = {1.046762e-4,0.231069e-4,1.09254e-4,-1.190554e-4};
                double guess[] = {-2.2e-5};
                // double guess[] = {-2.181442e-4};
                // double guess[] = {1.04e-4,0.23e-4,1.0e-4,-1.1e-4};
                double ID = Double.parseDouble(dataSet.getString("ID"));
                double x1 = Double.parseDouble(dataSet.getString("x1"));
                double x2 = Double.parseDouble(dataSet.getString("x2"));
                double x3 = Double.parseDouble(dataSet.getString("x3"));
                double x4 = Double.parseDouble(dataSet.getString("x4"));
                double temperature = Double.parseDouble(dataSet.getString("Temperature"));
                double pressure = Double.parseDouble(dataSet.getString("Pressure"));

                SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
                testSystem.addComponent("CO2", x1);
                testSystem.addComponent("water", x2);
                testSystem.addComponent("MDEA", x4 - x3);
                // testSystem.addComponent("MDEA",x4);
                testSystem.addComponent("Ac-", x3);
                testSystem.addComponent("MDEA+", x3);

                testSystem.chemicalReactionInit();
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);

                double sample1[] = {temperature, x1 / (x4 - x3)};
                double standardDeviation1[] = {0.01};
                double stddev = 0.01;
                SampleValue sample = new SampleValue(pressure, stddev, sample1, standardDeviation1);
                function.setInitialGuess(guess);
                sample.setFunction(function);
                sample.setDescription(Double.toString(ID));
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);

            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // optim.solve();

        // optim.displayGraph();
        optim.displayCurveFit();
    }
}
