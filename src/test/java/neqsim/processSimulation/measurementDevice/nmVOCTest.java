package neqsim.processSimulation.measurementDevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.processSimulation.processEquipment.stream.Stream;
import neqsim.processSimulation.processSystem.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class nmVOCTest extends neqsim.NeqSimTest {

  static ProcessSystem process1 = new ProcessSystem();

  /**
   * @throws java.lang.Exception
   */
  @BeforeAll
  static void setUpBeforeClass() throws Exception {
    SystemInterface thermoSystem = new SystemSrkEos(298.0, 100.0);
    thermoSystem.addComponent("water", 1.0);
    thermoSystem.addComponent("methane", 1.0);
    thermoSystem.addComponent("ethane", 1.0);
    thermoSystem.addComponent("propane", 1.0);
    thermoSystem.addComponent("i-butane", 1.0);
    thermoSystem.addComponent("n-butane", 1.0);
    thermoSystem.addComponent("i-pentane", 1.0);
    thermoSystem.addComponent("n-pentane", 1.0);

    Stream stream1 = new Stream("stream 1", thermoSystem);
    NMVOCAnalyser vocanalyser1 = new NMVOCAnalyser(stream1);
    vocanalyser1.setName("vocanalyser 1");
    vocanalyser1.setUnit("tonnes/year");

    process1.add(stream1);
    process1.add(vocanalyser1);

    process1.run();

  }

  @Test
  public void testGetFlowRate() {
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("kg/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("kg/sec"));
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("kg/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("kg/min"));
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("tonnes/year"),
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("kg/hr") * 24 * 365 / 1000);
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("m3/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("m3/sec"));
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("m3/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("m3/min"));
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("mole/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("mole/sec"));
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("mole/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getFlowRate("mole/min"));
    // throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
  }

  @Test
  public void testGetTotalFlowRate() {
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("kg/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("kg/sec"));
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("kg/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("kg/min"));

    // Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("m3/min"),60
    // * thermoSystem.getComponent("water").getTotalFlowRate("m3/sec"));
    // Assertions.assertEquals(thermoSystem.getComponent("water").getTotalFlowRate("m3/hr"),60
    // *
    // thermoSystem.getComponent("water").getTotalFlowRate("m3/min"));

    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("mole/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("mole/sec"));
    Assertions.assertEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("mole/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water")
            .getTotalFlowRate("mole/min"));
    // throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
  }

  @Test
  public void nmVOCFlowRateTest() {
    Assertions.assertEquals(
        ((NMVOCAnalyser) process1.getMeasurementDevice("vocanalyser 1")).getMeasuredValue(),
        10555.540704);
  }
}
