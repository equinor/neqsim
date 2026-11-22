package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import neqsim.process.equipment.pipeline.twophasepipe.PipeSection.FlowRegime;

/**
 * Checks that a detector which has already classified other cells answers like a new detector.
 *
 * <p>
 * {@link FlowRegimeDetector} keeps the last equilibrium stratified level so that classification and its transition
 * weights solve the level once per cell. These tests guard the condition that makes that safe: the kept level is used
 * only for the cell state it was solved for.
 * </p>
 *
 * @author ESOL
 * @version 1.0
 */
class FlowRegimeRepeatedClassificationTest {
  /**
   * Builds a near-horizontal gas-liquid section.
   *
   * @param superficialLiquid superficial liquid velocity, in m/s
   * @param superficialGas superficial gas velocity, in m/s
   * @param inclination inclination, in radians
   * @return a section ready for regime detection
   */
  private static PipeSection section(double superficialLiquid, double superficialGas, double inclination) {
    PipeSection section = new PipeSection(0.0, 100.0, 0.20, inclination);
    section.setGasHoldup(0.95);
    section.setLiquidHoldup(0.05);
    section.setGasVelocity(superficialGas / 0.95);
    section.setLiquidVelocity(superficialLiquid / 0.05);
    section.setGasDensity(45.0);
    section.setLiquidDensity(600.0);
    section.setGasViscosity(1.3e-5);
    section.setLiquidViscosity(5.0e-4);
    section.setSurfaceTension(0.02);
    section.updateDerivedQuantities();
    return section;
  }

  /**
   * One detector classifying a sweep of cells, each twice and out of order, gives the regimes and weights of a new
   * detector per cell.
   */
  @Test
  void reusedDetectorMatchesFreshDetectorForEveryCell() {
    double[][] cases = {{0.02, 1.0, 0.0}, {0.02, 6.0, 0.0}, {0.02, 12.0, 0.0}, {0.30, 3.0, 0.0},
        {0.02, 6.0, Math.toRadians(1.0)}, {0.02, 6.0, Math.toRadians(-1.0)}, {0.02, Math.nextUp(6.0), 0.0}};
    FlowRegimeDetector reused = new FlowRegimeDetector();
    int weighted = 0;
    for (int pass = 0; pass < 2; pass++) {
      for (int index = cases.length - 1; index >= 0; index--) {
        double[] state = cases[(index * 3 + pass) % cases.length];
        PipeSection expected = section(state[0], state[1], state[2]);
        new FlowRegimeDetector().classify(expected);

        PipeSection actual = section(state[0], state[1], state[2]);
        FlowRegime regime = reused.classify(actual);
        assertEquals(expected.getFlowRegime(), regime);
        // Classifying the same cell again must repeat the answer.
        assertEquals(regime, reused.classify(actual));
        Map<FlowRegime, Double> expectedWeights = expected.getRegimeWeights();
        if (expectedWeights != null) {
          weighted++;
          Map<FlowRegime, Double> actualWeights = actual.getRegimeWeights();
          assertNotNull(actualWeights);
          assertEquals(expectedWeights.keySet(), actualWeights.keySet());
          for (Map.Entry<FlowRegime, Double> entry : expectedWeights.entrySet()) {
            assertEquals(entry.getValue().doubleValue(), actualWeights.get(entry.getKey()).doubleValue());
          }
        }
      }
    }
    assertTrue(weighted > 0, "the sweep must include blended cells, where the level is used twice");
  }
}
