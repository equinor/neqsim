package neqsim.process.equipment.compressor.seal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * Tests for {@link LabyrinthSealFlowCalculator} with the closed-form methods (Stodola, Egli, Vermes, Kearton).
 *
 * <p>
 * Reference values come from a VBA workbook that uses GERG-2008 properties. The tests therefore use a NeqSim GERG-2008
 * fluid when comparing against the workbook.
 * </p>
 *
 * @author neqsim
 * @version 1.0
 */
public class LabyrinthSealFlowCalculatorTest extends neqsim.NeqSimTest {
  private static final String FIXTURE = "/neqsim/process/equipment/compressor/seal/labyrinth_excel_reference.json";
  private static final JsonObject REFERENCE = loadReference();
  private static final LabyrinthFlowMethod[] CLOSED_FORM = {LabyrinthFlowMethod.STODOLA, LabyrinthFlowMethod.EGLI,
      LabyrinthFlowMethod.VERMES, LabyrinthFlowMethod.KEARTON};

  /**
   * Loads the workbook reference cases.
   *
   * @return fixture root object
   */
  private static JsonObject loadReference() {
    try (InputStream stream = LabyrinthSealFlowCalculatorTest.class.getResourceAsStream(FIXTURE)) {
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
   * Builds a fluid with the case composition.
   *
   * @param testCase reference case
   * @param fluid empty fluid to fill
   * @return the filled fluid
   */
  private static SystemInterface fillComposition(JsonObject testCase, SystemInterface fluid) {
    JsonObject composition = testCase.getAsJsonObject("inputs").getAsJsonObject("composition_mole_fraction");
    for (Map.Entry<String, JsonElement> entry : composition.entrySet()) {
      fluid.addComponent(entry.getKey(), entry.getValue().getAsDouble());
    }
    fluid.setMixingRule("classic");
    return fluid;
  }

  /**
   * Builds a GERG-2008 fluid for a reference case.
   *
   * @param testCase reference case
   * @return GERG-2008 fluid
   */
  private static SystemInterface gergFluid(JsonObject testCase) {
    return fillComposition(testCase, new SystemGERG2008Eos(288.15, 1.01325));
  }

  /**
   * Builds the geometry of a reference case.
   *
   * @param testCase reference case
   * @return geometry
   */
  private static LabyrinthSealGeometry geometry(JsonObject testCase) {
    JsonObject in = testCase.getAsJsonObject("inputs");
    LabyrinthSealGeometry geometry = new LabyrinthSealGeometry();
    geometry.setShaftDiameter(in.get("shaft_diameter_mm").getAsDouble(), "mm");
    geometry.setToothHeight(in.get("tooth_height_mm").getAsDouble(), "mm");
    geometry.setToothPitch(in.get("tooth_pitch_mm").getAsDouble(), "mm");
    geometry.setTipWidth(in.get("tip_width_mm").getAsDouble(), "mm");
    geometry.setRadialClearance(in.get("radial_clearance_mm").getAsDouble(), "mm");
    geometry.setNumberOfTeeth(in.get("number_of_teeth").getAsInt());
    return geometry;
  }

  /**
   * Builds a calculator for a reference case with the given fluid.
   *
   * @param testCase reference case
   * @param fluid fluid
   * @param method flow method
   * @return configured calculator (not yet run)
   */
  private static LabyrinthSealFlowCalculator calculator(JsonObject testCase, SystemInterface fluid,
      LabyrinthFlowMethod method) {
    JsonObject in = testCase.getAsJsonObject("inputs");
    LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(fluid, geometry(testCase));
    calc.setInletPressure(in.get("inlet_pressure_barg").getAsDouble(), "barg");
    calc.setInletTemperature(in.get("inlet_temperature_C").getAsDouble(), "C");
    calc.setOutletPressure(in.get("outlet_pressure_barg").getAsDouble(), "barg");
    calc.setMethod(method);
    return calc;
  }

  /**
   * Returns the workbook mass flow for a method.
   *
   * @param testCase reference case
   * @param method flow method
   * @return mass flow in kg/s
   */
  private static double excelFlow(JsonObject testCase, LabyrinthFlowMethod method) {
    return testCase.getAsJsonObject("excel").getAsJsonObject("mass_flow_kg_s").get(method.name()).getAsDouble();
  }

  /** NeqSim GERG-2008 reproduces the workbook inlet properties used by every method. */
  @Test
  void gergInletPropertiesMatchWorkbook() {
    JsonObject worked = referenceCase("worked_case");
    JsonObject excel = worked.getAsJsonObject("excel").getAsJsonObject("inlet");
    LabyrinthSealFlowCalculator calc = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    calc.calculate();

    assertEquals(excel.get("density_kg_m3").getAsDouble(), calc.getInletDensity(), 1.0e-6 * 2.9118);
    assertEquals(excel.get("molar_mass_kg_kmol").getAsDouble(), calc.getInletMolarMass(), 1.0e-6 * 22.858);
    assertEquals(excel.get("kappa").getAsDouble(), calc.getInletIsentropicExponent(), 1.0e-6 * 1.2009);
    assertFalse(calc.hasLiquidAtInlet());
  }

  /** The four closed-form methods reproduce the workbook over the whole single-phase sweep. */
  @Test
  void closedFormMethodsMatchWorkbookAcrossSweep() {
    List<String> failures = new ArrayList<String>();
    int compared = 0;
    for (JsonElement element : REFERENCE.getAsJsonArray("cases")) {
      JsonObject testCase = element.getAsJsonObject();
      for (LabyrinthFlowMethod method : CLOSED_FORM) {
        LabyrinthSealFlowCalculator calc = calculator(testCase, gergFluid(testCase), method);
        calc.calculate();
        if (calc.hasLiquidAtInlet()) {
          continue;
        }
        compared++;
        double expected = excelFlow(testCase, method);
        double relativeError = Math.abs(calc.getMassFlowRate("kg/sec") - expected) / expected;
        if (relativeError > 1.0e-6) {
          failures.add(testCase.get("id").getAsString() + "/" + method + " rel.err=" + relativeError);
        }
      }
    }
    assertTrue(failures.isEmpty(), "Workbook mismatches: " + failures);
    assertTrue(compared >= 4 * 15, "Too few single-phase cases compared: " + compared);
  }

  /** Vermes chokes for two teeth vented to atmosphere; the result is then the critical flow. */
  @Test
  void chokedFlowIsCappedAtCriticalFlow() {
    JsonObject choked = referenceCase("n2_to_atm_choked");
    LabyrinthSealFlowCalculator calc = calculator(choked, gergFluid(choked), LabyrinthFlowMethod.VERMES);
    calc.calculate();
    assertTrue(calc.isChoked());
    assertEquals(calc.getCriticalMassFlowRate("kg/sec"), calc.getMassFlowRate("kg/sec"), 1.0e-15);

    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator unchoked = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    unchoked.calculate();
    assertFalse(unchoked.isChoked());
    assertTrue(unchoked.getMassFlowRate("kg/sec") < unchoked.getCriticalMassFlowRate("kg/sec"));
  }

  /** Pressure, temperature and pressure-drop inputs in different units give identical flows. */
  @Test
  void unitConventionsAreEquivalent() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator barg = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    barg.calculate();
    double reference = barg.getMassFlowRate("kg/sec");

    LabyrinthSealFlowCalculator absolute = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    absolute.setInletPressure((2.765 + 1.01325) * 100.0, "kPa");
    absolute.setInletTemperature(86.12 + 273.15, "K");
    absolute.setPressureDrop((2.765 - 1.5417) * 1000.0, "mbar");
    absolute.calculate();
    assertEquals(reference, absolute.getMassFlowRate("kg/sec"), 1.0e-12);

    assertEquals(reference * 3600.0, barg.getMassFlowRate("kg/hr"), 1.0e-9);
    assertEquals(2.765 - 1.5417, barg.getPressureDrop("bar"), 1.0e-12);
  }

  /** Derived flow quantities are consistent with mass flow, inlet density and clearance area. */
  @Test
  void derivedFlowQuantitiesAreConsistent() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator calc = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    calc.calculate();
    double massFlow = calc.getMassFlowRate("kg/sec");
    double actualVolumeFlow = massFlow / calc.getInletDensity();
    assertEquals(actualVolumeFlow * 3600.0, calc.getActualVolumeFlowRate("m3/hr"), 1.0e-9);
    assertEquals(actualVolumeFlow / geometry(worked).getClearanceArea(), calc.getClearanceVelocity("m/s"), 1.0e-9);

    double molarFlow = massFlow / (calc.getInletMolarMass() / 1000.0);
    double standardMolarVolume = neqsim.thermo.ThermodynamicConstantsInterface.R * 288.15 / 101325.0;
    assertEquals(molarFlow * standardMolarVolume * 3600.0, calc.getStandardVolumeFlowRate("Sm3/hr"), 1.0e-9);
  }

