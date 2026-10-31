package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentPR;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Native numerical diagnostics for declared CO2/water source configurations. Physical accuracy and original-author pure
 * inputs remain unqualified.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalCo2SourceDiagnosticTest {
  /**
   * Checks declared pure-CO2 inputs against independently solved stable cubic roots.
   *
   * @throws Exception if reference input, native saturation or report writing fails
   */
  @Test
  void declaredPureCo2PressuresMatchIndependentRoots() throws Exception {
    List<String> input = Files.readAllLines(Paths.get("src", "test", "resources", "neqsim", "thermo", "mixingrule",
        "hv-co2-source", "pure-co2-reference.csv"), StandardCharsets.UTF_8);
    List<String> report = new ArrayList<String>();
    report.add(
        "profile,T_K,rule,start_bara,history,native_bara,independent_bara,reference_bara,max_EOS_pressure_error_bara,log_fugacity_error");
    for (int line = 1; line < input.size(); line++) {
      String[] row = input.get(line).split(",");
      int profile = Integer.parseInt(row[0]);
      int nextLine = (line - 1) % 8 == 7 ? line - 7 : line + 1;
      String[] nearby = input.get(nextLine).split(",");
      for (int rule : new int[] {2, 4}) {
        for (double start : new double[] {.1, 100.0}) {
          SystemInterface fluid = profile == 1 ? new SystemSrkEos(Double.parseDouble(row[1]), start)
              : new SystemPrEos(Double.parseDouble(row[1]), start);
          fluid.addComponent("CO2", 1.0);
          fluid.setMixingRule(rule);
          if (profile != 0) {
            for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
              pure(fluid.getPhase(phase).getComponent(0), 304.1282, 73.773,
                  profile == 1 ? new double[] {.046115, .91388, 6.8838} : new double[] {.022762, .92848, 7.7217});
            }
          }
          for (int history = 0; history < 4; history++) {
            String[] expectedRow = history == 2 ? nearby : row;
            double expected = Double.parseDouble(expectedRow[2]);
            fluid.setTemperature(Double.parseDouble(expectedRow[1]));
            new ThermodynamicOperations(fluid).bubblePointPressureFlash();
            fluid.init(3);
            assertEquals(expected, fluid.getPressure(), expected * 5e-8,
                "Independent cubic root parity, not experimental accuracy");
            assertEquals(2, fluid.getNumberOfPhases());
            double maxPressureError = 0.0;
            for (int phase = 0; phase < 2; phase++) {
              neqsim.thermo.phase.PhaseEos state = (neqsim.thermo.phase.PhaseEos) fluid.getPhase(phase);
              assertTrue(state.calcPressuredV() < 0, "Mechanically stable coexistence roots");
              assertEquals(fluid.getPressure(), state.calcPressure(), 5e-7,
                  "Existing pure-saturation absolute EOS pressure contract, bara");
              assertEquals(1.0, state.getComponent(0).getx(), 1e-12);
              maxPressureError = Math.max(maxPressureError, Math.abs(state.calcPressure() - fluid.getPressure()));
            }
            assertTrue(Math.abs(fluid.getPhase(0).getMolarVolume() - fluid.getPhase(1).getMolarVolume()) > 1e-5
                * Math.max(fluid.getPhase(0).getMolarVolume(), fluid.getPhase(1).getMolarVolume()));
            assertEquals(fluid.getPhase(0).getComponent(0).getLogFugacityCoefficient(),
                fluid.getPhase(1).getComponent(0).getLogFugacityCoefficient(), 1e-8);
            // Saturation fixes pressure, not the pure-fluid phase amount. Balanced amounts resolve dn differences.
            fluid.setBeta(.5);
            fluid.init(3);
            assertPureCoexistenceNative(fluid);
            report.add(profile + "," + expectedRow[1] + "," + rule + "," + start + "," + history + ","
                + fluid.getPressure() + "," + expected + "," + expectedRow[3] + "," + maxPressureError + ","
                + Math.abs(fluid.getPhase(0).getComponent(0).getLogFugacityCoefficient()
                    - fluid.getPhase(1).getComponent(0).getLogFugacityCoefficient()));
          }
        }
      }
    }
    assertEquals(385, report.size());
    Files.createDirectories(Paths.get("target", "hv-co2-native"));
    Files.write(Paths.get("target", "hv-co2-native", "pure-co2.csv"), report, StandardCharsets.UTF_8);
  }

  /**
   * Asserts native identities and detached numerical differences at pure coexistence.
   *
   * @param fluid initialized pure fluid with balanced phase amounts
   */
  private static void assertPureCoexistenceNative(SystemInterface fluid) {
    assertEquals(1.0, fluid.getBeta(0) + fluid.getBeta(1), 1e-12);
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(1e-8);
    assertTrue(checker.checkFugacityCoefficients());
    assertTrue(checker.checkFugacityCoefficientsDn());
    assertTrue(checker.checkFugacityCoefficientsDn2());
    assertTrue(checker.checkFugacityCoefficientsDP());
    assertTrue(checker.checkFugacityCoefficientsDT());
    checker.setMaxError(1e-6);
    byte[] before = SerializationUtils.serialize(fluid);
    assertTrue(checker.checkNumerically());
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
  }

  /**
   * Reconciles declared Twu pure-water inputs with independent stable-root and IAPWS diagnostics.
   *
   * @throws Exception if input, saturation calculation or report writing fails
   */
  @Test
  void declaredPureWaterPressuresMatchIndependentRoots() throws Exception {
    List<String> input = Files.readAllLines(Paths.get("src", "test", "resources", "neqsim", "thermo", "mixingrule",
        "hv-co2-source", "pure-water-reference.csv"), StandardCharsets.UTF_8);
    List<String> report = new ArrayList<String>();
    report.add("EOS,T_K,rule,start_bara,native_bara,independent_bara,IAPWS_bara,relative_IAPWS_error");
    for (int line = 1; line < input.size(); line++) {
      String[] row = input.get(line).split(",");
      boolean srk = row[0].equals("SRK");
      double t = Double.parseDouble(row[1]);
      double expected = Double.parseDouble(row[2]);
      double reference = Double.parseDouble(row[3]);
      for (int rule : new int[] {2, 4}) {
        for (double start : new double[] {.1, 100.0}) {
          SystemInterface fluid = srk ? new SystemSrkEos(t, start) : new SystemPrEos(t, start);
          fluid.addComponent("water", 1.0);
          fluid.setMixingRule(rule);
          for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
            pure(fluid.getPhase(phase).getComponent(0), 647.096, 220.64,
                srk ? new double[] {.36117, .87308, 2.4389} : new double[] {.40274, .87000, 1.8869});
          }
          for (int repeat = 0; repeat < 2; repeat++) {
            new ThermodynamicOperations(fluid).bubblePointPressureFlash();
            fluid.init(3);
            assertEquals(expected, fluid.getPressure(), expected * 5e-8,
                "Independent cubic root parity, not experimental accuracy");
            assertEquals(2, fluid.getNumberOfPhases());
            for (int phase = 0; phase < 2; phase++) {
              neqsim.thermo.phase.PhaseEos state = (neqsim.thermo.phase.PhaseEos) fluid.getPhase(phase);
              assertTrue(state.calcPressuredV() < 0, "Mechanically stable coexistence roots");
              assertEquals(fluid.getPressure(), state.calcPressure(), 5e-7,
                  "Existing pure-saturation absolute EOS pressure contract, bara");
              assertEquals(1.0, state.getComponent(0).getx(), 1e-12);
            }
            assertTrue(Math.abs(fluid.getPhase(0).getMolarVolume() - fluid.getPhase(1).getMolarVolume()) > 1e-5
                * Math.max(fluid.getPhase(0).getMolarVolume(), fluid.getPhase(1).getMolarVolume()));
            assertEquals(fluid.getPhase(0).getComponent(0).getLogFugacityCoefficient(),
                fluid.getPhase(1).getComponent(0).getLogFugacityCoefficient(), 1e-8);
          }
          report.add(row[0] + "," + t + "," + rule + "," + start + "," + fluid.getPressure() + "," + expected + ","
              + reference + "," + (fluid.getPressure() / reference - 1));
        }
      }
    }
    assertEquals(57, report.size(), "Four controls for every independent reference state");
    Files.createDirectories(Paths.get("target", "hv-co2-native"));
    Files.write(Paths.get("target", "hv-co2-native", "pure-water.csv"), report, StandardCharsets.UTF_8);
  }

  /**
   * Runs every native identity, refined numerical check and fresh/history comparison.
   *
   * @throws IOException if the diagnostic report cannot be written
   */
  @Test
  void nativeProfilesRetainFreshChangedStateAndOrderConsistency() throws IOException {
    List<String> report = new ArrayList<String>();
    report.add("profile,reverse,T_K,P_bara,xCO2_aqueous,ywater_gas");
    for (int profile = 0; profile < 3; profile++) {
      for (boolean reverse : new boolean[] {false, true}) {
        SystemInterface repeated = fluid(353.15, 50.0, profile, reverse);
        for (double t : new double[] {353.15, 353.15, 363.15, 353.15}) {
          repeated.setTemperature(t);
          flashAndCheck(repeated);
          SystemInterface fresh = fluid(t, 50.0, profile, !reverse);
          flashAndCheck(fresh);
          for (PhaseType type : new PhaseType[] {PhaseType.GAS, PhaseType.AQUEOUS}) {
            for (String name : new String[] {"CO2", "water"}) {
              assertEquals(fresh.getPhase(type).getComponent(name).getx(),
                  repeated.getPhase(type).getComponent(name).getx(), 1e-10);
            }
          }
          report.add(profile + "," + reverse + "," + t + ",50.0,"
              + repeated.getPhase(PhaseType.AQUEOUS).getComponent("CO2").getx() + ","
              + repeated.getPhase(PhaseType.GAS).getComponent("water").getx());
        }
      }
    }
    Files.createDirectories(Paths.get("target", "hv-co2-native"));
    Files.write(Paths.get("target", "hv-co2-native", "history.csv"), report, StandardCharsets.UTF_8);
  }

  /**
   * Preserves every primary row and reports measured errors separately from numerical gates.
   *
   * @throws IOException if the preserved input or diagnostic report cannot be read or written
   */
  @Test
  void assessesEveryUsablePrimaryObservationWithoutRetuning() throws IOException {
    List<String> input = Files.readAllLines(
        Paths.get("src", "test", "resources", "neqsim", "thermo", "mixingrule", "hv-co2-source", "input.csv"),
        StandardCharsets.UTF_8);
    List<String> report = new ArrayList<String>();
    report.add(
        "profile,source,row,T_K,P_bara,direction,observed,author_Ux_k2,predicted,relative_error,phase0,phase1,max_balance_error,max_log_fugacity_error,max_normalization_error,status");
    int solved = 0;
    for (int profile = 0; profile < 3; profile++) {
      for (int line = 1; line < input.size(); line++) {
        String[] columns = input.get(line).split(",", -1);
        String prefix = profile + "," + input.get(line);
        if (columns[3].isEmpty()) {
          report.add(prefix + ",,,,,,,,blocked_original_pressure");
          continue;
        }
        SystemInterface fluid = fluid(Double.parseDouble(columns[2]), Double.parseDouble(columns[3]), profile, false);
        double[] residual = flashAndCheck(fluid, false);
        int rich = fluid.getPhase(0).getComponent("CO2").getx() > fluid.getPhase(1).getComponent("CO2").getx() ? 0 : 1;
        double predicted = columns[4].equals("water_in_CO2") ? fluid.getPhase(rich).getComponent("water").getx()
            : fluid.getPhase(1 - rich).getComponent("CO2").getx();
        double observed = Double.parseDouble(columns[5]);
        report.add(prefix + "," + predicted + "," + (predicted / observed - 1) + "," + fluid.getPhase(0).getType() + ","
            + fluid.getPhase(1).getType() + "," + residual[0] + "," + residual[1] + "," + residual[2] + ",solved");
        solved++;
      }
    }
    assertEquals(357, solved, "119 usable observations for each declared source profile");
    Files.createDirectories(Paths.get("target", "hv-co2-native"));
    Files.write(Paths.get("target", "hv-co2-native", "primary-assessment.csv"), report, StandardCharsets.UTF_8);
  }

  /** Checks two-step fixed-composition liquid temperature differences independently. */
  @Test
  void independentTemperatureDifferencesResolveForEveryDeclaredProfile() {
    for (int profile = 0; profile < 3; profile++) {
      for (double t : new double[] {353.15, 363.15}) {
        SystemInterface fluid = fluid(t, 50.0, profile, false);
        flashAndCheck(fluid);
        PhaseInterface liquid = fluid.getPhase(PhaseType.AQUEOUS);
        for (String name : new String[] {"CO2", "water"}) {
          double coarse = temperatureDifference(liquid, name, profile, .001);
          double fine = temperatureDifference(liquid, name, profile, .0005);
          assertEquals(coarse, fine, 1e-8, "Step-halving resolution, inverse kelvin");
          assertEquals(liquid.getComponent(name).getdfugdt(), fine, 1e-7,
              "Independent fixed-composition liquid temperature derivative");
        }
      }
    }
  }

  /**
   * Evaluates a centered difference from fresh independently configured liquids.
   *
   * @param source equilibrium liquid whose composition is held fixed
   * @param name differentiated component
   * @param profile declared source configuration
   * @param h temperature step in kelvin
   * @return derivative of log fugacity coefficient per kelvin
   */
  private static double temperatureDifference(PhaseInterface source, String name, int profile, double h) {
    double[] value = new double[2];
    for (int side = 0; side < 2; side++) {
      SystemInterface trial = fluid(source.getTemperature() + (side == 0 ? -h : h), source.getPressure(), profile,
          false);
      trial.setMolarComposition(new double[] {source.getComponent("water").getx(), source.getComponent("CO2").getx()});
      trial.setForceSinglePhase(PhaseType.LIQUID);
      trial.init(3);
      value[side] = trial.getPhase(0).getComponent(name).getLogFugacityCoefficient();
    }
    return (value[1] - value[0]) / (2 * h);
  }

  /**
   * Creates a declared profile without modifying database defaults.
   *
   * @param t temperature in kelvin
   * @param p absolute pressure in bara
   * @param profile zero PR2016/library pure inputs, one SRK2017/Twu, two PR2017/Twu
   * @param reverse whether CO2 is added before water
   * @return independently configured equal-mole synthetic feed
   */
  static SystemInterface fluid(double t, double p, int profile, boolean reverse) {
    SystemInterface fluid = profile == 1 ? new SystemSrkEos(t, p) : new SystemPrEos(t, p);
    fluid.addComponent(reverse ? "CO2" : "water", 0.5);
    fluid.addComponent(reverse ? "water" : "CO2", 0.5);
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    configureProfile(fluid, profile, true);
    return fluid;
  }

  /**
   * Applies only the explicitly selected CO2/water tuple, retaining ordinary kij for every pair.
   *
   * @param fluid configured rule4 system containing water and CO2
   * @param profile declared source configuration
   * @param selectPair whether CO2/water uses HV or the all-classical reduction control
   */
  private static void configureProfile(SystemInterface fluid, int profile, boolean selectPair) {
    int water = fluid.getPhase(0).getComponent("water").getComponentNumber();
    int gas = fluid.getPhase(0).getComponent("CO2").getComponentNumber();
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      if (profile != 0) {
        pure(state.getComponent(water), 647.096, 220.64,
            profile == 1 ? new double[] {.36117, .87308, 2.4389} : new double[] {.40274, .87000, 1.8869});
        pure(state.getComponent(gas), 304.1282, 73.773,
            profile == 1 ? new double[] {.046115, .91388, 6.8838} : new double[] {.022762, .92848, 7.7217});
      }
      HVMixingRulesInterface rule = (HVMixingRulesInterface) state.getMixingRule();
      for (int i = 0; i < state.getNumberOfComponents(); i++) {
        for (int j = 0; j < state.getNumberOfComponents(); j++) {
          rule.setClassicOrHV(i, j, "Classic");
        }
      }
      if (selectPair) {
        rule.setClassicOrHV(water, gas, "HV");
        rule.setClassicOrHV(gas, water, "HV");
      }
      rule.setHValphaParameter(water, gas, .03);
      rule.setHValphaParameter(gas, water, .03);
      rule.setHVDijParameter(gas, water, profile == 0 ? 5230 : profile == 1 ? 5820 : 5831);
      rule.setHVDijTParameter(gas, water, profile == 0 ? -8 : profile == 1 ? -2.565 : -2.559);
      rule.setHVDijParameter(water, gas, profile == 0 ? -4127 : profile == 1 ? -3339 : -3311);
      rule.setHVDijTParameter(water, gas, profile == 0 ? 8.9 : profile == 1 ? .1390 : .03770);
    }
  }

  /** Checks selected-pair isolation, retained tuned kij and classical reduction with light gas and inhibitors. */
  @Test
  void selectedPairLeavesOtherGasInhibitorPairsClassical() {
    String[] names = {"water", "CO2", "methane", "methanol", "MEG"};
    for (int profile = 0; profile < 3; profile++) {
      for (boolean reverse : new boolean[] {false, true}) {
        SystemInterface allClassic = fiveComponentFluid(profile, reverse, false);
        SystemInterface classicalRule = fiveComponentFluid(profile, reverse, false);
        double[][][] ordinary = kij(classicalRule);
        classicalRule.setMixingRule(2);
        for (int phase = 0; phase < classicalRule.getMaxNumberOfPhases(); phase++) {
          EosMixingRulesInterface rule = (EosMixingRulesInterface) classicalRule.getPhase(phase).getMixingRule();
          for (int i = 0; i < names.length; i++) {
            for (int j = 0; j < names.length; j++) {
              rule.setBinaryInteractionParameter(i, j, ordinary[phase][i][j]);
            }
          }
        }
        SystemInterface selected = fiveComponentFluid(profile, reverse, true);
        SystemInterface freshOrder = fiveComponentFluid(profile, !reverse, true);
        selected.init(3);
        double[] unchanged = logPhi(selected, names);
        for (int phase = 0; phase < selected.getMaxNumberOfPhases(); phase++) {
          PhaseInterface state = selected.getPhase(phase);
          HVMixingRulesInterface rule = (HVMixingRulesInterface) state.getMixingRule();
          int water = state.getComponent("water").getComponentNumber();
          int gas = state.getComponent("CO2").getComponentNumber();
          for (int i = 0; i < names.length; i++) {
            for (int j = 0; j < names.length; j++) {
              if ((i == water && j == gas) || (i == gas && j == water)) {
                continue;
              }
              // Deliberately synthetic dormant-data perturbations, never calibrated or activated.
              rule.setHVDijParameter(i, j, 2500 + 100 * i - 50 * j);
              rule.setHVDijTParameter(i, j, .2 * (i - j));
              rule.setHValphaParameter(i, j, .2);
            }
          }
        }
        selected.init(3);
        assertArrayEquals(unchanged, logPhi(selected, names), 1e-12,
            "Dormant unselected HV data must not affect the selected-pair model");
        for (double t : new double[] {353.15, 353.15, 363.15, 353.15}) {
          for (SystemInterface state : new SystemInterface[] {allClassic, classicalRule, selected, freshOrder}) {
            state.setTemperature(t);
            state.init(1);
            state.init(3);
            assertSinglePhaseNative(state);
          }
          assertArrayEquals(logPhi(classicalRule, names), logPhi(allClassic, names), 1e-11,
              "All-classical rule4 must reduce to rule2 at identical pure inputs and kij");
          assertArrayEquals(logPhi(freshOrder, names), logPhi(selected, names), 1e-10,
              "Component order and dormant data must not change the selected model");
        }
        assertTrue(Math.abs(logPhi(selected, names)[0] - logPhi(allClassic, names)[0]) > 1e-6,
            "The explicitly selected HV pair must have an observable effect");
        for (int phase = 0; phase < selected.getMaxNumberOfPhases(); phase++) {
          PhaseInterface state = selected.getPhase(phase);
          int water = state.getComponent("water").getComponentNumber();
          int gas = state.getComponent("CO2").getComponentNumber();
          HVMixingRulesInterface rule = (HVMixingRulesInterface) state.getMixingRule();
          rule.setClassicOrHV(water, gas, "Classic");
          rule.setClassicOrHV(gas, water, "Classic");
        }
        selected.init(3);
        assertArrayEquals(logPhi(allClassic, names), logPhi(selected, names), 1e-11,
            "Deactivating only the selected pair must restore the all-classical result");
        assertSinglePhaseNative(selected);
      }
    }
  }

  /**
   * Constructs a forced-gas numerical control; it is not a physical partition assessment.
   *
   * @param profile declared source configuration
   * @param reverse whether to reverse component insertion order
   * @param selected whether to select CO2/water HV
   * @return independently configured five-component control
   */
  private static SystemInterface fiveComponentFluid(int profile, boolean reverse, boolean selected) {
    SystemInterface fluid = profile == 1 ? new SystemSrkEos(353.15, 50.0) : new SystemPrEos(353.15, 50.0);
    String[] names = {"water", "CO2", "methane", "methanol", "MEG"};
    for (int i = 0; i < names.length; i++) {
      fluid.addComponent(names[reverse ? names.length - 1 - i : i], .2);
    }
    fluid.setMixingRule(4);
    int gas = fluid.getPhase(0).getComponent("methane").getComponentNumber();
    int inhibitor = fluid.getPhase(0).getComponent("methanol").getComponentNumber();
    for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
      EosMixingRulesInterface rule = (EosMixingRulesInterface) fluid.getPhase(phase).getMixingRule();
      // Synthetic ordinary-kij tuning sentinel, not a fitted interaction parameter.
      rule.setBinaryInteractionParameter(gas, inhibitor, .0123);
      rule.setBinaryInteractionParameter(inhibitor, gas, .0123);
    }
    double[][][] before = kij(fluid);
    configureProfile(fluid, profile, selected);
    double[][][] after = kij(fluid);
    for (int phase = 0; phase < before.length; phase++) {
      for (int i = 0; i < names.length; i++) {
        assertArrayEquals(before[phase][i], after[phase][i], 0.0, "Source selection must preserve every tuned kij");
      }
    }
    fluid.setForceSinglePhase(PhaseType.GAS);
    return fluid;
  }

  /**
   * Copies ordinary kij from all allocated phases without reading an HV tuple as kij.
   *
   * @param fluid configured system
   * @return phase-indexed ordinary interaction matrices
   */
  private static double[][][] kij(SystemInterface fluid) {
    int count = fluid.getNumberOfComponents();
    double[][][] result = new double[fluid.getMaxNumberOfPhases()][count][count];
    for (int phase = 0; phase < result.length; phase++) {
      EosMixingRulesInterface rule = (EosMixingRulesInterface) fluid.getPhase(phase).getMixingRule();
      for (int i = 0; i < count; i++) {
        for (int j = 0; j < count; j++) {
          result[phase][i][j] = rule.getBinaryInteractionParameter(i, j);
        }
      }
    }
    return result;
  }

  /**
   * Reads a stable named-component log-fugacity vector.
   *
   * @param fluid initialized single phase
   * @param names canonical component order
   * @return log-fugacity coefficients in the requested order
   */
  private static double[] logPhi(SystemInterface fluid, String[] names) {
    double[] result = new double[names.length];
    for (int i = 0; i < names.length; i++) {
      result[i] = fluid.getPhase(0).getComponent(names[i]).getLogFugacityCoefficient();
    }
    return result;
  }

  /**
   * Asserts native identities and numerical differences for one forced-phase control.
   *
   * @param fluid initialized forced phase
   */
  private static void assertSinglePhaseNative(SystemInterface fluid) {
    assertEquals(1, fluid.getNumberOfPhases());
    assertEquals(1.0, fluid.getBeta(0), 1e-12);
    double total = 0.0;
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double x = fluid.getPhase(0).getComponent(component).getx();
      total += x;
      assertEquals(fluid.getComponent(component).getz(), x, 1e-10);
    }
    assertEquals(1.0, total, 1e-12);
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(1e-8);
    assertTrue(checker.checkFugacityCoefficients());
    assertTrue(checker.checkFugacityCoefficientsDn());
    assertTrue(checker.checkFugacityCoefficientsDn2());
    assertTrue(checker.checkFugacityCoefficientsDP());
    assertTrue(checker.checkFugacityCoefficientsDT());
    checker.setMaxError(1e-6);
    byte[] before = SerializationUtils.serialize(fluid);
    assertTrue(checker.checkNumerically());
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
  }

  /**
   * Applies declared critical inputs and the source Twu coefficients.
   *
   * @param component configured pure component
   * @param tc critical temperature in kelvin
   * @param pc critical pressure in bara
   * @param parameters Twu L, M, N coefficients
   */
  private static void pure(ComponentInterface component, double tc, double pc, double[] parameters) {
    component.setTC(tc);
    component.setPC(pc);
    if (component instanceof ComponentPR) {
      ((ComponentPR) component).setOmegaA(.4572355289213822);
      ((ComponentPR) component).setOmegaB(.07779607390388846);
    }
    component.setAttractiveTerm(12);
    for (int i = 0; i < 3; i++) {
      component.getAttractiveTerm().setParameters(i, parameters[i]);
    }
  }

  /**
   * Flashes and asserts normalization, balances, fugacity closure and native checks.
   *
   * @param fluid configured fluid with an equal-mole synthetic feed
   * @return maximum component-balance, log-fugacity and normalization errors
   */
  private static double[] flashAndCheck(SystemInterface fluid) {
    return flashAndCheck(fluid, true);
  }

  /**
   * Checks a binary flash including dense CO2-rich liquid endpoints.
   *
   * @param fluid configured equal-mole synthetic feed
   * @param requireGas whether the bounded history fixture requires a gas phase
   * @return maximum component-balance, log-fugacity and normalization errors
   */
  private static double[] flashAndCheck(SystemInterface fluid, boolean requireGas) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    if (requireGas) {
      assertTrue(fluid.hasPhaseType(PhaseType.GAS) && fluid.hasPhaseType(PhaseType.AQUEOUS));
    }
    double beta = 0.0;
    double maxBalance = 0.0;
    double maxFugacity = 0.0;
    double maxNormalization = 0.0;
    for (int phase = 0; phase < 2; phase++) {
      PhaseInterface state = fluid.getPhase(phase);
      beta += fluid.getBeta(phase);
      maxNormalization = Math.max(maxNormalization,
          Math.abs(state.getComponent(0).getx() + state.getComponent(1).getx() - 1));
      assertEquals(1.0, state.getComponent(0).getx() + state.getComponent(1).getx(), 1e-12);
    }
    maxNormalization = Math.max(maxNormalization, Math.abs(beta - 1));
    assertEquals(1.0, beta, 1e-12);
    for (int i = 0; i < 2; i++) {
      double sum = 0.0;
      double[] fugacity = new double[2];
      for (int phase = 0; phase < 2; phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        double x = state.getComponent(i).getx();
        sum += fluid.getBeta(phase) * x;
        fugacity[phase] = Math.log(x) + state.getComponent(i).getLogFugacityCoefficient();
      }
      maxBalance = Math.max(maxBalance, Math.abs(sum - .5));
      maxFugacity = Math.max(maxFugacity, Math.abs(fugacity[0] - fugacity[1]));
      assertEquals(.5, sum, 1e-10);
      assertEquals(fugacity[0], fugacity[1], 1e-8);
    }
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(1e-8);
    assertTrue(checker.checkFugacityCoefficients());
    assertTrue(checker.checkFugacityCoefficientsDn());
    assertTrue(checker.checkFugacityCoefficientsDn2());
    assertTrue(checker.checkFugacityCoefficientsDP());
    assertTrue(checker.checkFugacityCoefficientsDT());
    checker.setMaxError(1e-6);
    byte[] before = SerializationUtils.serialize(fluid);
    assertTrue(checker.checkNumerically());
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
    return new double[] {maxBalance, maxFugacity, maxNormalization};
  }
}
