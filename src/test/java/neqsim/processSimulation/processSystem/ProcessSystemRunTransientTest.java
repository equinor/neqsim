package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import neqsim.processSimulation.controllerDevice.ControllerDeviceBaseClass;
import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.measurementDevice.LevelTransmitter;
import neqsim.processSimulation.measurementDevice.PressureTransmitter;
import neqsim.processSimulation.measurementDevice.VolumeFlowTransmitter;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

public class ProcessSystemRunTransientTest {
    ProcessSystem p;

    @BeforeEach
    public void setUp() {
        p = new ProcessSystem();
    }

    @Test
    public void testGetName() {
        String name = "TestProsess";
        p.setName(name);
        Assertions.assertEquals(p.getName(), name);
    }

    @Test
    void testGetTime() {

    }

    @Test
    void testGetTimeStep() {

    }

    private SystemInterface getTestSystem() {
        neqsim.thermo.system.SystemInterface testSystem = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
        testSystem.addComponent("methane", 0.900);
        testSystem.addComponent("ethane", 0.100);
        testSystem.addComponent("n-heptane", 1.00);
        testSystem.createDatabase(true);
        testSystem.setMixingRule(2);
        return testSystem;
    }

    @Test
    public void testDynamicCalculation() {
        neqsim.thermo.system.SystemInterface testSystem = getTestSystem();
        Stream stream_1 = new Stream("Stream1", testSystem);
        ThrottlingValve valve_1 = new ThrottlingValve(stream_1);
        valve_1.setOutletPressure(5.0);
        valve_1.setPercentValveOpening(50);

        Separator separator_1 = new Separator();
        separator_1.addStream(valve_1.getOutStream());

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

        p.add(stream_1);
        p.add(valve_1);
        p.add(separator_1);
        p.add(valve_2);
        p.add(valve_3);

        p.add(flowTransmitter);
        valve_1.setController(flowController);

        p.run();
        // p.displayResult();
        valve_2.setPercentValveOpening(0.1);
        valve_3.setPercentValveOpening(0.1);

        // transient behaviour
        p.setTimeStep(1.0);
        for (int i = 0; i < 460; i++) {
            System.out.println("volume flow " + flowTransmitter.getMeasuredValue()
                    + " valve opening " + valve_1.getPercentValveOpening() + " pressure "
                    + separator_1.getGasOutStream().getPressure());
            p.runTransient();
        }

        // p.displayResult();
        System.out.println("volume flow " + flowTransmitter.getMeasuredValue() + " valve opening "
                + valve_1.getPercentValveOpening());
    }

    @Test
    public void testDynamicCalculation2() {
        neqsim.thermo.system.SystemInterface testSystem = getTestSystem();

        neqsim.thermo.system.SystemInterface testSystem2 = new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0),
                10.00);
        testSystem2.addComponent("methane", 1.1);
        testSystem2.addComponent("ethane", 0.10001);
        testSystem2.addComponent("n-heptane", 0.001);
        testSystem2.setMixingRule(2);

        Stream purgeStream = new Stream("Purge Stream", testSystem2);
        ThrottlingValve purgeValve = new ThrottlingValve(purgeStream);
        purgeValve.setOutletPressure(7.0);
        purgeValve.setPercentValveOpening(50.0);

        Stream stream_1 = new Stream("Stream1", testSystem);
        ThrottlingValve valve_1 = new ThrottlingValve(stream_1);
        valve_1.setOutletPressure(7.0);
        valve_1.setPercentValveOpening(50);

        Separator separator_1 = new Separator();
        separator_1.addStream(valve_1.getOutStream());
        separator_1.addStream(purgeValve.getOutStream());

        ThrottlingValve valve_2 = new ThrottlingValve(separator_1.getLiquidOutStream());
        valve_2.setOutletPressure(5.0);
        valve_2.setPercentValveOpening(50);
        // valve_2.setCv(10.0);

        ThrottlingValve valve_3 = new ThrottlingValve(separator_1.getGasOutStream());
        valve_3.setOutletPressure(5.0);
        valve_3.setPercentValveOpening(50);
        // valve_3.setCv(10.0);

        LevelTransmitter separatorLevelTransmitter = new LevelTransmitter(separator_1);
        separatorLevelTransmitter.setName("separatorLEvelTransmitter1");
        separatorLevelTransmitter.setUnit("meter");
        separatorLevelTransmitter.setMaximumValue(1.0);
        separatorLevelTransmitter.setMinimumValue(0.0);

        ControllerDeviceInterface separatorLevelController = new ControllerDeviceBaseClass();
        separatorLevelController.setReverseActing(false);
        separatorLevelController.setTransmitter(separatorLevelTransmitter);
        separatorLevelController.setControllerSetPoint(0.3);
        separatorLevelController.setControllerParameters(1.0, 300.0, 10.0);

        PressureTransmitter separatorPressureTransmitter = new PressureTransmitter(separator_1.getGasOutStream());
        separatorPressureTransmitter.setUnit("bar");
        separatorPressureTransmitter.setMaximumValue(10.0);
        separatorPressureTransmitter.setMinimumValue(1.0);

        ControllerDeviceInterface separatorPressureController = new ControllerDeviceBaseClass();
        separatorPressureController.setTransmitter(separatorPressureTransmitter);
        separatorPressureController.setReverseActing(false);
        separatorPressureController.setControllerSetPoint(7.0);
        separatorPressureController.setControllerParameters(1.0, 300.0, 10.0);

        p.add(stream_1);
        p.add(valve_1);

        p.add(purgeStream);
        p.add(purgeValve);
        p.add(separator_1);
        p.add(valve_2);
        p.add(valve_3);

        // add transmitters
        p.add(separatorLevelTransmitter);
        valve_2.setController(separatorLevelController);

        p.add(separatorPressureTransmitter);
        valve_3.setController(separatorPressureController);

        p.run();
        // p.displayResult();
        p.setTimeStep(0.001);
        p.runTransient();
        p.runTransient();
    }
}
