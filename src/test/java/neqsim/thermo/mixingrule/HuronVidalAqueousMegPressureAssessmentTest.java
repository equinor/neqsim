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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentInterface;
import neqsim.thermo.component.ComponentPR;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemPrEos;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;
import neqsim.thermodynamicoperations.flashops.TPmultiflash;

/**
 * Assesses ordinary cubic total pressures against literal Fujita and Kikuchi (1989) measurements.
 *
 * <p>
 * DOI 10.11322/tjsrae.6.183, Table 1. Source y is liquid MEG mass percent, not vapor composition. No interaction, alpha
 * or accuracy tolerance is fitted. Reports retain adverse physical results. The source's abstract sign error is not
 * used as a thermodynamic equation.
 *
 * @author NeqSim contributors
 * @version 1.0
 */
class HuronVidalAqueousMegPressureAssessmentTest extends neqsim.NeqSimTest {
  /** Primary literal resource. */
  private static final String DATA = "/neqsim/thermo/mixingrule/hv-meg-aqueous/fujita-1989.csv";

  /**
   * Assesses every observation with ordinary classical and HV models, without physical acceptance.
   *
   * @param pr whether to use PR rather than SRK
   * @throws Exception if source reading, calculation, numerical qualification or output fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void assessTotalPressureAndInitializationHistory(boolean pr) throws Exception {
    List<double[]> rows = read();
    assertEquals(42, rows.size());
    Path directory = Paths.get("target", "hv-meg-aqueous");
    Files.createDirectories(directory);
    for (int rule : new int[] {2, 4}) {
      try (
          PrintWriter out = new PrintWriter(Files.newBufferedWriter(
              directory.resolve((pr ? "PR" : "SRK") + "-rule" + rule + ".csv"), StandardCharsets.UTF_8));
          PrintWriter histories = new PrintWriter(Files.newBufferedWriter(
              directory.resolve((pr ? "PR" : "SRK") + "-rule" + rule + "-histories.csv"), StandardCharsets.UTF_8))) {
        histories.println("row,history,T_K,p_bara,xMEG,yMEG,max_log_fugacity,max_balance,max_normalization,"
            + "cold_log_fugacity,cold_endpoint_relative,refined_endpoint_relative,cold_GRT,bubble_GRT,disposition_code");
        out.println("row,MEG_mass_percent,T_K,measured_p_kPa,predicted_p_kPa,signed_relative_error,"
            + "xMEG,yMEG,water_Tc_K,water_Pc_bara,water_omega,MEG_Tc_K,MEG_Pc_bara,MEG_omega,"
            + "max_log_fugacity,max_balance,max_normalization,cold_log_fugacity,cold_endpoint_relative,"
            + "refined_endpoint_relative,cold_gibbs_over_RT,bubble_tie_line_gibbs_over_RT,disposition");
        int index = 0;
        int metastableSourceRows = 0;
        for (double[] row : rows) {
          index++;
          SystemInterface fluid = fluid(pr, rule, row, false);
          double[] fresh = bubbleAndQualify(fluid, pr, rule);
          recordHistory(histories, index, "fresh", fluid.getTemperature(), fresh);
          double[] repeated = bubbleAndQualify(fluid, pr, rule);
          assertSame(fresh, repeated);
          recordHistory(histories, index, "repeated", fluid.getTemperature(), repeated);
          fluid.setTemperature(row[1] + 273.15 + 0.2);
          double[] changed = bubbleAndQualify(fluid, pr, rule);
          recordHistory(histories, index, "changed", fluid.getTemperature(), changed);
          fluid.setTemperature(row[1] + 273.15);
          double[] returned = bubbleAndQualify(fluid, pr, rule);
          assertSame(fresh, returned);
          recordHistory(histories, index, "returned", fluid.getTemperature(), returned);
          double[] reversed = bubbleAndQualify(fluid(pr, rule, row, true), pr, rule);
          assertSame(fresh, reversed);
          recordHistory(histories, index, "reversed", fluid.getTemperature(), reversed);
          metastableSourceRows += fresh[11] == 1 ? 1 : 0;
          double measured = row[2] * 101.325 / 760.0;
          out.printf(Locale.ROOT,
              "%d,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,"
                  + "%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%s%n",
              index, row[0], row[1] + 273.15, measured, fresh[0] * 100, fresh[0] * 100 / measured - 1, fresh[1],
              fresh[2], fluid.getComponent("water").getTC(), fluid.getComponent("water").getPC(),
              fluid.getComponent("water").getAcentricFactor(),
              row[0] == 0 ? Double.NaN : fluid.getComponent("MEG").getTC(),
              row[0] == 0 ? Double.NaN : fluid.getComponent("MEG").getPC(),
              row[0] == 0 ? Double.NaN : fluid.getComponent("MEG").getAcentricFactor(), fresh[3], fresh[4], fresh[5],
              fresh[6], fresh[7], fresh[8], fresh[9], fresh[10],
              fresh[11] == 1 ? "metastable_classical_bubble_branch" : "recovered_bubble_tie_line");
        }
        assertEquals(!pr && rule == 2 ? 1 : 0, metastableSourceRows, "Classified source controls");
        assertTrue(!out.checkError() && !histories.checkError(), "Every adverse comparison must be retained");
      }
    }
  }

  /**
   * Retains every history result including the unqualified nearby classical branch.
   *
   * @param out history report
   * @param row source row number
   * @param history initialization path
   * @param temperature kelvin temperature
   * @param values calculated pressure, endpoints, residuals, energies and disposition
   */
  private static void recordHistory(PrintWriter out, int row, String history, double temperature, double[] values) {
    out.printf(Locale.ROOT, "%d,%s,%.17g", row, history, temperature);
    for (double value : values) {
      out.printf(Locale.ROOT, ",%.17g", value);
    }
    out.println();
  }