  /** Flow vanishes with the pressure drop and follows the expected geometric trends. */
  @Test
  void flowFollowsPhysicalTrends() {
    JsonObject worked = referenceCase("worked_case");
    for (LabyrinthFlowMethod method : CLOSED_FORM) {
      LabyrinthSealFlowCalculator tiny = calculator(worked, gergFluid(worked), method);
      tiny.setOutletPressure(2.765 - 1.0e-6, "barg");
      tiny.calculate();
      LabyrinthSealFlowCalculator base = calculator(worked, gergFluid(worked), method);
      base.calculate();
      LabyrinthSealFlowCalculator larger = calculator(worked, gergFluid(worked), method);
      larger.setOutletPressure(0.8, "barg");
      larger.calculate();
      assertTrue(tiny.getMassFlowRate("kg/sec") < 1.0e-2 * base.getMassFlowRate("kg/sec"), method + " small dp");
      assertTrue(larger.getMassFlowRate("kg/sec") > base.getMassFlowRate("kg/sec"), method + " larger dp");
    }
    double previous = Double.MAX_VALUE;
    for (int teeth : new int[] {3, 5, 8, 12, 20}) {
      LabyrinthSealFlowCalculator calc = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
      LabyrinthSealGeometry geometry = geometry(worked);
      geometry.setNumberOfTeeth(teeth);
      calc.setGeometry(geometry);
      calc.calculate();
      assertTrue(calc.getMassFlowRate("kg/sec") < previous, "more teeth must reduce leakage, n=" + teeth);
      previous = calc.getMassFlowRate("kg/sec");
    }
  }

