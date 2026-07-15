package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorShaft;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CompressorShaftCalculator} — process-integrated common-speed control.
 *
 * @author NeqSim Development Team
 * @version 1.0
 */
public class CompressorShaftCalculatorTest {

  /**
   * Build a run gas stream.
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
   * Stepping the calculator once per iteration (as the process would) drives the reference discharge to the target.
   */
  @Test
  void testConvergesToTargetDischarge() {
    Stream f1 = feed(10.0);
    Compressor c1 = new Compressor("body1", f1);
    c1.setOutletPressure(20.0);
    c1.setUsePolytropicCalc(true);
    c1.setPolytropicEfficiency(0.75);
    c1.run();
    c1.generateCompressorChart("normal curves", 5);
    c1.getCompressorChart().setUseCompressorChart(true);
    double design = c1.getSpeed();

    CompressorShaft shaft = new CompressorShaft("shaft");
    shaft.addCompressor(c1);
    shaft.setSpeed(design);

    // Reference discharge at the design speed, then ask for a slightly higher reachable target.
    c1.setSolveSpeed(false);
    c1.setSpeed(design);
    c1.run();
    double p0 = c1.getOutletStream().getPressure("bara");
    double target = p0 + 0.5;

    CompressorShaftCalculator calc = new CompressorShaftCalculator("shaft calc", shaft, c1, target, "bara");
    calc.setSpeedBounds(design * 0.5, design * 1.6);

    // Simulate the process iteration: run the body, then the calculator, repeatedly.
    UUID id = UUID.randomUUID();
    for (int i = 0; i < 40; i++) {
      c1.run();
      calc.run(id);
    }
    c1.run();

    assertEquals(target, c1.getOutletStream().getPressure("bara"), 0.4);
  }
}
