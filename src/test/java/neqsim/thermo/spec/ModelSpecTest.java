package neqsim.thermo.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Executes every catalog row through the same strict numerical or explicit-absence contract. */
class ModelSpecTest extends neqsim.NeqSimTest {
  private static final List<String> REPORT = new ArrayList<String>();

  static Stream<ModelSpec> cases() throws Exception {
    List<ModelSpec> cases = ModelSpec.load();
    ModelSpecHarnessTest.requireCoverage(cases);
    ModelSpecInventory.validate(ModelSpecInventory.load(), ModelSpecInventory.discover(), cases);
    return cases.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void modelMatchesDeclaredContract(ModelSpec spec) {
    long start = System.nanoTime();
    try {
      if (spec.outcome == ModelSpec.Outcome.UNSUPPORTED) {
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
            () -> ModelSpecFixtures.evaluate(spec), spec.toString());
        assertTrue(exception.getMessage().contains("UNIQUAC"), spec.toString());
        record(spec, "EXPECTED_UNSUPPORTED", "-", start);
      } else {
        double actual = ModelSpecFixtures.evaluate(spec);
        check(spec, actual);
        record(spec, "PASS", Double.toString(actual), start);
      }
    } catch (RuntimeException | AssertionError ex) {
      record(spec, "FAIL", ex.toString().replace('\n', ' ').replace('\t', ' '), start);
      throw ex;
    }
  }

  static void check(ModelSpec s, double actual) {
    if (s.outcome == ModelSpec.Outcome.UNAVAILABLE) {
      assertTrue(Double.isNaN(actual), s + ": expected documented absence, got " + actual);
      return;
    }
    assertEquals(ModelSpec.Outcome.VALUE, s.outcome, "numeric assertion cannot handle unsupported outcome");
    assertTrue(Double.isFinite(actual), s + ": nonfinite value " + actual);
    if (ModelSpecFixtures.isPositiveOnly(s.property)) {
      ModelSpecFixtures.positive(actual, s.toString());
    }
    assertEquals(s.expected, actual, s.absTol + s.relTol * Math.abs(s.expected),
        s + ": expected " + s.expected + " " + s.unit + ", source=" + s.source);
  }

  private static synchronized void record(ModelSpec s, String verdict, String value, long start) {
    REPORT.add(s.id + "\t" + s.fixture + "\t" + s.property + "\t" + s.outcome + "\t" + verdict + "\t" + value + "\t"
        + (System.nanoTime() - start) / 1000000L);
  }

  @AfterAll
  static void publishReport() throws IOException {
    Path directory = Paths.get("target", "model-spec");
    Files.createDirectories(directory);
    List<String> lines = new ArrayList<String>();
    lines.add("id\tfixture\tproperty\texpectedOutcome\tverdict\tactualOrFailure\telapsedMs");
    synchronized (ModelSpecTest.class) {
      lines.addAll(REPORT);
    }
    Files.write(directory.resolve("results.tsv"), lines, StandardCharsets.UTF_8);
    assertEquals(ModelSpec.load().size(), REPORT.size(), "all required cases must execute");
  }
}
