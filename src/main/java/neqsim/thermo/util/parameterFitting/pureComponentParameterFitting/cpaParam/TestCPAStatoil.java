package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.cpaParam;

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
 * <p>TestCPAStatoil class.</p>
 *
 * @author Even Solbraa
 */
public class TestCPAStatoil {
    static Logger logger = LogManager.getLogger(TestCPAStatoil.class);

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();
        LevenbergMarquardt optim = new LevenbergMarquardt();
        // inserting samples from database
        NeqSimDataBase database = new NeqSimDataBase();
        ResultSet dataSet = database.getResultSet(
                "SELECT * FROM PureComponentVapourPressures WHERE ComponentName='MEG' AND Temperature<500.0");// AND
                                                                                                              // VapourPressure>0.00000000001
                                                                                                              // AND
                                                                                                              // Reference='Stull1947'");
        // ResultSet dataSet = database.getResultSet( "SELECT * FROM
        // activityCoefficientTable WHERE Component1='MDEA' AND Component2='water'");

        try {
            while (dataSet.next()) {
                CPAFunctionStatoil function = new CPAFunctionStatoil();

                SystemInterface testSystem = new SystemSrkCPAstatoil(280, 0.001);
                // SystemInterface testSystem = new SystemSrkEos(280, 0.001);
                testSystem.addComponent(dataSet.getString("ComponentName"), 100.0);
                testSystem.createDatabase(true); // legger til komponenter til systemet
                double sample1[] = {Double.parseDouble(dataSet.getString("Temperature"))}; // temperature
                double standardDeviation1[] = {0.1}; // std.dev temperature // presure std.dev
                                                     // pressure
                double val = Double.parseDouble(dataSet.getString("VapourPressure"));
                testSystem.setPressure(val);
                double stddev = val / 10.0;
                double logVal = Math.log(val);
                SampleValue sample = new SampleValue(val, stddev, sample1, standardDeviation1);
                testSystem.init(0);
                // double guess[] = {6602,2.45,0.27,353.69,0.05129};
                // double guess[] = {9.341E4,1.953E0,1.756E-1,92.69,0.129};

                // double guess[] =
                // {((ComponentSrk)testSystem.getPhase(0).getComponent(0)).geta(),((ComponentSrk)testSystem.getPhase(0).getComponent(0)).getb(),testSystem.getPhase(0).getComponent(0).getAcentricFactor(),0.04567};
                double guess[] = {0.7892765953, -1.0606510837, 2.2071936510};// water CPA statoil
                // double guess[] ={0.8581331725*0, -1.0053180150*0, 1.2736063639*0};//MEG CPA
                // statoil
                // double guess[] ={ 1.0008858863, 1.8649645470, -4.6720397496};//TEG CPA
                // statoil
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
        optim.setSampleSet(sampleSet);

        // do simulations
        // optim.solve();
        // optim.runMonteCarloSimulation();
        optim.displayCurveFit();
    }
}
