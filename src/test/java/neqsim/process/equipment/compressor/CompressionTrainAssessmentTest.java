package neqsim.process.equipment.compressor;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.capacity.CapacityConstraint;
import neqsim.process.equipment.heatexchanger.Cooler;
import neqsim.process.equipment.stream.Stream;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Real-gas two-stage candidate comparison and constraint-rejection regressions.
 *
 * @author NeqSim
 * @version 1.0
 */
class CompressionTrainAssessmentTest {
  /**
   * Builds a public synthetic two-stage train with independently controlled interstage cooling.
   *
   * @param interstageTemperature K
   * @return solved compressor bodies
   */
  private Compressor[] train(double interstageTemperature) {
    SystemSrkEos fluid = new SystemSrkEos(300, 20);
    fluid.addComponent("methane", 0.95);
    fluid.addComponent("ethane", 0.05);
    fluid.setMixingRule("classic");
    Stream feed = new Stream("net feed", fluid);
    feed.setFlowRate(10, "kg/sec");
    feed.run();
    Compressor lp = new Compressor("LP", feed);
    lp.setOutletPressure(50);
    lp.setUsePolytropicCalc(true);
    lp.setPolytropicEfficiency(0.8);
    lp.run();
    Cooler cooler = new Cooler("interstage", lp.getOutletStream());
    cooler.setOutTemperature(interstageTemperature);
    cooler.run();
    Compressor hp2 = new Compressor("HP2", cooler.getOutletStream());
    hp2.setOutletPressure(100);
    hp2.setUsePolytropicCalc(true);
    hp2.setPolytropicEfficiency(0.8);
    hp2.run();
    return new Compressor[] {lp, hp2};
  }

  /**
   * Assesses a two-body train on its net export basis.
   *
   * @param bodies solved bodies
   * @param budget kW
   * @param requireMaps map evidence policy
   * @return detached candidate
   */
  private CompressionTrainAssessment assess(Compressor[] bodies, double budget, boolean requireMaps) {
    return new CompressionTrainAssessment(Arrays.asList(bodies), bodies[1].getOutletStream(), budget, 100, 0.02, 0.1,
        0.05, requireMaps);
  }

  /** Checks cooling lowers shaft energy at equal net production and achieved pressure. */
  @Test
  void coolingComparisonUsesNetMassAndSummedStagePower() {
    Compressor[] cold = train(300);
    Compressor[] hot = train(340);
    CompressionTrainAssessment coldResult = assess(cold, 10000, false);
    CompressionTrainAssessment hotResult = assess(hot, 10000, false);
    assertTrue(coldResult.isFeasible(), coldResult.getViolations().toString());
    assertTrue(hotResult.isFeasible(), hotResult.getViolations().toString());
    assertEquals(10, coldResult.getNetExportKgPerSecond(), 1e-8);
    assertEquals(cold[0].getPower("kW") + cold[1].getPower("kW"), coldResult.getTotalShaftPowerKW(), 1e-8);
    assertEquals(coldResult.getTotalShaftPowerKW() / 10, coldResult.getSpecificEnergyKJPerKg(), 1e-8);
    assertTrue(coldResult.getSpecificEnergyKJPerKg() < hotResult.getSpecificEnergyKJPerKg());
    assertSame(coldResult, CompressionTrainAssessment.lowestSpecificEnergy(Arrays.asList(hotResult, coldResult)));
    double retainedPower = coldResult.getTotalShaftPowerKW();
    cold[0].setPolytropicEfficiency(0.5);
    cold[0].run();
    assertEquals(retainedPower, coldResult.getTotalShaftPowerKW(), 0);
    assertThrows(UnsupportedOperationException.class, () -> coldResult.getStages().clear());
  }

  /** Checks low-power but pressure-inadequate and driver-limited candidates cannot win. */
  @Test
  void pressurePowerCapacityAndMapEvidenceAreConstraints() {
    Compressor[] bodies = train(300);
    CompressionTrainAssessment baseline = assess(bodies, 10000, false);
    assertFalse(assess(bodies, 1, false).isFeasible());
    assertFalse(assess(bodies, 10000, true).isFeasible());
    bodies[1].getOutletStream().setPressure(80);
    CompressionTrainAssessment inadequate = assess(bodies, 10000, false);
    assertFalse(inadequate.isFeasible());
    assertSame(baseline, CompressionTrainAssessment.lowestSpecificEnergy(Arrays.asList(inadequate, baseline)));
    assertNull(CompressionTrainAssessment.lowestSpecificEnergy(Collections.singletonList(inadequate)));
    bodies[1].getOutletStream().setPressure(100);
    CapacityConstraint limit = new CapacityConstraint("vendorPower", "kW", CapacityConstraint.ConstraintType.HARD)
        .setDesignValue(1).setMaxValue(1).setCurrentValue(100).setSeverity(CapacityConstraint.ConstraintSeverity.HARD);
    bodies[0].addCapacityConstraint(limit);
    assertFalse(assess(bodies, 10000, false).isFeasible());
  }

  /** Checks duplicate bodies and missing/zero export cannot fabricate a candidate. */
  @Test
  void missingAndDuplicatedEvidenceIsRejected() {
    Compressor[] bodies = train(300);
    assertThrows(IllegalArgumentException.class,
        () -> new CompressionTrainAssessment(Arrays.asList(bodies[0], bodies[0]), bodies[1].getOutletStream(), 10000,
            100, 0.02, 0.1, 0.05, false));
    CompressionTrainAssessment missing = new CompressionTrainAssessment(Arrays.asList(bodies), null, 10000, 100, 0.02,
        0.1, 0.05, false);
    assertFalse(missing.isFeasible());
    assertTrue(Double.isNaN(missing.getSpecificEnergyKJPerKg()));
    bodies[1].getOutletStream().setFlowRate(0, "kg/sec");
    assertFalse(assess(bodies, 10000, false).isFeasible());
  }
}
