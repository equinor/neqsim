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
  private static final double METHANE_TC_K = 190.56;
  private static final double METHANE_PC_BAR = 45.99;
  private static final double METHANE_OMEGA = 0.0115;
  private static final Set<String> REQUIRED = required(new HashSet<String>(Arrays.asList("acetone-280",
      "acetone-298-15", "acetone-320", "i-pentane-290", "i-pentane-298-15", "i-pentane-301", "wilson-0-2-0",
      "wilson-0-2-1", "wilson-0-5-0", "wilson-0-5-1", "wilson-0-8-0", "wilson-0-8-1", "wilson-negative-log",
      "unifac-pure-290", "unifac-pure-310", "unifac-group-r", "psrk-pure-290", "psrk-pure-310", "psrk-group-r",
      "umr-pure-290", "umr-pure-310", "umr-group-r", "srk-dilute-z", "srk-reference-hid", "pr-dilute-z",
      "pr-reference-hid", "missing-hydrogen", "missing-nc20", "ion-sodium", "supercritical-methane",
      "unsupported-uniquac", "pow10kpa-derivative-260", "pow10kpa-derivative-300", "pow10kpa-derivative-350",
      "pow10kpa-inverse-260", "pow10kpa-inverse-300", "pow10kpa-inverse-350", "srk-methane-z-280-10",
      "srk-methane-phi-280-10", "srk-methane-z-300-30", "srk-methane-phi-300-30", "srk-methane-z-320-50",
      "srk-methane-phi-320-50", "pr-methane-z-280-10", "pr-methane-phi-280-10", "pr-methane-z-300-30",
      "pr-methane-phi-300-30", "pr-methane-z-320-50", "pr-methane-phi-320-50", "phase-srk-methane-z-280-10",
      "phase-srk-methane-phi-280-10", "phase-srk-methane-z-300-30", "phase-srk-methane-phi-300-30",
      "phase-srk-methane-z-320-50", "phase-srk-methane-phi-320-50", "phase-pr-methane-z-280-10",
      "phase-pr-methane-phi-280-10", "phase-pr-methane-z-300-30", "phase-pr-methane-phi-300-30",
      "phase-pr-methane-z-320-50", "phase-pr-methane-phi-320-50")));

  private static Set<String> required(Set<String> ids) {
    ids.addAll(Arrays.asList("phase-wilson-0-2-0", "phase-wilson-0-2-1", "phase-wilson-0-5-0", "phase-wilson-0-5-1",
        "phase-wilson-0-8-0", "phase-wilson-0-8-1", "phase-wilson-negative-log"));
    for (String fixture : new String[] {"system", "phase"}) {
      for (String temperature : new String[] {"298", "323"}) {
        for (String composition : new String[] {"02", "05", "08"}) {
          ids.add("nrtl-" + fixture + "-t" + temperature + "-x" + composition + "-gamma-0");
          ids.add("nrtl-" + fixture + "-t" + temperature + "-x" + composition + "-gamma-1");
          ids.add("nrtl-" + fixture + "-t" + temperature + "-x" + composition + "-ln-gamma-0");
          ids.add("nrtl-" + fixture + "-t" + temperature + "-x" + composition + "-ln-gamma-1");
          ids.add("nrtl-" + fixture + "-t" + temperature + "-x" + composition + "-gex");
        }
      }
    }
    for (String fixture : new String[] {"unifac", "psrk", "umr"}) {
      ids.add("phase-" + fixture + "-pure-290");
      ids.add("phase-" + fixture + "-pure-310");
      ids.add("phase-" + fixture + "-group-r");
      ids.add("phase-" + fixture + "-group-q");
      ids.add("phase-" + fixture + "-a-methanol-water");
      ids.add("phase-" + fixture + "-a-water-methanol");
    }
    return ids;
  }

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
  @ValueSource(ints = {4, 7, 13, 14, 15})
  void invalidNumbersRetainCatalogLineAndCause(int column) throws IOException {
    String[] lines = catalog().split("\n");
    String[] cells = lines[2].split("\t", -1);
    cells[column] = "not-a-number";
    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
        () -> ModelSpec.parse(lines[0] + "\n" + lines[1] + "\n" + String.join("\t", cells) + "\n"));
    assertTrue(error.getMessage().startsWith("catalog line 3:"));
    assertTrue(error.getCause() instanceof NumberFormatException);
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
  void cubicReferencesSatisfyPublishedPureFluidEquations() throws IOException {
    List<ModelSpec> cases = ModelSpec.load();
    for (ModelSpec spec : cases) {
      if (!isCubic(spec.fixture) || !spec.source.startsWith("https://doi.org/")) {
        continue;
      }
      double tr = spec.temperature / METHANE_TC_K;
      double pr = spec.pressure / METHANE_PC_BAR;
      boolean pengRobinson = spec.fixture == ModelSpec.Fixture.PR || spec.fixture == ModelSpec.Fixture.PR_PHASE;
      double m = pengRobinson ? 0.37464 + 1.54226 * METHANE_OMEGA - 0.26992 * METHANE_OMEGA * METHANE_OMEGA
          : 0.48 + 1.574 * METHANE_OMEGA - 0.176 * METHANE_OMEGA * METHANE_OMEGA;
      double alpha = Math.pow(1.0 + m * (1.0 - Math.sqrt(tr)), 2.0);
      double omegaA = pengRobinson ? 0.45724333333 : 1.0 / (9.0 * (Math.cbrt(2.0) - 1.0));
      double omegaB = pengRobinson ? 0.077803333 : (Math.cbrt(2.0) - 1.0) / 3.0;
      double a = omegaA * alpha * pr / (tr * tr);
      double b = omegaB * pr / tr;
      double z = cubicZ(cases, spec);
      double residual = pengRobinson
          ? z * z * z - (1.0 - b) * z * z + (a - 3.0 * b * b - 2.0 * b) * z - (a * b - b * b - b * b * b)
          : z * z * z - z * z + (a - b - b * b) * z - a * b;
      assertEquals(0.0, residual, 2e-15, spec.toString());
      if (spec.property == ModelSpec.Property.PHI) {
        double lnPhi = pengRobinson
            ? z - 1.0 - Math.log(z - b)
                - a / (2.0 * Math.sqrt(2.0) * b)
                    * Math.log((z + (1.0 + Math.sqrt(2.0)) * b) / (z + (1.0 - Math.sqrt(2.0)) * b))
            : z - 1.0 - Math.log(z - b) - a / b * Math.log(1.0 + b / z);
        assertEquals(spec.expected, Math.exp(lnPhi), 1e-14, spec.toString());
      }
    }
  }

  @Test
  void cubicFugacityRejectsZeroNonfiniteAndPlausiblePlaceholders() throws IOException {
    ModelSpec reference = null;
    for (ModelSpec spec : ModelSpec.load()) {
      if ("srk-methane-phi-300-30".equals(spec.id)) {
        reference = spec;
      }
    }
    assertTrue(reference != null);
    final ModelSpec checked = reference;
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 0.95}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(checked, bad));
    }
    ModelSpecTest.check(checked, checked.expected);
  }

  @Test
  void signedInteractionCoefficientRejectsZeroNonfiniteAndPlausiblePlaceholders() throws IOException {
    ModelSpec reference = null;
    for (ModelSpec spec : ModelSpec.load()) {
      if ("phase-unifac-a-methanol-water".equals(spec.id)) {
        reference = spec;
      }
    }
    assertTrue(reference != null);
    final ModelSpec checked = reference;
    for (double bad : new double[] {0.0, Double.NaN, Double.NEGATIVE_INFINITY, -180.0, 181.0}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(checked, bad));
    }
    ModelSpecTest.check(checked, checked.expected);
  }

  @Test
  void nrtlReferencesSatisfyPublishedLocalCompositionEquation() throws IOException {
    int checked = 0;
    for (ModelSpec spec : ModelSpec.load()) {
      if (!isNrtl(spec.fixture)) {
        continue;
      }
      double[] reference = nrtl(spec.components.get("methanol"), spec.temperature);
      double expected = spec.property == ModelSpec.Property.GEX ? reference[2]
          : spec.property == ModelSpec.Property.LN_GAMMA ? Math.log(reference[spec.componentIndex])
              : reference[spec.componentIndex];
      assertEquals(expected, spec.expected, spec.property == ModelSpec.Property.GEX ? 1e-10 : 1e-14, spec.toString());
      checked++;
    }
    assertEquals(60, checked, "every prescribed NRTL catalog anchor must be independently reconstructed");
  }

  @Test
  void nrtlActivityRejectsZeroNonfiniteAndPlausiblePlaceholders() throws IOException {
    ModelSpec reference = null;
    for (ModelSpec spec : ModelSpec.load()) {
      if ("nrtl-system-t298-x05-gamma-0".equals(spec.id)) {
        reference = spec;
      }
    }
    assertTrue(reference != null);
    final ModelSpec checked = reference;
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 1.05}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(checked, bad));
    }
    ModelSpecTest.check(checked, checked.expected);
  }

  @Test
  void nrtlLogActivityRejectsStaleZeroNonfiniteAndPlausiblePlaceholder() throws IOException {
    ModelSpec reference = null;
    for (ModelSpec spec : ModelSpec.load()) {
      if ("nrtl-system-t298-x05-ln-gamma-0".equals(spec.id)) {
        reference = spec;
      }
    }
    assertTrue(reference != null);
    final ModelSpec checked = reference;
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 0.05}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(checked, bad));
    }
    ModelSpecTest.check(checked, checked.expected);
  }

  private static boolean isCubic(ModelSpec.Fixture fixture) {
    return fixture == ModelSpec.Fixture.SRK || fixture == ModelSpec.Fixture.PR || fixture == ModelSpec.Fixture.SRK_PHASE
        || fixture == ModelSpec.Fixture.PR_PHASE;
  }

  private static boolean isNrtl(ModelSpec.Fixture fixture) {
    return fixture == ModelSpec.Fixture.NRTL_ANALYTIC || fixture == ModelSpec.Fixture.NRTL_PHASE;
  }

  private static double[] nrtl(double methanolFraction, double temperature) {
    double[] x = {methanolFraction, 1.0 - methanolFraction};
    double[][] alpha = {{0.0, 0.3}, {0.3, 0.0}};
    double[][] interaction = {{0.0, 200.0}, {-100.0, 0.0}};
    double[] gamma = new double[2];
    for (int i = 0; i < 2; i++) {
      double numerator = 0.0;
      double denominator = 0.0;
      for (int j = 0; j < 2; j++) {
        double tau = interaction[j][i] / temperature;
        double g = Math.exp(-alpha[j][i] * tau);
        numerator += x[j] * tau * g;
        denominator += x[j] * g;
      }
      double second = 0.0;
      for (int j = 0; j < 2; j++) {
        double tau = interaction[i][j] / temperature;
        double g = Math.exp(-alpha[i][j] * tau);
        double column = 0.0;
        double weightedColumn = 0.0;
        for (int k = 0; k < 2; k++) {
          double tauKj = interaction[k][j] / temperature;
          double gKj = Math.exp(-alpha[k][j] * tauKj);
          column += x[k] * gKj;
          weightedColumn += x[k] * tauKj * gKj;
        }
        second += x[j] * g / column * (tau - weightedColumn / column);
      }
      gamma[i] = Math.exp(numerator / denominator + second);
    }
    double excess = 8.3144621 * temperature * (x[0] * Math.log(gamma[0]) + x[1] * Math.log(gamma[1]));
    return new double[] {gamma[0], gamma[1], excess};
  }

  private static double cubicZ(List<ModelSpec> cases, ModelSpec reference) {
    for (ModelSpec candidate : cases) {
      if (candidate.fixture == reference.fixture && candidate.property == ModelSpec.Property.Z
          && candidate.temperature == reference.temperature && candidate.pressure == reference.pressure) {
        return candidate.expected;
      }
    }
    throw new AssertionError("missing cubic Z companion for " + reference);
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
    assertTrue(job.contains("needs.changes.outputs.run_tests == 'true'"));
    assertTrue(job.contains("target/model-spec/coverage.md"));
    assertTrue(job.contains("-Dtest=ModelSpec*Test,ComponentCorrelationSpecTest"));
    assertTrue(job.contains("-DfailIfNoTests=true"));
    assertTrue(job.contains("-Dsurefire.failIfNoSpecifiedTests=true"));
    assertTrue(job.contains("pomJava8.xml"));
    assertTrue(job.contains("pom.xml"));
    assertTrue(!job.contains("continue-on-error") && !job.contains("failIfNoTests=false"));
  }
}
