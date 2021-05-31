package neqsim.thermo.util.example;

import neqsim.processSimulation.processEquipment.separator.ThreePhaseSeparator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.stream.StreamInterface;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import org.apache.logging.log4j.*;

/*
 * TPflash.java
 *
 * Created on 27. september 2001, 09:43
 */

/*
*
* @author  esol
* @version
*/
public class ReadFluidData2 {

    private static final long serialVersionUID = 1000;
    static Logger logger = LogManager.getLogger(ReadFluidData.class);

    /**
     * Creates new TPflash
     */
    public ReadFluidData2() {
    }

    public static void main(String args[]) {

        SystemInterface testSystem = new SystemSrkEos(273.15 + 30.0, 10.0);//
        // testSystem =
        // testSystem.readObjectFromFile("C:/temp/neqsimfluids/-65919.68493879325.neqsim",
        testSystem = testSystem.readObjectFromFile("c:/temp/neqsimfluidwater.neqsim", "");
        testSystem.init(0);
        testSystem.display();
        Stream stream_1 = new Stream("Stream1", testSystem);
        ThreePhaseSeparator separator = new ThreePhaseSeparator("Separator", stream_1);
        stream_1.run();
        separator.run();
        separator.displayResult();
        separator.run();
        StreamInterface gas = separator.getGasOutStream();
        StreamInterface oil = separator.getOilOutStream();
        StreamInterface water = separator.getWaterOutStream();
        // gas.run();
        gas.displayResult();
        StreamInterface str1 = separator.getGasOutStream();
        int a = 3;

        // testSystem.getPhase(1).getN;
        /*
         * testSystem.init(0); testSystem.setPressure(100.0);
         * testSystem.setTemperature(273.15 + 15.0); // // ""); //
         * testSystem.addComponent("water", 1.0); // testSystem.setMixingRule(2); //
         * testSystem.setMultiPhaseCheck(true); //testSystem.setMultiPhaseCheck(false);
         * ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
         * 
         * try { testOps.TPflash(); testSystem.display();
         * testOps.PSflash(-123.108602625942); testSystem.display();
         * testSystem.setPressure(100.0); testOps.PSflash(-119.003271056256);
         * testSystem.display(); System.out.println("entropy " +
         * testSystem.getEntropy()); //testSystem.setPressure(100.0);
         * //testOps.PSflash(-1.503016881785468e+02); //testSystem.display();
         * //testSystem.setPressure(100.0); // testOps.PSflash(-1.266377583884310e+02);
         * } catch (Exception e) { logger.error(e.toString()); }
         */
    }
}
