package neqsim.thermo.util.parameterFitting.pureComponentParameterFitting.acentricFactorFitting;

import java.util.ArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import neqsim.statistics.parameterFitting.SampleSet;
import neqsim.statistics.parameterFitting.SampleValue;
import neqsim.statistics.parameterFitting.nonLinearParameterFitting.LevenbergMarquardt;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkSchwartzentruberEos;
import neqsim.thermo.system.SystemSrkTwuCoonParamEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 *
 * @author Even Solbraa
 * @version
 */
public class TestTwuCoon {
    static Logger logger = LogManager.getLogger(TestTwuCoon.class);

    public static void main(String[] args) {
        LevenbergMarquardt optim = new LevenbergMarquardt();
        ArrayList<SampleValue> sampleList = new ArrayList<SampleValue>();

        // String ComponentName = "CO2";
        // String ComponentName = "methane";
        // String ComponentName = "ethane";
        // String ComponentName = "propane";
        // String ComponentName = "n-butane";
        // String ComponentName = "i-butane";
        // String ComponentName = "i-pentane";
        // String ComponentName = "n-pentane";
        String ComponentName = "mercury";
        // String ComponentName = "neon";

        try {
            // for(int i=0;i<30;i++){
            TwuCoon function = new TwuCoon();
            // double guess[] = {1.4136010682288083,-0.6059654485151583,1.1018149483163808};
            // // CO2 chi sqr 0.45
            // double guess[] ={0.1228415979307727,0.9144707890284266,2.7419786111346327} ;
            // // Methane chi sqr 0.1425
            // double guess[] ={0.2996073181440976,0.8766268388933209,1.6792384004871077} ;
            // // ethane chi sqr 0.6
            // double guess[] ={0.4358258831879946,0.8661321788747547,1.3954328182729108} ;
            // // PROPANE chi sqr 0.57
            // double guess[] ={0.33180058324421624,0.8546927333111055,1.8658288576964368} ;
            // // n-butane chi sqr 0.66
            // double guess[] ={0.2179712800665,0.8488910728515817,2.284036968290834};
            // i-butane chi sqr 0.64
            // double guess[] ={0.3426699116420882,0.8518937813463485,1.9218752789862321} ;
            // // i-pentane chi sqr 1.71
            // double guess[] ={0.2728761064186051,0.8503101221977485,2.340728864856859} ;
            // // n-pentane chi sqr 0.827
            // double guess[] ={0.11262960694190573,0.9021997146737551,2.8749606780260866} ;
            // // nitrogen chi sqr 0.063
            // double guess[] ={0.08838047169500897,0.9400222937525736,3.4011901770700264} ;
            // // neon chi sqr 0.044
            // double guess[] = {0.0179791, 0.983218, 5.63251};
            double guess[] = {0.068584, 0.9784, 2.244};

            // SystemInterface testSystem = new SystemSrkSchwartzentruberEos(40, 1);
            // SystemInterface testSystem = new SystemSrkEos(280, 1);
            SystemInterface testSystem = new SystemSrkTwuCoonParamEos(280, 5);
            testSystem.addComponent(ComponentName, 100.0);
            testSystem.setMixingRule(2);

            testSystem.createDatabase(true);

            // function.setFittingParams(0, guess[0]);
            // function.setFittingParams(1, guess[1]);
            // function.setFittingParams(2, guess[2]);
            function.setInitialGuess(guess);

            SystemInterface System2 = new SystemSrkSchwartzentruberEos(280, 5);
            // SystemInterface System2 = new SystemSrkTwuCoonEos(280, 5);

            System2.addComponent(ComponentName, 100.0);
            System2.setMixingRule(2);
            ThermodynamicOperations Ops = new ThermodynamicOperations(System2);

            double Ttp = testSystem.getPhase(0).getComponent(0).getTriplePointTemperature();
            double TC = testSystem.getPhase(0).getComponent(0).getTC();

            for (int i = 0; i < 30; i++) {
                double temperature = Ttp + ((TC - Ttp) / 30) * i;
                // kan legge inn dewTflash for aa finne avvik til tilsvarende linje med
                // schwarzentruber... da ogsaa for flerkomponent blandinger istedenfor antoine
                // ligningen.
                // double pressure =
                // testSystem.getPhase(0).getComponent(0).getAntoineVaporPressure(temperature);
                System2.setTemperature(temperature);
                Ops.dewPointPressureFlash();
                double pressure = System2.getPressure();

                double sample1[] = {temperature}; // temperature
                double standardDeviation1[] = {0.1, 0.1, 0.1}; // std.dev temperature // presure
                                                               // std.dev pressure
                double val = Math.log(pressure);
                SampleValue sample = new SampleValue(val, val / 100.0, sample1, standardDeviation1);
                sample.setFunction(function);
                sample.setReference("Perry");
                sample.setThermodynamicSystem(testSystem);
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
        optim.displayCurveFit();
        // optim.writeToCdfFile("c:/testFit.nc");
        // optim.writeToTextFile("c:/testFit.txt");
    }
}
