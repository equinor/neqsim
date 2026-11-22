package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.Locale;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseEos;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Reproduces the published SRK-HV correlation for MEG and n-heptane without changing library defaults.
 *
 * <p>
 * The interaction parameters are from Folas, Modeling of Complex Mixtures Containing Hydrogen Bonding Molecules, Ph.D.
 * thesis (2006), Table 6.2. Table 6.6 reports mean absolute relative deviations of 6.7 percent for heptane in MEG and
 * 21.2 percent for MEG in heptane. Measurements and the compatible MEG pure-component configuration are from Derawi,
 * Modeling of Phase Equilibria Containing Associating Fluids (2002), Tables 3.3, 4.1 and 5.5. The parameters use
 * volume-weighted modified NRTL, rather than ordinary low-pressure NRTL. With MEG as component 1, D12 = (g12 - g22)/R =
 * 430 K, D21 = (g21 - g11)/R = 2595 K and alpha = 0.13. The temperature-independent offsets are zero. MEG has Tc = 720
 * K, Pc = 82 bar and the dimensionless Mathias-Copeman coefficients (1.1121, 0.9679, -1.4200).
 * </p>
 *
 * <p>
 * Primary sources: <a href=
 * "https://backend.orbit.dtu.dk/ws/portalfiles/portal/123926886/CDocuments_and_SettingsalbDesktopPh.D._Georgios_K._Folas.pdf.pdf">Folas
 * thesis</a> and <a href="https://backend.orbit.dtu.dk/ws/portalfiles/portal/5653725/PhD_Derawi.pdf">Derawi thesis</a>.
 * The published correlation error exceeds the reported measurement uncertainties; reproducing this correlation does not
 * establish accuracy within experimental uncertainty or justify transferring the coefficients to PR or other
 * pure-component models.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HuronVidalLiteratureReproductionTest extends neqsim.NeqSimTest {
  /** Temperature in kelvin and measured mass fractions, after dividing the source's percent values by 100. */
  private static final double[][] MEASUREMENTS = {{315.95, 0.000198, 0.001478}, {322.75, 0.000287, 0.001532},
      {329.75, 0.000399, 0.001760}, {336.55, 0.000553, 0.001903}, {341.15, 0.000677, 0.002024},
      {346.95, 0.000867, 0.002119}, {351.85, 0.001066, 0.002238}};

  /**
   * Reproduces both published deviations, asserts native derivatives, and preserves pointwise errors.
   *
   * @throws IOException if the diagnostic report cannot be written
   */
  @Test
  void reproducesPublishedMegHeptaneCorrelationWithCompatiblePureComponentParameters() throws IOException {
    double megDeviation = 0.0;
    double heptaneDeviation = 0.0;
    try (PrintWriter out = report("MEG-heptane")) {
      out.println("T_K,P_bara,measured_wMEG_in_HC,calculated_wMEG_in_HC,measured_wHC_in_MEG,"
          + "calculated_wHC_in_MEG,relative_MEG_error,relative_HC_error,numerically_qualified");
      for (double[] measurement : MEASUREMENTS) {
        SystemInterface fluid = literatureFluid(measurement[0]);
        new ThermodynamicOperations(fluid).TPflash();
        fluid.init(3);
        assertEquilibrium(fluid);
        double megInHeptane = massFraction(fluid.getPhase(PhaseType.OIL), 0);
        double heptaneInMeg = massFraction(fluid.getPhase(PhaseType.AQUEOUS), 1);
        double megError = megInHeptane / measurement[1] - 1.0;
        double heptaneError = heptaneInMeg / measurement[2] - 1.0;
        megDeviation += Math.abs(megError) * 100.0;
        heptaneDeviation += Math.abs(heptaneError) * 100.0;
        out.printf(Locale.ROOT, "%.17g,1.0,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,true%n", measurement[0], measurement[1],
            megInHeptane, measurement[2], heptaneInMeg, megError, heptaneError);
      }
      assertTrue(!out.checkError(), "Source-compatible LLE report must be written");
    }
    assertEquals(6.7, heptaneDeviation / MEASUREMENTS.length, 0.15,
        "Published mean absolute relative deviation for heptane in MEG, in percentage points");
    assertEquals(21.2, megDeviation / MEASUREMENTS.length, 0.15,
        "Published mean absolute relative deviation for MEG in heptane, in percentage points");
  }

  /**
   * Assesses the source-paired pure alpha against existing NIST measurements without an accuracy pass threshold.
   *
   * @throws Exception if the literal data, saturation calculation or report fails
   */
  @Test
  void sourcePureMegAlphaKeepsMeasuredAndNumericalQualificationSeparate() throws Exception {
    InputStream stream = HuronVidalLiteratureReproductionTest.class
        .getResourceAsStream("/neqsim/thermo/mixingrule/hv-meg-qualification/pure-meg-2009.csv");
    assertTrue(stream != null, "Existing literal NIST pressure resource must be present");
    int points = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        PrintWriter out = report("pure-MEG")) {
      reader.readLine();
      out.println("T_K,P_kPa,measured_P_kPa,compiler_U95_kPa,relative_error,inside_compiler_U95,"
          + "Tc_K,Pc_bara,MC1,MC2,MC3,bubble_beta0,validation_beta0,numerically_qualified");
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty()) {
          continue;
        }
        String[] row = line.split(",", -1);
        double temperature = Double.parseDouble(row[0]);
        double measured = Double.parseDouble(row[1]);
        double uncertainty = Double.parseDouble(row[2]);
        SystemInterface fluid = new SystemSrkEos(temperature, 1.0);
        fluid.addComponent("MEG", 1.0);
        fluid.setMixingRule(4);
        for (int phase = 0; phase < fluid.getMaxNumberOfPhases(); phase++) {
          configureMeg(fluid.getPhase(phase));
        }
        new ThermodynamicOperations(fluid).bubblePointPressureFlash();
        double bubbleBeta = fluid.getBeta(0);
        double saturationPressure = fluid.getPressure();
        // Pure coexistence permits any phase fraction. Avoid an infinitesimal bubble-point inventory in mole
        // differences.
        fluid.setBeta(0.5);
        fluid.init(3);
        assertEquals(saturationPressure, fluid.getPressure(), 0.0, "Validation basis preserves saturation pressure");
        assertNativeModel(fluid);
        assertEquals(2, fluid.getNumberOfPhases());
        double z0 = fluid.getPhase(0).getZ();
        double z1 = fluid.getPhase(1).getZ();
        assertTrue(Math.abs(z0 - z1) > 1e-7 * Math.max(z0, z1), "Distinct saturation roots");
        for (int phase = 0; phase < 2; phase++) {
          PhaseEos state = (PhaseEos) fluid.getPhase(phase);
          assertEquals(0.5, fluid.getBeta(phase), 1e-12, "Pure validation phase normalization");
          assertEquals(1.0, state.getComponent(0).getx(), 1e-12, "Pure composition normalization");
          assertEquals(fluid.getPressure(), state.calcPressure(), 1e-8);
          assertTrue(state.getdPdVTn() < 0.0, "Both roots are mechanically stable");
        }
        assertEquals(1.0, fluid.getPhase(0).getNumberOfMolesInPhase() + fluid.getPhase(1).getNumberOfMolesInPhase(),
            1e-10, "Pure component balance");
        assertEquals(logFugacity(fluid.getPhase(0), 0), logFugacity(fluid.getPhase(1), 0), 1e-8);
        double pressure = fluid.getPressure() * 100.0;
        out.printf(Locale.ROOT, "%.17g,%.17g,%.17g,%.17g,%.17g,%b,720.0,82.0,1.1121,0.9679,-1.4200,%.17g,%.17g,true%n",
            temperature, pressure, measured, uncertainty, pressure / measured - 1.0,
            Math.abs(pressure - measured) <= uncertainty, bubbleBeta, fluid.getBeta(0));
        points++;
      }
      assertTrue(!out.checkError(), "Source-compatible pure-pressure report must be written");
    }
    assertEquals(15, points, "All existing pressure observations are assessed");
  }

  /**
   * Opens a diagnostic file without interpreting numerical success as experimental accuracy.
   *
   * @param name report basename
   * @return UTF-8 report writer under the Maven target directory
   * @throws IOException if the output cannot be opened
   */
  private static PrintWriter report(String name) throws IOException {
    Path directory = Paths.get("target", "hv-source-compatible");
    Files.createDirectories(directory);
    return new PrintWriter(Files.newBufferedWriter(directory.resolve(name + ".csv"), StandardCharsets.UTF_8));
  }

  /** Checks changed, repeated, returned and reverse-order source configurations against fresh controls. */
  @Test
  void sourceConfigurationRetainsStateAndComponentOrder() {
    for (boolean reverse : new boolean[] {false, true}) {
      SystemInterface retained = literatureFluid(315.95, 0.5, 0.5, reverse);
      for (double temperature : new double[] {315.95, 315.95, 329.75, 351.85, 315.95}) {
        retained.setTemperature(temperature);
        new ThermodynamicOperations(retained).TPflash();
        retained.init(3);
        assertEquilibrium(retained);
        SystemInterface fresh = literatureFluid(temperature);
        new ThermodynamicOperations(fresh).TPflash();
        fresh.init(3);
        for (PhaseType type : new PhaseType[] {PhaseType.OIL, PhaseType.AQUEOUS}) {
          for (String name : new String[] {"MEG", "n-heptane"}) {
            double expected = fresh.getPhase(type).getComponent(name).getx();
            assertEquals(expected, retained.getPhase(type).getComponent(name).getx(),
                Math.max(1e-13, 1e-8 * Math.abs(expected)), "fresh/history/order " + type + " " + name);
          }
        }
      }
    }
  }

  /** Checks the source-specific temperature derivative using fresh fixed-composition liquid states. */
  @Test
  void sourceLiquidTemperatureDerivativesAgreeWithRefinedIndependentStates() {
    for (double[] measurement : MEASUREMENTS) {
      SystemInterface fluid = literatureFluid(measurement[0]);
      new ThermodynamicOperations(fluid).TPflash();
      fluid.init(3);
      for (PhaseType type : new PhaseType[] {PhaseType.OIL, PhaseType.AQUEOUS}) {
        PhaseInterface phase = fluid.getPhase(type);
        for (String name : new String[] {"MEG", "n-heptane"}) {
          double analytic = phase.getComponent(name).getdfugdt();
          double coarse = liquidTemperatureDifference(phase, name, 0.001);
          double fine = liquidTemperatureDifference(phase, name, 0.0005);
          double tolerance = 1e-6 * Math.max(1.0, Math.abs(analytic));
          assertEquals(fine, coarse, tolerance, "temperature step refinement " + type + " " + name);
          assertEquals(analytic, fine, tolerance, "independent temperature derivative " + type + " " + name);
        }
      }
    }
  }

  /**
   * Resolves a central temperature difference from independently constructed, unflashed liquid states.
   *
   * @param source equilibrium phase whose composition is held fixed
   * @param name differentiated component name
   * @param step temperature displacement in kelvin
   * @return derivative of log fugacity coefficient per kelvin
   */
  private static double liquidTemperatureDifference(PhaseInterface source, String name, double step) {
    double[] values = new double[2];
    for (int side = 0; side < 2; side++) {
      double temperature = source.getTemperature() + (side == 0 ? -step : step);
      SystemInterface trial = literatureFluid(temperature, source.getComponent("MEG").getx(),
          source.getComponent("n-heptane").getx(), false);
      trial.setForceSinglePhase(PhaseType.LIQUID);
      trial.init(3);
      values[side] = trial.getPhase(0).getComponent(name).getLogFugacityCoefficient();
    }
    return (values[1] - values[0]) / (2.0 * step);
  }

  /**
   * Asserts all native identities and refined derivatives without modifying checker tolerances or the source state.
   *
   * @param fluid source-compatible initialized fluid
   */
  private static void assertNativeModel(SystemInterface fluid) {
    ThermodynamicModelTest checker = new ThermodynamicModelTest(fluid);
    checker.setMaxError(1e-8);
    assertTrue(checker.checkFugacityCoefficients(), "source residual Gibbs identity");
    assertTrue(checker.checkFugacityCoefficientsDn(), "source composition Gibbs-Duhem");
    assertTrue(checker.checkFugacityCoefficientsDn2(), "source composition Hessian symmetry");
    assertTrue(checker.checkFugacityCoefficientsDP(), "source pressure derivative identity");
    assertTrue(checker.checkFugacityCoefficientsDT(), "source temperature derivative identity");
    checker.setMaxError(1e-6);
    byte[] before = SerializationUtils.serialize(fluid);
    assertTrue(checker.checkNumerically(), "source refined T/P/n numerical derivatives");
    assertArrayEquals(before, SerializationUtils.serialize(fluid), "native diagnostics preserve the source state");
  }

  /**
   * Creates an explicit test-local literature configuration, including the pure properties used in the fit.
   *
   * @param temperature measured equilibrium temperature in kelvin
   * @return configured equal-mole feed at 1 bara
   */
  private static SystemInterface literatureFluid(double temperature) {
    return literatureFluid(temperature, 0.5, 0.5, false);
  }

  /**
   * Creates a source-compatible fluid at an explicit amount and directed component order.
   *
   * @param temperature temperature in kelvin
   * @param megMoles MEG molar amount
   * @param heptaneMoles heptane molar amount
   * @param reverse whether heptane is added first
   * @return independently configured source fluid at 1 bara
   */
  private static SystemInterface literatureFluid(double temperature, double megMoles, double heptaneMoles,
      boolean reverse) {
    SystemInterface fluid = new SystemSrkEos(temperature, 1.0);
    fluid.addComponent(reverse ? "n-heptane" : "MEG", reverse ? heptaneMoles : megMoles);
    fluid.addComponent(reverse ? "MEG" : "n-heptane", reverse ? megMoles : heptaneMoles);
    int megIndex = reverse ? 1 : 0;
    int heptaneIndex = reverse ? 0 : 1;
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    for (int phaseIndex = 0; phaseIndex < fluid.getMaxNumberOfPhases(); phaseIndex++) {
      PhaseInterface phase = fluid.getPhase(phaseIndex);
      HVMixingRulesInterface rule = (HVMixingRulesInterface) phase.getMixingRule();
      rule.setClassicOrHV(0, 1, "HV");
      rule.setClassicOrHV(1, 0, "HV");
      rule.setHValphaParameter(0, 1, 0.13);
      rule.setHVDijParameter(megIndex, heptaneIndex, 430.0);
      rule.setHVDijParameter(heptaneIndex, megIndex, 2595.0);
      rule.setHVDijTParameter(0, 1, 0.0);
      rule.setHVDijTParameter(1, 0, 0.0);
      configureMeg(phase);
    }
    return fluid;
  }

  /**
   * Applies the exact already-catalogued Derawi pure-MEG configuration to one allocated phase.
   *
   * @param phase configured phase containing MEG
   */
  private static void configureMeg(PhaseInterface phase) {
    ComponentEosInterface meg = (ComponentEosInterface) phase.getComponent("MEG");
    meg.setTC(720.0);
    meg.setPC(82.0);
    meg.setAttractiveTerm(4);
    meg.getAttractiveTerm().setParameters(0, 1.1121);
    meg.getAttractiveTerm().setParameters(1, 0.9679);
    meg.getAttractiveTerm().setParameters(2, -1.4200);
  }

  /**
   * Converts a phase mole fraction to the mass-fraction basis of the experimental table.
   *
   * @param phase equilibrium phase
   * @param component component index
   * @return component mass fraction
   */
  private static double massFraction(PhaseInterface phase, int component) {
    return phase.getComponent(component).getx() * phase.getComponent(component).getMolarMass() / phase.getMolarMass();
  }

  /**
   * Checks the two-liquid topology, normalized fractions, component balance and interphase fugacity equality.
   *
   * @param fluid equilibrated literature fluid
   */
  private static void assertEquilibrium(SystemInterface fluid) {
    assertNativeModel(fluid);
    assertEquals(2, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.OIL));
    assertTrue(fluid.hasPhaseType(PhaseType.AQUEOUS));
    double betaSum = 0.0;
    for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
      double beta = fluid.getBeta(phase);
      assertTrue(Double.isFinite(beta) && beta > 0.0 && beta < 1.0);
      betaSum += beta;
      double compositionSum = 0.0;
      for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
        double fraction = fluid.getPhase(phase).getComponent(component).getx();
        assertTrue(Double.isFinite(fraction) && fraction > 0.0 && fraction < 1.0);
        compositionSum += fraction;
      }
      assertEquals(1.0, compositionSum, 1.0e-12);
    }
    assertEquals(1.0, betaSum, 1.0e-12);
    for (int component = 0; component < fluid.getNumberOfComponents(); component++) {
      double recovered = 0.0;
      double reference = logFugacity(fluid.getPhase(0), component);
      for (int phase = 0; phase < fluid.getNumberOfPhases(); phase++) {
        recovered += fluid.getBeta(phase) * fluid.getPhase(phase).getComponent(component).getx();
        assertEquals(reference, logFugacity(fluid.getPhase(phase), component), 1.0e-8);
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recovered, 1.0e-10);
    }
  }

  /**
   * Returns log fugacity without the common pressure factor.
   *
   * @param phase equilibrium phase
   * @param component component index
   * @return log mole fraction plus log fugacity coefficient
   */
  private static double logFugacity(PhaseInterface phase, int component) {
    double coefficient = phase.getComponent(component).getFugacityCoefficient();
    assertTrue(Double.isFinite(coefficient) && coefficient > 0.0);
    return Math.log(phase.getComponent(component).getx()) + Math.log(coefficient);
  }
}
