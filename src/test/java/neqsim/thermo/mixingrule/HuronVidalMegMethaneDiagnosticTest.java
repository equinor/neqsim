package neqsim.thermo.mixingrule;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import neqsim.thermo.ThermodynamicModelTest;
import neqsim.thermo.component.ComponentEosInterface;
import neqsim.thermo.phase.PhaseInterface;
import neqsim.thermo.phase.PhaseType;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;
import neqsim.thermodynamicoperations.ThermodynamicOperations;

/**
 * Numerically qualifies a staged SRK-HV MEG/methane diagnostic without activating a physical calibration.
 *
 * <p>
 * Folas thesis Table 6.2 supplies the MEG-first tuple (181 K, 2274 K, alpha 0.07), with zero temperature offsets. The
 * paired Derawi MEG Mathias-Copeman inputs are retained. Methane inputs are declared library reference values, not
 * recovered original-author inputs. Six liquid methane observations and compiler uncertainties are retained from
 * campaign receipt 6713997329, together with an independent cubic-HV calculation. Original journal table images and
 * author uncertainty remain unresolved. Gas MEG is a prediction; no reverse-solubility observation is invented.
 * </p>
 *
 * @author Even Solbraa
 * @version 1.0
 */
class HuronVidalMegMethaneDiagnosticTest extends neqsim.NeqSimTest {
  /** Resource containing observations and separately identified independent predictions. */
  private static final String RESOURCE = "/neqsim/thermo/mixingrule/hv-meg-methane/declared-input-reference.csv";

  /**
   * Requires independent parity and native checks while preserving all physical comparison errors.
   *
   * @throws Exception if the resource, flash or report fails
   */
  @Test
  void declaredInputAssessmentMatchesIndependentEquations() throws Exception {
    Path directory = Paths.get("target", "hv-meg-methane");
    Files.createDirectories(directory);
    try (PrintWriter out = new PrintWriter(
        Files.newBufferedWriter(directory.resolve("assessment.csv"), StandardCharsets.UTF_8))) {
      out.println("MC,T_K,P_bara,observed_x_CH4,compiler_U95_x_CH4,native_x_CH4,predicted_y_MEG,"
          + "relative_error,inside_compiler_U95,inside_MEG_alpha_fit,numerically_qualified");
      for (double[] row : rows()) {
        for (boolean mathiasCopeman : new boolean[] {true, false}) {
          SystemInterface fluid = fluid(row[0], row[1], 0.5, 0.5, false, mathiasCopeman);
          flashAndCheck(fluid);
          PhaseInterface gas = fluid.getPhase(PhaseType.GAS);
          PhaseInterface liquid = fluid.getPhase(0) == gas ? fluid.getPhase(1) : fluid.getPhase(0);
          double x = liquid.getComponent("methane").getx();
          double y = gas.getComponent("MEG").getx();
          int reference = mathiasCopeman ? 4 : 6;
          assertEquals(row[reference], x, Math.max(1e-11, Math.abs(row[reference]) * 5e-8),
              "Independent liquid-methane equation parity, not experimental acceptance");
          assertEquals(row[reference + 1], y, Math.max(1e-13, Math.abs(row[reference + 1]) * 5e-8),
              "Independent unmeasured vapor-MEG prediction");
          out.printf(Locale.ROOT, "%b,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%.17g,%b,%b,true%n", mathiasCopeman, row[0],
              row[1], row[2], row[3], x, y, x / row[2] - 1.0, Math.abs(x - row[2]) <= row[3],
              row[0] / 720.0 >= 0.4 && row[0] / 720.0 <= 0.9);
        }
      }
      assertTrue(!out.checkError(), "Diagnostic output must retain physical errors");
    }
  }

