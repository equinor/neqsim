package neqsim.thermo.util.parameterFitting.Statoil.Acids;

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
public class TestIonicInteractionParameterFittingAcid {
    static Logger logger = LogManager.getLogger(TestIonicInteractionParameterFittingAcid.class);

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet("SELECT * FROM Sleipner");
        int error = 1;
        double ID, x1, x2, x3, x4, temperature, pressure;

        double guess[] = {0.0000708122};

        try {
            int i = 0;
            logger.info("adding....");
            while (dataSet.next()) {
                i++;
                IonicInteractionParameterFittingFunctionAcid function =
                        new IonicInteractionParameterFittingFunctionAcid();

                ID = Integer.parseInt(dataSet.getString("ID"));
                pressure = Double.parseDouble(dataSet.getString("Pressure"));
                temperature = Double.parseDouble(dataSet.getString("Temperature"));
                x1 = Double.parseDouble(dataSet.getString("x1"));
                x2 = Double.parseDouble(dataSet.getString("x2"));
                x3 = Double.parseDouble(dataSet.getString("x3"));
                x4 = Double.parseDouble(dataSet.getString("x4"));

                SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
                testSystem.addComponent("CO2", x1);
                testSystem.addComponent("AceticAcid", x3);
                testSystem.addComponent("MDEA", x4);
                testSystem.addComponent("water", x2);

                testSystem.chemicalReactionInit();
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);
                testSystem.init(0);

                double sample1[] = {x1 / x4};
                double standardDeviation1[] = {0.01};

                if (ID == 162) {
                    error = 5;
                } else {
                    error = 1;
                }

                SampleValue sample = new SampleValue(pressure, error * pressure / 100.0, sample1,
                        standardDeviation1);

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

        optim.solve();
        optim.displayCurveFit();
    }
}
