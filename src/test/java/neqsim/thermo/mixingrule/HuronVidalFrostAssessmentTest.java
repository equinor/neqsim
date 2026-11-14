package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Compares staged methane binary flashes with independent equations and retains both measured endpoint errors.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalFrostAssessmentTest extends neqsim.NeqSimTest {
  /**
   * Assesses both source versions, both MEG alpha controls, and every available measured endpoint.
   *
   * @throws Exception if a fixture or report cannot be read or written
   */
  @Test
  void stagedBinaryFlashesMatchIndependentEndpoints() throws Exception {
    Path output = Paths.get("target", "hv-frost-native.csv");
    Files.createDirectories(output.getParent());
    try (
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            getClass().getResourceAsStream("/neqsim/thermo/mixingrule/hv-frost/observations-and-reference.csv"),
            StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      writer.println(header + ",native_x_CH4,native_y_polar,max_log_fugacity,max_balance,max_normalization,"
          + "relative_x_error,relative_y_error");
      int rows = 0;
      String line;
      while ((line = reader.readLine()) != null) {
        String[] row = line.split(",", -1);
        assertEquals(9, row.length);
        double t = Double.parseDouble(row[1]);
        double p = Double.parseDouble(row[2]);
        String polar = row[0].startsWith("water") ? "water" : "MEG";
        SystemInterface fluid = fluid(row[0], t, p, false);
        new ThermodynamicOperations(fluid).TPflash();
        fluid.init(3);
        assertEquals(2, fluid.getNumberOfPhases(), line);
        assertTrue(fluid.hasPhaseType(PhaseType.GAS), line);
        PhaseInterface gas = fluid.getPhase(PhaseType.GAS);
        PhaseInterface liquid = fluid.getPhase(0) == gas ? fluid.getPhase(1) : fluid.getPhase(0);
        double x = liquid.getComponent("methane").getx();
        double y = gas.getComponent(polar).getx();
        for (int column = 7; column < 9; column++) {
          double expected = Double.parseDouble(row[column]);
          assertEquals(expected, column == 7 ? x : y, Math.max(1e-13, expected * 5e-8), line);
        }
        double[] residuals = closure(fluid, polar);
        // Sample each distinct profile with the same checker limits as the earlier binary fixtures.
        if (rows == 0 || rows == 22 || rows == 45 || rows == 56) {
          checkIdentities(fluid);
        }
        writer.printf(Locale.ROOT, "%s,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s%n", line, x, y, residuals[0],
            residuals[1], residuals[2], x / Double.parseDouble(row[3]) - 1.0,
            row[4].isEmpty() ? "" : Double.toString(y / Double.parseDouble(row[4]) - 1.0));
        rows++;
      }
      assertEquals(67, rows);
      assertTrue(!writer.checkError());
    }
  }

  /** Checks order and repeatability as each configured binary visits nearby states and returns. */
  @Test
  void returnsToFreshStateAfterTemperatureAndPressureChanges() {
    for (String profile : new String[] {"water_journal", "meg_mc"}) {
      String polar = "meg_mc".equals(profile) ? "MEG" : "water";
      SystemInterface retained = fluid(profile, 323.56, 84.8, true);
      for (double[] coordinate : new double[][] {{323.56, 84.8}, {323.56, 84.8}, {323.76, 85.8}, {323.56, 84.8}}) {
        retained.setTemperature(coordinate[0]);
        retained.setPressure(coordinate[1]);
        SystemInterface fresh = fluid(profile, coordinate[0], coordinate[1], false);
        for (SystemInterface state : new SystemInterface[] {retained, fresh}) {
          new ThermodynamicOperations(state).TPflash();
          state.init(3);
          closure(state, polar);
        }
        for (String name : new String[] {polar, "methane"}) {
          double expected = fresh.getPhase(PhaseType.GAS).getComponent(name).getx();
          assertEquals(expected, retained.getPhase(PhaseType.GAS).getComponent(name).getx(),
              Math.max(1e-13, expected * 5e-8));
        }
      }
    }
  }

  /**
   * Builds a fresh binary diagnostic with an equal-mole synthetic feed and a test-local tuple.
   *
   * @param profile staged profile identifier
   * @param t temperature in kelvin
   * @param p absolute pressure in bar
   * @param reverse whether methane is added first
   * @return configured fluid
   */
  static SystemInterface fluid(String profile, double t, double p, boolean reverse) {
    if ("meg_mc".equals(profile) || "meg_soave".equals(profile)) {
      return HuronVidalMegMethaneDiagnosticTest.fluid(t, p, 0.5, 0.5, reverse, "meg_mc".equals(profile));
    }
    if (!"water_journal".equals(profile) && !"water_pr".equals(profile)) {
      throw new IllegalArgumentException("Unknown Frost profile " + profile);
    }
    SystemInterface fluid = new SystemPrEos(t, p);
    fluid.addComponent(reverse ? "methane" : "water", 0.5);
    fluid.addComponent(reverse ? "water" : "methane", 0.5);
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      int water = state.getComponent("water").getComponentNumber();
      int methane = state.getComponent("methane").getComponentNumber();
      HVMixingRulesInterface rule = (HVMixingRulesInterface) state.getMixingRule();
      rule.setClassicOrHV(water, methane, "HV");
      rule.setClassicOrHV(methane, water, "HV");
      rule.setHValphaParameter(water, methane, 0.16);
      rule.setHValphaParameter(methane, water, 0.16);
      rule.setHVDijParameter(water, methane, -1500.0);
      rule.setHVDijParameter(methane, water, 4050.0);
      rule.setHVDijTParameter(water, methane, 11.0);
      rule.setHVDijTParameter(methane, water, -5.2);
    }
    return fluid;
  }

  /**
   * Checks component closure, normalized phase amounts and compositions, and equal log fugacity.
   *
   * @param fluid initialized two-phase binary
   * @param polar polar component name
   * @return maximum log fugacity, balance and normalization residuals
   */
  static double[] closure(SystemInterface fluid, String polar) {
    assertEquals(2, fluid.getNumberOfPhases());
    double normalization = Math.abs(1.0 - fluid.getBeta(0) - fluid.getBeta(1));
    double balance = 0.0;
    double fugacity = 0.0;
    for (String name : new String[] {"methane", polar}) {
      PhaseInterface first = fluid.getPhase(0);
      PhaseInterface second = fluid.getPhase(1);
      double x = first.getComponent(name).getx();
      double y = second.getComponent(name).getx();
      assertTrue(x > 0.0 && y > 0.0);
      balance = Math.max(balance, Math.abs(0.5 - fluid.getBeta(0) * x - fluid.getBeta(1) * y));
      fugacity = Math.max(fugacity, Math.abs(Math.log(x / y) + first.getComponent(name).getLogFugacityCoefficient()
          - second.getComponent(name).getLogFugacityCoefficient()));
    }
    for (int phase = 0; phase < 2; phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      normalization = Math.max(normalization,
          Math.abs(1.0 - state.getComponent("methane").getx() - state.getComponent(polar).getx()));
    }
    assertTrue(balance <= 1e-10, "Component balance " + balance);
    assertTrue(normalization <= 1e-12, "Normalization " + normalization);
    assertTrue(fugacity <= 1e-8, "Log-fugacity residual " + fugacity);
    return new double[] {fugacity, balance, normalization};
  }

  /**
   * Asserts the existing identities and the refined numerical checker on a one-mole fluid.
   *
   * @param fluid initialized one-mole fluid
   */
  private static void checkIdentities(SystemInterface fluid) {
    ThermodynamicModelTest checker = new ThermodynamicModelTest(
        org.apache.commons.lang3.SerializationUtils.clone(fluid));
    checker.setMaxError(1e-8);
    assertTrue(checker.checkFugacityCoefficients());
    assertTrue(checker.checkFugacityCoefficientsDn());
    assertTrue(checker.checkFugacityCoefficientsDn2());
    assertTrue(checker.checkFugacityCoefficientsDP());
    assertTrue(checker.checkFugacityCoefficientsDT());
    checker.setMaxError(1e-6);
    assertTrue(checker.checkNumerically());
  }
}