  /**
   * Checks returned, repeated, changed, reverse-order and scaled-amount states against fresh controls.
   *
   * @throws Exception if the reference resource cannot be read
   */
  @Test
  void declaredProfilePreservesOrderAmountAndHistory() throws Exception {
    List<double[]> points = rows();
    for (boolean reverse : new boolean[] {false, true}) {
      for (double amount : new double[] {1e-6, 1.0, 1000.0}) {
        SystemInterface retained = fluid(points.get(0)[0], points.get(0)[1], amount / 2, amount / 2, reverse, true);
        for (int index : new int[] {0, 0, 2, 5, 0}) {
          double[] row = points.get(index);
          retained.setTemperature(row[0]);
          retained.setPressure(row[1]);
          flashAndCheck(retained);
          SystemInterface fresh = fluid(row[0], row[1], 0.5, 0.5, false, true);
          flashAndCheck(fresh);
          for (int phase = 0; phase < fresh.getNumberOfPhases(); phase++) {
            PhaseType type = fresh.getPhase(phase).getType();
            for (String component : new String[] {"MEG", "methane"}) {
              double expected = fresh.getPhase(type).getComponent(component).getx();
              assertEquals(expected, retained.getPhase(type).getComponent(component).getx(),
                  Math.max(1e-12, 5e-8 * Math.abs(expected)), "Fresh/history/order/amount");
            }
          }
        }
      }
    }
  }

  /**
   * Checks both alpha controls against independently constructed fixed-composition gas and liquid roots.
   *
   * @throws Exception if the coordinate resource cannot be read
   */
  @Test
  void temperatureDerivativesResolveOnIndependentFreshRoots() throws Exception {
    for (double[] row : rows()) {
      for (boolean mathiasCopeman : new boolean[] {true, false}) {
        SystemInterface source = fluid(row[0], row[1], 0.5, 0.5, false, mathiasCopeman);
        flashAndCheck(source);
        for (int index = 0; index < source.getNumberOfPhases(); index++) {
          PhaseInterface phase = source.getPhase(index);
          for (String name : new String[] {"MEG", "methane"}) {
            double analytic = phase.getComponent(name).getdfugdt();
            double coarse = temperatureDifference(phase, name, 0.001, mathiasCopeman);
            double fine = temperatureDifference(phase, name, 0.0005, mathiasCopeman);
            double tolerance = 1e-6 * Math.max(1.0, Math.abs(analytic));
            assertEquals(coarse, fine, tolerance, "Independent temperature refinement");
            assertEquals(analytic, fine, tolerance, "Independent temperature derivative");
          }
        }
      }
    }
  }

  /**
   * Computes a central derivative without flashing, reusing a clone or changing the reference phase.
   *
   * @param source phase whose composition and root are fixed
   * @param name component whose log fugacity coefficient is differentiated
   * @param step displacement in kelvin
   * @param mathiasCopeman MEG alpha choice
   * @return derivative per kelvin
   */
  private static double temperatureDifference(PhaseInterface source, String name, double step, boolean mathiasCopeman) {
    double[] values = new double[2];
    for (int side = 0; side < 2; side++) {
      SystemInterface trial = fluid(source.getTemperature() + (side == 0 ? -step : step), source.getPressure(),
          source.getComponent("MEG").getx(), source.getComponent("methane").getx(), false, mathiasCopeman);
      trial.setForceSinglePhase(source.getType() == PhaseType.GAS ? PhaseType.GAS : PhaseType.LIQUID);
      trial.init(3);
      values[side] = trial.getPhase(0).getComponent(name).getLogFugacityCoefficient();
    }
    return (values[1] - values[0]) / (2.0 * step);
  }

