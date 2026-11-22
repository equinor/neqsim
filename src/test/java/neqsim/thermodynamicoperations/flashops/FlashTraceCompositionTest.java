package neqsim.thermodynamicoperations.flashops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Regression coverage for finite flash iterates with exact-zero composition support.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class FlashTraceCompositionTest {
  /** @return initialized binary fluid */
  private SystemInterface createFluid() {
    SystemInterface fluid = new SystemSrkEos(298.15, 10.0);
    fluid.addComponent("methane", 0.5);
    fluid.addComponent("nC10", 0.5);
    fluid.setMixingRule(2);
    fluid.init(0);
    fluid.init(1);
    return fluid;
  }

  /** Finite weights may have an infinite sum; the trial must remain normalized. */
  @Test
  void overflowingWeightSumDoesNotEraseTheTrialPhase() {
    SystemInterface fluid = createFluid();
    Flash.normalizeStabilityTrial(fluid.getPhase(1), new double[] {Double.MAX_VALUE, Double.MAX_VALUE});
    assertEquals(0.5, fluid.getPhase(1).getComponent(0).getx(), 0.0);
    assertEquals(0.5, fluid.getPhase(1).getComponent(1).getx(), 0.0);
  }

  /** Normalization preserves both absent species and representable positive traces. */
  @Test
  void trialNormalizationPreservesZeroAndTraceFractions() {
    SystemInterface fluid = createFluid();
    Flash.normalizeStabilityTrial(fluid.getPhase(1), new double[] {1.0, 0.0});
    assertEquals(0.0, fluid.getPhase(1).getComponent(1).getx(), 0.0);
    Flash.normalizeStabilityTrial(fluid.getPhase(1), new double[] {1.0, 1.0e-200});
    assertEquals(1.0e-200, fluid.getPhase(1).getComponent(1).getx(), 1.0e-214);
    assertThrows(IllegalStateException.class,
        () -> Flash.normalizeStabilityTrial(fluid.getPhase(1), new double[] {0.0, 0.0}));
  }

  /** Unrepresentable fugacity ratios must not create zero or infinite K-values. */
  @Test
  void successiveSubstitutionBoundsOverflowingAndUnderflowingRatios() {
    SystemInterface fluid = createFluid();
    fluid.getPhase(0).getComponent(0).setFugacityCoefficient(1.0e-300);
    fluid.getPhase(1).getComponent(0).setFugacityCoefficient(1.0e300);
    fluid.getPhase(0).getComponent(1).setFugacityCoefficient(1.0e300);
    fluid.getPhase(1).getComponent(1).setFugacityCoefficient(1.0e-300);
    new TPflash(fluid).sucsSubs();
    assertEquals(1.0e50, fluid.getPhase(0).getComponent(0).getK(), 0.0);
    assertEquals(1.0e-50, fluid.getPhase(0).getComponent(1).getK(), 0.0);
    for (int phase = 0; phase < 2; phase++) {
      double sum = 0.0;
      for (int component = 0; component < 2; component++) {
        double x = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(x) && x > 0.0);
        sum += x;
      }
      assertEquals(1.0, sum, 1.0e-12);
    }
  }
}
