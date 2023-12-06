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
  void testGetTime() {}

  @Test
  void testGetTimeStep() {}

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

  @Test
  public void testDynamicCalculation() {
    neqsim.thermo.system.SystemInterface testSystem = getTestSystem();

    Stream stream1 = new Stream("Stream1", testSystem);
    stream1.setFlowRate(50.0, "kg/hr");
    stream1.setPressure(10.0, "bara");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(5.0);
    valve1.setPercentValveOpening(40);

    Separator separator1 = new Separator("sep 1");
    separator1.addStream(valve1.getOutletStream());
    separator1.setInternalDiameter(1.0);
    separator1.setSeparatorLength(2.5);
    separator1.setLiquidLevel(0.5);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator1.getLiquidOutStream());
    valve2.setOutletPressure(1.0);
    valve2.setPercentValveOpening(50);

    ThrottlingValve valve3 = new ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(1.0);
    valve3.setPercentValveOpening(50);
    valve3.setMinimumValveOpening(1.0);

    VolumeFlowTransmitter flowTransmitter = new VolumeFlowTransmitter(stream1);
    flowTransmitter.setUnit("kg/hr");
    flowTransmitter.setMaximumValue(100.0);
    flowTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface flowController = new ControllerDeviceBaseClass();
    flowController.setTransmitter(flowTransmitter);
    flowController.setReverseActing(true);
    flowController.setControllerSetPoint(73.5);
    flowController.setControllerParameters(0.2, 100.0, 0.0);

    p.add(stream1);
    p.add(valve1);
    p.add(separator1);
    p.add(valve2);
    p.add(valve3);
    p.add(flowTransmitter);
    valve1.setController(flowController);
    valve1.setCalculateSteadyState(false);

    p.run();

    // transient behaviour
    p.setTimeStep(20.0);
    for (int i = 0; i < 200; i++) {
      // System.out.println("volume flow " + flowTransmitter.getMeasuredValue() + " valve opening "
      // + valve1.getPercentValveOpening() + " pressure "
      // + separator1.getGasOutStream().getPressure());
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(sim.getCalculationIdentifier(), p.getCalculationIdentifier());
      }
    }
    assertEquals(73.5, flowTransmitter.getMeasuredValue(), 1.0);
  }

  @Test
  public void testDynamicCalculation2() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("n-heptane", 1.001);
    testSystem2.setMixingRule(2);

    neqsim.thermo.system.SystemInterface testSystem3 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem3.addComponent("methane", 1.1);
    testSystem3.addComponent("n-heptane", 0.001);
    testSystem3.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(1090.0, "kg/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(25.0, "C");

    Stream streamPurge = new Stream("StreamPurge", testSystem3);
    streamPurge.setFlowRate(50.0, "kg/hr");
    streamPurge.setPressure(10.0, "bara");
    streamPurge.setTemperature(25.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(7.0);
    valve1.setPercentValveOpening(50);
    valve1.setCalculateSteadyState(false);

    ThrottlingValve valvePurge = new ThrottlingValve("valve_purge", streamPurge);
    valvePurge.setOutletPressure(7.0);
    valvePurge.setPercentValveOpening(50);
    valvePurge.setCalculateSteadyState(false);

    Separator separator1 = new Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.addStream(valvePurge.getOutletStream());
    separator1.setCalculateSteadyState(false);
    separator1.setSeparatorLength(3.0);
    separator1.setInternalDiameter(0.8);
    separator1.setLiquidLevel(0.5);

    ThrottlingValve valve2 = new ThrottlingValve("valve_2", separator1.getLiquidOutStream());
    valve2.setOutletPressure(1.0);
    valve2.setPercentValveOpening(50);
    valve2.setCalculateSteadyState(false);
    valve2.setMinimumValveOpening(1.0);

    ThrottlingValve valve3 = new ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(1.0);
    valve3.setPercentValveOpening(10);
    valve3.setCalculateSteadyState(false);
    valve3.setMinimumValveOpening(1.0);

    LevelTransmitter separatorLevelTransmitter = new LevelTransmitter(separator1);
    separatorLevelTransmitter.setName("separatorLevelTransmitter1");
    separatorLevelTransmitter.setMaximumValue(0.8);
    separatorLevelTransmitter.setMinimumValue(0.2);

    ControllerDeviceInterface separatorLevelController = new ControllerDeviceBaseClass();
    separatorLevelController.setReverseActing(false);
    separatorLevelController.setTransmitter(separatorLevelTransmitter);
    separatorLevelController.setControllerSetPoint(0.45);
    separatorLevelController.setControllerParameters(2.0, 500.0, 0.0);

    PressureTransmitter separatorPressureTransmitter =
        new PressureTransmitter(separator1.getGasOutStream());
    separatorPressureTransmitter.setUnit("bar");
    separatorPressureTransmitter.setMaximumValue(10.0);
    separatorPressureTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface separatorPressureController = new ControllerDeviceBaseClass();
    separatorPressureController.setTransmitter(separatorPressureTransmitter);
    separatorPressureController.setReverseActing(false);
    separatorPressureController.setControllerSetPoint(7.0);
    separatorPressureController.setControllerParameters(1, 100, 0.0);

    p.add(stream1);
    p.add(streamPurge);
    p.add(valve1);
    p.add(valvePurge);
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
    p.setTimeStep(10.0);
    for (int i = 0; i < 250; i++) {
      // System.out.println("pressure " + separator1.getGasOutStream().getPressure() + " flow "
      // + separator1.getGasOutStream().getFlowRate("kg/hr") + " sepr height "
      // + separatorLevelTransmitter.getMeasuredValue() + "valve2 opening "
      // + valve2.getPercentValveOpening() + "valve3 opening " + valve3.getPercentValveOpening());
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(p.getCalculationIdentifier(), sim.getCalculationIdentifier());
      }
    }
    assertEquals(0.45, separatorLevelTransmitter.getMeasuredValue(), 0.01);
  }

  @Test
  public void testDynamicCalculation3() {
    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem2.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.1);
    testSystem2.setMixingRule(2);

    neqsim.thermo.system.SystemInterface testSystem3 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 25.0), 10.00);
    testSystem3.addComponent("methane", 1.1);
    testSystem2.addComponent("ethane", 0.1);
    testSystem3.setMixingRule(2);

    Stream stream1 = new Stream("Stream1", testSystem2);
    stream1.setFlowRate(100.0, "kg/hr");
    stream1.setPressure(10.0, "bara");
    stream1.setTemperature(25.0, "C");

    Stream streamPurge = new Stream("StreamPurge", testSystem3);
    streamPurge.setFlowRate(5.0, "kg/hr");
    streamPurge.setPressure(10.0, "bara");
    streamPurge.setTemperature(25.0, "C");

    ThrottlingValve valve1 = new ThrottlingValve("valve_1", stream1);
    valve1.setOutletPressure(7.0);
    valve1.setPercentValveOpening(50);
    valve1.setCalculateSteadyState(false);

    ThrottlingValve valvePurge = new ThrottlingValve("valve_purge", streamPurge);
    valvePurge.setOutletPressure(7.0);
    valvePurge.setPercentValveOpening(50);
    valvePurge.setCalculateSteadyState(false);

    Separator separator1 = new Separator("separator_1");
    separator1.addStream(valve1.getOutletStream());
    separator1.addStream(valvePurge.getOutletStream());
    separator1.setCalculateSteadyState(false);
    separator1.setSeparatorLength(3.0);
    separator1.setInternalDiameter(0.8);
    separator1.setLiquidLevel(0.0);

    ThrottlingValve valve3 = new ThrottlingValve("valve_3", separator1.getGasOutStream());
    valve3.setOutletPressure(1.0);
    valve3.setPercentValveOpening(50);
    valve3.setCalculateSteadyState(false);
    valve3.setMinimumValveOpening(1.0);

    PressureTransmitter separatorPressureTransmitter =
        new PressureTransmitter(separator1.getGasOutStream());
    separatorPressureTransmitter.setUnit("bar");
    separatorPressureTransmitter.setMaximumValue(10.0);
    separatorPressureTransmitter.setMinimumValue(1.0);

    ControllerDeviceInterface separatorPressureController = new ControllerDeviceBaseClass();
    separatorPressureController.setTransmitter(separatorPressureTransmitter);
    separatorPressureController.setReverseActing(false);
    separatorPressureController.setControllerSetPoint(5.0);
    separatorPressureController.setControllerParameters(1, 200.0, 0.0);

    p.add(stream1);
    p.add(streamPurge);
    p.add(valve1);
    p.add(valvePurge);
    p.add(separator1);
    p.add(valve3);

    p.add(separatorPressureTransmitter);
    valve3.setController(separatorPressureController);

    p.run();
    for (SimulationInterface sim : p.getUnitOperations()) {
      assertEquals(sim.getCalculationIdentifier(), p.getCalculationIdentifier());
    }

    // p.displayResult();
    p.setTimeStep(10.0);
    for (int i = 0; i < 500; i++) {
      // System.out.println("pressure " + separator1.getGasOutStream().getPressure() + " flow "
      // + separator1.getGasOutStream().getFlowRate("kg/hr") + "valve3 opening "
      // + valve3.getPercentValveOpening());
      p.runTransient();
      for (SimulationInterface sim : p.getUnitOperations()) {
        assertEquals(p.getCalculationIdentifier(), sim.getCalculationIdentifier());
      }
    }
    assertEquals(5.0, separator1.getGasOutStream().getPressure(), 0.01);

  }
}
