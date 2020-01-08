package neqsim.thermo.util.example;

import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicOperations.ThermodynamicOperations;
import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *b
 * Created on 27. september 2001, 09:43
 */

/*
 *
 * @author  esol
 * @version
 */
public class TestPCSAFT1_1 {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(TestPCSAFT1_1.class);

    /** Creates new TPflash */
    public TestPCSAFT1_1() {
    }

    public static void main(String args[]) {
        double pressure = 5.0;
        String[] componentName = {"methane", "ethane", "propane", "i-butane", "n-butane", "benzene"};
        double[] compositions = {93.121, 3.04830, 0.9936, 1.0323, 1.5099, 0.2948};
        double[] uncertcompositions = {1.1405, 0.1056, 0.0466, 0.0358, 0.0523, 0.0138};
        double[] runcompositions = new double[componentName.length];
        SystemInterface testSystem = new SystemSrkEos(273.14, pressure);
        double pres = 0.0;
        for (int p = 0; p < 1; p++) {
            pres += 5.0;
            for (int k = 0; k < 1; k++) {
                testSystem = new SystemSrkEos(testSystem.getTemperature(), pres);
                for (int i = 0; i < componentName.length; i++) {
                    double newVar = cern.jet.random.Normal.staticNextDouble(compositions[i], uncertcompositions[i]);
                    newVar = cern.jet.random.Normal.staticNextDouble(compositions[i], uncertcompositions[i]);
                    runcompositions[i] = compositions[i] + newVar;
                    testSystem.addComponent(componentName[i], runcompositions[i]);
                }
                //testSystem.createDatabase(true);
                //testSystem.setMixingRule(1);
                ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
                testSystem.init(0);
                try {
                    testOps.dewPointTemperatureFlash();
                    logger.info("pressure " + testSystem.getPressure() + " dew point " + testSystem.getTemperature());
                } catch (Exception e) {
                    logger.error(e.toString(), e);
                }
            }
        }

    }
}
