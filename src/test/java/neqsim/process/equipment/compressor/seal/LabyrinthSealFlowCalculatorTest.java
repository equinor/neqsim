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
import neqsim.thermo.system.SystemPrEos;
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
   * Relative tolerance against the workbook. The NeqSim GERG-2008 phase converts the GERG molar density with the NeqSim
   * database molar masses, which differ from the GERG-2008 molar masses used by the workbook by about 3e-5 (relative).
   */
  private static final double WORKBOOK_TOLERANCE = 5.0e-5;

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
    JsonElement value = testCase.getAsJsonObject("excel").getAsJsonObject("mass_flow_kg_s").get(method.name());
    return value == null || value.isJsonNull() ? Double.NaN : value.getAsDouble();
  }

  /** NeqSim GERG-2008 reproduces the workbook inlet properties used by every method. */
  @Test
  void gergInletPropertiesMatchWorkbook() {
    JsonObject worked = referenceCase("worked_case");
    JsonObject excel = worked.getAsJsonObject("excel").getAsJsonObject("inlet");
    LabyrinthSealFlowCalculator calc = calculator(worked, gergFluid(worked), LabyrinthFlowMethod.KEARTON);
    calc.calculate();

    assertEquals(excel.get("density_kg_m3").getAsDouble(), calc.getInletDensity(), 5.0e-5 * 2.9118);
    assertEquals(excel.get("molar_mass_kg_kmol").getAsDouble(), calc.getInletMolarMass(), 5.0e-5 * 22.858);
    assertEquals(excel.get("kappa").getAsDouble(), calc.getInletIsentropicExponent(), 1.0e-5 * 1.2009);
    assertEquals(excel.get("Z").getAsDouble(), calc.getInletCompressibility(), 1.0e-5);
    assertFalse(calc.hasLiquidAtInlet());
  }

  /**
   * The four closed-form methods reproduce the workbook over the whole single-phase sweep. Below the Kearton peak
   * pressure ratio the workbook keeps falling while NeqSim holds the choked peak, so those Kearton cases must be higher
   * than the workbook instead.
   */
  @Test
  void closedFormMethodsMatchWorkbookAcrossSweep() {
    List<String> failures = new ArrayList<String>();
    List<String> keartonChokedCases = new ArrayList<String>();
    int compared = 0;
    for (JsonElement element : REFERENCE.getAsJsonArray("cases")) {
      JsonObject testCase = element.getAsJsonObject();
      LabyrinthSealFlowCalculator calc = calculator(testCase, gergFluid(testCase), LabyrinthFlowMethod.KEARTON);
      calc.calculate();
      if (calc.hasLiquidAtInlet()) {
        continue;
      }
      JsonObject in = testCase.getAsJsonObject("inputs");
      double ratio = (in.get("outlet_pressure_barg").getAsDouble() + 1.01325)
          / (in.get("inlet_pressure_barg").getAsDouble() + 1.01325);
      boolean belowKeartonPeak = ratio < LabyrinthSealFlowCalculator
          .keartonCriticalPressureRatio(in.get("number_of_teeth").getAsInt());
      for (LabyrinthFlowMethod method : CLOSED_FORM) {
        double expected = excelFlow(testCase, method);
        if (Double.isNaN(expected)) {
          continue;
        }
        calc.setMethod(method);
        calc.calculate();
        compared++;
        if (method == LabyrinthFlowMethod.KEARTON && belowKeartonPeak) {
          keartonChokedCases.add(testCase.get("id").getAsString());
          assertTrue(calc.isChoked(), testCase.get("id").getAsString());
          assertTrue(calc.getMassFlowRate("kg/sec") > expected, testCase.get("id").getAsString());
          continue;
        }
        double relativeError = Math.abs(calc.getMassFlowRate("kg/sec") - expected) / expected;
        if (!(relativeError <= WORKBOOK_TOLERANCE)) {
          failures.add(testCase.get("id").getAsString() + "/" + method + " rel.err=" + relativeError);
        }
      }
    }
    assertTrue(failures.isEmpty(), "Workbook mismatches: " + failures);
    assertTrue(compared >= 4 * 15, "Too few single-phase cases compared: " + compared);
    assertEquals(2, keartonChokedCases.size(), "Kearton cases below the peak ratio: " + keartonChokedCases);
  }

  /** Vermes chokes for two teeth vented to atmosphere; the result is then the critical flow. */
  @Test
  void chokedFlowIsCappedAtCriticalFlow() {
    JsonObject choked = referenceCase("n2_to_atm_choked");
    LabyrinthSealFlowCalculator calc = calculator(choked, gergFluid(choked), LabyrinthFlowMethod.VERMES);
    calc.calculate();
    assertTrue(calc.isChoked());
    assertEquals(calc.getCriticalMassFlowRate("kg/sec"), calc.getMassFlowRate("kg/sec"), 1.0e-15);

    // n + ln(pn/p0) < 0: the workbook returns an error, NeqSim reports the critical flow.
    JsonObject deep = referenceCase("methane_hp_choked_n3");
    LabyrinthSealFlowCalculator vermes = calculator(deep, gergFluid(deep), LabyrinthFlowMethod.VERMES);
    vermes.calculate();
    assertTrue(vermes.isChoked());
    assertTrue(vermes.getMassFlowRate("kg/sec") > 0.0 && !Double.isInfinite(vermes.getMassFlowRate("kg/sec")));
    assertEquals(vermes.getCriticalMassFlowRate("kg/sec"), vermes.getMassFlowRate("kg/sec"), 1.0e-15);

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

  /** SRK and PR fluids give results close to the GERG-2008 workbook for a light gas at low pressure. */
  @Test
  void cubicEquationsOfStateGiveCloseResult() {
    JsonObject worked = referenceCase("worked_case");
    SystemInterface[] fluids = {srkFluid(worked), fillComposition(worked, new SystemPrEos(288.15, 1.01325))};
    for (SystemInterface fluid : fluids) {
      LabyrinthSealFlowCalculator calc = calculator(worked, fluid, LabyrinthFlowMethod.KEARTON);
      Map<LabyrinthFlowMethod, Double> all = calc.calculateAllMethods();
      for (LabyrinthFlowMethod method : LabyrinthFlowMethod.values()) {
        double expected = excelFlow(worked, method);
        assertEquals(expected, all.get(method), 5.0e-3 * expected, method + " " + fluid.getModelName());
      }
    }
  }

  /** Water that condenses at the inlet is detected; the workbook treats such gas as single phase. */
  @Test
  void condensedWaterAtInletIsDetected() {
    JsonObject cold = referenceCase("cold_20C");
    LabyrinthSealFlowCalculator calc = calculator(cold, gergFluid(cold), LabyrinthFlowMethod.KEARTON);
    calc.calculate();
    assertTrue(calc.hasLiquidAtInlet());
    assertTrue(calc.getMassFlowRate("kg/sec") > 0.0);
  }

  /** calculateAllMethods returns, for every method including Neumann, the value of an individual run. */
  @Test
  void calculateAllMethodsMatchesIndividualRuns() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealFlowCalculator calc = calculator(worked, srkFluid(worked), LabyrinthFlowMethod.NEUMANN);
    Map<LabyrinthFlowMethod, Double> all = calc.calculateAllMethods();
    assertEquals(LabyrinthFlowMethod.values().length, all.size());
    assertEquals(all.get(LabyrinthFlowMethod.NEUMANN), calc.getMassFlowRate("kg/sec"), 1.0e-15);
    assertEquals(worked.get("inputs").getAsJsonObject().get("number_of_teeth").getAsInt() + 1,
        calc.getToothPressures("bara").length, "selected method profile is kept after calculateAllMethods");
    for (LabyrinthFlowMethod method : LabyrinthFlowMethod.values()) {
      assertNotNull(all.get(method), method.name());
      LabyrinthSealFlowCalculator single = calculator(worked, srkFluid(worked), method);
      single.calculate();
      assertEquals(single.getMassFlowRate("kg/sec"), all.get(method), 1.0e-9 * all.get(method), method.name());
    }
  }

  /**
   * Builds an SRK fluid for a reference case (fast; used where GERG-2008 parity is not the point of the test).
   *
   * @param testCase reference case
   * @return SRK fluid
   */
  private static SystemInterface srkFluid(JsonObject testCase) {
    return fillComposition(testCase, new SystemSrkEos(288.15, 1.01325));
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

    assertThrows(IllegalArgumentException.class, () -> new LabyrinthSealFlowCalculator(null, geometry(worked)));
    assertThrows(IllegalStateException.class,
        () -> new LabyrinthSealFlowCalculator(gergFluid(worked), geometry(worked)).getMassFlowRate("kg/sec"));
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
    assertTrue(json.get("withinValidityRange").getAsBoolean());
    assertEquals(0, json.getAsJsonArray("validityViolations").size());
  }

  /**
   * Builds a worked-case calculator with a modified tip width and radial clearance (mm).
   *
   * @param tipWidthMm tip width in mm
   * @param clearanceMm radial clearance in mm
   * @param method flow method
   * @return configured calculator (not yet run)
   */
  private static LabyrinthSealFlowCalculator workedCaseWith(double tipWidthMm, double clearanceMm,
      LabyrinthFlowMethod method) {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealGeometry geometry = geometry(worked);
    geometry.setTipWidth(tipWidthMm, "mm");
    geometry.setRadialClearance(clearanceMm, "mm");
    LabyrinthSealFlowCalculator calc = calculator(worked, srkFluid(worked), method);
    calc.setGeometry(geometry);
    return calc;
  }

  /** Changing the caller's geometry after calculate() cannot alter or mix the stored result (review finding 1). */
  @Test
  void geometryIsCopiedAtEveryBoundary() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealGeometry geometry = geometry(worked);
    LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(gergFluid(worked), geometry);
    calc.setInletPressure(2.765, "barg");
    calc.setInletTemperature(86.12, "C");
    calc.setOutletPressure(1.5417, "barg");
    calc.calculate();
    double velocity = calc.getClearanceVelocity("m/s");
    double area = calc.getGeometry().getClearanceArea();

    geometry.setRadialClearance(1.0, "mm");
    assertEquals(velocity, calc.getClearanceVelocity("m/s"), 1.0e-12);
    assertEquals(0.2286, calc.getGeometry().getRadialClearance("mm"), 1.0e-12);

    calc.getGeometry().setRadialClearance(1.0, "mm");
    assertEquals(area, calc.getGeometry().getClearanceArea(), 1.0e-18);
    assertTrue(calc.toJson().contains("0.2286"));

    LabyrinthSealGeometry replacement = geometry(worked);
    calc.setGeometry(replacement);
    replacement.setRadialClearance(1.0, "mm");
    assertEquals(0.2286, calc.getGeometry().getRadialClearance("mm"), 1.0e-12);
  }

  /** Egli's carry-over coefficient turns negative at large c/t: no negative leakage is published (finding 2). */
  @Test
  void egliOutsideCorrelationRangeGivesNaNAndViolation() {
    LabyrinthSealFlowCalculator calc = workedCaseWith(0.3, 1.0, LabyrinthFlowMethod.EGLI);
    calc.calculate();
    assertTrue(Double.isNaN(calc.getMassFlowRate("kg/sec")));
    assertFalse(calc.isWithinValidityRange());
    assertEquals(1, calc.getValidityViolations().size());
    assertTrue(calc.getValidityViolations().get(0).startsWith("EGLI"), calc.getValidityViolations().toString());
    assertTrue(calc.getValidityViolations().get(0).contains("carry-over"));

    Map<LabyrinthFlowMethod, Double> all = calc.calculateAllMethods();
    assertTrue(Double.isNaN(all.get(LabyrinthFlowMethod.EGLI)));
    for (LabyrinthFlowMethod other : new LabyrinthFlowMethod[] {LabyrinthFlowMethod.STODOLA, LabyrinthFlowMethod.VERMES,
        LabyrinthFlowMethod.KEARTON}) {
      assertTrue(all.get(other) > 0.0 && !Double.isInfinite(all.get(other)), other.name());
    }
  }

  /** Vermes' residual-energy factor reaches 1 when the tip width approaches the pitch (finding 3). */
  @Test
  void vermesOutsideCorrelationRangeGivesNaNAndViolation() {
    LabyrinthSealFlowCalculator calc = workedCaseWith(1.8, 0.2286, LabyrinthFlowMethod.VERMES);
    calc.calculate();
    assertTrue(Double.isNaN(calc.getMassFlowRate("kg/sec")));
    assertFalse(calc.isChoked());
    assertFalse(calc.isWithinValidityRange());
    assertTrue(calc.getValidityViolations().get(0).startsWith("VERMES"), calc.getValidityViolations().toString());
    assertTrue(calc.getValidityViolations().get(0).contains("residual-energy"));

    Map<LabyrinthFlowMethod, Double> all = calc.calculateAllMethods();
    assertTrue(Double.isNaN(all.get(LabyrinthFlowMethod.VERMES)));
    assertTrue(all.get(LabyrinthFlowMethod.KEARTON) > 0.0);
    assertEquals(1, calc.getValidityViolations().size(), calc.getValidityViolations().toString());
  }

  /** JSON is strict (no bare NaN) when tooth height is unset or a method is undefined (finding 4). */
  @Test
  void jsonIsStrictWithoutToothHeightAndWithUndefinedMethod() {
    JsonObject worked = referenceCase("worked_case");
    LabyrinthSealGeometry geometry = new LabyrinthSealGeometry();
    geometry.setShaftDiameter(108.566, "mm");
    geometry.setToothPitch(2.0, "mm");
    geometry.setTipWidth(0.3, "mm");
    geometry.setRadialClearance(0.2286, "mm");
    geometry.setNumberOfTeeth(5);
    LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(gergFluid(worked), geometry);
    calc.setInletPressure(2.765, "barg");
    calc.setInletTemperature(86.12, "C");
    calc.setOutletPressure(1.5417, "barg");
    calc.calculate();
    String json = calc.toJson();
    assertFalse(json.contains("NaN"), json);
    assertFalse(json.contains("toothHeight"), json);

    LabyrinthSealFlowCalculator undefined = workedCaseWith(1.8, 0.2286, LabyrinthFlowMethod.VERMES);
    undefined.calculate();
    String undefinedJson = undefined.toJson();
    assertFalse(undefinedJson.contains("NaN"), undefinedJson);
    JsonObject parsed = JsonParser.parseString(undefinedJson).getAsJsonObject();
    assertTrue(parsed.get("massFlowRate_kg_s").isJsonNull());
    assertFalse(parsed.get("withinValidityRange").getAsBoolean());
  }

  /**
   * Builds the reviewer's methane case: SRK, inlet 3.77825 bara and 86.12 C, worked geometry with n teeth.
   *
   * @param teeth number of teeth
   * @param pressureRatio outlet to inlet absolute pressure ratio
   * @param method flow method
   * @return configured calculator (not yet run)
   */
  private static LabyrinthSealFlowCalculator methaneCase(int teeth, double pressureRatio, LabyrinthFlowMethod method) {
    SystemInterface methane = new SystemSrkEos(288.15, 1.01325);
    methane.addComponent("methane", 1.0);
    methane.setMixingRule("classic");
    LabyrinthSealGeometry geometry = geometry(referenceCase("worked_case"));
    geometry.setNumberOfTeeth(teeth);
    LabyrinthSealFlowCalculator calc = new LabyrinthSealFlowCalculator(methane, geometry);
    calc.setInletPressure(3.77825, "bara");
    calc.setInletTemperature(86.12, "C");
    calc.setOutletPressure(3.77825 * pressureRatio, "bara");
    calc.setMethod(method);
    return calc;
  }

  /** The Kearton peak pressure ratio depends only on the number of teeth and falls as teeth are added. */
  @Test
  void keartonPeakPressureRatioDependsOnTeeth() {
    assertEquals(1.0 / Math.sqrt(3.0), LabyrinthSealFlowCalculator.keartonCriticalPressureRatio(1), 1.0e-6);
    assertEquals(0.4262, LabyrinthSealFlowCalculator.keartonCriticalPressureRatio(2), 1.0e-4);
    assertEquals(0.2637, LabyrinthSealFlowCalculator.keartonCriticalPressureRatio(5), 1.0e-4);
    assertEquals(0.1233, LabyrinthSealFlowCalculator.keartonCriticalPressureRatio(20), 1.0e-4);
  }

  /**
   * Leakage never falls as backpressure falls, for every method, with two and five teeth (review: Kearton turnover).
   * Below the Kearton peak ratio Kearton reports choked flow equal to the peak value.
   */
  @Test
  void leakageIsMonotonicInBackpressureAcrossTurnover() {
    double[] ratios = {0.9, 0.7, 0.5, 0.43, 0.4, 0.3, 0.26, 0.2, 0.1, 0.05};
    List<String> failures = new ArrayList<String>();
    for (int teeth : new int[] {2, 5}) {
      double peakRatio = LabyrinthSealFlowCalculator.keartonCriticalPressureRatio(teeth);
      LabyrinthSealFlowCalculator atPeak = methaneCase(teeth, peakRatio, LabyrinthFlowMethod.KEARTON);
      atPeak.calculate();
      double peakFlow = atPeak.getMassFlowRate("kg/sec");
      for (LabyrinthFlowMethod method : LabyrinthFlowMethod.values()) {
        double previous = 0.0;
        for (double ratio : ratios) {
          LabyrinthSealFlowCalculator calc = methaneCase(teeth, ratio, method);
          calc.calculate();
          double flow = calc.getMassFlowRate("kg/sec");
          String id = method + " n=" + teeth + " r=" + ratio;
          if (!(flow >= previous * (1.0 - 1.0e-9))) {
            failures.add(id + " flow " + flow + " < previous " + previous);
          }
          if (!calc.isWithinValidityRange()) {
            failures.add(id + " " + calc.getValidityViolations());
          }
          if (method == LabyrinthFlowMethod.KEARTON && ratio < peakRatio) {
            if (!calc.isChoked()) {
              failures.add(id + " below the peak ratio but not choked");
            }
            if (Math.abs(flow - Math.min(peakFlow, calc.getCriticalMassFlowRate("kg/sec"))) > 1.0e-12) {
              failures.add(id + " flow " + flow + " differs from peak " + peakFlow);
            }
          }
          previous = flow;
        }
      }
    }
    assertTrue(failures.isEmpty(), failures.toString());
  }

  /** Reviewer's reproduction: two teeth, pn/p0 0.3 to 0.1 must not reduce Kearton leakage. */
  @Test
  void keartonDoesNotFallWithBackpressureForReviewerCase() {
    LabyrinthSealFlowCalculator higher = methaneCase(2, 0.3, LabyrinthFlowMethod.KEARTON);
    higher.calculate();
    LabyrinthSealFlowCalculator lower = methaneCase(2, 0.1, LabyrinthFlowMethod.KEARTON);
    lower.calculate();
    assertTrue(higher.isChoked());
    assertTrue(lower.isChoked());
    assertEquals(higher.getMassFlowRate("kg/sec"), lower.getMassFlowRate("kg/sec"), 1.0e-12);
    assertTrue(lower.getMassFlowRate("kg/sec") > 0.0353487483);
  }

  /** Invalid standard conditions are rejected and leave the previous standard conditions unchanged (review). */
  @Test
  void invalidStandardConditionsAreRejectedAtomically() {
    LabyrinthSealFlowCalculator calc = methaneCase(5, 0.5, LabyrinthFlowMethod.KEARTON);
    calc.calculate();
    double reference = calc.getStandardVolumeFlowRate("Sm3/hr");
    assertTrue(Double.isFinite(reference) && reference > 0.0);

    Object[][] rejected = {{15.0, "C", 0.0, "bara"}, {15.0, "C", -1.0, "bara"}, {15.0, "C", Double.NaN, "bara"},
        {15.0, "C", Double.POSITIVE_INFINITY, "bara"}, {-273.15, "C", 1.01325, "bara"}, {-10.0, "K", 1.01325, "bara"},
        {Double.NaN, "C", 1.01325, "bara"}, {Double.POSITIVE_INFINITY, "C", 1.01325, "bara"},
        {0.0, "C", 1.0, "invalid"}, {0.0, "invalid", 1.0, "bara"}};
    for (Object[] args : rejected) {
      assertThrows(IllegalArgumentException.class,
          () -> calc.setStandardConditions((Double) args[0], (String) args[1], (Double) args[2], (String) args[3]),
          java.util.Arrays.toString(args));
      assertEquals(reference, calc.getStandardVolumeFlowRate("Sm3/hr"), 1.0e-12, java.util.Arrays.toString(args));
    }

    calc.setStandardConditions(0.0, "C", 1.01325, "bara");
    assertEquals(reference * 273.15 / 288.15, calc.getStandardVolumeFlowRate("Sm3/hr"), 1.0e-9 * reference);
  }
}
