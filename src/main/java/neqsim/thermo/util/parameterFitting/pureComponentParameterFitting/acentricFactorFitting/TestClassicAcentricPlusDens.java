/*
 * TestAcentric.java
 *
 * Created on 23. januar 2001, 22:08
 */

package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemRKEos;
import neqsim.util.database.NeqSimDataBase;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestClassicAcentricPlusDens {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestClassicAcentricPlusDens.class);

    /** Creates new TestAcentric */
    public TestClassicAcentricPlusDens() {}

    public static void main(String[] args) {

        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();

        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // PureComponentVapourPressures WHERE ComponentName='methane' AND
        // VapourPressure<65.5 AND Reference='Perry1998'");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // PureComponentVapourPressures WHERE ComponentName='CO2' AND
        // VapourPressure>5");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // PureComponentVapourPressures WHERE ComponentName='water' AND VapourPressure>0
        // ORDER BY Temperature ASC");
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='nitrogen' AND VapourPressure>0 ORDER BY Temperature ASC");
        double guess[] = {0.04};//
        try {
            logger.info("adding....");
            while (!dataSet.next()) {
                ClassicAcentricFunction function = new ClassicAcentricFunction();
                // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(280, 0.001);
                // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                SystemInterface testSystem = new SystemRKEos(280, 0.001);
                // SystemInterface testSystem = new SystemPrEos(280, 0.001);
                // testSystem.useVolumeCorrection(false);
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
                // testSystem.createDatabase(true);
                // legger til komponenter til systemet
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))}; // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature // presure std.dev
                                                     // pressure
                double val = Double.parseDouble(dataSet.getString("VapourPressure"));
                double stddev = val / 100.0;
                double logVal = Math.log(val);
                SampleValue sample = new SampleValue(logVal, stddev, sample1, standardDeviation1);
                testSystem.init(0);
                function.setInitialGuess(guess);
                // function.setBounds(bounds);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(dataSet.getString("Reference"));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        // dataSet = database.getResultSet( "SELECT * FROM PureComponentVapourPressures
        // WHERE ComponentName='methane' AND VapourPressure<65.5 AND
        // Reference='Perry1998'");
        // dataSet = database.getResultSet( "SELECT * FROM PureComponentVapourPressures
        // WHERE ComponentName='CO2' AND VapourPressure>5");
        // dataSet = database.getResultSet( "SELECT * FROM PureComponentVapourPressures
        // WHERE ComponentName='water' AND VapourPressure>0 ORDER BY Temperature ASC");
        dataSet = database.getResultSet(
                "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='nitrogen' AND VapourPressure>0 ORDER BY Temperature ASC");
        try {
            logger.info("adding....");
            while (!dataSet.next()) {
                ClassicAcentricDens function = new ClassicAcentricDens(1);
                // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(280, 0.001);
                // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                SystemInterface testSystem = new SystemRKEos(280, 0.001);
                // SystemInterface testSystem = new SystemPrEos(280, 0.001);
                testSystem.useVolumeCorrection(false);
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
                testSystem.setPressure(Double.parseDouble(dataSet.getString("VapourPressure")));
                testSystem.init(0);
                testSystem.setMixingRule(1);
                logger.info("adding2....");
                double dens = Double.parseDouble(dataSet.getString("liquiddensity"));
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))}; // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature // presure std.dev
                                                     // pressure
                SampleValue sample =
                        new SampleValue(dens, dens / 100.0, sample1, standardDeviation1);
                function.setInitialGuess(guess);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(dataSet.getString("Reference"));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        // dataSet = database.getResultSet( "SELECT * FROM PureComponentVapourPressures
        // WHERE ComponentName='methane' AND VapourPressure<65.5 AND
        // Reference='Perry1998'");
        // dataSet = database.getResultSet( "SELECT * FROM PureComponentVapourPressures
        // WHERE ComponentName='CO2' AND VapourPressure>5");
        // dataSet = database.getResultSet( "SELECT * FROM PureComponentVapourPressures
        // WHERE ComponentName='water' AND VapourPressure>0 ORDER BY Temperature ASC");
        dataSet = database.getResultSet(
                "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='nitrogen' AND VapourPressure>0 ORDER BY Temperature ASC");
        try {
            logger.info("adding....");
            while (dataSet.next()) {
                ClassicAcentricDens function = new ClassicAcentricDens(0);
                // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                SystemInterface testSystem = new SystemRKEos(280, 0.001);
                // SystemInterface testSystem = new SystemPrEos(280, 0.001);
                // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(280, 0.001);
                testSystem.useVolumeCorrection(false);
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0); // legger
                                                                                    // komponenter
                                                                                    // til systemet
                testSystem.setPressure(Double.parseDouble(dataSet.getString("VapourPressure")));
                testSystem.init(0);
                testSystem.setMixingRule(1);
                double dens = Double.parseDouble(dataSet.getString("gasdensity"));
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))}; // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature // presure std.dev
                                                     // pressure
                SampleValue sample =
                        new SampleValue(dens, dens / 100.0, sample1, standardDeviation1);
                function.setInitialGuess(guess);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(dataSet.getString("Reference"));
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);

        LevenbergMarquardt optim = new LevenbergMarquardt();
        optim.setSampleSet(sampleSet);

        // do simulations
        //
        // optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();
        optim.writeToTextFile("c:/test.txt");
    }
}
