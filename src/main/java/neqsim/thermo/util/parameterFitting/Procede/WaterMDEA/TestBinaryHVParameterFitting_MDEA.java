package neqsim.thermo.util.parameterFitting.Procede.WaterMDEA;

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
 * <p>
 * TestBinaryHVParameterFitting_MDEA class. This program calculated Water - MDEA HV interaction
 * parameters. Two types of data is available. VLE data and freezing point depression data
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestBinaryHVParameterFitting_MDEA {
  static Logger logger = LogManager.getLogger(TestBinaryHVParameterFitting_MDEA.class);

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
    double gamma1;
    double Hm;
    double act1;
    double act2;
    // inserting samples from database
    NeqSimDataBase database = new NeqSimDataBase();

    // double guess[] = {1201, -1461, -7.24, 5.89, 0.21}; //Even Solbraa
    // double guess[] = {733.1497651631, -1100.3362377120, -6.0060055689,
    // 5.0938556111, 0.2082636701}; // Ans 2 using Heat of mixing as well
    double[] guess =
        {-5596.6518968945, 3995.5032952165, 10.9677849623, -8.0407258862, 0.2703018372};

    ResultSet dataSet = database.getResultSet("SELECT * FROM WaterMDEA WHERE ID<62");
    /*
     * try{ int i=0; logger.info("adding...."); while(dataSet.next()){
     * BinaryHVParameterFittingFunction_MDEA function = new BinaryHVParameterFittingFunction_MDEA();
     * function.setInitialGuess(guess);
     * 
     * ID = Double.parseDouble(dataSet.getString("ID")); pressure =
     * Double.parseDouble(dataSet.getString("Pressure")); temperature =
     * Double.parseDouble(dataSet.getString("Temperature")); x1 =
     * Double.parseDouble(dataSet.getString("x1")); x2 =
     * Double.parseDouble(dataSet.getString("x2"));
     * 
     * //if(ID<35) // continue;
     * 
     * SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1.5*pressure);
     * testSystem.addComponent("water",x1); testSystem.addComponent("MDEA", x2);
     * 
     * logger.info("...........ID............."+ ID);
     * 
     * testSystem.createDatabase(true); testSystem.setMixingRule(4); testSystem.init(0);
     * 
     * double sample1[] = {temperature}; double standardDeviation1[] = {0.1}; double stddev =
     * pressure/100.0; SampleValue sample = new SampleValue((pressure), stddev, sample1,
     * standardDeviation1);
     * 
     * sample.setFunction(function); sample.setReference(Double.toString(ID));
     * sample.setThermodynamicSystem(testSystem); sampleList.add(sample); } } catch(Exception ex){
     * logger.info("database error" + ex); }
     * 
     * 
     * dataSet = database.getResultSet( "SELECT * FROM WaterMDEA WHERE ID>61 AND ID<87");
     * 
     * try{ int i=0;
     * 
     * logger.info("adding...."); while(dataSet.next()){ i++;
     * 
     * BinaryHVParameterFittingFunction_MDEA function = new
     * BinaryHVParameterFittingFunction_MDEA(1,1); function.setInitialGuess(guess);
     * 
     * ID = Double.parseDouble(dataSet.getString("ID")); temperature =
     * Double.parseDouble(dataSet.getString("Temperature")); gamma1 =
     * Double.parseDouble(dataSet.getString("gamma1")); x1 =
     * Double.parseDouble(dataSet.getString("x1")); x2 =
     * Double.parseDouble(dataSet.getString("x2"));
     * 
     * 
     * SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, 1.0);
     * testSystem.addComponent("water",x1); testSystem.addComponent("MDEA", x2);
     * logger.info("...........ID............."+ ID);
     * 
     * testSystem.createDatabase(true); testSystem.setMixingRule(4); testSystem.init(0);
     * 
     * double sample1[] = {temperature}; double standardDeviation1[] = {0.1}; double stddev =
     * gamma1/100.0; SampleValue sample = new SampleValue(gamma1, stddev, sample1,
     * standardDeviation1);
     * 
     * sample.setFunction(function); sample.setReference(Double.toString(ID));
     * sample.setThermodynamicSystem(testSystem); sampleList.add(sample); } } catch(Exception ex){
     * logger.info("database error" + ex); }
     */

    /*
     * dataSet = database.getResultSet( "SELECT * FROM WaterMDEA WHERE ID>86");
     * 
     * try{ int i=0;
     * 
     * logger.info("adding...."); while(dataSet.next()){ i++;
     * 
     * BinaryHVParameterFittingFunction_MDEA function = new
     * BinaryHVParameterFittingFunction_MDEA(1,2); function.setInitialGuess(guess);
     * 
     * ID = Double.parseDouble(dataSet.getString("ID")); temperature =
     * Double.parseDouble(dataSet.getString("Temperature")); Hm =
     * Double.parseDouble(dataSet.getString("HeatOfMixing")); x1 =
     * Double.parseDouble(dataSet.getString("x1")); x2 =
     * Double.parseDouble(dataSet.getString("x2"));
     * 
     * 
     * SystemInterface testSystem = new SystemFurstElectrolyteEos(temperature, 1.0);
     * testSystem.addComponent("water",x1); testSystem.addComponent("MDEA", x2);
     * logger.info("...........ID............."+ ID);
     * 
     * testSystem.createDatabase(true); testSystem.setMixingRule(4); testSystem.init(0);
     * 
     * double sample1[] = {temperature}; double standardDeviation1[] = {0.1}; double stddev =
     * Hm/100.0; SampleValue sample = new SampleValue(Hm, stddev, sample1, standardDeviation1);
     * 
     * sample.setFunction(function); sample.setReference(Double.toString(ID));
     * sample.setThermodynamicSystem(testSystem); sampleList.add(sample); } } catch(Exception ex){
     * logger.info("database error" + ex); }
     */

    dataSet = database.getResultSet("SELECT * FROM WaterMDEAactivity WHERE ID<20");
    try {
      int i = 0;
      logger.info("adding....");
      while (dataSet.next()) {
        BinaryHVParameterFittingFunction_MDEA function =
            new BinaryHVParameterFittingFunction_MDEA(1, 3);
        function.setInitialGuess(guess);

        ID = Double.parseDouble(dataSet.getString("ID"));
        act1 = Double.parseDouble(dataSet.getString("act1"));
        temperature = Double.parseDouble(dataSet.getString("Temperature"));
        x1 = Double.parseDouble(dataSet.getString("x1"));

        SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1);
        testSystem.addComponent("water", x1);
        testSystem.addComponent("MDEA", 1 - x1);

        logger.info("...........ID............." + ID);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        double[] sample1 = {x1};
        double[] standardDeviation1 = {0.1};
        double stddev = act1 / 100.0;
        SampleValue sample = new SampleValue(act1, stddev, sample1, standardDeviation1);

        sample.setFunction(function);
        sample.setReference(Double.toString(ID));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.info("database error" + ex);
    }

    dataSet = database.getResultSet("SELECT * FROM WaterMDEAactivity WHERE ID>19");
    try {
      int i = 0;
      logger.info("adding....");
      while (dataSet.next()) {
        BinaryHVParameterFittingFunction_MDEA function =
            new BinaryHVParameterFittingFunction_MDEA(1, 4);
        function.setInitialGuess(guess);

        ID = Double.parseDouble(dataSet.getString("ID"));
        act2 = Double.parseDouble(dataSet.getString("act2"));
        temperature = Double.parseDouble(dataSet.getString("Temperature"));
        x1 = Double.parseDouble(dataSet.getString("x1"));

        SystemInterface testSystem = new SystemSrkSchwartzentruberEos(temperature, 1);
        testSystem.addComponent("water", x1);
        testSystem.addComponent("MDEA", 1 - x1);

        logger.info("...........ID............." + ID);

        testSystem.createDatabase(true);
        testSystem.setMixingRule(4);
        testSystem.init(0);

        double[] sample1 = {x1};
        double[] standardDeviation1 = {0.1};
        double stddev = act2 / 100.0;
        SampleValue sample = new SampleValue(act2, stddev, sample1, standardDeviation1);

        sample.setFunction(function);
        sample.setReference(Double.toString(ID));
        sample.setThermodynamicSystem(testSystem);
        sampleList.add(sample);
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    // optim.solve();
    // optim.displayGraph();
    optim.displayCurveFit();
  }
}
