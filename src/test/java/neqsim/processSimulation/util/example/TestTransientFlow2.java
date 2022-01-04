package neqsim.processSimulation.util.example;

import neqsim.processSimulation.controllerDevice.ControllerDeviceBaseClass;
import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.measurementDevice.VolumeFlowTransmitter;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;

/**
 * <p>TestTransientFlow2 class.</p>
 *
 * @author ESOL
 * @version $Id: $Id
 * @since 2.2.3
 */
public class TestTransientFlow2 {
    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String args[]) {
        neqsim.thermo.system.SystemInterface testSystem =
                new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
        testSystem.addComponent("methane", 0.900);
        testSystem.addComponent("ethane", 0.100);
        testSystem.addComponent("n-heptane", 1.00);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);

        Stream stream_1 = new Stream("Stream1", testSystem);
        ThrottlingValve valve_1 = new ThrottlingValve(stream_1);
        valve_1.setOutletPressure(5.0);
        valve_1.setPercentValveOpening(50);

        Separator separator_1 = new Separator();
        separator_1.addStream(valve_1.getOutStream());
        //
        ThrottlingValve valve_2 = new ThrottlingValve(separator_1.getLiquidOutStream());
        valve_2.setOutletPressure(1.0);
        valve_2.setPercentValveOpening(50);
        // valve_2.setCv(10.0);

        ThrottlingValve valve_3 = new ThrottlingValve(separator_1.getGasOutStream());
        valve_3.setOutletPressure(1.0);
        valve_3.setPercentValveOpening(50);

        VolumeFlowTransmitter flowTransmitter = new VolumeFlowTransmitter(stream_1);
        flowTransmitter.setUnit("m^3/hr");
        flowTransmitter.setMaximumValue(10.0);
        flowTransmitter.setMinimumValue(1.0);

        ControllerDeviceInterface flowController = new ControllerDeviceBaseClass();
        flowController.setTransmitter(flowTransmitter);
        flowController.setReverseActing(true);
        flowController.setControllerSetPoint(1.0);
        flowController.setControllerParameters(0.7, 300.0, 0.0);

        neqsim.processSimulation.processSystem.ProcessSystem operations =
                new neqsim.processSimulation.processSystem.ProcessSystem();
        operations.add(stream_1);
        operations.add(valve_1);
        operations.add(separator_1);
        operations.add(valve_2);
        operations.add(valve_3);

        operations.add(flowTransmitter);
        valve_1.setController(flowController);

        operations.run();
        operations.displayResult();
        valve_2.setPercentValveOpening(0.1);
        valve_3.setPercentValveOpening(0.1);
        // transient behaviour
        operations.setTimeStep(5.0);
        for (int i = 0; i < 460; i++) {
            System.out.println("volume flow " + flowTransmitter.getMeasuredValue()
                    + " valve opening " + valve_1.getPercentValveOpening() + " pressure "
                    + separator_1.getGasOutStream().getPressure());
            operations.runTransient();
        }

        operations.displayResult();
        System.out.println("volume flow " + flowTransmitter.getMeasuredValue() + " valve opening "
                + valve_1.getPercentValveOpening());

        // operations.displayResult();
    }
}
