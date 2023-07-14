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

    Assertions.assertEquals(pressureOut, 13.735508907175728, 1e-4);
    Assertions.assertEquals(temperatureOut, 38.82331519652632, 1e-4);
  }



  @Test
  public void testPipeLineBeggsAndBrills3() {

    double pressure = 50; // bara
    double temperature = 80; // C
    double massFlowRate = 110000.000000000;

    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 45), 1.01325);

    testSystem.addComponent("methane", 0.3);
    testSystem.addComponent("nC10", 0.4);
    testSystem.addComponent("water", 0.3);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);
    testSystem.setPressure(pressure, "bara");
    testSystem.setTemperature(temperature, "C");
    testSystem.setTotalFlowRate(massFlowRate, "kg/hr");


    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(massFlowRate, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(stream_1);
    pipe.setPipeWallRoughness(0);
    pipe.setLength(410.0);
    pipe.setElevation(300);
    pipe.setDiameter(0.125);
    pipe.setNumberOfIncrements(10);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.run();

    double pressureOut = pipe.getOutletPressure();
    double temperatureOut = pipe.getOutletTemperature() - 273.15;

    Assertions.assertEquals(pressureOut, 34.4716898025371, 1e-4);
    Assertions.assertEquals(temperatureOut, 79.80343, 1e-4);
  }



  @Test
  public void testPipeLineBeggsAndBrills4() {
    //One phase
    double pressure = 150; // bara
    double temperature = 80; // C
    double massFlowRate = 110000.000000000;

    neqsim.thermo.system.SystemInterface testSystem =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 45), 1.01325);

    testSystem.addComponent("methane", 1);
    testSystem.setMixingRule(2);
    testSystem.init(0);
    testSystem.useVolumeCorrection(true);
    testSystem.setPressure(pressure, "bara");
    testSystem.setTemperature(temperature, "C");
    testSystem.setTotalFlowRate(massFlowRate, "kg/hr");

    ThermodynamicOperations testOps = new ThermodynamicOperations(testSystem);
    testOps.TPflash();
    testSystem.initPhysicalProperties();

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(massFlowRate, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(stream_1);
    pipe.setPipeWallRoughness(0);
    pipe.setLength(1500.0);
    pipe.setElevation(-1000);
    pipe.setDiameter(0.125);
    pipe.setNumberOfIncrements(10);

    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    //operations.run();

    double pressureOut = pipe.getOutletPressure();
    double temperatureOut = pipe.getOutletTemperature() - 273.15;

    //Assertions.assertEquals(temperatureOut, 75.0748, 1e-4);
    //Assertions.assertEquals(pressureOut, 124.04439, 1e-4);


    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 45), 1.01325);

    testSystem2.addComponent("water", 1);
    testSystem2.setMixingRule(2);
    testSystem2.init(0);
    testSystem2.useVolumeCorrection(true);
    testSystem2.setPressure(pressure, "bara");
    testSystem2.setTemperature(temperature, "C");
    testSystem2.setTotalFlowRate(massFlowRate, "kg/hr");

    ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem2);
    testOps.TPflash();
    testSystem2.initPhysicalProperties();

    Stream stream_2 = new Stream("Stream1", testSystem2);
    stream_2.setFlowRate(massFlowRate, "kg/hr");

    PipeBeggsAndBrills pipe2 = new PipeBeggsAndBrills(stream_2);
    pipe2.setPipeWallRoughness(0);
    pipe2.setLength(1500.0);
    pipe2.setElevation(-1000);
    pipe2.setDiameter(0.125);
    pipe2.setNumberOfIncrements(10);

    neqsim.processSimulation.processSystem.ProcessSystem operations2 =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations2.add(stream_2);
    operations2.add(pipe2);
    operations2.run();

    double pressureOut2 = pipe2.getOutletPressure();
    double temperatureOut2 = pipe2.getOutletTemperature() - 273.15;

    Assertions.assertEquals(temperatureOut2, 78.20515, 1e-4);
    Assertions.assertEquals(pressureOut2, 238.8205556280226, 1e-4);
  }

}
