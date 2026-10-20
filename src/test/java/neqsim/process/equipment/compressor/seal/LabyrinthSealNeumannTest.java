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
   * Relative tolerance against the workbook. NeqSim solves the tooth-by-tooth model exactly (PH flash at constant inlet
   * enthalpy, converged flow), whereas the workbook uses a 0.05 % secant temperature search, lets the reference enthalpy
   * drift and stops its spline iteration after 11 passes.
   */
  private static final double NEUMANN_TOLERANCE = 5.0e-3;

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
        failures.add(testCase.get("id").getAsString() + " rel.err=" + relativeError + " " + calc.getValidityViolations());
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
}
