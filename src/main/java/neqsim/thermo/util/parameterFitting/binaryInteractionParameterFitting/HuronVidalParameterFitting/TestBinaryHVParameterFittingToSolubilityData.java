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
 * <p>TestBinaryHVParameterFittingToSolubilityData class.</p>
 *
 * @author Even Solbraa
 */
public class TestBinaryHVParameterFittingToSolubilityData {

    static Logger logger = LogManager.getLogger(TestBinaryHVParameterFittingToSolubilityData.class);


    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {

        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // inserting samples from database
        NeqSimExperimentDatabase database = new NeqSimExperimentDatabase();
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // binarySolubilityData WHERE ComponentSolute='CO2' AND ComponentSolvent='water'
        // AND Reference='Houghton1957' AND Reference<>'Nighswander1989' AND
        // Temperature>278.15 AND Temperature<363.15 AND Pressure<60.01325 ORDER BY
        // Temperature,Pressure");
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM binarySolubilityData WHERE ComponentSolute='oxygen' AND ComponentSolvent='water' ORDER BY Temperature,Pressure");

        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");
        // testSystem.addComponent(dataSet.getString("ComponentSolute"), 1.0);
        // testSystem.addComponent(dataSet.getString("ComponentSolvent"), 1.0);
        try {
            int p = 0;
            logger.info("adding....");
            while (dataSet.next() && p < 22) {
                p++;
                BinaryHVParameterFittingToSolubilityData function =
                        new BinaryHVParameterFittingToSolubilityData();
                // SystemInterface testSystem = new SystemSrkEos(280,1);
                // SystemInterface testSystem = new SystemFurstElectrolyteEos(280, 1.0);
                SystemInterface testSystem = new SystemSrkSchwartzentruberEos(290, 1.0);
                // SystemInterface testSystem = new SystemSrkMathiasCopeman(290, 1.0);
                testSystem.addComponent(dataSet.getString("ComponentSolute"), 1.0);
                testSystem.addComponent(dataSet.getString("ComponentSolvent"), 100.0);

                // testSystem.addComponent("CO2", 1.0);
                // testSystem.addComponent("water", 1.0);
                // testSystem.addComponent("MDEA", 1.000001);
                // testSystem.chemicalReactionInit();
                testSystem.createDatabase(true);
                testSystem.setMixingRule(4);

                // testSystem.getChemicalReactionOperations().solveChemEq(0);
                // testSystem.getChemicalReactionOperations().solveChemEq(1);

                // testSystem.isChemicalSystem(false);

                // testSystem.addComponent("NaPlus", 1.0e-10);
                // testSystem.addComponent("methane", 1.1);
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                logger.error("pressure " + testSystem.getPressure());
                testSystem.init(0);
                double sample1[] = {testSystem.getPressure(), testSystem.getTemperature()}; // temperature
                double standardDeviation1[] = {0.01}; // std.dev temperature // presure std.dev
                                                      // pressure
                SampleValue sample = new SampleValue(Double.parseDouble(dataSet.getString("x1")),
                        Double.parseDouble(dataSet.getString("StandardDeviation")), sample1,
                        standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sample.setReference(Double.toString(testSystem.getTemperature()));
                // double parameterGuess[] = {4799.35, -2772.29, 0.6381, -1.68096};
                // double parameterGuess[] = {5640.38, -3793.1, -4.42, 2.82}; // HV CO2
                // double parameterGuess[] = {7263.5285887088, -3712.3594920781, -7.1458168635,
                // 1.2714576276};//CO2-SRK-MC
                // double parameterGuess[] = {5251.7374371982, -3121.2788585048, -0.8420253536,
                // -0.5123316046}; // HV CO2 -PVT-sim
                double parameterGuess[] =
                        {2423.6600682957, -2136.4306560594, 1.9812435921, 1.4579901393}; // HV
                                                                                         // methane
                // double parameterGuess[] = {3204.3057406886, -2753.7379912645, -12.4728330162
                // , 13.0150379323}; // HV
                // double parameterGuess[] = {8.992E3, -3.244E3, -8.424E0, -1.824E0}; // HV
                // double parameterGuess[] = {-7.132E2, -3.933E2};//, 3.96E0, 9.602E-1}; //,
                // 1.239}; //WS
                function.setInitialGuess(parameterGuess);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        optim.solve();
        // optim.runMonteCarloSimulation();
        // optim.displayResult();
        optim.displayCurveFit();
        // optim.displayResult();
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
