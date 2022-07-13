package neqsim.physicalProperties.util.parameterFitting.binaryComponentParameterFitting.diffusivity;

import java.sql.ResultSet;
import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.util.database.NeqSimDataBase;

/**
 * <p>
 * TestDiffusivity class.
 * </p>
 *
 * @author Even Solbraa
 * @version $Id: $Id
 */
public class TestDiffusivity {
    static Logger logger = LogManager.getLogger(TestDiffusivity.class);

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
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM binaryliquiddiffusioncoefficientdata WHERE ComponentSolute='CO2' AND ComponentSolvent='water'");

        try {
            logger.info("adding....");
            while (dataSet.next()) {
                DiffusivityFunction function = new DiffusivityFunction();
                double guess[] = {0.001};
                function.setInitialGuess(guess);
                SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                testSystem.addComponent(dataSet.getString("ComponentSolute"), 1.0e-10);
                testSystem.addComponent(dataSet.getString("ComponentSolvent"), 1.0);
                testSystem.createDatabase(true);
                testSystem.setPressure(Double.parseDouble(dataSet.getString("Pressure")));
                testSystem.setTemperature(Double.parseDouble(dataSet.getString("Temperature")));
                testSystem.init(0);
                testSystem.setPhysicalPropertyModel(4);
                testSystem.initPhysicalProperties();
                double sample1[] = {testSystem.getTemperature()};
                double standardDeviation1[] = {0.1};
                SampleValue sample = new SampleValue(
                        Double.parseDouble(dataSet.getString("DiffusionCoefficient")), 0.01,
                        sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setThermodynamicSystem(testSystem);
                sampleList.add(sample);
            }
        } catch (Exception e) {
            logger.error("database error" + e);
        }

        // double sample1[] = {0.1};
        // for(int i=0;i<sampleList.size();i++){
        // logger.info"ans: " + ((SampleValue)sampleList.get(i)).getFunction().calcValue(sample1));
        // }

        SampleSet sampleSet = new SampleSet(sampleList);
        optim.setSampleSet(sampleSet);

        // do simulations
        // optim.solve();
        // optim.runMonteCarloSimulation();
        //optim.displayGraph();
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
