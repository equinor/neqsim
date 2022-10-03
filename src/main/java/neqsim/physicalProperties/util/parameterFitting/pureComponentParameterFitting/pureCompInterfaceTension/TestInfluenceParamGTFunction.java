package neqsim.physicalProperties.util.parameterFitting.pureComponentParameterFitting.pureCompInterfaceTension;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkCPAstatoil;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestInfluenceParamGTFunction class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestInfluenceParamGTFunction {
  static Logger logger = LogManager.getLogger(TestInfluenceParamGTFunction.class);

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

    NeqSimDataBase database = new NeqSimDataBase();
    // ComponentName<>'nC10'
    // AND
    // ComponentName<>'nC11'
    // AND
    // ComponentName<>'nC12'
    // AND
    // ComponentName<>'nC13'");
    boolean includePureCompData = true;

    // double guess[] = { -0.7708158524, 0.4990571549, 0.8645478315, -0.3509810630,
    // -0.1611763157}; // SRK param
    double guess[] =
        {-0.0286407191587279700, -1.85760887578596, 0.520588, -0.1386439759, 1.1216308727071944}; // CPA
                                                                                                  // param

    // double guess[] = {1.9286440937, -8.7271963910, 1.2495334818, -1.8975206092};
    // double guess[] = {-5.2897559010400935E-17, 7.103588505598196E-17}; //,
    // 1.1161368619, 0.8363538313}; // PR param

    try (ResultSet dataSet = database.getResultSet(
        "SELECT * FROM purecomponentsurfacetension2 WHERE ComponentName IN ('n-pentane','ethane','methane', 'propane','CO2', 'c-hexane','M-cy-C5', 'n-pentane','n-hexane', 'n-nonane','nC10')") // AND
    ) {
      while (dataSet.next() && includePureCompData) {
        InfluenceParamGTFunction function = new InfluenceParamGTFunction();
        function.setInitialGuess(guess);

        SystemInterface testSystem = new SystemSrkCPAstatoil(280, 10.1);
        testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
        testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
        testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
        testSystem.createDatabase(true);
        testSystem.setMixingRule(7);
        testSystem.init(0);
        testSystem.setNumberOfPhases(2);
        testSystem.getInterphaseProperties().setInterfacialTensionModel(2);
        testSystem.init(3);
        double sample1[] = {testSystem.getTemperature(), testSystem.getPressure()};
        double standardDeviation1[] = {0.1, 0.1};
        double surfTens = Double.parseDouble(dataSet.getString("SurfaceTension"));
        SampleValue sample =
            new SampleValue(surfTens, Double.parseDouble(dataSet.getString("StandardDeviation")),
                sample1, standardDeviation1);
        sample.setFunction(function);
        sample.setThermodynamicSystem(testSystem);

        sampleList.add(sample);

        Double.toString(sample.getFunction().calcValue(new double[0]));
        // double influenceParam = ((GTSurfaceTension)
        // testSystem.getInterphaseProperties().getSurfaceTensionModel(0)).getInfluenceParameter(surfTens
        // / 1.0e3, 0);
        // logger.error(testSystem.getTemperature() + " " + influenceParam);
        // double factor = influenceParam /
        // (testSystem.getPhase(0).getComponent(0).getAttractiveTerm().aT(testSystem.getTemperature())
        // * 1.0e-5) / Math.pow(((ComponentEosInterface)
        // testSystem.getPhase(0).getComponent(0)).calcb() * 1e-5, 2.0 / 3.0);
        // sample.setDescription((1.0 - testSystem.getTemperature() /
        // testSystem.getPhase(0).getComponent(0).getTC()) + " " +
        // Double.toString(factor));
        sample.setReference(testSystem.getPhase(0).getComponent(0).getComponentName());
      }
    } catch (Exception ex) {
      logger.error("database error" + ex);
    }

    /*
     * dataSet = database.getResultSet("NeqSimDataBase",
     * "SELECT * FROM BinaryComponentSurfaceTension WHERE Include=1"); // AND ComponentName<>'nC12'
     * AND ComponentName<>'nC13'"); boolean includeBinaryData = false;
     * 
     * try { logger.error("adding...."); while (dataSet.next() && includeBinaryData) {
     * InfluenceParamGTFunctionBinaryData function = new InfluenceParamGTFunctionBinaryData();
     * function.setInitialGuess(guess);
     * 
     * SystemInterface testSystem = new SystemPrEos(280, 0.1);
     * testSystem.addComponent(dataSet.getString("ComponentName1"), 10.10);
     * testSystem.addComponent(dataSet.getString("ComponentName2"), 1.10);
     * testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
     * testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature") ));
     * testSystem.createDatabase(true);
     * testSystem.getInterphaseProperties().setInterfacialTensionModel(1);
     * testSystem.setMixingRule(2); testSystem.init(0); testSystem.setNumberOfPhases(2);
     * testSystem.init(3); double sample1[] = {testSystem.getTemperature(),
     * testSystem.getPressure()}; double standardDeviation1[] = {0.1, 0.1}; double surfTens =
     * Double.parseDouble(dataSet.getString("SurfaceTension")); SampleValue sample = new
     * SampleValue(surfTens, Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
     * standardDeviation1); sample.setFunction(function); sample.setThermodynamicSystem(testSystem);
     * sampleList.add(sample);
     * 
     * Double.toString(sample.getFunction().calcValue(new double[0])); double influenceParam =
     * ((GTSurfaceTension) testSystem.getInterphaseProperties().getSurfaceTensionModel(0)).
     * getInfluenceParameter(surfTens / 1.0e3, 0); logger.error(testSystem.getTemperature() + " " +
     * influenceParam); double factor = influenceParam /
     * (testSystem.getPhase(0).getComponent(0).getAttractiveTerm().aT(testSystem. getTemperature())
     * * 1.0e-5) / Math.pow(((ComponentEosInterface) testSystem.getPhase(0).getComponent(0)).calcb()
     * * 1e-5, 2.0 / 3.0); sample.setDescription((1.0 - testSystem.getTemperature() /
     * testSystem.getPhase(0).getComponent(0).getTC()) + "  " + Double.toString(factor));
     * sample.setReference("binary data"); } } catch (Exception ex) { logger.error("database error"
     * + ex); }
     */
    SampleSet sampleSet = new SampleSet(sampleList);
    optim.setSampleSet(sampleSet);

    // do simulations
    optim.solve();
    // optim.runMonteCarloSimulation();
    optim.displayCurveFit();
    // optim.writeToTextFile("c:/testFit.txt");
  }
}