  /** Requires dormant inhibitor/water tuples to be inert and selected-pair deactivation to reduce to rule2. */
  @Test
  void onlyDeclaredMegMethanePairChangesFiveComponentForcedRoots() {
    for (boolean reverse : new boolean[] {false, true}) {
      for (PhaseType type : new PhaseType[] {PhaseType.GAS, PhaseType.LIQUID}) {
        String[] names = {"MEG", "methane", "water", "methanol", "ethane"};
        double[] amounts = {0.45, 0.40, 0.05, 0.04, 0.06};
        SystemInterface selected = new SystemSrkEos(298.25, 100.2);
        for (int count = 0; count < names.length; count++) {
          int index = reverse ? names.length - count - 1 : count;
          selected.addComponent(names[index], amounts[index]);
        }
        configure(selected, true);
        selected.setForceSinglePhase(type);
        selected.init(3);
        assertNumericalOnNormalizedAmount(selected);
        SystemInterface dormant = SerializationUtils.clone(selected);
        SystemInterface deactivated = SerializationUtils.clone(selected);
        SystemInterface classical = SerializationUtils.clone(selected);
        classical.setMixingRule(2);
        for (int phase = 0; phase < selected.getMaxNumberOfPhases(); phase++) {
          HVMixingRulesInterface changed = (HVMixingRulesInterface) dormant.getPhase(phase).getMixingRule();
          HVMixingRulesInterface disabled = (HVMixingRulesInterface) deactivated.getPhase(phase).getMixingRule();
          int meg = selected.getPhase(phase).getComponent("MEG").getComponentNumber();
          int methane = selected.getPhase(phase).getComponent("methane").getComponentNumber();
          disabled.setClassicOrHV(meg, methane, "Classic");
          disabled.setClassicOrHV(methane, meg, "Classic");
          for (int first = 0; first < names.length; first++) {
            for (int second = 0; second < names.length; second++) {
              if (first != second && !((first == meg && second == methane) || (first == methane && second == meg))) {
                // Synthetic sentinels exercise dormancy only; they are neither measured nor fitted parameters.
                changed.setHVDijParameter(first, second, 1234.0 + first * 100 + second);
                changed.setHVDijTParameter(first, second, -0.2);
              }
            }
          }
        }
        dormant.init(3);
        deactivated.init(3);
        classical.init(3);
        for (String name : names) {
          assertEquals(selected.getPhase(0).getComponent(name).getLogFugacityCoefficient(),
              dormant.getPhase(0).getComponent(name).getLogFugacityCoefficient(), 1e-12, "Dormant tuples inert");
          assertEquals(classical.getPhase(0).getComponent(name).getLogFugacityCoefficient(),
              deactivated.getPhase(0).getComponent(name).getLogFugacityCoefficient(), 1e-10, "CLASSICAL reduction");
        }
      }
    }
  }

