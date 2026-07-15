package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CompressorShaft} — multiple compressor bodies on one shaft at a single common speed.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorShaftTest {

  /**
   * Build a simple run gas stream.
   *
   * @param pressure inlet pressure in bara
   * @return a run {@link Stream}
   */
  private Stream feed(double pressure) {
    SystemSrkEos gas = new SystemSrkEos(273.15 + 30.0, pressure);
    gas.addComponent("methane", 0.90);
    gas.addComponent("ethane", 0.10);
    gas.setMixingRule("classic");
    Stream stream = new Stream("feed", gas);
    stream.setFlowRate(6000.0, "kg/hr");
    stream.setTemperature(30.0, "C");
    stream.setPressure(pressure, "bara");
    stream.run();
    return stream;
  }

  /**
   * A two-body shaft applies ONE common speed to every body.
   */
  @Test
  void testCommonSpeedAppliedToAllBodies() {
    Stream f1 = feed(10.0);
    Compressor c1 = new Compressor("body1", f1);
    c1.setOutletPressure(20.0);
    c1.setUsePolytropicCalc(true);
    c1.setPolytropicEfficiency(0.75);
    c1.run();

    Compressor c2 = new Compressor("body2", c1.getOutletStream());
    c2.setOutletPressure(40.0);
    c2.setUsePolytropicCalc(true);
    c2.setPolytropicEfficiency(0.75);
    c2.run();

    CompressorShaft shaft = new CompressorShaft("test shaft");
    shaft.addCompressor(c1);
    shaft.addCompressor(c2);
    assertEquals(2, shaft.getCompressors().size());

    shaft.setSpeed(11000.0);
    assertEquals(11000.0, shaft.getSpeed(), 1e-6);
    assertEquals(11000.0, c1.getSpeed(), 1e-6);
    assertEquals(11000.0, c2.getSpeed(), 1e-6);
  }

  /**
   * Total shaft power equals the sum of the body powers.
   */
  @Test
  void testTotalPowerSumsBodies() {
    Stream f1 = feed(10.0);
    Compressor c1 = new Compressor("body1", f1);
    c1.setOutletPressure(20.0);
    c1.setUsePolytropicCalc(true);
    c1.setPolytropicEfficiency(0.75);
    c1.run();

    Compressor c2 = new Compressor("body2", c1.getOutletStream());
    c2.setOutletPressure(40.0);
    c2.setUsePolytropicCalc(true);
    c2.setPolytropicEfficiency(0.75);
    c2.run();

    CompressorShaft shaft = new CompressorShaft("test shaft");
    shaft.addCompressor(c1);
    shaft.addCompressor(c2);

    double expected = c1.getPower() + c2.getPower();
    assertEquals(expected, shaft.getTotalPower(), Math.max(1.0, Math.abs(expected) * 1e-6));
    assertTrue(shaft.getTotalPower() > 0.0);
  }

  /**
   * With performance charts, runAtFixedSpeed locks the same speed on every body and the bodies run.
   */
  @Test
  void testWithChartRunAtFixedSpeed() {
    Stream f1 = feed(10.0);
    Compressor c1 = new Compressor("body1", f1);
    c1.setOutletPressure(20.0);
    c1.setUsePolytropicCalc(true);
    c1.setPolytropicEfficiency(0.75);
    c1.run();
    c1.generateCompressorChart("normal curves", 5);
    c1.getCompressorChart().setUseCompressorChart(true);

    double designSpeed = c1.getSpeed();
    CompressorShaft shaft = new CompressorShaft("charted shaft");
    shaft.addCompressor(c1);
    shaft.runAtFixedSpeed(designSpeed, null);

    assertEquals(designSpeed, c1.getSpeed(), 1e-6);
    assertTrue(shaft.getTotalPower() > 0.0);
  }

  /**
   * A single-body shaft is valid and applies its speed.
   */
  @Test
  void testSingleBodyShaft() {
    Stream f1 = feed(10.0);
    Compressor c1 = new Compressor("only body", f1);
    c1.setOutletPressure(25.0);
    c1.setUsePolytropicCalc(true);
    c1.setPolytropicEfficiency(0.78);
    c1.run();

    CompressorShaft shaft = new CompressorShaft("single-body shaft");
    shaft.addCompressor(c1);
    assertEquals(1, shaft.getCompressors().size());
    shaft.setSpeed(9000.0);
    assertEquals(9000.0, c1.getSpeed(), 1e-6);
  }
}
