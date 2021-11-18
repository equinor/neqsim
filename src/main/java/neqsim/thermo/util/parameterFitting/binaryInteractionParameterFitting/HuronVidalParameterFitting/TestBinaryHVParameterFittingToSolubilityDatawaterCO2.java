package neqsim.thermo.util.parameterFitting.binaryInteractionParameterFitting.HuronVidalParameterFitting;

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
 * @author Even Solbraa
 * @version
 */
public class TestBinaryHVParameterFittingToSolubilityDatawaterCO2 {

    static Logger logger =
            LogManager.getLogger(TestBinaryHVParameterFittingToSolubilityDatawaterCO2.class);


    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM CO2watersolubility
        // WHERE pressureMPA<6 AND reference IN ('[18]', '[35]', '[36]', '[37]', '[38]',
        // '[39]', '[40]', '[41]','[42]')");

        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM CO2watersolubility WHERE pressureMPA<5 AND reference IN ('[18]', '[35]', '[36]', '[37]', '[38]', '[39]', '[40]', '[41]','[42]', '[32]', '[33]', '[34]')");

        try {
            int p = 0;
            while (dataSet.next() && p < 550) {
                p++;
                BinaryHVParameterFittingToSolubilityData function =
                        new BinaryHVParameterFittingToSolubilityData(1, 10);

                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(
                        (Double.parseDouble(dataSet.getString("temperatureC")) + 273.15),
                        10.0 * Double.parseDouble(dataSet.getString("pressureMPA")));
                // SystemInterface testSystem = new
                // SystemSrkEos((Double.parseDouble(dataSet.getString("temperatureC"))+273.15),
                // Double.parseDouble(dataSet.getString("pressurebar")));
                double valCO2 = Double.parseDouble(dataSet.getString("xCO2"));
                testSystem.addComponent("CO2", valCO2);
                testSystem.addComponent("water", 100.0 - valCO2);

                testSystem.createDatabase(true);
                testSystem.setMixingRule("HV");

                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()}; // temperature
                double standardDeviation1[] = {0.01}; // std.dev temperature // presure std.dev
                                                      // pressure
                double val = testSystem.getPressure();
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                sample.setDescription(dataSet.getString("reference"));
                // double parameterGuess[] = {5601.2391787479, -3170.8329162571, -1.7069851770,
                // -0.5058509407}; // HV CO2
                double parameterGuess[] =
                        {5251.7374371982, -3121.2788585048, -0.8420253536, -0.5123316046};//
                // double parameterGuess[] ={13694.7303713825, -807.1129937507, -10.4589547972,
                // -10.9746096153};
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
                logger.info("liq points " + p);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        dataSet = database.getResultSet(
                "SELECT * FROM LuciaData8 WHERE Component='CO2' AND ID<3000 AND Temperature>250 AND Pressure<700000000 AND Temperature<420 AND Y<>NULL AND Y>0.0000000001 ORDER BY Temperature,Pressure");

        try {
            int p = 0;
            while (!dataSet.next() && p < 100) {
                p++;
                BinaryHVParameterFittingToSolubilityData function =
                        new BinaryHVParameterFittingToSolubilityData(0, 0);

                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(
                        Double.parseDouble(dataSet.getString("Temperature")),
                        Double.parseDouble(dataSet.getString("Pressure")) / 1e5);
                testSystem.addComponent("CO2", 1.0);
                testSystem.addComponent("water", 10.0);
                testSystem.setMixingRule("HV");

                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()}; // temperature
                double standardDeviation1[] = {0.01}; // std.dev temperature // presure std.dev
                                                      // pressure
                double val = 1.0 - Double.parseDouble(dataSet.getString("Y"));
                double sdev = val / 100.0;
                SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // double parameterGuess[] = {5601.2391787479, -3170.8329162571, -1.7069851770,
                // -0.5058509407}; // HV CO2
                double parameterGuess[] = {3626.0, -2241.0, 3.91, -3.16};// ; // HV CO2
                function.setInitialGuess(parameterGuess);
                sample.setDescription(Double.toString(testSystem.getTemperature()));
                sampleList.add(sample);
                logger.info("gas points " + p);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        // optim.solve();
        // optim.runMonteCarloSimulation();
        // optim.displayResult();
        optim.displayCurveFit();
        // optim.displayResult();
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
