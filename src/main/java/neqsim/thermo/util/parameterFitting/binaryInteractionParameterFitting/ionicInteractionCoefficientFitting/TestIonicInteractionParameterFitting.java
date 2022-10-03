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
 * <p>
 * TestIonicInteractionParameterFitting class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestIonicInteractionParameterFitting {
  static Logger logger = LogManager.getLogger(TestIonicInteractionParameterFitting.class);

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
    NeqSimDataBase database = new NeqSimDataBase();
    // ResultSet dataSet = database.getResultSet( "SELECT * FROM CO2KurCor WHERE
    // Reference<>'Bahiri1984' AND Temperature<373.15 ORDER BY
    // wtMDEA,Temperature,Reference,loading");
    // ResultSet dataSet = database.getResultSet( "SELECT * FROM CO2KurCor WHERE
    // Reference<>'Bahiri1984' AND Temperature<393.15 AND loading>0.05 AND loading<1.2 AND
    // VapourPressure1<15.0 AND wtMDEA>40 AND wtMDEA<60 ORDER BY
    // wtMDEA,Temperature,Reference,loading");

    // //ResultSet dataSet = database.getResultSet( "SELECT * FROM CO2KurCor WHERE
    // Reference<>'Bahiri1984' AND wtMDEA>40.0 AND Temperature>=280.15 AND Temperature<=470.15
    // AND loading>0.00002 AND VapourPressure1<25.0 ORDER BY wtMDEA,Temperature,Reference");
    // // ResultSet dataSet = database.getResultSet( "SELECT * FROM activityCoefficientTable
    // WHERE Component1='MDEA' AND Component2='water'");AND Reference='Lemoine2000'
    // //ResultSet dataSet = database.getResultSet( "SELECT * FROM CO2KurCor WHERE
    // Reference='Austgen1991' AND loading>0.01");

    // try{
    // int i=0;
    // while(dataSet.next() && i<450){
    // i++;
    // IonicInteractionParameterFittingFunction function = new
    // IonicInteractionParameterFittingFunction();
    // //IonicInteractionParameterFittingFunction_CO2 function = new
    // IonicInteractionParameterFittingFunction_CO2();
    // //double guess[] = {-0.0005842392, 0.0003621714, 0.0481887797,
    // 239.2501170265}; //{-0.0000629, 0.00001255, 0.05, 240.8165}; //,
    // 0.311975E-4}; //,0.0001439}; //, 0.00000001439}; //, -0.0000745697}; //, 263.73,
    // 466.41,0.1,0.1}; //, 1.0,1.0}; //;{-2.9222e-6, -7.991E-6, 3.924E-6}; //,1.0,1.0}; //,
    // -7.223e-4};
    // //double bounds[][] =
    // {{-0.0055,0.0055}};
    // //,{-0.0055,0.0055},{-10.001,10.001},{-1.0015,1.0015},{-320.0015,320.0015},{-320.901,320.900195},{-1.0,1000},{-0.800001,0.8},{-80000.01,20000.8},{-0.01,10.6},{-0.01,0.0015},{-0.01,0.0015}};
    // //double guess[] = {0.0000231069, 0.0001092540, 0.00001046762}; //, -0.0001190554}; //,
    // 1e-3,1e-3,1e-3,1e-3}; //, 1110.0e-8}; //, 0.0001052339, -0.0001046940}; //,-2753.0e-8,
    // 13.01e-8}; //, 242.6021196937,-7858.0,-36.7816}; //, -2753.0e-4, 13.01e-4}; //,
    // -0.00002653966}; //, -0.000000745697}; //,-2753.0e-4, 13.01e-4}; //,
    // 0.0000152542010}; //,4.5}; //, -2753.0e-4, 13.01e-4}; //, 240.2701596331}; //
    // -2753.7379912645, 13.0150379323}; //, -2.5254669805767994E-6}; //,0.78000000000}; //
    // 241.2701596331, 0.8000000000}; //, 3.162827E-4, -2.5254669805767994E-4}; //,
    // 239.2501170265}; //, 3.11975E-5};
    // double guess[] = {0.0000260019, -0.0001172396, 0.0000615323, -0.0001285810}; //,
    // -0.0004677550, -0.0000389863}; //, 2.88281314E-4}; // detailed reactions
    // // -0,0000186524 -0,0000470206 0,0000816440 -0,0001147055 -0,1422977509 -0,0351820850
    // 0,1531978514 -0,0062978601
    // //double guess[] = {0,0041639953 0,0009961457};,
    // //double guess[] = {-2753.0e-4, 13.01e-4}; // 263.73, -5.717, 8.6};
    // //SystemInterface testSystem = new SystemElectrolyteCPA((273.15+25.0), 1.0);
    // SystemInterface testSystem = new SystemFurstElectrolyteEos((273.15+25.0), 1.0);
    // testSystem.addComponent("CO2", Double.parseDouble(dataSet.getString("x1")));
    // testSystem.addComponent("MDEA", Double.parseDouble(dataSet.getString("x2")));
    // testSystem.addComponent("water", Double.parseDouble(dataSet.getString("x3")));
    // double temperature = Double.parseDouble(dataSet.getString("Temperature"));
    // double pressure = Double.parseDouble(dataSet.getString("VapourPressure1"));
    // testSystem.setTemperature(temperature);
    // testSystem.setPressure(pressure+1.0);
    // testSystem.chemicalReactionInit();
    // // testSystem.createDatabase(true);
    // testSystem.setMixingRule(4);
    // testSystem.init(0);
    // double sample1[] =
    // {testSystem.getPhase(0).getComponent(0).getNumberOfmoles()/testSystem.getPhase(0).getComponent(1).getNumberOfmoles()};
    //
    // double standardDeviation1[] = {0.01};
    // double stddev = pressure; //Double.parseDouble(dataSet.getString("StandardDeviation"))
    // SampleValue sample = new SampleValue(pressure, stddev, sample1, standardDeviation1);
    // function.setInitialGuess(guess);
    // //function.setBounds(bounds);
    // sample.setFunction(function);
    // double wtpr = Double.parseDouble(dataSet.getString("wtMDEA"));
    // sample.setReference(dataSet.getString("Reference") + " " + temperature);
    // sample.setDescription(Double.toString(wtpr));
    // sample.setThermodynamicSystem(testSystem);
    // sampleList.add(sample);
    // }
    // }
    // catch(Exception ex){
    // logger.error("database error" + ex);
    // }

    double[] guess = {-0.0001868490, -0.0006868943, -0.0000210224, -0.0002324934, 0.0005};
    ResultSet dataSet = database.getResultSet(
        "SELECT * FROM CO2waterMDEA2 WHERE Temperature<'393.15' AND PressureCO2<'20' AND Reference<>'GPA'");

    try {
      int i = 0;
      while (dataSet.next() && i < 25) {
        int ID = Integer.parseInt(dataSet.getString("ID"));
        if ((ID > 56 && ID < 64) || (ID > 92 && ID < 101) || (ID > 123 && ID < 131)) { // 75
                                                                                       // wt%
                                                                                       // amine
          continue;
        }
        if (ID == 155) {
          continue; // AAD >100
        }
        if (ID == 29 || ID == 28 || ID == 258) {
          continue; // large values of Pexp/Pcalc
        }

        i++;
        IonicInteractionParameterFittingFunction function =
            new IonicInteractionParameterFittingFunction();
        // IonicInteractionParameterFittingFunction_CO2 function = new
        // IonicInteractionParameterFittingFunction_CO2();
        // double guess[] = {-0.0005842392, 0.0003621714, 0.0481887797,
        // 239.2501170265}; //{-0.0000629, 0.00001255, 0.05, 240.8165}; //,
        // 0.311975E-4}; //,0.0001439}; //, 0.00000001439}; //, -0.0000745697}; //, 263.73,
        // 466.41,0.1,0.1}; //, 1.0,1.0}; //;{-2.9222e-6, -7.991E-6,
        // 3.924E-6}; //,1.0,1.0}; //, -7.223e-4};
        // double bounds[][] =
        // {{-0.0055,0.0055}};
        // //,{-0.0055,0.0055},{-10.001,10.001},{-1.0015,1.0015},{-320.0015,320.0015},{-320.901,320.900195},{-1.0,1000},{-0.800001,0.8},{-80000.01,20000.8},{-0.01,10.6},{-0.01,0.0015},{-0.01,0.0015}};
        // double guess[] = {0.0000231069, 0.0001092540, 0.00001046762}; //,
        // -0.0001190554}; //, 1e-3,1e-3,1e-3,1e-3}; //, 1110.0e-8}; //, 0.0001052339,
        // -0.0001046940}; //,-2753.0e-8, 13.01e-8}; //,
        // 242.6021196937,-7858.0,-36.7816}; //, -2753.0e-4, 13.01e-4}; //,
        // -0.00002653966}; //, -0.000000745697}; //,-2753.0e-4, 13.01e-4}; //,
        // 0.0000152542010}; //,4.5}; //, -2753.0e-4, 13.01e-4}; //, 240.2701596331}; //
        // -2753.7379912645, 13.0150379323}; //,
        // -2.5254669805767994E-6}; //,0.78000000000}; // 241.2701596331,
        // 0.8000000000}; //, 3.162827E-4, -2.5254669805767994E-4}; //,
        // 239.2501170265}; //, 3.11975E-5};
        // double guess[] = {0.0000260019, -0.0001172396, 0.0000615323,
        // -0.0001285810}; //, -0.0004677550, -0.0000389863}; //, 2.88281314E-4}; //
        // detailed reactions
        // -0,0000186524 -0,0000470206 0,0000816440 -0,0001147055 -0,1422977509
        // -0,0351820850 0,1531978514 -0,0062978601
        // double guess[] = {0,0041639953 0,0009961457};,
        // double guess[] = {-2753.0e-4, 13.01e-4}; // 263.73, -5.717, 8.6};
        // SystemInterface testSystem = new SystemElectrolyteCPA((273.15+25.0), 1.0);
        SystemInterface testSystem = new SystemFurstElectrolyteEos((273.15 + 25.0), 1.0);
        testSystem.addComponent("CO2", Double.parseDouble(dataSet.getString("x1")));
        testSystem.addComponent("MDEA", Double.parseDouble(dataSet.getString("x3")));
        testSystem.addComponent("water", Double.parseDouble(dataSet.getString("x2")));
        double temperature = Double.parseDouble(dataSet.getString("Temperature"));
        double pressure = Double.parseDouble(dataSet.getString("PressureCO2"));
        testSystem.setTemperature(temperature);
        testSystem.setPressure(pressure + 1.0);
        testSystem.chemicalReactionInit();
        // testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);
        double[] sample1 = {testSystem.getPhase(0).getComponent(0).getNumberOfmoles()
            / testSystem.getPhase(0).getComponent(1).getNumberOfmoles()};
        double[] standardDeviation1 = {0.01};
        double stddev = pressure; // Double.parseDouble(dataSet.getString("StandardDeviation"))
        SampleValue sample = new SampleValue(pressure, stddev, sample1, standardDeviation1);
        function.setInitialGuess(guess);
        // function.setBounds(bounds);
        sample.setFunction(function);
        sample.setReference(dataSet.getString("Reference"));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    dataSet = database.getResultSet(
        "SELECT * FROM CO2waterMDEA2 WHERE Temperature<'393.15' AND Pressure<'20' AND Reference<>'GPA'");

    try {
      int i = 0;
      while (dataSet.next() && i < 2) {
        int ID = Integer.parseInt(dataSet.getString("ID"));
        if ((ID > 56 && ID < 64) || (ID > 92 && ID < 101) || (ID > 123 && ID < 131)) { // 75
                                                                                       // wt%
                                                                                       // amine
          continue;
        }
        if (ID == 155) {
          continue; // AAD >100
        }
        if (ID == 29 || ID == 28 || ID == 258) {
          continue; // large values of Pexp/Pcalc
        }

        i++;
        IonicInteractionParameterFittingFunction_1 function =
            new IonicInteractionParameterFittingFunction_1();
        // IonicInteractionParameterFittingFunction_CO2 function = new
        // IonicInteractionParameterFittingFunction_CO2();
        // double guess[] = {-0.0005842392, 0.0003621714, 0.0481887797,
        // 239.2501170265}; //{-0.0000629, 0.00001255, 0.05, 240.8165}; //,
        // 0.311975E-4}; //,0.0001439}; //, 0.00000001439}; //, -0.0000745697}; //, 263.73,
        // 466.41,0.1,0.1}; //, 1.0,1.0}; //;{-2.9222e-6, -7.991E-6,
        // 3.924E-6}; //,1.0,1.0}; //, -7.223e-4};
        // double bounds[][] =
        // {{-0.0055,0.0055}};
        // //,{-0.0055,0.0055},{-10.001,10.001},{-1.0015,1.0015},{-320.0015,320.0015},{-320.901,320.900195},{-1.0,1000},{-0.800001,0.8},{-80000.01,20000.8},{-0.01,10.6},{-0.01,0.0015},{-0.01,0.0015}};
        // double guess[] = {0.0000231069, 0.0001092540, 0.00001046762}; //,
        // -0.0001190554}; //, 1e-3,1e-3,1e-3,1e-3}; //, 1110.0e-8}; //, 0.0001052339,
        // -0.0001046940}; //,-2753.0e-8, 13.01e-8}; //,
        // 242.6021196937,-7858.0,-36.7816}; //, -2753.0e-4, 13.01e-4}; //,
        // -0.00002653966}; //, -0.000000745697}; //,-2753.0e-4, 13.01e-4}; //,
        // 0.0000152542010}; //,4.5}; //, -2753.0e-4, 13.01e-4}; //, 240.2701596331}; //
        // -2753.7379912645, 13.0150379323}; //,
        // -2.5254669805767994E-6}; //,0.78000000000}; // 241.2701596331,
        // 0.8000000000}; //, 3.162827E-4, -2.5254669805767994E-4}; //,
        // 239.2501170265}; //, 3.11975E-5};
        // double guess[] = {0.0000260019, -0.0001172396, 0.0000615323,
        // -0.0001285810}; //, -0.0004677550, -0.0000389863}; //, 2.88281314E-4}; //
        // detailed reactions
        // -0,0000186524 -0,0000470206 0,0000816440 -0,0001147055 -0,1422977509
        // -0,0351820850 0,1531978514 -0,0062978601
        // double guess[] = {0,0041639953 0,0009961457};,
        // double guess[] = {-2753.0e-4, 13.01e-4}; // 263.73, -5.717, 8.6};
        // SystemInterface testSystem = new SystemElectrolyteCPA((273.15+25.0), 1.0);
        SystemInterface testSystem = new SystemFurstElectrolyteEos((273.15 + 25.0), 1.0);
        testSystem.addComponent("CO2", Double.parseDouble(dataSet.getString("x1")));
        testSystem.addComponent("MDEA", Double.parseDouble(dataSet.getString("x3")));
        testSystem.addComponent("water", Double.parseDouble(dataSet.getString("x2")));
        double temperature = Double.parseDouble(dataSet.getString("Temperature"));
        double pressure = Double.parseDouble(dataSet.getString("Pressure"));
        testSystem.setTemperature(temperature);
        testSystem.setPressure(pressure);
        testSystem.chemicalReactionInit();
        // testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);
        double[] sample1 = {testSystem.getPhase(0).getComponent(0).getNumberOfmoles()
            / testSystem.getPhase(0).getComponent(1).getNumberOfmoles()};
        double[] standardDeviation1 = {0.01};
        double stddev = pressure; // Double.parseDouble(dataSet.getString("StandardDeviation"))
        SampleValue sample = new SampleValue(pressure, stddev, sample1, standardDeviation1);
        function.setInitialGuess(guess);
        // function.setBounds(bounds);
        sample.setFunction(function);
        sample.setReference(dataSet.getString("Reference"));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations

    optim.solve();
    // optim.runMonteCarloSimulation();
    // optim.displayCurveFit();
    // optim.displayGraph();
    optim.displayCurveFit();
    optim.writeToTextFile("c:/testFit.txt");
  }
}
