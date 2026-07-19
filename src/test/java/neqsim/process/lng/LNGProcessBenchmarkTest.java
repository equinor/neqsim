package neqsim.process.lng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link LNGProcessBenchmark}.
 *
 * @author NeqSim contributors
 */
class LNGProcessBenchmarkTest {
  @ParameterizedTest
  @EnumSource(LNGProcessCycle.class)
  void testReferencePointPassesScreeningEnvelope(LNGProcessCycle cycle) {
    LNGProcessBenchmark.Benchmark benchmark = LNGProcessBenchmark.get(cycle);
    LNGProcessModel.Result result = new LNGProcessModel.Result("reference", cycle,
        20000.0, 19000.0, 0.95, 0.152, 5000.0, 0.0, 5000.0,
        benchmark.getReferenceSpecificEnergy(), -160.0, 1.2, 450.0, 3.0,
        1200.0, 100.0);

    LNGProcessBenchmark.Assessment assessment = LNGProcessBenchmark.assess(cycle, result);

    assertTrue(assessment.isWithinRange());
    assertEquals(0.0, assessment.getSpecificEnergyDeviation(), 1.0e-12);
    assertTrue(assessment.getMessages().isEmpty());
  }

  @Test
  void testInvalidResultProducesDiagnostics() {
    LNGProcessModel.Result result = new LNGProcessModel.Result("invalid",
        LNGProcessCycle.SMR, 20000.0, 10000.0, 0.50, 0.08, 10000.0, 0.0,
        10000.0, 1.0, -145.0, 1.2, 350.0, -1.0, 2000.0, 100.0);

    LNGProcessBenchmark.Assessment assessment =
        LNGProcessBenchmark.assess(LNGProcessCycle.SMR, result);

    assertFalse(assessment.isWithinRange());
    assertFalse(assessment.isEnergyWithinRange());
    assertFalse(assessment.isProductTemperatureWithinRange());
    assertFalse(assessment.isYieldWithinRange());
    assertFalse(assessment.isTemperatureApproachValid());
    assertEquals(4, assessment.getMessages().size());
  }

  @Test
  void testBenchmarkDefinitionsAreCompleteAndImmutable() {
    Map<LNGProcessCycle, LNGProcessBenchmark.Benchmark> benchmarks =
        LNGProcessBenchmark.getAll();

    assertEquals(LNGProcessCycle.values().length, benchmarks.size());
    assertEquals(0.2561,
        benchmarks.get(LNGProcessCycle.SMR).getReferenceSpecificEnergy(), 1.0e-12);
    assertEquals(0.2548,
        benchmarks.get(LNGProcessCycle.C3MR).getReferenceSpecificEnergy(), 1.0e-12);
    assertEquals(0.2456,
        benchmarks.get(LNGProcessCycle.DMR).getReferenceSpecificEnergy(), 1.0e-12);
    assertEquals(0.6180, benchmarks.get(LNGProcessCycle.NITROGEN_EXPANDER)
        .getReferenceSpecificEnergy(), 1.0e-12);
    assertThrows(UnsupportedOperationException.class,
        () -> benchmarks.remove(LNGProcessCycle.SMR));
  }
}
