package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Assesses unchanged SRK/PR-HV against public MEG data without treating numerical closure as accuracy.
 *
 * <p>
 * Experimental errors are written to target/hv-meg-assessment. No fitted parameter or empirical accuracy tolerance is
 * introduced. The numerical stencil accuracy is the existing HV checker setting.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalMegExperimentalAssessmentTest extends neqsim.NeqSimTest {
  /** Classpath directory for the source-transcribed measurements. */
  private static final String DATA = "/neqsim/thermo/mixingrule/hv-meg-qualification/";
  /** Existing numerical-checker accuracy, separate from analytical identity and equilibrium gates. */
  private static final double NUMERICAL_ACCURACY = 1e-6;

  /**
   * Assesses measured feeds and checks repeated, changed, restored and component-permuted states.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   * @throws Exception if input, equilibrium, diagnostics or report output fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void measuredFeedsRetainNumericalConsistencyAndReportAccuracy(boolean pr) throws Exception {
    List<String[]> rows = read("kruger-2018.csv");
    assertEquals(16, rows.size());
    int megObservations = 0;
    try (PrintWriter out = report(pr, "mixture")) {
      out.println("id,T_K,P_bara,raw_feed_sum,yMEG,yWater,xMethane,measured_yMEG,measured_yWater,"
          + "measured_xMethane,U3_yMEG,U3_yWater,U3_xMethane,relative_yMEG,relative_yWater,"
          + "relative_xMethane,numerically_qualified");
      for (String[] row : rows) {
        double temperature = number(row[1]);
        double pressure = 10 * number(row[2]);
        double feedSum = number(row[3]) + number(row[4]) + number(row[5]);
        SystemInterface fluid = mixture(pr, row, false);
        ThermodynamicOperations operations = new ThermodynamicOperations(fluid);
        operations.TPflash();
        double[] fresh = checkMixture(fluid);
        double observedMeg = optionalPpm(row[8]);
        double observedWater = number(row[9]) * 1e-6;
        double observedMethane = number(row[7]);
        double megUncertainty = optionalPpm(row[12]);
        assertEquals(Double.isNaN(observedMeg), Double.isNaN(megUncertainty));
        if (!Double.isNaN(observedMeg)) {
          megObservations++;
        }
        out.printf(Locale.ROOT,
            "%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g," + "%.17g,%.17g,%.17g,%.17g,true%n",
            row[0], temperature, pressure, feedSum, fresh[0], fresh[1], fresh[2], observedMeg, observedWater,
            observedMethane, megUncertainty, number(row[13]) * 1e-6, number(row[11]) * 1e-4, fresh[0] / observedMeg - 1,
            fresh[1] / observedWater - 1, fresh[2] / observedMethane - 1);

        operations.TPflash();
        assertSameObservables(fresh, checkMixture(fluid));
        fluid.setTemperature(temperature + 0.2);
        fluid.setPressure(pressure * 1.01);
        operations.TPflash();
        checkMixture(fluid);
        fluid.setTemperature(temperature);
        fluid.setPressure(pressure);
        operations.TPflash();
        assertSameObservables(fresh, checkMixture(fluid));

        SystemInterface reversed = mixture(pr, row, true);
        new ThermodynamicOperations(reversed).TPflash();
        assertSameObservables(fresh, checkMixture(reversed));
      }
    }
    assertEquals(10, megObservations, "Six unpublished high-temperature observations remain missing");
  }

  /**
   * Reports pure-MEG pressure errors while requiring distinct stable roots and equation closure.
   *
   * @param pr whether to use Peng-Robinson instead of SRK
   * @throws Exception if source data, a saturation calculation or output fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pureMegPressureAssessmentKeepsExperimentalAndNumericalGatesSeparate(boolean pr) throws Exception {
    List<String[]> rows = read("pure-meg-2009.csv");
    assertEquals(15, rows.size());
    try (PrintWriter out = report(pr, "pure")) {
      out.println("T_K,P_kPa,measured_P_kPa,compiler_U95_kPa,relative_error,inside_compiler_U95,"
          + "Tc_K,Pc_bara,omega,numerically_qualified");
      for (String[] row : rows) {
        double temperature = number(row[0]);
        SystemInterface fluid = system(pr, temperature, 1.0);
        fluid.addComponent("MEG", 1.0);
        fluid.setMixingRule(4);
        new ThermodynamicOperations(fluid).bubblePointPressureFlash();
        fluid.init(3);
        double pressure = fluid.getPressure();
        double z0 = fluid.getPhase(0).getZ();
        double z1 = fluid.getPhase(1).getZ();
        assertTrue(Math.abs(z0 - z1) > 1e-7 * Math.max(z0, z1));
        for (int phase = 0; phase < 2; phase++) {
          PhaseEos state = (PhaseEos) fluid.getPhase(phase);
          assertEquals(pressure, state.calcPressure(), 1e-8);
          assertTrue(state.getdPdVTn() < 0.0, "Both saturation roots must be mechanically stable");
        }
        assertEquals(0.0, Math.log(fluid.getPhase(0).getComponent(0).getFugacityCoefficient()
            / fluid.getPhase(1).getComponent(0).getFugacityCoefficient()), 1e-8);
        double measured = number(row[1]);
        double uncertainty = number(row[2]);
        out.printf(Locale.ROOT, "%.17g,%.17g,%.17g,%.17g,%.17g,%b,%.17g,%.17g,%.17g,true%n", temperature,
            pressure * 100, measured, uncertainty, pressure * 100 / measured - 1,
            Math.abs(pressure * 100 - measured) <= uncertainty, fluid.getComponent(0).getTC(),
            fluid.getComponent(0).getPC(), fluid.getComponent(0).getAcentricFactor());
      }
    }
  }

  /**
   * Creates a fresh fluid with explicit feed normalization and optional reversed component order.
   *
   * @param pr whether to use PR
   * @param row raw source row
   * @param reverse whether to add methane, water, MEG instead of MEG, water, methane
   * @return configured unflashed independent fluid
   */
  private static SystemInterface mixture(boolean pr, String[] row, boolean reverse) {
    SystemInterface fluid = system(pr, number(row[1]), number(row[2]) * 10);
    String[] names = {"MEG", "water", "methane"};
    double sum = number(row[3]) + number(row[4]) + number(row[5]);
    assertTrue(Math.abs(sum - 1) <= 0.001000000001, "Retain the published feed rounding");
    for (int position = 0; position < 3; position++) {
      int component = reverse ? 2 - position : position;
      fluid.addComponent(names[component], number(row[3 + component]) / sum);
    }
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    return fluid;
  }

  /**
   * Checks all active phase identities, numerical derivatives, normalization, balances and fugacities.
   *
   * @param fluid flashed fluid
   * @return vapor MEG, vapor water and liquid methane mole fractions
   */
  private static double[] checkMixture(SystemInterface fluid) {
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    double betaSum = 0;
    PhaseInterface liquid = null;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      betaSum += fluid.getBeta(phase);
      PhaseInterface state = fluid.getPhase(phase);
      if (state.getType() != PhaseType.GAS) {
        liquid = state;
      }
      double sum = 0;
      for (int i = 0; i < 3; i++) {
        sum += state.getComponent(i).getx();
      }
      assertEquals(1, sum, 1e-12);
    }
    assertEquals(1, betaSum, 1e-12);
    for (int i = 0; i < 3; i++) {
      double inventory = 0;
      double reference = Math.log(fluid.getPhase(0).getComponent(i).getx())
          + Math.log(fluid.getPhase(0).getComponent(i).getFugacityCoefficient());
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        inventory += fluid.getBeta(phase) * state.getComponent(i).getx();
        assertEquals(reference,
            Math.log(state.getComponent(i).getx()) + Math.log(state.getComponent(i).getFugacityCoefficient()), 1e-8);
      }
      assertEquals(fluid.getComponent(i).getz(), inventory, 1e-10);
    }
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    assertTrue(checker.checkFugacityCoefficients(), "Residual Gibbs identity");
    assertTrue(checker.checkFugacityCoefficientsDn(), "Composition Gibbs-Duhem");
    assertTrue(checker.checkFugacityCoefficientsDn2(), "Composition Hessian symmetry");
    assertTrue(checker.checkFugacityCoefficientsDP(), "Pressure derivative identity");
    assertTrue(checker.checkFugacityCoefficientsDT(), "Temperature derivative identity");
    checker.setMaxError(NUMERICAL_ACCURACY);
    assertTrue(checker.checkNumerically(), "Step-converged numerical derivatives");
    PhaseInterface gas = fluid.getPhase(PhaseType.GAS);
    assertTrue(liquid != null);
    return new double[] {gas.getComponent("MEG").getx(), gas.getComponent("water").getx(),
        liquid.getComponent("methane").getx()};
  }

  /**
   * Checks repeat/order invariance independently of any experimental-accuracy target.
   *
   * @param expected original observables
   * @param actual repeated or reordered observables
   */
  private static void assertSameObservables(double[] expected, double[] actual) {
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], actual[i], Math.max(1e-13, Math.abs(expected[i]) * 1e-8));
    }
  }

  /**
   * Creates an empty ordinary cubic system.
   *
   * @param pr whether to use PR
   * @param temperature temperature in kelvin
   * @param pressure absolute pressure in bara
   * @return empty system
   */
  private static SystemInterface system(boolean pr, double temperature, double pressure) {
    return pr ? new SystemPrEos(temperature, pressure) : new SystemSrkEos(temperature, pressure);
  }

  /**
   * Reads a small source-transcribed ASCII CSV without changing source values.
   *
   * @param name resource filename
   * @return data rows without the header
   * @throws IOException if the resource cannot be read
   */
  private static List<String[]> read(String name) throws IOException {
    InputStream stream = HuronVidalMegExperimentalAssessmentTest.class.getResourceAsStream(DATA + name);
    assertTrue(stream != null, "Missing measured-data resource");
    List<String[]> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.trim().isEmpty()) {
          rows.add(line.split(",", -1));
        }
      }
    }
    return rows;
  }

  /**
   * Opens a deterministic per-EOS assessment report under the Maven target directory.
   *
   * @param pr whether the report is for PR
   * @param kind mixture or pure assessment
   * @return report writer
   * @throws IOException if report creation fails
   */
  private static PrintWriter report(boolean pr, String kind) throws IOException {
    Path directory = Paths.get("target", "hv-meg-assessment");
    Files.createDirectories(directory);
    return new PrintWriter(
        Files.newBufferedWriter(directory.resolve((pr ? "PR" : "SRK") + "-" + kind + ".csv"), StandardCharsets.UTF_8));
  }

  /**
   * Parses a literal numeric source field.
   *
   * @param value source decimal
   * @return parsed value
   */
  private static double number(String value) {
    return Double.parseDouble(value);
  }

  /**
   * Preserves unpublished vapor-MEG data as NaN rather than zero or an invented measurement.
   *
   * @param value source ppm value or missing marker
   * @return mole fraction or NaN
   */
  private static double optionalPpm(String value) {
    return "-".equals(value) ? Double.NaN : number(value) * 1e-6;
  }
}
