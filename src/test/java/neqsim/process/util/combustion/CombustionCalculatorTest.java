package neqsim.process.util.combustion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import neqsim.thermo.system.SystemInterface;
import neqsim.thermo.system.SystemSrkEos;

/**
 * Tests for {@link CombustionCalculator}.
 *
 * @author NeqSim
 * @version 1.0
 */
public class CombustionCalculatorTest {

  /**
   * Build a pure methane fuel fluid.
   *
   * @return a methane {@link SystemInterface}
   */
  private static SystemInterface methane() {
    SystemInterface f = new SystemSrkEos(288.15, 20.0);
    f.addComponent("methane", 1.0);
    f.setMixingRule("classic");
    return f;
  }

  /**
   * Stoichiometric methane combustion: exhaust O2 ~ 0, and the air/fuel mass ratio ~ 17.2.
   */
  @Test
  public void testStoichiometricMethane() {
    CombustionCalculator.CombustionResult r = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.0).calculate();
    assertEquals(0.0, r.exhaustO2VolPercent, 0.05);
    // stoichiometric AFR for methane ~ 17.2 kg air / kg fuel
    assertEquals(17.2, r.stoichAirFuelMassRatio, 0.4);
    assertEquals(r.stoichAirFuelMassRatio, r.airFuelMassRatio, 1.0e-6);
    // methane LHV ~ 50 MJ/kg
    assertEquals(50000.0, r.fuelLhvKJperKg, 1500.0);
  }

  /**
   * Gas-turbine excess air (lambda ~3.2) gives ~14-15 vol% exhaust O2 and a physical CO2 fraction.
   */
  @Test
  public void testGasTurbineExcessAir() {
    CombustionCalculator.CombustionResult r = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(3.2).calculate();
    assertTrue(r.exhaustO2VolPercent > 13.0 && r.exhaustO2VolPercent < 16.0,
        "exhaust O2 out of GT range: " + r.exhaustO2VolPercent);
    assertTrue(r.getFlueMoleFraction("CO2") > 0.02 && r.getFlueMoleFraction("CO2") < 0.06,
        "CO2 fraction out of range: " + r.getFlueMoleFraction("CO2"));
    // major mole fractions sum to 1
    double sum = 0.0;
    for (String k : new String[] { "N2", "O2", "CO2", "H2O", "Ar", "SO2" }) {
      sum += r.getFlueMoleFraction(k);
    }
    assertEquals(1.0, sum, 1.0e-6);
  }

  /**
   * Adiabatic flame temperature for stoichiometric methane/air is physically high (&gt; 1800 K) and drops substantially
   * with high excess air.
   */
  @Test
  public void testAdiabaticFlameTemperature() {
    double tStoich = new CombustionCalculator(methane()).setExcessAirRatio(1.0).calculate().adiabaticFlameTemperatureK;
    double tLean = new CombustionCalculator(methane()).setExcessAirRatio(3.2).calculate().adiabaticFlameTemperatureK;
    assertFalse(Double.isNaN(tStoich), "stoichiometric flame temperature is NaN");
    assertTrue(tStoich > 1800.0 && tStoich < 2600.0, "stoichiometric flame temperature out of range: " + tStoich);
    assertTrue(tLean < tStoich - 400.0,
        "excess air did not lower the flame temperature (stoich=" + tStoich + ", lean=" + tLean + ")");
  }

  /**
   * Fuel sulphur (H2S) reports as SO2; NOx and CO come from emission factors and scale with fuel energy.
   */
  @Test
  public void testSulphurAndPollutants() {
    SystemInterface f = new SystemSrkEos(288.15, 20.0);
    f.addComponent("methane", 0.98);
    f.addComponent("H2S", 0.02);
    f.setMixingRule("classic");
    CombustionCalculator.CombustionResult r = new CombustionCalculator(f).setFuelFlowRate(1000.0).setExcessAirRatio(3.0)
        .setNoxFactorGPerGJ(130.0).setCoFactorGPerGJ(30.0).calculate();
    assertTrue(r.pollutantPpmv.get("SO2") > 0.0, "SO2 should be present with H2S in fuel");
    assertTrue(r.getMassRateKgPerHr("SO2") > 0.0, "SO2 mass rate should be positive");
    assertTrue(r.getMassRateKgPerHr("NOx_as_NO2") > 0.0, "NOx mass rate should be positive");
    assertTrue(r.getMassRateKgPerHr("CO") > 0.0, "CO mass rate should be positive");
    // NOx factor 130 g/GJ * energy(GJ/hr) = kg/hr
    assertEquals(r.fuelEnergyGJperHr * 130.0 / 1000.0, r.getMassRateKgPerHr("NOx_as_NO2"), 1.0e-6);
  }

  /**
   * Selecting a low-NOx burner from the database lowers the NOx factor and raises CO relative to a conventional burner,
   * and an explicit factor override after selecting a type wins.
   */
  @Test
  public void testBurnerTypeDatabase() {
    CombustionCalculator.CombustionResult conv = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).setBurnerType(CombustionCalculator.BurnerType.CONVENTIONAL).calculate();
    CombustionCalculator.CombustionResult lowNox = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).setBurnerType(CombustionCalculator.BurnerType.LOW_NOX).calculate();
    assertTrue(lowNox.getMassRateKgPerHr("NOx_as_NO2") < conv.getMassRateKgPerHr("NOx_as_NO2"),
        "Low-NOx burner should emit less NOx than a conventional burner");
    assertTrue(lowNox.getMassRateKgPerHr("CO") > conv.getMassRateKgPerHr("CO"), "Low-NOx staging typically raises CO");

    // Overriding the NOx factor after selecting a burner type wins.
    CombustionCalculator.CombustionResult override = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).setBurnerType(CombustionCalculator.BurnerType.CONVENTIONAL).setNoxFactorGPerGJ(10.0)
        .calculate();
    assertEquals(override.fuelEnergyGJperHr * 10.0 / 1000.0, override.getMassRateKgPerHr("NOx_as_NO2"), 1.0e-6);
  }

  /**
   * A single field CO measurement calibrates the emission factor so the model reproduces the measured ppmv (corrected
   * to the measured O2) and scales it to other loads; the reference-O2 corrected ppmv is also reported.
   */
  @Test
  public void testFieldCalibrationCo() {
    // Measured 55 ppmv CO at 3.5 vol% exhaust O2 in a fired heater at 1000 kg/hr fuel.
    double measuredCoPpmv = 55.0;
    double measuredO2 = 3.5;
    CombustionCalculator calc = new CombustionCalculator(methane()).setFuelFlowRate(1000.0).setExcessAirRatio(1.2)
        .setBurnerType(CombustionCalculator.BurnerType.LOW_NOX).setReferenceO2VolPercent(3.0)
        .calibrateCoFromMeasuredPpmv(measuredCoPpmv, measuredO2);
    CombustionCalculator.CombustionResult r = calc.calculate();
    // Model reproduces the measurement once corrected from its own O2 to the measured O2.
    double predAtMeasO2 = CombustionCalculator.correctToReferenceO2(r.pollutantPpmv.get("CO"), r.exhaustO2VolPercent,
        measuredO2);
    assertEquals(measuredCoPpmv, predAtMeasO2, 0.5, "Calibrated model should reproduce the field CO measurement");
    assertTrue(r.pollutantPpmvAtReferenceO2.get("CO") > 0.0, "Reference-O2 corrected CO should be reported");
    assertEquals(calc.getCoCalibrationFactor(), r.coCalibrationFactor, 1.0e-9);

    // At double the fuel rate the CO mass rate scales with energy (calibration is energy-based).
    double coMassBase = r.getMassRateKgPerHr("CO");
    CombustionCalculator.CombustionResult r2 = calc.setFuelFlowRate(2000.0).calculate();
    assertEquals(2.0 * coMassBase, r2.getMassRateKgPerHr("CO"), 1.0e-6);
  }

  /**
   * Changing the fuel composition (more propane vs more ethane) shifts the rigorous flue picture — CO2, exhaust O2,
   * air/fuel ratio, LHV and adiabatic flame temperature all move — and, with thermal scaling enabled, NOx rises and CO
   * falls for the hotter-burning (heavier) fuel.
   */
  @Test
  public void testCompositionEffectOnPollutants() {
    // Lean gas: mostly ethane. Rich gas: mostly propane (heavier, hotter flame, more CO2 per mole).
    SystemInterface lean = new SystemSrkEos(288.15, 20.0);
    lean.addComponent("methane", 0.80);
    lean.addComponent("ethane", 0.18);
    lean.addComponent("propane", 0.02);
    lean.setMixingRule("classic");

    SystemInterface rich = new SystemSrkEos(288.15, 20.0);
    rich.addComponent("methane", 0.80);
    rich.addComponent("ethane", 0.02);
    rich.addComponent("propane", 0.18);
    rich.setMixingRule("classic");

    CombustionCalculator.CombustionResult rLean = new CombustionCalculator(lean).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).calculate();
    // Reference flame temp = the lean-gas flame temp, so scaling is neutral there and only the change is tested.
    double refT = rLean.adiabaticFlameTemperatureK;
    CombustionCalculator.CombustionResult rRich = new CombustionCalculator(rich).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).enableThermalNoxScaling(refT).enableThermalCoScaling(refT).calculate();
    CombustionCalculator.CombustionResult rLeanScaled = new CombustionCalculator(lean).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).enableThermalNoxScaling(refT).enableThermalCoScaling(refT).calculate();

    // Rigorous stoichiometric picture responds to composition.
    assertTrue(rRich.getFlueMoleFraction("CO2") > rLean.getFlueMoleFraction("CO2"),
        "Heavier fuel produces more CO2 per mole burned");
    assertTrue(rRich.fuelLhvKJperKg != rLean.fuelLhvKJperKg, "LHV per kg changes with composition");
    assertTrue(rRich.adiabaticFlameTemperatureK > rLean.adiabaticFlameTemperatureK,
        "Heavier fuel burns hotter at the same excess air");

    // Thermal scaling: hotter (heavier) fuel -> more NOx, less CO relative to the reference lean gas.
    assertTrue(rRich.pollutantPpmv.get("NOx") > rLeanScaled.pollutantPpmv.get("NOx"),
        "NOx rises with the hotter flame of the heavier fuel");
    assertTrue(rRich.getMassRateKgPerHr("CO") < rLeanScaled.getMassRateKgPerHr("CO"),
        "CO falls with the hotter, more complete flame of the heavier fuel");
  }

  /**
   * The JSON serialization is non-null and contains the key result fields.
   */
  @Test
  public void testToJson() {
    String json = new CombustionCalculator(methane()).setExcessAirRatio(3.0).calculate().toJson();
    assertTrue(json.contains("exhaustO2VolPercent"));
    assertTrue(json.contains("flueMoleFraction"));
    assertTrue(json.contains("pollutantPpmv"));
    assertTrue(json.contains("adiabaticFlameTemperatureK"));
  }

  /**
   * Dry-basis reporting (the CEMS / emission-limit convention): removing water concentrates O2 and pollutants, so the
   * dry exhaust O2 and dry ppmv exceed the wet values, and the reference-O2 correction is applied on the dry basis.
   */
  @Test
  public void testDryBasisAndReferenceO2() {
    CombustionCalculator.CombustionResult r = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).setReferenceO2VolPercent(3.0).calculate();

    // Water is present in the flue, so dry values are higher than wet values.
    assertTrue(r.waterVolPercent > 0.0, "methane combustion produces water in the flue");
    assertTrue(r.exhaustO2VolPercentDry > r.exhaustO2VolPercent, "dry O2 exceeds wet O2 (water removed)");
    assertTrue(r.pollutantPpmvDry.get("NOx") > r.pollutantPpmv.get("NOx"), "dry NOx ppmv exceeds wet NOx ppmv");

    // Reference-O2 correction must be applied on the DRY concentration at the DRY exhaust O2.
    double expected = CombustionCalculator.correctToReferenceO2(r.pollutantPpmvDry.get("CO"), r.exhaustO2VolPercentDry,
        3.0);
    assertEquals(expected, r.pollutantPpmvAtReferenceO2.get("CO"), 1.0e-6,
        "reference-O2 CO must use the dry-basis concentration and dry exhaust O2");
  }

  /**
   * Stack-emission reporting outputs (EU IED / EN 14792 / EN 15058 / EN 14791): mg/Nm3 conversion (1 ppmv NOx-as-NO2 is
   * ~2.05 mg/Nm3 at 0 degC), normalized flue-gas flow, and the tonnes/year roll-up from operating hours.
   */
  @Test
  public void testStackEmissionOutputs() {
    CombustionCalculator.CombustionResult r = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).setReferenceO2VolPercent(3.0).setNormalTemperatureC(0.0)
        .setAnnualOperatingHours(8000.0).calculate();

    // mg/Nm3 conversion factor for NOx (as NO2) at 0 degC: 1 ppmv -> ~2.053 mg/Nm3.
    double noxMgPerNm3 = r.pollutantMgPerNm3Dry.get("NOx");
    double noxPpmvDry = r.pollutantPpmvDry.get("NOx");
    assertEquals(2.053, noxMgPerNm3 / noxPpmvDry, 0.02, "1 ppmv NOx-as-NO2 ~ 2.05 mg/Nm3 at 0 degC");

    // mg/Nm3 at reference O2 is available for the limit comparison and exceeds the as-measured dry value (dilution).
    assertTrue(r.pollutantMgPerNm3AtReferenceO2.get("NOx") > noxMgPerNm3,
        "reference-O2 mg/Nm3 exceeds the dry as-measured value at lean firing");

    // Normalized flue-gas flow: dry < wet, both positive.
    assertTrue(r.flueGasNm3PerHrDry > 0.0 && r.flueGasNm3PerHrDry < r.flueGasNm3PerHrWet,
        "dry Nm3/hr is positive and below wet Nm3/hr");

    // Annual tonnes = kg/hr * hours / 1000.
    double expectedNoxTpy = r.getMassRateKgPerHr("NOx_as_NO2") * 8000.0 / 1000.0;
    assertEquals(expectedNoxTpy, r.massRateTonnesPerYear.get("NOx_as_NO2"), 1.0e-6,
        "annual NOx tonnes = kg/hr * operating hours / 1000");
  }

  /**
   * Extended stack-emission physics: SO3 / acid dew point, PM / CH4-slip / VOC / N2O emission factors, the NOx route
   * breakdown (thermal + prompt + fuel-N), and actual stack-exit conditions (Am3/hr and velocity).
   */
  @Test
  public void testStackExtras() {
    // Sour fuel so there is real sulphur; 3 % of SOx leaves as SO3.
    SystemInterface sour = new SystemSrkEos(288.15, 20.0);
    sour.addComponent("methane", 0.98);
    sour.addComponent("H2S", 0.02);
    sour.setMixingRule("classic");

    CombustionCalculator.CombustionResult r = new CombustionCalculator(sour).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).setReferenceO2VolPercent(3.0).setSo3FractionOfSox(0.03).setPmFactorGPerGJ(1.0)
        .setCh4SlipFactorGPerGJ(20.0).setVocFactorGPerGJ(2.0).setN2oFactorGPerGJ(1.0).setPromptNoxFactorGPerGJ(10.0)
        .setFuelNoxFactorGPerGJ(5.0).setStackGasTemperatureC(150.0).setStackDiameterM(1.5).calculate();

    // SO3 split and acid dew point.
    assertTrue(r.pollutantPpmv.containsKey("SO3"), "SO3 reported when a conversion fraction is set");
    assertTrue(r.acidDewPointC > r.waterDewPointC, "sulfuric-acid dew point is well above the water dew point");
    assertTrue(r.acidDewPointC > 90.0 && r.acidDewPointC < 200.0, "acid dew point in a physical band");

    // Extra pollutants present.
    assertTrue(r.getMassRateKgPerHr("PM") > 0.0, "PM mass rate reported");
    assertTrue(r.getMassRateKgPerHr("CH4") > 0.0, "CH4 slip mass rate reported");
    assertTrue(r.getMassRateKgPerHr("VOC") > 0.0, "VOC mass rate reported");
    assertTrue(r.getMassRateKgPerHr("N2O") > 0.0, "N2O mass rate reported");
    assertTrue(r.pollutantMgPerNm3AtReferenceO2.containsKey("CH4"), "CH4 mg/Nm3 at reference O2 reported");

    // NOx route breakdown sums to the reported total.
    assertTrue(r.noxPromptKgPerHr > 0.0 && r.noxFuelKgPerHr > 0.0, "prompt and fuel NOx are added");
    assertEquals(r.noxThermalKgPerHr + r.noxPromptKgPerHr + r.noxFuelKgPerHr, r.getMassRateKgPerHr("NOx_as_NO2"),
        1.0e-9, "total NOx = thermal + prompt + fuel");

    // Actual stack conditions: hot stack expands the gas above the normalized flow, and velocity is positive.
    assertTrue(r.stackActualM3PerHr > r.flueGasNm3PerHrWet,
        "actual (150 degC) volume exceeds the 0 degC normalized volume");
    assertTrue(r.stackVelocityMPerS > 0.0, "stack velocity computed from the stack diameter");
  }

  /**
   * Air-driven mode: with a fixed combustion-air flow, lowering the fuel rate makes the mixture leaner (lambda rises,
   * exhaust O2 rises) while the air-driven lambda reproduces the classic lambda-driven result at the matching air flow.
   */
  @Test
  public void testAirDrivenMinimumFuelTurndown() {
    // First find the air flow that gives lambda = 1.15 at 1000 kg/hr methane (classic mode), then reuse it air-driven.
    CombustionCalculator.CombustionResult ref = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setExcessAirRatio(1.15).calculate();
    double fixedAirKgPerHr = ref.airFuelMassRatio * 1000.0;

    CombustionCalculator.CombustionResult atDesign = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setAirFlowRate(fixedAirKgPerHr).calculate();
    assertTrue(atDesign.airDriven, "result flags air-driven mode");
    assertEquals(1.15, atDesign.excessAirRatio, 1.0e-3, "air-driven lambda reproduces the classic lambda");
    assertFalse(atDesign.subStoichiometric, "design point is lean");

    // Turn the fuel down at the SAME fixed air: the mixture must get leaner (higher lambda and exhaust O2).
    CombustionCalculator.CombustionResult turndown = new CombustionCalculator(methane()).setFuelFlowRate(700.0)
        .setAirFlowRate(fixedAirKgPerHr).calculate();
    assertTrue(turndown.excessAirRatio > atDesign.excessAirRatio, "less fuel at fixed air is leaner");
    assertTrue(turndown.exhaustO2VolPercent > atDesign.exhaustO2VolPercent, "leaner firing raises exhaust O2");
  }

  /**
   * Air-driven mode with fuel heaviness. On a mass basis methane is the most hydrogen-rich fuel, so heavier fuel has a
   * LOWER stoichiometric air/fuel mass ratio (needs less air per kg). Consequently, at a fixed air flow and the same
   * fuel mass, the heavier fuel runs leaner (higher lambda).
   */
  @Test
  public void testAirDrivenHeavierFuelStoichAndLambda() {
    SystemInterface heavy = new SystemSrkEos(288.15, 20.0);
    heavy.addComponent("methane", 0.70);
    heavy.addComponent("ethane", 0.15);
    heavy.addComponent("propane", 0.15);
    heavy.setMixingRule("classic");

    double fixedAirKgPerHr = 20000.0;
    CombustionCalculator.CombustionResult light = new CombustionCalculator(methane()).setFuelFlowRate(1000.0)
        .setAirFlowRate(fixedAirKgPerHr).calculate();
    CombustionCalculator.CombustionResult heavyR = new CombustionCalculator(heavy).setFuelFlowRate(1000.0)
        .setAirFlowRate(fixedAirKgPerHr).calculate();

    assertTrue(heavyR.stoichAirFuelMassRatio < light.stoichAirFuelMassRatio,
        "heavier fuel needs less air per kg fuel (lower stoichiometric AFR on a mass basis)");
    assertTrue(heavyR.excessAirRatio > light.excessAirRatio,
        "at the same fuel mass and fixed air, the heavier fuel runs leaner (higher lambda)");
  }
}
