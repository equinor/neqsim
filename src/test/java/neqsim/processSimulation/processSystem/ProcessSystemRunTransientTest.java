package neqsim.processSimulation.processSystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.SimulationInterface;
import neqsim.processSimulation.controllerDevice.ControllerDeviceBaseClass;
import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.measurementDevice.LevelTransmitter;
import neqsim.processSimulation.measurementDevice.PressureTransmitter;
import neqsim.processSimulation.measurementDevice.VolumeFlowTransmitter;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

public class ProcessSystemRunTransientTest extends neqsim.NeqSimTest {
  ProcessSystem p;

  @BeforeEach
  public void setUp() {
    p = new ProcessSystem();
  }

  @Test
  public void testGetName() {
    String name = "TestProsess";
    p.setName(name);
    Assertions.assertEquals(name, p.getName());
  }

  @Test
  void testGetTime() {

  }

  @Test
  void testGetTimeStep() {

  }

  private SystemInterface getTestSystem() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem.addComponent("methane", 0.900);
    testSystem.addComponent("ethane", 0.100);
    testSystem.addComponent("n-heptane", 1.00);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    return testSystem;
  }

  // @Test
  public void testDynamicCalculation() {
    neqsim.thermo.system.SystemInterface testSystem = getTestSystem();

    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.setFlowRate(50.0, "kg/hr");
    stream1.setPressure(10.0, "bara");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(5.0);
    valve1.setPercentValveOpening(50);

    Separator separator1 = new Separator("sep 1");
    separator1.addStream(valve1.getOutletStream());

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator1.getLiquidOutStream());
    valve2.setOutletPressure(1.0);
    valve2.setPercentValveOpening(50);

    ThrottlingValve valve3 = new ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(1.0);
    valve3.setPercentValveOpening(50);

    VolumeFlowTransmitter flowTransmitter = new VolumeFlowTransmitter(stream1);
    flowTransmitter.setUnit("kg/hr");
    flowTransmitter.setMaximumValue(100.0);
    flowTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface flowController = new ControllerDeviceBaseClass();
    flowController.setTransmitter(flowTransmitter);
    flowController.setReverseActing(true);
    flowController.setControllerSetPoint(63.5);
    flowController.setControllerParameters(0.1, 0.10, 0.0);

    p.add(stream1);
    p.add(valve1);
    p.add(separator1);
    p.add(valve2);
    p.add(valve3);
    p.add(flowTransmitter);
    valve1.setController(flowController);

    p.run();

    // transient behaviour
    p.setTimeStep(1.0);
    for (int i = 0; i < 5; i++) {
      // logger.info("volume flow " + flowTransmitter.getMeasuredValue()
      // + " valve opening " + valve_1.getPercentValveOpening() + " pressure "
      // + separator_1.getGasOutStream().getPressure());
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(sim.getCalculationIdentifier(), p.getCalculationIdentifier());
      }
    }
  }

  @Test
  public void testDynamicCalculation2() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.10001);
    testSystem2.addComponent("n-heptane", 1.001);
    testSystem2.setMixingRule(2);

    Stream purgeStream = new Stream("Purge Stream", testSystem2);
    ThrottlingValve purgeValve = new ThrottlingValve("purgeValve", purgeStream);
    purgeValve.setOutletPressure(7.0);
    purgeValve.setPercentValveOpening(50.0);

    neqsim.thermo.system.SystemInterface testSystem = getTestSystem();
    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.setCalculateSteadyState(false);
    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(7.0);
    valve1.setPercentValveOpening(50);

    Separator separator1 = new Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.addStream(purgeValve.getOutletStream());
    separator1.setCalculateSteadyState(true);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator1.getLiquidOutStream());
    valve2.setOutletPressure(5.0);
    valve2.setPercentValveOpening(50);
    valve2.setCalculateSteadyState(true);
    // valve_2.setCv(10.0);

    ThrottlingValve valve3 = new ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(5.0);
    valve3.setPercentValveOpening(50);
    valve3.setCalculateSteadyState(true);
    // valve_3.setCv(10.0);

    LevelTransmitter separatorLevelTransmitter = new LevelTransmitter(separator1);
    separatorLevelTransmitter.setName("separatorLevelTransmitter1");
    separatorLevelTransmitter.setUnit("meter");
    separatorLevelTransmitter.setMaximumValue(1.0);
    separatorLevelTransmitter.setMinimumValue(0.0);

    ControllerDeviceInterface separatorLevelController = new ControllerDeviceBaseClass();
    separatorLevelController.setReverseActing(true);
    separatorLevelController.setTransmitter(separatorLevelTransmitter);
    separatorLevelController.setControllerSetPoint(0.3);
    separatorLevelController.setControllerParameters(1, 1000.0, 0.0);

    PressureTransmitter separatorPressureTransmitter =
        new PressureTransmitter(separator1.getGasOutStream());
    separatorPressureTransmitter.setUnit("bar");
    separatorPressureTransmitter.setMaximumValue(10.0);
    separatorPressureTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface separatorPressureController = new ControllerDeviceBaseClass();
    separatorPressureController.setTransmitter(separatorPressureTransmitter);
    separatorPressureController.setReverseActing(false);
    separatorPressureController.setControllerSetPoint(7.0);
    separatorPressureController.setControllerParameters(0.5, 10.0, 0.0);

    p.add(stream1);
    p.add(valve1);

    p.add(purgeStream);
    p.add(purgeValve);
    p.add(separator1);
    p.add(valve2);
    p.add(valve3);

    // add transmitters
    p.add(separatorLevelTransmitter);
    valve2.setController(separatorLevelController);

    p.add(separatorPressureTransmitter);
    valve3.setController(separatorPressureController);

    p.run();
    for (SimulationInterface sim : p.getUnitOperations()) {
      assertEquals(sim.getCalculationIdentifier(), p.getCalculationIdentifier());
    }

    // p.displayResult();
    p.setTimeStep(0.01);
    for (int i = 0; i < 500; i++) {
      // logger.info("pressure "+separator_1.getGasOutStream().getPressure()+ " flow "+
      // separator_1.getGasOutStream().getFlowRate("kg/hr") + " sepr height
      // "+separatorLevelTransmitter.getMeasuredValue());
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(p.getCalculationIdentifier(), sim.getCalculationIdentifier());
      }
    }

    valve1.setPercentValveOpening(60);

    for (int i = 0; i < 10; i++) {
      // logger.info("pressure "+separator_1.getGasOutStream().getPressure()+ " flow "+
      // separator_1.getGasOutStream().getFlowRate("kg/hr"));
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(p.getCalculationIdentifier(), sim.getCalculationIdentifier());
      }
    }
  }
}
