package neqsim.processSimulation.util.example;

import neqsim.processSimulation.processEquipment.heatExchanger.HeatExchanger;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.util.Recycle;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

/**
 * <p>
 * HeatExchanger2 class.
 * </p>
 *
 * @author asmund
 * @version $Id: $Id
 * @since 2.2.3
 */
public class HeatExchanger2 {
    /**
     * This method is just meant to test the thermo package.
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 60.0), 20.00);
        testSystem.addComponent("methane", 120.00);
        testSystem.addComponent("ethane", 120.0);
        testSystem.addComponent("n-heptane", 30.0);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
        testOps.TPflash();

        Stream stream_Hot = new Stream("Stream1", testSystem);
        Stream stream_Cold = new Stream("Stream1", testSystem.clone());

        HeatExchanger heatEx = new HeatExchanger();
        heatEx.setFeedStream(0, stream_Hot);
        heatEx.setFeedStream(1, stream_Cold);// resyc.getOutStream());

        Separator sep = new Separator(stream_Hot);

        Stream oilOutStream = new Stream(sep.getLiquidOutStream());

        ThrottlingValve valv1 = new ThrottlingValve(oilOutStream);
        valv1.setOutletPressure(5.0);

        Recycle resyc = new Recycle();
        resyc.addStream(valv1.getOutStream());
        resyc.setOutletStream(stream_Cold);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_Hot);
        operations.add(heatEx);
        operations.add(sep);
        operations.add(oilOutStream);
        operations.add(valv1);
        operations.add(resyc);

        operations.run();

        heatEx.getOutStream(0).displayResult();
        resyc.getOutStream().displayResult();
    }
}
