package neqsim.thermo.util.parameterFitting.Procede.CO2WaterMDEA;

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
 * TestIonicInteractionParameterFitting_CO2 class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestIonicInteractionParameterFitting_CO2 {
  static Logger logger = LogManager.getLogger(TestIonicInteractionParameterFitting_CO2.class);

  /**
   * <p>
   * main.
   * </p>
   *
   * @param args an array of {@link java.lang.String} objects
   */
  @SuppressWarnings("unused")
  public static void main(String[] args) {
    LevenbergMarquardt optim = new LevenbergMarquardt();
    ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();
    double ID;

    double pressure;
    double temperature;
    double x1;
    double x2;
    double x3;
    double loading;
    // inserting samples from database
    NeqSimDataBase database = new NeqSimDataBase();
    ResultSet dataSet = database.getResultSet("SELECT * FROM CO2WaterMDEA WHERE ID<231");

    // Water, HCO3-, MDEA, CO2, Co3--

    // double guess[] = {0.0004463876, -0.0001475081, 0.0021294606, 0.0002761438,
    // -0.0003450177}; //Detailed reactions, no data for 75 wt% MDEA or temp > 400K,
    // more iterations
    double[] guess = {-0.0001660156, -0.0006035675, -0.0000068587, -0.0002164970}; // ,0.0005};
                                                                                   // //Detailed
                                                                                   // reactions,
                                                                                   // no data
                                                                                   // for
                                                                                   // 75 wt%
                                                                                   // MDEA or
                                                                                   // temp >
                                                                                   // 400K
                                                                                   // or
                                                                                   // loading <
                                                                                   // 0.1 and
                                                                                   // bias = -4%
                                                                                   // and AAD =
                                                                                   // 18% for
                                                                                   // loading
                                                                                   // >0.1
    // double guess[] = {0.0004164151, 0.0002034767, 0.0018993447, 0.0022461592,
    // -0.0008412103}; //Detailed reactions, no data for 75 wt% MDEA or temp > 400K
    // or loading > 0.1 bias of -7% and AAD= 27% for loading<0.1
    // double guess[] = {1e-10}; //Assuming values for all other species, except
    // OH-. Regression done only for data loading<0.1. {0.0004463876, -0.0001475081,
    // 0.0021294606, 0.0002761438, -0.0003450177}
    // double guess[] = {0.0004480870, -0.0001871372, 0.0021482035, 0.0002770425,
    // -0.0003886375}; //Detailed reactions, no data for 75 wt% MDEA or temp > 400K,
    // more iterations diff. Water - MDEA HV parameter
    // double guess[] = {1e-10, 1e-10, 1e-10, 1e-10, 1e-10}; //Debye - Huckle type
    // equation
    try {
      int i = 0;
      logger.info("adding....");
      while (dataSet.next()) {
        i++;
        IonicInteractionParameterFittingFunction_CO2 function =
            new IonicInteractionParameterFittingFunction_CO2();
        function.setInitialGuess(guess);

        ID = Integer.parseInt(dataSet.getString("ID"));
        pressure = Double.parseDouble(dataSet.getString("PressureCO2"));
        temperature = Double.parseDouble(dataSet.getString("Temperature"));
        x1 = Double.parseDouble(dataSet.getString("x1"));
        x2 = Double.parseDouble(dataSet.getString("x2"));
        x3 = Double.parseDouble(dataSet.getString("x3"));

        loading = x1 / x3;

        // if(loading <= 0.1) continue;

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
        if (temperature > 400) { // Since Wij are assumed to be temp. independent, higher
                                 // temp. are neglected
          continue;
        }

        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, 1.5 * pressure);
        testSystem.addComponent("CO2", x1);
        testSystem.addComponent("MDEA", x3);
        testSystem.addComponent("water", x2);

        logger.info("...........ID............." + ID);

        testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        double[] sample1 = {loading};
        double[] standardDeviation1 = {0.1};
        double stddev = (pressure / 100.0);
        SampleValue sample = new SampleValue((pressure), stddev, sample1, standardDeviation1);

        sample.setFunction(function);
        sample.setReference(Double.toString(ID));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception e) {
      logger.error("database error" + e);
    }

    dataSet = database.getResultSet("SELECT * FROM CO2WaterMDEA WHERE ID>230");

    try {
      int i = 0;

      logger.info("adding....");
      while (dataSet.next()) {
        i++;

        IonicInteractionParameterFittingFunction_CO2 function =
            new IonicInteractionParameterFittingFunction_CO2(1, 1);
        function.setInitialGuess(guess);

        ID = Integer.parseInt(dataSet.getString("ID"));
        pressure = Double.parseDouble(dataSet.getString("Pressure"));
        temperature = Double.parseDouble(dataSet.getString("Temperature"));
        x1 = Double.parseDouble(dataSet.getString("x1"));
        x2 = Double.parseDouble(dataSet.getString("x2"));
        x3 = Double.parseDouble(dataSet.getString("x3"));

        loading = x1 / x3;
        // if(loading <= 0.1) continue;

        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, pressure);
        testSystem.addComponent("CO2", x1);
        testSystem.addComponent("water", x2);
        testSystem.addComponent("MDEA", x3);
        logger.info("...........ID............." + ID);

        if (ID == 294 || ID == 295) {
          continue; // convergence problem
        }
        if (ID > 235 && ID < 244) {
          continue; // large error
        }
        if (ID > 246 && ID < 252) {
          continue; // large error
        }
        if (ID == 258 || ID == 322) {
          continue;
        }
        if (ID == 328 || ID == 329 || (ID > 332 && ID < 339)) {
          continue;
        }
        if (temperature > 400) { // Since Wij are assumed to be temp. independent, higher
                                 // temp. are neglected
          continue;
        }

        testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        double[] sample1 = {loading};
        double[] standardDeviation1 = {0.1};
        double stddev = (pressure / 100.0);
        SampleValue sample = new SampleValue((pressure), stddev, sample1, standardDeviation1);

        sample.setFunction(function);
        sample.setReference(Double.toString(ID));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception e) {
      logger.error("database error" + e);
    }

    dataSet = database.getResultSet("SELECT * FROM CO2WaterMDEAtest");

    try {
      int i = 0;
      logger.info("adding....");
      while (dataSet.next()) {
        i++;
        IonicInteractionParameterFittingFunction_CO2 function =
            new IonicInteractionParameterFittingFunction_CO2();
        function.setInitialGuess(guess);

        ID = Integer.parseInt(dataSet.getString("ID"));
        pressure = Double.parseDouble(dataSet.getString("PressureCO2"));
        temperature = Double.parseDouble(dataSet.getString("Temperature"));
        x1 = Double.parseDouble(dataSet.getString("x1"));
        x2 = Double.parseDouble(dataSet.getString("x2"));
        x3 = Double.parseDouble(dataSet.getString("x3"));

        loading = x1 / x3;
        // if(loading <= 0.1) continue;

        SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, 1.5 * pressure);
        testSystem.addComponent("CO2", x1);
        testSystem.addComponent("MDEA", x3);
        testSystem.addComponent("water", x2);

        logger.info("...........ID............." + ID);

        testSystem.chemicalReactionInit();
        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        double[] sample1 = {loading};
        double[] standardDeviation1 = {0.1};
        double stddev = (pressure / 100.0);
        SampleValue sample = new SampleValue((pressure), stddev, sample1, standardDeviation1);

        sample.setFunction(function);
        sample.setReference(Double.toString(ID));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception e) {
      logger.error("database error" + e);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    // optim.solve();
    // optim.displayGraph();
    optim.displayCurveFit();
  }
}
