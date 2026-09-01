package neqsim.process.equipment.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.compressor.Compressor;
import neqsim.process.equipment.compressor.CompressorShaft;
import neqsim.process.equipment.stream.Stream;
import neqsim.process.processmodel.ProcessSystem;
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

  /**
   * When the target sits far above the maximum-speed capability, the calculator saturates at the max speed and reports
   * an infeasible PRESSURE_ABOVE_MAX_SPEED result instead of silently accepting the saturated speed.
   */
  @Test
  void testInfeasibleAboveMaxSpeedIsFlagged() {
    Stream f1 = feed(10.0);
    Compressor c1 = new Compressor("body1", f1);
    c1.setOutletPressure(20.0);
    c1.setUsePolytropicCalc(true);
    c1.setPolytropicEfficiency(0.75);
    c1.run();
    c1.generateCompressorChart("normal curves", 5);
    c1.getCompressorChart().setUseCompressorChart(true);
    double design = c1.getSpeed();

    // Discharge the machine can make at the max speed bound.
    c1.setSolveSpeed(false);
    c1.setSpeed(design * 1.3);
    c1.run();
    double pMax = c1.getOutletStream().getPressure("bara");

    CompressorShaft shaft = new CompressorShaft("shaft");
    shaft.addCompressor(c1);
    shaft.setSpeed(design);

    // Ask for far more than the max speed can deliver.
    CompressorShaftCalculator calc = new CompressorShaftCalculator("shaft calc", shaft, c1, pMax + 20.0, "bara");
    calc.setSpeedBounds(design * 0.7, design * 1.3);

    UUID id = UUID.randomUUID();
    for (int i = 0; i < 40; i++) {
      c1.run();
      calc.run(id);
    }

    assertFalse(calc.isFeasible());
    assertEquals(CompressorShaft.SolveStatus.PRESSURE_ABOVE_MAX_SPEED, calc.getLastSolveResult().getStatus());
    assertEquals(design * 1.3, calc.getSpeed(), design * 1.3 * 1e-3);
  }

  /**
   * A shaft power limit shows up as a {@code shaftPower} capacity constraint on the calculator, so the whole string
   * participates in the process utilization snapshot and bottleneck detection.
   */
  @Test
  void testShaftPowerConstraintInUtilizationSnapshot() {
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

    CompressorShaft shaft = new CompressorShaft("shaft");
    shaft.addCompressor(c1);
    shaft.addCompressor(c2);

    CompressorShaftCalculator calc = new CompressorShaftCalculator("shaft calc", shaft, c2, 40.0, "bara");
    assertFalse(calc.getCapacityConstraints().containsKey("shaftPower"));

    calc.setMaxShaftPower(shaft.getTotalPower("kW") / 0.8, "kW");
    assertTrue(calc.getCapacityConstraints().containsKey("shaftPower"));
    assertEquals(0.8, calc.getShaftPowerUtilization(), 1e-6);
    assertEquals(0.8, calc.getMaxUtilization(), 1e-6);
    assertEquals("shaftPower", calc.getBottleneckConstraint().getName());

    ProcessSystem process = new ProcessSystem();
    process.add(f1);
    process.add(c1);
    process.add(c2);
    process.add(calc);
    String snapshot = process.getUtilizationSnapshotJson();
    assertTrue(snapshot.contains("shaftPower"));
    assertTrue(snapshot.contains("shaft calc"));
  }
}
