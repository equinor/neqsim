package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;

/**
 * Tests for single-component dew/bubble point stream specifications with compressors.
 *
 * <p>
 * Verifies that dewT, dewP, bubT, and bubP specifications on streams produce correct phase
 * fractions for single-component systems, so downstream equipment (compressors) computes correct
 * discharge temperature and power.
 *
 * @author NeqSim
 * @version 1.0
 */
class CompressorDewTSpecTest extends neqsim.NeqSimTest {

  /**
   * Test propane refrigeration cycle with dewT specification. Dew point at -15C should give
   * saturated vapor at ~2.91 bara. Compressor to 15.69 bara should give ~65C discharge and ~188 kW
   * power.
   */
  @Test
  void testPropaneRefrigerationWithDewT() {
    SystemInterface fluid1 = new SystemPrEos(273.15 - 15.0, 1.0);
    fluid1.addComponent("propane", 1.0);
    fluid1.setTotalFlowRate(6454.0, "kg/hr");
    fluid1.setTemperature(-15.0, "C");

    ProcessSystem process = new ProcessSystem();

    Stream stream1 = new Stream("feed", fluid1);
    stream1.setSpecification("dewT");
    process.add(stream1);

    Compressor compressor1 = new Compressor("comp", stream1);
    compressor1.setIsentropicEfficiency(0.75);
    compressor1.setOutletPressure(15.69, "bara");
    process.add(compressor1);

    process.run();

    double suctionP = stream1.getPressure("bara");
    double dischargeT = compressor1.getOutletStream().getTemperature("C");
    double power = compressor1.getTotalWork() / 1e3;

    // Suction pressure should be dew point of propane at -15C (~2.91 bara)
    assertEquals(2.91, suctionP, 0.5, "Suction pressure should be ~2.91 bara");

    // dewT should produce nearly all gas (beta close to 1)
    assertTrue(stream1.getFluid().getBeta() > 0.99,
        "dewT should produce vapor (beta > 0.99) but was " + stream1.getFluid().getBeta());

    // Discharge temperature should be ~65C
    assertEquals(65.5, dischargeT, 5.0,
        "Discharge temperature should be ~65C but was " + dischargeT + " C");

    // Power should be ~188 kW
    assertEquals(189.0, power, 20.0, "Compressor power should be ~189 kW but was " + power + " kW");
  }

  /**
   * Test propane with dewP specification (dew point temperature at given pressure). At 2.91 bara,
   * propane dew point should be ~-15C.
   */
  @Test
  void testPropaneRefrigerationWithDewP() {
    SystemInterface fluid1 = new SystemPrEos(273.15, 2.911);
    fluid1.addComponent("propane", 1.0);
    fluid1.setTotalFlowRate(6454.0, "kg/hr");

    ProcessSystem process = new ProcessSystem();

    Stream stream1 = new Stream("feed", fluid1);
    stream1.setSpecification("dewP");
    process.add(stream1);

    Compressor compressor1 = new Compressor("comp", stream1);
    compressor1.setIsentropicEfficiency(0.75);
    compressor1.setOutletPressure(15.69, "bara");
    process.add(compressor1);

    process.run();

    double suctionT = stream1.getTemperature("C");
    double dischargeT = compressor1.getOutletStream().getTemperature("C");
    double power = compressor1.getTotalWork() / 1e3;

    // dewP should produce nearly all gas (beta close to 1)
    assertTrue(stream1.getFluid().getBeta() > 0.99,
        "dewP should produce vapor (beta > 0.99) but was " + stream1.getFluid().getBeta());

    // Suction temperature should be dew point at 2.911 bara (~-15C)
    assertEquals(-15.0, suctionT, 3.0,
        "Suction temperature should be ~-15C but was " + suctionT + " C");

    // Discharge temperature should be ~65C
    assertTrue(dischargeT > 40.0,
        "Discharge temperature should be > 40C but was " + dischargeT + " C");

    // Power should be > 100 kW
    assertTrue(power > 100.0, "Compressor power should be > 100 kW but was " + power + " kW");
  }

  /**
   * Test propane with bubT specification (bubble point pressure at given temperature). For a
   * single-component, bubble point = dew point. At -15C, propane bubble pressure ~2.91 bara. bubT
   * should leave the system as nearly all liquid.
   */
  @Test
  void testPropaneBubT() {
    SystemInterface fluid1 = new SystemPrEos(273.15 - 15.0, 1.0);
    fluid1.addComponent("propane", 1.0);
    fluid1.setTotalFlowRate(6454.0, "kg/hr");
    fluid1.setTemperature(-15.0, "C");

    Stream stream1 = new Stream("feed", fluid1);
    stream1.setSpecification("bubT");
    stream1.run();

    double pressure = stream1.getPressure("bara");
    double beta = stream1.getFluid().getBeta();

    // Pressure should be saturation pressure at -15C
    assertEquals(2.91, pressure, 0.5, "bubT pressure should be ~2.91 bara but was " + pressure);

    // bubT should produce nearly all liquid (beta close to 0)
    assertTrue(beta < 0.01, "bubT should produce liquid (beta < 0.01) but was " + beta);
  }

  /**
   * Test propane with bubP specification (bubble point temperature at given pressure). bubP should
   * leave the system as nearly all liquid.
   */
  @Test
  void testPropaneBubP() {
    SystemInterface fluid1 = new SystemPrEos(273.15, 2.911);
    fluid1.addComponent("propane", 1.0);
    fluid1.setTotalFlowRate(6454.0, "kg/hr");

    Stream stream1 = new Stream("feed", fluid1);
    stream1.setSpecification("bubP");
    stream1.run();

    double temperature = stream1.getTemperature("C");
    double beta = stream1.getFluid().getBeta();

    // Temperature should be saturation temperature at 2.911 bara (~-15C)
    assertEquals(-15.0, temperature, 3.0,
        "bubP temperature should be ~-15C but was " + temperature + " C");

    // bubP should produce nearly all liquid (beta close to 0)
    assertTrue(beta < 0.01, "bubP should produce liquid (beta < 0.01) but was " + beta);
  }

  /**
   * Test propane compression with TP specification (manual pressure) as reference.
   */
  @Test
  void testPropaneRefrigerationWithTP() {
    SystemInterface fluid1 = new SystemPrEos(273.15 - 15.0, 2.911);
    fluid1.addComponent("propane", 1.0);
    fluid1.setTotalFlowRate(6454.0, "kg/hr");
    fluid1.setTemperature(-15.0, "C");

    ProcessSystem process = new ProcessSystem();

    Stream stream1 = new Stream("feed", fluid1);
    process.add(stream1);

    Compressor compressor1 = new Compressor("comp", stream1);
    compressor1.setIsentropicEfficiency(0.75);
    compressor1.setOutletPressure(15.69, "bara");
    process.add(compressor1);

    process.run();

    double dischargeT = compressor1.getOutletStream().getTemperature("C");
    double power = compressor1.getTotalWork() / 1e3;

    assertEquals(65.5, dischargeT, 5.0,
        "Discharge temperature should be ~65C but was " + dischargeT + " C");
    assertEquals(189.0, power, 20.0, "Compressor power should be ~189 kW but was " + power + " kW");
  }
}
