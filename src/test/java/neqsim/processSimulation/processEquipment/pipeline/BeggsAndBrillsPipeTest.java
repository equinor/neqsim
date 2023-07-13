package neqsim.processSimulation.processEquipment.pipeline;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.thermodynamicOperations.ThermodynamicOperations;

public class BeggsAndBrillsPipeTest {
  @Test
  public void testFlowNoVolumeCorrection() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15), 1.01325);

    testSystem.addComponent("nC10", 50, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(false);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Assertions.assertEquals(testSystem.getPhase("oil").getFlowRate("m3/hr"),
        testSystem.getFlowRate("m3/hr"), 1e-4);
  }

  @Test
  public void testFlowVolumeCorrection() {
    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 15), 1.01325);

    testSystem.addComponent("nC10", 50, "MSm^3/day");
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Assertions.assertEquals(testSystem.getPhase("oil").getFlowRate("m3/hr"),
        testSystem.getFlowRate("m3/hr"), 1e-4);
  }

  @Test
  public void testPipeLineBeggsAndBrills() {

    double pressure = 50; // bara
    double temperature = 40; // C
    double massFlowRate = 1100000.000000000;

    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 45), 1.01325);

    testSystem.addComponent("methane", 0.5);
    testSystem.addComponent("nC10", 0.5);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);
    testSystem.setPressure(pressure, "bara");
    testSystem.setTemperature(temperature, "C");
    testSystem.setTotalFlowRate(massFlowRate, "kg/hr");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();


    double oil_flow_rate = testSystem.getPhase("oil").getFlowRate("m3/hr");
    double gas_flow_rate = testSystem.getPhase("gas").getFlowRate("m3/hr");

    System.out.println("oil_flow_rate  " + oil_flow_rate);
    System.out.println("gas_flow_rate  " + gas_flow_rate);

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(massFlowRate, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(stream_1);
    pipe.setPipeWallRoughness(5e-6);
    pipe.setLength(10.0);
    pipe.setAngle(0);
    pipe.setDiameter(0.125);
    pipe.setNumberOfIncrements(20);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.run();

    double pressureOut = pipe.getOutletPressure();
    double temperatureOut = pipe.getOutletTemperature() - 273.15;

    Assertions.assertEquals(pressureOut, 27.5402, 1e-4);
    Assertions.assertEquals(temperatureOut, 39.3374, 1e-4);

  }


  @Test
  public void testPipeLineBeggsAndBrills2() {

    double pressure = 50; // bara
    double temperature = 40; // C
    double massFlowRate = 110000.000000000;

    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 45), 1.01325);

    testSystem.addComponent("methane", 0.5);
    testSystem.addComponent("nC10", 0.5);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);
    testSystem.setPressure(pressure, "bara");
    testSystem.setTemperature(temperature, "C");
    testSystem.setTotalFlowRate(massFlowRate, "kg/hr");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();


    double oil_flow_rate = testSystem.getPhase("oil").getFlowRate("m3/hr");
    double gas_flow_rate = testSystem.getPhase("gas").getFlowRate("m3/hr");

    System.out.println("oil_flow_rate  " + oil_flow_rate);
    System.out.println("gas_flow_rate  " + gas_flow_rate);

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(massFlowRate, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(stream_1);
    pipe.setPipeWallRoughness(0);
    pipe.setLength(750.0);
    pipe.setAngle(90);
    pipe.setDiameter(0.125);
    pipe.setNumberOfIncrements(50);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.run();

    double pressureOut = pipe.getOutletPressure();
    double temperatureOut = pipe.getOutletTemperature() - 273.15;

    System.out.println("pressureOut  " + pressureOut);
    System.out.println("temperatureOut  " + temperatureOut);
  }

}