  /**
   * Screens the already retained EOS-specific 2017 Twu water inputs against six direct water controls.
   *
   * <p>
   * This is a pure-only declared-input source-adoption assessment, not transfer of that alpha to an inhibitor tuple.
   * The same inputs are retained in HuronVidalCo2SourceDiagnosticTest. No fit, default change, original-author
   * configuration claim or mixture-accuracy inference is made.
   *
   * @param pr whether to use PR rather than SRK
   * @throws Exception if saturation, numerical checks or report output fails
   */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void retainedTwuWaterInputsAgainstMeasuredWaterControls(boolean pr) throws Exception {
    Path directory = Paths.get("target", "hv-meg-aqueous");
    Files.createDirectories(directory);
    try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
        directory.resolve((pr ? "PR" : "SRK") + "-source-water-only.csv"), StandardCharsets.UTF_8))) {
      out.println("T_K,measured_p_kPa,predicted_p_kPa,signed_relative_error,Tc_K,Pc_bara,Twu_L,Twu_M,Twu_N");
      int points = 0;
      for (double[] row : read()) {
        if (row[0] != 0) {
          continue;
        }
        points++;
        SystemInterface fluid = system(pr, row[1] + 273.15, 0.01);
        fluid.addComponent("water", 1);
        fluid.setMixingRule(4);
        double[] parameters = pr ? new double[] {.40274, .87000, 1.8869} : new double[] {.36117, .87308, 2.4389};
        for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
          ComponentInterface water = fluid.getPhase(phase).getComponent(0);
          water.setTC(647.096);
          water.setPC(220.64);
          if (water instanceof ComponentPR) {
            ((ComponentPR) water).setOmegaA(.4572355289213822);
            ((ComponentPR) water).setOmegaB(.07779607390388846);
          }
          water.setAttractiveTerm(12);
          for (int i = 0; i < 3; i++) {
            water.getAttractiveTerm().setParameters(i, parameters[i]);
          }
        }
        new ThermodynamicOperations(fluid).bubblePointPressureFlash();
        double pressure = fluid.getPressure();
        fluid.setBeta(.5);
        fluid.init(3);
        ThermodynamicModelTest check = new ThermodynamicModelTest(fluid);
        assertTrue(check.checkFugacityCoefficients());
        assertTrue(check.checkFugacityCoefficientsDn());
        assertTrue(check.checkFugacityCoefficientsDn2());
        assertTrue(check.checkFugacityCoefficientsDP());
        assertTrue(check.checkFugacityCoefficientsDT());
        check.setMaxError(1e-6);
        assertTrue(check.checkNumerically());
        assertTrue(fluid.getPhase(0).getZ() - fluid.getPhase(1).getZ() > 1e-7);
        for (int phase = 0; phase < 2; phase++) {
          PhaseEos state = (PhaseEos) fluid.getPhase(phase);
          assertEquals(pressure, state.calcPressure(), 1e-8);
          assertTrue(state.getdPdVTn() < 0);
        }
        assertEquals(logFugacity(fluid.getPhase(0), 0), logFugacity(fluid.getPhase(1), 0), 1e-8);
        double measured = row[2] * 101.325 / 760;
        out.printf(Locale.ROOT, "%.17g,%.17g,%.17g,%.17g,647.096,220.64,%.17g,%.17g,%.17g%n", row[1] + 273.15, measured,
            pressure * 100, pressure * 100 / measured - 1, parameters[0], parameters[1], parameters[2]);
      }
      assertEquals(6, points);
      assertTrue(!out.checkError());
    }
  }

  /**
   * Creates a fresh independent fluid from the source liquid mass basis.
   *
   * @param pr whether to use PR
   * @param rule mixing-rule number
   * @param row MEG mass percent, Celsius temperature, absolute Torr pressure
   * @param reverse whether to reverse component insertion order
   * @return unflashed fluid with normalized liquid mole fractions
   */
  private static SystemInterface fluid(boolean pr, int rule, double[] row, boolean reverse) {
    SystemInterface fluid = system(pr, row[1] + 273.15, 0.01);
    if (row[0] == 0) {
      fluid.addComponent("water", 1.0);
    } else {
      // Read current native molecular weights; never interpret mass percentage as mole percentage.
      fluid.addComponent("water", 1.0);
      fluid.addComponent("MEG", 1.0);
      double waterMoles = (100 - row[0]) / fluid.getComponent("water").getMolarMass();
      double megMoles = row[0] / fluid.getComponent("MEG").getMolarMass();
      double x = megMoles / (waterMoles + megMoles);
      fluid = system(pr, row[1] + 273.15, 0.01);
      fluid.addComponent(reverse ? "MEG" : "water", reverse ? x : 1 - x);
      fluid.addComponent(reverse ? "water" : "MEG", reverse ? 1 - x : x);
    }
    fluid.setMixingRule(rule);
    return fluid;
  }

  /**
   * Computes saturation, then qualifies a balanced tie-line feed without changing measured liquid input.
   *
   * <p>
   * The bubble-point vapor fraction is infinitesimal. Derivative checking instead uses the same endpoints with equal
   * phase amounts, avoiding an unresolved trace-inventory mole stencil. This constructed overall feed is a numerical
   * validation basis, not a measured feed.
   *
   * @param bubble liquid-composition input
   * @param pr whether to use PR
   * @param rule mixing-rule number
   * @return pressure, endpoints, residuals, cold/refined precision, Gibbs energies and metastability flag
   * @throws Exception if the saturation calculation fails
   */
  private static double[] bubbleAndQualify(SystemInterface bubble, boolean pr, int rule) throws Exception {
    new ThermodynamicOperations(bubble).bubblePointPressureFlash();
    double initialPressure = bubble.getPressure();
    // Refine the same saturation solve; retain the first-call precision study in the public receipt.
    new ThermodynamicOperations(bubble).bubblePointPressureFlash();
    assertEquals(initialPressure, bubble.getPressure(), 1e-8, "Saturation refinement pressure");
    bubble.init(3);
    PhaseInterface liquid = bubble.getPhase(0).getType() == PhaseType.GAS ? bubble.getPhase(1) : bubble.getPhase(0);
    PhaseInterface gas = bubble.getPhase(0).getType() == PhaseType.GAS ? bubble.getPhase(0) : bubble.getPhase(1);
    assertTrue(gas.getZ() - liquid.getZ() > 1e-7, "Distinct vapor and liquid roots");
    SystemInterface balanced = system(pr, bubble.getTemperature(), bubble.getPressure());
    for (int i = 0; i < bubble.getNumberOfComponents(); i++) {
      balanced.addComponent(bubble.getComponent(i).getComponentName(),
          0.5 * (gas.getComponent(i).getx() + liquid.getComponent(i).getx()));
    }
    balanced.setMixingRule(rule);
    balanced.setEnhancedMultiPhaseCheck(true);
    if (balanced.getNumberOfComponents() == 1) {
      balanced.init(0);
      balanced.setBeta(0.5);
      balanced.init(3);
    } else {
      new ThermodynamicOperations(balanced).TPflash();
      balanced.init(3);
    }
    assertEquals(2, balanced.getNumberOfPhases(),
        "T=" + bubble.getTemperature() + " p=" + bubble.getPressure() + " pr=" + pr + " rule=" + rule + " bubbleX="
            + liquid.getComponent(0).getx() + " bubbleY=" + gas.getComponent(0).getx() + " bubbleF="
            + (logFugacity(liquid, 0) - logFugacity(gas, 0)) + " balancedZ=" + balanced.getPhase(0).getZ());
    assertTrue(balanced.hasPhaseType(PhaseType.GAS), "Recovered tie-line includes vapor");
    assertClosed(balanced);
    double coldF = maxLogFugacity(balanced);
    double coldEndpoint = endpointError(balanced, gas, liquid);
    double coldGibbs = gibbsOverRT(balanced);
    double bubbleGibbs = 0;
    for (int i = 0; i < bubble.getNumberOfComponents(); i++) {
      bubbleGibbs += .5
          * (gas.getComponent(i).getx() * logFugacity(gas, i) + liquid.getComponent(i).getx() * logFugacity(liquid, i));
    }
    SystemInterface cold = balanced;
    balanced = cold.clone();
    if (balanced.getNumberOfComponents() > 1) {
      // Refine only the already discovered phase set: no stability run, phase addition or endpoint seeding.
      TPmultiflash refinement = new TPmultiflash(balanced, false);
      refinement.setDoubleArrays();
      for (int i = 0; i < 5; i++) {
        refinement.solveBeta();
      }
      balanced.init(3);
    }
    assertEquals(cold.getNumberOfPhases(), balanced.getNumberOfPhases());
    assertClosed(balanced);
    for (int phase = 0; phase < 2; phase++) {
      assertEquals(cold.getPhase(phase).getType(), balanced.getPhase(phase).getType());
    }
    assertTrue(gibbsOverRT(balanced) <= coldGibbs + 1e-10, "Refinement does not raise Gibbs energy");
    int gasIndex = balanced.getPhase(0).getType() == PhaseType.GAS ? 0 : 1;
    PhaseInterface recoveredGas = balanced.getPhase(gasIndex);
    PhaseInterface recoveredLiquid = balanced.getPhase(1 - gasIndex);
    assertTrue(recoveredGas.getZ() - recoveredLiquid.getZ() > 1e-7, "Distinct recovered roots");
    double refinedEndpoint = endpointError(balanced, gas, liquid);
    boolean alternativeClassicalBranch = !pr && rule == 2 && bubble.getNumberOfComponents() == 2
        && Math.abs(liquid.getComponent("MEG").getx() - .16726406630291485) < 1e-10
        && (Math.abs(bubble.getTemperature() - 263.12) < 1e-9 || Math.abs(bubble.getTemperature() - 263.32) < 1e-9);
    if (alternativeClassicalBranch) {
      // The ordinary SRK control has a lower-G alternative liquid branch at this source state.
      assertTrue(refinedEndpoint > .1, "Retain alternative classical stationary branch");
      double tangentDistance = 0;
      for (int i = 0; i < bubble.getNumberOfComponents(); i++) {
        tangentDistance += recoveredLiquid.getComponent(i).getx()
            * (logFugacity(recoveredLiquid, i) - logFugacity(liquid, i));
      }
      if (Math.abs(bubble.getTemperature() - 263.12) < 1e-9) {
        assertTrue(coldGibbs < bubbleGibbs - 1e-7, "Source bubble branch is metastable");
        assertTrue(tangentDistance < -1e-7, "Independent lower liquid tangent-plane witness");
      } else {
        // Nearby discovery selects a higher-G stationary branch: retain this unresolved limitation explicitly.
        assertTrue(coldGibbs > bubbleGibbs + 1e-9, "Nearby cold-branch selection remains unqualified");
        assertTrue(tangentDistance > 1e-8, "Nearby alternative is not a lower tangent-plane witness");
      }
    } else {
      assertEquals(.5, balanced.getBeta(gasIndex), 1e-8, "Constructed equal-amount tie-line feed");
      for (int i = 0; i < bubble.getNumberOfComponents(); i++) {
        String name = bubble.getComponent(i).getComponentName();
        double expectedGas = gas.getComponent(name).getx();
        double expectedLiquid = liquid.getComponent(name).getx();
        assertEquals(expectedGas, recoveredGas.getComponent(name).getx(), Math.max(1e-13, Math.abs(expectedGas) * 1e-8),
            "Recovered vapor endpoint " + name);
        assertEquals(expectedLiquid, recoveredLiquid.getComponent(name).getx(),
            Math.max(1e-13, Math.abs(expectedLiquid) * 1e-8), "Recovered liquid endpoint " + name);
      }
    }
    double maxF = 0;
    double maxB = 0;
    double maxN = 0;
    double betaSum = 0;
    for (int phase = 0; phase < 2; phase++) {
      PhaseEos state = (PhaseEos) balanced.getPhase(phase);
      betaSum += balanced.getBeta(phase);
      assertEquals(balanced.getPressure(), state.calcPressure(), 1e-8, "EOS pressure closure");
      assertTrue(state.getdPdVTn() < 0, "Mechanically stable roots");
      double sum = 0;
      for (int i = 0; i < balanced.getNumberOfComponents(); i++) {
        sum += state.getComponent(i).getx();
      }
      maxN = Math.max(maxN, Math.abs(sum - 1));
    }
    maxN = Math.max(maxN, Math.abs(betaSum - 1));
    for (int i = 0; i < balanced.getNumberOfComponents(); i++) {
      double inventory = 0;
      double logF = logFugacity(balanced.getPhase(0), i);
      for (int phase = 0; phase < 2; phase++) {
        PhaseInterface state = balanced.getPhase(phase);
        inventory += balanced.getBeta(phase) * state.getComponent(i).getx();
        maxF = Math.max(maxF, Math.abs(logFugacity(state, i) - logF));
      }
      maxB = Math.max(maxB, Math.abs(inventory - balanced.getComponent(i).getz()));
    }
    assertTrue(maxN <= 1e-12, "Phase/composition normalization");
    assertTrue(maxB <= 1e-10, "Component balance");
    assertTrue(maxF <= 1e-8, "Interphase fugacity equality");
    ThermodynamicModelTest check = new ThermodynamicModelTest(balanced);
    assertTrue(check.checkFugacityCoefficients(), "Gibbs identity");
    assertTrue(check.checkFugacityCoefficientsDn(), "Gibbs-Duhem");
    assertTrue(check.checkFugacityCoefficientsDn2(), "Hessian symmetry");
    assertTrue(check.checkFugacityCoefficientsDP(), "Pressure derivative");
    assertTrue(check.checkFugacityCoefficientsDT(), "Temperature derivative");
    check.setMaxError(1e-6);
    assertTrue(check.checkNumerically(), "Existing independently refined native T/P/n stencil");
    double x = bubble.getNumberOfComponents() == 1 ? 0 : liquid.getComponent("MEG").getx();
    double y = bubble.getNumberOfComponents() == 1 ? 0 : gas.getComponent("MEG").getx();
    if (bubble.getNumberOfComponents() > 1) {
      assertEquals(bubble.getComponent("MEG").getz(), x, 1e-8, "Bubble liquid matches specified source basis");
    }
    return new double[] {bubble.getPressure(), x, y, maxF, maxB, maxN, coldF, coldEndpoint, refinedEndpoint, coldGibbs,
        bubbleGibbs, alternativeClassicalBranch ? (bubble.getTemperature() < 263.2 ? 1 : 2) : 0};
  }

  /**
   * Requires closure on the first cold result before any precision refinement.
   *
   * @param fluid cold native result
   */
  private static void assertClosed(SystemInterface fluid) {
    double beta = 0;
    for (int phase = 0; phase < 2; phase++) {
      beta += fluid.getBeta(phase);
      assertTrue(fluid.getBeta(phase) > 1e-8 && fluid.getBeta(phase) < 1 - 1e-8);
      double sum = 0;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        sum += fluid.getPhase(phase).getComponent(i).getx();
      }
      assertEquals(1, sum, 1e-12);
    }
    assertEquals(1, beta, 1e-12);
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      double inventory = 0;
      for (int phase = 0; phase < 2; phase++) {
        inventory += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(i).getx();
      }
      assertEquals(fluid.getComponent(i).getz(), inventory, 1e-10);
    }
    assertTrue(maxLogFugacity(fluid) <= 1e-8);
  }

  /**
   * Computes a dimensionless split Gibbs energy with common pure-reference terms omitted.
   *
   * @param fluid closed native state
   * @return G/(nRT) up to the shared pure reference
   */
  private static double gibbsOverRT(SystemInterface fluid) {
    double value = 0;
    for (int phase = 0; phase < 2; phase++) {
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        value += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(i).getx()
            * logFugacity(fluid.getPhase(phase), i);
      }
    }
    return value;
  }

  /**
   * Computes an interphase residual independently of the flash solver's termination scalar.
   *
   * @param fluid closed native state
   * @return maximum absolute log fugacity difference
   */
  private static double maxLogFugacity(SystemInterface fluid) {
    double value = 0;
    for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
      value = Math.max(value, Math.abs(logFugacity(fluid.getPhase(0), i) - logFugacity(fluid.getPhase(1), i)));
    }
    return value;
  }

  /**
   * Measures the phase-matched relative endpoint discrepancy without assuming global stability.
   *
   * @param fluid native split
   * @param gas saturation gas reference
   * @param liquid saturation liquid reference
   * @return maximum relative component discrepancy
   */
  private static double endpointError(SystemInterface fluid, PhaseInterface gas, PhaseInterface liquid) {
    double value = 0;
    for (int phase = 0; phase < 2; phase++) {
      PhaseInterface expected = fluid.getPhase(phase).getType() == PhaseType.GAS ? gas : liquid;
      for (int i = 0; i < fluid.getNumberOfComponents(); i++) {
        String name = fluid.getComponent(i).getComponentName();
        value = Math.max(value,
            Math.abs(fluid.getPhase(phase).getComponent(name).getx() / expected.getComponent(name).getx() - 1));
      }
    }
    return value;
  }

  /**
   * Computes a component's log composition-weighted fugacity coefficient.
   *
   * @param phase native phase
   * @param i component index
   * @return log(x phi)
   */
  private static double logFugacity(PhaseInterface phase, int i) {
    return Math.log(phase.getComponent(i).getx()) + Math.log(phase.getComponent(i).getFugacityCoefficient());
  }

  /**
   * Checks observable history/order identity independently of experimental errors.
   *
   * @param expected fresh pressure and phase compositions
   * @param actual repeated or reordered values
   */
  private static void assertSame(double[] expected, double[] actual) {
    for (int i = 0; i < 3; i++) {
      assertEquals(expected[i], actual[i], Math.max(1e-13, Math.abs(expected[i]) * 1e-8));
    }
  }

  /**
   * Creates an ordinary cubic system, preserving native pure alpha and critical inputs.
   *
   * @param pr whether to use PR
   * @param t temperature in kelvin
   * @param p pressure in absolute bara
   * @return empty native system
   */
  private static SystemInterface system(boolean pr, double t, double p) {
    return pr ? new SystemPrEos(t, p) : new SystemSrkEos(t, p);
  }

  /**
   * Reads every source decimal without replacing missing observations or fitting parameters.
   *
   * @return literal source rows
   * @throws Exception if the resource cannot be read
   */
  private static List<double[]> read() throws Exception {
    List<double[]> rows = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        HuronVidalAqueousMegPressureAssessmentTest.class.getResourceAsStream(DATA), StandardCharsets.UTF_8))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] v = line.split(",");
        rows.add(new double[] {Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2])});
      }
    }
    return rows;
  }
}
