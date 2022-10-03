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
import neqsim.util.database.NeqSimExperimentDatabase;

/**
 * <p>
 * TestBinaryHVParameterFittingToSolubilityData_LuciaPropane class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestBinaryHVParameterFittingToSolubilityData_LuciaPropane {
  static Logger logger =
      LogManager.getLogger(TestBinaryHVParameterFittingToSolubilityData_LuciaPropane.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  public static void main(String[] args) {
    LevenbergMarquardt optim = new LevenbergMarquardt();
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

    // inserting samples from database
    NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();
    ResultSet dataSet = database.getResultSet(
        "SELECT * FROM LuciaData8 WHERE Component='nitrogen' AND Temperature>270.0 AND L2>0.000000001"); // AND
                                                                                                         // Temperature<600
                                                                                                         // AND
                                                                                                         // Pressure<7000000000
                                                                                                         // AND
                                                                                                         // L2<>NULL
                                                                                                         // AND
                                                                                                         // L2>0.000000001
                                                                                                         // ORDER
                                                                                                         // BY
                                                                                                         // Temperature,Pressure");
                                                                                                         // AND
                                                                                                         // Reference='Houghton1957'
                                                                                                         // AND
                                                                                                         // Reference<>'Nighswander1989'
                                                                                                         // AND
                                                                                                         // Temperature>278.15
                                                                                                         // AND
                                                                                                         // Temperature<383.15
                                                                                                         // AND
                                                                                                         // Pressure<60.01325");

    try {
      int p = 0;
      logger.info("adding....");
      while (dataSet.next() && p < 80) {
        p++;
        BinaryHVParameterFittingToSolubilityData function =
            new BinaryHVParameterFittingToSolubilityData();

        SystemInterface testSystem =
            new SystemSrkSchwartzentruberEos(Double.parseDouble(dataSet.getString("Temperature")),
                Double.parseDouble(dataSet.getString("Pressure")) / 1e5);

        testSystem.addComponent("nitrogen", 10.0);
        testSystem.addComponent("water", 10.0);

        testSystem.createDatabase(true);
        testSystem.setMixingRule("HV");

        testSystem.init(0);
        double[] sample1 = {testSystem.getPressure(), testSystem.getTemperature()};
        double[] standardDeviation1 = {0.01};
        double val = Double.parseDouble(dataSet.getString("L2"));
        double sdev = val / 100.0;
        SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(Double.toString(testSystem.getTemperature()));
        double[] parameterGuess = {4898.64, -111.76}; // , -0.1, -0.44}; //, 0.07}; //propane

        function.setInitialGuess(parameterGuess);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    dataSet = database.getResultSet(
        "SELECT * FROM LuciaData8 WHERE Component='nitrogen' AND ID>=1014 AND ID<=1045 AND Temperature<373.0"); // AND
                                                                                                                // ID<3000
                                                                                                                // AND
                                                                                                                // Temperature>250
                                                                                                                // AND
                                                                                                                // Pressure<7000000000
                                                                                                                // AND
                                                                                                                // Temperature<600
                                                                                                                // AND
                                                                                                                // Y<>NULL
                                                                                                                // AND
                                                                                                                // Y>0.0000000001
                                                                                                                // ORDER
                                                                                                                // BY
                                                                                                                // Temperature,Pressure");
                                                                                                                // AND
                                                                                                                // Reference='Houghton1957'
                                                                                                                // AND
                                                                                                                // Reference<>'Nighswander1989'
                                                                                                                // AND
                                                                                                                // Temperature>278.15
                                                                                                                // AND
                                                                                                                // Temperature<383.15
                                                                                                                // AND
                                                                                                                // Pressure<60.01325");
    try {
      int p = 0;
      logger.info("adding....");
      while (dataSet.next() && p < 100) {
        p++;
        BinaryHVParameterFittingToSolubilityData function =
            new BinaryHVParameterFittingToSolubilityData(0, 0);
        SystemInterface testSystem =
            new SystemSrkSchwartzentruberEos(Double.parseDouble(dataSet.getString("Temperature")),
                Double.parseDouble(dataSet.getString("Pressure")) / 1e5);
        testSystem.addComponent("nitrogen", 10.0);
        testSystem.addComponent("water", 1000.0);
        testSystem.setMixingRule("HV");
        testSystem.init(0);
        double[] sample1 = {testSystem.getPressure(), testSystem.getTemperature()};
        double[] standardDeviation1 = {0.01};
        double val = 1.0 - Double.parseDouble(dataSet.getString("Y"));
        double sdev = val / 100.0;
        SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);
        sample.setReference(Double.toString(testSystem.getTemperature()));
        double[] parameterGuess = {4898.64, -111.76}; // , -0.1, -0.44}; //, 0.07}; //propane
        function.setInitialGuess(parameterGuess);
        sample.setDescription(Double.toString(testSystem.getTemperature()));
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }
    /*
     * dataSet = database.getResultSet(
     * "SELECT * FROM LuciaData8 WHERE Component='propane'); // AND Temperature>270 AND Temperature<400 AND Pressure<700000000 AND L1<>NULL ORDER BY Temperature,Pressure"
     * ); // AND Reference='Houghton1957' AND Reference<>'Nighswander1989' AND Temperature>278.15
     * AND Temperature<383.15 AND Pressure<60.01325");
     * 
     * try{ int p=0; logger.info("adding...."); while(!dataSet.next() && p<10){ p++;
     * BinaryHVParameterFittingToSolubilityData function = new
     * BinaryHVParameterFittingToSolubilityData(0,0);
     * 
     * SystemInterface testSystem = new
     * SystemSrkSchwartzentruberEos(Double.parseDouble(dataSet.getString( "Temperature")),
     * Double.parseDouble(dataSet.getString("Pressure"))/1e5);
     * 
     * testSystem.addComponent("propane", 10.0); testSystem.addComponent("water", 10.0);
     * 
     * //testSystem.createDatabase(true); testSystem.setMixingRule("HV");
     * 
     * testSystem.init(0); double sample1[] = {testSystem.getPressure(),
     * testSystem.getTemperature()}; double standardDeviation1[] = {0.01}; // std.dev temperature
     * double val = 1.0-Double.parseDouble(dataSet.getString("L1")); double sdev = val/100.0;
     * SampleValue sample = new SampleValue(val, sdev, sample1, standardDeviation1);
     * sample.setFunction(function); sample.setThermodynamicSystem(testSystem);
     * sample.setReference(Double.toString(testSystem.getTemperature())); double parameterGuess[]
     * ={3517,-1584, -0.1, -0.44, 0.07}; //propane function.setInitialGuess(parameterGuess);
     * sampleList.add(sample); } } catch(Exception ex){ logger.error("database error" + ex); }
     */
    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    // optim.displayResult();
    // optim.displayCurveFit();
    optim.displayResult();
    // optim.writeToTextFile("c:/testFit.txt");
  }
}
