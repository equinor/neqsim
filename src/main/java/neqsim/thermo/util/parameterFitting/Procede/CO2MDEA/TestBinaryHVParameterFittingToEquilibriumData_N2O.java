

// To find HV parameters for CO2 - MDEA systempackage
// neqsim.thermo.util.parameterFitting.Procede.CO2MDEA;

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
public class TestBinaryHVParameterFittingToEquilibriumData_N2O {

    static Logger logger =
            LogManager.getLogger(TestBinaryHVParameterFittingToEquilibriumData_N2O.class);

    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();
        double error = 5;

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        // Reference = 'Versteeg' OR Reference='Al-Ghawas' OR Reference = 'Pawlak' OR
        // Reference ='Haimour' Reference='Al-Ghawas' OR
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM CO2MDEA WHERE Reference='Jou' OR  Reference = 'Pawlak' OR Reference = 'Versteeg' OR Reference ='Haimour'");

        // double guess[] = {493.2862980752, 265.1993459038, -0.4817235596,
        // -0.6827900771, -0.7855706585}; //First one
        double guess[] =
                {-387.8913684529, -2028.8216959926, 6.1851396710, 3.4677644464, -0.2029288678}; // Second
                                                                                                // one

        try {

            while (dataSet.next()) {
                BinaryHVParameterFittingFunction_N2O function =
                        new BinaryHVParameterFittingFunction_N2O();

                function.setInitialGuess(guess);

                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(280, 1);

                int ID = dataSet.getInt("ID");
                double temperature = Double.parseDouble(dataSet.getString("Temperature"));
                double pressure = Double.parseDouble(dataSet.getString("Pressure"));
                double x1 = Double.parseDouble(dataSet.getString("x1"));
                double x2 = Double.parseDouble(dataSet.getString("x2"));
                double x3 = Double.parseDouble(dataSet.getString("x3"));

                if (ID == 96 || ID == 115 || ID == 124) {
                    continue; // Data points of Versteeg corresponding to high MDEA wt %
                }
                if (ID == 132) {
                    continue;
                }

                testSystem.setTemperature(temperature);
                testSystem.setPressure(pressure);

                testSystem.addComponent("CO2", x1);
                testSystem.addComponent("Water", x2);
                testSystem.addComponent("MDEA", x3);

                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);

                double sample1[] = {x3, temperature};
                double standardDeviation1[] = {x3 / 100, temperature / 100.0};

                SampleValue sample = new SampleValue(pressure, error * pressure / 100.0, sample1,
                        standardDeviation1);

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
