package neqsim.processSimulation.processSystem;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.controllerDevice.ControllerDeviceBaseClass;
import neqsim.processSimulation.controllerDevice.ControllerDeviceInterface;
import neqsim.processSimulation.measurementDevice.VolumeFlowTransmitter;
import neqsim.processSimulation.processEquipment.separator.Separator;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processEquipment.valve.ThrottlingValve;
import neqsim.thermo.system.SystemInterface;

public class ProcessSystemControllerTest extends neqsim.NeqSimTest {
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
    testSystem.addComponent("n-heptane", 0.1);
    testSystem.createDatabase(true);
    testSystem.setMixingRule(2);
    return testSystem;
  }

  private double getRandomDistrurbanceFlowRate() {
    double max = 5;
    double min = -5;
    double random_double = (int) Math.floor(Math.random() * (max - min + 1) + min);
    return random_double;
  }

  @Test
  public void testStaticSimulationWithController() {
    neqsim.thermo.system.SystemInterface testSystem = getTestSystem();

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(100.0 + getRandomDistrurbanceFlowRate(), "kg/hr");
    stream_1.setPressure(10.0, "bara");

    ThrottlingValve valve_1 = new ThrottlingValve("valve_1", stream_1);
    valve_1.setOutletPressure(5.0);

    Separator separator_1 = new Separator("sep 1");
    separator_1.addStream(valve_1.getOutStream());

    VolumeFlowTransmitter flowTransmitter =
        new VolumeFlowTransmitter(separator_1.getGasOutStream());
    flowTransmitter.setUnit("kg/hr");
    flowTransmitter.setMaximumValue(110.0);
    flowTransmitter.setMinimumValue(90.0);

    ControllerDeviceInterface flowController = new ControllerDeviceBaseClass();
    flowController.setTransmitter(flowTransmitter);
    flowController.setReverseActing(true);
    flowController.setControllerSetPoint(65.0 + getRandomDistrurbanceFlowRate());
    flowController.setControllerParameters(0.01, 0.10, 0.100);

    p.add(stream_1);
    p.add(valve_1);
    p.add(separator_1);
    p.add(flowTransmitter);
    stream_1.setController(flowController);

    p.run();

    // transient behaviour
    p.setTimeStep(1.0);
    for (int i = 0; i < 25; i++) {
      flowController.setControllerSetPoint(65.0 + getRandomDistrurbanceFlowRate());
      p.runTransient();
      System.out.println(
          "flow rate " + valve_1.getOutStream().getFluid().getPhase("gas").getFlowRate("kg/hr")
              + " controller response " + flowController.getResponse() + " valve opening "
              + valve_1.getPercentValveOpening() + " pressure "
              + separator_1.getGasOutStream().getPressure());
      // p.runTransient();
    }
  }

}
