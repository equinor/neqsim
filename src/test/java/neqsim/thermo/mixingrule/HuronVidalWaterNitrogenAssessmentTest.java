package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Assesses staged PR-HV nitrogen/water coefficients separately from numerical acceptance.
 *
 * <p>
 * The complete tuple is from Yushchenko and Brusilovsky, FPE 409 (2016), Table 1, DOI 10.1016/j.fluid.2015.09.029, with
 * water first. The test retains current library pure properties, whose exact equivalence to the fitted source inputs is
 * unverified. It therefore reports diagnostic errors and never asserts experimental accuracy. No library default, alpha
 * model or database value is changed.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HuronVidalWaterNitrogenAssessmentTest extends neqsim.NeqSimTest {
  /**
   * Preserves all observations and pointwise errors for defaults and the explicitly staged PR tuple.
   *
   * @throws IOException if an input or diagnostic report cannot be read or written
   */
  @Test
  void assessesBothSolubilityDirectionsWithoutAnAccuracyPassThreshold() throws IOException {
    Path directory = Paths.get("target", "hv-water-nitrogen");
    Files.createDirectories(directory);
    for (String name : new String[] {"water-in-nitrogen", "nitrogen-in-water"}) {
      boolean gas = name.startsWith("water");
      try (
          BufferedReader in = new BufferedReader(new InputStreamReader(
              getClass().getResourceAsStream("/neqsim/thermo/mixingrule/hv-water-nitrogen/" + name + ".csv"),
              StandardCharsets.UTF_8));
          PrintWriter out = new PrintWriter(
              Files.newBufferedWriter(directory.resolve(name + ".csv"), StandardCharsets.UTF_8))) {
        out.println(
            "model,T_K,P_kPa,observed,compiler_U95,predicted,relative_error,error_over_U95,in_fit_emphasis,water_Tc_K,water_Pc_bara,water_omega,nitrogen_Tc_K,nitrogen_Pc_bara,nitrogen_omega");
        in.readLine();
        String line;
        int points = 0;
        while ((line = in.readLine()) != null) {
          String[] columns = line.split(",");
          double t = Double.parseDouble(columns[0]);
          double p = Double.parseDouble(columns[1]);
          double observed = Double.parseDouble(columns[2]);
          double uncertainty = Double.parseDouble(columns[3]);
          for (int model = 0; model < 3; model++) {
            SystemInterface fluid = fluid(t, p / 100.0, model, false);
            flash(fluid);
            PhaseInterface phase = fluid.getPhase(gas ? PhaseType.GAS : PhaseType.AQUEOUS);
            double predicted = phase.getComponent(gas ? "water" : "nitrogen").getx();
            out.printf(Locale.ROOT,
                "%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%b,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g%n", model, t, p,
                observed, uncertainty, predicted, predicted / observed - 1.0, (predicted - observed) / uncertainty,
                t >= 323.15 && t <= 423.15 && p >= 2500.0 && p <= 70000.0,
                fluid.getPhase(0).getComponent("water").getTC(), fluid.getPhase(0).getComponent("water").getPC(),
                fluid.getPhase(0).getComponent("water").getAcentricFactor(),
                fluid.getPhase(0).getComponent("nitrogen").getTC(), fluid.getPhase(0).getComponent("nitrogen").getPC(),
                fluid.getPhase(0).getComponent("nitrogen").getAcentricFactor());
          }
          points++;
        }
        assertEquals(gas ? 35 : 52, points, "All measured rows retained");
        assertTrue(!out.checkError(), "Diagnostic report written");
      }
    }
  }

  /** Checks native identities, refined differences, component order and initialization history. */
  @Test
  void stagedTupleHasNativeConsistencyAcrossStateHistoryAndOrder() {
    for (int model = 0; model < 3; model++) {
      for (boolean reverse : new boolean[] {false, true}) {
        SystemInterface repeated = fluid(353.15, 50.0, model, reverse);
        for (double t : new double[] {353.15, 353.15, 363.02, 353.15}) {
          repeated.setTemperature(t);
          flash(repeated);
          nativeChecks(repeated);
          SystemInterface fresh = fluid(t, 50.0, model, !reverse);
          flash(fresh);
          for (PhaseType type : new PhaseType[] {PhaseType.GAS, PhaseType.AQUEOUS}) {
            for (String component : new String[] {"water", "nitrogen"}) {
              assertEquals(fresh.getPhase(type).getComponent(component).getx(),
                  repeated.getPhase(type).getComponent(component).getx(), 1e-10, "Fresh/repeated/order parity");
            }
          }
        }
      }
    }

  }

  /** Checks independently constructed fixed-composition liquid temperature differences. */
  @Test
  void resolvesIndependentTemperatureDifferences() {
    for (int model = 0; model < 3; model++) {
      for (double t : new double[] {353.15, 363.02}) {
        SystemInterface fluid = fluid(t, 50.0, model, false);
        flash(fluid);
        PhaseInterface phase = fluid.getPhase(PhaseType.AQUEOUS);
        for (String name : new String[] {"water", "nitrogen"}) {
          double coarse = temperatureDifference(phase, name, model, 0.001);
          double fine = temperatureDifference(phase, name, model, 0.0005);
          assertEquals(coarse, fine, 1e-8, "Step-halving resolution, inverse kelvin");
          assertEquals(phase.getComponent(name).getdfugdt(), fine, 1e-7, "Independent liquid T derivative");
        }
      }
    }
  }

  /**
   * Evaluates a central difference from two fresh fixed-composition liquid systems.
   *
   * @param source equilibrium liquid whose composition is held fixed
   * @param name differentiated component
   * @param model selected test-local model
   * @param h temperature step in kelvin
   * @return derivative of log fugacity coefficient per kelvin
   */
  private static double temperatureDifference(PhaseInterface source, String name, int model, double h) {
    double[] value = new double[2];
    for (int side = 0; side < 2; side++) {
      SystemInterface trial = fluid(source.getTemperature() + (side == 0 ? -h : h), source.getPressure(), model, false);
      trial.setMolarComposition(
          new double[] {source.getComponent("water").getx(), source.getComponent("nitrogen").getx()});
      trial.setForceSinglePhase(PhaseType.LIQUID);
      trial.init(3);
      value[side] = trial.getPhase(0).getComponent(name).getLogFugacityCoefficient();
    }
    return (value[1] - value[0]) / (2.0 * h);
  }

  /**
   * Creates an independently configured equal-mole synthetic binary feed.
   *
   * @param t temperature in kelvin
   * @param p absolute pressure in bara
   * @param model 0 for default SRK, 1 for default PR, 2 for staged PR
   * @param reverse whether nitrogen is added before water
   * @return configured synthetic feed; no measured feed is inferred
   */
  static SystemInterface fluid(double t, double p, int model, boolean reverse) {
    SystemInterface fluid = model == 0 ? new SystemSrkEos(t, p) : new SystemPrEos(t, p);
    fluid.addComponent(reverse ? "nitrogen" : "water", 0.5);
    fluid.addComponent(reverse ? "water" : "nitrogen", 0.5);
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    if (model == 2) {
      int water = reverse ? 1 : 0;
      int nitrogen = 1 - water;
      for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
        HVMixingRulesInterface rule = (HVMixingRulesInterface) fluid.getPhase(phase).getMixingRule();
        rule.setClassicOrHV(water, nitrogen, "HV");
        rule.setClassicOrHV(nitrogen, water, "HV");
        rule.setHValphaParameter(water, nitrogen, 0.08);
        rule.setHVDijParameter(water, nitrogen, -64.5);
        rule.setHVDijTParameter(water, nitrogen, -1.05);
        rule.setHVDijParameter(nitrogen, water, 3500.0);
        rule.setHVDijTParameter(nitrogen, water, -1.0);
      }
    }
    return fluid;
  }

  /**
   * Checks phase topology, normalization, balances and log-fugacity equality after a flash.
   *
   * @param fluid configured binary system
   */
  private static void flash(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.GAS) && fluid.hasPhaseType(PhaseType.AQUEOUS));
    double beta = 0.0;
    for (int phase = 0; phase < 2; phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      assertTrue(fluid.getBeta(phase) > 0.0 && fluid.getBeta(phase) < 1.0);
      beta += fluid.getBeta(phase);
      assertEquals(1.0, state.getComponent(0).getx() + state.getComponent(1).getx(), 1e-12);
    }
    assertEquals(1.0, beta, 1e-12);
    for (int component = 0; component < 2; component++) {
      double recovered = 0.0;
      double[] fugacity = new double[2];
      for (int phase = 0; phase < 2; phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        double x = state.getComponent(component).getx();
        recovered += fluid.getBeta(phase) * x;
        fugacity[phase] = Math.log(x) + state.getComponent(component).getLogFugacityCoefficient();
      }
      assertEquals(0.5, recovered, 1e-10);
      assertEquals(fugacity[0], fugacity[1], 1e-8);
    }
  }

  /**
   * Asserts native identities and refined numerical derivatives without mutating the source state.
   *
   * @param fluid initialized equilibrium system
   */
  private static void nativeChecks(SystemInterface fluid) {
    ThermodynamicModelTest test = new ThermodynamicModelTest(fluid);
    test.setMaxError(1e-8);
    assertTrue(test.checkFugacityCoefficients());
    assertTrue(test.checkFugacityCoefficientsDn());
    assertTrue(test.checkFugacityCoefficientsDn2());
    assertTrue(test.checkFugacityCoefficientsDP());
    assertTrue(test.checkFugacityCoefficientsDT());
    test.setMaxError(1e-6);
    byte[] before = SerializationUtils.serialize(fluid);
    assertTrue(test.checkNumerically());
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
  }
}
