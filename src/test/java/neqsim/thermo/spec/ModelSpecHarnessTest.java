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
    for (String fixture : new String[] {"system", "phase"}) {
      for (String temperature : new String[] {"298", "323"}) {
        for (String composition : new String[] {"02", "05", "08"}) {
          ids.add("unifac-" + fixture + "-t" + temperature + "-x" + composition + "-gamma-0");
          ids.add("unifac-" + fixture + "-t" + temperature + "-x" + composition + "-gamma-1");
          ids.add("unifac-" + fixture + "-t" + temperature + "-x" + composition + "-ln-gamma-0");
          ids.add("unifac-" + fixture + "-t" + temperature + "-x" + composition + "-ln-gamma-1");
          ids.add("unifac-" + fixture + "-t" + temperature + "-x" + composition + "-gex");
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
    for (String fixture : new String[] {"system", "phase"}) {
      for (String property : new String[] {"molar-mass", "molar-density", "z", "dpd-density", "d2pd-density2", "dpd-t",
          "internal-energy", "enthalpy", "entropy", "cv", "cp", "sound-speed", "gibbs-energy", "jt", "kappa"}) {
        ids.add("gerg-" + fixture + "-nist-" + property);
      }
    }
    for (String fixture : new String[] {"system", "phase"}) {
      for (String temperature : new String[] {"298", "400", "600"}) {
        for (String property : new String[] {"molar-mass", "molar-density", "z", "phi", "cp", "cv", "sound-speed",
            "jt"}) {
          ids.add("ideal-" + fixture + "-argon-" + temperature + "-" + property);
        }
      }
    }
    for (String fixture : new String[] {"system", "phase"}) {
      for (String state : new String[] {"gas-293-5", "gas-400-50", "liquid-293-10", "liquid-280-10"}) {
        for (String property : new String[] {"molar-mass", "molar-density", "mass-density", "z", "internal-energy",
            "enthalpy", "entropy", "cv", "cp", "sound-speed", "jt", "kappa"}) {
          ids.add("ammonia-" + fixture + "-" + state + "-" + property);
        }
      }
    }
    for (String fixture : new String[] {"system", "phase"}) {
      for (String state : new String[] {"gas-300-10", "gas-100-50", "liquid-25-10", "liquid-20-5"}) {
        for (String property : new String[] {"molar-mass", "molar-density", "mass-density", "z", "internal-energy",
            "enthalpy", "entropy", "cv", "cp", "sound-speed", "gibbs-energy", "jt", "kappa"}) {
          ids.add("leachman-" + fixture + "-" + state + "-" + property);
        }
      }
    }
    for (String fixture : new String[] {"system", "phase"}) {
      for (String state : new String[] {"gas-300-10", "gas-250-25", "gas-150-50", "gas-100-50"}) {
        for (String property : new String[] {"molar-mass", "molar-density", "mass-density", "z", "internal-energy",
            "enthalpy", "entropy", "cv", "cp", "sound-speed", "gibbs-energy", "jt", "kappa"}) {
          ids.add("vega-" + fixture + "-" + state + "-" + property);
        }
      }
    }
    for (String fixture : new String[] {"system", "phase"}) {
      for (String state : new String[] {"gas-300-10", "liquid-280-50", "supercritical-320-80",
          "supercritical-350-200"}) {
        for (String property : new String[] {"molar-mass", "molar-density", "mass-density", "z", "phi",
            "internal-energy", "enthalpy", "entropy", "gibbs-energy", "cv", "cp", "sound-speed", "jt"}) {
          ids.add("span-wagner-" + fixture + "-" + state + "-" + property);
        }
      }
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
  void gergReferencesRejectZeroNonfiniteAndPlausiblePlaceholders() throws IOException {
    ModelSpec z = find("gerg-system-nist-z");
    ModelSpec enthalpy = find("gerg-system-nist-enthalpy");
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 1.17}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(z, bad));
    }
    for (double bad : new double[] {0.0, Double.NaN, Double.NEGATIVE_INFINITY, 1000.0, 1161.0}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(enthalpy, bad));
    }
    ModelSpecTest.check(z, z.expected);
    ModelSpecTest.check(enthalpy, enthalpy.expected);
  }

  @Test
  void gergOfficialReferenceSatisfiesThermodynamicIdentities() throws IOException {
    double density = find("gerg-system-nist-molar-density").expected;
    double internalEnergy = find("gerg-system-nist-internal-energy").expected;
    double enthalpy = find("gerg-system-nist-enthalpy").expected;
    double entropy = find("gerg-system-nist-entropy").expected;
    double gibbsEnergy = find("gerg-system-nist-gibbs-energy").expected;
    double cv = find("gerg-system-nist-cv").expected;
    double cp = find("gerg-system-nist-cp").expected;
    assertEquals(enthalpy, internalEnergy + 50000.0 / density, 1e-9);
    assertEquals(gibbsEnergy, enthalpy - 400.0 * entropy, 1e-9);
    assertTrue(cp > cv && cv > 0.0);
  }

  @Test
  void idealGasReferencesReconstructNistArgonAndIdealEquations() throws IOException {
    int checked = 0;
    for (ModelSpec spec : ModelSpec.load()) {
      if (spec.fixture != ModelSpec.Fixture.IDEAL_GAS && spec.fixture != ModelSpec.Fixture.IDEAL_GAS_PHASE) {
        continue;
      }
      double t = spec.temperature / 1000.0;
      double cp = 20.78600 + 2.825911e-7 * t - 1.464191e-7 * t * t + 1.092131e-8 * t * t * t - 3.661371e-8 / (t * t);
      double cv = cp - 8.31446261815324;
      double expected;
      switch (spec.property) {
      case MOLAR_MASS:
        expected = 39.948;
        break;
      case MOLAR_DENSITY:
        expected = spec.pressure * 1.0e5 / (8.31446261815324 * spec.temperature) / 1000.0;
        break;
      case Z:
      case PHI:
        expected = 1.0;
        break;
      case CP:
        expected = cp;
        break;
      case CV:
        expected = cv;
        break;
      case SOUND_SPEED:
        expected = Math.sqrt(cp / cv * 8.31446261815324 * spec.temperature / 0.039948);
        break;
      case JT:
        expected = 0.0;
        break;
      default:
        throw new AssertionError(spec.property);
      }
      assertEquals(expected, spec.expected, 1e-12, spec.toString());
      checked++;
    }
    assertEquals(48, checked, "every NIST argon ideal-gas anchor must be independently reconstructed");
  }

  @Test
  void idealGasAnchorsRejectPositiveAndZeroPlaceholders() throws IOException {
    ModelSpec density = find("ideal-system-argon-400-molar-density");
    ModelSpec heatCapacity = find("ideal-system-argon-600-cp");
    ModelSpec zeroJouleThomson = find("ideal-system-argon-400-jt");
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 1.05}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(density, bad));
    }
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 20.0, 21.0}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(heatCapacity, bad));
    }
    ModelSpecTest.check(zeroJouleThomson, 0.0);
    assertThrows(AssertionError.class, () -> ModelSpecTest.check(zeroJouleThomson, 0.01));
  }

  @Test
  void ammoniaReferencesSatisfyIndependentThermodynamicIdentities() throws IOException {
    int checked = 0;
    for (String fixture : new String[] {"system", "phase"}) {
      for (String state : new String[] {"gas-293-5", "gas-400-50", "liquid-293-10", "liquid-280-10"}) {
        String prefix = "ammonia-" + fixture + "-" + state + "-";
        ModelSpec density = find(prefix + "molar-density");
        ModelSpec internalEnergy = find(prefix + "internal-energy");
        ModelSpec enthalpy = find(prefix + "enthalpy");
        ModelSpec cv = find(prefix + "cv");
        ModelSpec cp = find(prefix + "cp");
        assertEquals(enthalpy.expected, internalEnergy.expected + density.pressure * 100.0 / density.expected, 1e-6,
            prefix + "H=U+PV");
        assertTrue(cp.expected > cv.expected && cv.expected > 0.0, prefix + "Cp>Cv>0");
        checked += 12;
      }
    }
    assertEquals(96, checked, "every CoolProp/Gao ammonia anchor must be covered");
  }

  @Test
  void ammoniaAnchorsRejectZeroNonfiniteAndPlausiblePlaceholders() throws IOException {
    for (String id : new String[] {"ammonia-system-gas-400-50-mass-density", "ammonia-system-gas-400-50-z",
        "ammonia-system-gas-400-50-enthalpy", "ammonia-system-gas-400-50-kappa"}) {
      ModelSpec reference = find(id);
      for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 1.05}) {
        assertThrows(AssertionError.class, () -> ModelSpecTest.check(reference, bad), id);
      }
      ModelSpecTest.check(reference, reference.expected);
    }
    ModelSpec signed = find("ammonia-system-liquid-293-10-jt");
    for (double bad : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, 0.0, -0.01, 1.05}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(signed, bad), signed.id);
    }
    ModelSpecTest.check(signed, signed.expected);
  }

  @Test
  void leachmanReferencesSatisfyIndependentThermodynamicIdentities() throws IOException {
    int checked = 0;
    for (String fixture : new String[] {"system", "phase"}) {
      for (String state : new String[] {"gas-300-10", "gas-100-50", "liquid-25-10", "liquid-20-5"}) {
        String prefix = "leachman-" + fixture + "-" + state + "-";
        ModelSpec molarMass = find(prefix + "molar-mass");
        ModelSpec molarDensity = find(prefix + "molar-density");
        ModelSpec massDensity = find(prefix + "mass-density");
        ModelSpec internalEnergy = find(prefix + "internal-energy");
        ModelSpec enthalpy = find(prefix + "enthalpy");
        ModelSpec entropy = find(prefix + "entropy");
        ModelSpec gibbsEnergy = find(prefix + "gibbs-energy");
        ModelSpec cv = find(prefix + "cv");
        ModelSpec cp = find(prefix + "cp");
        assertEquals(massDensity.expected, molarDensity.expected * molarMass.expected, 1e-12,
            prefix + "mass/molar density basis");
        assertEquals(enthalpy.expected, internalEnergy.expected + molarDensity.pressure * 100.0 / molarDensity.expected,
            1e-7, prefix + "H=U+PV");
        assertEquals(gibbsEnergy.expected, enthalpy.expected - enthalpy.temperature * entropy.expected, 1e-9,
            prefix + "G=H-TS");
        assertTrue(cp.expected > cv.expected && cv.expected > 0.0, prefix + "Cp>Cv>0");
        checked += 13;
      }
    }
    assertEquals(104, checked, "every CoolProp/Leachman normal-hydrogen anchor must be covered");
  }

  @Test
  void leachmanAnchorsRejectZeroNonfiniteAndPlausiblePlaceholders() throws IOException {
    for (String id : new String[] {"leachman-system-gas-300-10-molar-density", "leachman-system-gas-300-10-z",
        "leachman-system-liquid-25-10-cp", "leachman-system-liquid-20-5-kappa"}) {
      ModelSpec reference = find(id);
      for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 1.05}) {
        assertThrows(AssertionError.class, () -> ModelSpecTest.check(reference, bad), id);
      }
      ModelSpecTest.check(reference, reference.expected);
    }
    for (String id : new String[] {"leachman-system-gas-300-10-gibbs-energy",
        "leachman-system-liquid-20-5-internal-energy", "leachman-system-liquid-20-5-jt"}) {
      ModelSpec reference = find(id);
      for (double bad : new double[] {0.0, Double.NaN, Double.NEGATIVE_INFINITY, 1.0, 1.05}) {
        assertThrows(AssertionError.class, () -> ModelSpecTest.check(reference, bad), id);
      }
      ModelSpecTest.check(reference, reference.expected);
    }
  }

  @Test
  void vegaReferencesSatisfyIndependentThermodynamicIdentities() throws IOException {
    int checked = 0;
    for (String fixture : new String[] {"system", "phase"}) {
      for (String state : new String[] {"gas-300-10", "gas-250-25", "gas-150-50", "gas-100-50"}) {
        String prefix = "vega-" + fixture + "-" + state + "-";
        ModelSpec molarMass = find(prefix + "molar-mass");
        ModelSpec molarDensity = find(prefix + "molar-density");
        ModelSpec massDensity = find(prefix + "mass-density");
        ModelSpec internalEnergy = find(prefix + "internal-energy");
        ModelSpec enthalpy = find(prefix + "enthalpy");
        ModelSpec entropy = find(prefix + "entropy");
        ModelSpec gibbsEnergy = find(prefix + "gibbs-energy");
        ModelSpec cv = find(prefix + "cv");
        ModelSpec cp = find(prefix + "cp");
        assertEquals(massDensity.expected, molarDensity.expected * molarMass.expected, 1e-12,
            prefix + "mass/molar density basis");
        assertEquals(enthalpy.expected, internalEnergy.expected + molarDensity.pressure * 100.0 / molarDensity.expected,
            1e-7, prefix + "H=U+PV");
        assertEquals(gibbsEnergy.expected, enthalpy.expected - enthalpy.temperature * entropy.expected, 1e-9,
            prefix + "G=H-TS");
        assertTrue(cp.expected > cv.expected && cv.expected > 0.0, prefix + "Cp>Cv>0");
        checked += 13;
      }
    }
    assertEquals(104, checked, "every CoolProp/Vega helium anchor must be covered");
  }

  @Test
  void vegaAnchorsRejectZeroNonfiniteAndPlausiblePlaceholders() throws IOException {
    for (String id : new String[] {"vega-system-gas-300-10-molar-density", "vega-system-gas-300-10-z",
        "vega-system-gas-150-50-cp", "vega-system-gas-100-50-kappa"}) {
      ModelSpec reference = find(id);
      for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 1.05}) {
        assertThrows(AssertionError.class, () -> ModelSpecTest.check(reference, bad), id);
      }
      ModelSpecTest.check(reference, reference.expected);
    }
    for (String id : new String[] {"vega-system-gas-300-10-gibbs-energy", "vega-system-gas-100-50-jt"}) {
      ModelSpec reference = find(id);
      for (double bad : new double[] {0.0, Double.NaN, Double.NEGATIVE_INFINITY, 1.0, 1.05}) {
        assertThrows(AssertionError.class, () -> ModelSpecTest.check(reference, bad), id);
      }
      ModelSpecTest.check(reference, reference.expected);
    }
  }

  private static ModelSpec find(String id) throws IOException {
    for (ModelSpec spec : ModelSpec.load()) {
      if (id.equals(spec.id)) {
        return spec;
      }
    }
    throw new AssertionError("missing case " + id);
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

  @Test
  void originalUnifacReferencesSatisfyPublishedGroupContributionEquation() throws IOException {
    int checked = 0;
    for (ModelSpec spec : ModelSpec.load()) {
      if (!"published-original-unifac".equals(spec.operation)) {
        continue;
      }
      double[] reference = originalUnifac(spec.components.get("methanol"), spec.temperature);
      double expected = spec.property == ModelSpec.Property.GEX ? reference[4]
          : spec.property == ModelSpec.Property.LN_GAMMA ? reference[2 + spec.componentIndex]
              : reference[spec.componentIndex];
      assertEquals(expected, spec.expected, spec.property == ModelSpec.Property.GEX ? 1e-9 : 1e-12, spec.toString());
      checked++;
    }
    assertEquals(60, checked, "every published original UNIFAC anchor must be independently reconstructed");
  }

  @Test
  void unifacActivityAndStoredLogRejectDefectValues() throws IOException {
    ModelSpec gamma = null;
    ModelSpec log = null;
    for (ModelSpec spec : ModelSpec.load()) {
      if ("unifac-system-t298-x05-gamma-1".equals(spec.id)) {
        gamma = spec;
      }
      if ("unifac-system-t298-x05-ln-gamma-0".equals(spec.id)) {
        log = spec;
      }
    }
    assertTrue(gamma != null && log != null);
    final ModelSpec checkedGamma = gamma;
    final ModelSpec checkedLog = log;
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 1.0, 1.05}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(checkedGamma, bad));
    }
    for (double bad : new double[] {0.0, Double.NaN, Double.POSITIVE_INFINITY, 0.05}) {
      assertThrows(AssertionError.class, () -> ModelSpecTest.check(checkedLog, bad));
    }
    ModelSpecTest.check(checkedGamma, checkedGamma.expected);
    ModelSpecTest.check(checkedLog, checkedLog.expected);
  }

  @Test
  void excessGibbsEnergyUsesSignedRatherThanPositiveOnlyContract() throws IOException {
    String[] lines = catalog().split("\n");
    String selected = null;
    for (String line : lines) {
      if (line.contains("\tGEX\t")) {
        selected = line;
        break;
      }
    }
    assertTrue(selected != null);
    String[] cells = selected.split("\t", -1);
    cells[13] = "-5.0";
    cells[14] = "0";
    cells[15] = "0";
    ModelSpec signed = ModelSpec
        .parse(ModelSpec.VERSION + "\n" + ModelSpec.HEADER + "\n" + String.join("\t", cells) + "\n").get(0);
    ModelSpecTest.check(signed, -5.0);
  }

  private static boolean isCubic(ModelSpec.Fixture fixture) {
    return fixture == ModelSpec.Fixture.SRK || fixture == ModelSpec.Fixture.PR || fixture == ModelSpec.Fixture.SRK_PHASE
        || fixture == ModelSpec.Fixture.PR_PHASE;
  }

  private static boolean isNrtl(ModelSpec.Fixture fixture) {
    return fixture == ModelSpec.Fixture.NRTL_ANALYTIC || fixture == ModelSpec.Fixture.NRTL_PHASE;
  }

  static double[] originalUnifac(double methanolFraction, double temperature) {
    double[] x = {methanolFraction, 1.0 - methanolFraction};
    double[] r = {1.4311, 0.9200};
    double[] q = {1.4320, 1.4000};
    double[][] interaction = {{0.0, -180.95}, {289.6, 0.0}};
    double sumR = x[0] * r[0] + x[1] * r[1];
    double sumQ = x[0] * q[0] + x[1] * q[1];
    double[] l = new double[2];
    double sumL = 0.0;
    for (int i = 0; i < 2; i++) {
      l[i] = 5.0 * (r[i] - q[i]) - (r[i] - 1.0);
      sumL += x[i] * l[i];
    }
    double[] theta = {x[0] * q[0] / sumQ, x[1] * q[1] / sumQ};
    double[][] pureTheta = {{1.0, 0.0}, {0.0, 1.0}};
    double[] gamma = new double[2];
    double[] lnGamma = new double[2];
    for (int i = 0; i < 2; i++) {
      double volumeFraction = x[i] * r[i] / sumR;
      double areaFraction = x[i] * q[i] / sumQ;
      double combinatorial = Math.log(volumeFraction / x[i]) + 5.0 * q[i] * Math.log(areaFraction / volumeFraction)
          + l[i] - volumeFraction / x[i] * sumL;
      double residual = unifacGroupLogActivity(theta, i, temperature, q, interaction)
          - unifacGroupLogActivity(pureTheta[i], i, temperature, q, interaction);
      lnGamma[i] = combinatorial + residual;
      gamma[i] = Math.exp(lnGamma[i]);
    }
    double excess = 8.314462618 * temperature * (x[0] * lnGamma[0] + x[1] * lnGamma[1]);
    return new double[] {gamma[0], gamma[1], lnGamma[0], lnGamma[1], excess};
  }

  private static double unifacGroupLogActivity(double[] theta, int group, double temperature, double[] q,
      double[][] interaction) {
    double first = 0.0;
    for (int m = 0; m < 2; m++) {
      first += theta[m] * Math.exp(-interaction[m][group] / temperature);
    }
    double third = 0.0;
    for (int m = 0; m < 2; m++) {
      double denominator = 0.0;
      for (int n = 0; n < 2; n++) {
        denominator += theta[n] * Math.exp(-interaction[n][m] / temperature);
      }
      third += theta[m] * Math.exp(-interaction[group][m] / temperature) / denominator;
    }
    return q[group] * (1.0 - Math.log(first) - third);
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