  /**
   * Reads literal retained observations without fitting or relabeling gas-side data.
   *
   * @return six source-coordinate rows
   * @throws Exception if the resource cannot be read or parsed
   */
  private static List<double[]> rows() throws Exception {
    List<double[]> result = new ArrayList<double[]>();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
        HuronVidalMegMethaneDiagnosticTest.class.getResourceAsStream(RESOURCE), StandardCharsets.UTF_8))) {
      reader.readLine();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] values = line.split(",", -1);
        double[] row = new double[values.length];
        for (int i = 0; i < values.length; i++) {
          row[i] = Double.parseDouble(values[i]);
        }
        result.add(row);
      }
    }
    assertEquals(6, result.size());
    return result;
  }

  /**
   * Constructs an independently configured test-local profile with an explicit synthetic feed.
   *
   * @param temperature temperature in kelvin
   * @param pressure absolute pressure in bara
   * @param megMoles MEG amount in mol
   * @param methaneMoles methane amount in mol
   * @param reverse whether methane is added first
   * @param mathiasCopeman whether to use source-paired MEG alpha or its declared Soave control
   * @return configured SRK fluid without flashing
   */
  static SystemInterface fluid(double temperature, double pressure, double megMoles, double methaneMoles,
      boolean reverse, boolean mathiasCopeman) {
    SystemInterface fluid = new SystemSrkEos(temperature, pressure);
    fluid.addComponent(reverse ? "methane" : "MEG", reverse ? methaneMoles : megMoles);
    fluid.addComponent(reverse ? "MEG" : "methane", reverse ? megMoles : methaneMoles);
    configure(fluid, mathiasCopeman);
    return fluid;
  }

  /**
   * Installs the complete binary tuple while explicitly retaining CLASSICAL treatment elsewhere.
   *
   * @param fluid independently configured test fluid
   * @param mathiasCopeman MEG alpha choice
   */
  private static void configure(SystemInterface fluid, boolean mathiasCopeman) {
    fluid.setMixingRule(4);
    fluid.setMultiPhaseCheck(true);
    int megIndex = fluid.getPhase(0).getComponent("MEG").getComponentNumber();
    int methaneIndex = fluid.getPhase(0).getComponent("methane").getComponentNumber();
    for (int i = 0; i < fluid.getMaxNumberOfPhases(); i++) {
      PhaseInterface phase = fluid.getPhase(i);
      ComponentEosInterface meg = (ComponentEosInterface) phase.getComponent("MEG");
      meg.setTC(720.0);
      meg.setPC(82.0);
      meg.setAcentricFactor(0.5347);
      meg.setAttractiveTerm(mathiasCopeman ? 4 : 0);
      if (mathiasCopeman) {
        meg.getAttractiveTerm().setParameters(0, 1.1121);
        meg.getAttractiveTerm().setParameters(1, 0.9679);
        meg.getAttractiveTerm().setParameters(2, -1.4200);
      }
      ComponentEosInterface methane = (ComponentEosInterface) phase.getComponent("methane");
      methane.setTC(190.56);
      methane.setPC(45.99);
      methane.setAcentricFactor(0.0115);
      methane.setAttractiveTerm(0);
      HVMixingRulesInterface rule = (HVMixingRulesInterface) phase.getMixingRule();
      for (int first = 0; first < fluid.getNumberOfComponents(); first++) {
        for (int second = 0; second < fluid.getNumberOfComponents(); second++) {
          rule.setClassicOrHV(first, second, "Classic");
        }
      }
      rule.setClassicOrHV(megIndex, methaneIndex, "HV");
      rule.setClassicOrHV(methaneIndex, megIndex, "HV");
      rule.setHValphaParameter(megIndex, methaneIndex, 0.07);
      rule.setHVDijParameter(megIndex, methaneIndex, 181.0);
      rule.setHVDijParameter(methaneIndex, megIndex, 2274.0);
      rule.setHVDijTParameter(megIndex, methaneIndex, 0.0);
      rule.setHVDijTParameter(methaneIndex, megIndex, 0.0);
    }
  }

  /**
   * Asserts native identities, numerical derivatives, topology and synthetic-feed equilibrium closure.
   *
   * @param fluid configured fluid to flash and qualify
   */
  private static void flashAndCheck(SystemInterface fluid) {
    new ThermodynamicOperations(fluid).TPflash();
    fluid.init(3);
    assertEquals(2, fluid.getNumberOfPhases());
    assertTrue(fluid.hasPhaseType(PhaseType.GAS));
    byte[] before = SerializationUtils.serialize(fluid);
    assertNumericalOnNormalizedAmount(fluid);
    assertArrayEquals(before, SerializationUtils.serialize(fluid));
    double betaSum = 0.0;
    for (int phase = 0; phase < 2; phase++) {
      betaSum += fluid.getBeta(phase);
      double composition = 0.0;
      for (int component = 0; component < 2; component++) {
        composition += fluid.getPhase(phase).getComponent(component).getx();
      }
      assertEquals(1.0, composition, 1e-12);
    }
    assertEquals(1.0, betaSum, 1e-12);
    for (String component : new String[] {"MEG", "methane"}) {
      double recovered = 0.0;
      double[] logFugacity = new double[2];
      for (int phase = 0; phase < 2; phase++) {
        PhaseInterface state = fluid.getPhase(phase);
        recovered += fluid.getBeta(phase) * state.getComponent(component).getx();
        logFugacity[phase] = state.getComponent(component).getLogFugacityCoefficient()
            + Math.log(state.getComponent(component).getx());
      }
      assertEquals(fluid.getPhase(0).getComponent(component).getz(), recovered, 1e-10);
      assertEquals(logFugacity[0], logFugacity[1], 1e-8);
    }
  }

  /**
   * Checks homogeneous mole derivatives and numerical stencils on a detached one-mole basis.
   *
   * <p>
   * At a total amount of 1e-6 mol, the checker's progressively halved gas trace stencils amplify log-fugacity roundoff.
   * At 273.25 K/99.8 bara the final estimates 62.89311579 and 62.89593135 do not resolve the analytic 62.89384402 per
   * mol. Normalization keeps compositions, fugacities and dimensionless n-phase times d(log phi)/dn invariant; these
   * invariants are asserted before applying the unchanged checker and tolerance. Neither the source fluid nor the
   * public diagnostic implementation is modified. At 323.45 K/100.8 bara and 1e-6 mol, the raw absolute Hessian
   * residual is 2.98e-8 per mol (relative 5.2e-13), above the checker's absolute 1e-8 gate. Dimensionless original
   * symmetry and the unchanged native checks on the detached unit basis are both required.
   * </p>
   *
   * @param source initialized source at its original amount
   */
  private static void assertNumericalOnNormalizedAmount(SystemInterface source) {
    SystemInterface normalized = SerializationUtils.clone(source);
    normalized.setTotalNumberOfMoles(1.0);
    normalized.init(3);
    for (int phase = 0; phase < source.getNumberOfPhases(); phase++) {
      PhaseInterface original = source.getPhase(phase);
      PhaseInterface trial = normalized.getPhase(phase);
      assertEquals(source.getBeta(phase), normalized.getBeta(phase), 1e-12);
      for (int first = 0; first < source.getNumberOfComponents(); first++) {
        for (int second = first + 1; second < source.getNumberOfComponents(); second++) {
          double symmetry = original.getNumberOfMolesInPhase()
              * (original.getComponent(first).getdfugdn(second) - original.getComponent(second).getdfugdn(first));
          assertEquals(0.0, symmetry, 1e-10, "Dimensionless original-amount Hessian symmetry");
        }
      }
      for (int component = 0; component < source.getNumberOfComponents(); component++) {
        assertEquals(original.getComponent(component).getx(), trial.getComponent(component).getx(), 1e-12);
        assertEquals(original.getComponent(component).getLogFugacityCoefficient(),
            trial.getComponent(component).getLogFugacityCoefficient(), 1e-11);
        for (int variable = 0; variable < source.getNumberOfComponents(); variable++) {
          double expected = original.getNumberOfMolesInPhase() * original.getComponent(component).getdfugdn(variable);
          double actual = trial.getNumberOfMolesInPhase() * trial.getComponent(component).getdfugdn(variable);
          assertEquals(expected, actual, 1e-8 * Math.max(1.0, Math.max(Math.abs(expected), Math.abs(actual))),
              "Amount-homogeneous mole derivative");
        }
      }
    }
    ThermodynamicModelTest numerical = new ThermodynamicModelTest(normalized);
    numerical.setMaxError(1e-8);
    assertTrue(numerical.checkFugacityCoefficients(), "Unit-basis residual Gibbs identity");
    assertTrue(numerical.checkFugacityCoefficientsDn(), "Unit-basis Gibbs-Duhem identity");
    assertTrue(numerical.checkFugacityCoefficientsDn2(), "Unit-basis Hessian symmetry");
    assertTrue(numerical.checkFugacityCoefficientsDP(), "Unit-basis pressure identity");
    assertTrue(numerical.checkFugacityCoefficientsDT(), "Unit-basis temperature identity");
    numerical.setMaxError(1e-6);
    assertTrue(numerical.checkNumerically(), "Detached unit-amount refined stencils");
  }
}
