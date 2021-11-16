// To find HV parameters for CO2 - MDEA system

package neqsim.thermo.util.parameterFitting.Procede.CH4MDEA;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.util.database.NeqSimDataBase;

/**
 *
 * @author Neeraj Agrawal
 * @version
 */
public class TestBinaryHVParameterFittingToEquilibriumData_CH4 {
    static Logger logger =
            LogManager.getLogger(TestBinaryHVParameterFittingToEquilibriumData_CH4.class);

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM CH4MDEA");
        double guess[] = {500, -500, 1e-10, 1e-10, 0.3};
        try {
            while (dataSet.next()) {
                BinaryHVParameterFittingFunction_CH4 function =
                        new BinaryHVParameterFittingFunction_CH4();

                function.setInitialGuess(guess);

                int ID = dataSet.getInt("ID");
                double temperature = Double.parseDouble(dataSet.getString("Temperature"));
                double pressure = Double.parseDouble(dataSet.getString("Pressure"));
                double x1 = Double.parseDouble(dataSet.getString("x1"));
                double x2 = Double.parseDouble(dataSet.getString("x2"));
                double x3 = Double.parseDouble(dataSet.getString("x3"));

                SystemInterface testSystem =
                        new SystemSrkSchwartzentruberEos(temperature, pressure);

                testSystem.addComponent("methane", x1);
                testSystem.addComponent("water", x2);
                testSystem.addComponent("MDEA", x3);

                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);

                double sample1[] = {temperature};
                double standardDeviation1[] = {temperature / 100.0};

                SampleValue sample =
                        new SampleValue(pressure, pressure / 100.0, sample1, standardDeviation1);

                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(ID));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        // optim.solve();
        optim.displayCurveFit();
        // optim.displayResult();
    }
}
