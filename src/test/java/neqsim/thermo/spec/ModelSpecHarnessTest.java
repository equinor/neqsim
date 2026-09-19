package neqsim.thermo.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies that malformed catalogs and defective values cannot make the harness pass silently. */
class ModelSpecHarnessTest {
  private static final Set<String> REQUIRED = new HashSet<String>(Arrays.asList("acetone-280", "acetone-298-15",
      "acetone-320", "i-pentane-290", "i-pentane-298-15", "i-pentane-301", "wilson-0-2-0", "wilson-0-2-1",
      "wilson-0-5-0", "wilson-0-5-1", "wilson-0-8-0", "wilson-0-8-1", "wilson-negative-log", "unifac-pure-290",
      "unifac-pure-310", "unifac-group-r", "psrk-pure-290", "psrk-pure-310", "psrk-group-r", "umr-pure-290",
      "umr-pure-310", "umr-group-r", "srk-dilute-z", "srk-reference-hid", "pr-dilute-z", "pr-reference-hid",
      "missing-hydrogen", "missing-nc20", "ion-sodium", "supercritical-methane", "unsupported-uniquac"));

  static void requireCoverage(List<ModelSpec> cases) {
    Set<String> actual = new HashSet<String>();
    Set<ModelSpec.Fixture> fixtures = EnumSet.noneOf(ModelSpec.Fixture.class);
    for (ModelSpec spec : cases) {
      assertTrue(actual.add(spec.id), "duplicate executed case " + spec.id);
      fixtures.add(spec.fixture);
    }
    assertTrue(actual.containsAll(REQUIRED), "missing required case IDs: " + difference(REQUIRED, actual));
    assertEquals(EnumSet.allOf(ModelSpec.Fixture.class), fixtures, "every curated adapter must execute");
  }

  private static Set<String> difference(Set<String> required, Set<String> actual) {
    Set<String> missing = new HashSet<String>(required);
    missing.removeAll(actual);
    return missing;
  }

  private static String catalog() throws IOException {
    StringBuilder text = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(ModelSpec.class.getResourceAsStream(ModelSpec.RESOURCE), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        text.append(line).append('\n');
      }
    }
    return text.toString();
  }

  @Test
  void allRequiredCasesAndAdaptersArePresent() throws IOException {
    requireCoverage(ModelSpec.load());
  }

  @Test
  void omittingARequiredCaseFails() throws IOException {
    List<ModelSpec> cases = new ArrayList<ModelSpec>(ModelSpec.load());
    cases.remove(0);
    assertThrows(AssertionError.class, () -> requireCoverage(cases));
  }

  @ParameterizedTest
  @ValueSource(strings = {"empty", "version", "header", "duplicate", "trailing-column", "unknown-fixture",
      "unknown-property", "nan-reference", "nan-tolerance", "negative-tolerance", "unit", "domain", "source",
      "provenance", "amount", "absent-reference", "unknown-reason", "outcome", "phase", "operation", "mixing-rule"})
  void malformedCatalogFailsClosed(String fault) throws IOException {
    String original = catalog();
    String[] lines = original.split("\n");
    String[] cells = lines[2].split("\t", -1);
    String changed;
    switch (fault) {
    case "empty":
      changed = lines[0] + "\n" + lines[1] + "\n";
      break;
    case "version":
      changed = original.replace("spec-v1", "spec-v9");
      break;
    case "header":
      changed = original.replace("temperatureK", "temperatureC");
      break;
    case "duplicate":
      changed = original + lines[2] + "\n";
      break;
    case "trailing-column":
      changed = lines[0] + "\n" + lines[1] + "\n" + lines[2] + "\textra\n";
      break;
    default:
      switch (fault) {
      case "unknown-fixture":
        cells[1] = "UNKNOWN";
        break;
      case "unknown-property":
        cells[2] = "UNKNOWN";
        break;
      case "nan-reference":
        cells[13] = "NaN";
        break;
      case "nan-tolerance":
        cells[14] = "NaN";
        break;
      case "negative-tolerance":
        cells[15] = "-1";
        break;
      case "unit":
        cells[10] = "Pa";
        break;
      case "domain":
        cells[16] = "400";
        break;
      case "source":
        cells[18] = "-";
        break;
      case "provenance":
        cells[19] = "-";
        break;
      case "amount":
        cells[3] = "acetone=2";
        break;
      case "absent-reference":
        cells[12] = "UNAVAILABLE";
        cells[20] = "missing";
        break;
      case "unknown-reason":
        cells[12] = "UNAVAILABLE";
        cells[13] = "-";
        cells[20] = "anything";
        break;
      case "outcome":
        cells[12] = "PASS_ANYWAY";
        break;
      case "phase":
        cells[6] = "anything";
        break;
      case "operation":
        cells[9] = "anything";
        break;
      case "mixing-rule":
        cells[8] = "anything";
        break;
      default:
        throw new AssertionError(fault);
      }
      changed = lines[0] + "\n" + lines[1] + "\n" + String.join("\t", cells) + "\n";
    }
    final String invalid = changed;
    assertThrows(IllegalArgumentException.class, () -> ModelSpec.parse(invalid), fault);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, -1.0, 1e38, 1e96, 0.306})
  void incorrectFiniteNumbersFail(double actual) throws IOException {
    ModelSpec acetone280 = ModelSpec.load().get(0);
    assertThrows(AssertionError.class, () -> ModelSpecTest.check(acetone280, actual));
  }

  @Test
  void nonfiniteAndWrongAbsenceCannotPass() throws IOException {
    ModelSpec value = ModelSpec.load().get(0);
    for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(value, bad));
    }
    for (ModelSpec spec : ModelSpec.load()) {
      if (spec.outcome == ModelSpec.Outcome.UNAVAILABLE) {
        assertThrows(AssertionError.class, () -> ModelSpecTest.check(spec, 0.0));
        assertThrows(AssertionError.class, () -> ModelSpecTest.check(spec, 1.0));
        ModelSpecTest.check(spec, Double.NaN);
      }
      if (spec.property == ModelSpec.Property.HID || spec.property == ModelSpec.Property.LN_GAMMA) {
        ModelSpecTest.check(spec, spec.expected);
      }
    }
  }

  @Test
  void dataAndCatalogOnlyChangesSelectTheCiGate() throws IOException {
    String workflow = new String(Files.readAllBytes(Paths.get(".github/workflows/verify_build.yml")),
        StandardCharsets.UTF_8);
    String filter = workflow.substring(workflow.indexOf("run_tests:"), workflow.indexOf("  test_javadoc:"));
    assertTrue(filter.contains("- 'src/main/resources/data/**'"));
    assertTrue(filter.contains("- 'src/test/resources/**'"));
    String job = workflow.substring(workflow.indexOf("  model_spec:"), workflow.indexOf("  agent_benchmark:"));
    assertTrue(job.contains("needs: changes"));
    assertTrue(job.contains("-Dtest=ModelSpec*Test,ComponentCorrelationSpecTest"));
    assertTrue(job.contains("-DfailIfNoTests=true"));
    assertTrue(job.contains("-Dsurefire.failIfNoSpecifiedTests=true"));
    assertTrue(job.contains("pomJava8.xml"));
    assertTrue(job.contains("pom.xml"));
    assertTrue(!job.contains("continue-on-error") && !job.contains("failIfNoTests=false"));
  }
}
