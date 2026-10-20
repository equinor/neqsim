package neqsim.process.equipment.compressor.seal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import neqsim.thermo.system.SystemGERG2008Eos;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for the tooth-by-tooth Neumann method of {@link LabyrinthSealFlowCalculator}.
 *
 * <p>
 * The workbook reference uses GERG-2008 properties. Most tests use SRK, which is within 0.01 % of GERG-2008 for these
 * low-pressure lean-gas cases and much faster for the repeated PH flashes; one test uses GERG-2008 directly.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 */
public class LabyrinthSealNeumannTest extends neqsim.NeqSimTest {
  private static final String FIXTURE = "/neqsim/process/equipment/compressor/seal/labyrinth_excel_reference.json";
  private static final JsonObject REFERENCE = loadReference();
  /**
   * Relative tolerance against the workbook, about twice the largest deviation found (0.021 % at 20 teeth). NeqSim
   * solves the tooth-by-tooth model exactly (PH flash at constant inlet enthalpy, converged flow), whereas the workbook
   * uses a 0.05 % secant temperature search, lets the reference enthalpy drift and stops its spline iteration after 11
   * passes.
   */
  private static final double NEUMANN_TOLERANCE = 5.0e-4;

  /**
   * Loads the workbook reference cases.
   *
   * @return fixture root object
   */
  private static JsonObject loadReference() {
    try (InputStream stream = LabyrinthSealNeumannTest.class.getResourceAsStream(FIXTURE)) {
      Objects.requireNonNull(stream, "Missing labyrinth reference fixture " + FIXTURE);
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
      }
    } catch (IOException ex) {
      throw new IllegalStateException("Failed to read labyrinth reference fixture", ex);
    }
  }

  /**
   * Returns a reference case by id.
   *
   * @param id case id
   * @return case object
   */
  private static JsonObject referenceCase(String id) {
    for (JsonElement element : REFERENCE.getAsJsonArray("cases")) {
      JsonObject candidate = element.getAsJsonObject();
      if (id.equals(candidate.get("id").getAsString())) {
        return candidate;
      }
    }
    throw new IllegalArgumentException("Unknown reference case " + id);
  }

  /**
   * Fills a fluid with the case composition.
   *
   * @param testCase reference case
   * @param fluid empty fluid
   * @return the filled fluid
   */
  private static SystemInterface fill(JsonObject testCase, SystemInterface fluid) {
    JsonObject composition = testCase.getAsJsonObject("inputs").getAsJsonObject("composition_mole_fraction");
    for (Map.Entry<String, JsonElement> entry : composition.entrySet()) {
      fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds a Neumann calculator for a reference case.
   *
   * @param testCase reference case
   * @param fluid fluid
   * @return configured calculator (not yet run)
   */
  private static LabyrinthSealFlowCalculator neumann(JsonObject testCase, SystemInterface fluid) {
    JsonObject in = testCase.getAsJsonObject("inputs");
    LabyrinthSealGeometry geometry = new LabyrinthSealGeometry();
    geometry.setShaftDiameter(in.get("shaft_diameter_mm").getAsDouble(), "mm");
    geometry.setToothHeight(in.get("tooth_height_mm").getAsDouble(), "mm");
    geometry.setToothPitch(in.get("tooth_pitch_mm").getAsDouble(), "mm");
    geometry.setTipWidth(in.get("tip_width_mm").getAsDouble(), "mm");
    geometry.setRadialClearance(in.get("radial_clearance_mm").getAsDouble(), "mm");
    geometry.setNumberOfTeeth(in.get("number_of_teeth").getAsInt());
    LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(fluid, geometry);
    calc.setInletPressure(in.get("inlet_pressure_barg").getAsDouble(), "barg");
    calc.setInletTemperature(in.get("inlet_temperature_C").getAsDouble(), "C");
    calc.setOutletPressure(in.get("outlet_pressure_barg").getAsDouble(), "barg");
    calc.setMethod(LabyrinthFlowMethod.NEUMANN);
    return calc;
  }

  /**
   * Builds an SRK Neumann calculator for a reference case.
   *
   * @param id case id
   * @return configured calculator (not yet run)
   */
  private static LabyrinthSealFlowCalculator srkNeumann(String id) {
    JsonObject testCase = referenceCase(id);
    return neumann(testCase, fill(testCase, new SystemSrkEos(288.15, 1.01325)));
  }

  /**
   * Returns the workbook Neumann flow, NaN when the workbook returned an error.
   *
   * @param testCase reference case
   * @return mass flow in kg/s
   */
  private static double excelNeumann(JsonObject testCase) {
    JsonElement value = testCase.getAsJsonObject("excel").getAsJsonObject("mass_flow_kg_s").get("NEUMANN");
    return value == null || value.isJsonNull() ? Double.NaN : value.getAsDouble();
  }

  /** Neumann reproduces the workbook over the single-phase sweep. */
  @Test
  void neumannMatchesWorkbookAcrossSweep() {
    List<String> failures = new ArrayList<String>();
    int compared = 0;
    for (JsonElement element : REFERENCE.getAsJsonArray("cases")) {
      JsonObject testCase = element.getAsJsonObject();
      double expected = excelNeumann(testCase);
      if (Double.isNaN(expected)) {
        continue;
      }
      LabyrinthSealFlowCalculator calc = neumann(testCase, fill(testCase, new SystemSrkEos(288.15, 1.01325)));
      calc.calculate();
      if (calc.hasLiquidAtInlet()) {
        continue;
      }
      compared++;
      double relativeError = Math.abs(calc.getMassFlowRate("kg/sec") - expected) / expected;
      if (!(relativeError <= NEUMANN_TOLERANCE) || !calc.isWithinValidityRange()) {
        failures
            .add(testCase.get("id").getAsString() + " rel.err=" + relativeError + " " + calc.getValidityViolations());
      }
    }
    assertTrue(failures.isEmpty(), "Workbook mismatches: " + failures);
    assertTrue(compared >= 15, "Too few single-phase cases compared: " + compared);
  }

  /** A GERG-2008 fluid reproduces the workbook worked case. */
  @Test
  void gergWorkedCaseMatchesWorkbook() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator calc = neumann(worked, fill(worked, new SystemGERG2008Eos(288.15, 1.01325)));
    calc.calculate();
    assertEquals(excelNeumann(worked), calc.getMassFlowRate("kg/sec"), NEUMANN_TOLERANCE * excelNeumann(worked));
    assertTrue(calc.isWithinValidityRange(), calc.getValidityViolations().toString());
  }

  /** The tooth profile runs from inlet to outlet pressure, falling at every tooth, at nearly constant temperature. */
  @Test
  void toothProfileIsConsistent() {
    LabyrinthSealFlowCalculator calc = srkNeumann("worked_case");
    calc.calculate();
    double[] pressures = calc.getToothPressures("bara");
    double[] temperatures = calc.getToothTemperatures("C");
    double[] densities = calc.getToothDensities();
    assertEquals(6, pressures.length);
    assertEquals(6, temperatures.length);
    assertEquals(6, densities.length);
    assertEquals(2.765 + 1.01325, pressures[0], 1.0e-12);
    assertEquals(1.5417 + 1.01325, pressures[5], 1.0e-6 * 2.555);
    assertEquals(86.12, temperatures[0], 1.0e-9);
    for (int i = 1; i < pressures.length; i++) {
      assertTrue(pressures[i] < pressures[i - 1], "pressure must fall at tooth " + i);
      assertTrue(densities[i] < densities[i - 1], "density must fall at tooth " + i);
      assertTrue(temperatures[i] <= temperatures[i - 1] + 1.0e-6, "Joule-Thomson cooling at tooth " + i);
      assertEquals(86.12, temperatures[i], 1.0, "isenthalpic expansion of a lean gas cools little");
    }
    assertEquals(calc.getToothPressures("bara")[5] - 1.01325, calc.getToothPressures("barg")[5], 1.0e-12);
    assertFalse(calc.isChoked());
    assertTrue(calc.getCriticalMassFlowRate("kg/sec") > calc.getMassFlowRate("kg/sec"));
  }

  /** Two teeth vented to atmosphere choke: the result is the choked flow and the last tooth stays above the outlet. */
  @Test
  void chokedCaseReturnsChokedFlow() {
    LabyrinthSealFlowCalculator calc = srkNeumann("n2_to_atm_choked");
    calc.calculate();
    assertTrue(calc.isChoked());
    assertTrue(calc.isWithinValidityRange(), calc.getValidityViolations().toString());
    double choked = calc.getMassFlowRate("kg/sec");
    assertTrue(choked > 0.0 && !Double.isInfinite(choked));
    assertEquals(calc.getCriticalMassFlowRate("kg/sec"), choked, 1.0e-15);
    double[] pressures = calc.getToothPressures("bara");
    assertEquals(3, pressures.length);
    assertTrue(pressures[2] > 1.01325, "choked: pressure after the last tooth is above the outlet pressure");

    LabyrinthSealFlowCalculator lessDrop = srkNeumann("n2_to_atm_choked");
    lessDrop.setOutletPressure(1.0, "barg");
    lessDrop.calculate();
    assertTrue(lessDrop.getMassFlowRate("kg/sec") <= choked * (1.0 + 1.0e-6));
  }

  /** Neumann flow vanishes with the pressure drop, rises with it and falls with the number of teeth. */
  @Test
  void neumannFollowsPhysicalTrends() {
    LabyrinthSealFlowCalculator tiny = srkNeumann("worked_case");
    tiny.setOutletPressure(2.765 - 1.0e-6, "barg");
    tiny.calculate();
    LabyrinthSealFlowCalculator base = srkNeumann("worked_case");
    base.calculate();
    LabyrinthSealFlowCalculator larger = srkNeumann("worked_case");
    larger.setOutletPressure(0.8, "barg");
    larger.calculate();
    assertTrue(tiny.getMassFlowRate("kg/sec") < 1.0e-2 * base.getMassFlowRate("kg/sec"));
    assertTrue(larger.getMassFlowRate("kg/sec") > base.getMassFlowRate("kg/sec"));

    double previous = Double.MAX_VALUE;
    for (int teeth : new int[] {3, 5, 8, 12}) {
      LabyrinthSealFlowCalculator calc = srkNeumann("worked_case");
      LabyrinthSealGeometry geometry = calc.getGeometry();
      geometry.setNumberOfTeeth(teeth);
      calc.setGeometry(geometry);
      calc.calculate();
      assertTrue(calc.getMassFlowRate("kg/sec") < previous, "more teeth must reduce leakage, n=" + teeth);
      assertEquals(teeth + 1, calc.getToothPressures("bara").length);
      previous = calc.getMassFlowRate("kg/sec");
    }
  }

  /** The tooth profile is only defined for Neumann; the closed-form methods return empty profiles. */
  @Test
  void profileIsEmptyForClosedFormMethods() {
    LabyrinthSealFlowCalculator calc = srkNeumann("worked_case");
    calc.setMethod(LabyrinthFlowMethod.KEARTON);
    calc.calculate();
    assertEquals(0, calc.getToothPressures("bara").length);
    assertEquals(0, calc.getToothTemperatures("K").length);
    assertEquals(0, calc.getToothDensities().length);
  }

  /** With water condensed at the inlet, Neumann marches the gas phase only and still returns a leakage. */
  @Test
  void liquidAtInletUsesGasPhase() {
    LabyrinthSealFlowCalculator calc = srkNeumann("cold_20C");
    calc.calculate();
    assertTrue(calc.hasLiquidAtInlet());
    assertTrue(calc.isWithinValidityRange(), calc.getValidityViolations().toString());
    double flow = calc.getMassFlowRate("kg/sec");
    assertTrue(flow > 0.0 && !Double.isInfinite(flow));
    assertEquals(1.5417 + 1.01325, calc.getToothPressures("bara")[5], 1.0e-6 * 2.555);
  }

  /** The JSON summary carries the tooth profile for Neumann. */
  @Test
  void jsonContainsToothProfile() {
    LabyrinthSealFlowCalculator calc = srkNeumann("worked_case");
    calc.calculate();
    JsonObject json = JsonParser.parseString(calc.toJson()).getAsJsonObject();
    assertEquals("NEUMANN", json.get("method").getAsString());
    assertEquals(6, json.getAsJsonArray("toothPressures_bara").size());
    assertEquals(6, json.getAsJsonArray("toothTemperatures_C").size());
    assertFalse(calc.toJson().contains("NaN"));
  }

  /**
   * Builds the worked-case geometry with a given number of teeth.
   *
   * @param teeth number of teeth
   * @return geometry
   */
  private static LabyrinthSealGeometry workedGeometry(int teeth) {
    LabyrinthSealGeometry geometry = new LabyrinthSealGeometry();
    geometry.setShaftDiameter(108.566, "mm");
    geometry.setToothPitch(2.0, "mm");
    geometry.setTipWidth(0.3, "mm");
    geometry.setRadialClearance(0.2286, "mm");
    geometry.setNumberOfTeeth(teeth);
    return geometry;
  }

  /**
   * Flashes a fluid at a state and initializes its properties.
   *
   * @param fluid fluid to flash in place
   * @param temperatureK temperature in K
   * @param pressureBara pressure in bara
   * @return the flashed fluid
   */
  private static SystemInterface flashed(SystemInterface fluid, double temperatureK, double pressureBara) {
    fluid.setTemperature(temperatureK);
    fluid.setPressure(pressureBara);
    new neqsim.thermodynamicoperations.ThermodynamicOperations(fluid).TPflash();
    fluid.initProperties();
    return fluid;
  }

  /**
   * Returns the relative residual of the tooth equation p_{i+1}^2 = p_i^2 - (m/(Cf u A))^2 p_i/rho_i for one tooth.
   *
   * @param march march to check
   * @param tooth tooth index, 0 for the first tooth
   * @param massFlow mass flow in kg/s
   * @param geometry seal geometry
   * @return |residual| / p_i^2
   */
  private static double toothResidual(NeumannLabyrinthSolver.March march, int tooth, double massFlow,
      LabyrinthSealGeometry geometry) {
    int n = geometry.getNumberOfTeeth();
    double ratio = 1.0 + 16.6 * geometry.getRadialClearance() / geometry.getToothPitch();
    double alpha = 1.0 - 1.0 / (ratio * ratio);
    double carryOver = tooth == 0 ? 1.0 : Math.sqrt(n / (n * (1.0 - alpha) + alpha));
    double upstream = march.pressurePa[tooth];
    double downstream = march.pressurePa[tooth + 1];
    double k = march.kappa[tooth];
    double beta = Math.max(Math.pow(upstream / downstream, (k - 1.0) / k) - 1.0, 0.0);
    double flowCoefficient = Math.PI / (Math.PI + 2.0 - 5.0 * beta + 2.0 * beta * beta);
    double flux = massFlow / (flowCoefficient * carryOver * geometry.getClearanceArea());
    double residual = downstream * downstream - upstream * upstream + flux * flux * upstream / march.density[tooth];
    return Math.abs(residual) / (upstream * upstream);
  }

  /**
   * High isentropic exponent (argon, kappa about 5/3): the tooth pressure solves the tooth equation exactly. A plain
   * fixed-point iteration oscillates between the choke pressure and about 0.62 p_i when the exact ratio is 0.55 (review
   * finding on #4491).
   */
  @Test
  void toothPressureSolvesToothEquationForHighKappa() {
    SystemInterface argon = new SystemSrkEos(300.0, 3.0);
    argon.addComponent("argon", 1.0);
    argon.setMixingRule("classic");
    flashed(argon, 300.0, 3.0);
    double density = argon.getPhase(0).getDensity("kg/m3");
    double kappa = argon.getPhase(0).getKappa();
    assertTrue(kappa > 1.6, "argon kappa " + kappa);
    LabyrinthSealGeometry geometry = workedGeometry(3);
    NeumannLabyrinthSolver solver = new NeumannLabyrinthSolver(argon, density, kappa, geometry);

    double p0 = 3.0e5;
    for (double targetRatio : new double[] {0.55, 0.53, 0.6, 0.7, 0.9}) {
      double beta = Math.pow(targetRatio, -(kappa - 1.0) / kappa) - 1.0;
      double flowCoefficient = Math.PI / (Math.PI + 2.0 - 5.0 * beta + 2.0 * beta * beta);
      double massFlow = flowCoefficient * geometry.getClearanceArea()
          * Math.sqrt((p0 * p0 - targetRatio * targetRatio * p0 * p0) * density / p0);
      NeumannLabyrinthSolver.March march = solver.march(massFlow);
      assertEquals(null, march.failure, "ratio " + targetRatio);
      assertEquals(targetRatio, march.pressurePa[1] / p0, 1.0e-9, "first-tooth ratio");
      int lastFilled = 0;
      while (lastFilled < geometry.getNumberOfTeeth() && march.pressurePa[lastFilled + 1] > 0.0) {
        lastFilled++;
      }
      int solvedTeeth = march.choked ? lastFilled - 1 : lastFilled;
      for (int tooth = 0; tooth < solvedTeeth; tooth++) {
        assertTrue(toothResidual(march, tooth, massFlow, geometry) < 1.0e-10,
            "tooth " + tooth + " at ratio " + targetRatio);
      }
    }

    LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(argon, geometry);
    calc.setInletPressure(3.0, "bara");
    calc.setInletTemperature(300.0, "K");
    calc.setOutletPressure(1.01325, "bara");
    calc.setMethod(LabyrinthFlowMethod.NEUMANN);
    calc.calculate();
    assertTrue(calc.isWithinValidityRange(), calc.getValidityViolations().toString());
    assertTrue(calc.getMassFlowRate("kg/sec") > 0.0);
  }

  /**
   * A flash failure during the lazy critical-flow search is reported as a validity violation, also in toJson (review
   * finding on #4491).
   */
  @Test
  void criticalFlowSearchFailureIsReported() {
    JsonObject worked = referenceCase("worked_case");
    SystemInterface fluid = fill(worked, new SystemSrkEos(288.15, 1.01325));
    LabyrinthSealFlowCalculator calc = neumann(worked, fluid);
    calc.setOutletPressure(2.6, "barg");
    calc.calculate();
    assertTrue(calc.isWithinValidityRange(), calc.getValidityViolations().toString());

    SystemInterface inlet = flashed(fluid.clone(), 86.12 + 273.15, 2.765 + 1.01325);
    final NeumannLabyrinthSolver.IsenthalpicFlash exact = NeumannLabyrinthSolver.defaultIsenthalpicFlash(inlet);
    final double failBelowPa = 1.5e5;
    NeumannLabyrinthSolver.IsenthalpicFlash failingAtLowPressure = new NeumannLabyrinthSolver.IsenthalpicFlash() {
      @Override
      public double[] state(double pressurePa, double warmStartK) {
        if (pressurePa < failBelowPa) {
          throw new IllegalStateException("injected flash failure");
        }
        return exact.state(pressurePa, warmStartK);
      }
    };
    calc.useNeumannSolver(new NeumannLabyrinthSolver(inlet, calc.getInletDensity(), calc.getInletIsentropicExponent(),
        calc.getGeometry(), failingAtLowPressure));
    calc.calculate();
    assertTrue(calc.isWithinValidityRange(), "the leakage solve stays above the injected failure pressure");
    double leakage = calc.getMassFlowRate("kg/sec");
    assertTrue(leakage > 0.0);

    JsonObject json = JsonParser.parseString(calc.toJson()).getAsJsonObject();
    assertTrue(json.get("criticalMassFlowRate_kg_s").isJsonNull());
    assertFalse(json.get("withinValidityRange").getAsBoolean(), json.toString());
    assertTrue(Double.isNaN(calc.getCriticalMassFlowRate("kg/sec")));
    assertFalse(calc.isWithinValidityRange());
    String violation = calc.getValidityViolations().get(0);
    assertTrue(violation.startsWith("NEUMANN") && violation.contains("critical"), violation);
    assertTrue(violation.contains("injected flash failure"), violation);
    assertEquals(leakage, calc.getMassFlowRate("kg/sec"), 0.0, "leakage result is unchanged");
  }

  /**
   * A single-phase GERG-2008 inlet can become two-phase after an expansion; the dedicated GERG PH flash then stops
   * after its TP flash, so the state must come from the general PH flash (review finding on #4491).
   */
  @Test
  void gergExpansionIntoTwoPhaseIsIsenthalpic() {
    SystemInterface gas = new SystemGERG2008Eos(300.0, 60.0);
    gas.addComponent("methane", 0.97);
    gas.addComponent("n-pentane", 0.03);
    gas.setMixingRule("classic");
    double inletTemperatureK = 273.15 + 26.5;
    flashed(gas, inletTemperatureK, 60.0);
    assertEquals(1, gas.getNumberOfPhases(), "inlet must be single-phase gas");
    double inletEnthalpy = gas.getEnthalpy();

    NeumannLabyrinthSolver.IsenthalpicFlash flash = NeumannLabyrinthSolver.defaultIsenthalpicFlash(gas);
    double[] state = flash.state(48.0e5, inletTemperatureK);

    SystemInterface reference = gas.clone();
    reference.setPressure(48.0);
    reference.setTemperature(inletTemperatureK);
    neqsim.thermodynamicoperations.ThermodynamicOperations ops = new neqsim.thermodynamicoperations.ThermodynamicOperations(
        reference);
    ops.PHflash(inletEnthalpy);
    reference.initProperties();
    assertEquals(2, reference.getNumberOfPhases(), "test premise: the expanded state is two-phase");
    assertEquals(reference.getTemperature(), state[0], 1.0e-3, "isenthalpic temperature");
    assertEquals(reference.getPhase("gas").getDensity("kg/m3"), state[1], 1.0e-6 * state[1], "gas density");
    assertTrue(state[0] < inletTemperatureK - 2.0, "expansion into the two-phase region cools the gas");
  }
}