  /** The calculator works with any NeqSim equation of state, not only GERG-2008. */
  @Test
  void cubicEquationOfStateGivesCloseResult() {
    JsonObject worked = referenceCase("worked_case");
    for (LabyrinthFlowMethod method : CLOSED_FORM) {
      LabyrinthSealFlowCalculator calc =
          calculator(worked, fillComposition(worked, new SystemSrkEos(288.15, 1.01325)), method);
      calc.calculate();
      double expected = excelFlow(worked, method);
      assertEquals(expected, calc.getMassFlowRate("kg/sec"), 0.03 * expected, method + " SRK vs workbook");
    }
  }

  /** calculateAllMethods returns the same values as running each method separately. */
  @Test
  void calculateAllMethodsMatchesIndividualRuns() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator calc = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    Map<LabyrinthFlowMethod, Double> all = calc.calculateAllMethods();
    for (LabyrinthFlowMethod method : CLOSED_FORM) {
      assertNotNull(all.get(method), method.name());
      assertEquals(excelFlow(worked, method), all.get(method), 1.0e-6 * excelFlow(worked, method));
    }
  }

  /** The caller's fluid is cloned, never flashed in place. */
  @Test
  void inputFluidIsNotModified() {
    JsonObject worked = referenceCase("worked_case");
    SystemInterface fluid = gergFluid(worked);
    LabyrinthSealFlowCalculator calc = calculator(worked, fluid, LabyrinthFlowMethod.STODOLA);
    calc.calculate();
    assertEquals(288.15, fluid.getTemperature(), 1.0e-12);
    assertEquals(1.01325, fluid.getPressure(), 1.0e-12);
  }

  /** Missing or inconsistent inputs are rejected with a remediation message. */
  @Test
  void invalidInputsAreRejected() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator reversed = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    reversed.setOutletPressure(3.0, "barg");
    IllegalStateException ex = assertThrows(IllegalStateException.class, reversed::calculate);
    assertTrue(ex.getMessage().contains("outlet pressure"));

    LabyrinthSealFlowCalculator missing = new LabyrinthSealFlowCalculator(gergFluid(worked), geometry(worked));
    missing.setInletPressure(2.765, "barg");
    missing.setInletTemperature(86.12, "C");
    assertThrows(IllegalStateException.class, missing::calculate);

    assertThrows(IllegalArgumentException.class,
        () -> new LabyrinthSealFlowCalculator(null, geometry(worked)));
    assertThrows(IllegalStateException.class, () -> new LabyrinthSealFlowCalculator(gergFluid(worked),
        geometry(worked)).getMassFlowRate("kg/sec"));
  }

  /** The JSON summary reports method, flow and choke status. */
  @Test
  void jsonSummaryContainsResults() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator calc = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.EGLI);
    calc.calculate();
    JsonObject json = JsonParser.parseString(calc.toJson()).getAsJsonObject();
    assertEquals("EGLI", json.get("method").getAsString());
    assertEquals(calc.getMassFlowRate("kg/hr"), json.get("massFlowRate_kg_hr").getAsDouble(), 1.0e-9);
    assertFalse(json.get("choked").getAsBoolean());
  }
}
