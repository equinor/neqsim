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

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills("beggs and brils pipe 1", stream_1);
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
    testSystem.setMultiPhaseCheck(true);

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

    Assertions.assertEquals(pressureOut, 34.4716898025371, 1);
    Assertions.assertEquals(temperatureOut, 79.80343, 1);
    Assertions.assertEquals(pipe.getPressureDrop(), 15.5283101974629, 1.0);
    Assertions.assertEquals(pipe.getSegmentPressure(10), 34.4716898025371, 1.0);
    Assertions.assertEquals(pipe.getSegmentPressureDrop(10), 1.5468048987983438, 1.0);
    Assertions.assertEquals(pipe.getSegmentTemperature(10) - 273.15, 79.80343029302054, 1.0);
    Assertions.assertEquals(pipe.getSegmentFlowRegime(10), "INTERMITTENT");
    Assertions.assertEquals(pipe.getSegmentMixtureDensity(10), 233.1155792052253, 1.0);
    Assertions.assertEquals(pipe.getSegmentLiquidSuperficialVelocity(10), 3.357338501138603, 1.0);
    Assertions.assertEquals(pipe.getSegmentGasSuperficialVelocity(10), 7.109484383317198, 1.0);
    Assertions.assertEquals(pipe.getSegmentMixtureSuperficialVelocity(10), 10.466822884455802, 1.0);
    Assertions.assertEquals(pipe.getSegmentMixtureViscosity(10), 0.14329203901478244, 1.0);
    Assertions.assertEquals(pipe.getSegmentLiquidHoldup(10), 0.42601098053163294, 1.0);
    Assertions.assertEquals(pipe.getSegmentMixtureReynoldsNumber(10), 2127138.3343691113, 1.0);
    Assertions.assertEquals(pipe.getSegmentLength(10), 410.0, 1.0);
    Assertions.assertEquals(pipe.getSegmentElevation(10), 300, 1.0);
  }



  @Test
  public void testPipeLineBeggsAndBrills4() {
    // One phase
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
    testSystem.initPhysicalProperties();

    Stream stream_1 = new Stream("Stream1", testSystem);
    stream_1.setFlowRate(massFlowRate, "kg/hr");

    PipeBeggsAndBrills pipe = new PipeBeggsAndBrills(stream_1);
    pipe.setPipeWallRoughness(0);
    pipe.setLength(1500.0);
    pipe.setElevation(-1000);
    pipe.setDiameter(0.125);
    pipe.setNumberOfIncrements(10);

    // test with only water phase
    neqsim.processSimulation.processSystem.ProcessSystem operations =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations.add(stream_1);
    operations.add(pipe);
    operations.run();

    double pressureOut = pipe.getOutletPressure();
    double temperatureOut = pipe.getOutletTemperature() - 273.15;

    Assertions.assertEquals(temperatureOut, 75.0748, 1e-4);
    Assertions.assertEquals(pressureOut, 124.04439, 1e-4);


    neqsim.thermo.system.SystemInterface testSystem2 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 45), 1.01325);

    testSystem2.addComponent("water", 1);
    testSystem2.setMixingRule(2);
    testSystem2.init(0);
    testSystem2.useVolumeCorrection(true);
    testSystem2.setPressure(pressure, "bara");
    testSystem2.setTemperature(temperature, "C");
    testSystem2.setTotalFlowRate(massFlowRate, "kg/hr");
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


    Assertions.assertEquals(pressureOut2, 238.8205556280226, 1e-4);



    neqsim.thermo.system.SystemInterface testSystem3 =
        new neqsim.thermo.system.SystemSrkEos((273.15 + 45), 1.01325);

    testSystem3.addComponent("ethane", 1);
    testSystem3.setMixingRule(2);
    testSystem3.init(0);
    testSystem3.useVolumeCorrection(true);
    testSystem3.setPressure(pressure, "bara");
    testSystem3.setTemperature(temperature, "C");
    testSystem3.setTotalFlowRate(massFlowRate, "kg/hr");
    testSystem3.initPhysicalProperties();

    ThermodynamicOperations testOps2 = new ThermodynamicOperations(testSystem3);
    testOps2.TPflash();
    testSystem3.initPhysicalProperties();

    Assertions.assertEquals(testSystem3.hasPhaseType("oil"), true);

    Stream stream_3 = new Stream("Stream1", testSystem3);
    stream_3.setFlowRate(massFlowRate, "kg/hr");

    PipeBeggsAndBrills pipe3 = new PipeBeggsAndBrills(stream_3);
    pipe3.setPipeWallRoughness(0);
    pipe3.setLength(10000.0);
    pipe3.setElevation(1500);
    pipe3.setDiameter(0.125);
    pipe3.setNumberOfIncrements(10);

    neqsim.processSimulation.processSystem.ProcessSystem operations3 =
        new neqsim.processSimulation.processSystem.ProcessSystem();
    operations3.add(stream_3);
    operations3.add(pipe3);
    operations3.run();

    double pressureOut3 = pipe3.getOutletPressure();
    double temperatureOut3 = pipe3.getOutletTemperature() - 273.15;

    Assertions.assertEquals(testSystem3.hasPhaseType("gas"), true);

    Assertions.assertEquals(temperatureOut3, -11.04463, 1e-4);
    Assertions.assertEquals(pressureOut3, 18.3429, 1e-4);

  }

}
