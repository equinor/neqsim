package neqsim.process.measurementdevice;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

public class nmVOCTest extends neqsim.NeqSimTest {
  static ProcessSystem process1;
  static NMVOCAnalyser vocanalyser1;

  /**
   * Compare independently evaluated conversion paths at floating-point rounding precision.
   *
   * @param expected result of the direct conversion
   * @param actual result of the chained conversion
   */
  private static void assertConversionEquals(double expected, double actual) {
    Assertions.assertEquals(expected, actual, 2.0 * Math.ulp(expected));
  }

  /**
   * Creates the shared process and NMVOC analyser used by the tests.
   */
  @BeforeAll
  static void setUpBeforeClass() {
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
    vocanalyser1 = new NMVOCAnalyser("vocanalyser 1", stream1);
    vocanalyser1.setUnit("tonnes/year");

    process1 = new ProcessSystem();
    process1.add(stream1);
    process1.add(vocanalyser1);
  }

  @Test
  public void testSetUnit() {
    String origUnit = vocanalyser1.getUnit();
    String newUnit = "kg/hr";
    Assertions.assertNotEquals(origUnit, newUnit);
    vocanalyser1.setUnit(newUnit);
    Assertions.assertEquals(newUnit, vocanalyser1.getUnit());
    vocanalyser1.setUnit(origUnit);
    Assertions.assertEquals(origUnit, vocanalyser1.getUnit());
  }

  @Test
  public void testGetFlowRate() {
    process1.run();
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("kg/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("kg/sec"));
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("kg/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("kg/min"));
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("tonnes/year"),
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("kg/hr") * 24 * 365
            / 1000);
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("m3/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("m3/sec"));
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("m3/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("m3/min"));
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("mole/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("mole/sec"));
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("mole/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getFlowRate("mole/min"));
    // throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
  }

  @Test
  public void testGetTotalFlowRate() {
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("kg/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("kg/sec"));
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("kg/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("kg/min"));

    // assertConversionEquals(thermoSystem.getComponent("water").getTotalFlowRate("m3/min"),60
    // * thermoSystem.getComponent("water").getTotalFlowRate("m3/sec"));
    // assertConversionEquals(thermoSystem.getComponent("water").getTotalFlowRate("m3/hr"),60
    // *
    // thermoSystem.getComponent("water").getTotalFlowRate("m3/min"));

    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("mole/min"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("mole/sec"));
    assertConversionEquals(
        ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("mole/hr"),
        60 * ((Stream) process1.getUnit("stream 1")).getFluid().getComponent("water").getTotalFlowRate("mole/min"));
    // throw new RuntimeException("failed.. unit: " + flowunit + " not supported");
  }

  @Test
  public void testnmVOCFlowRate() {
    process1.run();
    Assertions.assertEquals(10555.540704, vocanalyser1.getMeasuredValue(), 0.1);
    Assertions.assertEquals(10555.540704, vocanalyser1.getMeasuredValue("tonnes/year"), 0.1);
    Assertions.assertEquals(10555.540704 * 1000 / (365 * 24), vocanalyser1.getMeasuredValue("kg/hr"), 0.1);
  }
}
