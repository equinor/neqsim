package neqsim.process.equipment.pipeline.twophasepipe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Fast regression coverage for the public experimental gate used by the slow Tengesdal fixture. */
class SevereSluggingQualificationCriteriaTest {
  @ParameterizedTest
  @CsvSource({ "98000, 38", "68600, 26.6", "127400, 49.4" })
  void acceptsTargetsAndThirtyPercentBoundaries(double pressureAmplitudePa, double liquidCyclePeriodSeconds) {
    assertDoesNotThrow(() -> SevereSluggingExperimentalBenchmarkTest.assertExperimentalAgreement(pressureAmplitudePa,
        liquidCyclePeriodSeconds));
  }

  @ParameterizedTest
  @CsvSource({ "68599, 38", "127401, 38", "137200, 38", "98000, 26.599", "98000, 49.401", "98000, 53.2", "NaN, 38",
      "Infinity, 38", "-Infinity, 38", "98000, NaN", "98000, Infinity", "98000, -Infinity" })
  void rejectsOutOfBandAndNonFinitePredictions(double pressureAmplitudePa, double liquidCyclePeriodSeconds) {
    assertThrows(AssertionError.class, () -> SevereSluggingExperimentalBenchmarkTest
        .assertExperimentalAgreement(pressureAmplitudePa, liquidCyclePeriodSeconds));
  }
}
